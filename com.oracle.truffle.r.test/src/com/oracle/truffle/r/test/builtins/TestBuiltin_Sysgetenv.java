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
 * Copyright (c) 2012-2014, Purdue University
 * Copyright (c) 2013, 2021, Oracle and/or its affiliates
 *
 * All rights reserved.
 */
package com.oracle.truffle.r.test.builtins;

import org.junit.Test;

import com.oracle.truffle.r.test.TestBase;

// Checkstyle: stop line length check
public class TestBuiltin_Sysgetenv extends TestBase {

    @Test
    public void testSysgetenv1() {
        assertEval("argv <- list('R_PAPERSIZE', ''); .Internal(Sys.getenv(argv[[1]], argv[[2]])) %in% c('a4', 'letter')");
    }

    @Test
    public void testSysgetenv2() {
        assertEval("argv <- list('SWEAVE_OPTIONS', NA_character_); .Internal(Sys.getenv(argv[[1]], argv[[2]]))");
    }

    @Test
    public void testEnvVars() {
        assertEval("{ Sys.setenv(\"a\") } ");
        assertEval("{ Sys.setenv(FASTR_A=\"a\"); Sys.getenv(\"FASTR_A\"); } ");
        assertEval("{ Sys.setenv(FASTR_A=\"a\", FASTR_B=\"b\"); Sys.getenv(c(\"FASTR_A\", \"FASTR_B\"));  } ");
        assertEval("{ Sys.getenv(\"FASTR_SHOULD_NOT_BE_DEFINED\") } ");
        assertEval("{ Sys.getenv(\"FASTR_SHOULD_NOT_BE_DEFINED\", unset=\"UNSET\") } ");
    }
}
