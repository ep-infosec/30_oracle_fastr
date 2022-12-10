/*
 * Copyright (c) 2014, 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.r.nodes.test;

import static com.oracle.truffle.r.nodes.test.TestUtilities.copy;
import static com.oracle.truffle.r.nodes.test.TestUtilities.createHandle;
import static com.oracle.truffle.r.nodes.test.TestUtilities.withinTestContext;
import static com.oracle.truffle.r.runtime.data.RDataFactory.createDoubleSequence;
import static com.oracle.truffle.r.runtime.data.RDataFactory.createDoubleVectorFromScalar;
import static com.oracle.truffle.r.runtime.data.RDataFactory.createEmptyComplexVector;
import static com.oracle.truffle.r.runtime.data.RDataFactory.createEmptyDoubleVector;
import static com.oracle.truffle.r.runtime.data.RDataFactory.createEmptyIntVector;
import static com.oracle.truffle.r.runtime.data.RDataFactory.createEmptyLogicalVector;
import static com.oracle.truffle.r.runtime.data.RDataFactory.createIntSequence;
import static com.oracle.truffle.r.runtime.data.RDataFactory.createIntVectorFromScalar;
import static com.oracle.truffle.r.runtime.ops.BinaryArithmetic.ADD;
import static com.oracle.truffle.r.runtime.ops.BinaryArithmetic.DIV;
import static com.oracle.truffle.r.runtime.ops.BinaryArithmetic.INTEGER_DIV;
import static com.oracle.truffle.r.runtime.ops.BinaryArithmetic.MAX;
import static com.oracle.truffle.r.runtime.ops.BinaryArithmetic.MIN;
import static com.oracle.truffle.r.runtime.ops.BinaryArithmetic.MOD;
import static com.oracle.truffle.r.runtime.ops.BinaryArithmetic.MULTIPLY;
import static com.oracle.truffle.r.runtime.ops.BinaryArithmetic.SUBTRACT;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeThat;
import static org.junit.Assume.assumeTrue;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.theories.DataPoints;
import org.junit.experimental.theories.Theories;
import org.junit.experimental.theories.Theory;
import org.junit.internal.AssumptionViolatedException;
import org.junit.runner.RunWith;

import com.oracle.truffle.api.object.DynamicObject;
import com.oracle.truffle.r.nodes.binary.BinaryArithmeticNode;
import com.oracle.truffle.r.nodes.test.TestUtilities.NodeHandle;
import com.oracle.truffle.r.runtime.RType;
import com.oracle.truffle.r.runtime.data.RAttributesLayout;
import com.oracle.truffle.r.runtime.data.RComplex;
import com.oracle.truffle.r.runtime.data.RNull;
import com.oracle.truffle.r.runtime.data.RScalarVector;
import com.oracle.truffle.r.runtime.data.RSequence;
import com.oracle.truffle.r.runtime.data.RSharingAttributeStorage;
import com.oracle.truffle.r.runtime.data.model.RAbstractVector;
import com.oracle.truffle.r.runtime.ops.BinaryArithmetic;
import com.oracle.truffle.r.runtime.ops.BinaryArithmeticFactory;

/**
 * This test verifies white box assumptions for the arithmetic node. Please note that this node
 * should NOT verify correctness. This is done by the integration test suite.
 */
@RunWith(Theories.class)
public class BinaryArithmeticNodeTest extends BinaryVectorTest {
    @Test
    public void dummy() {
        // to make sure this file is recognized as a test
    }

    @DataPoints public static final BinaryArithmeticFactory[] BINARY = BinaryArithmetic.ALL;

    @Theory
    public void testScalarUnboxing(BinaryArithmeticFactory factory, RAbstractVector aOrig, RAbstractVector bOrig) {
        execInContext(() -> {
            RAbstractVector a = withinTestContext(() -> aOrig.copy());
            RAbstractVector b = copy(bOrig);
            // unboxing cannot work if length is 1
            assumeThat(b.getLength(), is(1));

            // if the right side is shareable these should be prioritized
            assumeThat(b, is(not(instanceOf(RSharingAttributeStorage.class))));

            assumeArithmeticCompatible(factory, a, b);
            Object result = executeArithmetic(factory, a, b);
            Assert.assertTrue(isPrimitive(result));
            return null;
        });
    }

    @Theory
    public void testVectorResult(BinaryArithmeticFactory factory, RAbstractVector aOrig, RAbstractVector bOrig) {
        execInContext(() -> {
            RAbstractVector a = copy(aOrig);
            RAbstractVector b = copy(bOrig);
            assumeThat(a, is(not(instanceOf(RScalarVector.class))));
            assumeThat(b, is(not(instanceOf(RScalarVector.class))));
            assumeArithmeticCompatible(factory, a, b);

            Object result = executeArithmetic(factory, a, b);
            Assert.assertFalse(isPrimitive(result));
            assertLengthAndType(factory, a, b, (RAbstractVector) result);

            assumeThat(b, is(not(instanceOf(RScalarVector.class))));
            result = executeArithmetic(factory, b, a);
            assertLengthAndType(factory, a, b, (RAbstractVector) result);
            return null;
        });
    }

    @Theory
    public void testSharing(BinaryArithmeticFactory factory, RAbstractVector aOrig, RAbstractVector bOrig) {
        execInContext(() -> {
            RAbstractVector a = copy(aOrig);
            RAbstractVector b = copy(bOrig);
            assumeArithmeticCompatible(factory, a, b);

            // not part of this test, see #testEmptyArrays
            assumeThat(a.getLength(), is(not(0)));
            assumeThat(b.getLength(), is(not(0)));

            // sharing does not work if a is a scalar vector
            assumeThat(a, is(not(instanceOf(RScalarVector.class))));

            RType resultType = getResultType(factory, a, b);
            int maxLength = Integer.max(a.getLength(), b.getLength());
            RAbstractVector sharedResult = null;
            if (a.getLength() == maxLength && isShareable(a, resultType)) {
                sharedResult = a;
            }
            if (sharedResult == null && b.getLength() == maxLength && isShareable(b, resultType)) {
                sharedResult = b;
            }

            Object result = executeArithmetic(factory, a, b);

            if (sharedResult == null) {
                Assert.assertNotSame(a, result);
                Assert.assertNotSame(b, result);
            } else {
                Assert.assertSame(sharedResult, result);
            }
            return null;
        });
    }

    private static boolean isShareable(RAbstractVector a, RType resultType) {
        if (a.getRType() != resultType) {
            // needs cast -> not shareable
            return false;
        }

        if (RSharingAttributeStorage.isShareable(a)) {
            return ((RSharingAttributeStorage) a).isTemporary();
        }
        return false;
    }

    private static void assertLengthAndType(BinaryArithmeticFactory factory, RAbstractVector a, RAbstractVector b, RAbstractVector resultVector) {
        int expectedLength = Math.max(a.getLength(), b.getLength());
        if (a.getLength() == 0 || b.getLength() == 0) {
            expectedLength = 0;
        }
        assertThat(resultVector.getLength(), is(equalTo(expectedLength)));
        RType resultType = getResultType(factory, a, b);
        assertThat(resultVector.getRType(), is(equalTo(resultType)));
    }

    private static RType getResultType(BinaryArithmeticFactory factory, RAbstractVector a, RAbstractVector b) {
        RType resultType = getArgumentType(a, b);
        if (!factory.createOperation().isSupportsIntResult() && resultType == RType.Integer) {
            resultType = RType.Double;
        }
        return resultType;
    }

    @Theory
    public void testEmptyArrays(BinaryArithmeticFactory factory, RAbstractVector originalVector) {
        execInContext(() -> {
            RAbstractVector vector = withinTestContext(() -> originalVector.copy());
            testEmptyArray(factory, vector, createEmptyLogicalVector());
            testEmptyArray(factory, vector, createEmptyIntVector());
            testEmptyArray(factory, vector, createEmptyDoubleVector());
            testEmptyArray(factory, vector, createEmptyComplexVector());
            return null;
        });
    }

    @Theory
    public void testRNullConstantResult(BinaryArithmeticFactory factory, RAbstractVector originalVector) {
        execInContext(() -> {
            RAbstractVector vector = withinTestContext(() -> originalVector.copy());

            RType type = null;
            RType rType = vector.getRType();
            if (rType == RType.Complex) {
                type = RType.Complex;
            } else {
                if (rType == RType.Integer || rType == RType.Logical) {
                    if (factory == BinaryArithmetic.DIV || factory == BinaryArithmetic.POW) {
                        type = RType.Double;
                    } else {
                        type = RType.Integer;
                    }
                } else if (rType == RType.Double) {
                    type = RType.Double;
                } else {
                    Assert.fail();
                }
            }

            assertThat(executeArithmetic(factory, vector, RNull.instance), isEmptyVectorOf(type));
            assertThat(executeArithmetic(factory, RNull.instance, vector), isEmptyVectorOf(type));
            return null;
        });
    }

    @Theory
    public void testBothNull(BinaryArithmeticFactory factory) {
        execInContext(() -> {
            assertThat(executeArithmetic(factory, RNull.instance, RNull.instance), isEmptyVectorOf(factory == BinaryArithmetic.DIV || factory == BinaryArithmetic.POW ? RType.Double : RType.Integer));
            return null;
        });
    }

    @Theory
    public void testCompleteness(BinaryArithmeticFactory factory, RAbstractVector aOrig, RAbstractVector bOrig) {
        execInContext(() -> {
            assumeTrue(isCacheEnabled());

            RAbstractVector a = copy(aOrig);
            RAbstractVector b = copy(bOrig);
            assumeArithmeticCompatible(factory, a, b);

            // disable division they might produce NA values by division with 0
            assumeFalse(factory == BinaryArithmetic.DIV);
            assumeFalse(factory == BinaryArithmetic.INTEGER_DIV);
            assumeFalse(factory == BinaryArithmetic.MOD);

            Object result = executeArithmetic(factory, a, b);

            boolean resultComplete = isPrimitive(result) || ((RAbstractVector) result).isComplete();

            if (a.getLength() == 0 || b.getLength() == 0) {
                Assert.assertTrue(resultComplete);
            } else {
                if (result instanceof RAbstractVector && ((RAbstractVector) result).isComplete()) {
                    RAbstractVector.verifyVector((RAbstractVector) result);
                } else {
                    boolean expectedComplete = a.isComplete() && b.isComplete();
                    Assert.assertEquals(expectedComplete, resultComplete);
                }
            }
            return null;
        });
    }

    @Theory
    public void testCopyAttributes(BinaryArithmeticFactory factory, RAbstractVector aOrig, RAbstractVector bOrig) {
        execInContext(() -> {
            assumeArithmeticCompatible(factory, aOrig, bOrig);

            // we have to e careful not to change mutable vectors
            RAbstractVector a = copy(aOrig);
            RAbstractVector b = copy(bOrig);
            if (RSharingAttributeStorage.isShareable(a)) {
                assert ((RSharingAttributeStorage) a).isTemporary();
                ((RSharingAttributeStorage) a).incRefCount();
            }
            if (RSharingAttributeStorage.isShareable(b)) {
                assert ((RSharingAttributeStorage) b).isTemporary();
                ((RSharingAttributeStorage) b).incRefCount();
            }

            RAbstractVector aMaterialized = withinTestContext(() -> a.copy().materialize());
            RAbstractVector bMaterialized = withinTestContext(() -> b.copy().materialize());

            aMaterialized.setAttr("a", "a");
            bMaterialized.setAttr("b", "b");

            if (a.getLength() == 0 || b.getLength() == 0) {
                assertAttributes(executeArithmetic(factory, copy(aMaterialized), copy(bMaterialized)));
                assertAttributes(executeArithmetic(factory, a, copy(bMaterialized)));
                assertAttributes(executeArithmetic(factory, copy(aMaterialized), b));
            } else if (a.getLength() == b.getLength()) {
                assertAttributes(executeArithmetic(factory, copy(aMaterialized), copy(bMaterialized)), "a", "b");
                assertAttributes(executeArithmetic(factory, a, copy(bMaterialized)), "b");
                assertAttributes(executeArithmetic(factory, copy(aMaterialized), b), "a");
            } else if (a.getLength() > b.getLength()) {
                assertAttributes(executeArithmetic(factory, copy(aMaterialized), copy(bMaterialized)), "a");
                assertAttributes(executeArithmetic(factory, a, copy(bMaterialized)));
                assertAttributes(executeArithmetic(factory, copy(aMaterialized), b), "a");
            } else {
                assert a.getLength() < b.getLength();
                assertAttributes(executeArithmetic(factory, copy(aMaterialized), copy(bMaterialized)), "b");
                assertAttributes(executeArithmetic(factory, a, copy(bMaterialized)), "b");
                assertAttributes(executeArithmetic(factory, copy(aMaterialized), b));
            }
            return null;
        });
    }

    @Test
    public void testSequenceFolding() {
        execInContext(() -> {
            assertFold(true, createIntSequence(1, 3, 10), createIntVectorFromScalar(5), ADD, SUBTRACT, MULTIPLY, INTEGER_DIV);
            assertFold(true, createIntVectorFromScalar(5), createIntSequence(1, 3, 10), ADD, MULTIPLY);
            assertFold(true, createIntSequence(1, 3, 10), createIntSequence(2, 5, 10), ADD, SUBTRACT);
            assertFold(false, createIntVectorFromScalar(5), createIntSequence(1, 3, 10), SUBTRACT, INTEGER_DIV, MOD);
            assertFold(false, createIntSequence(1, 3, 10), createIntSequence(2, 5, 5), ADD, SUBTRACT, MULTIPLY, INTEGER_DIV);

            assertFold(true, createDoubleSequence(1, 3, 10), createDoubleVectorFromScalar(5), ADD, SUBTRACT, MULTIPLY, INTEGER_DIV);
            assertFold(true, createDoubleVectorFromScalar(5), createDoubleSequence(1, 3, 10), ADD, MULTIPLY);
            assertFold(true, createDoubleSequence(1, 3, 10), createDoubleSequence(2, 5, 10), ADD, SUBTRACT);
            assertFold(false, createDoubleVectorFromScalar(5), createDoubleSequence(1, 3, 10), SUBTRACT, INTEGER_DIV, MOD);
            assertFold(false, createDoubleSequence(1, 3, 10), createDoubleSequence(2, 5, 5), ADD, SUBTRACT, MULTIPLY, INTEGER_DIV);
            return null;
        });
    }

    @Theory
    public void testGeneric(BinaryArithmeticFactory factory) {
        execInContext(() -> {
            // this should trigger the generic case
            for (RAbstractVector vector : ALL_VECTORS) {
                try {
                    assumeArithmeticCompatible(factory, vector, vector);
                } catch (AssumptionViolatedException e) {
                    continue;
                }
                executeArithmetic(factory, copy(vector), copy(vector));
            }
            return null;
        });
    }

    private static void assertAttributes(Object value, String... keys) {
        if (!(value instanceof RAbstractVector)) {
            Assert.assertEquals(0, keys.length);
            return;
        }

        RAbstractVector vector = (RAbstractVector) value;
        Set<String> expectedAttributes = new HashSet<>(Arrays.asList(keys));

        DynamicObject attributes = vector.getAttributes();
        if (attributes == null) {
            Assert.assertEquals(0, keys.length);
            return;
        }
        Set<Object> foundAttributes = new HashSet<>();
        for (RAttributesLayout.RAttribute attribute : RAttributesLayout.asIterable(attributes)) {
            foundAttributes.add(attribute.getName());
            foundAttributes.add(attribute.getValue());
        }
        Assert.assertEquals(expectedAttributes, foundAttributes);
    }

    private static void assumeArithmeticCompatible(BinaryArithmeticFactory factory, RAbstractVector a, RAbstractVector b) {
        RType argumentType = getArgumentType(a, b);
        assumeTrue(argumentType.isNumeric());

        // TODO complex mod, div, min, max not yet implemented
        assumeFalse(factory == DIV && (argumentType == RType.Complex));
        assumeFalse(factory == INTEGER_DIV && (argumentType == RType.Complex));
        assumeFalse(factory == MOD && (argumentType == RType.Complex));
        assumeFalse(factory == MAX && (argumentType == RType.Complex));
        assumeFalse(factory == MIN && (argumentType == RType.Complex));
    }

    private void testEmptyArray(BinaryArithmeticFactory factory, RAbstractVector vector, RAbstractVector empty) {
        assertThat(executeArithmetic(factory, vector, empty), isEmptyVectorOf(getResultType(factory, vector, empty)));
        assertThat(executeArithmetic(factory, empty, vector), isEmptyVectorOf(getResultType(factory, empty, vector)));
        assertThat(executeArithmetic(factory, empty, empty), isEmptyVectorOf(getResultType(factory, empty, empty)));
    }

    private static RType getArgumentType(RAbstractVector a, RAbstractVector b) {
        return RType.maxPrecedence(RType.Integer, RType.maxPrecedence(a.getRType(), b.getRType()));
    }

    private static boolean isPrimitive(Object result) {
        return result instanceof Integer || result instanceof Double || result instanceof Byte || result instanceof RComplex;
    }

    private void assertFold(boolean expectedFold, RAbstractVector left, RAbstractVector right, BinaryArithmeticFactory... arithmetics) {
        for (int i = 0; i < arithmetics.length; i++) {
            BinaryArithmeticFactory factory = arithmetics[i];
            Object result = executeArithmetic(factory, left, right);
            if (expectedFold) {
                assertThat("expected fold " + left + " <op> " + right, ((RAbstractVector) result).isSequence());
            } else {
                assertThat("expected not fold" + left + " <op> " + right, !(result instanceof RSequence));
            }
        }
    }

    private NodeHandle<BinaryArithmeticNode> handle;
    private BinaryArithmeticFactory currentFactory;

    @Before
    public void setUp() {
        handle = null;
    }

    @After
    public void tearDown() {
        handle = null;
    }

    private Object executeArithmetic(BinaryArithmeticFactory factory, Object left, Object right) {
        if (handle == null || this.currentFactory != factory) {
            handle = create(factory);
            this.currentFactory = factory;
        }
        return handle.call(left, right);
    }

    private static NodeHandle<BinaryArithmeticNode> create(BinaryArithmeticFactory factory) {
        return createHandle(BinaryArithmeticNode.create(factory, null),
                        (node, args) -> node.execute(args[0], args[1]));
    }
}
