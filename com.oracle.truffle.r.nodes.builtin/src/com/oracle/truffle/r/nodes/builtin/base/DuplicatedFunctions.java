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
 * Copyright (c) 2014, 2020, Oracle and/or its affiliates
 *
 * All rights reserved.
 */

package com.oracle.truffle.r.nodes.builtin.base;

import static com.oracle.truffle.r.nodes.builtin.CastBuilder.Predef.abstractVectorValue;
import static com.oracle.truffle.r.nodes.builtin.CastBuilder.Predef.emptyList;
import static com.oracle.truffle.r.runtime.builtins.RBehavior.PURE;
import static com.oracle.truffle.r.runtime.builtins.RBuiltinKind.INTERNAL;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.profiles.ConditionProfile;
import com.oracle.truffle.r.nodes.binary.CastTypeNode;
import com.oracle.truffle.r.nodes.binary.CastTypeNodeGen;
import com.oracle.truffle.r.nodes.builtin.RBuiltinNode;
import com.oracle.truffle.r.nodes.unary.TypeofNode;
import com.oracle.truffle.r.runtime.RError;
import com.oracle.truffle.r.runtime.RError.Message;
import com.oracle.truffle.r.runtime.RRuntime;
import com.oracle.truffle.r.runtime.RType;
import com.oracle.truffle.r.runtime.builtins.RBuiltin;
import com.oracle.truffle.r.runtime.data.RDataFactory;
import com.oracle.truffle.r.runtime.data.RLogicalVector;
import com.oracle.truffle.r.runtime.data.model.RAbstractVector;
import com.oracle.truffle.r.runtime.nodes.DuplicationHelper;

public class DuplicatedFunctions {

    @RBuiltin(name = "duplicated", kind = INTERNAL, parameterNames = {"x", "incomparables", "fromLast", "nmax"}, behavior = PURE)
    public abstract static class Duplicated extends RBuiltinNode.Arg4 {

        protected static void casts(Casts casts) {
            // these are similar to those in DuplicatedFunctions.java
            casts.arg("x").mapNull(emptyList()).mustBe(abstractVectorValue(), RError.Message.APPLIES_TO_VECTORS, "duplicated()").asVector();
            // not much more can be done for incomparables as it is either a vector of incomparable
            // values or a (single) logical value
            casts.arg("incomparables").asVector(true);
            casts.arg("fromLast").asLogicalVector().findFirst(RRuntime.LOGICAL_FALSE);
        }

        static {
            Casts casts = new Casts(Duplicated.class);
            casts(casts);
            // currently not supported and not tested, but NA is a correct value (the same for empty
            // vectors) whereas 0 is not (throws an error)
            casts.arg("nmax").asIntegerVector().findFirst(RRuntime.INT_NA);
        }

        private final ConditionProfile incomparable = ConditionProfile.createBinaryProfile();

        protected boolean isIncomparable(RAbstractVector incomparables) {
            if (incomparable.profile(incomparables.getLength() == 1 && incomparables instanceof RLogicalVector && ((RLogicalVector) incomparables).getDataAt(0) == RRuntime.LOGICAL_FALSE)) {
                return false;
            } else {
                return true;
            }
        }

        @TruffleBoundary
        protected static RLogicalVector analyzeAndCreateResult(RAbstractVector x, RAbstractVector incomparables, byte fromLast) {
            DuplicationHelper ds = DuplicationHelper.analyze(x, incomparables, false, RRuntime.fromLogical(fromLast));
            return RDataFactory.createLogicalVector(ds.getDupVec(), RDataFactory.COMPLETE_VECTOR);
        }

        @Specialization(guards = {"!isIncomparable(incomparables)", "x.getLength() != 0"})
        protected RLogicalVector duplicatedFalseIncomparables(RAbstractVector x, @SuppressWarnings("unused") RAbstractVector incomparables, byte fromLast, @SuppressWarnings("unused") int nmax) {
            return analyzeAndCreateResult(x, null, fromLast);
        }

        protected static TypeofNode createTypeof() {
            return TypeofNode.create();
        }

        protected static CastTypeNode createCastType() {
            return CastTypeNodeGen.create();
        }

        @Specialization(guards = {"isIncomparable(incomparables)", "x.getLength() != 0"})
        protected RLogicalVector duplicatedTrueIncomparables(RAbstractVector x, RAbstractVector incomparables, byte fromLast, @SuppressWarnings("unused") int nmax,
                        @Cached("createTypeof()") TypeofNode typeof,
                        @Cached("createCastType()") CastTypeNode castTypeNode) {
            RType xType = typeof.execute(x);
            RAbstractVector vector = (RAbstractVector) (castTypeNode.execute(incomparables, xType));
            return analyzeAndCreateResult(x, vector, fromLast);
        }

        @SuppressWarnings("unused")
        @Specialization(guards = {"!isRAbstractVector(incomparables)", "x.getLength() != 0"})
        @TruffleBoundary
        protected RLogicalVector duplicatedTrueIncomparables(RAbstractVector x, Object incomparables, byte fromLast, int nmax,
                        @Cached("createTypeof()") TypeofNode typeof) {
            throw error(Message.CANNOT_COERCE, RRuntime.getRTypeName(incomparables), RRuntime.getRTypeName(x));
        }

        @SuppressWarnings("unused")
        @Specialization(guards = "x.getLength() == 0")
        protected RLogicalVector duplicatedEmpty(RAbstractVector x, Object incomparables, byte fromLast, int nmax) {
            return RDataFactory.createEmptyLogicalVector();
        }
    }

    @RBuiltin(name = "anyDuplicated", kind = INTERNAL, parameterNames = {"x", "incomparables", "fromLast"}, behavior = PURE)
    public abstract static class AnyDuplicated extends RBuiltinNode.Arg3 {

        static {
            Casts casts = new Casts(AnyDuplicated.class);
            Duplicated.casts(casts);
        }

        private final ConditionProfile incomparable = ConditionProfile.createBinaryProfile();

        protected boolean isIncomparable(RAbstractVector incomparables) {
            if (incomparable.profile(incomparables.getLength() == 1 && incomparables instanceof RLogicalVector && ((RLogicalVector) incomparables).getDataAt(0) == RRuntime.LOGICAL_FALSE)) {
                return false;
            } else {
                return true;
            }
        }

        @SuppressWarnings("unused")
        @Specialization(guards = {"!isIncomparable(incomparables)", "x.getLength() != 0"})
        protected int anyDuplicatedFalseIncomparables(RAbstractVector x, RAbstractVector incomparables, byte fromLast) {
            return DuplicationHelper.analyze(x, null, true, RRuntime.fromLogical(fromLast)).getIndex();
        }

        protected static TypeofNode createTypeof() {
            return TypeofNode.create();
        }

        protected static CastTypeNode createCastType() {
            return CastTypeNodeGen.create();
        }

        @Specialization(guards = {"isIncomparable(incomparables)", "x.getLength() != 0"})
        protected int anyDuplicatedTrueIncomparables(RAbstractVector x, RAbstractVector incomparables, byte fromLast,
                        @Cached("createTypeof()") TypeofNode typeof,
                        @Cached("createCastType()") CastTypeNode castTypeNode) {
            RType xType = typeof.execute(x);
            return DuplicationHelper.analyze(x, (RAbstractVector) (castTypeNode.execute(incomparables, xType)), true, RRuntime.fromLogical(fromLast)).getIndex();
        }

        @Specialization(guards = {"!isRAbstractVector(incomparables)", "x.getLength() != 0"})
        @TruffleBoundary
        protected int anyDuplicatedTrueIncomparables(RAbstractVector x, Object incomparables, @SuppressWarnings("unused") byte fromLast) {
            // this is not quite correct, as passing expression generates some obscure error message
            throw error(Message.CANNOT_COERCE, RRuntime.getRTypeName(incomparables), RRuntime.getRTypeName(x));
        }

        @SuppressWarnings("unused")
        @Specialization(guards = "x.getLength() == 0")
        protected int anyDuplicatedEmpty(RAbstractVector x, Object incomparables, byte fromLast) {
            return 0;
        }
    }
}
