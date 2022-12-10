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
public class TestBuiltin_cat extends TestBase {

    @Test
    public void testcat1() {
        assertEval("argv <- list('head\\n', 1:2, '\\n', 3:4, file = 'foo4');cat(argv[[1]],argv[[2]],argv[[3]],argv[[4]],argv[[5]]);");
    }

    @Test
    public void testcat2() {
        assertEval("argv <- list(list('Loading required package: splines\\n'), structure(2L, class = c('terminal', 'connection')), '', FALSE, NULL, FALSE); .Internal(cat(argv[[1]], argv[[2]], argv[[3]], argv[[4]], argv[[5]], argv[[6]]))");
    }

    @Test
    public void testcat3() {
        assertEval("argv <- list('%comment\\n\\n%another\\n%\\n%\\n', 'C1\\tC2\\tC3\\n\\\'Panel\\\'\\t\\\'Area Examined\\\'\\t\\\'% Blemishes\\\'\\n', '\\\'1\\\'\\t\\\'0.8\\\'\\t\\\'3\\\'\\n', '\\\'2\\\'\\t\\\'0.6\\\'\\t\\\'2\\\'\\n', '\\\'3\\\'\\t\\\'0.8\\\'\\t\\\'3\\\'\\n', file = 'test.dat', sep = '');cat(argv[[1]],argv[[2]],argv[[3]],argv[[4]],argv[[5]],argv[[6]],argv[[7]]);");
    }

    @Test
    public void testcat4() {
        assertEval("argv <- list('#comment\\n\\n#another\\n#\\n#\\n', 'C1\\tC2\\tC3\\n\\\'Panel\\\'\\t\\\'Area Examined\\\'\\t\\\'# Blemishes\\\'\\n', '\\\'1\\\'\\t\\\'0.8\\\'\\t\\\'3\\\'\\n', '\\\'2\\\'\\t\\\'0.6\\\'\\t\\\'2\\\'\\n', '\\\'3\\\'\\t\\\'0.8\\\'\\t\\\'3\\\'\\n', file = 'test.dat', sep = '');cat(argv[[1]],argv[[2]],argv[[3]],argv[[4]],argv[[5]],argv[[6]],argv[[7]]);");
    }

    @Test
    public void testcat5() {
        assertEval("argv <- list('head\\n', file = 'foo2');cat(argv[[1]],argv[[2]]);");
    }

    @Test
    public void testCat() {
        assertEval("{ cat() }");
        assertEval("{ cat(1) }");
        assertEval("{ cat(1, sep=\"\\n\") }");
        assertEval("{ cat(1,2,3) }");
        assertEval("{ cat(\"a\") }");
        assertEval("{ cat(\"a\", \"b\") }");
        assertEval("{ cat(1, \"a\") }");
        assertEval("{ cat(c(1,2,3)) }");
        assertEval("{ cat(c(\"a\",\"b\")) }");
        assertEval("{ cat(c(1,2,3),c(\"a\",\"b\")) }");
        assertEval("{ cat(TRUE) }");
        assertEval("{ cat(TRUE, c(1,2,3), FALSE, 7, c(\"a\",\"b\"), \"x\") }");
        assertEval("{ cat(1:3) }");
        assertEval("{ cat(\"hi\",1:3,\"hello\") }");
        assertEval("{ cat(2.3) }");
        assertEval("{ cat(1.2,3.4) }");
        assertEval("{ cat(c(1.2,3.4),5.6) }");
        assertEval("{ cat(c(TRUE,FALSE), TRUE) }");
        assertEval("{ cat(NULL) }");
        assertEval("{ cat(1L) }");
        assertEval("{ cat(1L, 2L, 3L) }");
        assertEval("{ cat(c(1L, 2L, 3L)) }");
        assertEval("{ cat(1,2,sep=\".\") }");
        assertEval("{ cat(\"hi\",1[2],\"hello\",sep=\"-\") }");
        assertEval("{ cat(\"hi\",1[2],\"hello\",sep=\"-\\n\") }");
        assertEval("{ m <- matrix(as.character(1:6), nrow=2) ; cat(m) }");
        assertEval("{ cat(sep=\" \", \"hello\") }");
        assertEval("{ cat(rep(NA, 8), \"Hey\",\"Hey\",\"Goodbye\",\"\\n\") }");
        assertEval("{ cat(\"hi\",NULL,\"hello\",sep=\"-\") }");
        assertEval("{ cat(\"hi\",integer(0),\"hello\",sep=\"-\") }");
        assertEval("{ cat(\"a\", \"b\", \"c\", sep=c(\"-\", \"+\")) }");

        assertEval("{ cat(c(\"a\", \"b\", \"c\"), \"d\", sep=c(\"-\", \"+\")) }");
        assertEval("{ cat(paste(letters, 100* 1:26), fill = TRUE, labels = paste0(\"{\", 1:10, \"}:\"))}");
        assertEval(Output.IgnoreErrorMessage, "{ foo <- function(a,b) cat(a,b); foo(42,); }");
    }

    @Test
    public void testCatVarargs() {
        assertEval("{ f <- function(...) {cat(...,sep=\"-\")}; f(\"a\") }");
        assertEval("{ f <- function(...) {cat(...,sep=\"-\\n\")}; f(\"a\") }");
        assertEval("{ f <- function(...) {cat(...,sep=\"-\")}; f(\"a\", \"b\") }");
        assertEval("{ f <- function(...) {cat(...,sep=\"-\\n\")}; f(\"a\", \"b\") }");
    }

    @Test
    public void testCatUnsupportedArgs() {
        assertEval("cat(list(), expression(), sep='this-should-be-ok')");
        assertEval("cat(list(1,2,3))");
        assertEval("cat(parse(text='42'))");
        assertEval("cat(quote(a))");
        assertEval("cat(quote(3+3))");
    }
}
