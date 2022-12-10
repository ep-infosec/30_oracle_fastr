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
package com.oracle.truffle.r.test.functions;

import org.junit.Test;

import com.oracle.truffle.r.test.TestBase;

public class TestFunctions extends TestBase {

    @Test
    public void testFunctionLookup() {
        assertEval(Output.IgnoreErrorContext, "{ f<-1; f() }");
        assertEval("{ abs }");
        assertEval(Output.IgnoreErrorContext, "{ foo() }");
        assertEval(Output.IgnoreErrorContext, "{ 'foo'() }");
        // these errors will be fixed by proper handling of "("
        assertEval("{ (foo)() }");
        assertEval("{ ('foo')() }");
        assertEval("{ foo <- function() 1; foo() }");
        assertEval("{ foo <- function() 1; 'foo'() }");
        assertEval("{ foo <- function() 1; `foo`() }");
        assertEval("{ foo <- function() 1; (foo)() }");
        assertEval("{ foo <- function() 1; ('foo')() }");
        assertEval("{ foo <- function() 1; (`foo`)() }");
        assertEval("{ sum <- 1; sum(1,2) }");
        assertEval("{ sum <- 1; `sum`(1,2) }");
        assertEval("{ sum <- 1; 'sum'(1,2) }");
        assertEval("{ sum <- 1; (sum)(1,2) }");
        assertEval("{ sum <- 1; ('sum')(1,2) }");
        assertEval("{ sum <- 1; (`sum`)(1,2) }");
    }

    @Test
    public void testDefinitionsWorking() {
        assertEval("{ x<-function(z){z} ; x(TRUE) }");
        assertEval("{ x<-1 ; f<-function(){x} ; x<-2 ; f() }");
        assertEval("{ x<-1 ; f<-function(x){x} ; f(TRUE) }");
        assertEval("{ x<-1 ; f<-function(x){a<-1;b<-2;x} ; f(TRUE) }");
        assertEval("{ f<-function(x){g<-function(x) {x} ; g(x) } ; f(TRUE) }");
        assertEval("{ x<-1 ; f<-function(x){a<-1; b<-2; g<-function(x) {b<-3;x} ; g(b) } ; f(TRUE) }");
        assertEval("{ x<-1 ; f<-function(z) { if (z) { x<-2 } ; x } ; x<-3 ; f(FALSE) }");
        assertEval("{ f<-function() {z} ; z<-2 ; f() }");
        assertEval("{ x<-1 ; g<-function() { x<-12 ; f<-function(z) { if (z) { x<-2 } ; x } ; x<-3 ; f(FALSE) } ; g() }");
        assertEval("{ x<-function() { z<-211 ; function(a) { if (a) { z } else { 200 } } } ; f<-x() ; z<-1000 ; f(TRUE) }");
    }

    @Test
    public void testInvocation() {
        assertEval("{ g <- function(...) { max(...) } ; g(1,2) }");
        assertEval("{ f <- function(a, ...) { list(...) } ; f(1) }");

        assertEval("{ rnorm(n = 1, n = 2) }");
        assertEval("{ rnorm(s = 1, s = 1) }");
        assertEval("{ matrix(1:4, n = 2) }");

        assertEval("{ matrix(da=1:3,1) }");

        assertEval("{ f <- function(...) { l <- list(...) ; l[[1]] <- 10; ..1 } ; f(11,12,13) }");
        assertEval("{ g <- function(...) { length(list(...)) } ; f <- function(...) { g(..., ...) } ; f(z = 1, g = 31) }");
        assertEval("{ g <- function(...) { `-`(...) } ; g(1,2) }");
        assertEval("{ f <- function(...) { list(a=1,...) } ; f(b=2,3) }");
        assertEval("{ f <- function(...) { substitute(...) } ; f(x + z) } ");
        assertEval("{ p <- function(prefix, ...) { cat(prefix, ..., \"\\n\") } ; p(\"INFO\", \"msg:\", \"Hello\", 42) }");

        assertEval("{ f <- function(...) { g <- function() { list(...)$a } ; g() } ; f(a=1) }");
        assertEval("{ f <- function(...) { args <- list(...) ; args$name } ; f(name = 42) }");

        assertEval("{ matrix(x=1) }");
        assertEval("{ set.seed(4357); round( rnorm(1,), digits = 5 ) }");
        // FIXME UnsupportedSpecializationException: Unexpected values provided for
        // PrecedenceNodeGen@7c078bf0: [empty, false], [REmpty,Boolean]
        assertEval(Ignored.ImplementationError, "{ max(1,2,) }");
        assertEval("{ f <- function(a) a; f(cat('Hello\\n')) }");
    }

    @Test
    public void testReturn() {
        assertEval("{ f<-function() { return() } ; f() }");
        assertEval("{ f<-function() { return(2) ; 3 } ; f() }");
        assertEval("{ f<-function() { return(invisible(2)) } ; f() }");
    }

    @Test
    public void testDefinitionsNamedAndDefault() {
        assertEval("{ f<-function(a=1,b=2,c=3) {TRUE} ; f(,,) }");

        assertEval("{ f<-function(x=2) {x} ; f() } ");
        assertEval("{ f<-function(a,b,c=2,d) {c} ; f(1,2,c=4,d=4) }");
        assertEval("{ f<-function(a,b,c=2,d) {c} ; f(1,2,d=8,c=1) }");
        assertEval("{ f<-function(a,b,c=2,d) {c} ; f(1,d=8,2,c=1) }");
        assertEval("{ f<-function(a,b,c=2,d) {c} ; f(d=8,1,2,c=1) }");
        assertEval("{ f<-function(a,b,c=2,d) {c} ; f(d=8,c=1,2,3) }");
        assertEval("{ f<-function(a=10,b,c=20,d=20) {c} ; f(4,3,5,1) }");

        assertEval("{ x<-1 ; z<-TRUE ; f<-function(y=x,a=z,b) { if (z) {y} else {z}} ; f(b=2) }");
        assertEval("{ x<-1 ; z<-TRUE ; f<-function(y=x,a=z,b) { if (z) {y} else {z}} ; f(2) }");
        assertEval("{ x<-1 ; f<-function(x=x) { x } ; f(x=x) }");
        assertEval("{ f<-function(z, x=if (z) 2 else 3) {x} ; f(FALSE) }");

        assertEval("{f<-function(a,b,c=2,d) {c} ; g <- function() f(d=8,c=1,2,3) ; g() ; g() }");

        assertEval("{ x <- function(y) { sum(y) } ; f <- function() { x <- 1 ; x(1:10) } ; f() }");
        assertEval("{ f <- sum ; f(1:10) }");
    }

    @Test
    public void testDefinitions() {
        assertEval("{ \"%plus%\" <- function(a,b) a+b ; 3 %plus% 4 }");
        assertEval("{ \"-\"(1) }");
        assertEval("x<-function(){1};x");

        // replacement function
        assertEval("{ 'my<-' <- function(x, value) { attr(x, \"myattr\") <- value ; x } ; z <- 1; my(z) <- \"hello\" ; z }");
        assertEval("{ x<-matrix(1:4, ncol=2); y<-list(x=c(\"a\", \"b\"), y=c(\"c\", \"d\")); dimnames(x)<-y; names(dimnames(x))<-c(\"m\", \"n\"); dimnames(x) }");

        assertEval("{ x <- function(a,b) { a^b } ; f <- function() { x <- \"sum\" ; sapply(1, x, 2) } ; f() }");
        assertEval("{ x <- function(a,b) { a^b } ; g <- function() { x <- \"sum\" ; f <- function() { sapply(1, x, 2) } ; f() }  ; g() }");
        assertEval("{ foo <- function (x) { x } ; foo() }");

        assertEval("{ x <- function(a,b) { a^b } ; dummy <- sum ; f <- function() { x <- \"dummy\" ; sapply(1, x, 2) } ; f() }");
        assertEval("{ x <- function(a,b) { a^b } ; dummy <- sum ; f <- function() { x <- \"dummy\" ; dummy <- 200 ; sapply(1, x, 2) } ; f() }");

        assertEval("{ foo <- function (x) { x } ; foo(1,2,3) }");

        assertEval("{ x <- function(a,b) { a^b } ; f <- function() { x <- 211 ; sapply(1, x, 2) } ; f() }");

        // weird syntax, that doesn't work, but should not cause errors
        assertEval("{ foo <- function(x, ...=NULL) x; list(foo(42), foo(21,33)); }");
        assertEval("{ foo <- function(x, ..) x; list(foo(42), foo(21,33)); }");
        assertEval("{ side <- function(x) { cat('side effect ', x, '\\n'); x }; foo <- function(x, ...=side(42)) x;  foo(3) }");
        assertEval("{ side <- function(x) { cat('side effect ', x, '\\n'); x }; foo <- function(x, ..1=side(42)) x;  foo(3) }");

        // the weird parameter is still used in positional arg matching
        assertEval("{ foo <- function(x, ..1, y) y; foo(21,33,22); }");
    }

    @Test
    public void testEmptyParamName() {
        assertEval("{ f <- function(a, ...) a; f(''=123) }");
        assertEval(Ignored.ParserError, "{ function(''=123) 4 }");
    }

    @Test
    public void testErrors() {
        assertEval("{ x<-function(){1} ; x(y=sum(1:10)) }");
        assertEval("{ x<-function(){1} ; x(1) }");
        assertEval("{ f <- function(x) { x } ; f() }");
        assertEval("{ x<-function(y,b){1} ; x(y=1,y=3,4) }");
        assertEval("{ x<-function(foo,bar){foo*bar} ; x(fo=10,f=1,2) }");
        assertEval("{ x<-function(){1} ; x(y=1) }");
        assertEval("{ f <- function(a,b,c,d) { a + b } ; f(1,x=1,2,3,4) }");

        // FIXME GnuR's error message more precise
        // Expected output: Error in x(y = 1, 2, 3, z = 5) : unused arguments (3, z = 5)
        // FastR output: Error in x(y = 1, 2, 3, z = 5) : unused argument (z = 5)
        assertEval(Ignored.ImplementationError, "{ x<-function(y, b){1} ; x(y=1, 2, 3, z = 5) }");
        assertEval("{ x<-function(a){1} ; x(1,) }");

        // FIXME error message missing
        // Expected output: Error: repeated formal argument 'a' on line 1
        // FastR output:
        assertEval(Ignored.ImplementationError, Output.IgnoreErrorContext, "{ f <- function(a,a) {1} }");
    }

    @Test
    public void testBinding() {
        assertEval("{ myapp <- function(f, x, y) { f(x,y) } ; myapp(function(x,y) { x + y }, 1, 2) ; myapp(sum, 1, 2) }");
        assertEval("{ myapp <- function(f, x, y) { f(x,y) } ; myapp(f = function(x,y) { x + y }, y = 1, x = 2) ; myapp(f = sum, x = 1, y = 2) }");
        assertEval("{ myapp <- function(f, x, y) { f(x,y) } ; myapp(f = function(x,y) { x + y }, y = 1, x = 2) ; myapp(f = sum, x = 1, y = 2) ; myapp(f = c, y = 10, x = 3) }");
        assertEval("{ myapp <- function(f, x, y) { f(x,y) } ; myapp(f = function(x,y) { x + y }, y = 1, x = 2) ; myapp(f = sum, x = 1, y = 2) ; myapp(f = function(x,y) { x - y }, y = 10, x = 3) }");
        assertEval("{ myapp <- function(f, x, y) { f(x,y) } ; g <- function(x,y) { x + y } ; myapp(f = g, y = 1, x = 2) ; myapp(f = sum, x = 1, y = 2) ; myapp(f = g, y = 10, x = 3) ;  myapp(f = g, y = 11, x = 2) }");
        assertEval("{ f <- function(i) { if (i==2) { c <- sum }; c(1,2) } ; f(1) ; f(2) }");
        assertEval("{ f <- function(i) { if (i==2) { assign(\"c\", sum) }; c(1,2) } ; f(1) ; f(2) }");
        assertEval("{ f <- function(func, arg) { func(arg) } ; f(sum, c(3,2)) ; f(length, 1:4) }");
        assertEval("{ f <- function(func, arg) { func(arg) } ; f(sum, c(3,2)) ; f(length, 1:4) ; f(length,1:3) }");
        assertEval("{ f <- function(func, arg) { func(arg) } ; f(sum, c(3,2)) ; f(length, 1:4) ; f(function(i) {3}, 1) ; f(length,1:3) }");
        assertEval("{ f <- function(func, a) { if (func(a)) { 1 } else { 2 } } ; f(function(x) {TRUE}, 5) ; f(is.na, 4) }");
        assertEval("{ f <- function(func, a) { if (func(a)) { 1 } else { 2 } } ; g <- function(x) {TRUE} ; f(g, 5) ; f(is.na, 4) ; f(g, 3) }");
        assertEval("{ f <- function(func, a) { if (func(a)) { 1 } else { 2 } } ; g <- function(x) {TRUE} ; f(g, 5) ; f(is.na, 4) ; h <- function(x) { x == x } ; f(h, 3) }");
        assertEval("{ f <- function(func, a) { if (func(a)) { 1 } else { 2 } } ; g <- function(x) {TRUE} ; f(g, 5) ; f(is.na, 4) ; f(g, 3) ; f(is.na, 10) }");
        assertEval("{ f <- function(func, a) { if (func(a)) { 1 } else { 2 } } ; g <- function(x) {TRUE} ; f(g, 5) ; f(is.na, 4) ; f(g, 3) ; f(c, 10) }");
        assertEval("{ f <- function(func, a) { if (func(a)) { 1 } else { 2 } } ; g <- function(x) {TRUE} ; f(g, 5) ; f(is.na, 4) ; f(g, 3) ; f(function(x) { 3+4i }, 10) }");
        assertEval("{ f <- function(func, a) { if (func(a)) { 1 } else { 2 } } ; g <- function(x) {TRUE} ; f(g, 5) ; f(is.na, 4) ; f(is.na, 10) }");
        assertEval("{ f <- function(func, a) { func(a) && TRUE } ; g <- function(x) {TRUE} ; f(g, 5) ; f(is.na, 4)  }");
        assertEval("{ f <- function(func, a) { func(a) && TRUE } ; g <- function(x) {TRUE} ; f(g, 5) ; f(is.na, 4) ; f(is.na, 10) }");
        assertEval("{ f <- function(func, a) { func(a) && TRUE } ; g <- function(x) {TRUE} ; f(g, 5) ; f(is.na, 4) ; f(length, 10) }");
        assertEval("{ f <- function(func, a) { func(a) && TRUE } ; g <- function(x) {TRUE} ; f(g, 5) ; f(is.na, 4) ; f(g, 10) ; f(is.na,5) }");
        assertEval("{ f <- function(func, a) { func(a) && TRUE } ; g <- function(x) {TRUE} ; f(g, 5) ; f(is.na, 4) ; f(function(x) { x + x }, 10) }");

        assertEval("{ f <- function(i) { c(1,2) } ; f(1) ; c <- sum ; f(2) }");
    }

    @Test
    public void testRecursion() {
        assertEval("{ f<-function(i) { if(i==1) { 1 } else { j<-i-1 ; f(j) } } ; f(10) }");
        assertEval("{ f<-function(i) { if(i==1) { 1 } else { f(i-1) } } ; f(10) }");
        assertEval("{ f<-function(i) { if(i<=1) 1 else i*f(i-1) } ; f(10) }"); // factorial
        assertEval("{ f<-function(i) { if(i<=1L) 1L else i*f(i-1L) } ; f(10L) }"); // factorial
        // 100 times calculate factorial of 100
        assertEval("{ f<-function(i) { if(i<=1) 1 else i*f(i-1) } ; g<-function(n, f, a) { if (n==1) { f(a) } else { f(a) ; g(n-1, f, a) } } ; g(100,f,100) }");

        // Fibonacci numbers
        assertEval("{ f<-function(i) { if (i==1) { 1 } else if (i==2) { 1 } else { f(i-1) + f(i-2) } } ; f(10) }");
        assertEval("{ f<-function(i) { if (i==1L) { 1L } else if (i==2L) { 1L } else { f(i-1L) + f(i-2L) } } ; f(10L) }");
    }

    @Test
    public void testPromises() {
        assertEval("{ z <- 1 ; f <- function(c = z) { c(1,2) ; z <- z + 1 ; c  } ; f() }");
        assertEval("{ f <- function(x) { for (i in 1:10) { x <- g(x,i) }; x }; g <- function(x,i) { x + i }; f(2) }");
        assertEval("{ f <- function(x = z) { z = 1 ; x } ; f() }");
        assertEval("{ z <- 1 ; f <- function(c = z) {  z <- z + 1 ; c  } ; f() }");
        assertEval("{ f <- function(a) { g <- function(b) { x <<- 2; b } ; g(a) } ; x <- 1 ; f(x) }");
        assertEval("{ f <- function(a) { g <- function(b) { a <<- 3; b } ; g(a) } ; x <- 1 ; f(x) }");
        assertEval("{ f <- function(x) { function() {x} } ; a <- 1 ; b <- f(a) ; a <- 10 ; b() }");
        assertEval("{ f <- function(x = y, y = x) { y } ; f() }");
        assertEval("{ foo <- function(a,b) { x<<-4; b; }; x <- 0; foo(2, x > 2); }");
        // FIXME eager promises bug
        // Note: following test fails on the first invocation only, consequent invocations produce
        // correct result. The problem is that OptForcedEagerPromiseNode is not creating assumptions
        // that variables involved in the eagerly evaluated expression are no being updated in the
        // meantime as side effect of evaluation of some other argument.
        assertEval(Ignored.ImplementationError, "{ foo <- function(x,z) x + z; x <- 4; bar <- function() { x <<- 10; 1; }; foo(bar(), x); }");
        assertEval(Ignored.ImplementationError, "{ foo <- function(x,z) list(x,z); x <- 4; bar <- function() { x <<- 10; 1; }; foo(bar(), x > 5); }");
        // FIXME eager promises bug2
        // If eager promise evaluates to another promise, that promise should only be evaluated if
        // it is again a simple expression without any side effects.
        assertEval(Ignored.ImplementationError, "{ bar <- function(x, y) { y; x; 42 }; foo <- function(a) bar(a, cat('side2')); foo(cat('side1')) }");

        assertEval("{ options(list(width=80)); f <- function(arg, defArg = getOption('width')) { print(arg + defArg); print(defArg) }; f(80L); f(80L); f(80L) }");
        assertEval("{ x <- rep(80, 1); f <- function(arg, defArg = x) {print(arg + defArg); print(defArg)}; f(80L); f(80L); f(80L) }");
    }

    @Test
    public void testVarArgPromises() {
        assertEval("g <- function(e) get(\"ex\", e);f <- function(e, en) {  exports <- g(e);  unlist(lapply(en, get, envir = exports, inherits = FALSE))}; " +
                        "e1 <- new.env(); e1n <- new.env(); assign(\"a\", \"isa\", e1n); assign(\"ex\", e1n, e1); " +
                        "e2 <- new.env(); e2n <- new.env(); assign(\"b\", \"isb\", e2n); assign(\"ex\", e2n, e2); ex1 <- c(\"a\"); ex2 <- c(\"b\"); f(e1, ex1); f(e2, ex2) == \"isb\"");
        assertEval("rule <- 1; stopifnot((lenR <- length(rule)) >= 1L, lenR <= 2L)");
        assertEval("{ x <- 3; stopifnot((x <- 5) < 10, x == 5); }");
        // assignment in arguments + function that has a fast path
        assertEval("{ seq((abc <- 1), length.out = abc + 1) }");
        assertEval("{ seq((abc <- 1), abc + 10) }");
    }

    @Test
    public void testArgEvaluationOrder() {
        assertEval("v <- 1; class(v) <- 'foo'; `-.foo` <- function(x,y,...) { cat('[-.foo]'); 1234 }; g <- function(...) { cat('[ing]'); ({cat('[-]'); `-`})(...) }; ({cat('[g]'); g})({cat('[x]'); v},{cat('[y]'); 3},{cat('[z]'); 5})");
    }

    @Test
    public void testMatching() {
        assertEval("{ x<-function(foo,bar){foo*bar} ; x(f=10,2) }");
        assertEval("{ x<-function(foo,bar){foo*bar} ; x(fo=10, bar=2) }");
        assertEval("{ f <- function(hello, hi) { hello + hi } ; f(h = 1) }");
        assertEval("{ f <- function(hello, hi) { hello + hi } ; f(hello = 1, bye = 3) }");
        assertEval("{ f <- function(a) { a } ; f(1,2) }");

        assertEval("{ f <- function(xy, x) xy + x; f(xy=1,x=2) }");

        // with ... partial-match only if formal parameter are before ...
        assertEval("{ f<-function(..., val=1) { c(list(...), val) }; f(v=7, 2) }");
        assertEval("{ f<-function(er=1, ..., val=1) { c(list(...), val, er) }; f(v=7, 2, e=8) }");

        // exact match of 'xa' is not "stolen" by partial match of 'x'
        assertEval("{ foo <- function(xa, ...) list(xa=xa, ...); foo(x=4,xa=5); }");
        // however, two partial matches produce error, even if one is "longer"
        assertEval("{ foo <- function(xaaa, ...) list(xaa=xaaa, ...); foo(xa=4,xaa=5); }");
        assertEval("list(`...`=NULL);");
        assertEval("`$<-`(someNonesense = list(), anotherNonesense = 'foo', 42)");

        // Unmatched argument from delegated "..."
        // FastR does not provide "c=3", but only "3" in the error message
        assertEval(Output.IgnoreErrorMessage, "foo <- function(...) bar(...); bar <- function(a,b) list(a,b); foo(a=1,b=2,c=3);");

        // no named arguments matching for some primitives
        assertEval("as.character(3, x=42)");

        // "drop" argument matched by name, can be anywhere, but not the first one
        assertEval("matrix(1:6, nrow = 2, dimnames = list(c('a', 'b'), LETTERS[1:3]))[1,,drop=FALSE]");
        assertEval("matrix(1:6, nrow = 2, dimnames = list(c('a', 'b'), LETTERS[1:3]))[1,drop=FALSE,]");
        assertEval("matrix(1:6, nrow = 2, dimnames = list(c('a', 'b'), LETTERS[1:3]))[drop=FALSE,1,]");
        // first actual argument is not considered for matching to "drop" formal argument
        assertEval("`[`(drop=matrix(1:6, nrow = 2, dimnames = list(c('a', 'b'), LETTERS[1:3])), 1,)");
        assertEval("`[`(drop=matrix(1:6, nrow = 2, dimnames = list(c('a', 'b'), LETTERS[1:3])), 1,, drop=F)");
        // "i" and "j" argument names are ignored
        assertEval("matrix(1:6, nrow = 2, dimnames = list(c('a', 'b'), LETTERS[1:3]))[drop=FALSE,j=1,]");

        assertEval("list(abc=3)[[exact=F,'ab']]");
        assertEval("list(abc=3)[['ab',exact=F]]");
        assertEval("list(abc=3)[[exact=F,'ab',drop=T]]");
        assertEval("list(abc=3)[[drop=T,exact=F,'ab']]");

        assertEval(".subset2(c(1,2,3), 2, exact = T)");
        assertEval(".subset2(c(1,2,3), exact = T, 2)");
        assertEval(".subset2(exact = T, c(1,2,3), 2)");
        assertEval(".subset2(c(1,2,3), exact = T, 2, drop=T)");

        assertEval(".subset(c(1,2,3), drop = T, 2)");
        assertEval(".subset(c(1,2,3), 2, drop = T)");

        assertEval("c(1,2,3)[[drop=T,3,drop=T]]");
        assertEval("c(1,2,3)[[extract=T,3,drop=T]]");
        assertEval("c(1,2,3)[extract=T,3,drop=T]");

        // This would fail if we matched arguments of forceAndCall by name\
        assertEval("apply(array(1,dim=c(2,3)), 2, function(x,n) x, n = 1)");
    }

    @Test
    public void testDots() {
        assertEval("{ f <- function(a, b) { a - b } ; g <- function(...) { f(1, ...) } ; g(b = 2) }");
        assertEval("{ f <- function(...) { g(...) } ;  g <- function(b=2) { b } ; f() }");
        assertEval("{ f <- function(a, barg) { a + barg } ; g <- function(...) { f(a=1, ...) } ; g(b=2) }");
        assertEval("{ f <- function(a, b) { a * b } ; g <- function(...) { f(...,...) } ; g(3) }");
        assertEval("{ g <- function(...) { c(...,...) } ; g(3) }");

        assertEval("{ f <- function(...) cat(..., \"\\n\") ; f(\"Hello\", \"world\") }");
        assertEval("{ f <- function(a=1,...) a ; f() }");

        assertEval("{ f<-function(x, y) { print(missing(y)); } ; f(42) }");
        assertEval("{ g<-function(nm, x) { print(c(nm, x)); } ; f<-function(x, ...) { g(x, ...) }; f(x=1, nm=42) }");
        // Checkstyle: stop line length check
        assertEval("{ f.numeric<-function(x, row.names = NULL, optional = FALSE, ..., nm = NULL) { print(optional); print(nm) }; f<-function(x, row.names = NULL, optional = FALSE, ...) { UseMethod(\"f\") }; f(c(1,2), row.names = \"r1\", nm=\"bar\") }");
        // Checkstyle: resume line length check

        assertEval(Output.IgnoreErrorContext, "{ f <- function(x) { ..1 } ;  f(10) }");
        assertEval("{ f <- function(...) { ..1 } ;  f() }");

        assertEval("{ fn1 <- function (a, b) a + b; fn2 <- function (a, b, ...) fn1(a, b, ...); fn2(1, 1) }");
        assertEval("{ asdf <- function(x,...) UseMethod(\"asdf\",x); asdf.numeric <- function(x, ...) print(paste(\"num:\", x, ...)); asdf(1) }");

        assertEval("{ f <- function(...) { ..1 } ;  f(10) }");
        assertEval("{ f <- function(...) { ..1 ; x <<- 10 ; ..1 } ; x <- 1 ; f(x) }");
        assertEval("{ f <- function(...) { g <- function() { ..1 } ; g() } ; f(a=2) }");
        assertEval("{ f <- function(...) { ..1 <- 2 ; ..1 } ; f(z = 1) }");
        assertEval("{ f <- function(...) { ..1 <- 2 ; get(\"..1\") } ; f(1,2,3,4) }");
        assertEval("{ f <- function(...) { get(\"..1\") } ; f(1,2,3,4) }");
        assertEval("{ f <- function(...) { eval(as.symbol(\"..1\")) } ; f(1,2,3,4) }");
        assertEval("{ f <- function(...) { ..1 <- 2; eval(as.symbol(\"..1\")) } ; f(1,2,3,4) }");

        assertEval("{ g <- function(a,b) { a + b } ; f <- function(...) { g(...) }  ; f(1,2) }");
        assertEval("{ g <- function(a,b,x) { a + b * x } ; f <- function(...) { g(...,x=4) }  ; f(b=1,a=2) }");
        assertEval("{ g <- function(a,b,x) { a + b * x } ; f <- function(...) { g(x=4, ...) }  ; f(b=1,a=2) }");
        assertEval("{ g <- function(a,b,x) { a + b * x } ; f <- function(...) { g(x=4, ..., 10) }  ; f(b=1) }");

        assertEval("{ g <- function(a,b,aa,bb) { a ; x <<- 10 ; aa ; c(a, aa) } ; f <- function(...) {  g(..., ...) } ; x <- 1; y <- 2; f(x, y) }");
        assertEval("{ f <- function(a, b) { a - b } ; g <- function(...) { f(1, ...) } ; g(a = 2) }");

        assertEval(Output.IgnoreErrorContext, "{ f <- function(...) { ..3 } ; f(1,2) }");

        assertEval(Output.IgnoreErrorContext, "{ f <- function() { dummy() } ; f() }");
        assertEval(Output.IgnoreErrorContext, "{ f <- function() { if (FALSE) { dummy <- 2 } ; dummy() } ; f() }");
        assertEval(Output.IgnoreErrorContext, "{ f <- function() { if (FALSE) { dummy <- 2 } ; g <- function() { dummy() } ; g() } ; f() }");
        assertEval(Output.IgnoreErrorContext, "{ f <- function() { dummy <- 2 ; g <- function() { dummy() } ; g() } ; f() }");
        assertEval(Output.IgnoreErrorContext, "{ f <- function() { dummy() } ; dummy <- 2 ; f() }");
        assertEval(Output.IgnoreErrorContext, "{ dummy <- 2 ; dummy() }");
        assertEval("{ f <- function(a, b) { a + b } ; g <- function(...) { f(a=1, ...) } ; g(a=2) }");
        assertEval("{ f <- function(a, barg, bextra) { a + barg } ; g <- function(...) { f(a=1, ...) } ; g(b=2,3) }");
        assertEval("{ f <- function(a, barg, bextra, dummy) { a + barg } ; g <- function(...) { f(a=1, ...) } ; g(be=2,bex=3, 3) }");

        assertEval("{ f <- function(a, barg, bextra, dummy) { a + barg } ; g <- function(...) { f(a=1, ...) } ; g(1,2,3) }");
        assertEval("{ f <- function(...,d) { ..1 + ..2 } ; f(1,d=4,2) }");
        assertEval("{ f <- function(...,d) { ..1 + ..2 } ; f(1,2,d=4) }");

        assertEval("{ f<-function(...) print(attributes(list(...))); f(a=7) }");
        assertEval("{ f<-function(...) print(attributes(list(...))); f(a=7, b=42) }");
        assertEval("{ f <- function(...) { x <<- 10 ; ..1 } ; x <- 1 ; f(x) }");
        assertEval("{ f <- function(...) { ..1 ; x <<- 10 ; ..2 } ; x <- 1 ; f(100,x) }");
        assertEval("{ f <- function(...) { ..2 ; x <<- 10 ; ..1 } ; x <- 1 ; f(x,100) }");
        assertEval("{ g <- function(...) { 0 } ; f <- function(...) { g(...) ; x <<- 10 ; ..1 } ; x <- 1 ; f(x) }");

        assertEval("{ x<-7; y<-42; f<-function(...) { substitute(g(...)) }; is.language(f(x,y)) }");
        assertEval("{ x<-7; y<-42; f<-function(...) { substitute(g(...)) }; typeof(f(x,y)) }");
        assertEval("{ x<-7; y<-42; f<-function(...) { as.list(substitute(g(...))) }; f(x,y) }");

        assertEval("{ f <- function(...) g(...); g <- function(a,b) { print(a); print(b) }; f(1,2); f(a=3,b=4); f(a=5,b=6) }");

        assertEval("{ f <- function(a, barg, ...) { a + barg } ; g <- function(...) { f(a=1, ...) } ; g(b=2,3) }");
        assertEval("{ f <- function(a, barg, bextra, dummy) { a + barg } ; g <- function(...) { f(a=1, ...) } ; g(be=2,du=3, 3) }");

        assertEval("{ f<-function(x, ...) { sum(x, ...) }; f(7) }");
        assertEval("{ h<-function(x,...) f(x,...); f<-function(x, ...) { sum(x, ...) }; h(7) }");
        assertEval("{ g <- function(x, ...) c(x, ...); g(1) }");
        assertEval("{ g <- function(x, ...) f(x,...); f <-function(x,...) c(x, ...); g(1) }");

        assertEval("bug <- function(a, ...) vapply(a, function(.) identical(a, T, ...), NA); v <- c(1,2,3); bug(v)");
        assertEval("bug <- function(a, ...) { f <- function(x) identical(a, T, ...); f(x)}; v1 <- c(1,2,3); bug(v1)");
        assertEval("bug <- function(a, ...) { f <- function(x) identical(a, T, ...); environment(f) <- globalenv(); f(x)}; v1 <- c(1,2,3); bug(v1)");
        assertEval("f2 <- function(...) { f <- function() cat(...); f() }; f2()");
        assertEval("f2 <- function(...) { f <- function() cat(...); environment(f) <- globalenv(); f() }; f2()");
        assertEval("f2 <- function(...) { f <- function() cat(...); f() }; f2(\"a\")");
        assertEval(Ignored.ImplementationError, "f2 <- function(...) { f <- function() cat(...); assign(\"...\", NULL); f() }; f2(\"a\")");
        assertEval("f2 <- function(...) { f <- function() cat(...); assign(\"...\", \"asdf\"); f() }; f2(\"a\")");

        assertEval("{ g <- function(a,b,x) { a + b * x } ; f <- function(...) { g(x=4, ..., 10) }  ; f(b=1,a=2) }");
        assertEval("{ f <- function(a, barg, bextra, dummy) { a + barg } ; g <- function(...) { f(a=1, ..., x=2) } ; g(1) }");
        assertEval("{ f <- function(a, barg, bextra, dummy) { a + barg } ; g <- function(...) { f(a=1, ..., xxx=2) } ; g(1) }");
        assertEval("{ f <- function(a, barg, bextra, dummy) { a + barg } ; g <- function(...) { f(a=1, xxx=2, ...) } ; g(1) }");

        assertEval("{ `-.foo` <- function(...) 123; v <- 1; class(v) <- 'foo'; sapply(1,`-`,v); sapply(v,`-`,1); sapply(v,`-`,v) }");

        assertEval("{ f <- function(...) { substitute(..1) } ;  f(x+y) }");
        // Error messages differ
        // Expected output: Error in get(as.character(FUN), mode = "function", envir = envir) :
        // object 'dummy' of mode 'function' was not found
        // FastR output: Error in match.fun(FUN) : could not find function "dummy"
        assertEval(Output.IgnoreErrorMessage, "{ lapply(1:3, \"dummy\") }");

        // Error messages differ
        // Expected output: Error in f(a = 1, ..., x = 2, z = 3) : unused arguments (x = 2, z = 3)
        // FastR output: Error in f(a = 1, ..., x = 2, z = 3) : unused argument (x = 2)
        assertEval(Output.IgnoreErrorMessage, "{ f <- function(a, barg, bextra, dummy) { a + barg } ; g <- function(...) { f(a=1, ..., x=2,z=3) } ; g(1) }");
        assertEval("{ f <- function(a, barg, bextra, dummy) { a + barg } ; g <- function(...) { f(a=1, ...,,,) } ; g(1) }");
        // Error messages differ
        // Expected output: Error in ..2 + ..2 : non-numeric argument to binary operator
        // FastR output: Error in ..2 + ..2 : invalid argument to unary operator
        assertEval(Output.IgnoreErrorMessage, "{ f <- function(...) { ..2 + ..2 } ; f(1,,2) }");
        // FIXME GnuR's probably better approach since missing(..n) could be used
        // for arg presence so using ImplementationError for now.
        // Expected output: Error in ..1 + ..2 : non-numeric argument to binary operator
        // FastR output: [1] 1
        assertEval(Ignored.ImplementationError, "{ f <- function(...) { ..1 + ..2 } ; f(1,,3) }");

        assertEval("{ f1 <- function(...) { subst <- substitute(list(...))[-1L]; eval(subst[[1]]) }; f2 <- function(a, ...) TRUE; f3 <- function(a, ...) { cat(\"Here:\"); f1(f2(a, ...)) }; f1(f3(\"aaa\")) }");
    }

    @Test
    public void testInvokeIndirectly() {
        assertEval("{ f <- function(x) x+1 ; g <- function(x) x+2 ; h <- function(v) if (v==1) f else g ; h(1)(1) }");
        assertEval("{ f <- function(x) x+1 ; g <- function(x) x+2 ; h <- function(v) if (v==1) f else g ; h(2)(1) }");
        assertEval("{ f <- function(x) x+1 ; g <- function(x) x+2 ; v <- 1 ; (if (v==1) f else g)(1) }");
        assertEval("{ f <- function(x) x+1 ; g <- function(x) x+2 ; v <- 2 ; (if (v==1) f else g)(1) }");
        assertEval("{ f <- function(x) x+1 ; g <- function(x) x+2 ; funs <- list(f,g) ; funs[[1]](1) }");
        assertEval("{ f <- function(x) x+1 ; g <- function(x) x+2 ; funs <- list(f,g) ; funs[[2]](1) }");
    }

    @Test
    public void testArgs() {
        assertEval("{ f<-function(x, row.names = NULL, optional = FALSE, ...) {print(optional)}; f(c(7,42), row.names=NULL, nm=\"x\") }");
        assertEval("{ f<-function(x, row.names = NULL, optional = FALSE, ...) {print(optional); for (i in list(...)) {print(i)} }; f(c(7,42), row.names=NULL, nm=\"x\") }");
    }

    @Test
    public void testUnusedArgumentErrors() {
        assertEval("{ foo <- function(x) x; foo(1, 2, 3) }");
        assertEval("{ foo <- function(x) x; foo() }");
    }

    @Test
    public void testFunctionPrinting() {
        assertEval("{ foo <- function(x) x; foo }");
        assertEval("{ sum }");

        // mismatch on <bytecode> and formatting
        assertEval(Ignored.Unstable, "{ exists }");
    }

    @Test
    public void testFunctionResultPrinting() {
        assertEval("{ foo <- function() { x <- 1; return(x) }; foo() }");
    }

    @Test
    public void testIsPrimitive() {
        assertEval("{ is.primitive(is.primitive) }");
        assertEval("{ is.primitive(is.function) }");
    }

    @Test
    public void testDefaultArgs() {
        assertEval("{ array(dim=c(-2,2)); }");
        assertEval("{ array(dim=c(-2,-2)); }");
        assertEval("{ length(array(dim=c(1,0,2,3))) }");
        assertEval("{ dim(array(dim=c(2.1,2.9,3.1,4.7))) }");
    }

    @Test
    public void testMatchFun() {
        assertEval("{ f <- match.fun(length) ; f(c(1,2,3)) }");
        assertEval("{ f <- match.fun(\"length\") ; f(c(1,2,3)) }");
        assertEval("{ f <- function(x) { y <- match.fun(x) ; y(c(1,2,3)) } ; c(f(\"sum\"),f(\"cumsum\")) }");
        assertEval("{ f <- function(x) { y <- match.fun(x) ; y(3,4) } ; c(f(\"+\"),f(\"*\")) }");
    }

    @Test
    public void testStateTransitions() {
        assertEval("{ f<-function(x) { l<-length(x); x[1]<-1 }; y<-c(42,7); f(y); y }");
    }

    @Test
    public void testConversions() {
        assertEval("{ x<-quote(list(...)); l<-list(); l[[2]]<-x; names(l)<-c(\"...\"); f<-as.function(l); f(7, 42) }");
    }

    @Test
    public void testSrcref() {
        assertEval("1\nf <- quote(function(x, y) \n a + \n foooo); as.list(f); as.list(f)[[4]]; unclass(as.list(f)[[4]]); class(as.list(f)[[4]])");
    }
}
