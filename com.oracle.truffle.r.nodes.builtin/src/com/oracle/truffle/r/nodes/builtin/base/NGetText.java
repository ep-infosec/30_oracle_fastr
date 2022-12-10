/*
 * Copyright (c) 2014, 2018, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.truffle.r.nodes.builtin.CastBuilder.Predef.gte0;
import static com.oracle.truffle.r.nodes.builtin.CastBuilder.Predef.singleElement;
import static com.oracle.truffle.r.nodes.builtin.CastBuilder.Predef.stringValue;
import static com.oracle.truffle.r.runtime.builtins.RBehavior.COMPLEX;
import static com.oracle.truffle.r.runtime.builtins.RBuiltinKind.INTERNAL;

import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.r.nodes.builtin.RBuiltinNode;
import com.oracle.truffle.r.runtime.RError;
import com.oracle.truffle.r.runtime.builtins.RBuiltin;

@RBuiltin(name = "ngettext", kind = INTERNAL, parameterNames = {"n", "msg1", "msg2", "domain"}, behavior = COMPLEX)
public abstract class NGetText extends RBuiltinNode.Arg4 {

    static {
        Casts casts = new Casts(NGetText.class);
        casts.arg("n").asIntegerVector().findFirst().mustBe(gte0());
        casts.arg("msg1").defaultError(RError.Message.MUST_BE_STRING, "msg1").mustBe(stringValue()).asStringVector().mustBe(singleElement()).findFirst();
        casts.arg("msg2").defaultError(RError.Message.MUST_BE_STRING, "msg2").mustBe(stringValue()).asStringVector().mustBe(singleElement()).findFirst();
    }

    @Specialization()
    protected String getText(int n, String msg1, String msg2, @SuppressWarnings("unused") Object domain) {
        return n == 1 ? msg1 : msg2;
    }
}
