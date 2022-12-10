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
public class TestBuiltin_comment extends TestBase {

    @Test
    public void testcomment1() {
        assertEval("argv <- list(NULL); .Internal(comment(argv[[1]]))");
    }

    @Test
    public void testcomment2() {
        assertEval("argv <- list(structure(list(c0 = structure(integer(0), .Label = character(0), class = 'factor')), .Names = 'c0', row.names = character(0), class = structure('integer(0)', .Names = 'c0'))); .Internal(comment(argv[[1]]))");
    }

    @Test
    public void testcomment3() {
        assertEval("argv <- list(structure(numeric(0), .Dim = c(0L, 0L))); .Internal(comment(argv[[1]]))");
    }

    @Test
    public void testcomment4() {
        assertEval("argv <- list(structure(1:12, .Dim = 3:4, comment = c('This is my very important data from experiment #0234', 'Jun 5, 1998'))); .Internal(comment(argv[[1]]))");
    }

    @Test
    public void testCommentS4() {
        assertEval("{ setClass('CommentS4Test', representation(f='numeric')); x <- new('CommentS4Test'); attr(x, 'comment') <- 'comment ABC'; comment(x); }");
    }
}
