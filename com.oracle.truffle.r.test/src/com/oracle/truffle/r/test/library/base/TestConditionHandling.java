/*
 * Copyright (c) 2015, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.r.test.library.base;

import org.junit.Test;

import com.oracle.truffle.r.test.TestRBase;

public class TestConditionHandling extends TestRBase {

    @Override
    public String getTestDir() {
        return "condition";
    }

    @Test
    public void testTryCatch() {
        assertEval("{ tryCatch(1, finally = print(\"Hello\")) }");
        assertEval("{ e <- simpleError(\"test error\"); tryCatch(stop(e), finally = print(\"Hello\")) }");
        assertEval("{ e <- simpleError(\"test error\"); f <- function() { tryCatch(1, finally = print(\"Hello\")); stop(e)}; f() }");
        assertEval(Output.IgnoreErrorContext, "{ tryCatch(stop(\"fred\"), finally = print(\"Hello\")) }");
        assertEval("{ e <- simpleError(\"test error\"); tryCatch(stop(e), error = function(e) e, finally = print(\"Hello\"))}");
        // FIXME missing "in doTryCatch(return(expr), name, parentenv, handler)"
        // in FastR error description
        // Expected output: [1] "Hello"
        // <simpleError in doTryCatch(return(expr), name, parentenv, handler): fred>
        // FastR output: [1] "Hello"
        // <simpleError: fred>
        assertEval(Ignored.ImplementationError, "{ tryCatch(stop('fred'), error = function(e) e, finally = print('Hello'))}");
        assertEval("x <- { tryCatch(stop('fred'), error = function(e) e, finally = print('Hello'))}; x$call <- NULL; x");
        assertEval("{ f <- function() { tryCatch(1, error = function(e) print(\"Hello\")); stop(\"fred\")}; f() }");
        assertEval("{ f <- function() { tryCatch(stop(\"fred\"), error = function(e) print(\"Hello\"))}; f() }");
        assertEval("{ tryCatch(stop(\"xyz\"), error=function(e) { cat(\"<error>\");123L }, finally=function() { cat(\"<finally>\")}) }");
        assertEval("my.error <- function(war) cat('my.error:', war$message, '\\n'); f <- function() print(g); tryCatch({f()}, error=my.error)");
    }

    @Test
    public void testWithRestarts() {
        assertEval("withRestarts({cat(\"<start>\");invokeRestart(\"foo\", 123L, 456L);789L},\n foo=function(a,b) c(a,b))");
        assertEval("withRestarts({cat(\"<start>\");invokeRestart(\"foo\", 123L, 456L);789L},\n foo=list(description=\"my handler\", handler=function(a,b) c(a,b)))");
        assertEval("withRestarts({cat(\"<start>\");invokeRestart(\"foo\", 123L, 456L);789L},\n foo=function(a,b) {cat(\"<first>\");invokeRestart(\"foo\", a+1, b+1)},\n foo=function(a,b) {cat(\"<second>\");c(a,b)})");
        assertEval("withRestarts(findRestart(\"noSuchRestart\"),\n foo=function(a,b) c(a,b))");
        assertEval("withRestarts(findRestart(\"foo\"),\n foo=function(a,b) c(a,b))");
        assertEval("withRestarts(computeRestarts(),\n foo=function(a,b) c(a,b))");
        // FIXME: visibility is not properly transformed
        assertEval(Ignored.ImplementationError, "{ boo <- function() invisible(\"hello world\"); foo <- function(expr) expr; foo(boo()); }");
        // TODO: once visibility is fixed, the invisible can be removed
        assertEval("invisible(withCallingHandlers(warning('warn'), warning=function(...) { print(sys.call()[[1]]); invokeRestart('muffleWarning'); }))");
        // FIXME: the "arg" is not properly constructed by FastR, it has arg$message (correct) and
        // arg$call -- which in FastR is NULL unlike in GNUR
        assertEval(Ignored.ImplementationError, "invisible(withCallingHandlers(warning('warn'), warning=function(arg) { print(unclass(arg)); invokeRestart('muffleWarning'); }))");
    }

    @Test
    public void testWithCallingHandlers() {
        assertEval(Output.IgnoreWarningContext, "withCallingHandlers({warning(\"foo\");123L},\n warning=function(e) {cat(\"<warn>\")})");
        assertEval("withCallingHandlers({warning(\"foo\");123L},\n warning=function(e) {\n cat(\"<warn>\")\n invokeRestart(\"muffleWarning\")\n })");
        assertEval("withCallingHandlers({message(\"foo\");123L},\n message=function(e) {cat(\"<msg>\")})");
        assertEval("withCallingHandlers({message(\"foo\");123L},\n message=function(e) {\n cat(\"<msg>\")\n invokeRestart(\"muffleMessage\")\n })");
        assertEval("withCallingHandlers({message(\"foo\");packageStartupMessage(\"bar\");123L},\n packageStartupMessage=function(e) {\n cat(\"<msg>\")\n invokeRestart(\"muffleMessage\")\n })");
        assertEval(Output.IgnoreErrorContext, "withCallingHandlers(stop('error message'), error=function(e) {})");
        assertEval(Output.IgnoreErrorMessage, "withCallingHandlers(unknownSymbol(), condition = function(e) {})");
    }

    @Test
    public void testWarning() {
        assertEval("tryCatch(warning('some warning text'), warning = function(w) {print('WARNING')})");
        assertEval("my.warning <- function(war) cat('my.warning:', war$message, '\\n'); f <- function()  warning('from f'); tryCatch({f()}, warning=my.warning)");
    }
}
