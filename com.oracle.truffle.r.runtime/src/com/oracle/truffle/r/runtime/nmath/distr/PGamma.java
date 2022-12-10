/*
 * Copyright (C) 2005-6 Morten Welinder <terra@gnome.org>
 * Copyright (C) 2005-10 The R Foundation
 * Copyright (C) 2006-2015 The R Core Team
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

import static com.oracle.truffle.r.runtime.nmath.GammaFunctions.pgammaRaw;

import com.oracle.truffle.r.runtime.nmath.DPQ;
import com.oracle.truffle.r.runtime.nmath.MathFunctions.Function3_2;
import com.oracle.truffle.r.runtime.nmath.RMathError;

public final class PGamma implements Function3_2 {

    public static PGamma create() {
        return new PGamma();
    }

    public static PGamma getUncached() {
        return new PGamma();
    }

    @Override
    public double evaluate(double xIn, double alph, double scale, boolean lowerTail, boolean logP) {
        if (Double.isNaN(xIn) || Double.isNaN(alph) || Double.isNaN(scale)) {
            return xIn + alph + scale;
        }
        if (alph < 0 || scale < 0) {
            return RMathError.defaultError();
        }

        double x = xIn / scale;
        if (Double.isNaN(x)) {
            return x;
        }
        if (alph == 0.) {
            return x <= 0 ? DPQ.rdt0(lowerTail, logP) : DPQ.rdt1(lowerTail, logP);
        }
        return pgammaRaw(x, alph, lowerTail, logP);
    }
}
