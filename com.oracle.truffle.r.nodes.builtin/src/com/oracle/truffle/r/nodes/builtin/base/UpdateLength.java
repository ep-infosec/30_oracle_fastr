/*
 * Copyright (c) 2013, 2021, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.truffle.r.nodes.builtin.CastBuilder.Predef.abstractVectorValue;
import static com.oracle.truffle.r.nodes.builtin.CastBuilder.Predef.doubleValue;
import static com.oracle.truffle.r.nodes.builtin.CastBuilder.Predef.integerValue;
import static com.oracle.truffle.r.nodes.builtin.CastBuilder.Predef.singleElement;
import static com.oracle.truffle.r.nodes.builtin.CastBuilder.Predef.stringValue;
import static com.oracle.truffle.r.runtime.RDispatch.INTERNAL_GENERIC;
import static com.oracle.truffle.r.runtime.RError.Message.CANNOT_SET_LENGTH;
import static com.oracle.truffle.r.runtime.RError.Message.INVALID_UNNAMED_VALUE;
import static com.oracle.truffle.r.runtime.RError.Message.LENGTH_OF_NULL_UNCHANGED;
import static com.oracle.truffle.r.runtime.RError.Message.WRONG_LENGTH_ARG;
import static com.oracle.truffle.r.runtime.builtins.RBehavior.PURE;
import static com.oracle.truffle.r.runtime.builtins.RBuiltinKind.PRIMITIVE;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.r.nodes.builtin.RBuiltinNode;
import com.oracle.truffle.r.runtime.builtins.RBuiltin;
import com.oracle.truffle.r.runtime.data.RNull;
import com.oracle.truffle.r.runtime.data.model.RAbstractContainer;
import com.oracle.truffle.r.runtime.data.nodes.ResizeContainer;

@RBuiltin(name = "length<-", kind = PRIMITIVE, parameterNames = {"x", "value"}, dispatch = INTERNAL_GENERIC, behavior = PURE)
public abstract class UpdateLength extends RBuiltinNode.Arg2 {

    static {
        Casts casts = new Casts(UpdateLength.class);
        // Note: `length<-`(NULL, newLen) really works in GnuR unlike other update builtins
        casts.arg("x").allowNull().mustBe(abstractVectorValue(), CANNOT_SET_LENGTH);
        casts.arg("value").defaultError(INVALID_UNNAMED_VALUE).mustBe(integerValue().or(doubleValue()).or(stringValue())).asIntegerVector().mustBe(singleElement(), WRONG_LENGTH_ARG,
                        "value").findFirst();
    }

    @SuppressWarnings("unused")
    @Specialization
    protected RNull updateLength(RNull value, int length) {
        if (length > 0) {
            warning(LENGTH_OF_NULL_UNCHANGED);
        }
        return RNull.instance;
    }

    @Specialization
    protected RAbstractContainer updateLength(RAbstractContainer container, int newLength,
                    @Cached ResizeContainer resizeContainer) {
        return resizeContainer.execute(container, newLength);
    }
}
