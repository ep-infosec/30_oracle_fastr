/*
 * Copyright (c) 2016, 2019, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 3 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 3 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 3 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package com.oracle.truffle.r.test.builtins;

import org.junit.After;
import org.junit.Test;

import com.oracle.truffle.r.test.TestBase;

public class TestStackBuiltins extends TestBase {

    private static final String[] FRAME_FUNCTIONS = {
                    "sys.nframe()",
                    "sys.parent()", "sys.parent(2)", "sys.parent(4)",
                    "sys.call()", "sys.call(1)", "sys.call(-2)", "sys.call(4)",
                    "sys.calls()",
                    "{ e <- parent.frame(); e$n}", "{ e <- parent.frame(2); e$n}"
    };

    public static final String DONT_KEEP_SOURCE = "options(keep.source = FALSE);";
    public static final String KEEP_SOURCE = "options(keep.source = TRUE);";

    @Test
    public void testErrors() {
        assertEval("parent.frame(-1)");
    }

    @Override
    @After
    public void afterTest() {
        // Restore the keep.source option
        assertEval(KEEP_SOURCE);
        super.afterTest();
    }

    @Test
    public void testVariations() {
        assertEval(template("n <- 100; %0", FRAME_FUNCTIONS));
        assertEval(template("n <- 100; f <- function() { n <- 101; %0 }; f()", FRAME_FUNCTIONS));
        assertEval(template("n <- 100; g <- function() { n <- 101; f() }; f <- function()  { n <- 102; %0 }; g()", FRAME_FUNCTIONS));
        assertEval(template(DONT_KEEP_SOURCE + "n <- 100; f <- function() { n <- 101; %0 }; ident <- function(x) { n <- 102; x }; ident(f())", FRAME_FUNCTIONS));
        assertEval(template(DONT_KEEP_SOURCE + "n <- 100; f <- function() { n <- 101; %0 }; ident <- function(x) { n <- 102; x }; ident(ident(ident(f())))", FRAME_FUNCTIONS));
        assertEval(template(DONT_KEEP_SOURCE + "n <- 100; g <- function() { n <- 101; f() }; f <- function() { n <- 102; %0 }; ident <- function(x) { n <- 103; x }; ident(g())", FRAME_FUNCTIONS));
        assertEval(template(DONT_KEEP_SOURCE + "n <- 100; g <- function() { n <- 101; ident(f()) }; f <- function() { n <- 102; %0 }; ident <- function(x) { n <- 103; x}; ident(g())",
                        FRAME_FUNCTIONS));
        assertEval(template(DONT_KEEP_SOURCE + "n <- 100; g <- function() { n <- 101; ident(f()) }; f <- function() { n <- 102; ident(%0) }; ident <- function(x) { n <- 103; x }; ident(g())",
                        FRAME_FUNCTIONS));
    }

    @Test
    public void testTry() {
        assertEval(/* Output.IgnoreWhitespace, */template(DONT_KEEP_SOURCE + "n <- 100; f <- function() { n <- 101; %0 }; ident <- function(x) { n <- 102; x }; ident(try(f()))", FRAME_FUNCTIONS));
        assertEval("tryCatch(cat('Hello\\n'))");
        assertEval("tryCatch(print.default('Hello'))");
    }

    @Test
    public void testDataFrame() {
        assertEval(template("n <- 100; data.frame(a=%0,b=%0)", FRAME_FUNCTIONS));
        assertEval(template("n <- 100; f <- function(x) { n <- 101; data.frame(a=as.character(%0),b=as.character(%0),c=x) }; f('foo')", FRAME_FUNCTIONS));
    }

    private static final String[] SIMPLE_FRAME_FUNCTIONS = {
                    "sys.parent()", "sys.parent(2)", "sys.parent(4)",
                    "sys.call()", "sys.call(1)", "sys.call(4)",
                    "{ e <- parent.frame(); e$n}", "{ e <- parent.frame(2); e$n}"
    };
    private static final String[] COMPLEX_FRAME_FUNCTIONS = {
                    "sys.nframe()",
                    "sys.call(-2)",
                    "sys.calls()",
    };

    @Test
    public void testS3() {
        assertEval(template("n <- 100; v <- 123; class(v) <- 'cls'; foo <- function(o) { n <- 101; UseMethod('foo') }; foo.cls <- function(o) { n <- 102; NextMethod() }; " +
                        "foo.default <- function(o) { n <- 103; %0 }; ident <- function(x) { n <- 104; x }; ident(try(foo(v)))", SIMPLE_FRAME_FUNCTIONS));
        // S3 frames are not handled correctly at the moment
        assertEval(Ignored.ImplementationError,
                        template("n <- 100; v <- 123; class(v) <- 'cls'; foo <- function(o) { n <- 101; UseMethod('foo') }; foo.cls <- function(o) { n <- 102; NextMethod() }; " +
                                        "foo.default <- function(o) { n <- 103; %0 }; ident <- function(x) { n <- 104; x }; ident(try(foo(v)))", COMPLEX_FRAME_FUNCTIONS));
    }
}
