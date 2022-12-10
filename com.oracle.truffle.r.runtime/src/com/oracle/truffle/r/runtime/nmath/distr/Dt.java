/*
 * Copyright (c) 2000--2014, The R Core Team
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
// Acknowledgement from GnuR header:
// Author: Catherine Loader, catherine@research.bell-labs.com, October 23, 2000.
package com.oracle.truffle.r.runtime.nmath.distr;

import static com.oracle.truffle.r.runtime.nmath.MathConstants.DBL_EPSILON;
import static com.oracle.truffle.r.runtime.nmath.MathConstants.M_1_SQRT_2PI;
import static com.oracle.truffle.r.runtime.nmath.MathConstants.M_LN_SQRT_2PI;
import static com.oracle.truffle.r.runtime.nmath.RMath.bd0;
import static com.oracle.truffle.r.runtime.nmath.RMath.stirlerr;

import com.oracle.truffle.r.runtime.nmath.DPQ;
import com.oracle.truffle.r.runtime.nmath.MathFunctions.Function2_1;
import com.oracle.truffle.r.runtime.nmath.RMathError;

public final class Dt implements Function2_1 {

    public static Dt create() {
        return new Dt();
    }

    public static Dt getUncached() {
        return new Dt();
    }

    private static final DNorm dnorm = new DNorm();

    @Override
    public double evaluate(double x, double n, boolean giveLog) {
        if (Double.isNaN(x) || Double.isNaN(n)) {
            return x + n;
        }

        if (n <= 0) {
            return RMathError.defaultError();
        }

        if (!Double.isFinite(x)) {
            return DPQ.rd0(giveLog);
        }
        if (!Double.isFinite(n)) {
            return dnorm.evaluate(x, 0., 1., giveLog);
        }

        double u;
        double t = -bd0(n / 2., (n + 1) / 2.) + stirlerr((n + 1) / 2.) - stirlerr(n / 2.);
        double x2n = x * x / n; // in [0, Inf]
        double ax = 0.; // <- -Wpedantic
        double lx2n; // := Math.log(Math.sqrt(1 + x2n)) = Math.log(1 + x2n)/2
        boolean lrgx2n = (x2n > 1. / DBL_EPSILON);
        if (lrgx2n) { // large x^2/n :
            ax = Math.abs(x);
            lx2n = Math.log(ax) - Math.log(n) / 2.; // = Math.log(x2n)/2 = 1/2 * Math.log(x^2 / n)
            u = // Math.log(1 + x2n) * n/2 = n * Math.log(1 + x2n)/2 =
                            n * lx2n;
        } else if (x2n > 0.2) {
            lx2n = Math.log(1 + x2n) / 2.;
            u = n * lx2n;
        } else {
            lx2n = Math.log1p(x2n) / 2.;
            u = -bd0(n / 2., (n + x * x) / 2.) + x * x / 2.;
        }

        // old: return R_D_fMath.exp(M_2PI*(1+x2n), t-u);

        // R_D_fMath.exp(f,x) := (give_Math.log ? -0.5*Math.log(f)+(x) : Math.exp(x)/Math.sqrt(f))
        // f = 2pi*(1+x2n)
        // ==> 0.5*Math.log(f) = Math.log(2pi)/2 + Math.log(1+x2n)/2 = Math.log(2pi)/2 + l_x2n
        // 1/Math.sqrt(f) = 1/Math.sqrt(2pi * (1+ x^2 / n))
        // = 1/Math.sqrt(2pi)/(|x|/Math.sqrt(n)*Math.sqrt(1+1/x2n))
        // = M_1_SQRT_2PI * Math.sqrt(n)/ (|x|*Math.sqrt(1+1/x2n))
        if (giveLog) {
            return t - u - (M_LN_SQRT_2PI + lx2n);
        }

        // else : if(lrg_x2n) : Math.sqrt(1 + 1/x2n) ='= Math.sqrt(1) = 1
        double tmp = (lrgx2n ? Math.sqrt(n) / ax : Math.exp(-lx2n));
        return Math.exp(t - u) * M_1_SQRT_2PI * tmp;
    }
}
