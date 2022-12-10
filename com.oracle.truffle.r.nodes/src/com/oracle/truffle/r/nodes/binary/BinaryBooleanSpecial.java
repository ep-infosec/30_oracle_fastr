/*
 * Copyright (c) 2016, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.r.nodes.binary;

import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.r.runtime.ArgumentsSignature;
import com.oracle.truffle.r.runtime.RRuntime;
import com.oracle.truffle.r.runtime.builtins.RSpecialFactory;
import com.oracle.truffle.r.runtime.nodes.RNode;
import com.oracle.truffle.r.runtime.ops.BooleanOperation;
import com.oracle.truffle.r.runtime.ops.BooleanOperationFactory;

import static com.oracle.truffle.r.nodes.helpers.SpecialsUtils.unboxValue;

/**
 * Fast-path for scalar values: these cannot have any class attribute. Note: we intentionally use
 * empty type system to avoid conversions to vector types. NA values cause
 * {@link RSpecialFactory#throwFullCallNeeded()} exception.
 */
@NodeChild(value = "arguments", type = RNode[].class)
public abstract class BinaryBooleanSpecial extends RNode {
    @Child private BooleanOperation operation;

    private final BranchProfile naProfile = BranchProfile.create();

    protected BinaryBooleanSpecial(BooleanOperation operation) {
        this.operation = operation;
    }

    public static RSpecialFactory createSpecialFactory(final BooleanOperationFactory opFactory) {
        return new RSpecialFactory() {
            @Override
            public RNode create(ArgumentsSignature signature, RNode[] arguments, boolean inReplacement) {
                if (signature.getNonNullCount() != 0 || arguments.length != 2) {
                    return null;
                }
                RNode[] newArguments = new RNode[]{unboxValue(arguments[0]), unboxValue(arguments[1])};
                return BinaryBooleanSpecialNodeGen.create(opFactory.createOperation(), newArguments);
            }
        };
    }

    @Specialization
    public byte doInts(int left, int right) {
        if (RRuntime.isNA(left) || RRuntime.isNA(right)) {
            naProfile.enter();
            return RRuntime.LOGICAL_NA;
        }
        return RRuntime.asLogical(operation.op(left, right));
    }

    @Specialization
    public byte doDoubles(double left, double right) {
        if (Double.isNaN(left) || Double.isNaN(right)) {
            naProfile.enter();
            return RRuntime.LOGICAL_NA;
        }
        return RRuntime.asLogical(operation.op(left, right));
    }

    @Specialization
    public byte doIntDouble(int left, double right) {
        if (RRuntime.isNA(left) || Double.isNaN(right)) {
            naProfile.enter();
            return RRuntime.LOGICAL_NA;
        }
        return RRuntime.asLogical(operation.op(left, right));
    }

    @Specialization
    public byte doDoubleInt(double left, int right) {
        if (Double.isNaN(left) || RRuntime.isNA(right)) {
            naProfile.enter();
            return RRuntime.LOGICAL_NA;
        }
        return RRuntime.asLogical(operation.op(left, right));
    }

    @Specialization
    public byte doLogical(byte left, byte right) {
        if (RRuntime.isNA(left) || RRuntime.isNA(right)) {
            naProfile.enter();
            return RRuntime.LOGICAL_NA;
        }
        return RRuntime.asLogical(operation.opLogical(left, right));
    }

    @Fallback
    @SuppressWarnings("unused")
    public byte doFallback(Object left, Object right) {
        throw RSpecialFactory.throwFullCallNeeded();
    }
}
