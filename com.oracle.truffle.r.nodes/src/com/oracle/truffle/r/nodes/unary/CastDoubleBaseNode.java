/*
 * Copyright (c) 2013, 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.r.nodes.unary;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.profiles.ConditionProfile;
import com.oracle.truffle.r.runtime.RError.ErrorContext;
import com.oracle.truffle.r.runtime.RError;
import com.oracle.truffle.r.runtime.RRuntime;
import com.oracle.truffle.r.runtime.RType;
import com.oracle.truffle.r.runtime.data.RComplex;
import com.oracle.truffle.r.runtime.data.RComplexVector;
import com.oracle.truffle.r.runtime.data.RDataFactory;
import com.oracle.truffle.r.runtime.data.RMissing;
import com.oracle.truffle.r.runtime.data.RNull;
import com.oracle.truffle.r.runtime.data.RRaw;
import com.oracle.truffle.r.runtime.data.nodes.VectorAccess;
import com.oracle.truffle.r.runtime.ops.na.NACheck;
import com.oracle.truffle.r.runtime.ops.na.NAProfile;

public abstract class CastDoubleBaseNode extends CastBaseNode {

    protected final NACheck naCheck = NACheck.create();

    protected CastDoubleBaseNode(boolean preserveNames, boolean preserveDimensions, boolean preserveAttributes) {
        super(preserveNames, preserveDimensions, preserveAttributes);
    }

    protected CastDoubleBaseNode(boolean preserveNames, boolean preserveDimensions, boolean preserveAttributes, boolean forRFFI, boolean useClosure) {
        super(preserveNames, preserveDimensions, preserveAttributes, forRFFI, useClosure);
    }

    protected CastDoubleBaseNode(boolean preserveNames, boolean preserveDimensions, boolean preserveAttributes, boolean forRFFI, boolean useClosure, ErrorContext warningContext) {
        super(preserveNames, preserveDimensions, preserveAttributes, forRFFI, useClosure, warningContext);
    }

    @Override
    protected final RType getTargetType() {
        return RType.Double;
    }

    public abstract Object executeDouble(Object o);

    @Specialization
    protected RNull doNull(@SuppressWarnings("unused") RNull operand) {
        return RNull.instance;
    }

    @Specialization
    protected RMissing doMissing(RMissing missing) {
        return missing;
    }

    @Specialization
    protected double doInt(int operand) {
        naCheck.enable(operand);
        return naCheck.convertIntToDouble(operand);
    }

    @Specialization
    protected double doDouble(double operand) {
        return operand;
    }

    @Specialization(guards = "uAccess.supports(operand)", limit = "getVectorAccessCacheSize()")
    protected double doComplex(@SuppressWarnings("unused") RComplex operand,
                    @Cached("getVector(operand)") RComplexVector vector,
                    @Cached("vector.access()") VectorAccess uAccess) {
        VectorAccess.SequentialIterator sIter = uAccess.access(vector, warningContext());
        uAccess.next(sIter);
        return uAccess.getDouble(sIter);
    }

    @Specialization(replaces = "doComplex")
    protected double doComplexGeneric(RComplex operand) {
        RComplexVector vector = getVector(operand);
        return doComplex(operand, vector, vector.slowPathAccess());
    }

    protected RComplexVector getVector(RComplex c) {
        return RDataFactory.createComplexVectorFromScalar(c);
    }

    @Specialization
    protected double doLogical(byte operand) {
        naCheck.enable(operand);
        return naCheck.convertLogicalToDouble(operand);
    }

    @Specialization
    protected double doString(String operand,
                    @Cached("createBinaryProfile()") ConditionProfile emptyStringProfile,
                    @Cached("create()") NAProfile naProfile) {
        if (naProfile.isNA(operand) || emptyStringProfile.profile(operand.isEmpty())) {
            return RRuntime.DOUBLE_NA;
        }
        double result = RRuntime.string2doubleNoCheck(operand);
        if (RRuntime.isNA(result)) {
            warning(warningContext(), RError.Message.NA_INTRODUCED_COERCION);
        }
        return result;
    }

    @Specialization
    protected double doRaw(RRaw operand) {
        return RRuntime.raw2double(operand.getValue());
    }
}
