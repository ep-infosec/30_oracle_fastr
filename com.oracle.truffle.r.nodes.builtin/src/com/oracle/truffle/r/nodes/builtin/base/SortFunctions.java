/*
 * Copyright (c) 2013, 2022, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.truffle.r.nodes.builtin.CastBuilder.Predef.abstractVectorValue;
import static com.oracle.truffle.r.nodes.builtin.CastBuilder.Predef.constant;
import static com.oracle.truffle.r.nodes.builtin.CastBuilder.Predef.doubleValue;
import static com.oracle.truffle.r.nodes.builtin.CastBuilder.Predef.instanceOf;
import static com.oracle.truffle.r.nodes.builtin.CastBuilder.Predef.integerValue;
import static com.oracle.truffle.r.nodes.builtin.CastBuilder.Predef.numericValue;
import static com.oracle.truffle.r.nodes.builtin.CastBuilder.Predef.rawValue;
import static com.oracle.truffle.r.nodes.builtin.CastBuilder.Predef.toBoolean;
import static com.oracle.truffle.r.runtime.RError.Message.INVALID_LOGICAL;
import static com.oracle.truffle.r.runtime.RError.Message.NOT_NUMERIC_VECTOR;
import static com.oracle.truffle.r.runtime.RError.Message.ONLY_ATOMIC_CAN_BE_SORTED;
import static com.oracle.truffle.r.runtime.RError.Message.RAW_SORT;
import static com.oracle.truffle.r.runtime.builtins.RBehavior.PURE;
import static com.oracle.truffle.r.runtime.builtins.RBuiltinKind.INTERNAL;

import java.util.Arrays;
import java.util.Collections;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.r.nodes.builtin.NodeWithArgumentCasts.Casts;
import com.oracle.truffle.r.nodes.builtin.RBuiltinNode;
import com.oracle.truffle.r.runtime.DSLConfig;
import com.oracle.truffle.r.runtime.RError;
import com.oracle.truffle.r.runtime.RRuntime;
import com.oracle.truffle.r.runtime.builtins.RBuiltin;
import com.oracle.truffle.r.runtime.data.RArgsValuesAndNames;
import com.oracle.truffle.r.runtime.data.RComplexVector;
import com.oracle.truffle.r.runtime.data.RDataFactory;
import com.oracle.truffle.r.runtime.data.RDoubleVector;
import com.oracle.truffle.r.runtime.data.RIntVector;
import com.oracle.truffle.r.runtime.data.RLogicalVector;
import com.oracle.truffle.r.runtime.data.RNull;
import com.oracle.truffle.r.runtime.data.RStringVector;
import com.oracle.truffle.r.runtime.data.VectorDataLibrary;
import com.oracle.truffle.r.runtime.data.model.RAbstractListVector;

/**
 * The internal functions mandated by {@code base/sort.R}. N.B. We use the standard JDK sorting
 * algorithms and not the specific algorithms specified in the R manual entry.
 */
public class SortFunctions {

    protected static void addCastForX(Casts casts) {
        casts.arg("x").allowNull().mustBe(rawValue().not(), RAW_SORT).mustBe(instanceOf(RAbstractListVector.class).not(), ONLY_ATOMIC_CAN_BE_SORTED).mustBe(
                        abstractVectorValue(), ONLY_ATOMIC_CAN_BE_SORTED);
    }

    protected static void addCastForDecreasing(Casts casts) {
        casts.arg("decreasing").defaultError(INVALID_LOGICAL, "decreasing").mustBe(numericValue()).asLogicalVector().findFirst().map(toBoolean());
    }

    @TruffleBoundary
    private static double[] sort(double[] data, boolean decreasing) {
        // no reverse comparator for primitives
        Arrays.parallelSort(data);
        if (decreasing) {
            int len = data.length;
            for (int i = len / 2 - 1; i >= 0; i--) {
                double temp = data[i];
                data[i] = data[len - i - 1];
                data[len - i - 1] = temp;
            }
        }
        return data;
    }

    @TruffleBoundary
    private static int[] sort(int[] data, boolean decreasing) {
        Arrays.parallelSort(data);
        if (decreasing) {
            int len = data.length;
            for (int i = len / 2 - 1; i >= 0; i--) {
                int temp = data[i];
                data[i] = data[len - i - 1];
                data[len - i - 1] = temp;
            }
        }
        return data;
    }

    @TruffleBoundary
    private static byte[] sort(byte[] data, boolean decreasing) {
        Arrays.parallelSort(data);
        if (decreasing) {
            int len = data.length;
            for (int i = len / 2 - 1; i >= 0; i--) {
                byte temp = data[i];
                data[i] = data[len - i - 1];
                data[len - i - 1] = temp;
            }
        }
        return data;
    }

    @TruffleBoundary
    private static String[] sort(String[] data, boolean decreasing) {
        if (decreasing) {
            Arrays.parallelSort(data, Collections.reverseOrder());
        } else {
            Arrays.parallelSort(data);
        }
        return data;
    }

    protected static RDoubleVector jdkSort(RDoubleVector vec, boolean decreasing, VectorDataLibrary vecDataLib) {
        double[] data = vec.materialize().getDataCopy();
        return RDataFactory.createDoubleVector(sort(data, decreasing), vecDataLib.isComplete(vec.getData()));
    }

    protected static RIntVector jdkSort(RIntVector vec, boolean decreasing, VectorDataLibrary vecDataLib) {
        int[] data = vec.materialize().getDataCopy();
        return RDataFactory.createIntVector(sort(data, decreasing), vecDataLib.isComplete(vec.getData()));
    }

    protected static RStringVector jdkSort(RStringVector vec, boolean decreasing, VectorDataLibrary vecDataLib) {
        String[] data = vec.materialize().getDataCopy();
        return RDataFactory.createStringVector(sort(data, decreasing), vecDataLib.isComplete(vec.getData()));
    }

    protected static RLogicalVector jdkSort(RLogicalVector vec, boolean decreasing, VectorDataLibrary vecDataLib) {
        byte[] data = vec.materialize().getDataCopy();
        return RDataFactory.createLogicalVector(sort(data, decreasing), vecDataLib.isComplete(vec.getData()));
    }

    /**
     * In GnuR this is a shell sort variant, see
     * <a href = "https://stat.ethz.ch/R-manual/R-devel/library/base/html/sort.html>here">here</a>.
     * The JDK does not have a shell sort so for now we just use the default JDK sort (quicksort).
     *
     * N.B. The R code strips out {@code NA} and {@code NaN} values before calling the builtin.
     */
    @RBuiltin(name = "sort", kind = INTERNAL, parameterNames = {"x", "decreasing"}, behavior = PURE)
    public abstract static class Sort extends RBuiltinNode.Arg2 {

        static {
            Casts casts = new Casts(Sort.class);
            addCastForX(casts);
            addCastForDecreasing(casts);
        }

        @Child private VectorDataLibrary vectorDataLib = VectorDataLibrary.getFactory().createDispatched(DSLConfig.getGenericDataLibraryCacheSize());

        @Specialization
        protected RDoubleVector sort(RDoubleVector vec, boolean decreasing) {
            return jdkSort(vec, decreasing, vectorDataLib);
        }

        @Specialization
        protected RIntVector sort(RIntVector vec, boolean decreasing) {
            return jdkSort(vec, decreasing, vectorDataLib);
        }

        @Specialization
        protected RStringVector sort(RStringVector vec, boolean decreasing) {
            return jdkSort(vec, decreasing, vectorDataLib);
        }

        @Specialization
        protected RLogicalVector sort(RLogicalVector vec, boolean decreasing) {
            return jdkSort(vec, decreasing, vectorDataLib);
        }

        @Specialization
        protected RLogicalVector sort(@SuppressWarnings("unused") RComplexVector vec, @SuppressWarnings("unused") boolean decreasing) {
            // TODO: implement complex sort
            throw RError.error(this, RError.Message.UNIMPLEMENTED_ARG_TYPE, 1);
        }

        @Specialization
        protected RNull sort(@SuppressWarnings("unused") RNull vec, @SuppressWarnings("unused") boolean decreasing) {
            return RNull.instance;
        }
    }

    @RBuiltin(name = "qsort", kind = INTERNAL, parameterNames = {"x", "decreasing"}, behavior = PURE)
    public abstract static class QSort extends RBuiltinNode.Arg2 {

        static {
            Casts casts = new Casts(QSort.class);
            casts.arg("x").defaultError(NOT_NUMERIC_VECTOR).mustBe(instanceOf(RAbstractListVector.class).not()).mustBe(integerValue().or(doubleValue()));
            casts.arg("decreasing").mapIf(numericValue().not(), constant(RRuntime.LOGICAL_TRUE)).asLogicalVector().findFirst().map(toBoolean());
        }

        @Child private VectorDataLibrary vectorDataLib = VectorDataLibrary.getFactory().createDispatched(DSLConfig.getGenericDataLibraryCacheSize());

        @Specialization
        protected RDoubleVector qsort(RDoubleVector vec, boolean decreasing) {
            return jdkSort(vec, decreasing, vectorDataLib);
        }

        @Specialization
        protected RIntVector qsort(RIntVector vec, boolean decreasing) {
            return jdkSort(vec, decreasing, vectorDataLib);
        }
    }

    @RBuiltin(name = "psort", kind = INTERNAL, parameterNames = {"x", "partial"}, behavior = PURE)
    public abstract static class PartialSort extends RBuiltinNode.Arg2 {

        static {
            Casts casts = new Casts(PartialSort.class);
            addCastForX(casts);
        }

        @Child private VectorDataLibrary vectorDataLib = VectorDataLibrary.getFactory().createDispatched(DSLConfig.getGenericDataLibraryCacheSize());

        @SuppressWarnings("unused")
        @Specialization
        protected RDoubleVector sort(RDoubleVector vec, Object partial) {
            return jdkSort(vec, false, vectorDataLib);
        }

        @SuppressWarnings("unused")
        @Specialization
        protected RIntVector sort(RIntVector vec, Object partial) {
            return jdkSort(vec, false, vectorDataLib);
        }

        @SuppressWarnings("unused")
        @Specialization
        protected RStringVector sort(RStringVector vec, Object partial) {
            return jdkSort(vec, false, vectorDataLib);
        }

        @SuppressWarnings("unused")
        @Specialization
        protected RLogicalVector sort(RLogicalVector vec, Object partial) {
            return jdkSort(vec, false, vectorDataLib);
        }

        @SuppressWarnings("unused")
        @Specialization
        protected RLogicalVector sort(RComplexVector vec, Object partial) {
            throw RError.error(this, RError.Message.UNIMPLEMENTED_ARG_TYPE, 1); // [TODO] implement
        }

        @Specialization
        protected RNull sort(@SuppressWarnings("unused") RNull vec, @SuppressWarnings("unused") Object partial) {
            return RNull.instance;
        }
    }

    /**
     * This a helper function for the code in sort.R. It does NOT return the input vectors sorted,
     * but returns an {@link RIntVector} of indices (positions) indicating the sort order (Or
     * {@link RNull#instance} if no vectors). In short it is a special variant of {@code order}. For
     * now we delegate to {@code order} and do not implement the {@code retgrp} argument.
     */
    @RBuiltin(name = "radixsort", kind = INTERNAL, parameterNames = {"na.last", "decreasing", "retgrp", "sortstr", "..."}, behavior = PURE)
    public abstract static class RadixSort extends RBuiltinNode.Arg5 {
        @Child private Order orderNode = OrderNodeGen.create();

        static {
            Casts casts = new Casts(RadixSort.class);
            casts.arg("na.last").asLogicalVector().findFirst();
            casts.arg("decreasing").mustBe(numericValue(), INVALID_LOGICAL, "decreasing").asLogicalVector();
            casts.arg("retgrp").asLogicalVector().findFirst().map(toBoolean());
            casts.arg("sortstr").asLogicalVector().findFirst().map(toBoolean());
        }

        @SuppressWarnings("unused")
        @Specialization(limit = "getVectorAccessCacheSize()")
        protected Object radixSort(byte naLast, RLogicalVector decreasingVec, boolean retgrp, boolean sortstr, RArgsValuesAndNames zz,
                        @Bind("decreasingVec.getData()") Object decreasingVecData,
                        @CachedLibrary("decreasingVecData") VectorDataLibrary decreasingDataLib) {
            // Partial implementation just to get startup to work
            if (retgrp) {
                // sortstr only has an effect when retrgrp == true
                throw RError.nyi(this, "radixsort: retgrp == TRUE not implemented");
            }
            int nargs = zz.getLength();
            if (nargs == 0) {
                return RNull.instance;
            }
            if (nargs != decreasingDataLib.getLength(decreasingVecData)) {
                throw error(RError.Message.RADIX_SORT_DEC_MATCH);
            }
            /*
             * Order takes a single decreasing argument that applies to all the vectors. We
             * potentially have a different value for each vector, so we have to process one by one.
             * However, OrderNode can't yet handle that, so we abort if nargs > 1 and the decreasing
             * values don't match.
             */
            byte lastdb = RRuntime.LOGICAL_NA;
            for (int i = 0; i < nargs; i++) {
                byte db = decreasingDataLib.getLogicalAt(decreasingVecData, i);
                if (RRuntime.isNA(db)) {
                    throw error(RError.Message.RADIX_SORT_DEC_NOT_LOGICAL);
                }
                if (lastdb != RRuntime.LOGICAL_NA && db != lastdb) {
                    throw RError.nyi(this, "radixsort: args > 1 with differing 'decreasing' values not implemented");
                }
                lastdb = db;
            }
            boolean decreasing = RRuntime.fromLogical(decreasingDataLib.getLogicalAt(decreasingVecData, 0));
            Object result = orderNode.execute(naLast, decreasing, zz);
            return result;
        }
    }
}
