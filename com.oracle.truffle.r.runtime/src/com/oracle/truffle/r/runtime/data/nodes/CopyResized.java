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

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.r.runtime.data.AbstractContainerLibrary;
import com.oracle.truffle.r.runtime.data.VectorDataLibrary;
import com.oracle.truffle.r.runtime.data.model.RAbstractContainer;
import com.oracle.truffle.r.runtime.data.model.RAbstractVector;
import com.oracle.truffle.r.runtime.nodes.RBaseNode;

/**
 * Resizes a container to new length filling the extra space with either the original data recycled
 * or with {@code NA} values depending on the parameter {@code fillWithNA}. The original data must
 * contain at least one element if {@code fillWithNA} is {@code false}.
 *
 * Note: for raw vectors, the {@code fillWithNA} implies filling with {@code 0x00}.
 */
@GenerateUncached
public abstract class CopyResized extends RBaseNode {

    public abstract RAbstractVector execute(RAbstractContainer container, int newSize, boolean fillWithNA);

    public static CopyResized create() {
        return CopyResizedNodeGen.create();
    }

    public static CopyResized getUncached() {
        return CopyResizedNodeGen.getUncached();
    }

    @Specialization(guards = "fillWithNA == fillWithNAIn", limit = "getGenericVectorAccessCacheSize()")
    RAbstractVector doIt(RAbstractVector x, int newSize, boolean fillWithNAIn,
                    @Cached("fillWithNAIn") boolean fillWithNA,
                    @CachedLibrary("x") AbstractContainerLibrary xLib,
                    @CachedLibrary("x.getData()") VectorDataLibrary xDataLib,
                    @Cached CopyResizedToPreallocated copyResizedToPreallocated) {
        Object xData = x.getData();
        // empty input is allowed only if fillWithNA is true
        assert fillWithNAIn || xDataLib.getLength(xData) > 0;
        RAbstractVector result = xLib.createEmptySameType(x, newSize, false);
        copyResizedToPreallocated.execute(xDataLib, xData, result.getData(), fillWithNA);
        return result;
    }

    @TruffleBoundary
    @Specialization(replaces = "doIt")
    RAbstractVector doUncached(RAbstractVector x, int newSize, boolean fillWithNA,
                    @Cached CopyResizedToPreallocated copyResizedToPreallocated) {
        AbstractContainerLibrary continerLib = AbstractContainerLibrary.getFactory().getUncached();
        VectorDataLibrary dataLib = VectorDataLibrary.getFactory().getUncached();
        return doIt(x, newSize, fillWithNA, fillWithNA, continerLib, dataLib, copyResizedToPreallocated);
    }
}
