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

import org.junit.Test;

import com.oracle.truffle.r.test.TestBase;

// Checkstyle: stop line length check

public class TestBuiltin_eval extends TestBase {

    @Test
    public void testEval() {
        assertEval("{ eval(2 ^ 2 ^ 3)}");
        assertEval("{ a <- 1; eval(a) }");
        assertEval("{ a <- 1; eval(a + 1) }");
        assertEval("{ a <- 1; eval(expression(a + 1)) }");
        assertEval("{ f <- function(x) { eval(x) }; f(1) }");
        assertEval("{ eval(x <- 1); ls() }");
        assertEval("{ ne <- new.env(); eval(x <- 1, ne); ls() }");
        assertEval("{ ne <- new.env(); evalq(x <- 1, ne); ls(ne) }");
        assertEval("{ ne <- new.env(); evalq(envir=ne, expr=x <- 1); ls(ne) }");
        assertEval("{ e1 <- new.env(); assign(\"x\", 100, e1); e2 <- new.env(parent = e1); evalq(x, e2) }");

        assertEval("{ f <- function(z) {z}; e<-as.call(c(expression(f), 7)); eval(e) }");

        assertEval("{ f<-function(...) { substitute(list(...)) }; eval(f(c(1,2))) }");
        assertEval("{ f<-function(...) { substitute(list(...)) }; x<-1; eval(f(c(x,2))) }");
        assertEval("{ f<-function(...) { substitute(list(...)) }; eval(f(c(x=1,2))) }");

        assertEval("{ g<-function() { f<-function() { 42 }; substitute(f()) } ; eval(g()) }");
        assertEval("{ g<-function(y) { f<-function(x) { x }; substitute(f(y)) } ; eval(g(42)) }");
        assertEval("{ eval({ xx <- pi; xx^2}) ; xx }");

        assertEval("eval('foo')");
        assertEval("eval(as.symbol('foo'))");
        assertEval("eval(as.symbol('baseenv'))");

        // should print two values, xx^2 and xx
        assertEval("eval({ xx <- pi; xx^2}) ; xx");

        // lists
        assertEval("{ l <- list(a=1, b=2); eval(quote(a), l)}");

        // sys.calls
        assertEval(Ignored.ReferenceError, "{ length(.Internal(eval(quote(foo()),  list2env(list(foo=function() {sys.calls()})), baseenv()))) }");
        // expected to return NULL
        assertEval(Ignored.ImplementationError, "{ .Internal(eval(quote(foo()),  list2env(list(foo=function() {sys.calls()})), baseenv()))[[1]] }");
        assertEval(Ignored.ReferenceError, "{ .Internal(eval(quote(foo()),  list2env(list(foo=function() {sys.calls()})), baseenv()))[[2]] }");

    }

    @Test
    public void testWithEnvirAndEnclose() {
        // note: symbol 'list' is from base environment
        assertEval("a <- 1; lang <- quote(list(a)); eval(lang, data.frame(), NULL)");
        assertEval("a <- 1; lang <- quote(list(a)); eval(lang, NULL, NULL)");
        assertEval("a <- 1; lang <- quote(list(a)); eval(lang, new.env(), new.env())");
        assertEval(Output.IgnoreErrorMessage, "y <- 2; x <- 2 ; eval(quote(x+y), c(-1, -2))");

        // note: 'with' is a oneliner builtin calling into eval
        assertEval("{ df <- list(a=c(1,2,3), b=c(3,4,5)); df$c <- with(df, a^2); df; }");
    }

    @Test
    public void testReturnInEvalExpr() {
        assertEval("f1 <- function(x) { eval(quote(if(x>2){return()}else 1)); 10 };f1(5);f1(0)");
    }

    @Test
    public void testEvalVisibility() {
        assertEval("eval(parse(text='x<-1'))");
        assertEval("eval(parse(text='1+1'))");
    }
}
