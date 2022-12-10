/*
 * Copyright (c) 2014, 2018, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.r.runtime;

/**
 * Support for callbacks in embedded mode for certain VM operations. If the embedding code overrides
 * certain operation, the flag for that operation is updated here and when FastR is about to invoke
 * one of those operations, normally implemented by default by FastR in Java, it checks the flag and
 * eventually down-calls to the user provided handler.
 *
 */
public enum RInterfaceCallbacks {
    R_Suicide,
    R_ShowMessage,
    R_ReadConsole,
    R_WriteConsole,
    // R_WriteConsoleEx, handled in native code
    R_ResetConsole,
    R_FlushConsole,
    R_ClearerrConsole,
    R_Busy,
    R_CleanUp;

    private boolean overridden;

    public boolean isOverridden() {
        return overridden;
    }

    /**
     * Upcalled from native code.
     */
    @SuppressWarnings("unused")
    private static void override(String name) {
        RInterfaceCallbacks.valueOf(name).overridden = true;
    }
}
