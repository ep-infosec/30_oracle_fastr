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
 * Copyright (c) 2015, Purdue University
 * Copyright (c) 2014, 2020, Oracle and/or its affiliates
 *
 * All rights reserved.
 */

package com.oracle.truffle.r.nodes.builtin.base;

import static com.oracle.truffle.r.nodes.builtin.CastBuilder.Predef.asIntegerVector;
import static com.oracle.truffle.r.nodes.builtin.CastBuilder.Predef.asStringVector;
import static com.oracle.truffle.r.nodes.builtin.CastBuilder.Predef.doubleValue;
import static com.oracle.truffle.r.nodes.builtin.CastBuilder.Predef.integerValue;
import static com.oracle.truffle.r.nodes.builtin.CastBuilder.Predef.missingValue;
import static com.oracle.truffle.r.nodes.builtin.CastBuilder.Predef.stringValue;
import static com.oracle.truffle.r.nodes.builtin.CastBuilder.Predef.typeName;
import static com.oracle.truffle.r.runtime.builtins.RBehavior.PURE;
import static com.oracle.truffle.r.runtime.builtins.RBuiltinKind.INTERNAL;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.profiles.LoopConditionProfile;
import com.oracle.truffle.r.nodes.builtin.RBuiltinNode;
import com.oracle.truffle.r.runtime.RError;
import com.oracle.truffle.r.runtime.RInternalError;
import com.oracle.truffle.r.runtime.RRuntime;
import com.oracle.truffle.r.runtime.builtins.RBuiltin;
import com.oracle.truffle.r.runtime.data.RDataFactory;
import com.oracle.truffle.r.runtime.data.RNull;
import com.oracle.truffle.r.runtime.data.RIntVector;
import com.oracle.truffle.r.runtime.data.RStringVector;
import com.oracle.truffle.r.runtime.ops.na.NACheck;

public class BitwiseFunctions {

    protected enum Operation {
        AND("bitwAnd"),
        OR("bitwOr"),
        XOR("bitwXor"),
        NOT("bitwNot"),
        SHIFTR("bitShiftR"),
        SHIFTL("bitShiftL");

        private final String name;

        Operation(String name) {
            this.name = name;
        }
    }

    public abstract static class BasicBitwise extends RBuiltinNode.Arg2 {

        private final NACheck naCheckA = NACheck.create();
        private final NACheck naCheckB = NACheck.create();
        private final LoopConditionProfile loopProfile = LoopConditionProfile.createCountingProfile();

        protected Object basicBit(RIntVector aVec, RIntVector bVec, Operation op) {
            naCheckA.enable(aVec);
            naCheckB.enable(bVec);
            int aLen = aVec.getLength();
            int bLen = bVec.getLength();
            int ansSize = (aLen != 0 && bLen != 0) ? Math.max(aLen, bLen) : 0;
            int[] ans = new int[ansSize];
            boolean completeVector = true;
            loopProfile.profileCounted(ansSize);
            for (int i = 0; loopProfile.inject(i < ansSize); i++) {
                int aVal = aVec.getDataAt(i % aLen);
                int bVal = bVec.getDataAt(i % bLen);
                if (naCheckA.check(aVal) || naCheckB.check(bVal)) {
                    ans[i] = RRuntime.INT_NA;
                    completeVector = false;
                } else {
                    int v;
                    switch (op) {
                        case AND:
                            v = aVal & bVal;
                            break;
                        case OR:
                            v = aVal | bVal;
                            break;
                        case XOR:
                            v = aVal ^ bVal;
                            break;
                        case SHIFTR:
                            if (bVal > 31 || bVal < 0) {
                                v = RRuntime.INT_NA;
                            } else {
                                v = aVal >>> bVal;
                            }
                            break;
                        case SHIFTL:
                            if (bVal > 31 || bVal < 0) {
                                v = RRuntime.INT_NA;
                            } else {
                                v = aVal << bVal;
                            }
                            break;
                        default:
                            throw RInternalError.shouldNotReachHere();
                    }
                    ans[i] = v;
                    if (v == RRuntime.INT_NA) {
                        completeVector = false;
                    }
                }
            }

            return RDataFactory.createIntVector(ans, completeVector);
        }

        protected Object makeNA(int length) {
            int[] na = new int[length];
            for (int i = 0; i < length; i++) {
                na[i] = RRuntime.INT_NA;
            }
            return RDataFactory.createIntVector(na, RDataFactory.INCOMPLETE_VECTOR);
        }
    }

    @RBuiltin(name = "bitwiseAnd", kind = INTERNAL, parameterNames = {"a", "b"}, behavior = PURE)
    public abstract static class BitwiseAnd extends BasicBitwise {

        static {
            Casts casts = new Casts(BitwiseAnd.class);
            casts.arg("a").defaultError(RError.Message.UNIMPLEMENTED_TYPE_IN_FUNCTION, typeName(), Operation.AND.name).mustBe(doubleValue().or(integerValue())).asIntegerVector();
            casts.arg("b").defaultError(RError.Message.UNIMPLEMENTED_TYPE_IN_FUNCTION, typeName(), Operation.AND.name).mustBe(doubleValue().or(integerValue())).asIntegerVector();
        }

        @Specialization
        protected Object bitwAnd(RIntVector a, RIntVector b) {
            return basicBit(a, b, Operation.AND);
        }

        @Fallback
        @SuppressWarnings("unused")
        protected Object differentTypes(Object a, Object b) {
            throw error(RError.Message.SAME_TYPE, "a", "b");
        }
    }

    @RBuiltin(name = "bitwiseOr", kind = INTERNAL, parameterNames = {"a", "b"}, behavior = PURE)
    public abstract static class BitwiseOr extends BasicBitwise {

        static {
            Casts casts = new Casts(BitwiseOr.class);
            casts.arg("a").defaultError(RError.Message.UNIMPLEMENTED_TYPE_IN_FUNCTION, typeName(), Operation.OR.name).mustBe(doubleValue().or(integerValue())).asIntegerVector();
            casts.arg("b").defaultError(RError.Message.UNIMPLEMENTED_TYPE_IN_FUNCTION, typeName(), Operation.OR.name).mustBe(doubleValue().or(integerValue())).asIntegerVector();
        }

        @Specialization
        protected Object bitwOr(RIntVector a, RIntVector b) {
            return basicBit(a, b, Operation.OR);
        }

        @Fallback
        @SuppressWarnings("unused")
        protected Object differentTypes(Object a, Object b) {
            throw error(RError.Message.SAME_TYPE, "a", "b");
        }
    }

    @RBuiltin(name = "bitwiseXor", kind = INTERNAL, parameterNames = {"a", "b"}, behavior = PURE)
    public abstract static class BitwiseXor extends BasicBitwise {

        static {
            Casts casts = new Casts(BitwiseXor.class);
            casts.arg("a").defaultError(RError.Message.UNIMPLEMENTED_TYPE_IN_FUNCTION, typeName(), Operation.XOR.name).mustBe(doubleValue().or(integerValue())).asIntegerVector();
            casts.arg("b").defaultError(RError.Message.UNIMPLEMENTED_TYPE_IN_FUNCTION, typeName(), Operation.XOR.name).mustBe(doubleValue().or(integerValue())).asIntegerVector();
        }

        @Specialization
        protected Object bitwXor(RIntVector a, RIntVector b) {
            return basicBit(a, b, Operation.XOR);
        }

        @Fallback
        @SuppressWarnings("unused")
        protected Object differentTypes(Object a, Object b) {
            throw error(RError.Message.SAME_TYPE, "a", "b");
        }
    }

    @RBuiltin(name = "bitwiseShiftR", kind = INTERNAL, parameterNames = {"a", "n"}, behavior = PURE)
    public abstract static class BitwiseShiftR extends BasicBitwise {

        static {
            Casts casts = new Casts(BitwiseShiftR.class);
            casts.arg("a").defaultError(RError.Message.UNIMPLEMENTED_TYPE_IN_FUNCTION, typeName(), Operation.SHIFTR.name).mustBe(missingValue().not()).mustBe(
                            doubleValue().or(integerValue())).asIntegerVector();
            casts.arg("n").allowNull().mustBe(missingValue().not()).mapIf(stringValue(), asStringVector(), asIntegerVector());
        }

        @Specialization
        protected Object bitwShiftR(RIntVector a, RIntVector n) {
            return basicBit(a, n, Operation.SHIFTR);
        }

        @Specialization
        @SuppressWarnings("unused")
        protected Object bitwShiftR(RIntVector a, RNull n) {
            return RDataFactory.createEmptyIntVector();
        }

        @Specialization
        @SuppressWarnings("unused")
        protected Object bitwShiftRChar(RIntVector a, RStringVector n) {
            return makeNA(a.getLength());
        }
    }

    @RBuiltin(name = "bitwiseShiftL", kind = INTERNAL, parameterNames = {"a", "n"}, behavior = PURE)
    public abstract static class BitwiseShiftL extends BasicBitwise {

        static {
            Casts casts = new Casts(BitwiseShiftL.class);
            // 'b' reported instead of 'n' in GNU R
            casts.arg("a").defaultError(RError.Message.SAME_TYPE, "a", "b").mustBe(
                            doubleValue().or(integerValue())).asIntegerVector();
            casts.arg("n").allowNull().mustNotBeMissing().asIntegerVector();
        }

        @Specialization
        protected Object bitwShiftL(RIntVector a, RIntVector n) {
            return basicBit(a, n, Operation.SHIFTL);
        }

        @Specialization
        @SuppressWarnings("unused")
        protected Object bitwShiftL(RIntVector a, RNull n) {
            return RDataFactory.createEmptyIntVector();
        }
    }

    @RBuiltin(name = "bitwiseNot", kind = INTERNAL, parameterNames = {"a"}, behavior = PURE)
    public abstract static class BitwiseNot extends RBuiltinNode.Arg1 {

        static {
            Casts casts = new Casts(BitwiseNot.class);
            casts.arg("a").defaultError(RError.Message.UNIMPLEMENTED_TYPE_IN_FUNCTION, typeName(), Operation.NOT.name).mustBe(doubleValue().or(integerValue())).asIntegerVector();
        }

        @Specialization
        protected Object bitwNot(RIntVector a,
                        @Cached("create()") NACheck naCheck) {
            int[] ans = new int[a.getLength()];
            naCheck.enable(a);
            for (int i = 0; i < a.getLength(); i++) {
                int val = a.getDataAt(i);
                ans[i] = naCheck.check(val) ? RRuntime.INT_NA : ~val;
            }
            return RDataFactory.createIntVector(ans, naCheck.neverSeenNA());
        }
    }
}
