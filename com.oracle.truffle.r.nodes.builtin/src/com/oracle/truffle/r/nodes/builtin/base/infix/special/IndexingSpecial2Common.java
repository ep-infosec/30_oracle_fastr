/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.r.nodes.builtin.base.infix.special;

import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.r.runtime.data.nodes.attributes.SpecialAttributesFunctions.GetDimAttributeNode;
import com.oracle.truffle.r.runtime.DSLConfig;
import com.oracle.truffle.r.runtime.data.model.RAbstractVector;

@ImportStatic(DSLConfig.class)
abstract class IndexingSpecial2Common extends Node {

    protected final boolean inReplacement;

    protected IndexingSpecial2Common(boolean inReplacement) {
        this.inReplacement = inReplacement;
    }

    protected boolean simpleVector(@SuppressWarnings("unused") RAbstractVector vector) {
        return true;
    }

    /**
     * Checks whether the given (1-based) index is valid for the given vector.
     */
    protected static boolean isValidIndex(RAbstractVector vector, int index) {
        return index >= 1 && index <= vector.getLength();
    }

    /**
     * Checks if the value is single element that can be put into a list or vector as is, because in
     * the case of vectors on the LSH of update we take each element and put it into the RHS of the
     * update function.
     */
    protected static boolean isSingleElement(Object value) {
        return value instanceof Integer || value instanceof Double || value instanceof Byte || value instanceof String;
    }

    @Child private GetDimAttributeNode getDimensions = GetDimAttributeNode.create();

    protected int matrixIndex(RAbstractVector vector, int index1, int index2) {
        return index1 - 1 + ((index2 - 1) * getDimensions.getDimensions(vector)[0]);
    }

    /**
     * Checks whether the given (1-based) indexes are valid for the given matrix.
     */
    protected boolean isValidIndex(RAbstractVector vector, int index1, int index2) {
        int[] dimensions = getDimensions.getDimensions(vector);
        return dimensions != null && dimensions.length == 2 && index1 >= 1 && index1 <= dimensions[0] && index2 >= 1 && index2 <= dimensions[1];
    }
}
