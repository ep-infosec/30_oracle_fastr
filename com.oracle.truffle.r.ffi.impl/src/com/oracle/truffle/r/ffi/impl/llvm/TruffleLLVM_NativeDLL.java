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
package com.oracle.truffle.r.ffi.impl.llvm;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.r.runtime.RInternalError;
import com.oracle.truffle.r.runtime.data.RTruffleObject;
import com.oracle.truffle.r.runtime.ffi.DLL;
import com.oracle.truffle.r.runtime.ffi.DLL.SymbolHandle;
import com.oracle.truffle.r.runtime.ffi.RFFIRootNode;
import com.oracle.truffle.r.runtime.ffi.interop.NativeCharArray;

/**
 * Direct access to native {@code dlopen} for libraries for which no LLVM code is available.
 */
class TruffleLLVM_NativeDLL {

    @ExportLibrary(InteropLibrary.class)
    public abstract static class ErrorCallback implements RTruffleObject {
        abstract void setResult(String errorMessage);

        @SuppressWarnings("static-method")
        @ExportMessage
        boolean isExecutable() {
            return true;
        }

        @ExportMessage
        public Object execute(Object[] arguments) {
            setResult("" + arguments[0]);
            return this;
        }
    }

    private static class ErrorCallbackImpl extends ErrorCallback {
        private String errorMessage;

        @Override
        public void setResult(String errorMessage) {
            this.errorMessage = errorMessage;
        }
    }

    static class TruffleLLVM_NativeDLOpen extends Node {
        @Child private InteropLibrary interop;
        @CompilationFinal private TruffleObject symbolHandleTO;

        TruffleLLVM_NativeDLOpen() {
            SymbolHandle symbolHandle = DLL.findSymbol("call_dlopen", null);
            symbolHandleTO = symbolHandle.asTruffleObject();
            interop = InteropLibrary.getFactory().create(symbolHandleTO);
        }

        public long execute(String path, boolean local, boolean now) throws UnsatisfiedLinkError {
            try {
                ErrorCallbackImpl errorCallbackImpl = new ErrorCallbackImpl();
                long result = (long) interop.execute(symbolHandleTO, errorCallbackImpl, new NativeCharArray(path.getBytes()), local ? 1 : 0,
                                now ? 1 : 0);
                if (result == 0) {
                    throw new UnsatisfiedLinkError(errorCallbackImpl.errorMessage + " : " + path);
                }
                return result;
            } catch (InteropException e) {
                throw RInternalError.shouldNotReachHere(e);
            }
        }
    }

    static class TruffleLLVM_NativeDLClose extends Node {
        @Child private InteropLibrary interop = InteropLibrary.getFactory().getUncached();
        @CompilationFinal private TruffleObject symbolHandleTO;

        TruffleLLVM_NativeDLClose() {
            SymbolHandle symbolHandle = DLL.findSymbol("call_dlclose", null);
            symbolHandleTO = symbolHandle.asTruffleObject();
            interop = InteropLibrary.getFactory().create(symbolHandleTO);
        }

        public int execute(long handle) {
            try {
                int result = (int) interop.execute(symbolHandleTO, handle);
                return result;
            } catch (InteropException e) {
                throw RInternalError.shouldNotReachHere(e);
            }
        }
    }

    public static final class NativeDLOpenRootNode extends RFFIRootNode<TruffleLLVM_NativeDLOpen> {
        private static NativeDLOpenRootNode nativeDLOpenRootNode;

        private NativeDLOpenRootNode() {
            super(new TruffleLLVM_NativeDLOpen());
        }

        @Override
        public Object execute(VirtualFrame frame) {
            Object[] args = frame.getArguments();
            return rffiNode.execute((String) args[0], (boolean) args[1], (boolean) args[2]);
        }

        public static NativeDLOpenRootNode create() {
            if (nativeDLOpenRootNode == null) {
                nativeDLOpenRootNode = new NativeDLOpenRootNode();
            }
            return nativeDLOpenRootNode;
        }
    }
}
