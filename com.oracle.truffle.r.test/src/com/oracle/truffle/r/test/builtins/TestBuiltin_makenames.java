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
 * Copyright (c) 2013, 2020, Oracle and/or its affiliates
 *
 * All rights reserved.
 */
package com.oracle.truffle.r.test.builtins;

import org.junit.Test;

import com.oracle.truffle.r.test.TestBase;

// Checkstyle: stop line length check
public class TestBuiltin_makenames extends TestBase {

    @Test
    public void testmakenames1() {
        assertEval("argv <- list('head', TRUE); .Internal(make.names(argv[[1]], argv[[2]]))");
    }

    @Test
    public void testmakenames2() {
        assertEval("argv <- list('FALSE', TRUE); .Internal(make.names(argv[[1]], argv[[2]]))");
    }

    @Test
    public void testmakenames3() {
        assertEval("argv <- list(c('.Call', '.Call numParameters', '.Fortran', '.Fortran numParameters'), TRUE); .Internal(make.names(argv[[1]], argv[[2]]))");
    }

    @Test
    public void testmakenames4() {
        assertEval("argv <- list('..adfl.row.names', TRUE); .Internal(make.names(argv[[1]], argv[[2]]))");
    }

    @Test
    public void testmakenames5() {
        assertEval("argv <- list(c('name', 'title', 'other.author'), TRUE); .Internal(make.names(argv[[1]], argv[[2]]))");
    }

    @Test
    public void testmakenames6() {
        assertEval("argv <- list('.2a', TRUE); .Internal(make.names(argv[[1]], argv[[2]]))");
    }

    @Test
    public void testmakenames7() {
        assertEval("argv <- list('', TRUE); .Internal(make.names(argv[[1]], argv[[2]]))");
    }

    @Test
    public void testmakenames8() {
        assertEval("argv <- list(NA_character_, TRUE); .Internal(make.names(argv[[1]], argv[[2]]))");
    }

    @Test
    public void testmakenames9() {
        assertEval("argv <- list(c('Subject', 'predict.fixed', 'predict.Subject'), TRUE); .Internal(make.names(argv[[1]], argv[[2]]))");
    }

    @Test
    public void testmakenames10() {
        assertEval("argv <- list(c('', '', 'bady'), TRUE); .Internal(make.names(argv[[1]], argv[[2]]))");
    }

    @Test
    public void testmakenames11() {
        assertEval("argv <- list(character(0), TRUE); .Internal(make.names(argv[[1]], argv[[2]]))");
    }

    @Test
    public void testMakeNamesNonWriteableStringSeq() {
        assertEval("make.names(seq_len(1))");
        assertEval("make.names(seq(2))");
        assertEval("make.names(1:10)");
    }

    @Test
    public void testMakeNames() {
        assertEval("{ make.names(7) }");
        assertEval("{ make.names(\"a_a\") }");
        assertEval("{ make.names(\"a a\") }");
        assertEval("{ make.names(\"a_a\", allow_=FALSE) }");
        assertEval("{ make.names(\"a_a\", allow_=7) }");
        assertEval("{ make.names(\"a_a\", allow_=c(7,42)) }");
        assertEval("{ make.names(\"...7\") }");
        assertEval("{ make.names(\"..7\") }");
        assertEval("{ make.names(\".7\") }");
        assertEval("{ make.names(\"7\") }");
        assertEval("{ make.names(\"$\") }");
        assertEval("{ make.names(\"$_\", allow_=FALSE) }");
        assertEval("{ make.names(\"else\")}");
        assertEval("{ make.names(\"NA_integer_\", allow_=FALSE) }");

        assertEval("{ make.names(\"a_a\", allow_=\"a\") }");
        assertEval("{ make.names(\"a_a\", allow_=logical()) }");
        assertEval("{ make.names(\"a_a\", allow_=NULL) }");

        assertEval("{ .Internal(make.names(42, F)) }");
    }
}
