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
public class TestBuiltin_paste extends TestBase {

    @Test
    public void testpaste1() {
        assertEval("argv <- list(list('%%  ~~objects to See Also as', '\\\\code{\\\\link{~~fun~~}}, ~~~'), ' ', NULL); .Internal(paste(argv[[1]], argv[[2]], argv[[3]]))");
    }

    @Test
    public void testpaste2() {
        assertEval("argv <- list(list(c('[', 'cox.zph', NA)), ' ', '\\r'); .Internal(paste(argv[[1]], argv[[2]], argv[[3]]))");
    }

    @Test
    public void testpaste3() {
        assertEval("argv <- list(list(), ' ', NULL); .Internal(paste(argv[[1]], argv[[2]], argv[[3]]))");
    }

    @Test
    public void testpaste4() {
        assertEval("argv <- list(list('package', structure('pkgA', .Names = 'name')), ':', NULL); .Internal(paste(argv[[1]], argv[[2]], argv[[3]]))");
    }

    @Test
    public void testpaste5() {
        assertEval("argv <- list(list(c('[', 'as.data.frame', 'plot', 'print', 'summary', 'as.character', 'print', 'print', 'plot', 'update', 'dim', 'dimnames', 'dimnames<-', '[', 't', 'summary', 'print', 'barchart', 'barchart', 'barchart', 'barchart', 'barchart', 'barchart', 'bwplot', 'bwplot', 'densityplot', 'densityplot', 'dotplot', 'dotplot', 'dotplot', 'dotplot', 'dotplot', 'dotplot', 'histogram', 'histogram', 'histogram', 'qqmath', 'qqmath', 'stripplot', 'stripplot', 'qq', 'xyplot', 'xyplot', 'levelplot', 'levelplot', 'levelplot', 'levelplot', 'contourplot', 'contourplot', 'contourplot', 'contourplot', 'cloud', 'cloud', 'cloud', 'wireframe', 'wireframe', 'splom', 'splom', 'splom', 'parallelplot', 'parallelplot', 'parallelplot', 'parallel', 'parallel', 'parallel', 'tmd', 'tmd', 'llines', 'ltext', 'lpoints'), c('shingle', 'shingle', 'shingle', 'shingle', 'shingle', 'shingleLevel', 'shingleLevel', 'trellis', 'trellis', 'trellis', 'trellis', 'trellis', 'trellis', 'trellis', 'trellis', 'trellis', 'summary.trellis', 'formula', 'array', 'default', 'matrix', 'numeric', 'table', 'formula', 'numeric', 'formula', 'numeric', 'formula', 'array', 'default', 'matrix', 'numeric', 'table', 'formula', 'factor', 'numeric', 'formula', 'numeric', 'formula', 'numeric', 'formula', 'formula', 'ts', 'formula', 'table', 'array', 'matrix', 'formula', 'table', 'array', 'matrix', 'formula', 'matrix', 'table', 'formula', 'matrix', 'formula', 'matrix', 'data.frame', 'formula', 'matrix', 'data.frame', 'formula', 'matrix', 'data.frame', 'formula', 'trellis', 'default', 'default', 'default')), '.', NULL); .Internal(paste(argv[[1]], argv[[2]], argv[[3]]))");
    }

    @Test
    public void testpaste6() {
        assertEval("argv <- list(list(c('dotplot', 'table', NA)), ' ', '\\r'); .Internal(paste(argv[[1]], argv[[2]], argv[[3]]))");
    }

    @Test
    public void testpaste7() {
        assertEval("argv <- list(list(character(0)), ' ', ' '); .Internal(paste(argv[[1]], argv[[2]], argv[[3]]))");
    }

    @Test
    public void testpaste8() {
        assertEval("argv <- list(list('detaching', '‘package:splines’'), ' ', NULL); .Internal(paste(argv[[1]], argv[[2]], argv[[3]]))");
    }

    @Test
    public void testpaste9() {
        assertEval("argv <- list(list('GRID', 'text', '6'), '.', NULL); .Internal(paste(argv[[1]], argv[[2]], argv[[3]]))");
    }

    @Test
    public void testpaste10() {
        assertEval("argv <- list(list('(', structure(c(' 1.124', ' 1.056', ' 1.059', ' 0.932'), .Dim = c(2L, 2L)), ','), '', NULL); .Internal(paste(argv[[1]], argv[[2]], argv[[3]]))");
    }

    @Test
    public void testpaste11() {
        assertEval("argv <- list(list(character(0)), ' ', ''); .Internal(paste(argv[[1]], argv[[2]], argv[[3]]))");
    }

    @Test
    public void testPaste() {
        assertEval("{ paste() }");
        assertEval("{ a <- as.raw(200) ; b <- as.raw(255) ; paste(a, b) }");
        assertEval("{ paste(character(0),31415) }");
        assertEval("{ paste(1:2, 1:3, FALSE, collapse=NULL) }");
        assertEval("{ paste(sep=\"\") }");
        assertEval("{ paste(1:2, 1:3, FALSE, collapse=\"-\", sep=\"+\") }");
        assertEval("{ paste(NULL, list(), sep = \"=\") }");
        assertEval("{ paste(NA) }");
        assertEval("{ paste(c(1,NA)) }");
        assertEval("{ s<-c('1',NA); paste(s); s; }");

        assertEval("{ paste(1:3, sep='.') }");
        assertEval("{ paste('a', 1:3, sep='.') }");
        assertEval("{ paste(1:3, 'b', sep='.') }");
        assertEval("{ paste('a', 1:3, 'b', sep='.') }");
        assertEval("{ paste('a', 'a', 1:3, 'b', 'b', sep='.') }");
    }

    @Test
    public void testPasteWithS3AsCharacter() {
        // Note the catch: class on strings is ignored....
        assertEval("{ as.character.myc <- function(x) '42'; val <- 'hello'; class(val) <- 'myc'; paste(val, 'world') }");
        // Next 2 tests should show error since 42, nor NULL is not character
        assertEval("{ as.character.myc <- function(x) 42; val <- 3.14; class(val) <- 'myc'; paste(val, 'world') }");
        assertEval("{ as.character.myc <- function(x) NULL; val <- 3.14; class(val) <- 'myc'; paste(val, 'world') }");
        assertEval("{ as.character.myc <- function(x) '42'; val <- 3.14; class(val) <- 'myc'; paste(val, 'world') }");
        assertEval("{ assign('as.character.myc', function(x) '42', envir=.__S3MethodsTable__.); val <- 3.14; class(val) <- 'myc'; res <- paste(val, 'world'); rm('as.character.myc', envir=.__S3MethodsTable__.); res }");
    }

    @Test
    public void testStringSequence() {
        assertEval("{ paste(\"a\", 1, TRUE, 1:4, 1.2) }");
        assertEval("{ paste(\"a\", 1, TRUE, 1:4) }");
        assertEval("{ paste(1:4, 1.2) }");

        // ISOdate utilizes paste
        assertEval("{ ISOdate(2010, 01, 01, 1:10) }");
    }

    /**
     * recycle0 optional parameter was added to GNU-R version 4.0.1
     */
    @Test
    public void testRecycleParameter() {
        assertEval("{ paste('hello', c(), recycle0=FALSE) }");
        assertEval("{ paste('hello', c(), recycle0=TRUE) }");
        assertEval("{ paste(1:3, 11:13, c(), sep=':', collapse='-', recycle0=FALSE) }");
        assertEval("{ paste(1:3, 11:13, c(), sep=':', collapse='-', recycle0=TRUE) }");
        assertEval("{ paste(1:3, 11:13, c(), sep=':', collapse=NULL, recycle0=FALSE) }");
        assertEval("{ paste(1:3, 11:13, c(), sep=':', collapse=NULL, recycle0=TRUE) }");
    }

    @Test
    public void testRecycleParameterWrongValues() {
        assertEval("{ paste('hello', recycle0=NA) }");
        assertEval("{ paste('hello', recycle0=NULL) }");
        assertEval("{ paste('hello', recycle0=42) }");
        assertEval("{ paste('hello', recycle0=':') }");
        assertEval("{ paste('hello', recycle0=c(TRUE, FALSE)) }");
        assertEval("{ paste('hello', recycle0=c(FALSE, TRUE)) }");
    }
}
