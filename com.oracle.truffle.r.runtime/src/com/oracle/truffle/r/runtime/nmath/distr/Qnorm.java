/*
 * Copyright (C) 1998 Ross Ihaka
 * Copyright (c) 2000--2014, The R Core Team
 * Copyright (c) 2007, The R Foundation
 * Copyright (c) 2016, 2021, Oracle and/or its affiliates
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
/*
 *  based on AS 111 (C) 1977 Royal Statistical Society
 *  and   on AS 241 (C) 1988 Royal Statistical Society
 */
package com.oracle.truffle.r.runtime.nmath.distr;

import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.r.runtime.nmath.DPQ;
import com.oracle.truffle.r.runtime.nmath.DPQ.EarlyReturn;
import com.oracle.truffle.r.runtime.nmath.MathFunctions.Function3_2;

// transcribed from qnorm.c

public final class Qnorm implements Function3_2 {

    public static Qnorm create() {
        return new Qnorm();
    }

    public static Qnorm getUncached() {
        return new Qnorm();
    }

    private final BranchProfile nanProfile = BranchProfile.create();

    @Override
    public double evaluate(double p, double mu, double sigma, boolean lowerTail, boolean logP) {
        if (Double.isNaN(p) || Double.isNaN(mu) || Double.isNaN(sigma)) {
            nanProfile.enter();
            return p + mu + sigma;
        }

        try {
            DPQ.rqp01boundaries(p, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, lowerTail, logP);
        } catch (EarlyReturn early) {
            return early.result;
        }

        if (sigma < 0) {
            nanProfile.enter();
            return Double.NaN;
        }
        if (sigma == 0) {
            return mu;
        }

        return qnormImpl(p, mu, sigma, lowerTail, logP);
    }

    /**
     * Static version without arguments validation.
     */
    public static double qnorm(double p, double mu, double sigma, boolean lowerTail, boolean logP) {
        try {
            DPQ.rqp01boundaries(p, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, lowerTail, logP);
        } catch (EarlyReturn early) {
            return early.result;
        }

        return qnormImpl(p, mu, sigma, lowerTail, logP);
    }

    private static double qnormImpl(double p, double mu, double sigma, boolean lowerTail, boolean logP) {

        double p2 = DPQ.rdtqiv(p, lowerTail, logP); /* real lower_tail prob. p */
        double q = p2 - 0.5;

        debugPrintf("qnorm(p=%10.7g, m=%g, s=%g, l.t.= %d, log= %d): q = %g\n", p, mu, sigma, lowerTail, logP, q);

        /*-- use AS 241 --- */
        /* double ppnd16_(double *p, long *ifault) */
        /*
         * ALGORITHM AS241 APPL. STATIST. (1988) VOL. 37, NO. 3
         *
         * Produces the normal deviate Z corresponding to a given lower tail area of P; Z is
         * accurate to about 1 part in 10**16.
         *
         * (original fortran code used PARAMETER(..) for the coefficients and provided hash codes
         * for checking them...)
         */
        double val;
        if (Math.abs(q) <= .425) { /* 0.075 <= p <= 0.925 */
            double r = .180625 - q * q;
            val = q * (((((((r * 2509.0809287301226727 +
                            33430.575583588128105) * r + 67265.770927008700853) * r +
                            45921.953931549871457) * r + 13731.693765509461125) * r +
                            1971.5909503065514427) * r + 133.14166789178437745) * r +
                            3.387132872796366608) /
                            (((((((r * 5226.495278852854561 +
                                            28729.085735721942674) * r + 39307.89580009271061) * r +
                                            21213.794301586595867) * r + 5394.1960214247511077) * r +
                                            687.1870074920579083) * r + 42.313330701600911252) * r + 1.);
        } else { /* closer than 0.075 from {0,1} boundary */

            /* r = min(p, 1-p) < 0.075 */
            double r;
            if (q > 0) {
                r = DPQ.rdtciv(p, lowerTail, logP); /* 1-p */
            } else {
                r = p2; /* = R_DT_Iv(p) ^= p */
            }

            r = Math.sqrt(-((logP &&
                            ((lowerTail && q <= 0) || (!lowerTail && q > 0))) ? p : /* else */Math.log(r)));
            /* r = sqrt(-log(r)) <==> min(p, 1-p) = exp( - r^2 ) */
            debugPrintf("\t close to 0 or 1: r = %7g\n", r);

            if (r <= 5.) { /* <==> min(p,1-p) >= exp(-25) ~= 1.3888e-11 */
                r += -1.6;
                val = (((((((r * 7.7454501427834140764e-4 +
                                .0227238449892691845833) * r + .24178072517745061177) *
                                r + 1.27045825245236838258) * r +
                                3.64784832476320460504) * r + 5.7694972214606914055) *
                                r + 4.6303378461565452959) * r +
                                1.42343711074968357734) /
                                (((((((r *
                                                1.05075007164441684324e-9 + 5.475938084995344946e-4) *
                                                r + .0151986665636164571966) * r +
                                                .14810397642748007459) * r + .68976733498510000455) *
                                                r + 1.6763848301838038494) * r +
                                                2.05319162663775882187) * r + 1.);
            } else { /* very close to 0 or 1 */
                r += -5.;
                val = (((((((r * 2.01033439929228813265e-7 +
                                2.71155556874348757815e-5) * r +
                                .0012426609473880784386) * r + .026532189526576123093) *
                                r + .29656057182850489123) * r +
                                1.7848265399172913358) * r + 5.4637849111641143699) *
                                r + 6.6579046435011037772) /
                                (((((((r *
                                                2.04426310338993978564e-15 + 1.4215117583164458887e-7) *
                                                r + 1.8463183175100546818e-5) * r +
                                                7.868691311456132591e-4) * r + .0148753612908506148525) * r + .13692988092273580531) * r +
                                                .59983220655588793769) * r + 1.);
            }

            if (q < 0.0) {
                val = -val;
                /* return (q >= 0.)? r : -r ; */
            }
        }
        return mu + sigma * val;
    }

    @SuppressWarnings("unused")
    private static void debugPrintf(String format, Object... args) {
        // empty
    }
}
