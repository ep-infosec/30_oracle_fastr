/*
 * Copyright (c) 2017, 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.r.test.builtins;

import org.junit.Test;

import com.oracle.truffle.r.test.TestBase;

// Checkstyle: stop line length check
public class TestBuiltin_dqr extends TestBase {

    @Test
    public void testdqrdc2() {
        assertEval(Ignored.OutputFormatting, ".Fortran(.F_dqrdc2, 1, 1L, 1L, 1L, 1, 1L, 1, 1L, 1)");
    }

    @Test
    public void testdqrcf() {
        assertEval(".Fortran(.F_dqrcf, 1, 1L, 1L, 1, 1, 1L, 1, 1L)");
    }

    @Test
    public void testdqrqty() {
        assertEval(".Fortran(.F_dqrqty, 1, 1L, 1L, 1, 1, 1L, qty=1)");
    }

    @Test
    public void testdqrqy() {
        assertEval(".Fortran(.F_dqrqy, 1, 1L, 1L, 1, 1, 1L, 1)");
    }

    @Test
    public void testdqrrsd() {
        assertEval(".Fortran(.F_dqrrsd, 1, 1L, 1L, 1, 1, 1L, 1)");
    }

    @Test
    public void testdqrxb() {
        assertEval(".Fortran(.F_dqrxb, 1, 1L, 1L, 1, 1, 1L, 1)");
    }
}
