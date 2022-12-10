/*
 * Copyright (c) 2014, 2020, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 3 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 3 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 3 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package com.oracle.truffle.r.nodes.builtin.base;

import static com.oracle.truffle.r.nodes.builtin.CastBuilder.Predef.singleElement;
import static com.oracle.truffle.r.runtime.builtins.RBehavior.PURE;
import static com.oracle.truffle.r.runtime.builtins.RBuiltinKind.INTERNAL;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.r.runtime.data.nodes.attributes.TypeFromModeNode;
import com.oracle.truffle.r.runtime.data.nodes.attributes.TypeFromModeNodeGen;
import com.oracle.truffle.r.nodes.builtin.RBuiltinNode;
import com.oracle.truffle.r.runtime.DSLConfig;
import com.oracle.truffle.r.runtime.RError;
import com.oracle.truffle.r.runtime.RType;
import com.oracle.truffle.r.runtime.builtins.RBuiltin;
import com.oracle.truffle.r.runtime.data.RDataFactory;
import com.oracle.truffle.r.runtime.gnur.SEXPTYPE;

@ImportStatic(DSLConfig.class)
@RBuiltin(name = "vector", kind = INTERNAL, parameterNames = {"mode", "length"}, behavior = PURE)
public abstract class Vector extends RBuiltinNode.Arg2 {

    protected static final int CACHED_MODES_LIMIT = 3;

    @Child private TypeFromModeNode typeFromMode = TypeFromModeNodeGen.create();

    static {
        Casts casts = new Casts(Vector.class);
        casts.arg("mode").defaultError(RError.Message.INVALID_ARGUMENT, "mode").asStringVector().mustBe(singleElement()).findFirst();
        casts.arg("length").defaultError(RError.Message.INVALID_ARGUMENT, "length").asIntegerVector().mustBe(singleElement()).findFirst();
    }

    protected RType modeToType(String mode) {
        RType type = typeFromMode.execute(mode);
        if (type != RType.PairList && !type.isVector()) {
            throw error(RError.Message.CANNOT_MAKE_VECTOR_OF_MODE, mode);
        }
        return type;
    }

    @Specialization(guards = {"mode == cachedMode"}, limit = "getCacheSize(CACHED_MODES_LIMIT)")
    Object vectorCached(@SuppressWarnings("unused") String mode, int length,
                    @SuppressWarnings("unused") @Cached("mode") String cachedMode,
                    @Cached("modeToType(mode)") RType type) {
        return createType(type, length);
    }

    @Specialization(replaces = "vectorCached")
    @TruffleBoundary
    protected Object vector(String mode, int length) {
        return createType(modeToType(mode), length);
    }

    // Note: we have to handle RPairList separately. In other circumstances it is not seen as a
    // vector, e.g. is.vector(vector('pairlist',1)) is FALSE, so we cannot just turn it into
    // RAbstractVector. Note2: pair list of size == 0 is RNull -> we have to return Object.
    private static Object createType(RType type, int length) {
        if (type == RType.PairList) {
            return RDataFactory.createPairList(length, SEXPTYPE.LISTSXP);
        }
        return type.create(length, false);
    }
}
