/*
 * This program is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation; either version 2 of the License, or (at your option)
 * any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License for
 * more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program; if not, write to the Free Software Foundation, Inc., 51
 * Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 *
 * Copyright (c) 2014, Purdue University
 * Copyright (c) 2014, 2018, Oracle and/or its affiliates
 *
 * All rights reserved.
 */
package com.oracle.truffle.r.test.builtins;

import org.junit.Test;

import com.oracle.truffle.r.test.TestBase;

// Checkstyle: stop line length check
public class TestBuiltin_enc2native extends TestBase {

    @Test
    public void testenc2native1() {
        assertEval("argv <- list(character(0));enc2native(argv[[1]]);");
    }

    @Test
    public void testenc2native3() {
        assertEval("argv <- list(structure(character(0), .Names = character(0)));enc2native(argv[[1]]);");
    }

    @Test
    public void testenc2native4() {
        assertEval("argv <- list('José Pinheiro [aut] (S version)');enc2native(argv[[1]]);");
    }

    @Test
    public void testInvalidArguments() {
        // Note: GnuR has typo in the message
        assertEval(Output.IgnoreErrorMessage, "enc2native(42);");
    }
}