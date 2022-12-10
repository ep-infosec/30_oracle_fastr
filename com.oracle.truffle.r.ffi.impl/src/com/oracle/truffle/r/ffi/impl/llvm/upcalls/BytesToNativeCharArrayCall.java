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
package com.oracle.truffle.r.ffi.impl.llvm.upcalls;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.r.ffi.impl.llvm.TruffleLLVM_UpCallsRFFIImpl;
import com.oracle.truffle.r.runtime.data.RTruffleObject;

@ExportLibrary(InteropLibrary.class)
public class BytesToNativeCharArrayCall implements RTruffleObject {

    public final TruffleLLVM_UpCallsRFFIImpl upCallsImpl;

    public BytesToNativeCharArrayCall(TruffleLLVM_UpCallsRFFIImpl upCallsImpl) {
        this.upCallsImpl = upCallsImpl;
    }

    public static boolean isInstance(TruffleObject value) {
        return value instanceof BytesToNativeCharArrayCall;
    }

    @ExportMessage
    public Object execute(Object[] arguments) {
        String strArg = (String) arguments[0];
        return upCallsImpl.bytesToNativeCharArray(getBytes(strArg));
    }

    @CompilerDirectives.TruffleBoundary
    private static byte[] getBytes(String strArg) {
        return strArg.getBytes();
    }

    @SuppressWarnings("static-method")
    @ExportMessage
    boolean isExecutable() {
        return true;
    }

}
