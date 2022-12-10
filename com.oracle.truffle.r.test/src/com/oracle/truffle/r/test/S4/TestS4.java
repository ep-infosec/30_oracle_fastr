/*
 * Copyright (c) 2015, 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.r.test.S4;

import org.junit.Test;

import com.oracle.truffle.r.test.TestRBase;

// Checkstyle: stop LineLength

/**
 * Tests for the S4 object model implementation.
 */
public class TestS4 extends TestRBase {

    @Override
    protected String getTestDir() {
        return "S4";
    }

    @Test
    public void testSlotAccess() {
        assertEval("{ `@`(getClass(\"ClassUnionRepresentation\"), virtual) }");
        assertEval("{ `@`(getClass(\"ClassUnionRepresentation\"), \"virtual\") }");
        assertEval("{ `@`(getClass(\"ClassUnionRepresentation\"), c(\"virtual\", \"foo\")) }");
        assertEval("{ getClass(\"ClassUnionRepresentation\")@virtual }");
        assertEval("{ getClass(\"ClassUnionRepresentation\")@.S3Class }");
        assertEval("{ c(42)@.Data }");
        assertEval("{ x<-42; `@`(x, \".Data\") }");
        assertEval("{ x<-42; `@`(x, .Data) }");
        assertEval("{ x<-42; slot(x, \".Data\") }");

        // disabled because of side effects causing other tests to fail
        assertEval(Ignored.SideEffects, "{ setClass(\"foo\", contains=\"numeric\"); x<-new(\"foo\"); res<-x@.Data; removeClass(\"foo\"); res }");
        assertEval(Ignored.SideEffects, "{ setClass(\"foo\", contains=\"numeric\"); x<-new(\"foo\"); res<-slot(x, \".Data\"); removeClass(\"foo\"); res }");

        assertEval(Output.IgnoreErrorContext, "{ getClass(\"ClassUnionRepresentation\")@foo }");
        assertEval(Output.IgnoreErrorContext, "{ c(42)@foo }");
        assertEval(Output.IgnoreErrorContext, " { x<-42; attr(x, \"foo\")<-7; x@foo }");
        assertEval("{ x<-42; attr(x, \"foo\")<-7; slot(x, \"foo\") }");
        assertEval(Output.IgnoreErrorContext, "{ x<-c(42); class(x)<-\"bar\"; x@foo }");
        assertEval("{ x<-getClass(\"ClassUnionRepresentation\"); slot(x, \"virtual\") }");
        assertEval(Output.IgnoreErrorContext, "{ x<-getClass(\"ClassUnionRepresentation\"); slot(x, virtual) }");
        assertEval("{ x<-function() 42; attr(x, \"foo\")<-7; y<-asS4(x); y@foo }");
        assertEval(Output.IgnoreErrorContext, "{ x<-NULL; `@`(x, foo) }");
        assertEval(Output.IgnoreErrorContext, "{ x<-NULL; x@foo }");
        assertEval("{ x<-paste0(\".\", \"Data\"); y<-42; slot(y, x) }");
        assertEval("{ setClass('A0', representation(name = 'character', age = 'numeric')); getSlots('A0') }");
        assertEval("{ setClass('A1', representation(name = 'character', age = 'numeric'), prototype(name = NA_character_, age = NA_real_)); obj <- new('A1', name = 'FastR'); obj@age }");
        assertEval("{ setClass('A2', representation(name = 'character', age = 'numeric')); obj <- new('A2', name = 'FastR'); obj@age }");
        assertEval("{ setClass('A3', representation(name = 'character', age = 'numeric')); obj <- new('A3', name = 'FastR'); slot(obj, 'age') <- 5; obj@age }");
    }

    @Test
    public void testSlotUpdate() {
        assertEval("{ x<-getClass(\"ClassUnionRepresentation\"); x@virtual<-TRUE; x@virtual }");
        assertEval("{ x<-getClass(\"ClassUnionRepresentation\"); slot(x, \"virtual\", check=TRUE)<-TRUE; x@virtual }");
        assertEval("{ x<-initialize@valueClass; initialize@valueClass<-\"foo\"; initialize@valueClass<-x }");

        assertEval(Output.IgnoreErrorContext, "{ x<-function() 42; attr(x, \"foo\")<-7; x@foo<-42 }");
        assertEval(Output.IgnoreErrorContext, "{ x<-function() 42; attr(x, \"foo\")<-7; slot(y, \"foo\")<-42 }");
        assertEval(Output.IgnoreErrorContext, "{ x<-function() 42; attr(x, \"foo\")<-7; y<-asS4(x); y@foo<-42 }");
        assertEval(Output.IgnoreErrorContext, "{ x<-NULL; `@<-`(x, foo, \"bar\") }");
        assertEval(Output.IgnoreErrorContext, "{ x<-NULL; x@foo<-\"bar\" }");

    }

    @Test
    public void testConversions() {
        assertEval("{ x<-42; isS4(x) }");
        assertEval("{ x<-42; y<-asS4(x); isS4(y) }");
        assertEval("{ isS4(NULL) }");
        assertEval("{ asS4(NULL); isS4(NULL) }");
        assertEval("{  asS4(7:42) }");
    }

    @Test
    public void testAllocation() {
        assertEval("{ new(\"numeric\") }");
        assertEval("{ setClass(\"foo\", representation(j=\"numeric\")); new(\"foo\", j=42) }");
        assertEval("{ setClass(\"foo\", representation(j=\"numeric\")); new(\"foo\", j='text') }");
        assertEval(Output.IgnoreErrorContext, "{ setClass(\"foo\", representation(j=\"numeric\")); new(\"foo\", inexisting=42) }");
    }

    @Test
    public void testClassCreation() {
        assertEval("{ setClass(\"foo\", representation(j=\"numeric\")); getClass(\"foo\") }");
        assertEval("{ setClass(\"foo\"); setClass(\"bar\", representation(j = \"numeric\"), contains = \"foo\"); is.null(getClass(\"foo\")@prototype) }");
        assertEval("{ setClass('foo', contains='standardGeneric'); getClass('foo') }");
    }

    @Test
    public void testPrototype() {
        assertEval("{ C11 <- setClass('C11', slots=c(data='numeric'), prototype=list(data=1)); C11() }");
        assertEval("{ C11 <- setClass('C11', slots=c(data='numeric'), prototype=list(data=1)); C11(data=42) }");
        assertEval("{ C11 <- setClass('C11', slots=c(data='numeric'), prototype=list(data=1)); setMethod('initialize', 'C11', function(.Object, ...) {print(.Object@data); callNextMethod(.Object, ...)}); C11() }");
        assertEval("{ C11 <- setClass('C11', slots=c(data='numeric'), prototype=list(data=1)); setMethod('initialize', 'C11', function(.Object, ...) {print(.Object@data); callNextMethod(.Object, ...)}); C11(data=42) }");
    }

    @Test
    public void testMethods() {
        // output slightly different from GNU R even though we use R's "show" method to print it
        // GNU R shows environment info:
        // function(object) standardGeneric("gen")
        // <environment: 0x...>
        assertEval(Ignored.OutputFormatting, "{ setGeneric(\"gen\", function(object) standardGeneric(\"gen\")); res<-print(gen); removeGeneric(\"gen\"); res }");
        assertEval(Ignored.OutputFormatting, "{ gen<-function(object) 0; setGeneric(\"gen\"); res<-print(gen); removeGeneric(\"gen\"); res }");

        assertEval("{ gen<-function(object) 0; setGeneric(\"gen\"); setClass(\"foo\", representation(d=\"numeric\")); setMethod(\"gen\", signature(object=\"foo\"), function(object) object@d); res<-print(gen(new(\"foo\", d=42))); removeGeneric(\"gen\"); res }");

        assertEval("{ setClass(\"foo\", representation(d=\"numeric\")); setClass(\"bar\",  contains=\"foo\"); setGeneric(\"gen\", function(o) standardGeneric(\"gen\")); setMethod(\"gen\", signature(o=\"foo\"), function(o) \"FOO\"); setMethod(\"gen\", signature(o=\"bar\"), function(o) \"BAR\"); res<-print(c(gen(new(\"foo\", d=7)), gen(new(\"bar\", d=42)))); removeGeneric(\"gen\"); res }");

        assertEval("{ setGeneric(\"gen\", function(o) standardGeneric(\"gen\")); res<-print(setGeneric(\"gen\", function(o) standardGeneric(\"gen\"))); removeGeneric(\"gen\"); res }");

        assertEval("{ setClass(\"foo\"); setMethod(\"diag<-\", \"foo\", function(x, value) 42); removeMethod(\"diag<-\", \"foo\"); removeGeneric(\"diag<-\"); removeClass(\"foo\") }");

        assertEval("{ setClass('A'); setClass('A1', contains = 'A'); setClass('A2', contains = 'A1'); setGeneric('foo', function(a, b) standardGeneric('foo')); setMethod('foo', signature('A1', 'A2'), function(a, b) '1-2'); setMethod('foo', signature('A2', 'A1'), function(a, b) '2-1'); foo(new('A2'), new('A2')) }");

        assertEval("setGeneric('do.call', signature = c('what', 'args'))");

        assertEval("{ setClass('A1', representation(a='numeric')); setMethod('length', 'A1', function(x) x@a); obj <- new('A1'); obj@a <- 10; length(obj) }");

        assertEval("{ setClass('A2', representation(a = 'numeric')); setMethod('rep', 'A2', function(x, a, b, c) { c(x@a, a, b, c) }); setMethod('ifelse', c(yes = 'A2'), function(test, yes, no) print(test)) }");
    }

    @Test
    public void testInternalDispatch() {
        assertEval("setClass('foo', representation(d='numeric')); setMethod(`$`, signature('foo'), function(x, name) 'FOO'); obj <- new('foo'); obj$asdf");

    }

    @Test
    public void testStdGeneric() {
        assertEval("{ standardGeneric(42) }");
        assertEval("{ standardGeneric(character()) }");
        assertEval("{ standardGeneric(\"\") }");
        assertEval(Output.IgnoreErrorContext, "{ standardGeneric(\"foo\", 42) }");
        assertEval(Output.IgnoreErrorContext, "{ x<-42; class(x)<-character(); standardGeneric(\"foo\", x) }");
        assertEval("{ setClass('A4', representation(a = 'numeric')); setMethod('[[', 'A4', function(x, i, j, ...) NULL); obj <- new('A4'); obj[[1]] }");

        assertEval("{ testStdGenericBar <- function(x = {cat('eval y\\n');y}) { cat('enter bar\\n'); y <- 41; cat('read y\\n'); x+1 }; setGeneric('testStdGenericBar'); testStdGenericBar() }");
    }

    @Test
    public void testObjectValidity() {
        assertEval("{ check <- function(object) length(object@n) == 1; setClass('SingleInt', representation(n = 'numeric'), validity = check); new('SingleInt', n = c(1, 2)) }");
        assertEval("{ check <- function(object) length(object@n) == 1; setClass('SingleInt', representation(n = 'numeric'), validity = check); new('SingleInt', n = 1) }");
    }

    @Test
    public void testObjectValueSemantics() {
        assertEval("{ setClass('WrappedIntVec', representation(n = 'numeric')); x0 <- new('WrappedIntVec', n = 1); x1 <- x0; x1@n <- 2; x0@n == x1@n }");
    }

    @Test
    public void testActiveBindings() {
        assertEval("someSymbol0 <- 1; makeActiveBinding('someSymbol0', function(x) { x }, .GlobalEnv)");
        assertEval(Output.IgnoreErrorMessage, Context.NonShared, "lockEnvironment(.GlobalEnv); makeActiveBinding('someSymbol1', function(x) { x }, .GlobalEnv)");
        assertEval("makeActiveBinding('someSymbol2', function(x) { x }, .GlobalEnv); bindingIsActive('someSymbol2', .GlobalEnv)");
        assertEval("bindingIsActive('someSymbol3', .GlobalEnv)");
        assertEval(".Internal(bindingIsActive(as.name('someSymbol4'), .GlobalEnv))");
        assertEval("someSymbol5 <- 0; lockBinding('someSymbol5', .GlobalEnv); makeActiveBinding('someSymbol5', function(x) { x }, .GlobalEnv)");
        assertEval("makeActiveBinding('someSymbol6', function(x) { x }, .GlobalEnv); lockBinding('someSymbol6', .GlobalEnv); makeActiveBinding('someSymbol6', function(x) { print('hello') }, .GlobalEnv)");
        // FIXME
        // Expected output: [1] "get0"
        // [1] "set0"
        // [1] "get1"
        // [1] "set1"
        // FastR output: [1] "get0"
        // [1] "get0"
        // [1] "set0"
        // [1] "get1"
        // [1] "get1"
        // [1] "set1"
        assertEval(Ignored.ImplementationError,
                        "makeActiveBinding('someSymbol7', function(x) { if(missing(x)) print('get0') else print('set0') }, .GlobalEnv); someSymbol7; someSymbol7 <- 1; makeActiveBinding('someSymbol7', function(x) { if(missing(x)) print('get1') else print('set1') }, .GlobalEnv); someSymbol7; someSymbol7 <- 1");
        assertEval("makeActiveBinding('someSymbol8', function(x) { print('hello') }, .GlobalEnv); someSymbol9 <- 'world'; print(someSymbol8); print(someSymbol9)");
        assertEval("makeActiveBinding('someSymbol10', function(x) { if(missing(x)) print('get0') else print('set0'); NULL }, .GlobalEnv); someSymbol10; someSymbol10 <- 1; makeActiveBinding('someSymbol10', function(x) { if(missing(x)) print('get1') else print('set1'); NULL }, .GlobalEnv); someSymbol10; someSymbol10 <- 1");
        assertEval("makeActiveBinding('var_a', function(x) { if(missing(x)) { print('get'); return(123) } else { print('set'); return(x) } }, .GlobalEnv); inherits(var_a, 'numeric')");
    }

    @Test
    public void testRegularFieldAssign() {
        assertEval(Output.IgnoreErrorContext, "{ setClass('TestS4CornerCases', representation(fld = 'character'));  obj <- new('TestS4CornerCases', fld = 'xyz'); obj$fld2 <- 'value'; }");
        assertEval(Output.IgnoreErrorContext,
                        "{ setClass('TestS4CornerCases', representation(fld = 'character'));  obj <- new('TestS4CornerCases', fld = 'xyz'); obj$fld2; }");
        assertEval("{ setClass('TestS4CornerCases', representation(fld = 'character'));  obj <- new('TestS4CornerCases', fld = 'xyz'); attr(obj, '.Data') <- new.env(); obj$fld2 <- 'value'; list(obj, as.list(attr(obj, '.Data')), obj$fld2); }");
        assertEval("{ setClass('TestS4CornerCases', representation(fld = 'character'));  obj <- new('TestS4CornerCases', fld = 'xyz'); attr(obj, '.xData') <- new.env(); obj$fld2 <- 'value'; list(obj, as.list(attr(obj, '.xData')), obj$fld2); }");
    }

    @Test
    public void testDispatchToS3ForBuiltins() {
        assertEval("{ setClass('TestS4S31', representation(f = 'numeric')); p <- new('TestS4S31', f = 2); `$.TestS4S31` <- function(...) 42; p$field }");
    }

    @Test
    public void testAs() {
        assertEval("{ my_as <- function(object, to) { class(object) <- to; object }; A12 <- setClass('A12', slots=c(data='numeric')); a <- A12(); x <- my_as(a, 'X'); class(a) }");
        assertEval("{ setClass('A13', slots=c(data='numeric')); B13 <- setClass('B13', contains='A13'); b <- B13(data=42); as(b, 'A13') }");
    }

    /**
     * The following snippet is a simplified excerpt from diffobj package. The idea behind this test
     * is that getting a class definition of an object in the validity function should not cause any
     * errors.
     */
    @Test
    public void testValidityFunction() {
        assertEval("{ setClass('A11', slots=c(data='numeric'), validity=function(object) {class(object); TRUE}); B11 <- setClass('B11', contains='A11'); B11(data=42) }");
    }
}
