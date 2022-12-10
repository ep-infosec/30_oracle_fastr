/*
 * Copyright (c) 2014, 2019, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.r.runtime.DSLConfig;
import com.oracle.truffle.r.runtime.RError;
import com.oracle.truffle.r.runtime.RError.Message;
import com.oracle.truffle.r.runtime.Utils;

@ImportStatic(DSLConfig.class)
public abstract class FFIUpCallNode extends Node {
    protected abstract int numArgs();

    @TruffleBoundary
    protected static RError unsupportedTypes(String name, Object... args) {
        assert args.length > 0;
        StringBuilder sb = new StringBuilder(args.length * 15);
        sb.append("wrong argument types provided to ").append(name).append(": ").append(Utils.getTypeName(args[0]));
        for (int i = 1; i < args.length; i++) {
            sb.append(',').append(Utils.getTypeName(args[i]));
        }
        throw RError.error(RError.NO_CALLER, Message.GENERIC, sb);
    }

    public abstract static class Arg0 extends FFIUpCallNode {
        public abstract Object executeObject();

        @Override
        protected int numArgs() {
            return 0;
        }
    }

    public abstract static class Arg1 extends FFIUpCallNode {
        public abstract Object executeObject(Object arg0);

        @Override
        protected int numArgs() {
            return 1;
        }
    }

    public abstract static class Arg2 extends FFIUpCallNode {
        public abstract Object executeObject(Object arg0, Object arg1);

        @Override
        protected int numArgs() {
            return 2;
        }
    }

    public abstract static class Arg3 extends FFIUpCallNode {
        public abstract Object executeObject(Object arg0, Object arg1, Object arg2);

        @Override
        protected int numArgs() {
            return 3;
        }
    }

    public abstract static class Arg4 extends FFIUpCallNode {
        public abstract Object executeObject(Object arg0, Object arg1, Object arg2, Object arg3);

        @Override
        protected int numArgs() {
            return 4;
        }
    }

    public abstract static class Arg5 extends FFIUpCallNode {
        public abstract Object executeObject(Object arg0, Object arg1, Object arg2, Object arg3, Object arg4);

        @Override
        protected int numArgs() {
            return 5;
        }
    }

    public abstract static class Arg6 extends FFIUpCallNode {
        public abstract Object executeObject(Object arg0, Object arg1, Object arg2, Object arg3, Object arg4, Object arg5);

        @Override
        protected int numArgs() {
            return 6;
        }
    }

    public abstract static class Arg7 extends FFIUpCallNode {
        public abstract Object executeObject(Object arg0, Object arg1, Object arg2, Object arg3, Object arg4, Object arg5, Object arg6);

        @Override
        protected int numArgs() {
            return 7;
        }
    }
}
