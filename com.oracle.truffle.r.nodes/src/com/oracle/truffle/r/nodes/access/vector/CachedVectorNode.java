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

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.r.runtime.DSLConfig;
import com.oracle.truffle.r.runtime.data.VectorDataLibrary;
import com.oracle.truffle.r.runtime.data.nodes.attributes.SpecialAttributesFunctions.GetDimAttributeNode;
import com.oracle.truffle.r.runtime.RRuntime;
import com.oracle.truffle.r.runtime.RType;
import com.oracle.truffle.r.runtime.data.RBaseObject;
import com.oracle.truffle.r.runtime.data.RMissing;
import com.oracle.truffle.r.runtime.data.model.RAbstractContainer;
import com.oracle.truffle.r.runtime.data.RLogicalVector;
import com.oracle.truffle.r.runtime.nodes.RBaseNodeWithWarnings;

/**
 * Implements common logic for accessing element of a vector which is used in RW operations:
 * {@link CachedExtractVectorNode} and {@link CachedReplaceVectorNode}.
 *
 * One of the more significant parts is getting dimensions: the built-in function dim has a
 * specialization for data.frame, i.e. data.frames have different way of getting dimensions, which
 * is reflected in {@link #loadVectorDimensions(RAbstractContainer)} method.
 */
abstract class CachedVectorNode extends RBaseNodeWithWarnings {

    protected final ElementAccessMode mode;
    protected final RType vectorType;

    /**
     * Recursive indicates that the vector node is used inside {@link RecursiveReplaceSubscriptNode}
     * or {@link RecursiveExtractSubscriptNode}.
     */
    protected final boolean recursive;

    protected final int numberOfPositions;
    private final int filteredPositionsLength;

    @Child private GetDimAttributeNode getDimNode = GetDimAttributeNode.create();
    @Child private VectorDataLibrary asLogicalVectorDataLib;

    CachedVectorNode(ElementAccessMode mode, RBaseObject vector, Object[] positions, boolean recursive) {
        this.mode = mode;
        this.vectorType = vector.getRType();
        this.recursive = recursive;
        this.filteredPositionsLength = initializeFilteredPositionsCount(positions);
        if (filteredPositionsLength == -1) {
            this.numberOfPositions = positions.length;
        } else {
            this.numberOfPositions = filteredPositionsLength;
        }
    }

    private static int initializeFilteredPositionsCount(Object[] positions) {
        int dimensions = 0;
        for (int i = 0; i < positions.length; i++) {
            // for cases like RMissing the position does not contribute to the number of dimensions,
            // however, REmpty, i.e. explicitly given empty argument, contributes.
            if (!isRemovePosition(positions[i])) {
                dimensions++;
            }
        }
        if (positions.length == dimensions || dimensions <= 0) {
            return -1;
        } else {
            return dimensions;
        }
    }

    protected Object[] filterPositions(Object[] positions) {
        /*
         * we assume that the positions count cannot change as the isRemovePosition check is just
         * based on types and therefore does not change per position instance.
         *
         * normally empty positions are just empty but with S3 function dispatch it may happen that
         * positions are also of type RMissing. These values should not contribute to the access
         * dimensions.
         *
         * Example test case: dimensions.x<-data.frame(c(1,2), c(3,4)); x[1]
         */
        assert initializeFilteredPositionsCount(positions) == filteredPositionsLength;
        if (filteredPositionsLength != -1) {
            Object[] newPositions = new Object[filteredPositionsLength];
            int newPositionIndex = 0;
            for (int i = 0; i < positions.length; i++) {
                Object position = positions[i];
                if (!isRemovePosition(position)) {
                    newPositions[newPositionIndex++] = position;
                }
            }
            return newPositions;
        }
        return positions;
    }

    private static boolean isRemovePosition(Object position) {
        return position instanceof RMissing;
    }

    protected static boolean logicalAsBoolean(VectorDataLibrary dataLib, RBaseObject cast, boolean defaultValue) {
        if (cast instanceof RMissing) {
            return defaultValue;
        } else {
            RLogicalVector logical = (RLogicalVector) cast;
            Object data = logical.getData();
            if (dataLib.getLength(data) == 0) {
                return defaultValue;
            } else {
                return RRuntime.fromLogical(dataLib.getLogicalAt(data, 0));
            }
        }
    }

    protected VectorDataLibrary getAsLogicalVectorDataLib() {
        if (asLogicalVectorDataLib == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            asLogicalVectorDataLib = insert(VectorDataLibrary.getFactory().createDispatched(DSLConfig.getTypedVectorDataLibraryCacheSize()));
        }
        return asLogicalVectorDataLib;
    }

    protected final int[] loadVectorDimensions(RAbstractContainer vector) {
        // N.B. (stepan) this method used to be instance method and have special handling for
        // RDataFrame, which was removed and any test case, which would require this special
        // handling was not found (see TestBuiltin_extract_dataframe for tests used and further
        // explanation). This method and note will remain here for a while in case this behavior
        // crops up somewhere
        return getDimNode.getDimensions(vector);
    }

    public ElementAccessMode getMode() {
        return mode;
    }
}
