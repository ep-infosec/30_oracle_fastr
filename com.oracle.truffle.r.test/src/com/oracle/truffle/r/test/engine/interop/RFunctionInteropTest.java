/*
 * Copyright (c) 2017, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.r.test.engine.interop;

import static org.junit.Assert.assertTrue;

import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;
import org.junit.Test;

import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.r.runtime.data.RFunction;
import com.oracle.truffle.r.test.generate.FastRSession;

public class RFunctionInteropTest extends AbstractInteropTest {

    @Test
    public void testExecute() throws Exception {
        RFunction f = create("function() {}");
        assertTrue(getInterop().isExecutable(f));

        TruffleObject result = (TruffleObject) getInterop().execute(f);
        assertTrue(getInterop().isNull(result));

        f = create("function() {1L}");
        assertSingletonVector(1, getInterop().execute(f));

        f = create("function() {1}");
        assertSingletonVector(1.0, getInterop().execute(f));

        f = create("function() {TRUE}");
        assertSingletonVector(true, getInterop().execute(f));

        f = create("function(a) {a}");
        assertSingletonVector("abc", getInterop().execute(f, "abc"));

        f = create("function(a) { is.logical(a) }");
        assertSingletonVector(true, getInterop().execute(f, true));

        f = create("function(a) { .fastr.interop.asShort(a) }");
        assertTrue(getInterop().execute(f, 123) instanceof Short);

        f = create("function() { NA }");
        Object naVectorResult = getInterop().execute(f);
        Object naValue = getInterop().readArrayElement(naVectorResult, 0);
        assertTrue(getInterop().isNull(naValue));

        f = create("function() { NULL }");
        Object nullResult = getInterop().execute(f);
        assertTrue(getInterop().isNull(nullResult));
    }

    @Override
    protected Object[] getExecuteArguments(TruffleObject obj) {
        return new Object[]{true};
    }

    @Override
    protected Object getExecuteExpected(TruffleObject obj) {
        return true;
    }

    @Override
    protected TruffleObject[] createTruffleObjects() {
        return new TruffleObject[]{create("function(s) {s}")};
    }

    private static RFunction create(String fun) {
        Source src = Source.newBuilder("R", fun, "<testrfunction>").internal(true).buildLiteral();
        Value result = context.eval(src);
        return (RFunction) FastRSession.getReceiver(result);
    }

    @Override
    protected TruffleObject createEmptyTruffleObject() throws Exception {
        return null;
    }
}
