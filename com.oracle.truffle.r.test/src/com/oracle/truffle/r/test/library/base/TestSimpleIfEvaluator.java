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
package com.oracle.truffle.r.test.library.base;

import org.junit.Test;

import com.oracle.truffle.r.test.TestBase;

public class TestSimpleIfEvaluator extends TestBase {

    @Test
    public void testIfWithoutElse() {
        assertEval("if(TRUE) 1");
    }

    @Test
    public void testIfWithoutElseIgnore() {
        assertEval("if(FALSE) 1");
    }

    @Test
    public void testIf2() {
        assertEval("if(TRUE) 1 else 2");
        assertEval("if(FALSE) 1 else 2");
    }

    @Test
    public void testIfNot1() {
        assertEval("if(!FALSE) 1 else 2");
        assertEval("if(!TRUE) 1 else 2");
    }

    @Test
    public void testIfDanglingElse() {
        assertEval("if(TRUE) if (FALSE) 1 else 2");
    }

    @Test
    public void testIfDanglingElseIgnore() {
        assertEval("if(FALSE) if (FALSE) 1 else 2");
    }

    @Test
    public void testIf() {
        assertEval("{ x <- 2 ; if (1==x) TRUE else 2 }");
        assertEval("{ x <- 2 ; if (NA) x <- 3 ; x }");
        assertEval("{ f <- function(x) { if (x) 1 else 2 } ; f(NA)  }");
        assertEval("{ f <- function(x) { if (x) 1 else 2 } ; f(1) ; f(NA) }");
        assertEval("{ f <- function(x) { if (x) 1 else 2 } ; f(1) ; f(TRUE) }");
        assertEval("{ f <- function(x) { if (x) 1 else 2 } ; f(1) ; f(FALSE) }");

        assertEval("{ f <- function(x) { if (x) 1 else 2 } ; f(1) ; f(\"hello\") }");
        assertEval("{ f <- function(x) { if (x) 1 else 2 } ; f(1) ; f(logical()) }");
        assertEval("{ f <- function(x) { if (x == 2) 1 else 2 } ; f(1) ; f(NA) }");

        assertEval("{ if (TRUE==FALSE) TRUE else FALSE }");
        assertEval("{ if (NA == TRUE) TRUE else FALSE }");
        assertEval("{ if (TRUE == NA) TRUE else FALSE }");
        assertEval("{ if (FALSE==TRUE) TRUE else FALSE }");
        assertEval("{ if (FALSE==1) TRUE else FALSE }");
        assertEval("{ f <- function(v) { if (FALSE==v) TRUE else FALSE } ; f(TRUE) ; f(1) }");
        assertEval("{ x<-list(1,2); if (x) 7 else 42 }");
        assertEval("{ if (!(7+42i)) TRUE else FALSE }");
    }

    @Test
    public void testIfIgnore() {
        assertEval("{ f <- function(x) { if (x) 1 else 2 } ; f(1) ; f(1:3) }");
    }

    @Test
    public void testCast() {
        assertEval("{ f <- function(a) { if (is.na(a)) { 1 } else { 2 } } ; f(5) ; f(1:3)}");
        assertEval("{ if (1:3) { TRUE } }");
        assertEval("{ if (integer()) { TRUE } }");
        assertEval("{ if (1[2:1]) { TRUE } }");
        assertEval("{ if (c(0,0,0)) { TRUE } else { 2 } }");
        assertEval("{ if (c(1L,0L,0L)) { TRUE } else { 2 } }");
        assertEval("{ if (c(0L,0L,0L)) { TRUE } else { 2 } }");
        assertEval("{ if (c(1L[2],0L,0L)) { TRUE } else { 2 } }");
        assertEval("{ f <- function(cond) { if (cond) { TRUE } else { 2 } } ; f(1:3) ; f(2) }");
        assertEval("{ f <- function(cond) { if (cond) { TRUE } else { 2 }  } ; f(c(TRUE,FALSE)) ; f(FALSE) }");
        assertEval("{ f <- function(cond) { if (cond) { TRUE } else { 2 }  } ; f(logical()) }");
        assertEval("{ f <- function(cond) { if (cond) { TRUE } else { 2 }  } ; f(c(TRUE,FALSE)) ; f(1) }");
    }

    @Test
    public void testIfVisibility() {
        assertEval("{ if (FALSE) 23 }");
        assertEval("{ if (FALSE) 23 else NULL }");
        assertEval("{ if (TRUE) invisible(23) else 23 }");
        assertEval("{ if (TRUE) invisible(23) }");
        assertEval("{ if (FALSE) 23 else invisible(23) }");
    }

    @Test
    public void testInvalidCondition() {
        assertEval("{ f <- function() TRUE; if (f) 'unexpected' }");
    }
}
