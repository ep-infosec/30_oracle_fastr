/*
 * Copyright (c) 2013, 2020, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.truffle.r.nodes.builtin.CastBuilder.Predef.gte0;
import static com.oracle.truffle.r.nodes.builtin.CastBuilder.Predef.lte;
import static com.oracle.truffle.r.runtime.builtins.RBehavior.PURE;
import static com.oracle.truffle.r.runtime.builtins.RBuiltinKind.INTERNAL;

import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.api.profiles.ConditionProfile;
import com.oracle.truffle.api.profiles.IntValueProfile;
import com.oracle.truffle.api.profiles.ValueProfile;
import com.oracle.truffle.r.runtime.data.nodes.attributes.SpecialAttributesFunctions.GetRowNamesAttributeNode;
import com.oracle.truffle.r.nodes.builtin.RBuiltinNode;
import com.oracle.truffle.r.runtime.data.nodes.UpdateShareableChildValueNode;
import com.oracle.truffle.r.runtime.RError;
import com.oracle.truffle.r.runtime.RRuntime;
import com.oracle.truffle.r.runtime.builtins.RBuiltin;
import com.oracle.truffle.r.runtime.data.RAttributable;
import com.oracle.truffle.r.runtime.data.RDoubleVector;
import com.oracle.truffle.r.runtime.data.RNull;
import com.oracle.truffle.r.runtime.data.model.RAbstractContainer;
import com.oracle.truffle.r.runtime.data.RIntVector;

@RBuiltin(name = "shortRowNames", kind = INTERNAL, parameterNames = {"x", "type"}, behavior = PURE)
public abstract class ShortRowNames extends RBuiltinNode.Arg2 {

    private final BranchProfile naValueMet = BranchProfile.create();
    private final ValueProfile operandTypeProfile = ValueProfile.createClassProfile();
    private final ConditionProfile nonNullValue = ConditionProfile.createBinaryProfile();

    @Child private GetRowNamesAttributeNode getRowNamesNode = GetRowNamesAttributeNode.create();
    @Child private UpdateShareableChildValueNode updateRefCount = UpdateShareableChildValueNode.create();

    static {
        Casts casts = new Casts(ShortRowNames.class);
        casts.arg("type").asIntegerVector().findFirst().mustBe(gte0().and(lte(2)));
    }

    private final IntValueProfile typeProfile = IntValueProfile.createIdentityProfile();

    @Specialization
    protected Object getNames(Object originalOperand, int originalType) {
        Object operand = operandTypeProfile.profile(originalOperand);
        Object rowNames;
        if (operand instanceof RAttributable) {
            rowNames = getRowNamesNode.execute((RAttributable) operand);
        } else {
            rowNames = null;
        }

        int type = typeProfile.profile(originalType);
        if (type >= 1) {
            int n = calculateN(rowNames);
            rowNames = type == 1 ? n : Math.abs(n);
        } else {
            if (nonNullValue.profile(rowNames != null)) {
                updateRefCount.updateState(operand, rowNames);
            }
        }

        if (rowNames == null) {
            return RNull.instance;
        }

        return rowNames;
    }

    private int calculateN(Object rowNames) {
        if (rowNames == null || rowNames == RNull.instance) {
            return 0;
        } else if (rowNames instanceof RIntVector) {
            RIntVector intVector = ((RIntVector) rowNames);
            if (intVector.getLength() == 2) {
                if (RRuntime.isNA(intVector.getDataAt(0))) {
                    naValueMet.enter();
                    return intVector.getDataAt(1);
                }
            }
            return intVector.getLength();
        } else if (rowNames instanceof RDoubleVector) {
            RDoubleVector doubleVector = ((RDoubleVector) rowNames);
            if (doubleVector.getLength() == 2) {
                if (RRuntime.isNA(doubleVector.getDataAt(0))) {
                    naValueMet.enter();
                    return (int) doubleVector.getDataAt(1);
                }
            }
            return doubleVector.getLength();
        } else if (rowNames instanceof RAbstractContainer) {
            return ((RAbstractContainer) rowNames).getLength();
        } else {
            throw error(RError.Message.INVALID_ARGUMENT, "type");
        }
    }
}
