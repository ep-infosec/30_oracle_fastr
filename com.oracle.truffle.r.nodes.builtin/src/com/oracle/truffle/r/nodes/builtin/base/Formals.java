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

import static com.oracle.truffle.r.runtime.builtins.RBehavior.PURE;
import static com.oracle.truffle.r.runtime.builtins.RBuiltinKind.INTERNAL;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.r.nodes.RASTUtils;
import com.oracle.truffle.r.nodes.builtin.RBuiltinNode;
import com.oracle.truffle.r.runtime.RError;
import com.oracle.truffle.r.runtime.builtins.RBuiltin;
import com.oracle.truffle.r.runtime.data.RFunction;
import com.oracle.truffle.r.runtime.data.RNull;

@ImportStatic(RASTUtils.class)
@RBuiltin(name = "formals", kind = INTERNAL, parameterNames = {"fun"}, behavior = PURE)
public abstract class Formals extends RBuiltinNode.Arg1 {

    static {
        Casts casts = new Casts(Formals.class);
        casts.arg("fun").shouldBe(RFunction.class, RError.Message.ARGUMENT_NOT_FUNCTION);
    }

    @SuppressWarnings("unused")
    @Specialization(limit = "getCacheSize(3)", guards = "fun == cachedFunction")
    protected Object formalsCached(RFunction fun,
                    @Cached("fun") RFunction cachedFunction,
                    @Cached("createFormals(fun)") Object formals) {
        return formals;
    }

    @Specialization(replaces = "formalsCached")
    protected Object formals(RFunction fun) {
        return RASTUtils.createFormals(fun);
    }

    @Fallback
    protected Object formals(@SuppressWarnings("unused") Object fun) {
        // for anything that is not a function, GnuR returns NULL
        return RNull.instance;
    }
}
