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
 * Copyright (c) 2013, 2019, Oracle and/or its affiliates
 *
 * All rights reserved.
 */
package com.oracle.truffle.r.test.builtins;

import org.junit.Test;

import com.oracle.truffle.r.test.TestBase;

// Checkstyle: stop line length check
public class TestBuiltin_lengthassign extends TestBase {

    @Test
    public void testlengthassign1() {
        assertEval("argv <- list(c('A', 'B'), value = 5);`length<-`(argv[[1]],argv[[2]]);");
    }

    @Test
    public void testlengthassign2() {
        assertEval("argv <- list(list(list(2, 2, 6), list(2, 2, 0)), value = 0);`length<-`(argv[[1]],argv[[2]]);");
    }

    @Test
    public void testlengthassign3() {
        assertEval("argv <- list(list(list(2, 2, 6), list(1, 3, 9), list(1, 3, -1)), value = 1);`length<-`(argv[[1]],argv[[2]]);");
    }

    @Test
    public void testlengthassign4() {
        assertEval("argv <- list(c(28, 27, 26, 25, 24, 23, 22, 21, 20, 19, 18, 17, 16, 15, 14, 13, 12, 11, 10, 9, 8, 7, 6, 5, 4, 3, 2, 1), value = 0L);`length<-`(argv[[1]],argv[[2]]);");
    }

    @Test
    public void testlengthassign5() {
        assertEval("argv <- list(c(0L, 1L, 2L, 3L, 4L, 5L, 6L, 0L, 1L, 2L, 3L, 4L, 5L, 6L, 0L, 1L, 2L, 3L, 4L, 5L, 6L, 0L, 1L, 2L, 3L, 4L, 5L, 6L), value = 0L);`length<-`(argv[[1]],argv[[2]]);");
    }

    @Test
    public void testlengthassign6() {
        assertEval("argv <- list(list(), value = 0L);`length<-`(argv[[1]],argv[[2]]);");

        assertEval(Output.IgnoreErrorContext, "argv <- structure(list(1:3, value = TRUE), .Names = c('', 'value'));do.call('length<-', argv)");
    }

    @Test
    public void testLengthUpdate() {
        assertEval("{ x<-c(a=1, b=2); length(x)<-1; x }");
        assertEval("{ x<-c(a=1, b=2); length(x)<-4; x }");
        assertEval("{ x<-data.frame(a=c(1,2), b=c(3,4)); length(x)<-1; x }");
        assertEval("{ x<-data.frame(a=c(1,2), b=c(3,4)); length(x)<-4; x }");
        assertEval("{ x<-data.frame(a=1,b=2); length(x)<-1; attributes(x) }");
        assertEval("{ x<-data.frame(a=1,b=2); length(x)<-4; attributes(x) }");
        assertEval("{ x<-factor(c(\"a\", \"b\", \"a\")); length(x)<-1; x }");
        assertEval("{ x<-factor(c(\"a\", \"b\", \"a\")); length(x)<-4; x }");
        assertEval("{ x <- 1:4 ; length(x) <- 2 ; x }");
        assertEval("{ x <- 1:2 ; length(x) <- 4 ; x }");
        assertEval("{ x <- 1 ; f <- function() { length(x) <<- 2 } ; f() ; x }");
        assertEval("{ x <- 1:2 ; z <- (length(x) <- 4) ; z }");
        assertEval("{ x<-c(a=7, b=42); length(x)<-4; x }");
        assertEval("{ x<-c(a=7, b=42); length(x)<-1; x }");
        assertEval("{ x<-NULL; length(x)<-2; x }");
        assertEval("{ x<-NULL; length(x)<-0; x }");

        // regression: updating length of b caused attributes reset of a
        assertEval("{a <- structure(c(3,4), names=c('a','b'),package=c('methods')); b <- a; length(b) <- 1; list(a=a,b=b)}");
        // undocumented GNU-R feature/bug that "methods" rely on: updating length of x to L is no-op
        // if length(x) == L, so then x retains all its attributes
        assertEval("{a <- structure(c(3,4), names=c('a','b'),package=c('methods')); length(a) <- 2; a}");
    }

    @Test
    public void testArgsCasts() {
        assertEval("{ x<-quote(a); length(x)<-2 }");
        assertEval("{ x<-c(42, 1); length(x)<-'3'; x }");
        assertEval("{ x<-c(42, 1); length(x)<-3.1; x }");
        assertEval("{ x<-c(42, 1); length(x)<-c(1,2) }");
    }
}
