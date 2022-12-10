/*
 * Copyright (c) 2020, 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.r.runtime.data.nodes;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.profiles.PrimitiveValueProfile;
import com.oracle.truffle.r.runtime.data.VectorDataLibrary;
import com.oracle.truffle.r.runtime.data.VectorDataLibrary.SeqIterator;
import com.oracle.truffle.r.runtime.data.VectorDataLibrary.SeqWriteIterator;
import com.oracle.truffle.r.runtime.nodes.RBaseNode;

/**
 * Variant of {@link CopyResized}, where the caller provides library instance to access the input
 * data and the data of pre-allocated result vector.
 */
@GenerateUncached
public abstract class CopyResizedToPreallocated extends RBaseNode {
    public abstract void execute(VectorDataLibrary dataLib, Object data, Object resultData, boolean fillWithNA);

    @Specialization(guards = "!fillWithNA", limit = "getCacheSize(1)")
    public void recycle(VectorDataLibrary dataLib, Object data, Object resultData, @SuppressWarnings("unused") boolean fillWithNA,
                    @CachedLibrary("resultData") VectorDataLibrary resultDataLib) {
        SeqWriteIterator rIt = resultDataLib.writeIterator(resultData);
        boolean neverSeenNA = false;
        try {
            SeqIterator xIt = dataLib.iterator(data);
            while (resultDataLib.nextLoopCondition(resultData, rIt)) {
                dataLib.nextWithWrap(data, xIt);
                resultDataLib.transferNext(resultData, rIt, dataLib, xIt, data);
            }
            neverSeenNA = dataLib.isComplete(data) || dataLib.getNACheck(data).neverSeenNA();
        } finally {
            resultDataLib.commitWriteIterator(resultData, rIt, neverSeenNA);
        }
    }

    @Specialization(guards = "fillWithNA", limit = "getCacheSize(1)")
    public void fillWithNAValues(VectorDataLibrary dataLib, Object data, Object resultData, @SuppressWarnings("unused") boolean fillWithNA,
                    @Cached("createEqualityProfile()") PrimitiveValueProfile writtenNAProfile,
                    @CachedLibrary("resultData") VectorDataLibrary resultDataLib) {
        SeqWriteIterator rIt = resultDataLib.writeIterator(resultData);
        boolean neverSeenNA = false;
        try {
            boolean writtenNA = false;
            SeqIterator xIt = dataLib.iterator(data);
            while (dataLib.nextLoopCondition(data, xIt)) {
                resultDataLib.next(resultData, rIt);
                resultDataLib.transferNext(resultData, rIt, dataLib, xIt, data);
            }
            while (resultDataLib.nextLoopCondition(resultData, rIt)) {
                resultDataLib.setNextNA(resultData, rIt);
                writtenNA = true;
            }
            neverSeenNA = !writtenNAProfile.profile(writtenNA) && (dataLib.isComplete(data) || dataLib.getNACheck(data).neverSeenNA());
        } finally {
            resultDataLib.commitWriteIterator(resultData, rIt, neverSeenNA);
        }
    }
}
