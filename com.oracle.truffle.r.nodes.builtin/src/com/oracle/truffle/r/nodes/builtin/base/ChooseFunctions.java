/*
 * Copyright (c) 2017, 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.r.nodes.builtin.base;

import static com.oracle.truffle.r.nodes.builtin.CastBuilder.Predef.asIntegerVector;
import static com.oracle.truffle.r.nodes.builtin.CastBuilder.Predef.logicalValue;
import static com.oracle.truffle.r.nodes.builtin.CastBuilder.Predef.numericValue;
import static com.oracle.truffle.r.runtime.builtins.RBehavior.PURE;
import static com.oracle.truffle.r.runtime.builtins.RBuiltinKind.INTERNAL;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.r.nodes.builtin.RBuiltinNode;
import com.oracle.truffle.r.runtime.RError.Message;
import com.oracle.truffle.r.runtime.RRuntime;
import com.oracle.truffle.r.runtime.RType;
import com.oracle.truffle.r.runtime.builtins.RBuiltin;
import com.oracle.truffle.r.runtime.data.RDataFactory.VectorFactory;
import com.oracle.truffle.r.runtime.data.RDoubleVector;
import com.oracle.truffle.r.runtime.data.VectorDataLibrary;
import com.oracle.truffle.r.runtime.data.VectorDataLibrary.SeqIterator;
import com.oracle.truffle.r.runtime.data.model.RAbstractVector;
import com.oracle.truffle.r.runtime.data.nodes.attributes.UnaryCopyAttributesNode;

public final class ChooseFunctions {

    /**
     * Binomial coefficients (n, k) for real n and integral k (rounded with warning).
     */
    @RBuiltin(name = "choose", kind = INTERNAL, parameterNames = {"n", "k"}, behavior = PURE)
    public abstract static class Choose extends RBuiltinNode.Arg2 {

        static {
            Casts casts = new Casts(Choose.class);
            casts.arg(0).mustBe(numericValue(), Message.NON_NUMERIC_MATH).mapIf(logicalValue(), asIntegerVector());
            casts.arg(1).mustBe(numericValue(), Message.NON_NUMERIC_MATH).mapIf(logicalValue(), asIntegerVector());
        }

        @Child private UnaryCopyAttributesNode copyAttrs = UnaryCopyAttributesNode.create();

        @Specialization(limit = "getGenericDataLibraryCacheSize()")
        protected RDoubleVector doVectors(RAbstractVector n, RAbstractVector k,
                        @CachedLibrary("n.getData()") VectorDataLibrary nDataLib,
                        @CachedLibrary("k.getData()") VectorDataLibrary kDataLib,
                        @Cached("create()") VectorFactory factory) {
            Object nData = n.getData();
            Object kData = k.getData();
            SeqIterator nIter = nDataLib.iterator(nData);
            SeqIterator kIter = kDataLib.iterator(kData);
            int nLength = nDataLib.getLength(nData);
            int kLength = kDataLib.getLength(kData);
            int resultLen = Math.max(nLength, kLength);
            double[] result = new double[resultLen];
            for (int i = 0; i < resultLen; i++) {
                nDataLib.nextWithWrap(nData, nIter);
                kDataLib.nextWithWrap(kData, kIter);
                double nScalar = nDataLib.getNextDouble(nData, nIter);
                int kScalar = (kDataLib.getType(kData) == RType.Integer) ? kDataLib.getNextInt(kData, kIter) : castToInt(kDataLib.getNextDouble(kData, kIter));
                result[i] = choose(nScalar, kScalar);
            }
            RDoubleVector resultVector = factory.createDoubleVector(result, nDataLib.isComplete(nData) && kDataLib.isComplete(kData));
            if (resultLen == nLength) {
                copyAttrs.execute(resultVector, n);
            } else {
                copyAttrs.execute(resultVector, k);
            }
            return resultVector;
        }

        @TruffleBoundary
        @Specialization(replaces = "doVectors")
        protected RDoubleVector doVectorsGeneric(RAbstractVector n, RAbstractVector k,
                        @Cached("create()") VectorFactory factory) {
            VectorDataLibrary uncachedDataLib = VectorDataLibrary.getFactory().getUncached();
            return doVectors(n, k, uncachedDataLib, uncachedDataLib, factory);
        }

        private int castToInt(double data) {
            // In the cases where 'k' is real vector we round values in 'k' to integers. Warning is
            // shown only when the numbers indeed loose precision and is show for each such
            // instance. For example, if k is two times shorter than n, for each non-integral value
            // the warning will be shown twice.
            if (data == RRuntime.DOUBLE_NA) {
                return RRuntime.INT_NA;
            }

            double result = Math.round(data);
            if (result != data) {
                warning(Message.CHOOSE_ROUNDING_WARNING, data, (int) result);
            }
            return (int) result;
        }

        private double choose(double n, int k) {
            if (RRuntime.isNA(n) || RRuntime.isNA(k)) {
                return RRuntime.DOUBLE_NA;
            }
            return doChoose(n, k);
        }

        protected double doChoose(double n, int k) {
            return com.oracle.truffle.r.runtime.nmath.Choose.choose(n, k);
        }

    }

    /**
     * Builtin lchoose with double n and integer k vector parameters.
     */
    @RBuiltin(name = "lchoose", kind = INTERNAL, parameterNames = {"n", "k"}, behavior = PURE)
    public abstract static class LChoose extends Choose {

        static {
            Casts casts = new Casts(LChoose.class);
            casts.arg(0).mustBe(numericValue(), Message.NON_NUMERIC_MATH).mapIf(logicalValue(), asIntegerVector());
            casts.arg(1).mustBe(numericValue(), Message.NON_NUMERIC_MATH).mapIf(logicalValue(), asIntegerVector());
        }

        @Override
        protected double doChoose(double n, int k) {
            return com.oracle.truffle.r.runtime.nmath.Choose.lchoose(n, k);
        }

    }

}
