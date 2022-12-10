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
package com.oracle.truffle.r.nodes.helpers;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.NodeCost;
import com.oracle.truffle.api.nodes.NodeInfo;
import com.oracle.truffle.api.nodes.NodeInterface;
import com.oracle.truffle.api.profiles.ConditionProfile;
import com.oracle.truffle.r.runtime.data.VectorDataLibrary;
import com.oracle.truffle.r.runtime.data.model.RAbstractAtomicVector;
import com.oracle.truffle.r.runtime.data.nodes.attributes.HasAttributesNode;
import com.oracle.truffle.r.nodes.helpers.SpecialsUtilsFactory.ConvertIndexNodeGen;
import com.oracle.truffle.r.nodes.helpers.SpecialsUtilsFactory.ConvertValueNodeGen;
import com.oracle.truffle.r.runtime.ArgumentsSignature;
import com.oracle.truffle.r.runtime.Utils;
import com.oracle.truffle.r.runtime.nodes.RNode;
import com.oracle.truffle.r.runtime.nodes.RSyntaxNode;

/**
 * Helper methods for implementing special calls.
 *
 * @see com.oracle.truffle.r.runtime.builtins.RSpecialFactory
 */
public class SpecialsUtils {
    public interface SubInterface extends NodeInterface {

        Object execute(Object vec, Object index);

        Object execute(Object vec, int index);
    }

    public interface Sub2Interface extends NodeInterface {

        Object execute(Object vector, Object index1, Object index2);

        Object execute(Object vec, int index1, int index2);
    }

    private static final String valueArgName = Utils.intern("value");

    public static boolean isCorrectUpdateSignature(ArgumentsSignature signature) {
        if (signature.getLength() == 3) {
            return signature.getName(0) == null && signature.getName(1) == null && Utils.identityEquals(signature.getName(2), valueArgName);
        } else if (signature.getLength() == 4) {
            return signature.getName(0) == null && signature.getName(1) == null && signature.getName(2) == null && Utils.identityEquals(signature.getName(3), valueArgName);
        }
        return false;
    }

    @NodeInfo(cost = NodeCost.NONE)
    @NodeChild(value = "delegate", type = RNode.class)
    public abstract static class ConvertIndex extends RNode {

        protected abstract RNode getDelegate();

        public abstract Object execute(Object value);

        @Specialization
        protected static int convertInteger(int value) {
            return value;
        }

        @Specialization(rewriteOn = IllegalArgumentException.class)
        protected int convertDouble(double value) {
            int intValue = (int) value;
            if (intValue <= 0) {
                /*
                 * Conversion from double to an index differs in subscript and subset for values in
                 * the ]0..1[ range (subscript interprets 0.1 as 1, whereas subset treats it as 0).
                 * We avoid this special case by simply going to the more generic case for this
                 * range. Additionally, (int) Double.NaN is 0, which is also caught by this case.
                 */
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw new IllegalArgumentException();
            } else {
                return intValue;
            }
        }

        @Specialization(replaces = {"convertInteger", "convertDouble"})
        protected Object convert(Object value) {
            return value;
        }

        @Override
        protected RSyntaxNode getRSyntaxNode() {
            return getDelegate().asRSyntaxNode();
        }
    }

    @NodeInfo(cost = NodeCost.NONE)
    @NodeChild(value = "delegate", type = RNode.class)
    public abstract static class ConvertValue extends RNode {
        @Child private HasAttributesNode hasAttrsNode;

        protected boolean hasAttributes(Object value) {
            if (hasAttrsNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                hasAttrsNode = insert(HasAttributesNode.create());
            }
            return hasAttrsNode.execute(value);
        }

        protected abstract RNode getDelegate();

        @Specialization
        protected static int convertInt(int value) {
            return value;
        }

        @Specialization
        protected static double convertDouble(double value) {
            return value;
        }

        @Specialization
        protected static byte convertLogical(byte value) {
            return value;
        }

        @Specialization
        protected static String convertString(String value) {
            return value;
        }

        @Specialization(guards = {"!hasAttributes(vec)"}, limit = "getTypedVectorDataLibraryCacheSize()")
        Object doSimpleVectors(RAbstractAtomicVector vec,
                        @Cached("createBinaryProfile()") ConditionProfile isLengthOneProfile,
                        @CachedLibrary("vec.getData()") VectorDataLibrary dataLib) {
            Object data = vec.getData();
            if (isLengthOneProfile.profile(dataLib.getLength(data) == 1)) {
                return dataLib.getDataAtAsObject(data, 0);
            } else {
                return vec;
            }
        }

        @Specialization(guards = {"hasAttributes(vec)"})
        RAbstractAtomicVector doVectorsWithAttrs(RAbstractAtomicVector vec) {
            return vec;
        }

        @Fallback
        protected Object convert(Object value) {
            return value;
        }

        @Override
        protected RSyntaxNode getRSyntaxNode() {
            return getDelegate().asRSyntaxNode();
        }
    }

    public static ConvertIndex convertIndex(RNode value) {
        return ConvertIndexNodeGen.create(value);
    }

    public static ConvertValue unboxValue(RNode value) {
        return ConvertValueNodeGen.create(value);
    }
}
