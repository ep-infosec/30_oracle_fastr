/*
 * Copyright (c) 2016, 2018, Oracle and/or its affiliates. All rights reserved.
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

import java.util.Base64;

public abstract class LLVM_IR {
    public static final int TEXT_CODE = 1;
    public static final int BINARY_CODE = 2;

    /**
     * The name of the "module", aka object file, that the IR pertains to.
     */
    public final String name;

    public final String libPath;

    protected LLVM_IR(String name, String libPath) {
        this.name = name;
        this.libPath = libPath;
    }

    @Override
    public String toString() {
        return name;
    }

    /**
     * Denotes textual LLVM IR.
     */
    public static final class Text extends LLVM_IR {
        public final String text;

        public Text(String name, String text, String libPath) {
            super(name, libPath);
            this.text = text;
        }
    }

    /**
     * Denotes binary LLVM IR.
     */
    public static final class Binary extends LLVM_IR {
        public final byte[] binary;
        public final String base64;

        public Binary(String name, byte[] binary, String libPath) {
            super(name, libPath);
            this.binary = binary;
            this.base64 = Base64.getEncoder().encodeToString(binary);
        }
    }
}
