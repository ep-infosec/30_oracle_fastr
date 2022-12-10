/*
 * Copyright (c) 2013, 2022, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.truffle.r.nodes.builtin.CastBuilder.Predef.instanceOf;
import static com.oracle.truffle.r.nodes.builtin.CastBuilder.Predef.stringValue;
import static com.oracle.truffle.r.runtime.builtins.RBehavior.MODIFIES_STATE;
import static com.oracle.truffle.r.runtime.builtins.RBehavior.PURE;
import static com.oracle.truffle.r.runtime.builtins.RBehavior.READS_STATE;
import static com.oracle.truffle.r.runtime.builtins.RBuiltinKind.INTERNAL;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.r.nodes.builtin.NodeWithArgumentCasts.Casts;
import com.oracle.truffle.r.nodes.builtin.RBuiltinNode;
import com.oracle.truffle.r.runtime.RError;
import com.oracle.truffle.r.runtime.RRuntime;
import com.oracle.truffle.r.runtime.builtins.RBuiltin;
import com.oracle.truffle.r.runtime.data.RNull;
import com.oracle.truffle.r.runtime.data.RStringVector;
import com.oracle.truffle.r.runtime.data.RSymbol;
import com.oracle.truffle.r.runtime.env.REnvironment;

public class NamespaceFunctions {

    private static final class CastsHelper {
        private static void name(Casts casts) {
            casts.arg("name").mustBe(stringValue().or(instanceOf(RSymbol.class)));
        }
    }

    @RBuiltin(name = "getRegisteredNamespace", kind = INTERNAL, parameterNames = {"name"}, behavior = READS_STATE)
    public abstract static class GetRegisteredNamespace extends RBuiltinNode.Arg1 {

        static {
            Casts casts = new Casts(GetRegisteredNamespace.class);
            CastsHelper.name(casts);
        }

        @Specialization
        protected Object doGetRegisteredNamespace(RStringVector name) {
            Object result = REnvironment.getRegisteredNamespace(getRContext(), name.getDataAt(0));
            if (result == null) {
                return RNull.instance;
            } else {
                return result;
            }
        }

        @Specialization
        protected Object doGetRegisteredNamespace(RSymbol name) {
            Object result = REnvironment.getRegisteredNamespace(getRContext(), name.getName());
            if (result == null) {
                return RNull.instance;
            } else {
                return result;
            }
        }
    }

    @RBuiltin(name = "isRegisteredNamespace", kind = INTERNAL, parameterNames = {"name"}, behavior = READS_STATE)
    public abstract static class IsRegisteredNamespace extends RBuiltinNode.Arg1 {

        static {
            Casts casts = new Casts(IsRegisteredNamespace.class);
            CastsHelper.name(casts);
        }

        @Specialization
        protected byte doIsRegisteredNamespace(RStringVector name) {
            Object result = REnvironment.getRegisteredNamespace(getRContext(), name.getDataAt(0));
            if (result == null) {
                return RRuntime.LOGICAL_FALSE;
            } else {
                return RRuntime.LOGICAL_TRUE;
            }
        }

        @Specialization
        protected Object doIsRegisteredNamespace(RSymbol name) {
            Object result = REnvironment.getRegisteredNamespace(getRContext(), name.getName());
            if (result == null) {
                return RRuntime.LOGICAL_FALSE;
            } else {
                return RRuntime.LOGICAL_TRUE;
            }
        }
    }

    @RBuiltin(name = "isNamespaceEnv", kind = INTERNAL, parameterNames = {"env"}, behavior = PURE)
    public abstract static class IsNamespaceEnv extends RBuiltinNode.Arg1 {

        static {
            Casts.noCasts(IsNamespaceEnv.class);
        }

        @Specialization
        @TruffleBoundary
        protected byte doIsNamespaceEnv(REnvironment env) {
            return RRuntime.asLogical(env.isNamespaceEnv());
        }

        @Fallback
        protected byte doIsNamespaceEnv(@SuppressWarnings("unused") Object env) {
            return RRuntime.LOGICAL_FALSE;
        }
    }

    @RBuiltin(name = "getNamespaceRegistry", kind = INTERNAL, parameterNames = {}, behavior = READS_STATE)
    public abstract static class GetNamespaceRegistry extends RBuiltinNode.Arg0 {
        @Specialization
        protected REnvironment doGetNamespaceRegistry() {
            return REnvironment.getNamespaceRegistry(getRContext());
        }
    }

    @RBuiltin(name = "registerNamespace", kind = INTERNAL, parameterNames = {"name", "env"}, behavior = MODIFIES_STATE)
    public abstract static class RegisterNamespace extends RBuiltinNode.Arg2 {

        static {
            Casts casts = new Casts(RegisterNamespace.class);
            CastsHelper.name(casts);
            casts.arg("env").mustBe(instanceOf(REnvironment.class));
        }

        @Specialization
        protected RNull registerNamespace(RStringVector name, REnvironment env) {
            if (REnvironment.registerNamespace(name.getDataAt(0), env, getRContext()) == null) {
                throw error(RError.Message.NS_ALREADY_REG);
            }
            return RNull.instance;
        }

        @Specialization
        protected RNull registerNamespace(RSymbol nameSym, REnvironment env) {
            if (REnvironment.registerNamespace(nameSym.getName(), env, getRContext()) == null) {
                throw error(RError.Message.NS_ALREADY_REG);
            }
            return RNull.instance;

        }
    }

    @RBuiltin(name = "unregisterNamespace", kind = INTERNAL, parameterNames = {"name"}, behavior = MODIFIES_STATE)
    public abstract static class UnregisterNamespace extends RBuiltinNode.Arg1 {

        static {
            Casts casts = new Casts(UnregisterNamespace.class);
            CastsHelper.name(casts);
        }

        @Specialization
        protected RNull unregisterNamespace(RStringVector name) {
            doUnregisterNamespace(name.getDataAt(0));
            return RNull.instance;
        }

        @Specialization
        protected Object unregisterNamespace(RSymbol name) {
            doUnregisterNamespace(name.getName());
            return RNull.instance;
        }

        @TruffleBoundary
        private void doUnregisterNamespace(String name) {
            Object ns = REnvironment.unregisterNamespace(name);
            if (ns == null) {
                throw error(RError.Message.NS_NOTREG);
            }
        }
    }
}
