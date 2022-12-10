/*
 * Copyright (c) 1995, 1996, 1997  Robert Gentleman and Ross Ihaka
 * Copyright (c) 1995-2014, The R Core Team
 * Copyright (c) 2002-2008, The R Foundation
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
package com.oracle.truffle.r.nodes.builtin.base;

import static com.oracle.truffle.r.nodes.builtin.CastBuilder.Predef.nullValue;
import static com.oracle.truffle.r.nodes.builtin.CastBuilder.Predef.stringValue;
import static com.oracle.truffle.r.runtime.builtins.RBehavior.PURE;
import static com.oracle.truffle.r.runtime.builtins.RBuiltinKind.INTERNAL;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.profiles.ConditionProfile;
import com.oracle.truffle.r.nodes.builtin.RBuiltinNode;
import com.oracle.truffle.r.nodes.builtin.base.ToLowerOrUpper.Mapper;
import com.oracle.truffle.r.nodes.builtin.base.ToLowerOrUpper.StringMapNode;
import com.oracle.truffle.r.runtime.RError;
import com.oracle.truffle.r.runtime.RError.Message;
import com.oracle.truffle.r.runtime.RRuntime;
import com.oracle.truffle.r.runtime.builtins.RBuiltin;
import com.oracle.truffle.r.runtime.data.RIntVector;
import com.oracle.truffle.r.runtime.data.RStringVector;
import com.oracle.truffle.r.runtime.data.VectorDataLibrary;

@RBuiltin(name = "strtrim", kind = INTERNAL, parameterNames = {"x", "width"}, behavior = PURE)
public abstract class Strtrim extends RBuiltinNode.Arg2 {

    static {
        Casts casts = new Casts(Strtrim.class);
        casts.arg("x").defaultError(Message.REQUIRES_CHAR_VECTOR, "strtrim()").mustBe(stringValue()).asStringVector(true, true, true);
        casts.arg("width").mustNotBeMissing().mustBe(nullValue().not()).asIntegerVector();
    }

    @Specialization(limit = "getTypedVectorDataLibraryCacheSize()")
    protected RStringVector srtrim(RStringVector x, RIntVector width,
                    @Cached("create()") StringMapNode mapNode,
                    @Cached("createBinaryProfile()") ConditionProfile fitsProfile,
                    @CachedLibrary("x.getData()") VectorDataLibrary xDataLib) {
        int len = xDataLib.getLength(x.getData());
        int nw = width.getLength();
        if (nw == 0 || nw < len && (len % nw != 0)) {
            CompilerDirectives.transferToInterpreter();
            throw error(RError.Message.INVALID_ARGUMENT, "width");
        }
        for (int i = 0; i < nw; i++) {
            assert RRuntime.INT_NA < 0; // check for NA folded into < 0
            if (width.getDataAt(i) < 0) {
                CompilerDirectives.transferToInterpreter();
                throw error(RError.Message.INVALID_ARGUMENT, "width");
            }
        }
        Mapper function = (element, i) -> {
            // TODO multibyte character handling
            int w = width.getDataAt(i % nw);
            if (fitsProfile.profile(w >= element.length())) {
                return element;
            } else {
                return substring(element, w);
            }
        };
        return mapNode.apply(x, xDataLib, function);
    }

    @TruffleBoundary
    private static String substring(String element, int w) {
        return element.substring(0, w);
    }
}
