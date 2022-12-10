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
 * Copyright (c) 2014, Purdue University
 * Copyright (c) 2014, 2021, Oracle and/or its affiliates
 *
 * All rights reserved.
 */
package com.oracle.truffle.r.nodes.builtin.base;

import static com.oracle.truffle.r.nodes.builtin.CastBuilder.Predef.asDoubleVector;
import static com.oracle.truffle.r.nodes.builtin.CastBuilder.Predef.complexValue;
import static com.oracle.truffle.r.nodes.builtin.CastBuilder.Predef.missingValue;
import static com.oracle.truffle.r.runtime.RDispatch.MATH_GROUP_GENERIC;
import static com.oracle.truffle.r.runtime.builtins.RBehavior.PURE;
import static com.oracle.truffle.r.runtime.builtins.RBuiltinKind.PRIMITIVE;

import java.util.Arrays;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.r.runtime.data.nodes.attributes.SpecialAttributesFunctions.ExtractNamesAttributeNode;
import com.oracle.truffle.r.nodes.builtin.RBuiltinNode;
import com.oracle.truffle.r.runtime.RError;
import com.oracle.truffle.r.runtime.RRuntime;
import com.oracle.truffle.r.runtime.builtins.RBuiltin;
import com.oracle.truffle.r.runtime.data.RComplex;
import com.oracle.truffle.r.runtime.data.RDataFactory;
import com.oracle.truffle.r.runtime.data.RDoubleVector;
import com.oracle.truffle.r.runtime.data.RNull;
import com.oracle.truffle.r.runtime.data.RComplexVector;
import com.oracle.truffle.r.runtime.data.nodes.VectorAccess;
import com.oracle.truffle.r.runtime.data.nodes.VectorAccess.SequentialIterator;
import com.oracle.truffle.r.runtime.ops.BinaryArithmetic;

@RBuiltin(name = "cumprod", kind = PRIMITIVE, parameterNames = {"x"}, dispatch = MATH_GROUP_GENERIC, behavior = PURE)
public abstract class CumProd extends RBuiltinNode.Arg1 {

    @Child private ExtractNamesAttributeNode extractNamesNode = ExtractNamesAttributeNode.create();
    @Child private BinaryArithmetic mul = BinaryArithmetic.MULTIPLY.createOperation();

    static {
        Casts casts = new Casts(CumProd.class);
        casts.arg("x").allowNull().mustBe(missingValue().not(), RError.Message.ARGUMENT_EMPTY, 0, "cumprod", 1).mapIf(complexValue().not(), asDoubleVector(true, false, false));
    }

    @Specialization
    protected double cumrpod(double arg) {
        return arg;
    }

    @Specialization
    protected RDoubleVector cumNull(@SuppressWarnings("unused") RNull x) {
        return RDataFactory.createEmptyDoubleVector();
    }

    @Specialization(guards = "xAccess.supports(x)", limit = "getVectorAccessCacheSize()")
    protected RDoubleVector cumprodDouble(RDoubleVector x,
                    @Cached("x.access()") VectorAccess xAccess) {
        SequentialIterator iter = xAccess.access(x);
        double[] array = new double[xAccess.getLength(iter)];
        double prev = 1;
        while (xAccess.next(iter)) {
            double value = xAccess.getDouble(iter);
            if (xAccess.na.check(value)) {
                Arrays.fill(array, iter.getIndex(), array.length, RRuntime.DOUBLE_NA);
                break;
            }
            if (xAccess.na.checkNAorNaN(value)) {
                Arrays.fill(array, iter.getIndex(), array.length, Double.NaN);
                break;
            }
            prev = mul.op(prev, value);
            assert !RRuntime.isNA(prev) : "double multiplication should not introduce NAs";
            array[iter.getIndex()] = prev;
        }
        return RDataFactory.createDoubleVector(array, xAccess.na.neverSeenNA(), extractNamesNode.execute(x));
    }

    @Specialization(replaces = "cumprodDouble")
    protected RDoubleVector cumprodDoubleGeneric(RDoubleVector x) {
        return cumprodDouble(x, x.slowPathAccess());
    }

    @Specialization(guards = "xAccess.supports(x)", limit = "getVectorAccessCacheSize()")
    protected RComplexVector cumprodComplex(RComplexVector x,
                    @Cached("x.access()") VectorAccess xAccess) {
        SequentialIterator iter = xAccess.access(x);
        double[] array = new double[xAccess.getLength(iter) * 2];
        RComplex prev = RComplex.valueOf(1, 0);
        while (xAccess.next(iter)) {
            double real = xAccess.getComplexR(iter);
            double imag = xAccess.getComplexI(iter);
            if (xAccess.na.check(real, imag)) {
                Arrays.fill(array, 2 * iter.getIndex(), array.length, RRuntime.DOUBLE_NA);
                break;
            }
            prev = mul.op(prev.getRealPart(), prev.getImaginaryPart(), real, imag);
            assert !RRuntime.isNA(prev) : "complex multiplication should not introduce NAs";
            array[iter.getIndex() * 2] = prev.getRealPart();
            array[iter.getIndex() * 2 + 1] = prev.getImaginaryPart();
        }
        return RDataFactory.createComplexVector(array, xAccess.na.neverSeenNA(), extractNamesNode.execute(x));
    }

    @Specialization(replaces = "cumprodComplex")
    protected RComplexVector cumprodComplexGeneric(RComplexVector x) {
        return cumprodComplex(x, x.slowPathAccess());
    }
}
