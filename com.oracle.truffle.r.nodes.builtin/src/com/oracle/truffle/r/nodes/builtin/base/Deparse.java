/*
 * Copyright (c) 1995-2012, The R Core Team
 * Copyright (c) 2003, The R Foundation
 * Copyright (c) 2014, 2018, Oracle and/or its affiliates
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
package com.oracle.truffle.r.nodes.builtin.base;

import static com.oracle.truffle.r.nodes.builtin.CastBuilder.Predef.toBoolean;
import static com.oracle.truffle.r.runtime.builtins.RBehavior.PURE;
import static com.oracle.truffle.r.runtime.builtins.RBuiltinKind.INTERNAL;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.r.nodes.builtin.RBuiltinNode;
import com.oracle.truffle.r.runtime.RDeparse;
import com.oracle.truffle.r.runtime.RError;
import com.oracle.truffle.r.runtime.RRuntime;
import com.oracle.truffle.r.runtime.builtins.RBuiltin;
import com.oracle.truffle.r.runtime.data.RDataFactory;
import com.oracle.truffle.r.runtime.data.RStringVector;

// Part of this transcribed from GnuR src/main/deparse.c

@RBuiltin(name = "deparse", kind = INTERNAL, parameterNames = {"expr", "width.cutoff", "backtick", "control", "nlines"}, behavior = PURE)
public abstract class Deparse extends RBuiltinNode.Arg5 {

    static {
        Casts casts = new Casts(Deparse.class);
        casts.arg("width.cutoff").asIntegerVector().findFirst(0);
        casts.arg("backtick").asLogicalVector().findFirst(RRuntime.LOGICAL_TRUE).map(toBoolean());
        casts.arg("control").asIntegerVector().findFirst();
        casts.arg("nlines").asIntegerVector().findFirst(-1);
    }

    @Specialization
    @TruffleBoundary
    protected RStringVector deparse(Object expr, int widthCutoffArg, boolean backtick, int control, int nlines) {
        int widthCutoff = widthCutoffArg;
        if (widthCutoff == RRuntime.INT_NA || widthCutoff < RDeparse.MIN_CUTOFF || widthCutoff > RDeparse.MAX_CUTOFF) {
            warning(RError.Message.DEPARSE_INVALID_CUTOFF);
            widthCutoff = RDeparse.DEFAULT_CUTOFF;
        }

        String[] data = RDeparse.deparse(expr, widthCutoff, backtick, control, nlines).split("\n");
        return RDataFactory.createStringVector(data, RDataFactory.COMPLETE_VECTOR);
    }
}
