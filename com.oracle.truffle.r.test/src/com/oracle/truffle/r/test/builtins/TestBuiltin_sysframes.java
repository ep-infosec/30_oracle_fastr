/*
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.truffle.r.test.builtins.TestBuiltin_sysparent.SYS_PARENT_SETUP;

import org.junit.Test;

import com.oracle.truffle.r.test.TestBase;

public class TestBuiltin_sysframes extends TestBase {
    @Test
    public void frameAccessCommonTest() {
        // FIXME: problem when promise "sys.frames()" is evaluated inside "lapply", the frame used
        // for the evaluation of the promise has suspicious RCaller with depth == 9. Changing the
        // following code in PromiseHelper:
        // RCaller.createForPromise(RArguments.getCall(promiseFrame), frame);
        // to
        // RCaller.createForPromise(RArguments.getCall(promiseFrame), promiseFrame);
        // fixes the RCaller and this test, but breaks few other promise related tests.
        assertEval(Ignored.ImplementationError, "{ foo <- function(x) lapply(sys.frames(), function(x) ls(x));" + SYS_PARENT_SETUP + "}");
        assertEval("{ foo <- function(x) list(a = ls(sys.frames()[[1]]), b=ls(sys.frames()[[2]]), len=length(sys.frames()));" + SYS_PARENT_SETUP + "}");
    }
}
