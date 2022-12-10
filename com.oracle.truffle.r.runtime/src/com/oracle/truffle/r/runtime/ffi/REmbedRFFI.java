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
package com.oracle.truffle.r.runtime.ffi;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.r.runtime.DSLConfig;
import com.oracle.truffle.r.runtime.RInternalError;
import com.oracle.truffle.r.runtime.ffi.DownCallNodeFactory.DownCallNode;

/**
 * Function down-calls related to the embedded API. TODO: these all should be invoked as proper
 * down-calls because the user code may want to use R API.
 */
public final class REmbedRFFI {
    private final DownCallNodeFactory downCallNodeFactory;

    public REmbedRFFI(DownCallNodeFactory downCallNodeFactory) {
        this.downCallNodeFactory = downCallNodeFactory;
    }

    public static final class ReadConsoleNode extends NativeCallNode {
        @Child private InteropLibrary interop;

        private ReadConsoleNode(DownCallNodeFactory factory) {
            super(factory.createDownCallNode());
        }

        public String execute(String prompt) {
            Object result = call(NativeFunction.rembedded_read_console, prompt);
            if (result instanceof String) {
                return (String) result;
            }
            assert result instanceof TruffleObject : "NFI is expected to send us TruffleObject or String";
            if (interop == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                interop = insert(InteropLibrary.getFactory().createDispatched(DSLConfig.getInteropLibraryCacheSize()));
            }
            assert interop.isString(result) : "NFI is expected to send us TruffleObject or String";

            try {
                return interop.asString(result);
            } catch (UnsupportedMessageException e) {
                CompilerDirectives.transferToInterpreter();
                throw RInternalError.shouldNotReachHere("Unboxing TruffleObject from NFI, which should be String wrapper, failed. " + e.getMessage());
            }
        }

        public static REmbedRFFI.ReadConsoleNode create() {
            return RFFIFactory.getREmbedRFFI().createReadConsoleNode();
        }
    }

    public abstract static class WriteConsoleBaseNode extends NativeCallNode {
        private WriteConsoleBaseNode(DownCallNode downCallNode) {
            super(downCallNode);
        }

        public abstract void execute(String x);
    }

    public static final class WriteConsoleNode extends WriteConsoleBaseNode {
        public static REmbedRFFI.WriteConsoleNode create() {
            return RFFIFactory.getREmbedRFFI().createWriteConsoleNode();
        }

        public WriteConsoleNode(DownCallNodeFactory factory) {
            super(factory.createDownCallNode());
        }

        @Override
        public void execute(String x) {
            call(NativeFunction.rembedded_write_console, x, x.length());
        }
    }

    public static final class WriteErrConsoleNode extends WriteConsoleBaseNode {
        public static REmbedRFFI.WriteErrConsoleNode create() {
            return RFFIFactory.getREmbedRFFI().createWriteErrConsoleNode();
        }

        public WriteErrConsoleNode(DownCallNodeFactory factory) {
            super(factory.createDownCallNode());
        }

        @Override
        public void execute(String x) {
            call(NativeFunction.rembedded_write_err_console, x, x.length());
        }
    }

    public static final class EmbeddedSuicideNode extends NativeCallNode {
        private EmbeddedSuicideNode(DownCallNodeFactory factory) {
            super(factory.createDownCallNode());
        }

        public void execute(String message) {
            call(NativeFunction.rembedded_suicide, message);
        }
    }

    public static final class EmbeddedCleanUpNode extends NativeCallNode {
        private EmbeddedCleanUpNode(DownCallNodeFactory factory) {
            super(factory.createDownCallNode());
        }

        public void execute(int x, int y, int z) {
            call(NativeFunction.rembedded_cleanup, x, y, z);
        }
    }

    ReadConsoleNode createReadConsoleNode() {
        return new ReadConsoleNode(downCallNodeFactory);
    }

    WriteConsoleNode createWriteConsoleNode() {
        return new WriteConsoleNode(downCallNodeFactory);
    }

    WriteErrConsoleNode createWriteErrConsoleNode() {
        return new WriteErrConsoleNode(downCallNodeFactory);
    }

    public EmbeddedSuicideNode createEmbeddedSuicideNode() {
        return new EmbeddedSuicideNode(downCallNodeFactory);
    }

    public EmbeddedCleanUpNode createEmbeddedCleanUpNode() {
        return new EmbeddedCleanUpNode(downCallNodeFactory);
    }
}
