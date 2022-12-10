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
 * Copyright (c) 2013, 2020, Oracle and/or its affiliates
 *
 * All rights reserved.
 */
package com.oracle.truffle.r.test.builtins;

import org.junit.Test;

import com.oracle.truffle.r.test.TestBase;

// Checkstyle: stop line length check
public class TestBuiltin_sprintf extends TestBase {

    @Test
    public void testsprintf1() {
        assertEval("argv <- list('%s is not TRUE', 'identical(fxy, c(1, 2, 3))'); .Internal(sprintf(argv[[1]], argv[[2]]))");
    }

    @Test
    public void testsprintf2() {
        assertEval("argv <- list('%1.0f', 3.14159265358979); .Internal(sprintf(argv[[1]], argv[[2]]))");
    }

    @Test
    public void testsprintf3() {
        assertEval("argv <- list('min 3-char string \\'%3s\\'', c('a', 'ABC', 'and an even longer one')); .Internal(sprintf(argv[[1]], argv[[2]]))");
        assertEval("argv <- list('min 10-char string \\'%10s\\'', c('a', 'ABC', 'and an even longer one')); .Internal(sprintf(argv[[1]], argv[[2]]))");
    }

    @Test
    public void testsprintf4() {
        assertEval("argv <- list('%o', integer(0)); .Internal(sprintf(argv[[1]], argv[[2]]))");
    }

    @Test
    public void testsprintf5() {
        assertEval("argv <- list('%*s', 1, ''); .Internal(sprintf(argv[[1]], argv[[2]], argv[[3]]))");
    }

    @Test
    public void testsprintf6() {
        assertEval("argv <- list('p,L,S = (%2d,%2d,%2d): ', TRUE, TRUE, FALSE); .Internal(sprintf(argv[[1]], argv[[2]], argv[[3]], argv[[4]]))");
    }

    @Test
    public void testsprintf7() {
        assertEval("argv <- list('p,L,S = (%2d,%2d,%2d): ', TRUE, FALSE, NA); .Internal(sprintf(argv[[1]], argv[[2]], argv[[3]], argv[[4]]))");
    }

    @Test
    public void testsprintf8() {
        assertEval("argv <- list('plot_%02g', 1L); .Internal(sprintf(argv[[1]], argv[[2]]))");
    }

    @Test
    public void testsprintf9() {
        assertEval("argv <- list('tools:::.createExdotR(\\\'%s\\\', \\\'%s\\\', silent = TRUE, use_gct = %s, addTiming = %s)', structure('KernSmooth', .Names = 'Package'), '/home/lzhao/hg/r-instrumented/library/KernSmooth', FALSE, FALSE); .Internal(sprintf(argv[[1]], argv[[2]], argv[[3]], argv[[4]], argv[[5]]))");
    }

    @Test
    public void testsprintf10() {
        assertEval("argv <- list('%.0f%% said yes (out of a sample of size %.0f)', 66.666, 3); .Internal(sprintf(argv[[1]], argv[[2]], argv[[3]]))");
    }

    @Test
    public void testsprintf11() {
        assertEval("argv <- list('%1$d %1$x %1$X', 0:15); .Internal(sprintf(argv[[1]], argv[[2]]))");
    }

    @Test
    public void testsprintf12() {
        assertEval("argv <- list('%03o', 1:255); .Internal(sprintf(argv[[1]], argv[[2]]))");
    }

    @Test
    public void testsprintf13() {
        assertEval("argv <- list('%d y value <= 0 omitted from logarithmic plot', 1L); .Internal(sprintf(argv[[1]], argv[[2]]))");
    }

    @Test
    public void testsprintf14() {
        assertEval("argv <- list('%o', 1:255); .Internal(sprintf(argv[[1]], argv[[2]]))");
    }

    @Test
    public void testsprintf15() {
        assertEval("argv <- list('%s-class.Rd', structure('foo', .Names = 'foo')); .Internal(sprintf(argv[[1]], argv[[2]]))");
    }

    @Test
    public void testsprintf16() {
        assertEval("argv <- list('checkRd: (%d) %s', -3, 'evalSource.Rd:157: Unnecessary braces at ‘{\\\'sourceEnvironment\\\'}’'); .Internal(sprintf(argv[[1]], argv[[2]], argv[[3]]))");
    }

    @Test
    public void testsprintf17() {
        assertEval("argv <- list('tools:::check_compiled_code(\\\'%s\\\')', '/home/lzhao/hg/r-instrumented/library/foreign'); .Internal(sprintf(argv[[1]], argv[[2]]))");
    }

    @Test
    public void testsprintf18() {
        // FIXME %5g should mean 5 significant digits and padded from left to 5 characters
        assertEval(Ignored.ImplementationError,
                        "argv <- list('%5g', structure(c(18, 18, 0, 14, 4, 12, 12, 0, 4, 8, 26, 23, 3, 18, 5, 8, 5, 3, 0, 5, 21, 0, 21, 0, 0), .Dim = c(5L, 5L), .Dimnames = list(NULL, c('', '', '', '', '')))); .Internal(sprintf(argv[[1]], argv[[2]]))");
    }

    @Test
    public void testsprintf19() {
        assertEval("argv <- list('%G', 3.14159265358979e-06); .Internal(sprintf(argv[[1]], argv[[2]]))");
    }

    @Test
    public void testsprintf21() {
        assertEval("argv <- structure(list(fmt = '%9.4g', 12345.6789), .Names = c('fmt',     ''));do.call('sprintf', argv)");
    }

    @Test
    public void testSprintf() {
        assertEval("{ sprintf(\"0x%x\",1L) }");
        assertEval("{ sprintf(\"0x%x\",10L) }");
        assertEval("{ sprintf(\"%d%d\",1L,2L) }");
        assertEval("{ sprintf(\"0x%x\",1) }");
        assertEval("{ sprintf(\"0x%x\",10) }");
        assertEval("{ sprintf(\"%d\", 10) }");
        assertEval("{ sprintf(\"%7.3f\", 10.1) }");
        assertEval("{ sprintf(\"%03d\", 1:3) }");
        assertEval("{ sprintf(\"%3d\", 1:3) }");
        assertEval("{ sprintf(\"%4X\", 26) }");
        assertEval("{ sprintf(\"%04X\", 26) }");
        assertEval("{ sprintf(\"Hello %*d\", 3, 2) }");
        assertEval("{ sprintf(\"Hello %*2$d\", 3, 2) }");
        assertEval("{ sprintf(\"Hello %2$*2$d\", 3, 2) }");
        assertEval("{ sprintf(\"%d\", as.integer(c(7))) }");
        assertEval("{ sprintf(\"%d\", as.integer(c(7,42)), as.integer(c(1,2))) }");
        assertEval("{ sprintf(\"%d %d\", as.integer(c(7,42)), as.integer(c(1,2))) }");
        assertEval("{ sprintf(\"%d %d\", as.integer(c(7,42)), as.integer(1)) }");
        assertEval("{ sprintf(\"%d %d\", as.integer(c(7,42)), integer()) }");
        assertEval("{ sprintf(\"foo\") }");
        assertEval("{ sprintf(c(\"foo\", \"bar\")) }");
        assertEval("{ sprintf(c(\"foo %f\", \"bar %f\"), 7) }");
        assertEval("{ sprintf(c(\"foo %f %d\", \"bar %f %d\"), 7, 42L) }");
        assertEval("{ sprintf(c(\"foo %f %d\", \"bar %f %d\"), c(7,1), c(42L, 2L)) }");
        assertEval("{ sprintf(\"%.3g\", 1.234) }");
        assertEval("{ sprintf('plot_%02g', 3L) }");
        assertEval("{ sprintf(c('hello', 'world'), NULL) }");
        assertEval("{ sprintf('%s', list('hello world')) }");

        assertEval("{ sprintf('%.3d', 4.0) }");
        assertEval("{ sprintf('%.3i', 4.0) }");

        // Note: we save the result to variable to avoid diff because of different formatting,
        // however, at least we test that the format expression is not causing any problems to
        // FastR.
        assertEval("{ asdfgerta <- sprintf('%0.f', 1); }");
        assertEval("{ asdfgerta <- sprintf('%0.0f', 1); }");
        assertEval("{ asdfgerta <- sprintf('%.0f', 1); }");

        // g/G behaves differently in Java vs C w.r.t. trailing zeroes after decimal point
        assertEval("{ sprintf('%.3g', 4.0) }");
        assertEval("{ sprintf('%+g', 4.0) }");
        assertEval("{ sprintf('% g', 4.0) }");
        assertEval("{ sprintf('%.3g', 4.33) }");
        assertEval("{ sprintf('%+g', 4.33) }");
        assertEval("{ sprintf('% g', 4.33) }");
        assertEval("{ sprintf('%g', 4.3345423) }");

        // If one of args is NULL or an empty vector, sprintf should produce character(0).
        assertEval("{ sprintf('%s%d', 'Hello', c()) }");
        assertEval("{ sprintf('%s%d', 'Hello', NULL) }");
        assertEval("{ sprintf('%d%s', NULL, 'Hello') }");
        assertEval("{ sprintf('%s%d', 'Hello', seq_along(c())) }");

        assertEval(Ignored.ImplementationError, "{ sprintf('%#g', 4.0) }");
    }

    @Test
    public void testGenericDispatch() {
        // from the doc: sprintf uses as.character for %s arguments, and as.double for %f, %E, ...
        assertEval(Ignored.Unimplemented, "{ as.character.myclass65231 <- function(x) '42'; y <- 2; class(y) <- 'myclass65231'; sprintf('%s', y); }");
        assertEval(Ignored.Unimplemented, "{ as.double.myclass65321 <- function(x) 3.14; y <- 3L; class(y) <- 'myclass65321'; sprintf('%g', y); }");
        // catch: probably before calling as.double there is numeric validation? Strings are not OK
        // even, when they have as.double function
        assertEval(Ignored.Unimplemented, "{ as.double.myclass65321 <- function(x) 3.14; y <- 'str'; class(y) <- 'myclass65321'; sprintf('%g', y); }");
    }

    @Test
    public void testCornerCases() {
        assertEval(Ignored.ImplementationError, "{ sprintf(c('hello %d %s', 'world %d %s'), list(2, 'x')) }");
        assertEval("{ sprintf('limited to %d part%s due to %.0f', 3L, 3.3) }");
    }

    @Test
    public void testConversions() {
        assertEval("{ sprintf('limited to %d part%s due to %.0f', 3L, 'a', 3L) }");
        assertEval(template("{ sprintf('%s', %0) }", new String[]{"1L", "2", "2.2", "TRUE"}));
        assertEval(template("{ sprintf('%d', %0) }", new String[]{"1L", "2", "2.2", "TRUE"}));
        assertEval(template("{ sprintf('%f', %0) }", new String[]{"1L", "2", "2.2", "TRUE"}));
        assertEval("sprintf('%02d', as.integer(NA))");
        // Note: as.raw may be problematic also in the case of %d, %f, ...
        assertEval(Ignored.Unimplemented, "{ sprintf('%s', as.raw(1)) }");
    }
}
