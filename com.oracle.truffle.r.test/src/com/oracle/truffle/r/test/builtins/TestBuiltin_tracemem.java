/*
 * Copyright (c) 2016, 2018, Oracle and/or its affiliates. All rights reserved.
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

import org.junit.Test;

import com.oracle.truffle.r.test.TestBase;

/**
 * Tests tracemem and related builtins.
 */
public class TestBuiltin_tracemem extends TestBase {
    @Test
    public void argumentErrors() {
        assertEval("tracemem(NULL)");
        assertEval("retracemem(NULL)");
        assertEval("retracemem(c(1,10,100), 1:10)");
    }

    @Test
    public void vectors() {
        assertEval(Output.ContainsReferences, "v <- c(1,10,100); tracemem(v); x <- v; y <- v; x[[1]]<-42; y[[2]] <- 84");
        assertEval(Output.ContainsReferences, "v <- c(1,10,100); tracemem(v); x <- v; y <- v; x[[1]]<-42; untracemem(v); y[[2]] <- 84");
    }

    @Test
    public void list() {
        assertEval(Output.ContainsReferences, "v <- list(1,10,100); tracemem(v); x <- v; x[[1]]<-42;");
    }

    @Test
    public void retracemem() {
        // intended semantics of retracemem is not clear, this tests what is definitely intended:
        // retracemem starts tracing of its first argument
        assertEval(Output.ContainsReferences, "v <- c(1,10,100); tracemem(v); x <- v[-1]; retracemem(x, retracemem(v)); u <- x; u[[1]] <- 42;");
        assertEval(Output.ContainsReferences, "x<-1:10; retracemem(x, c(\"first\", \"second\")) ");
    }
}
