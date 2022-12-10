/*
 * Copyright (c) 2015, 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.r.nodes.access.vector;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.profiles.ConditionProfile;
import com.oracle.truffle.r.nodes.access.vector.PositionsCheckNode.PositionProfile;
import com.oracle.truffle.r.runtime.DSLConfig;
import com.oracle.truffle.r.runtime.RError;
import com.oracle.truffle.r.runtime.RError.Message;
import com.oracle.truffle.r.runtime.RRuntime;
import com.oracle.truffle.r.runtime.RType;
import com.oracle.truffle.r.runtime.data.RDataFactory;
import com.oracle.truffle.r.runtime.data.RDataFactory.VectorFactory;
import com.oracle.truffle.r.runtime.data.RIntVector;
import com.oracle.truffle.r.runtime.data.RLogicalVector;
import com.oracle.truffle.r.runtime.data.RMissing;
import com.oracle.truffle.r.runtime.data.RStringVector;
import com.oracle.truffle.r.runtime.data.VectorDataLibrary;
import com.oracle.truffle.r.runtime.data.model.RAbstractVector;
import com.oracle.truffle.r.runtime.data.nodes.attributes.SpecialAttributesFunctions.ExtractNamesAttributeNode;
import com.oracle.truffle.r.runtime.ops.na.NACheck;

@ImportStatic(DSLConfig.class)
abstract class PositionCheckSubscriptNode extends PositionCheckNode {

    private final NACheck positionNACheck = NACheck.create();
    private final ConditionProfile greaterZero = ConditionProfile.createBinaryProfile();
    @Child VectorFactory vectorFactory;

    private final boolean recursive;

    PositionCheckSubscriptNode(ElementAccessMode mode, RType containerType, Object positionValue, int dimensionIndex, int numPositions, boolean exact, boolean assignment, boolean recursive) {
        super(mode, containerType, positionValue, dimensionIndex, numPositions, exact, assignment);
        this.recursive = recursive;
    }

    @Specialization
    protected Object doMissing(PositionProfile statistics, int dimSize, RMissing position, @SuppressWarnings("unused") int positionLength) {
        statistics.selectedPositionsCount = dimSize;
        return position;
    }

    @Specialization(limit = "getGenericVectorAccessCacheSize()")
    protected RAbstractVector doLogical(PositionProfile statistics, int dimSize, RLogicalVector position, int positionLength,
                    @Cached("create()") ExtractNamesAttributeNode extractNamesNode,
                    @CachedLibrary("position.getData()") VectorDataLibrary positionLibrary) {
        positionNACheck.enable(positionLibrary, position.getData());
        byte value = position.getDataAt(0);
        if (positionLength != 1) {
            error.enter();
            if (positionLength >= 3) {
                throw error(RError.Message.SELECT_MORE_1);
            } else {
                if (value == RRuntime.LOGICAL_TRUE) {
                    throw error(RError.Message.SELECT_MORE_1);
                } else {
                    throw error(RError.Message.SELECT_LESS_1);
                }
            }
        }

        return doIntegerImpl(statistics, dimSize, positionNACheck.convertLogicalToInt(value), position, extractNamesNode);
    }

    @Specialization(limit = "getGenericVectorAccessCacheSize()")
    protected RAbstractVector doInteger(PositionProfile profile, int dimSize, RIntVector position, int positionLength,
                    @Cached("create()") ExtractNamesAttributeNode extractNamesNode,
                    @CachedLibrary("position.getData()") VectorDataLibrary positionLibrary) {
        if (positionLength != 1) {
            error.enter();
            Message message;
            if (positionLength > 1) {
                /* This is a very specific check. But it just did not fit into other checks. */
                if (positionLength == 2 && positionLibrary.getIntAt(position.getData(), 0) == 0) {
                    message = RError.Message.SELECT_LESS_1;
                } else {
                    message = RError.Message.SELECT_MORE_1;
                }
            } else {
                message = RError.Message.SELECT_LESS_1;
            }
            throw error(message);
        }
        assert positionLength == 1;
        positionNACheck.enable(positionLibrary, position);
        return doIntegerImpl(profile, dimSize, positionLibrary.getIntAt(position.getData(), 0), position, extractNamesNode);
    }

    private RAbstractVector doIntegerImpl(PositionProfile profile, int dimSize, int value, RAbstractVector originalVector, ExtractNamesAttributeNode extractNamesNode) {
        int result;
        if (greaterZero.profile(value > 0)) {
            // fast path
            assert RRuntime.INT_NA <= 0;
            result = value;
            if (!replace && result > dimSize) {
                error.enter();
                throwBoundsError();
            }
            profile.maxOutOfBoundsIndex = result;
        } else {
            // slow path
            result = doIntegerSlowPath(profile, dimSize, value);
        }
        profile.selectedPositionsCount = 1;

        RStringVector names = extractNamesNode.execute(originalVector);
        if (names != null) {
            return getVectorFactory().createIntVector(new int[]{result}, !profile.containsNA, names);
        } else {
            return RDataFactory.createIntVectorFromScalar(result);
        }
    }

    private int doIntegerSlowPath(PositionProfile profile, int dimSize, int value) {
        assert value <= 0;
        positionNACheck.enable(value);
        if (positionNACheck.check(value)) {
            handleNA(dimSize);
            profile.containsNA = true;
            return value;
        } else {
            if (dimSize == 2) {
                if (value == -2) {
                    return 1;
                } else if (value == -1) {
                    return 2;
                }
            }
            error.enter();
            if (value == 0) {
                throw error(Message.SELECT_LESS_1);
            } else {
                throw error(Message.INVALID_NEGATIVE_SUBSCRIPT);
            }
        }
    }

    private void handleNA(int dimSize) {
        if (replace) {
            error.enter();
            Message message;
            if (isMultiDimension()) {
                message = RError.Message.SUBSCRIPT_BOUNDS_SUB;
            } else {
                if (dimSize < 2) {
                    message = RError.Message.SELECT_LESS_1_IN_ONE_INDEX;
                } else {
                    message = RError.Message.SELECT_MORE_1_IN_ONE_INDEX;
                }
            }
            throw error(message);
        } else {
            if (numPositions == 1 && isListLike(containerType) && !recursive) {
                // lists pass on the NA value
            } else {
                error.enter();
                throwBoundsError();
            }
        }
    }

    public VectorFactory getVectorFactory() {
        if (vectorFactory == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            vectorFactory = insert(VectorFactory.create());
        }
        return vectorFactory;
    }

    private void throwBoundsError() {
        if (recursive) {
            throw new RecursiveIndexNotFoundError();
        } else {
            throw error(RError.Message.SUBSCRIPT_BOUNDS);
        }
    }
}
