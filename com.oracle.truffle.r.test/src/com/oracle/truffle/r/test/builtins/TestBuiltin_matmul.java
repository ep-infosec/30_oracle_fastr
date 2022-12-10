/*
 * Copyright (c) 2016, 2019, Oracle and/or its affiliates. All rights reserved.
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
public class TestBuiltin_matmul extends TestBase {
    @Test
    public void testMatmulCorrectDimnames() {
        assertEval("x <- matrix(c(1,1,1,1,2,3), 3, 2); dimnames(x) <- list(c(1,2,3),c('','x')); coeff <- c(0,1); names(coeff) <- c('a', 'b'); x %*% coeff; ");
        assertEval("m1 <- matrix(1:6,3,2,dimnames=list(c('a','b','c'),c('c1','c2')));m2 <- matrix(c(3,4),2,1,dimnames=list(c('a2','b2'),c('col'))); m1 %*% m2; ");
        assertEval("vec <- c(1,2); names(vec) <- c('a','b'); mat <- matrix(c(8,3),1,2,dimnames=list('row',c('c1','c2'))); vec %*% mat; ");

        assertEval("NA %*% c(3,4,5,6)");
        assertEval("3 %*% c(3,4,5,6)");
        assertEval("NA %*% c(3L,4L,5L,6L)");
        assertEval("3L %*% c(3L,4L,5L,6L)");
        assertEval("c(NA+2i) %*% c(3,4,5,6)");
        assertEval("c(1+2i) %*% c(3,4,5,6)");
    }

    @Test
    public void testMatmulCornerCases() {
        assertEval("matrix(0, nrow=0, ncol=1) %*% c(1,2,3)");
        assertEval("matrix(0, nrow=1, ncol=0) %*% c(1,2,3)");
        assertEval("c(1,2,3) %*% matrix(0, nrow=0, ncol=1)");
        assertEval("c(1,2,3) %*% matrix(0, nrow=1, ncol=0)");
        assertEval("matrix(0, nrow=1, ncol=0) %*% numeric()");
        assertEval("matrix(0, nrow=0, ncol=1) %*% numeric()");
        assertEval("numeric() %*% matrix(0, nrow=1, ncol=0)");
        assertEval("numeric() %*% matrix(0, nrow=0, ncol=1)");
    }
}
