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
package com.oracle.truffle.r.nodes.access.vector;

import java.util.Arrays;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.api.profiles.ConditionProfile;
import com.oracle.truffle.api.profiles.LoopConditionProfile;
import com.oracle.truffle.r.nodes.access.vector.PositionsCheckNode.PositionProfile;
import com.oracle.truffle.r.runtime.DSLConfig;
import com.oracle.truffle.r.runtime.NullProfile;
import com.oracle.truffle.r.runtime.RError;
import com.oracle.truffle.r.runtime.RRuntime;
import com.oracle.truffle.r.runtime.RType;
import com.oracle.truffle.r.runtime.Utils;
import com.oracle.truffle.r.runtime.data.RDataFactory;
import com.oracle.truffle.r.runtime.data.RDoubleVector;
import com.oracle.truffle.r.runtime.data.RIntVector;
import com.oracle.truffle.r.runtime.data.RLogicalVector;
import com.oracle.truffle.r.runtime.data.RMissing;
import com.oracle.truffle.r.runtime.data.RStringVector;
import com.oracle.truffle.r.runtime.data.VectorDataLibrary;
import com.oracle.truffle.r.runtime.data.VectorDataLibrary.SeqIterator;
import com.oracle.truffle.r.runtime.data.model.RAbstractVector;
import com.oracle.truffle.r.runtime.data.nodes.attributes.SpecialAttributesFunctions.GetNamesAttributeNode;
import com.oracle.truffle.r.runtime.data.nodes.attributes.SpecialAttributesFunctions.SetNamesAttributeNode;
import com.oracle.truffle.r.runtime.ops.na.NACheck;

@ImportStatic(DSLConfig.class)
abstract class PositionCheckSubsetNode extends PositionCheckNode {

    private final NACheck positionNACheck = NACheck.create();
    @Child private VectorDataLibrary double2IntCoercedPosDataLibrary;

    PositionCheckSubsetNode(ElementAccessMode mode, RType containerType, Object positionValue, int dimensionIndex, int numDimensions, boolean exact, boolean assignment) {
        super(mode, containerType, positionValue, dimensionIndex, numDimensions, exact, assignment);
    }

    @Specialization
    protected Object doMissing(PositionProfile statistics, int dimSize, RMissing position, @SuppressWarnings("unused") int positionLength) {
        statistics.selectedPositionsCount = dimSize;
        return position;
    }

    @Specialization(limit = "getGenericVectorAccessCacheSize()", guards = {"isMultiplesOf(dimensionLength, positionLength)", "positionLength <= dimensionLength"})
    protected RAbstractVector doLogicalMultiplesInBounds(PositionProfile statistics, int dimensionLength, RLogicalVector position, int positionLength,
                    @Cached("createBinaryProfile()") ConditionProfile equalsProfile,
                    @CachedLibrary("position.getData()") VectorDataLibrary positionLibrary) {
        assert positionLength > 0;
        Object positionData = position.getData();
        positionNACheck.enable(positionLibrary, positionData);
        int elementCount = 0;
        boolean hasSeenNA = false;
        SeqIterator it = positionLibrary.iterator(positionData);
        while (positionLibrary.nextLoopCondition(positionData, it)) {
            byte positionValue = positionLibrary.getNextLogical(positionData, it);
            if (positionNACheck.check(positionValue)) {
                hasSeenNA = true;
                elementCount++;
            }
            if (positionValue == RRuntime.LOGICAL_TRUE) {
                elementCount++;
            }
        }
        statistics.containsNA = hasSeenNA;
        statistics.maxOutOfBoundsIndex = positionLength;
        statistics.selectedPositionsCount = elementCount * (equalsProfile.profile(dimensionLength == positionLength) ? 1 : dimensionLength / positionLength);
        return position;
    }

    protected static boolean isMultiplesOf(int a, int b) {
        return b != 0 && (a == b || a % b == 0);
    }

    @Specialization(replaces = "doLogicalMultiplesInBounds", limit = "getGenericVectorAccessCacheSize()")
    protected RAbstractVector doLogicalGenericInBounds(PositionProfile statistics,  //
                    int dimensionLength, RLogicalVector position, int positionLength,
                    @Cached("create()") BranchProfile outOfBoundsProfile,
                    @Cached("createCountingProfile()") LoopConditionProfile lengthProfile,
                    @Cached("createBinaryProfile()") ConditionProfile incModProfile,
                    @CachedLibrary("position.getData()") VectorDataLibrary positionLibrary) {
        Object positionData = position.getData();
        positionNACheck.enable(positionLibrary, positionData);
        int positionIndex = 0;
        int elementCount = 0;
        boolean hasSeenNA = false;
        if (positionLength > 0) {
            int length = dimensionLength;
            if (length < positionLength) {
                outOfBoundsProfile.enter();
                if (isMultiDimension()) {
                    error.enter();
                    throw error(RError.Message.LOGICAL_SUBSCRIPT_LONG);
                }
                length = positionLength;
            }
            lengthProfile.profileCounted(length);
            for (int i = 0; lengthProfile.inject(i < length); i++) {
                byte positionValue = positionLibrary.getLogicalAt(positionData, positionIndex);
                // boolean outOfBounds = outOfBoundsProfile.isVisited() && i >= dimensionLength;
                if (positionNACheck.check(positionValue)) {
                    hasSeenNA = true;
                    elementCount++;
                }
                if (positionValue == RRuntime.LOGICAL_TRUE) {
                    elementCount++;
                }
                positionIndex = Utils.incMod(positionIndex, positionLength, incModProfile);
            }
        }
        statistics.containsNA = hasSeenNA;
        statistics.maxOutOfBoundsIndex = positionLength;
        statistics.selectedPositionsCount = elementCount;
        return position;
    }

    @Specialization(/* contains = "doSequence" */ limit = "getGenericVectorAccessCacheSize()")
    protected RAbstractVector doDouble(PositionProfile profile, int dimensionLength, RDoubleVector position, int positionLength,
                    @Cached("create()") BranchProfile seenZeroProfile,
                    @Cached("create()") BranchProfile seenPositiveProfile,
                    @Cached("create()") BranchProfile seenNegativeProfile,
                    @Cached("create()") BranchProfile seenNegativeZeroProfile,
                    @Cached("create()") BranchProfile seenOutOfBounds,
                    @Cached("create()") BranchProfile seenInf,
                    @Cached("create()") NullProfile hasNamesProfile,
                    @Cached("createCountingProfile()") LoopConditionProfile lengthProfile,
                    @Cached("create()") GetNamesAttributeNode getNamesNode,
                    @Cached("create()") SetNamesAttributeNode setNamesNode,
                    @CachedLibrary("position.getData()") VectorDataLibrary positionLibrary) {
        int[] intPosition = new int[positionLength];
        positionNACheck.enable(positionLibrary, position.getData());
        boolean hasSeenPositive = false;
        boolean hasSeenNegative = false;
        boolean hasSeenNA = false;
        int outOfBoundsCount = 0;
        int zeroCount = 0;
        int maxOutOfBoundsIndex = 0;
        lengthProfile.profileCounted(positionLength);
        for (int i = 0; lengthProfile.inject(i < positionLength); i++) {
            double positionValue = positionLibrary.getDoubleAt(position.getData(), i);
            int intPositionValue;
            if (Double.isInfinite(positionValue)) {
                seenInf.enter();
                hasSeenNA = true;
                intPositionValue = RRuntime.INT_NA;
            } else {
                intPositionValue = RRuntime.double2intNoCheck(positionValue);

                if (intPositionValue > 0) {
                    seenPositiveProfile.enter();
                    hasSeenPositive = true;
                    if (intPositionValue > dimensionLength) {
                        seenOutOfBounds.enter();
                        outOfBoundsCount++;
                        maxOutOfBoundsIndex = Math.max(maxOutOfBoundsIndex, intPositionValue);
                    }
                } else if (intPositionValue == 0) {
                    seenZeroProfile.enter();
                    if (positionValue < 0) {
                        seenNegativeZeroProfile.enter();
                        /*
                         * It seems that the range ]-2:0[ is all translated to -1. So much for
                         * continuous math properties.
                         */
                        hasSeenNegative = true;
                        intPositionValue = -1;
                    } else if (positionNACheck.checkNAorNaN(positionValue)) {
                        hasSeenNA = true;
                        intPositionValue = RRuntime.INT_NA;
                    } else {
                        zeroCount++;
                    }
                } else {
                    seenNegativeProfile.enter();
                    assert positionValue < 0;
                    hasSeenNegative = true;
                    if (-positionValue > dimensionLength) {
                        seenOutOfBounds.enter();
                        outOfBoundsCount++;
                        /*
                         * We need to decrement the value to ensure that the later nodes see that
                         * the value is actually out of bounds.
                         */
                        intPositionValue--;
                    }
                }
            }
            intPosition[i] = intPositionValue;
        }

        RIntVector intPositionVec = RDataFactory.createIntVector(intPosition, !hasSeenNA);
        // requires names preservation
        RStringVector names = hasNamesProfile.profile(getNamesNode.getNames(position));
        if (names != null) {
            setNamesNode.setNames(intPositionVec, names);
        }
        if (double2IntCoercedPosDataLibrary == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            // We assume that RDataFactory.createIntVector always returns the same implementation
            double2IntCoercedPosDataLibrary = insert(VectorDataLibrary.getFactory().create(intPositionVec.getData()));
        }

        return doIntegerProfiled(profile, dimensionLength, double2IntCoercedPosDataLibrary, intPositionVec, positionLength, hasSeenPositive, hasSeenNegative, hasSeenNA, outOfBoundsCount, zeroCount,
                        maxOutOfBoundsIndex);

    }

    @Specialization(/* contains = "doSequence" */ limit = "getGenericVectorAccessCacheSize()")
    protected RAbstractVector doInteger(PositionProfile profile, int dimensionLength, RIntVector position, int positionLength,
                    @Cached("create()") BranchProfile seenZeroProfile,
                    @Cached("create()") BranchProfile seenPositiveProfile,
                    @Cached("create()") BranchProfile seenNegativeProfile,
                    @Cached("createBinaryProfile()") ConditionProfile seenNAFlagProfile,
                    @Cached("createBinaryProfile()") ConditionProfile seenPositiveFlagProfile,
                    @Cached("createBinaryProfile()") ConditionProfile seenNegativeFlagProfile,
                    @Cached("create()") BranchProfile seenOutOfBounds,
                    @CachedLibrary("position.getData()") VectorDataLibrary positionLibrary) {

        positionNACheck.enable(positionLibrary, position);
        boolean hasSeenPositive = false;
        boolean hasSeenNegative = false;
        boolean hasSeenNA = false;
        int outOfBoundsCount = 0;
        int zeroCount = 0;
        int maxOutOfBoundsIndex = 0;
        Object positionsData = position.getData();
        SeqIterator it = positionLibrary.iterator(positionsData);
        while (positionLibrary.nextLoopCondition(positionsData, it)) {
            int positionValue = positionLibrary.getNextInt(positionsData, it);
            if (positionValue > 0) {
                seenPositiveProfile.enter();
                hasSeenPositive = true;
                if (positionValue > dimensionLength) {
                    seenOutOfBounds.enter();
                    outOfBoundsCount++;
                    maxOutOfBoundsIndex = Math.max(maxOutOfBoundsIndex, positionValue);
                }
            } else if (positionValue == 0) {
                seenZeroProfile.enter();
                zeroCount++;
            } else if (positionNACheck.check(positionValue)) {
                hasSeenNA = true;
            } else {
                assert positionValue != RRuntime.INT_NA;
                seenNegativeProfile.enter();
                assert positionValue < 0;
                hasSeenNegative = true;
                if (-positionValue > dimensionLength) {
                    seenOutOfBounds.enter();
                    outOfBoundsCount++;
                }
            }
        }
        return doIntegerProfiled(profile, dimensionLength, positionLibrary, position, positionLength, seenPositiveFlagProfile.profile(hasSeenPositive),
                        seenNegativeFlagProfile.profile(hasSeenNegative),
                        seenNAFlagProfile.profile(hasSeenNA), outOfBoundsCount, zeroCount, maxOutOfBoundsIndex);
    }

    private final BranchProfile noZeroes = BranchProfile.create();

    private RAbstractVector doIntegerProfiled(PositionProfile profile, int dimensionLength, VectorDataLibrary intPositionDataLib, RIntVector intPosition, int positionLength, boolean hasSeenPositive,
                    boolean hasSeenNegative,
                    boolean hasSeenNA, int outOfBoundsCount, int zeroCount, int maxOutOfBoundsIndex) {
        if (hasSeenPositive || hasSeenNA) {
            if (numPositions > 1 && outOfBoundsCount > 0) {
                error.enter();
                throw error(RError.Message.SUBSCRIPT_BOUNDS);
            }
            if (hasSeenNegative) {
                error.enter();
                throw error(RError.Message.ONLY_0_MIXED);
            }
            profile.maxOutOfBoundsIndex = maxOutOfBoundsIndex;
            profile.selectedPositionsCount = positionLength - zeroCount;
            boolean containsNAForOutOfBounds = !replace && outOfBoundsCount > 0;
            profile.containsNA = hasSeenNA || containsNAForOutOfBounds;

            if (zeroCount == 0 && !containsNAForOutOfBounds) {
                // fast path (most common expected behavior)
                return intPosition;
            } else {
                noZeroes.enter();
                return eliminateZerosAndOutOfBounds(intPositionDataLib, intPosition, positionLength, dimensionLength, outOfBoundsCount, zeroCount, hasSeenNA);
            }
        } else if (hasSeenNegative) {
            assert !hasSeenNA;
            return transformNegative(profile, dimensionLength, intPositionDataLib, intPosition, zeroCount > 0);
        } else {
            return RDataFactory.createEmptyIntVector();
        }
    }

    private RAbstractVector eliminateZerosAndOutOfBounds(VectorDataLibrary posDataLibrary, RIntVector position, int positionLength, int dimensionLength, int outOfBoundsCount, int zeroCount,
                    boolean hasSeenNA) {
        int[] newIndices = new int[positionLength - zeroCount];
        int newPositionIndex = 0;
        Object posData = position.getData();
        SeqIterator posIt = posDataLibrary.iterator(posData);
        while (posDataLibrary.nextLoopCondition(posData, posIt)) {
            int positionValue = posDataLibrary.getNextInt(posData, posIt);
            if (zeroCount > 0 && positionValue == 0) {
                continue;
            } else if (!replace && outOfBoundsCount > 0 && positionValue > dimensionLength) {
                newIndices[newPositionIndex++] = RRuntime.INT_NA;
            } else {
                newIndices[newPositionIndex++] = positionValue;
            }
        }
        return RDataFactory.createIntVector(newIndices, !hasSeenNA && outOfBoundsCount == 0);
    }

    private static RAbstractVector transformNegative(PositionProfile statistics, int dimLength, VectorDataLibrary posDataLibrary, RIntVector position, boolean hasZeros) {
        byte[] mask = new byte[dimLength];
        Arrays.fill(mask, RRuntime.LOGICAL_TRUE);
        int allPositionsNum = dimLength;
        Object posData = position.getData();
        SeqIterator posIt = posDataLibrary.iterator(posData);
        while (posDataLibrary.nextLoopCondition(posData, posIt)) {
            int pos = -posDataLibrary.getNextInt(posData, posIt);
            if (hasZeros && pos == 0) {
                continue;
            }
            assert pos > 0;
            if (pos <= dimLength && mask[pos - 1] != RRuntime.LOGICAL_FALSE) {
                allPositionsNum--;
                mask[pos - 1] = RRuntime.LOGICAL_FALSE;
            }
        }
        statistics.selectedPositionsCount = allPositionsNum;
        return RDataFactory.createLogicalVector(mask, RDataFactory.COMPLETE_VECTOR);
    }
}
