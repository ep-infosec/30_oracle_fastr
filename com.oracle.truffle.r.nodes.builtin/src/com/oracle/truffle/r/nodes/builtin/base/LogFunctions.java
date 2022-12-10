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

import static com.oracle.truffle.r.nodes.builtin.CastBuilder.Predef.complexValue;
import static com.oracle.truffle.r.nodes.builtin.CastBuilder.Predef.numericValue;
import static com.oracle.truffle.r.runtime.RDispatch.MATH_GROUP_GENERIC;
import static com.oracle.truffle.r.runtime.builtins.RBehavior.PURE;
import static com.oracle.truffle.r.runtime.builtins.RBuiltinKind.PRIMITIVE;

import java.util.Arrays;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.ControlFlowException;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.api.profiles.ConditionProfile;
import com.oracle.truffle.api.profiles.ValueProfile;
import com.oracle.truffle.r.runtime.data.nodes.attributes.CopyOfRegAttributesNode;
import com.oracle.truffle.r.runtime.data.nodes.attributes.SpecialAttributesFunctions.InitDimsNamesDimNamesNode;
import com.oracle.truffle.r.nodes.binary.BinaryMapArithmeticFunctionNode;
import com.oracle.truffle.r.nodes.builtin.CastBuilder.Predef;
import com.oracle.truffle.r.nodes.builtin.RBuiltinNode;
import com.oracle.truffle.r.runtime.RError;
import com.oracle.truffle.r.runtime.RError.Message;
import com.oracle.truffle.r.runtime.RRuntime;
import com.oracle.truffle.r.runtime.RType;
import com.oracle.truffle.r.runtime.builtins.RBuiltin;
import com.oracle.truffle.r.runtime.data.RComplex;
import com.oracle.truffle.r.runtime.data.RDataFactory;
import com.oracle.truffle.r.runtime.data.RDoubleVector;
import com.oracle.truffle.r.runtime.data.RMissing;
import com.oracle.truffle.r.runtime.data.RComplexVector;
import com.oracle.truffle.r.runtime.data.model.RAbstractVector;
import com.oracle.truffle.r.runtime.nodes.RBaseNode;
import com.oracle.truffle.r.runtime.ops.BinaryArithmetic;
import com.oracle.truffle.r.runtime.ops.UnaryArithmetic;
import com.oracle.truffle.r.runtime.ops.na.NACheck;
import com.oracle.truffle.r.runtime.ops.na.NAProfile;

public class LogFunctions {
    @RBuiltin(name = "log", kind = PRIMITIVE, parameterNames = {"x", "base"}, dispatch = MATH_GROUP_GENERIC, behavior = PURE)
    public abstract static class Log extends RBuiltinNode.Arg2 {

        private final NAProfile naX = NAProfile.create();
        private final BranchProfile nanProfile = BranchProfile.create();
        private final BranchProfile warningProfile = BranchProfile.create();

        @Override
        public Object[] getDefaultParameterValues() {
            return new Object[]{RMissing.instance, Math.E};
        }

        static {
            Casts casts = new Casts(Log.class);
            casts.arg("x").defaultError(RError.Message.NON_NUMERIC_ARGUMENT_FUNCTION).mustBe(numericValue().or(complexValue()));
            casts.arg("base").defaultError(RError.Message.NON_NUMERIC_ARGUMENT_FUNCTION).mustBe(numericValue().or(complexValue())).mapIf(numericValue(), Predef.asDoubleVector(),
                            Predef.asComplexVector()).asVector().findFirst();
        }

        static BinaryMapArithmeticFunctionNode createDivNode() {
            return new BinaryMapArithmeticFunctionNode(BinaryArithmetic.DIV.createOperation());
        }

        @Specialization
        protected double log(byte x, double base,
                        @Cached("create()") NAProfile naBase) {
            if (naX.isNA(x)) {
                return RRuntime.DOUBLE_NA;
            }
            return logb(x, base, naBase);
        }

        @Specialization
        protected double log(int x, double base,
                        @Cached("create()") NAProfile naBase) {
            if (naX.isNA(x)) {
                return RRuntime.DOUBLE_NA;
            }
            return logb(x, base, naBase);
        }

        @Specialization
        protected double log(double x, double base,
                        @Cached("create()") NAProfile naBase) {
            if (naX.isNA(x)) {
                return RRuntime.DOUBLE_NA;
            }
            return logb(x, base, naBase);
        }

        @Specialization
        protected RComplex log(RComplex x, double base,
                        @Cached("createDivNode()") BinaryMapArithmeticFunctionNode divNode,
                        @Cached("create()") NAProfile naBase) {
            if (naX.isNA(x)) {
                return x;
            }
            return logb(x, RComplex.valueOf(base, 0), divNode, naBase);
        }

        @Specialization
        protected RComplex log(byte x, RComplex base,
                        @Cached("createDivNode()") BinaryMapArithmeticFunctionNode divNode,
                        @Cached("create()") NAProfile naBase) {
            if (naX.isNA(x)) {
                return RRuntime.COMPLEX_NA;
            }
            return logb(RComplex.valueOf(x, 0), base, divNode, naBase);
        }

        @Specialization
        protected RComplex log(int x, RComplex base,
                        @Cached("createDivNode()") BinaryMapArithmeticFunctionNode divNode,
                        @Cached("create()") NAProfile naBase) {
            if (naX.isNA(x)) {
                return RRuntime.COMPLEX_NA;
            }
            return logb(RComplex.valueOf(x, 0), base, divNode, naBase);
        }

        @Specialization
        protected RComplex log(double x, RComplex base,
                        @Cached("createDivNode()") BinaryMapArithmeticFunctionNode divNode,
                        @Cached("create()") NAProfile naBase) {
            if (naX.isNA(x)) {
                return RRuntime.COMPLEX_NA;
            }
            return logb(RComplex.valueOf(x, 0), base, divNode, naBase);
        }

        @Specialization
        protected RComplex log(RComplex x, RComplex base,
                        @Cached("createDivNode()") BinaryMapArithmeticFunctionNode divNode,
                        @Cached("create()") NAProfile naBase) {
            if (naX.isNA(x)) {
                return RRuntime.COMPLEX_NA;
            }
            return logb(x, base, divNode, naBase);
        }

        @Specialization(guards = "!isRComplexVector(vector)")
        protected RDoubleVector log(RAbstractVector vector, double base,
                        @Cached("createClassProfile()") ValueProfile vectorProfile,
                        @Cached("createBinaryProfile()") ConditionProfile isNAProfile,
                        @Cached("create()") CopyOfRegAttributesNode copyAttrsNode,
                        @Cached("create()") InitDimsNamesDimNamesNode initDimsNamesDimNames,
                        @Cached("create()") NACheck xNACheck,
                        @Cached("create()") NACheck baseNACheck) {
            RDoubleVector doubleVector = (RDoubleVector) vectorProfile.profile(vector).castSafe(RType.Double, isNAProfile);
            return logInternal(doubleVector, base, copyAttrsNode, initDimsNamesDimNames, xNACheck, baseNACheck);
        }

        @Specialization
        protected RComplexVector log(RComplexVector vector, double base,
                        @Cached("createClassProfile()") ValueProfile vectorProfile,
                        @Cached("create()") CopyOfRegAttributesNode copyAttrsNode,
                        @Cached("create()") InitDimsNamesDimNamesNode initDimsNamesDimNames,
                        @Cached("createDivNode()") BinaryMapArithmeticFunctionNode divNode,
                        @Cached("create()") NACheck xNACheck,
                        @Cached("create()") NACheck baseNACheck) {
            return logInternal(vectorProfile.profile(vector), RComplex.valueOf(base, 0), divNode, initDimsNamesDimNames, copyAttrsNode, xNACheck, baseNACheck);
        }

        @Specialization
        protected RComplexVector log(RAbstractVector vector, RComplex base,
                        @Cached("createClassProfile()") ValueProfile vectorProfile,
                        @Cached("createBinaryProfile()") ConditionProfile isNAProfile,
                        @Cached("create()") CopyOfRegAttributesNode copyAttrsNode,
                        @Cached("create()") InitDimsNamesDimNamesNode initDimsNamesDimNames,
                        @Cached("createDivNode()") BinaryMapArithmeticFunctionNode divNode,
                        @Cached("create()") NACheck xNACheck,
                        @Cached("create()") NACheck baseNACheck) {
            RComplexVector complexVector = (RComplexVector) vectorProfile.profile(vector).castSafe(RType.Complex, isNAProfile);
            return logInternal(complexVector, base, divNode, initDimsNamesDimNames, copyAttrsNode, xNACheck, baseNACheck);
        }

        private RDoubleVector logInternal(RDoubleVector vector, double base, CopyOfRegAttributesNode copyAttrsNode, InitDimsNamesDimNamesNode initDimsNamesDimNames,
                        NACheck xNACheck, NACheck baseNACheck) {
            baseNACheck.enable(base);
            double[] resultVector = new double[vector.getLength()];
            if (baseNACheck.check(base)) {
                Arrays.fill(resultVector, 0, resultVector.length, base);
            } else if (Double.isNaN(base)) {
                nanProfile.enter();
                Arrays.fill(resultVector, 0, resultVector.length, Double.NaN);
            } else {
                xNACheck.enable(vector);
                ShowWarningException showWarning = null;
                for (int i = 0; i < vector.getLength(); i++) {
                    double value = vector.getDataAt(i);
                    if (xNACheck.check(value)) {
                        resultVector[i] = RRuntime.DOUBLE_NA;
                    } else {
                        try {
                            resultVector[i] = logb(value, base);
                        } catch (ShowWarningException ex) {
                            showWarning = ex;
                            resultVector[i] = ex.result;
                        }
                    }
                }
                if (showWarning != null) {
                    RError.warning(showWarning.context, showWarning.message);
                }
            }
            boolean complete = xNACheck.neverSeenNA() && baseNACheck.neverSeenNA();
            return createResult(vector, resultVector, complete, copyAttrsNode, initDimsNamesDimNames);
        }

        private double logb(double x, double base, NAProfile naBase) {
            if (naBase.isNA(base)) {
                return RRuntime.DOUBLE_NA;
            }

            if (Double.isNaN(base)) {
                nanProfile.enter();
                return base;
            }
            try {
                return logb(x, base);
            } catch (ShowWarningException showWarn) {
                RError.warning(showWarn.context, showWarn.message);
                return showWarn.result;
            }
        }

        private double logb(double x, double base) throws ShowWarningException {
            double logx = Math.log(x);
            RBaseNode warningCtx = null;
            if (!Double.isNaN(x) && Double.isNaN(logx)) {
                warningProfile.enter();
                warningCtx = this;
            }
            double result;
            if (base == Math.E) {
                result = logx;
            } else {
                result = logx / Math.log(base);
                if (warningCtx == null && Double.isNaN(result)) {
                    warningProfile.enter();
                    warningCtx = RError.SHOW_CALLER;
                }
            }
            if (warningCtx != null) {
                throw new ShowWarningException(result, warningCtx, RError.Message.NAN_PRODUCED);
            }
            return result;
        }

        private static final class ShowWarningException extends ControlFlowException {
            private static final long serialVersionUID = -5922014313815330744L;
            @SuppressWarnings("serial") final RBaseNode context;
            final RError.Message message;
            final double result;

            ShowWarningException(double result, RBaseNode context, Message message) {
                this.result = result;
                this.context = context;
                this.message = message;
            }
        }

        private RComplexVector logInternal(RComplexVector vector, RComplex base, BinaryMapArithmeticFunctionNode divNode,
                        InitDimsNamesDimNamesNode initDimsNamesDimNames, CopyOfRegAttributesNode copyAttrsNode, NACheck xNACheck, NACheck baseNACheck) {
            baseNACheck.enable(base);
            double[] complexVector = new double[vector.getLength() * 2];
            boolean complete;
            if (baseNACheck.check(base)) {
                int fillStartIndex = 0;
                int len = vector.getLength();
                for (int i = 0; i < len; i++) {
                    RComplex value = vector.getDataAt(i);
                    if (value.isNA()) {
                        int i2 = i << 1;
                        if (i2 > fillStartIndex) {
                            Arrays.fill(complexVector, fillStartIndex, i2, Double.NaN);
                        }
                        complexVector[i2++] = RRuntime.COMPLEX_NA_REAL_PART;
                        complexVector[i2++] = RRuntime.COMPLEX_NA_IMAGINARY_PART;
                        fillStartIndex = i2;
                    }
                }
                complete = (fillStartIndex == 0);
                if ((len << 1) > fillStartIndex) {
                    Arrays.fill(complexVector, fillStartIndex, (len << 1), Double.NaN);
                }
            } else {
                if (Double.isNaN(base.getRealPart()) || Double.isNaN(base.getImaginaryPart())) {
                    nanProfile.enter();
                    Arrays.fill(complexVector, 0, complexVector.length, Double.NaN);
                } else {
                    xNACheck.enable(vector);
                    boolean seenNaN = false;
                    for (int i = 0; i < vector.getLength(); i++) {
                        RComplex value = vector.getDataAt(i);
                        if (xNACheck.check(value)) {
                            fill(complexVector, i * 2, value);
                        } else {
                            RComplex rc = logb(value, base, divNode, false);
                            seenNaN = isNaN(rc);
                            fill(complexVector, i * 2, rc);
                        }
                    }
                    if (seenNaN) {
                        RError.warning(this, RError.Message.NAN_PRODUCED_IN_FUNCTION, "log");
                    }
                }
                complete = xNACheck.neverSeenNA() && baseNACheck.neverSeenNA();
            }
            return createResult(vector, complexVector, complete, initDimsNamesDimNames, copyAttrsNode);
        }

        private static void fill(double[] array, int i, RComplex rc) {
            array[i] = rc.getRealPart();
            array[i + 1] = rc.getImaginaryPart();
        }

        private RComplex logb(RComplex x, RComplex base, BinaryMapArithmeticFunctionNode div, NAProfile naBase) {
            if (naBase.isNA(base)) {
                return RComplex.valueOf(Double.NaN, Double.NaN);
            }
            if (isNaN(base)) {
                nanProfile.enter();
                return base;
            }
            return logb(x, base, div, true);
        }

        private RComplex logb(RComplex x, RComplex base, BinaryMapArithmeticFunctionNode div, boolean nanWarning) {
            RComplex logx = logb(x);
            if (base.getRealPart() == Math.E) {
                return logx;
            }

            RComplex logbase = logb(base);
            RComplex ret = div.applyComplex(logx, logbase);
            if (nanWarning && isNaN(ret)) {
                RError.warning(this, RError.Message.NAN_PRODUCED_IN_FUNCTION, "log");
            }
            return ret;
        }

        private static RComplex logb(RComplex x) {
            double re = x.getRealPart();
            double im = x.getImaginaryPart();

            double mod = RComplex.abs(re, im);
            double arg = Math.atan2(im, re);

            return RComplex.valueOf(Math.log(mod), arg);
        }

        private static RDoubleVector createResult(RAbstractVector source, double[] resultData, boolean complete,
                        CopyOfRegAttributesNode copyAttrsNode, InitDimsNamesDimNamesNode initDimsNamesDimNames) {
            RDoubleVector result = RDataFactory.createDoubleVector(resultData, complete);
            initDimsNamesDimNames.initAttributes(result, source);
            copyAttrsNode.execute(source, result);
            return result;
        }

        private static RComplexVector createResult(RAbstractVector source, double[] resultData, boolean complete,
                        InitDimsNamesDimNamesNode initDimsNamesDimNames, CopyOfRegAttributesNode copyAttrsNode) {
            RComplexVector result = RDataFactory.createComplexVector(resultData, complete);
            initDimsNamesDimNames.initAttributes(result, source);
            copyAttrsNode.execute(source, result);
            return result;
        }

        private static boolean isNaN(RComplex base) {
            return Double.isNaN(base.getRealPart()) || Double.isNaN(base.getImaginaryPart());
        }
    }

    @RBuiltin(name = "log10", kind = PRIMITIVE, parameterNames = {"x"}, dispatch = MATH_GROUP_GENERIC, behavior = PURE)
    public static final class Log10 extends UnaryArithmetic {

        private static final double LOG_10 = Math.log(10);

        @Override
        public double op(double op) {
            return Math.log10(op);
        }

        @Override
        public RComplex op(double re, double im) {
            double arg = Math.atan2(im, re);
            double mod = RComplex.abs(re, im);
            return RComplex.valueOf(Math.log10(mod), arg / LOG_10);
        }
    }

    @RBuiltin(name = "log2", kind = PRIMITIVE, parameterNames = {"x"}, dispatch = MATH_GROUP_GENERIC, behavior = PURE)
    public static final class Log2 extends UnaryArithmetic {

        private static final double LOG_2 = Math.log(2);

        @Override
        public double op(double op) {
            return Math.log(op) / LOG_2;
        }

        @Override
        public RComplex op(double re, double im) {
            double arg = Math.atan2(im, re);
            double mod = RComplex.abs(re, im);
            return RComplex.valueOf(Math.log(mod) / LOG_2, arg / LOG_2);
        }
    }

    @RBuiltin(name = "log1p", kind = PRIMITIVE, parameterNames = {"x"}, dispatch = MATH_GROUP_GENERIC, behavior = PURE)
    public static final class Log1p extends UnaryArithmetic {

        @Override
        public double op(double op) {
            return Math.log1p(op);
        }

        @Override
        public RComplex op(double r, double i) {
            double re = r + 1;
            double im = i;
            double arg = Math.atan2(im, re);
            double mod = RComplex.abs(re, im);
            return RComplex.valueOf(Math.log(mod), arg);
        }
    }
}
