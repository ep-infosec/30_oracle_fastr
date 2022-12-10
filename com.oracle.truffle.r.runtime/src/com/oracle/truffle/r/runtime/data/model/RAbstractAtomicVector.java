/*
 * Copyright (c) 2017, 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.r.runtime.data.model;

import com.oracle.truffle.r.runtime.data.RComplexVector;
import com.oracle.truffle.r.runtime.data.RDoubleVector;
import com.oracle.truffle.r.runtime.data.RIntVector;
import com.oracle.truffle.r.runtime.data.RLogicalVector;
import com.oracle.truffle.r.runtime.data.RRawVector;
import com.oracle.truffle.r.runtime.data.RStringVector;
import com.oracle.truffle.r.runtime.data.VectorDataLibrary;

/**
 * Distinguishes what R considers an "atomic" vector, e.g. {@code integer()} from other "vectors",
 * e.g., {@code list()}. Specifically these are the FastR atomic vector types:
 * <ul>
 * <li>{@link RIntVector}</li>
 * <li>{@link RLogicalVector}</li>
 * <li>{@link RDoubleVector}</li>
 * <li>{@link RComplexVector}</li>
 * <li>{@link RStringVector}</li>
 * <li>{@link RRawVector}</li>
 * </ul>
 */
public abstract class RAbstractAtomicVector extends RAbstractVector {

    protected final boolean isScalar(VectorDataLibrary dataLib) {
        return dataLib.getLength(getData()) == 1;
    }

    @Override
    protected final boolean boxReadElements() {
        return false;
    }

}
