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
 * Copyright (c) 2013, 2018, Oracle and/or its affiliates
 *
 * All rights reserved.
 */
package com.oracle.truffle.r.test.builtins;

import org.junit.Test;

import com.oracle.truffle.r.test.TestBase;

// Checkstyle: stop line length check
public class TestBuiltin_ascall extends TestBase {

    @Test
    public void testascall1() {
        assertEval("argv <- list(list(quote(quote), c(0.568, 1.432, -1.08, 1.08)));as.call(argv[[1]]);");
    }

    @Test
    public void testascall2() {
        assertEval("argv <- list(list(quote(quote), FALSE));as.call(argv[[1]]);");
    }

    @Test
    public void testascall3() {
        assertEval("argv <- list(list(quote(quote), list(NULL, c('time', 'status'))));as.call(argv[[1]]);");
    }

    @Test
    public void testascall4() {
        assertEval("argv <- list(structure(expression(data.frame, check.names = TRUE, stringsAsFactors = TRUE), .Names = c('', 'check.names', 'stringsAsFactors')));as.call(argv[[1]]);");
    }

    @Test
    public void testascall5() {
        assertEval("argv <- list(list(quote(quote), 80L));as.call(argv[[1]]);");
    }

    @Test
    public void testascall6() {
        assertEval("argv <- list(list(quote(quote), NA));as.call(argv[[1]]);");
    }

    @Test
    public void testAsCall() {
        assertEval("{ l <- list(f) ; as.call(l) }");
        assertEval("{ l <- list(f, 2, 3) ; as.call(l) }");
        assertEval("{ g <- function() 23 ; l <- list(f, g()) ; as.call(l) }");
        assertEval("{ f <- round ; g <- as.call(list(f, quote(A))) }");
        assertEval("{ f <- function() 23 ; l <- list(f) ; cl <- as.call(l) ; eval(cl) }");
        assertEval("{ f <- function(a,b) a+b ; l <- list(f,2,3) ; cl <- as.call(l) ; eval(cl) }");
        assertEval("{ f <- function(x) x+19 ; g <- function() 23 ; l <- list(f, g()) ; cl <- as.call(l) ; eval(cl) }");

        assertEval("{ f <- function(x) x ; l <- list(f, 42) ; cl <- as.call(l); typeof(cl[[1]]) }");
        assertEval("{ f <- function(x) x ; l <- list(f, 42) ; cl <- as.call(l); typeof(cl[[2]]) }");

        assertEval("{ as.call(42) }");

        assertEval("typeof(as.call(list(substitute(graphics::par))))");

        assertEval("e <- substitute(a$b(c)); as.call(lapply(e, function(x) x))");
        assertEval(Output.IgnoreWhitespace, "e <- expression(function(a) b); as.call(list(e[[1]][[1]]))");
        assertEval("e <- expression(function(a) b); as.call(list(e[[1]][[2]]))");
        assertEval("call('foo')");
        // Note: call('function', 'a') should not cause the exception, it should be the printing
        assertEval(Output.IgnoreWhitespace, "invisible(call('function', 'a'))");
        assertEval(Output.IgnoreWhitespace, "length(call('function', 'a'))");
        assertEval(Output.IgnoreWhitespace, "call('function', pairlist(a=1))");
        assertEval(Output.IgnoreWhitespace, "call('function', pairlist(a=1), 3)");
        assertEval(Output.IgnoreWhitespace, "call('function', pairlist(a=1), 5,3)");
        assertEval(Output.IgnoreWhitespace, "call('function', pairlist(a=1), 5)");
        assertEval("as.call(list('function', pairlist(a=1), 5))");
        assertEval(Output.IgnoreWhitespace, "as.call(list(as.symbol('function'), pairlist(a=1), 5))");
        assertEval(Output.IgnoreWhitespace, "as.call(list(as.symbol('function'), pairlist(a=1)))");
        assertEval(Output.IgnoreWhitespace, "as.call(list(as.symbol('function')))");
        assertEval(Output.IgnoreWhitespace, "call('function')");

        assertEval(Output.IgnoreWhitespace, "{ cl <- quote(fun(3)); as.call(cl) }");
    }

    @Test
    public void testSideEffect() {
        assertEval("{ a <- c(1, 2, 3); b <- function() { a[1] <<-10 ; 4 }; f <- sum; (g <- as.call(list(f, quote(a), quote(b()), quote(a[1] <- 11)))); eval(g) }");
    }
}
