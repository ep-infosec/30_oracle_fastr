/*
 * Copyright (c) 2017, 2020, Oracle and/or its affiliates. All rights reserved.
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Assert;
import org.junit.Test;

import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.r.runtime.data.RInteropScalar;
import static org.junit.Assert.fail;

public class RInteropScalarInteropTest extends AbstractInteropTest {

    @Override
    protected boolean shouldTestToNative(TruffleObject obj) {
        return false;
    }

    @Test
    public void testRInteroptScalar() throws Exception {
        for (TruffleObject obj : createTruffleObjects()) {
            RInteropScalar is = (RInteropScalar) obj;
            testRIS(obj, is.getJavaType());
        }
    }

    private static void testRIS(TruffleObject obj, Class<?> unboxedType) throws Exception {
        assertFalse(obj.getClass().getName() + " " + obj + " isn't expected to be null", getInterop().isNull(obj));
        assertFalse(obj.getClass().getName() + " " + obj + " isn't expected to have a size", getInterop().hasArrayElements(obj));

        assertTrue(obj.getClass().getName() + " " + obj + " is expected to be boxed", isBoxed(obj));
        Object ub = unbox(obj);
        if (unboxedType == Character.TYPE) {
            assertEquals(obj.getClass().getName() + " " + obj, String.class, ub.getClass());
        } else {
            assertEquals(obj.getClass().getName() + " " + obj, unboxedType, ub.getClass().getField("TYPE").get(null));
        }
    }

    @Override
    protected TruffleObject[] createTruffleObjects() throws Exception {
        return new TruffleObject[]{
                        RInteropScalar.RInteropByte.valueOf(Byte.MIN_VALUE),
                        RInteropScalar.RInteropByte.valueOf(Byte.MAX_VALUE),
                        RInteropScalar.RInteropChar.valueOf('a'),
                        RInteropScalar.RInteropFloat.valueOf(Float.MIN_VALUE),
                        RInteropScalar.RInteropFloat.valueOf(Float.MAX_VALUE),
                        RInteropScalar.RInteropLong.valueOf(Long.MIN_VALUE),
                        RInteropScalar.RInteropLong.valueOf(Long.MAX_VALUE),
                        RInteropScalar.RInteropShort.valueOf(Short.MIN_VALUE),
                        RInteropScalar.RInteropShort.valueOf(Short.MAX_VALUE)};
    }

    @Override
    protected Object getUnboxed(TruffleObject obj) {
        RInteropScalar is = (RInteropScalar) obj;
        try {
            Object value = is.getClass().getDeclaredMethod("getValue").invoke(is);
            if (value instanceof Character) {
                return value.toString();
            }
            return value;
        } catch (Exception ex) {
            Assert.fail("can't read interop scalar value " + ex);
        }
        return null;
    }

    @Override
    protected TruffleObject createEmptyTruffleObject() throws Exception {
        return null;
    }

    private static boolean isBoxed(Object obj) {
        if (getInterop().fitsInByte(obj) ||
                        getInterop().fitsInDouble(obj) ||
                        getInterop().fitsInFloat(obj) ||
                        getInterop().fitsInInt(obj) ||
                        getInterop().fitsInLong(obj) ||
                        getInterop().fitsInShort(obj) ||
                        getInterop().isBoolean(obj) ||
                        getInterop().isString(obj)) {
            return true;
        }
        return false;
    }

    private static Object unbox(Object obj) throws UnsupportedMessageException {
        if (getInterop().isString(obj)) {
            return getInterop().asString(obj);
        }
        if (getInterop().isBoolean(obj)) {
            return getInterop().asBoolean(obj);
        }
        if (getInterop().fitsInByte(obj)) {
            return getInterop().asByte(obj);
        }
        if (getInterop().fitsInShort(obj)) {
            return getInterop().asShort(obj);
        }
        if (getInterop().fitsInLong(obj)) {
            return getInterop().asLong(obj);
        }
        if (getInterop().fitsInFloat(obj)) {
            return getInterop().asFloat(obj);
        }
        fail();
        return null;
    }
}
