/*
 * Copyright (c) 2019, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.r.test.tck;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;

import com.oracle.truffle.r.runtime.data.RIntVector;
import com.oracle.truffle.r.runtime.data.RStringVector;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.oracle.truffle.r.test.TestBase;
import com.oracle.truffle.r.test.generate.FastRSession;
import com.oracle.truffle.r.test.tck.ParseWithArgsTesterInstrument.ParseWithArgsTestData;
import com.oracle.truffle.r.test.tck.ToStringTesterInstrument.ToStringTestData;

public class TruffleRLanguageTest extends TestBase {

    private org.graalvm.polyglot.Context context;

    @Before
    public void before() {
        context = FastRSession.getContextBuilder("R").build();
    }

    @After
    public void dispose() {
        context.close();
    }

    @Test
    public void testToString() {
        // Test set-up:
        context.eval("R", "print.myclass <- function(...) cat('custom printer')");
        context.eval("R", "lazyInvoked <- F; delayedAssign('lazy', { lazyInvoked <<- T; 'lazyVal' })");
        assertFalse(context.eval("R", "lazyInvoked").asBoolean());

        ToStringTestData testData = context.getEngine().getInstruments().get(ToStringTesterInstrument.ID).lookup(ToStringTestData.class);
        context.eval("R", "1+1"); // to trigger the instrument
        assertEquals("[1] 42", testData.intAsString);
        assertEquals("[1] 42", testData.byteAsString);
        assertEquals("[1] 42.5", testData.doubleAsString);
        assertEquals("[1] \"Hello\"", testData.stringAsString);
        assertEquals("[1] TRUE", testData.trueAsString);
        assertEquals("[1] FALSE", testData.falseAsString);
        assertEquals("custom printer", testData.objAsString);

        // toDisplayString without side effects does not evaluate the promise
        assertEquals("<unevaluated expression: {\n" +
                        "    lazyInvoked <<- T\n" +
                        "    \"lazyVal\"\n" +
                        "}>", testData.lazyWithoutSideEffects);

        // toDisplayString with side effects evaluates the promise and gives toDisplayString of the
        // value
        assertEquals("[1] \"lazyVal\"", testData.lazyWithSideEffects);
    }

    @Test
    public void testParseWithArgs() {
        ParseWithArgsTestData testData = context.getEngine().getInstruments().get(ParseWithArgsTesterInstrument.ID).lookup(ParseWithArgsTestData.class);
        context.eval("R", "1+1");   // to trigger the instrument

        assertThat(testData.additionResult, instanceOf(RIntVector.class));
        assertEquals(4, ((RIntVector) testData.additionResult).getDataAt(0));

        assertThat(testData.sumResult, instanceOf(RIntVector.class));
        assertEquals(6, ((RIntVector) testData.sumResult).getDataAt(0));

        assertThat(testData.helloWorld, instanceOf(RStringVector.class));
        assertEquals("Hello world", ((RStringVector) testData.helloWorld).getDataAt(0));
        // assertEquals(RRuntime.LOGICAL_TRUE, ((RLogicalVector)
        // testData.hasSysCallResult).getDataAt(0));
    }
}
