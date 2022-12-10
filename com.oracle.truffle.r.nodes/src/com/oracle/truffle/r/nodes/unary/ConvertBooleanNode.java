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
package com.oracle.truffle.r.nodes.unary;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.r.runtime.DSLConfig;
import com.oracle.truffle.r.runtime.RError;
import com.oracle.truffle.r.runtime.RRuntime;
import com.oracle.truffle.r.runtime.data.RComplex;
import com.oracle.truffle.r.runtime.data.RLogicalVector;
import com.oracle.truffle.r.runtime.data.RNull;
import com.oracle.truffle.r.runtime.data.RRaw;
import com.oracle.truffle.r.runtime.data.VectorDataLibrary;
import com.oracle.truffle.r.runtime.data.model.RAbstractVector;
import com.oracle.truffle.r.runtime.interop.ConvertForeignObjectNode;
import com.oracle.truffle.r.runtime.nodes.RNode;
import com.oracle.truffle.r.runtime.nodes.RSyntaxNode;
import com.oracle.truffle.r.runtime.ops.na.NAProfile;

@NodeChild("operand")
@ImportStatic({RRuntime.class, DSLConfig.class})
public abstract class ConvertBooleanNode extends RNode {

    protected static final int ATOMIC_VECTOR_LIMIT = 8;

    private final NAProfile naProfile = NAProfile.create();
    private final BranchProfile invalidElementCountBranch = BranchProfile.create();
    @Child private ConvertBooleanNode recursiveConvertBoolean;

    @Override
    public final Object execute(VirtualFrame frame) {
        return executeByte(frame);
    }

    public abstract RNode getOperand();

    @Override
    public abstract byte executeByte(VirtualFrame frame);

    public abstract byte executeByte(VirtualFrame frame, Object operandValue);

    @Specialization
    protected byte doNull(@SuppressWarnings("unused") RNull value) {
        throw error(RError.Message.LENGTH_ZERO);
    }

    @Specialization
    protected byte doInt(int value) {
        if (naProfile.isNA(value)) {
            throw error(RError.Message.ARGUMENT_NOT_INTERPRETABLE_LOGICAL);
        }
        return RRuntime.int2logicalNoCheck(value);
    }

    @Specialization
    protected byte doDouble(double value) {
        if (naProfile.isNA(value)) {
            throw error(RError.Message.ARGUMENT_NOT_INTERPRETABLE_LOGICAL);
        }
        return RRuntime.double2logicalNoCheck(value);
    }

    @Specialization
    protected byte doLogical(byte value) {
        if (naProfile.isNA(value)) {
            throw error(RError.Message.NA_UNEXP);
        }
        return value;
    }

    @Specialization
    protected byte doComplex(RComplex value) {
        if (naProfile.isNA(value)) {
            throw error(RError.Message.ARGUMENT_NOT_INTERPRETABLE_LOGICAL);
        }
        return RRuntime.complex2logicalNoCheck(value);
    }

    @Specialization
    protected byte doString(String value) {
        byte logicalValue = RRuntime.string2logicalNoCheck(value);
        if (naProfile.isNA(logicalValue)) {
            throw error(RError.Message.ARGUMENT_NOT_INTERPRETABLE_LOGICAL);
        }
        return logicalValue;
    }

    @Specialization(guards = "library.getLength(value.getData()) == 1", limit = "getTypedVectorDataLibraryCacheSize()")
    protected byte doRLogical(RLogicalVector value,
                    @CachedLibrary("value.getData()") VectorDataLibrary library) {
        // fast path for very common case, handled also in doVector
        byte res = library.getLogicalAt(value.getData(), 0);
        if (naProfile.isNA(res)) {
            throw error(RError.Message.NA_UNEXP);
        }
        return res;
    }

    @Specialization
    protected byte doRaw(RRaw value) {
        return RRuntime.raw2logical(value.getValue());
    }

    private void checkLength(int length) {
        if (length != 1) {
            invalidElementCountBranch.enter();
            if (length == 0) {
                throw error(RError.Message.LENGTH_ZERO);
            } else {
                warning(RError.Message.LENGTH_GT_1);
            }
        }
    }

    @Specialization(replaces = "doRLogical", limit = "getGenericDataLibraryCacheSize()")
    protected byte doVector(RAbstractVector value,
                    @CachedLibrary("value.getData()") VectorDataLibrary dataLib) {
        Object data = value.getData();
        checkLength(dataLib.getLength(data));
        switch (dataLib.getType(data)) {
            case Integer:
                return doInt(dataLib.getIntAt(data, 0));
            case Double:
                return doDouble(dataLib.getDoubleAt(data, 0));
            case Raw:
                return RRuntime.raw2logical(dataLib.getRawAt(data, 0));
            case Logical:
                return doLogical(dataLib.getLogicalAt(data, 0));
            case Character:
                return doString(dataLib.getStringAt(data, 0));
            case Complex:
                return doComplex(dataLib.getComplexAt(data, 0));
            default:
                throw error(RError.Message.ARGUMENT_NOT_INTERPRETABLE_LOGICAL);
        }
    }

    @Specialization(guards = "isForeignObject(obj)")
    protected byte doForeignObject(VirtualFrame frame, TruffleObject obj,
                    @Cached("create()") ConvertForeignObjectNode convertForeign) {
        Object o = convertForeign.convert(obj);
        if (!RRuntime.isForeignObject(o)) {
            return convertBooleanRecursive(frame, o);
        }
        throw error(RError.Message.ARGUMENT_NOT_INTERPRETABLE_LOGICAL);
    }

    @Fallback
    protected byte doObject(@SuppressWarnings("unused") Object o) {
        throw error(RError.Message.ARGUMENT_NOT_INTERPRETABLE_LOGICAL);
    }

    public static ConvertBooleanNode create(RSyntaxNode node) {
        if (node instanceof ConvertBooleanNode) {
            return (ConvertBooleanNode) node;
        }
        return ConvertBooleanNodeGen.create(node.asRNode());
    }

    @Override
    public RSyntaxNode getRSyntaxNode() {
        return getOperand().asRSyntaxNode();
    }

    protected byte convertBooleanRecursive(VirtualFrame frame, Object o) {
        if (recursiveConvertBoolean == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            recursiveConvertBoolean = insert(ConvertBooleanNode.create(getRSyntaxNode()));
        }
        return recursiveConvertBoolean.executeByte(frame, o);
    }
}
