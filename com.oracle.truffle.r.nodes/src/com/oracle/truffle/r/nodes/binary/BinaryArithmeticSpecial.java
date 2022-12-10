/*
 * Copyright (c) 2016, 2020, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.api.profiles.ConditionProfile;
import com.oracle.truffle.r.nodes.binary.BinaryArithmeticSpecialNodeGen.IntegerBinaryArithmeticSpecialNodeGen;
import com.oracle.truffle.r.nodes.unary.UnaryArithmeticSpecialNodeGen;
import com.oracle.truffle.r.runtime.ArgumentsSignature;
import com.oracle.truffle.r.runtime.DSLConfig;
import com.oracle.truffle.r.runtime.RError;
import com.oracle.truffle.r.runtime.RError.Message;
import com.oracle.truffle.r.runtime.RRuntime;
import com.oracle.truffle.r.runtime.RType;
import com.oracle.truffle.r.runtime.builtins.RSpecialFactory;
import com.oracle.truffle.r.runtime.data.RNull;
import com.oracle.truffle.r.runtime.data.VectorDataLibrary;
import com.oracle.truffle.r.runtime.data.model.RAbstractVector;
import com.oracle.truffle.r.runtime.nodes.RNode;
import com.oracle.truffle.r.runtime.ops.BinaryArithmetic;
import com.oracle.truffle.r.runtime.ops.BinaryArithmeticFactory;
import com.oracle.truffle.r.runtime.ops.UnaryArithmeticFactory;
import com.oracle.truffle.r.runtime.data.WarningInfo;

import static com.oracle.truffle.r.nodes.helpers.SpecialsUtils.unboxValue;

/**
 * Fast-path for scalar values: these cannot have any class attribute. Note: we intentionally use
 * empty type system to avoid conversions to vector types. Some binary operations have simple NA
 * handling, which is replicated here, others (notably pow and mul) throw
 * {@link RSpecialFactory#throwFullCallNeeded()} on NA.
 */
@NodeChild(value = "left", type = RNode.class)
@NodeChild(value = "right", type = RNode.class)
@ImportStatic(DSLConfig.class)
public abstract class BinaryArithmeticSpecial extends RNode {

    private final boolean handleNA;
    private final BinaryArithmeticFactory binaryFactory;
    private final UnaryArithmeticFactory unaryFactory;

    @Child private BinaryArithmetic operation;

    public BinaryArithmeticSpecial(BinaryArithmeticFactory binaryFactory, UnaryArithmeticFactory unaryFactory) {
        this.binaryFactory = binaryFactory;
        this.unaryFactory = unaryFactory;
        this.operation = binaryFactory.createOperation();
        this.handleNA = !(binaryFactory == BinaryArithmetic.POW || binaryFactory == BinaryArithmetic.MOD);
    }

    public static RSpecialFactory createSpecialFactory(BinaryArithmeticFactory binaryFactory, UnaryArithmeticFactory unaryFactory) {
        return new RSpecialFactory() {
            @Override
            public RNode create(ArgumentsSignature signature, RNode[] arguments, boolean inReplacement) {
                if (signature.getNonNullCount() == 0) {
                    if (arguments.length == 2) {
                        boolean handleIntegers = !(binaryFactory == BinaryArithmetic.POW || binaryFactory == BinaryArithmetic.DIV);
                        if (handleIntegers) {
                            return IntegerBinaryArithmeticSpecialNodeGen.create(binaryFactory, unaryFactory, unboxValue(arguments[0]), unboxValue(arguments[1]));
                        } else {
                            return BinaryArithmeticSpecialNodeGen.create(binaryFactory, unaryFactory, unboxValue(arguments[0]), unboxValue(arguments[1]));
                        }
                    } else if (arguments.length == 1 && unaryFactory != null) {
                        return UnaryArithmeticSpecialNodeGen.create(unaryFactory, unboxValue(arguments[0]));
                    }
                }
                return null;
            }
        };
    }

    @Specialization
    protected double doDoubles(double left, double right,
                    @Cached("createBinaryProfile()") ConditionProfile leftNanProfile,
                    @Cached("createBinaryProfile()") ConditionProfile rightNaProfile) {
        if (leftNanProfile.profile(Double.isNaN(left))) {
            checkFullCallNeededOnNA();
            return left;
        } else if (rightNaProfile.profile(RRuntime.isNA(right))) {
            checkFullCallNeededOnNA();
            return RRuntime.DOUBLE_NA;
        }
        return getOperation().op(left, right);
    }

    protected BinaryArithmeticNode createFull() {
        return BinaryArithmeticNodeGen.create(binaryFactory, unaryFactory);
    }

    // TODO There is a equivalence in logic between similar code in BinaryArithmeticNode, but
    // this code cannot assume RAbstractVector arguments.

    @Specialization
    protected Object doBothNull(@SuppressWarnings("unused") RNull left, @SuppressWarnings("unused") RNull right) {
        return operation instanceof BinaryArithmetic.Div || operation instanceof BinaryArithmetic.Pow ? RType.Double.getEmpty() : RType.Integer.getEmpty();
    }

    @Specialization
    protected Object doFallback(VirtualFrame frame, Object left, Object right,
                    @Cached("createFull()") BinaryArithmeticNode binary) {
        return binary.call(frame, left, right);
    }

    protected BinaryArithmetic getOperation() {
        return operation;
    }

    protected void checkFullCallNeededOnNA() {
        if (!handleNA) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw RSpecialFactory.throwFullCallNeeded();
        }
    }

    protected static boolean isNaN(double value) {
        return Double.isNaN(value) && !RRuntime.isNA(value);
    }

    protected static boolean areLength1(VectorDataLibrary aDataLib, RAbstractVector a, VectorDataLibrary bDataLib, RAbstractVector b) {
        return aDataLib.getLength(a.getData()) == 1 && bDataLib.getLength(b.getData()) == 1;
    }

    /**
     * Adds integers handling.
     */
    abstract static class IntegerBinaryArithmeticSpecial extends BinaryArithmeticSpecial {

        IntegerBinaryArithmeticSpecial(BinaryArithmeticFactory binaryFactory, UnaryArithmeticFactory unaryFactory) {
            super(binaryFactory, unaryFactory);
        }

        @Specialization(insertBefore = "doFallback")
        public int doIntegers(int left, int right,
                        @Cached("createBinaryProfile()") ConditionProfile naProfile,
                        @Cached() BranchProfile hasWarningsBranchProfile) {
            if (naProfile.profile(RRuntime.isNA(left) || RRuntime.isNA(right))) {
                checkFullCallNeededOnNA();
                return RRuntime.INT_NA;
            }
            WarningInfo overflowWarning = new WarningInfo();
            int result = getOperation().op(overflowWarning, left, right);
            if (overflowWarning.hasIntergerOverflow()) {
                hasWarningsBranchProfile.enter();
                RError.warning(this, Message.INTEGER_OVERFLOW);
            }
            return result;
        }

        @Specialization(insertBefore = "doFallback")
        public double doIntDouble(int left, double right,
                        @Cached("createBinaryProfile()") ConditionProfile naProfile) {
            if (naProfile.profile(RRuntime.isNA(left) || RRuntime.isNA(right))) {
                checkFullCallNeededOnNA();
                return RRuntime.DOUBLE_NA;
            }
            return getOperation().op(left, right);
        }

        @Specialization(insertBefore = "doFallback")
        public double doDoubleInt(double left, int right,
                        @Cached("createBinaryProfile()") ConditionProfile leftNanProfile,
                        @Cached("createBinaryProfile()") ConditionProfile rightNaProfile) {
            if (leftNanProfile.profile(Double.isNaN(left))) {
                checkFullCallNeededOnNA();
                return left;
            } else if (rightNaProfile.profile(RRuntime.isNA(right))) {
                checkFullCallNeededOnNA();
                return RRuntime.DOUBLE_NA;
            }
            return getOperation().op(left, right);
        }
    }
}
