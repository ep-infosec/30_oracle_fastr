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
 * Copyright (c) 2013, 2021, Oracle and/or its affiliates
 *
 * All rights reserved.
 */
package com.oracle.truffle.r.test.builtins;

import org.junit.Test;

import com.oracle.truffle.r.test.TestBase;

// Checkstyle: stop line length check
public class TestBuiltin_get extends TestBase {

    private final String[] envirValues2 = {"-2", "-1", "0", "1", "2"};
    private final String[] envirValues3 = {"-3", "-2", "-1", "0", "1", "2", "3"};

    @Test
    public void testGet() {
        assertEval("{y<-function(){y<-2;get(\"y\",mode=\"integer\")};y();}");
        assertEval("{y<-function(){y<-2;get(\"y\",mode=\"closure\")};y();}");
        assertEval("{y<-function(){y<-2;get(\"y\",mode=\"integer\",inherits=FALSE);get(\"y\",mode=\"integer\",inherits=FALSE)};y();}");
        assertEval("{y<-function(){y<-2;get(\"y\",mode=\"double\")};y();}");
        assertEval("{y<-function(){y<-2;get(\"y\",mode=\"double\",inherits=FALSE)};y();}");
        assertEval("{ get(\"dummy\") }");
        assertEval("{ x <- 33 ; f <- function() { if (FALSE) { x <- 22  } ; get(\"x\", inherits = FALSE) } ; f() }");
        assertEval("{ x <- 33 ; f <- function() { get(\"x\", inherits = FALSE) } ; f() }");
        assertEval("{ get(\".Platform\", globalenv())$endian }");
        assertEval("{ get(\".Platform\")$endian }");
        assertEval("{y<-function(){y<-2;get(\"y\",mode=\"closure\",inherits=FALSE);};y();}");

        // behavior specific to RS4Object as environment:
        assertEval("setClass('foo', representation(x='numeric')); f <- new('foo'); e <- new.env(); e$x <- 1; attr(f, '.xData') <- e; get('x', envir=f)");
        assertEval("setClass('foo', representation(x='numeric')); f <- new('foo'); e <- new.env(); e$x <- 1; attr(f, '.Data') <- e; get('x', envir=f)");

        assertEval("{x <- 1L; get('x', mode='numeric'); }");
        assertEval("{x <- 1L; get('x', mode='double'); }");

        // get('x', envir = 0) => [1] "xv"
        // get('x', envir = 1) => [1] "x"
        assertEval(template("{ x <- 'xv'; get('x', envir = %0) }", envirValues2));

        assertEval(template("{ xx <- 'xv'; get('xx', envir =%0) }", envirValues2));
        assertEval(template("{ xx <- 'xv'; f <- function() { get('xx', envir = %0)}; f() }", envirValues3));
        assertEval(template("{ f <- function() { xx <- 'xv'; f1 <- function() get('xx', envir = %0); f1()}; f() }", envirValues3));

        assertEval(template("{ xx <- 'xv'; get0('xx', envir = %0, ifnotfound = 'DNF') }", envirValues2));
        assertEval(template("{ xx <- 'xv'; f <- function() { get0('xx', envir = %0, ifnotfound = 'DNF')}; f() }", envirValues3));
        assertEval(template("{ f <- function() { xx <- 'xv'; f1 <- function() get0('xx', envir = %0, ifnotfound = 'DNF'); f1()}; f() }", envirValues3));
    }
}
