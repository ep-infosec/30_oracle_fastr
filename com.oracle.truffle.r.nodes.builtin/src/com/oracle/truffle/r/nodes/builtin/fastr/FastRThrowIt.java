/*
 * Copyright (c) 2015, 2018, Oracle and/or its affiliates. All rights reserved.
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
import com.oracle.truffle.r.nodes.builtin.RBuiltinNode;
import com.oracle.truffle.r.runtime.JumpToTopLevelException;
import com.oracle.truffle.r.runtime.RError;
import com.oracle.truffle.r.runtime.RInternalError;
import com.oracle.truffle.r.runtime.Utils;
import com.oracle.truffle.r.runtime.builtins.RBuiltin;
import com.oracle.truffle.r.runtime.data.RNull;

@RBuiltin(name = ".fastr.throw", kind = PRIMITIVE, parameterNames = {"name"}, behavior = COMPLEX)
public abstract class FastRThrowIt extends RBuiltinNode.Arg1 {

    static {
        Casts casts = new Casts(FastRThrowIt.class);
        casts.arg("name").asStringVector().findFirst();
    }

    @Specialization
    @TruffleBoundary
    protected RNull throwit(String name) {
        switch (name) {
            case "AIX":
                throw new ArrayIndexOutOfBoundsException();
            case "NPE":
                throw new NullPointerException();
            case "ASE":
                throw new AssertionError();
            case "RTE":
                throw new RuntimeException();
            case "RINT":
                throw RInternalError.shouldNotReachHere();
            case "DBE":
                throw new Utils.DebugExitException();
            case "Q":
            case "BRQ":
                throw new JumpToTopLevelException();
            default:
                throw error(RError.Message.GENERIC, "unknown case: " + name);
        }
    }
}
