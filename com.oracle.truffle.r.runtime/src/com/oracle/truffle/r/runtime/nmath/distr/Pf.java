/*
 * Copyright (C) 1998 Ross Ihaka
 * Copyright (c) 1998--2008, The R Core Team
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

import static com.oracle.truffle.r.runtime.nmath.DPQ.rdt0;
import static com.oracle.truffle.r.runtime.nmath.DPQ.rdt1;

import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.r.runtime.nmath.GammaFunctions;
import com.oracle.truffle.r.runtime.nmath.MathConstants;
import com.oracle.truffle.r.runtime.nmath.MathFunctions.Function3_2;

// transcribed from nmath/pf.c
public final class Pf implements Function3_2 {

    public static Pf create() {
        return new Pf();
    }

    public static Pf getUncached() {
        return new Pf();
    }

    private final BranchProfile nanProfile = BranchProfile.create();

    @Override
    public double evaluate(double x, double df1, double df2, boolean lowerTail, boolean logP) {
        if (Double.isNaN(x) || Double.isNaN(df1) || Double.isNaN(df2)) {
            return x + df2 + df1;
        }

        if (df1 <= 0 || df2 <= 0) {
            // TODO ML_ERR_return_NAN
            return Double.NaN;
        }

        // expansion of R_P_bounds_01(x, 0., ML_POSINF);
        if (x <= 0) {
            return rdt0(lowerTail, logP);
        }
        if (x >= Double.POSITIVE_INFINITY) {
            return rdt1(lowerTail, logP);
        }

        if (df2 == Double.POSITIVE_INFINITY) {
            if (df1 == Double.POSITIVE_INFINITY) {
                if (x < 1) {
                    return rdt0(lowerTail, logP);
                }
                if (x == 1) {
                    return logP ? -MathConstants.M_LN2 : 0.5;
                }
                if (x > 1) {
                    return rdt1(lowerTail, logP);
                }
            }

            return GammaFunctions.pgamma(x * df1, df1 / 2, 2, lowerTail, logP);
        }

        if (df1 == Double.POSITIVE_INFINITY) {
            return GammaFunctions.pgamma(df2 / x, df2 / 2, 2, !lowerTail, logP);
        }

        double ret;
        if (df1 * x > df2) {
            ret = Pbeta.pbeta(df2 / (df2 + df1 * x), df2 / 2, df1 / 2, !lowerTail, logP, nanProfile);
        } else {
            ret = Pbeta.pbeta(df1 * x / (df2 + df1 * x), df1 / 2, df2 / 2, lowerTail, logP, nanProfile);
        }

        return ret;

    }
}
