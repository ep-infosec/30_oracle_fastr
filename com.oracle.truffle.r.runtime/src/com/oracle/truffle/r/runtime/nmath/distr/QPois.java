/*
 * Copyright (C) 1998 Ross Ihaka
 * Copyright (c) 2000-2014, The R Core Team
 * Copyright (c) 2016, 2019, Oracle and/or its affiliates
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, a copy is available at
 * https://www.R-project.org/Licenses/
 */
package com.oracle.truffle.r.runtime.nmath.distr;

import static com.oracle.truffle.r.runtime.nmath.MathConstants.DBL_EPSILON;

import com.oracle.truffle.r.runtime.nmath.DPQ;
import com.oracle.truffle.r.runtime.nmath.DPQ.EarlyReturn;
import com.oracle.truffle.r.runtime.nmath.MathFunctions.Function2_2;
import com.oracle.truffle.r.runtime.nmath.RMath;
import com.oracle.truffle.r.runtime.nmath.RMathError;

public final class QPois implements Function2_2 {

    public static QPois create() {
        return new QPois();
    }

    public static QPois getUncached() {
        return new QPois();
    }

    private final Qnorm qnorm = new Qnorm();
    private final PPois ppois = new PPois();

    @Override
    public double evaluate(double pIn, double lambda, boolean lowerTail, boolean logP) {
        if (Double.isNaN(pIn) || Double.isNaN(lambda)) {
            return pIn + lambda;
        }
        if (!Double.isFinite(lambda)) {
            return RMathError.defaultError();
        }
        if (lambda < 0) {
            return RMathError.defaultError();
        }

        try {
            DPQ.rqp01check(pIn, logP);
        } catch (EarlyReturn e) {
            return e.result;
        }

        if (lambda == 0) {
            return 0;
        }

        if (pIn == DPQ.rdt0(lowerTail, logP)) {
            return 0;
        }
        if (pIn == DPQ.rdt1(lowerTail, logP)) {
            return Double.POSITIVE_INFINITY;
        }

        double sigma = Math.sqrt(lambda);
        /* gamma = sigma; PR#8058 should be kurtosis which is mu^-0.5 */
        double gamma = 1.0 / sigma;

        /*
         * Note : "same" code in qpois.c, qbinom.c, qnbinom.c -- FIXME: This is far from optimal
         * [cancellation for p ~= 1, etc]:
         */
        double p = pIn;
        if (!lowerTail || logP) {
            p = DPQ.rdtqiv(p, lowerTail, logP); /* need check again (cancellation!): */
            if (p == 0.) {
                return 0;
            }
            if (p == 1.) {
                return Double.POSITIVE_INFINITY;
            }
        }
        /* temporary hack --- FIXME --- */
        if (p + 1.01 * DBL_EPSILON >= 1.) {
            return Double.POSITIVE_INFINITY;
        }

        /* y := approx.value (Cornish-Fisher expansion) : */
        double z = qnorm.evaluate(p, 0., 1., /* lower_tail */true, /* log_p */false);
        // #ifdef HAVE_NEARBYINT
        // y = nearbyint(mu + sigma * (z + gamma * (z*z - 1) / 6));
        // #else
        double y = RMath.round(lambda + sigma * (z + gamma * (z * z - 1) / 6));

        /* fuzz to ensure left continuity; 1 - 1e-7 may lose too much : */
        p *= 1 - 64 * DBL_EPSILON;

        QuantileSearch search = new QuantileSearch((quantile, lt, lp) -> ppois.evaluate(quantile, lambda, lt, lp));
        if (lambda < 1e5) {
            /* If the mean is not too large a simple search is OK */
            return search.simpleSearch(y, p, 1);
        } else {
            /* Otherwise be a bit cleverer in the search */
            return search.iterativeSearch(y, p);
        }
    }
}
