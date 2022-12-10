/*
 * Copyright (c) 2013, 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.r.nodes.builtin.base;

import static com.oracle.truffle.r.nodes.builtin.CastBuilder.Predef.toBoolean;
import static com.oracle.truffle.r.runtime.builtins.RBehavior.PURE;
import static com.oracle.truffle.r.runtime.builtins.RBuiltinKind.INTERNAL;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.profiles.ConditionProfile;
import com.oracle.truffle.r.nodes.binary.BinaryMapBooleanFunctionNode;
import com.oracle.truffle.r.nodes.builtin.RBuiltinNode;
import com.oracle.truffle.r.nodes.builtin.base.Order.CmpNode;
import com.oracle.truffle.r.nodes.builtin.base.OrderNodeGen.CmpNodeGen;
import com.oracle.truffle.r.runtime.RRuntime;
import com.oracle.truffle.r.runtime.RType;
import com.oracle.truffle.r.runtime.builtins.RBuiltin;
import com.oracle.truffle.r.runtime.data.RDoubleVector;
import com.oracle.truffle.r.runtime.data.RIntVector;
import com.oracle.truffle.r.runtime.data.RRawVector;
import com.oracle.truffle.r.runtime.data.RComplexVector;
import com.oracle.truffle.r.runtime.data.RStringVector;
import com.oracle.truffle.r.runtime.data.VectorDataLibrary;
import com.oracle.truffle.r.runtime.ops.BinaryCompare;

// TODO support strictly
// TODO support lists
@RBuiltin(name = "is.unsorted", kind = INTERNAL, parameterNames = {"x", "strictly"}, behavior = PURE)
public abstract class IsUnsorted extends RBuiltinNode.Arg2 {

    @Child private BinaryMapBooleanFunctionNode ge = new BinaryMapBooleanFunctionNode(BinaryCompare.GREATER_EQUAL.createOperation());
    @Child private BinaryMapBooleanFunctionNode gt = new BinaryMapBooleanFunctionNode(BinaryCompare.GREATER_THAN.createOperation());

    private final ConditionProfile strictlyProfile = ConditionProfile.createBinaryProfile();

    static {
        Casts casts = new Casts(IsUnsorted.class);
        casts.arg("strictly").asLogicalVector().findFirst().mustNotBeNA().map(toBoolean());
    }

    @Specialization
    protected byte isUnsorted(RDoubleVector x, boolean strictly) {
        double last = x.getDataAt(0);
        for (int k = 1; k < x.getLength(); k++) {
            double current = x.getDataAt(k);
            if (strictlyProfile.profile(strictly)) {
                if (ge.applyLogical(last, current) == RRuntime.LOGICAL_TRUE) {
                    return RRuntime.LOGICAL_TRUE;
                }
            } else {
                if (gt.applyLogical(last, current) == RRuntime.LOGICAL_TRUE) {
                    return RRuntime.LOGICAL_TRUE;
                }
            }
            last = current;
        }
        return RRuntime.LOGICAL_FALSE;
    }

    @Specialization
    protected byte isUnsorted(RIntVector x, boolean strictly) {
        int last = x.getDataAt(0);
        for (int k = 1; k < x.getLength(); k++) {
            int current = x.getDataAt(k);
            if (strictlyProfile.profile(strictly)) {
                if (ge.applyLogical(last, current) == RRuntime.LOGICAL_TRUE) {
                    return RRuntime.LOGICAL_TRUE;
                }
            } else {
                if (gt.applyLogical(last, current) == RRuntime.LOGICAL_TRUE) {
                    return RRuntime.LOGICAL_TRUE;
                }
            }
            last = current;
        }
        return RRuntime.LOGICAL_FALSE;
    }

    @Specialization
    protected byte isUnsorted(RStringVector x, boolean strictly) {
        String last = x.getDataAt(0);
        for (int k = 1; k < x.getLength(); k++) {
            String current = x.getDataAt(k);
            if (strictlyProfile.profile(strictly)) {
                if (ge.applyLogical(last, current) == RRuntime.LOGICAL_TRUE) {
                    return RRuntime.LOGICAL_TRUE;
                }
            } else {
                if (gt.applyLogical(last, current) == RRuntime.LOGICAL_TRUE) {
                    return RRuntime.LOGICAL_TRUE;
                }
            }
            last = current;
        }
        return RRuntime.LOGICAL_FALSE;
    }

    protected CmpNode createCmpNode() {
        return CmpNodeGen.create();
    }

    @Specialization
    protected byte isUnsorted(RRawVector x, boolean strictly) {
        byte last = x.getRawDataAt(0);
        for (int k = 1; k < x.getLength(); k++) {
            byte current = x.getRawDataAt(k);
            if (strictlyProfile.profile(strictly)) {
                if (ge.applyRaw(last, current) == RRuntime.LOGICAL_TRUE) {
                    return RRuntime.LOGICAL_TRUE;
                }
            } else {
                if (gt.applyRaw(last, current) == RRuntime.LOGICAL_TRUE) {
                    return RRuntime.LOGICAL_TRUE;
                }
            }
            last = current;
        }
        return RRuntime.LOGICAL_FALSE;
    }

    @Specialization(limit = "getVectorAccessCacheSize()")
    protected byte isUnsorted(RComplexVector x, boolean strictly,
                    @Bind("x.getData()") Object xData,
                    @CachedLibrary("xData") VectorDataLibrary dataLib,
                    @Cached("createCmpNode()") CmpNode cmpNode) {
        int last = 0;
        for (int k = 1; k < x.getLength(); k++) {
            if (strictlyProfile.profile(strictly)) {
                if (cmpNode.ccmp(xData, RType.Complex, last, k, true, dataLib) >= 0) {
                    return RRuntime.LOGICAL_TRUE;
                }
            } else {
                if (cmpNode.ccmp(xData, RType.Complex, last, k, true, dataLib) > 0) {
                    return RRuntime.LOGICAL_TRUE;
                }
            }
            last = k;
        }
        return RRuntime.LOGICAL_FALSE;
    }

    @Fallback
    @SuppressWarnings("unused")
    protected byte isUnsortedFallback(Object x, Object strictly) {
        return RRuntime.LOGICAL_NA;
    }
}
