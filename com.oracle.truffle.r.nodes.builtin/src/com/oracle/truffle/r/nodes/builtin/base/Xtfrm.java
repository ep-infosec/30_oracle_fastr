/*
 * Copyright (c) 2015, 2022, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.truffle.r.runtime.RDispatch.INTERNAL_GENERIC;
import static com.oracle.truffle.r.runtime.builtins.RBehavior.COMPLEX;
import static com.oracle.truffle.r.runtime.builtins.RBuiltinKind.PRIMITIVE;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.profiles.ConditionProfile;
import com.oracle.truffle.r.nodes.builtin.RBuiltinNode;
import com.oracle.truffle.r.nodes.builtin.base.GetFunctionsFactory.GetNodeGen;
import com.oracle.truffle.r.runtime.RArguments;
import com.oracle.truffle.r.runtime.RType;
import com.oracle.truffle.r.runtime.builtins.RBuiltin;
import com.oracle.truffle.r.runtime.data.RFunction;
import com.oracle.truffle.r.runtime.env.REnvironment;

@RBuiltin(name = "xtfrm", kind = PRIMITIVE, parameterNames = {"x"}, dispatch = INTERNAL_GENERIC, behavior = COMPLEX)
public abstract class Xtfrm extends RBuiltinNode.Arg1 {

    @Child private GetFunctions.Get getNode;

    static {
        Casts.noCasts(Xtfrm.class);
    }

    @Specialization
    protected Object xtfrm(VirtualFrame frame, Object x,
                    @Cached("createBinaryProfile()") ConditionProfile createProfile) {
        /*
         * Although this is a PRIMITIVE, there is an xtfrm.default that we must call if "x" is not
         * of a class that already has an xtfrm.class function defined. We only get here in the
         * default case.
         */
        if (getNode == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            getNode = insert(GetNodeGen.create());
        }

        REnvironment env = RArguments.getEnvironment(frame);
        if (createProfile.profile(env == null)) {
            env = REnvironment.frameToEnvironment(frame.materialize());
        }
        RFunction func = (RFunction) getNode.execute(frame, "xtfrm.default", env, RType.Function.getName(), true);
        return getRContext().getThisEngine().evalFunction(func, null, null, true, null, x);
    }
}
