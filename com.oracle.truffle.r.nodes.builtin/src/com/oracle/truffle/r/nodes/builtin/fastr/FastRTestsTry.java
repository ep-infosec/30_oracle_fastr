/*
 * Copyright (c) 2016, 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.r.nodes.builtin.fastr;

import static com.oracle.truffle.r.runtime.builtins.RBehavior.COMPLEX;
import static com.oracle.truffle.r.runtime.builtins.RBuiltinKind.PRIMITIVE;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.r.nodes.builtin.RBuiltinNode;
import com.oracle.truffle.r.nodes.function.call.RExplicitCallNode;
import com.oracle.truffle.r.runtime.RErrorHandling;
import com.oracle.truffle.r.runtime.RRuntime;
import com.oracle.truffle.r.runtime.builtins.RBuiltin;
import com.oracle.truffle.r.runtime.data.RArgsValuesAndNames;
import com.oracle.truffle.r.runtime.data.RFunction;

/**
 * Allows to be 100% robust even in the case of FastR errors like runtime exceptions. The argument
 * must be a single parameter-less function. The return value is true on success, otherwise error
 * message.
 * <p>
 * <b>WARNING: For use in tests only! </b><br>
 * There is no guarantee that after an error the internal FastR state will stay consistent.
 * </p>
 */
@RBuiltin(name = ".fastr.tests.try", kind = PRIMITIVE, parameterNames = {""}, behavior = COMPLEX)
public abstract class FastRTestsTry extends RBuiltinNode.Arg1 {
    @Child private RExplicitCallNode call = RExplicitCallNode.create();

    static {
        Casts.noCasts(FastRTestsTry.class);
    }

    @Specialization
    public Object tryFunc(VirtualFrame frame, RFunction func) {
        try {
            call.call(frame, func, RArgsValuesAndNames.EMPTY);
        } catch (Throwable ex) {
            // try to recover from a possibly incosistent state when running tests:
            // some handlers might still be lying around and interfere with subsequent calls
            RErrorHandling.resetStacks(getRContext());
            return formatError(ex);
        }
        return RRuntime.LOGICAL_TRUE;
    }

    @TruffleBoundary
    private static String formatError(Throwable ex) {
        return String.format("Exception %s, message: %s", ex.getClass().getSimpleName(), ex.getMessage());
    }
}
