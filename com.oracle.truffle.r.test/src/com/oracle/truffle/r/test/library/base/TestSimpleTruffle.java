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

public class TestSimpleTruffle extends TestBase {

    @Test
    public void test1() {
        assertEval("{ f<-function(i) {i} ; f(1) ; f(2) }");
        assertEval("{ f<-function() { 1:5 } ; f(); f() }");
        assertEval("{ f<-function() { length(c(1,2)) } ; f(); f() }");
        assertEval("{ f<-function() { if (1) TRUE } ; f(); f() }");
        assertEval("{ f<-function() { if (if (1) {TRUE} else {FALSE} ) 1 } ; f(); f() }");
        assertEval("{ f<-function() { logical(0) } ; f(); f() }"); // FIXME

        assertEval("{ f<-function(i) { if (TRUE) { i } } ; f(2) ; f(1) }");
        assertEval("{ f<-function(i) { i ; if (FALSE) { 1 } else { i } } ; f(2) ; f(1) }");
        assertEval("{ f<-function(i) { i ; if (TRUE) { 1 } else { i } } ; f(2) ; f(1) }");
        assertEval("{ f<-function(i) { if(i==1) { 1 } else { i } } ; f(2) ; f(2) }");
        assertEval("{ f<-function(i) { if(i==1) { 1 } else { i } } ; f(2) ; f(1) }");
    }

    @Test
    public void test1Ignore() {
        assertEval("{ f<-function(i) { if(i==1) { i } } ; f(1) ; f(2) }");
        assertEval("{ f<-function() { if (!1) TRUE } ; f(); f() }");
        assertEval("{ f<-function() { if (!TRUE) 1 } ; f(); f() }");
        assertEval("{ f<-function(i) { if (FALSE) { i } } ; f(2) ; f(1) }");
    }

    @Test
    public void testLoop() {
        assertEval("{ f<-function() { x<-210 ; repeat { x <- x + 1 ; break } ; x } ; f() ; f() }");
        assertEval("{ f<-function() { x<-1 ; repeat { x <- x + 1 ; if (x > 11) { break } } ; x } ; f(); f() }");
        assertEval("{ f<-function() { x<-1 ; repeat { x <- x + 1 ; if (x <= 11) { next } else { break } ; x <- 1024 } ; x } ; f() ; f() }");
        assertEval("{ f<-function() { x<-1 ; while(TRUE) { x <- x + 1 ; if (x > 11) { break } } ; x } ; f(); f() }");
        assertEval("{ f<-function() { x<-1 ; while(x <= 10) { x<-x+1 } ; x } ; f(); f() }");
        assertEval("{ f<-function() { x<-1 ; for(i in 1:10) { x<-x+1 } ; x } ; f(); f() }");
    }

    @Test
    public void testWarningsAndErrors() {
        assertEval("{ (c(1, 2) < c(1, 2, 3)) ==  (c(1, 2) < c(1, 3, 4)) }");
        assertEval("{ 1i > (c(1, 2) < c(1, 2, 3)) }");
        assertEval("{ 1i > ((c(1, 2) < c(1, 2, 3)) ==  (c(1, 2) < c(1, 3, 4))) }");
    }
}
