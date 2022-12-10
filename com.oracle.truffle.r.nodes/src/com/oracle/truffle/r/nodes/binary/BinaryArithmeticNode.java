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
package com.oracle.truffle.r.nodes.binary;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.profiles.ValueProfile;
import com.oracle.truffle.r.nodes.builtin.RBuiltinNode;
import com.oracle.truffle.r.nodes.primitive.BinaryMapNode;
import com.oracle.truffle.r.nodes.profile.TruffleBoundaryNode;
import com.oracle.truffle.r.nodes.unary.UnaryArithmeticNode;
import com.oracle.truffle.r.nodes.unary.UnaryArithmeticNodeGen;
import com.oracle.truffle.r.runtime.DSLConfig;
import com.oracle.truffle.r.runtime.RError;
import com.oracle.truffle.r.runtime.RError.Message;
import com.oracle.truffle.r.runtime.RType;
import com.oracle.truffle.r.runtime.data.RDataFactory;
import com.oracle.truffle.r.runtime.data.RDoubleVector;
import com.oracle.truffle.r.runtime.data.RMissing;
import com.oracle.truffle.r.runtime.data.RNull;
import com.oracle.truffle.r.runtime.data.RComplexVector;
import com.oracle.truffle.r.runtime.data.RIntVector;
import com.oracle.truffle.r.runtime.data.RLogicalVector;
import com.oracle.truffle.r.runtime.data.model.RAbstractVector;
import com.oracle.truffle.r.runtime.nodes.RBaseNode;
import com.oracle.truffle.r.runtime.ops.BinaryArithmetic;
import com.oracle.truffle.r.runtime.ops.BinaryArithmeticFactory;
import com.oracle.truffle.r.runtime.ops.UnaryArithmeticFactory;

/**
 * Represents a binary or unary operation from the 'arithmetic' subset of Ops R group. The concrete
 * operation is implemented by factory object given as a constructor parameter, e.g.
 * {@link com.oracle.truffle.r.runtime.ops.BinaryArithmetic.Add}
 */
@ImportStatic(DSLConfig.class)
public abstract class BinaryArithmeticNode extends RBuiltinNode.Arg2 {

    protected static final int CACHE_LIMIT = 5;

    protected final BinaryArithmeticFactory binary;
    private final UnaryArithmeticFactory unary;

    @Child private BinaryArithmetic operation;

    static {
        Casts casts = new Casts(BinaryArithmeticNode.class);
        casts.arg(0).boxPrimitive();
        casts.arg(1).boxPrimitive();
    }

    public BinaryArithmeticNode(BinaryArithmeticFactory binaryFactory, UnaryArithmeticFactory unaryFactory) {
        this.binary = binaryFactory;
        this.operation = binaryFactory.createOperation();
        this.unary = unaryFactory;
    }

    public abstract Object execute(Object left, Object right);

    @Override
    public RBaseNode getErrorContext() {
        return this;
    }

    public static BinaryArithmeticNode create(BinaryArithmeticFactory binary, UnaryArithmeticFactory unary) {
        return BinaryArithmeticNodeGen.create(binary, unary);
    }

    @Specialization(limit = "getCacheSize(CACHE_LIMIT)", guards = {"cached != null", "cached.isSupported(left, right)"})
    protected Object doNumericVectorCached(RAbstractVector left, RAbstractVector right,
                    @Cached("createFastCached(left, right)") BinaryMapNode cached) {
        return cached.apply(left, right);
    }

    @Specialization(replaces = "doNumericVectorCached", guards = {"isNumericVector(left)", "isNumericVector(right)"})
    @TruffleBoundary
    protected Object doNumericVectorGeneric(RAbstractVector left, RAbstractVector right,
                    @Cached("binary.createOperation()") BinaryArithmetic arithmetic,
                    @Cached("createGeneric()") GenericNumericVectorNode generic) {
        return generic.get(arithmetic, left, right).apply(left, right);
    }

    protected BinaryMapNode createFastCached(RAbstractVector left, RAbstractVector right) {
        if (isNumericVector(left) && isNumericVector(right)) {
            return createCached(binary.createOperation(), left, right, false);
        }
        return null;
    }

    protected static boolean isNumericVector(RAbstractVector value) {
        return value instanceof RIntVector || value instanceof RDoubleVector || value instanceof RComplexVector || value instanceof RLogicalVector;
    }

    @Specialization
    protected Object doUnary(Object left, @SuppressWarnings("unused") RMissing right,
                    @Cached("createUnaryArithmeticNode()") UnaryArithmeticNode unaryNode) {
        return unaryNode.execute(left);
    }

    protected final UnaryArithmeticNode createUnaryArithmeticNode() {
        if (unary == null) {
            throw error(RError.Message.ARGUMENT_EMPTY, 2);
        } else {
            return UnaryArithmeticNodeGen.create(unary);
        }
    }

    @Specialization
    protected Object doBothNull(@SuppressWarnings("unused") RNull left, @SuppressWarnings("unused") RNull right) {
        return operation instanceof BinaryArithmetic.Div || operation instanceof BinaryArithmetic.Pow ? RType.Double.getEmpty() : RType.Integer.getEmpty();
    }

    @Specialization(guards = {"isNumericVector(right)"})
    protected Object doLeftNull(@SuppressWarnings("unused") RNull left, RAbstractVector right,
                    @Cached("createClassProfile()") ValueProfile classProfile) {
        RType type = classProfile.profile(right).getRType();
        if (type == RType.Complex) {
            return RDataFactory.createEmptyComplexVector();
        } else {
            if (type == RType.Integer || type == RType.Logical) {
                if (operation instanceof BinaryArithmetic.Div || operation instanceof BinaryArithmetic.Pow) {
                    return RType.Double.getEmpty();
                } else {
                    return RType.Integer.getEmpty();
                }
            } else if (type == RType.Double) {
                return RType.Double.getEmpty();
            } else {
                throw error(Message.NON_NUMERIC_BINARY);
            }
        }
    }

    @Specialization(guards = {"isNumericVector(left)"})
    protected Object doRightNull(RAbstractVector left, RNull right,
                    @Cached("createClassProfile()") ValueProfile classProfile) {
        return doLeftNull(right, left, classProfile);
    }

    @Fallback
    protected Object doInvalidType(@SuppressWarnings("unused") Object left, @SuppressWarnings("unused") Object right) {
        throw error(Message.NON_NUMERIC_BINARY);
    }

    protected static BinaryMapNode createCached(BinaryArithmetic innerArithmetic, Object left, Object right, boolean isGeneric) {
        RAbstractVector leftVector = (RAbstractVector) left;
        RAbstractVector rightVector = (RAbstractVector) right;

        RType argumentType = RType.maxPrecedence(RType.Integer, RType.maxPrecedence(leftVector.getRType(), rightVector.getRType()));
        RType resultType = argumentType;
        if (resultType == RType.Integer && !innerArithmetic.isSupportsIntResult()) {
            resultType = RType.Double;
        }

        return BinaryMapNode.create(new BinaryMapArithmeticFunctionNode(innerArithmetic), leftVector, rightVector, argumentType, resultType, true, isGeneric);
    }

    protected static GenericNumericVectorNode createGeneric() {
        return new GenericNumericVectorNode();
    }

    protected static final class GenericNumericVectorNode extends TruffleBoundaryNode {

        @Child private BinaryMapNode cached;

        public BinaryMapNode get(BinaryArithmetic arithmetic, RAbstractVector left, RAbstractVector right) {
            CompilerAsserts.neverPartOfCompilation();
            BinaryMapNode map = cached;
            if (map == null || !map.isSupported(left, right)) {
                cached = map = insert(createCached(arithmetic, left, right, true));
            }
            return map;
        }
    }
}
