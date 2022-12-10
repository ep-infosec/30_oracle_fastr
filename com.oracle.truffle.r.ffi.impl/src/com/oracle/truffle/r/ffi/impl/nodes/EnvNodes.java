/*
 * Copyright (c) 2017, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.r.ffi.impl.nodes;

import static com.oracle.truffle.r.ffi.impl.common.RFFIUtils.guaranteeInstanceOf;

import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.TypeSystemReference;
import com.oracle.truffle.r.ffi.impl.nodes.EnvNodesFactory.LockBindingNodeGen;
import com.oracle.truffle.r.ffi.impl.nodes.EnvNodesFactory.UnlockBindingNodeGen;
import com.oracle.truffle.r.runtime.RInternalError;
import com.oracle.truffle.r.runtime.data.RSymbol;
import com.oracle.truffle.r.runtime.data.RTypes;
import com.oracle.truffle.r.runtime.env.REnvironment;

@GenerateUncached
public class EnvNodes {

    @TypeSystemReference(RTypes.class)
    @GenerateUncached
    public abstract static class LockBindingNode extends FFIUpCallNode.Arg2 {

        @Specialization
        Void lock(RSymbol sym, REnvironment env) {
            // TODO copied from EnvFunctions.LockBinding
            env.lockBinding(sym.getName());
            return null;
        }

        @Fallback
        Void lock(Object sym, Object env) {
            guaranteeInstanceOf(sym, RSymbol.class);
            guaranteeInstanceOf(env, REnvironment.class);
            throw RInternalError.shouldNotReachHere();
        }

        public static LockBindingNode create() {
            return LockBindingNodeGen.create();
        }

        public static LockBindingNode getUncached() {
            return LockBindingNodeGen.getUncached();
        }
    }

    @TypeSystemReference(RTypes.class)
    @GenerateUncached
    public abstract static class UnlockBindingNode extends FFIUpCallNode.Arg2 {

        @Specialization
        Void unlock(RSymbol sym, REnvironment env) {
            // TODO copied from EnvFunctions.LockBinding
            env.unlockBinding(sym.getName());
            return null;
        }

        @Fallback
        Void unlock(Object sym, Object env) {
            guaranteeInstanceOf(sym, RSymbol.class);
            guaranteeInstanceOf(env, REnvironment.class);
            throw RInternalError.shouldNotReachHere();
        }

        public static UnlockBindingNode create() {
            return UnlockBindingNodeGen.create();
        }

        public static UnlockBindingNode getUncached() {
            return UnlockBindingNodeGen.getUncached();
        }
    }
}
