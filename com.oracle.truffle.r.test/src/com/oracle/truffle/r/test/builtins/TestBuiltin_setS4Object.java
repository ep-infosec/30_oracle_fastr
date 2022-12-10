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
 * Copyright (c) 2014, 2019, Oracle and/or its affiliates
 *
 * All rights reserved.
 */
package com.oracle.truffle.r.test.builtins;

import org.junit.Test;

import com.oracle.truffle.r.test.TestBase;

// Checkstyle: stop line length check
public class TestBuiltin_setS4Object extends TestBase {

    @Test
    public void testsetS4Object1() {
        assertEval(Ignored.Unstable,
                        "argv <- list(structure('ObjectsWithPackage', class = structure('signature', package = 'methods'), .Names = '.Object', package = 'methods'), TRUE, 0L); .Internal(setS4Object(argv[[1]], argv[[2]], argv[[3]]))");
    }

    @Test
    public void testsetS4Object2() {
        // Quotes (GnuR) vs. Apostrophes (FastR) used to quote standardGeneric('toeplitz')
        assertEval(Ignored.OutputFormatting,
                        "argv <- list(structure(function (x, ...) standardGeneric('toeplitz'), generic = character(0), package = character(0), group = list(), valueClass = character(0), signature = character(0), default = quote(`\\001NULL\\001`), skeleton = quote(`<undef>`()), class = structure('standardGeneric', package = 'methods')), TRUE, 0L); .Internal(setS4Object(argv[[1]], argv[[2]], argv[[3]]))");
    }

    @Test
    public void testsetS4Object3() {
        assertEval("argv <- list(structure(character(0), package = character(0), class = structure('ObjectsWithPackage', package = 'methods')), TRUE, 0L); .Internal(setS4Object(argv[[1]], argv[[2]], argv[[3]]))");
    }

    @Test
    public void testsetS4Object4() {
        // Quotes (GnuR) vs. Apostrophes (FastR) used to quote standardGeneric('qr.Q')
        assertEval(Ignored.OutputFormatting,
                        "argv <- list(structure(function (qr, complete = FALSE, Dvec) standardGeneric('qr.Q'), generic = character(0), package = character(0), group = list(), valueClass = character(0), signature = character(0), default = quote(`\\001NULL\\001`), skeleton = quote(`<undef>`()), class = structure('standardGeneric', package = 'methods')), TRUE, 0L); .Internal(setS4Object(argv[[1]], argv[[2]], argv[[3]]))");
    }

    @Test
    public void testsetS4Object5() {
        assertEval("argv <- list(structure(c('nonStructure', 'ANY', 'ANY', 'ANY'), .Names = c(NA_character_, NA_character_, NA_character_, NA_character_), package = character(0), class = structure('signature', package = 'methods')), TRUE, 0L); .Internal(setS4Object(argv[[1]], argv[[2]], argv[[3]]))");
    }

    @Test
    public void testsetS4Object6() {
        // FIXME FastR outputs error
        // Error in dimnames(mm) <- list(sigSlots, names(allSlots[[1L]])) :
        // length of 'dimnames' [1] not equal to array extent
        assertEval(Ignored.ImplementationError,
                        "argv <- list(structure(function (x) .Internal(drop(x)), target = structure(character(0), .Names = character(0), package = character(0), class = structure('signature', package = 'methods')), defined = structure(character(0), .Names = character(0), package = character(0), class = structure('signature', package = 'methods')), generic = character(0), class = structure('derivedDefaultMethod', package = 'methods')), TRUE, 0L); .Internal(setS4Object(argv[[1]], argv[[2]], argv[[3]]))");
    }

    @Test
    public void testsetS4Object7() {
        // FIXME FastR outputs error
        // Error in dimnames(mm) <- list(sigSlots, names(allSlots[[1L]])) :
        // length of 'dimnames' [1] not equal to array extent
        assertEval(Ignored.ImplementationError,
                        "argv <- list(structure(function (x, y = NULL) .Internal(crossprod(x, y)), target = structure(character(0), .Names = character(0), package = character(0), class = structure('signature', package = 'methods')), defined = structure(character(0), .Names = character(0), package = character(0), class = structure('signature', package = 'methods')), generic = character(0), class = structure('derivedDefaultMethod', package = 'methods')), TRUE, 0L); .Internal(setS4Object(argv[[1]], argv[[2]], argv[[3]]))");
    }

    @Test
    public void testsetS4Object8() {
        // FIXME FastR outputs error
        // Error in dimnames(mm) <- list(sigSlots, names(allSlots[[1L]])) :
        // length of 'dimnames' [1] not equal to array extent
        assertEval(Ignored.ImplementationError,
                        "argv <- list(structure(function (x, i, j, ...) x@aa[[i]], target = structure(character(0), .Names = character(0), package = character(0), class = structure('signature', package = 'methods')), defined = structure(character(0), .Names = character(0), package = character(0), class = structure('signature', package = 'methods')), generic = character(0), class = structure('MethodDefinition', package = 'methods')), TRUE, 0L); .Internal(setS4Object(argv[[1]], argv[[2]], argv[[3]]))");
    }

    @Test
    public void testsetS4Object9() {
        assertEval("argv <- list(numeric(0), TRUE, 0L); .Internal(setS4Object(argv[[1]], argv[[2]], argv[[3]]))");
    }

    @Test
    public void testsetS4Object10() {
        // FIXME FastR outputs the following error as part of output:
        // Error in dimnames(mm) <- list(sigSlots, names(allSlots[[1L]])) :
        // length of 'dimnames' [1] not equal to array extent
        assertEval(Ignored.ImplementationError,
                        "argv <- list(structure(function (object) cat('I am a \\\'foo\\\'\\n'), target = structure(character(0), .Names = character(0), package = character(0), class = structure('signature', package = 'methods')), defined = structure(character(0), .Names = character(0), package = character(0), class = structure('signature', package = 'methods')), generic = character(0), class = structure('MethodDefinition', package = 'methods')), TRUE, 0L); .Internal(setS4Object(argv[[1]], argv[[2]], argv[[3]]))");
    }

    @Test
    public void testsetS4Object11() {
        // Besides whitespace differences there's just apostrophe vs. quote
        // difference in standardGeneric('diag')
        assertEval(Ignored.OutputFormatting,
                        "argv <- list(structure(function (x = 1, nrow, ncol) standardGeneric('diag'), generic = character(0), package = character(0), group = list(), valueClass = character(0), signature = character(0), default = quote(`\\001NULL\\001`), skeleton = quote(`<undef>`()), class = structure('standardGeneric', package = 'methods')), TRUE, 0L); .Internal(setS4Object(argv[[1]], argv[[2]], argv[[3]]))");
    }

    @Test
    public void testsetS4Object12() {
        // FIXME FastR outputs the following error as part of output:
        // Error in dimnames(mm) <- list(sigSlots, names(allSlots[[1L]])) :
        // length of 'dimnames' [1] not equal to array extent

        // FastR uses apostrophes to quote string: function (object) cat('I am a \'foo\'\n')
        assertEval(Ignored.ImplementationError,
                        "argv <- list(structure(list(`NA` = structure(function (object) cat('I am a \\\'foo\\\'\\n'), target = structure('foo', .Names = 'object', package = 'myTst', class = structure('signature', package = 'methods')), defined = structure('foo', .Names = 'object', package = 'myTst', class = structure('signature', package = 'methods')), generic = structure('show', package = 'methods'), class = structure('MethodDefinition', package = 'methods'))), .Names = NA_character_, arguments = structure('object', simpleOnly = TRUE), signatures = list(), generic = structure(function (object) standardGeneric('show'), generic = structure('show', package = 'methods'), package = 'methods', group = list(), valueClass = character(0), signature = structure('object', simpleOnly = TRUE), default = structure(function (object) showDefault(object, FALSE), target = structure('ANY', class = structure('signature', package = 'methods'), .Names = 'object', package = 'methods'), defined = structure('ANY', class = structure('signature', package = 'methods'), .Names = 'object', package = 'methods'), generic = structure('show', package = 'methods'), class = structure('derivedDefaultMethod', package = 'methods')), skeleton = quote((function (object) showDefault(object, FALSE))(object)), class = structure('standardGeneric', package = 'methods')), class = structure('listOfMethods', package = 'methods')), TRUE, 0L); .Internal(setS4Object(argv[[1]], argv[[2]], argv[[3]]))");
    }

    @Test
    public void testsetS4Object13() {
        // Quotes (GnuR) vs. Apostrophes (FastR) used to quote string.
        assertEval(Ignored.OutputFormatting,
                        "argv <- list(structure(function (x, type = c('O', 'I', 'F', 'M', '2')) {    if (identical('2', type)) {        svd(x, nu = 0L, nv = 0L)$d[1L]    } else .Internal(La_dlange(x, type))}, target = structure(character(0), .Names = character(0), package = character(0), class = structure('signature', package = 'methods')), defined = structure(character(0), .Names = character(0), package = character(0), class = structure('signature', package = 'methods')), generic = character(0), class = structure('derivedDefaultMethod', package = 'methods')), TRUE, 0L); .Internal(setS4Object(argv[[1]], argv[[2]], argv[[3]]))");
    }

    @Test
    public void testsetS4Object14() {
        // Quotes (GnuR) vs. Apostrophes (FastR) used to quote UseMethod('plot')
        assertEval(Ignored.OutputFormatting,
                        "argv <- list(structure(function (x, y, ...) UseMethod('plot'), target = structure(character(0), .Names = character(0), package = character(0), class = structure('signature', package = 'methods')), defined = structure(character(0), .Names = character(0), package = character(0), class = structure('signature', package = 'methods')), generic = character(0), class = structure('derivedDefaultMethod', package = 'methods')), TRUE, 0L); .Internal(setS4Object(argv[[1]], argv[[2]], argv[[3]]))");
    }

    @Test
    public void testSetS4Object() {
        assertEval("{ x<-42; asS4(x, \"TRUE\") }");
        assertEval("{ x<-42; asS4(x, logical()) }");
        assertEval("{ x<-42; asS4(x, c(TRUE, FALSE)) }");
        assertEval("{ x<-42; asS4(x, TRUE, \"1\") }");
        assertEval("{ x<-42; asS4(x, TRUE, logical()) }");
        assertEval("{ x<-42; asS4(x, TRUE, c(1,2)) }");
        assertEval("{ x<-42; asS4(, TRUE, 1)) }");
    }
}
