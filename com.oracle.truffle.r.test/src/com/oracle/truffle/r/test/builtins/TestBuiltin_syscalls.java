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

import org.junit.After;
import org.junit.Test;

import com.oracle.truffle.r.test.TestBase;

import static com.oracle.truffle.r.test.builtins.TestStackBuiltins.DONT_KEEP_SOURCE;
import static com.oracle.truffle.r.test.builtins.TestStackBuiltins.KEEP_SOURCE;

public class TestBuiltin_syscalls extends TestBase {

    @Override
    @After
    public void afterTest() {
        // Restore the keep.source option
        assertEval(KEEP_SOURCE);
        super.afterTest();
    }

    @Test
    public void testSysCalls() {
        assertEval("sys.calls()");
        assertEval(DONT_KEEP_SOURCE + "{ f <- function(x) sys.calls(); g <- function() f(x); g() }");
        // Avoid deparse issues in the output of the try code by comparing length
        assertEval("{ f <- function(x) sys.calls(); g <- function() f(x); length(try(g())) }");
    }

    @Test
    public void testSysCallsPromises() {
        assertEval("{ f <- function(x) x; g <- function() f(sys.calls()); g() }");
        assertEval("{ f <- function(x) x; g <- function() f(sys.calls()); length(try(g())) }");
        assertEval(DONT_KEEP_SOURCE + "{ v <- function() sys.calls(); u<- function() v(); f <- function(x) x ; g <- function(y) f(y) ; h <- function(z=u()) g(z) ; h() }");
    }

    @Test
    public void testSysCallsWithEval() {
        assertEval(DONT_KEEP_SOURCE + "{ foo <- function() sys.calls(); bar <- function() eval(parse(text='foo()'), envir=new.env()); bar(); }");
        assertEval(DONT_KEEP_SOURCE + "{ foo <- function() sys.calls(); bar <- function() eval(parse(text='foo()')); bar(); }");
    }
}
