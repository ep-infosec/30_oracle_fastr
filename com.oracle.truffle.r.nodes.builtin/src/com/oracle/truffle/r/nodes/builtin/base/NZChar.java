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

import static com.oracle.truffle.r.nodes.builtin.CastBuilder.Predef.toBoolean;
import static com.oracle.truffle.r.runtime.builtins.RBehavior.PURE;
import static com.oracle.truffle.r.runtime.builtins.RBuiltinKind.PRIMITIVE;

import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.r.nodes.builtin.RBuiltinNode;
import com.oracle.truffle.r.runtime.RError;
import com.oracle.truffle.r.runtime.RRuntime;
import com.oracle.truffle.r.runtime.builtins.RBuiltin;
import com.oracle.truffle.r.runtime.data.RDataFactory;
import com.oracle.truffle.r.runtime.data.RLogicalVector;
import com.oracle.truffle.r.runtime.data.RMissing;
import com.oracle.truffle.r.runtime.data.RNull;
import com.oracle.truffle.r.runtime.data.RStringVector;

@RBuiltin(name = "nzchar", kind = PRIMITIVE, parameterNames = {"x", "keepNA"}, behavior = PURE)
public abstract class NZChar extends RBuiltinNode.Arg2 {

    static {
        Casts casts = new Casts(NZChar.class);
        casts.arg("x").asStringVector();
        casts.arg("keepNA").asLogicalVector().findFirst(RRuntime.LOGICAL_FALSE).map(toBoolean(false));
    }

    private static byte isNonZeroLength(String s) {
        return s.length() > 0 ? RRuntime.LOGICAL_TRUE : RRuntime.LOGICAL_FALSE;
    }

    @Specialization
    protected RLogicalVector rev(@SuppressWarnings("unused") RNull value, @SuppressWarnings("unused") boolean keepNA) {
        return RDataFactory.createEmptyLogicalVector();
    }

    @Specialization
    protected RLogicalVector rev(RStringVector vector, boolean keepNA) {
        int len = vector.getLength();
        byte[] result = new byte[len];
        boolean hasNA = false;
        for (int i = 0; i < len; i++) {
            if (keepNA && RRuntime.isNA(vector.getDataAt(i))) {
                result[i] = RRuntime.LOGICAL_NA;
                hasNA = true;
            } else {
                result[i] = isNonZeroLength(vector.getDataAt(i));
            }
        }
        return RDataFactory.createLogicalVector(result, /* complete: */ keepNA && !hasNA);
    }

    @Specialization
    protected RLogicalVector rev(@SuppressWarnings("unused") RMissing value, @SuppressWarnings("unused") boolean keepNA) {
        throw RError.error(this, RError.Message.ARGUMENT_NOT_MATCH, "keepNA", "x");
    }
}
