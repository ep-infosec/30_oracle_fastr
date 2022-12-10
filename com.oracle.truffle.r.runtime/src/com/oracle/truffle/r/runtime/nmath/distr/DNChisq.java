/*
 * Copyright (C) 1998 Ross Ihaka
 * Copyright (c) 2000-15, The R Core Team
 * Copyright (c) 2004-15, The R Foundation
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

import static com.oracle.truffle.r.runtime.nmath.distr.DPois.dpoisRaw;

import com.oracle.truffle.r.runtime.nmath.DPQ;
import com.oracle.truffle.r.runtime.nmath.MathFunctions.Function3_1;
import com.oracle.truffle.r.runtime.nmath.RMathError;
import com.oracle.truffle.r.runtime.nmath.distr.Chisq.DChisq;

public final class DNChisq implements Function3_1 {

    public static DNChisq create() {
        return new DNChisq();
    }

    public static DNChisq getUncached() {
        return new DNChisq();
    }

    private static final double eps = 5e-15;
    private final DChisq dchisq = new DChisq();

    @Override
    public double evaluate(double x, double df, double ncp, boolean giveLog) {
        if (Double.isNaN(x) || Double.isNaN(df) || Double.isNaN(ncp)) {
            return x + df + ncp;
        }

        if (!Double.isFinite(df) || !Double.isFinite(ncp) || ncp < 0 || df < 0) {
            return RMathError.defaultError();
        }

        if (x < 0) {
            return DPQ.rd0(giveLog);
        }
        if (x == 0 && df < 2.) {
            return Double.POSITIVE_INFINITY;
        }
        if (ncp == 0) {
            return (df > 0) ? dchisq.evaluate(x, df, giveLog) : DPQ.rd0(giveLog);
        }
        if (x == Double.POSITIVE_INFINITY) {
            return DPQ.rd0(giveLog);
        }

        double ncp2 = 0.5 * ncp;

        /* find max element of sum */
        double imax = Math.ceil((-(2 + df) + Math.sqrt((2 - df) * (2 - df) + 4 * ncp * x)) / 4);
        double mid;
        double dfmid = 0;   // Note: not initialized in GnuR
        if (imax < 0) {
            imax = 0;
        }
        if (Double.isFinite(imax)) {
            dfmid = df + 2 * imax;
            mid = dpoisRaw(imax, ncp2, false) * dchisq.evaluate(x, dfmid, false);
        } else {
            /* imax = Inf */
            mid = 0;
        }

        if (mid == 0) {
            /*
             * underflow to 0 -- maybe numerically correct; maybe can be more accurate, particularly
             * when giveLog = true
             */
            /*
             * Use central-chisq approximation formula when appropriate; ((FIXME: the optimal cutoff
             * also depends on (x,df); use always here? ))
             */
            if (giveLog || ncp > 1000.) {
                /* = "1/(1+b)" Abramowitz & St. */
                double nl = df + ncp;
                double ic = nl / (nl + ncp);
                return dchisq.evaluate(x * ic, nl * ic, giveLog);
            } else {
                return DPQ.rd0(giveLog);
            }
        }

        /* errorbound := term * q / (1-q) now subsumed in while() / if () below: */

        /* upper tail */
        /* LDOUBLE */double sum = mid;
        /* LDOUBLE */double term = mid;
        double df2 = dfmid;
        double i = imax;
        double x2 = x * ncp2;
        double q;
        do {
            i++;
            q = x2 / i / df2;
            df2 += 2;
            term *= q;
            sum += term;
        } while (q >= 1 || term * q > (1 - q) * eps || term > 1e-10 * sum);
        /* lower tail */
        term = mid;
        df2 = dfmid;
        i = imax;
        while (i != 0) {
            df2 -= 2;
            q = i * df2 / x2;
            i--;
            term *= q;
            sum += term;
            if (q < 1 && term * q <= (1 - q) * eps) {
                break;
            }
        }
        return DPQ.rdval(sum, giveLog);
    }
}
