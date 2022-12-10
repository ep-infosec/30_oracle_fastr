/*
 * Copyright (c) 2013, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.r.runtime.data;

import com.oracle.truffle.api.dsl.ImplicitCast;
import com.oracle.truffle.api.dsl.TypeCast;
import com.oracle.truffle.api.dsl.TypeCheck;
import com.oracle.truffle.api.dsl.TypeSystem;
import com.oracle.truffle.r.runtime.data.RInteropScalar.RInteropByte;
import com.oracle.truffle.r.runtime.data.RInteropScalar.RInteropChar;
import com.oracle.truffle.r.runtime.data.RInteropScalar.RInteropFloat;
import com.oracle.truffle.r.runtime.data.RInteropScalar.RInteropLong;
import com.oracle.truffle.r.runtime.data.RInteropScalar.RInteropShort;
import com.oracle.truffle.r.runtime.data.model.RAbstractAtomicVector;
import com.oracle.truffle.r.runtime.data.model.RAbstractContainer;
import com.oracle.truffle.r.runtime.data.model.RAbstractListVector;
import com.oracle.truffle.r.runtime.data.model.RAbstractVector;
import com.oracle.truffle.r.runtime.env.REnvironment;
import com.oracle.truffle.r.runtime.nodes.RNode;

/**
 * Whenever you add a type {@code T} to the list below, make sure a corresponding {@code executeT()}
 * method is added to {@link RNode}, a {@code typeof} method is added to {@code TypeofNode} and a
 * {@code print} method added to {code PrettyPrinterNode}.
 *
 * @see RNode
 */
@TypeSystem({
                byte.class, RLogicalVector.class, RInteropByte.class,
                RInteropChar.class,
                RInteropFloat.class,
                RInteropLong.class,
                RInteropShort.class,
                int.class, RIntVector.class,
                double.class, RDoubleVector.class,
                String.class, RStringVector.class,
                RRaw.class, RRawVector.class,
                RComplex.class, RComplexVector.class,
                RAbstractListVector.class,
                RNull.class,
                RExpression.class,
                RPromise.class,
                RMissing.class,
                RPairList.class,
                RSymbol.class,
                RFunction.class,
                REnvironment.class,
                RAbstractContainer.class,
                RArgsValuesAndNames.class
})
public class RTypes {

    @TypeCheck(RNull.class)
    public static boolean isRNull(Object value) {
        return value == RNull.instance;
    }

    @TypeCast(RNull.class)
    @SuppressWarnings("unused")
    public static RNull asRNull(Object value) {
        return RNull.instance;
    }

    @TypeCheck(RMissing.class)
    public static boolean isRMissing(Object value) {
        return value == RMissing.instance;
    }

    @TypeCast(RMissing.class)
    @SuppressWarnings("unused")
    public static RMissing asRMissing(Object value) {
        return RMissing.instance;
    }

    @ImplicitCast
    public static RAbstractContainer toAbstractContainer(int value) {
        return RDataFactory.createIntVectorFromScalar(value);
    }

    @ImplicitCast
    public static RAbstractContainer toAbstractContainer(double value) {
        return RDataFactory.createDoubleVectorFromScalar(value);
    }

    @ImplicitCast
    public static RAbstractContainer toAbstractContainer(byte value) {
        return RDataFactory.createLogicalVectorFromScalar(value);
    }

    @ImplicitCast
    public static RAbstractContainer toAbstractContainer(String value) {
        return RDataFactory.createStringVectorFromScalar(value);
    }

    @ImplicitCast
    public static RAbstractContainer toAbstractContainer(RComplex vector) {
        return RDataFactory.createComplexVectorFromScalar(vector);
    }

    public static RAbstractContainer toAbstractContainer(RRaw vector) {
        return RDataFactory.createRawVectorFromScalar(vector);
    }

    @ImplicitCast
    public static RAbstractVector toAbstractVector(int value) {
        return RDataFactory.createIntVectorFromScalar(value);
    }

    @ImplicitCast
    public static RAbstractVector toAbstractVector(double value) {
        return RDataFactory.createDoubleVectorFromScalar(value);
    }

    @ImplicitCast
    public static RAbstractVector toAbstractVector(byte value) {
        return RDataFactory.createLogicalVectorFromScalar(value);
    }

    @ImplicitCast
    public static RAbstractVector toAbstractVector(String value) {
        return RDataFactory.createStringVectorFromScalar(value);
    }

    @ImplicitCast
    public static RAbstractVector toAbstractVector(RComplex vector) {
        return RDataFactory.createComplexVectorFromScalar(vector);
    }

    @ImplicitCast
    public static RAbstractVector toAbstractVector(RRaw vector) {
        return RDataFactory.createRawVectorFromScalar(vector);
    }

    @ImplicitCast
    public static RIntVector toIntVector(int value) {
        return RDataFactory.createIntVectorFromScalar(value);
    }

    @ImplicitCast
    public static RDoubleVector toDoubleVector(double value) {
        return RDataFactory.createDoubleVectorFromScalar(value);
    }

    @ImplicitCast
    public static RLogicalVector toLogicalVector(byte vector) {
        return RDataFactory.createLogicalVectorFromScalar(vector);
    }

    @ImplicitCast
    public static RStringVector toStringVector(String vector) {
        return RDataFactory.createStringVectorFromScalar(vector);
    }

    @ImplicitCast
    public static RRawVector toRawVector(RRaw vector) {
        return RDataFactory.createRawVectorFromScalar(vector);
    }

    @ImplicitCast
    public static RComplexVector toComplexVector(RComplex vector) {
        return RDataFactory.createComplexVectorFromScalar(vector);
    }

    @ImplicitCast
    public static RAbstractAtomicVector toAbstractAtomicVector(int value) {
        return RDataFactory.createIntVectorFromScalar(value);
    }

    @ImplicitCast
    public static RAbstractAtomicVector toAbstractAtomicVector(double value) {
        return RDataFactory.createDoubleVectorFromScalar(value);
    }

    @ImplicitCast
    public static RAbstractAtomicVector toAbstractAtomicVector(byte value) {
        return RDataFactory.createLogicalVectorFromScalar(value);
    }

    @ImplicitCast
    public static RAbstractAtomicVector toAbstractAtomicVector(String value) {
        return RDataFactory.createStringVectorFromScalar(value);
    }

    @ImplicitCast
    public static RAbstractAtomicVector toAbstractAtomicVector(RRaw value) {
        return RDataFactory.createRawVectorFromScalar(value);
    }

    @ImplicitCast
    public static RAbstractAtomicVector toAbstractAtomicVector(RComplex vector) {
        return RDataFactory.createComplexVectorFromScalar(vector);
    }

    @ImplicitCast
    public static RMissing toRMissing(@SuppressWarnings("unused") REmpty empty) {
        return RMissing.instance;
    }
}
