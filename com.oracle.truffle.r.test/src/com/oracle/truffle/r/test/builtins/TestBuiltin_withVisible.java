/*
 * Copyright (c) 2016, 2021, Oracle and/or its affiliates. All rights reserved.
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

public class TestBuiltin_withVisible extends TestBase {

    @Test
    public void testwithVisible() {
        assertEval("withVisible()");
        assertEval("withVisible(1)");
        assertEval("withVisible(x <- 1)");
        assertEval("withVisible({ x <- 1; 1 })");
        assertEval("withVisible({ 1; x <- 1 })");
        assertEval("f <- function(x) { foo <- 1 + x }; withVisible(f(1))");
        assertEval("f <- function(x) { 1 + x }; withVisible(f(1))");
    }

    @Test
    public void testInvisibleValueForWithVisible() {
        // FIXME: FastR fails on this test probably because of eager promise optimization, or
        // because of
        // wrong visibility propagation. A similar test is present in testthat 3.0.1 package
        // test-expect-comparison.
        assertEval(Ignored.ImplementationError, "{ id <- function(x) x; f <- function(x) { res <- withVisible(x); res$visible }; f(id(invisible(1))) }");
    }
}
