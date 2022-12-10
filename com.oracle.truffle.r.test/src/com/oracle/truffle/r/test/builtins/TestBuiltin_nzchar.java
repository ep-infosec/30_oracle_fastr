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
 * Copyright (c) 2014, 2018, Oracle and/or its affiliates
 *
 * All rights reserved.
 */
package com.oracle.truffle.r.test.builtins;

import org.junit.Test;

import com.oracle.truffle.r.test.TestBase;

// Checkstyle: stop line length check
public class TestBuiltin_nzchar extends TestBase {

    @Test
    public void testnzchar1() {
        assertEval("argv <- list('./myTst2/man/DocLink-class.Rd');nzchar(argv[[1]]);");
    }

    @Test
    public void testnzchar2() {
        assertEval("argv <- list(FALSE);nzchar(argv[[1]]);");
    }

    @Test
    public void testnzchar3() {
        assertEval("argv <- list(c('a', 'b', 'c'));nzchar(argv[[1]]);");
    }

    @Test
    public void testnzchar4() {
        assertEval("argv <- list(structure('MASS', .Names = ''));nzchar(argv[[1]]);");
    }

    @Test
    public void testnzchar5() {
        assertEval("argv <- list(NULL);nzchar(argv[[1]]);");
    }

    @Test
    public void testnzchar6() {
        assertEval("argv <- list(c('Fr', 'Temp', 'Soft', 'M.user', 'Brand'));nzchar(argv[[1]]);");
    }

    @Test
    public void testnzchar7() {
        assertEval("argv <- list(structure('survival', .Names = ''));nzchar(argv[[1]]);");
    }

    @Test
    public void testnzchar8() {
        assertEval("argv <- list(structure(3.14159265358979, class = structure('3.14159265358979', class = 'testit')));nzchar(argv[[1]]);");
    }

    @Test
    public void testnzchar9() {
        assertEval("argv <- list(c('  \\036 The other major change was an error for asymmetric loss matrices,', '    prompted by a user query.  With L=loss asymmetric, the altered', '    priors were computed incorrectly - they were using L\\' instead of L.', '    Upshot - the tree would not not necessarily choose optimal splits', '    for the given loss matrix.  Once chosen, splits were evaluated', '    correctly.  The printed “improvement” values are of course the', '    wrong ones as well.  It is interesting that for my little test', '    case, with L quite asymmetric, the early splits in the tree are', '    unchanged - a good split still looks good.'));nzchar(argv[[1]]);");
    }

    @Test
    public void testnzchar10() {
        assertEval("argv <- list(logical(0));nzchar(argv[[1]]);");
    }

    @Test
    public void testnzchar12() {
        assertEval("argv <- list('');do.call('nzchar', argv)");
    }

    @Test
    public void keepNATests() {
        assertEval("nzchar(c('asdasd', NA), keepNA=TRUE)");
        assertEval("nzchar(c('asdasd', NA), keepNA=FALSE)");
        assertEval("nzchar(c('aasd', NA), keepNA=NA)");
    }

    @Test
    public void nonStringArgs() {
        assertEval("nzchar(list('x', 42, list('a'), list()))");
        assertEval("nzchar(NULL)");
        assertEval("nzchar(NA)");
        assertEval("nzchar(keepNA=F)");
        assertEval("nzchar(keepNA=NA)");
        assertEval("nchar(wrongArgName=\"a\")");
        assertEval("nchar(wrongArgName='a')");
    }
}
