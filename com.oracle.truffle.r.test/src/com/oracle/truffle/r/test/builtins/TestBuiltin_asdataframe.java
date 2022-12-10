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

public class TestBuiltin_asdataframe extends TestBase {

    @Test
    public void testasdataframe1() {
        assertEval("argv <- structure(list(x = structure(c(3.5, 2, 1.7, 0.40625,     0.5, 0.882, 4, 2, 2, 4, 2, 3, 0.625, 0.5, 0.444444444444444,     0, 0, 0.333333333333333, 0.833333333333333, 1, 0.333333333333333,     0.5, 0.666666666666667, 0.666666666666667, 0.166666666666667,     0, 0.5), .Dim = c(3L, 9L), .Dimnames = list(c('q1.csv', 'q2.csv',     'q3.csv'), c('effsize', 'constraint', 'outdegree', 'indegree',     'efficiency', 'hierarchy', 'centralization', 'gden', 'ego.gden')))),     .Names = 'x');" +
                        "do.call('as.data.frame', argv)");

    }

    @Test
    public void testWithDimnames() {
        assertEval("{ v1 <- matrix(rep(1.1, 16), 4, 4, dimnames=list(c('a', 'b', 'c', 'd'), c('e', 'f', 'g', 'h'))); v0 <- matrix(rep(1.2, 16), 4, 4, dimnames=list(1L:4L, c('e', 'f', 'g', 'h'))); as.data.frame(v1); as.data.frame(v0) }");
    }
}
