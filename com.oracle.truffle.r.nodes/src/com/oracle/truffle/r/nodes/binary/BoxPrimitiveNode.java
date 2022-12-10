/*
 * Copyright (c) 2015, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.r.nodes.binary;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.r.runtime.data.RStringVector;
import com.oracle.truffle.r.runtime.nodes.unary.CastNode;
import com.oracle.truffle.r.runtime.data.CharSXPWrapper;
import com.oracle.truffle.r.runtime.data.RComplex;
import com.oracle.truffle.r.runtime.data.RComplexVector;
import com.oracle.truffle.r.runtime.data.RDataFactory;
import com.oracle.truffle.r.runtime.data.RDoubleVector;
import com.oracle.truffle.r.runtime.data.RIntVector;
import com.oracle.truffle.r.runtime.data.RRaw;
import com.oracle.truffle.r.runtime.data.RRawVector;
import com.oracle.truffle.r.runtime.data.RLogicalVector;
import com.oracle.truffle.r.runtime.data.model.RAbstractVector;

/**
 * Boxes all Java primitive values to a class that supports {@link RAbstractVector} and their typed
 * analogies.
 */
public abstract class BoxPrimitiveNode extends CastNode {

    @Override
    public abstract Object execute(Object operand);

    protected BoxPrimitiveNode() {
    }

    @Specialization
    protected static RIntVector doInt(int vector) {
        return RDataFactory.createIntVectorFromScalar(vector);
    }

    @Specialization
    protected static RDoubleVector doDouble(double vector) {
        return RDataFactory.createDoubleVectorFromScalar(vector);
    }

    @Specialization
    protected static RLogicalVector doLogical(byte vector) {
        return RDataFactory.createLogicalVectorFromScalar(vector);
    }

    @Specialization
    protected static RStringVector doString(String value) {
        return RDataFactory.createStringVectorFromScalar(value);
    }

    @Specialization
    protected static RRawVector doRaw(RRaw value) {
        return RDataFactory.createRawVectorFromScalar(value);
    }

    @Specialization
    protected static RComplexVector doComplex(RComplex value) {
        return RDataFactory.createComplexVectorFromScalar(value);
    }

    @Specialization
    protected static CharSXPWrapper doCharSXPWrapper(CharSXPWrapper value) {
        return value;
    }

    /*
     * For the limit we use the number of primitive specializations - 1. After that its better to
     * check !isPrimitive.
     */
    @Specialization(limit = "getCacheSize(5)", guards = "vector.getClass() == cachedClass")
    protected static Object doCached(Object vector,
                    @Cached("vector.getClass()") Class<?> cachedClass) {
        assert (!isPrimitive(vector));
        return cachedClass.cast(vector);
    }

    @Specialization(replaces = "doCached", guards = "!isPrimitive(vector)")
    protected static Object doGeneric(Object vector) {
        return vector;
    }

    public static BoxPrimitiveNode create() {
        return BoxPrimitiveNodeGen.create();
    }

    protected static boolean isPrimitive(Object value) {
        return (value instanceof Integer) || (value instanceof Double) ||
                        (value instanceof Byte) || (value instanceof String) || (value instanceof RRaw) || (value instanceof RComplex);
    }
}
