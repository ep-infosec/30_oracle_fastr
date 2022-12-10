/*
 * Copyright (c) 2017, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.r.nodes.builtin.base.infix.special;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.r.nodes.helpers.SpecialsUtils.ConvertIndex;
import com.oracle.truffle.r.nodes.helpers.SpecialsUtils.ConvertValue;
import com.oracle.truffle.r.runtime.DSLConfig;
import com.oracle.truffle.r.runtime.RInternalError;
import com.oracle.truffle.r.runtime.data.model.RAbstractVector;
import com.oracle.truffle.r.runtime.nodes.RNode;

public class ProfiledSpecialsUtils {

    @ImportStatic(DSLConfig.class)
    @NodeChild(value = "vector", type = RNode.class)
    @NodeChild(value = "index", type = ConvertIndex.class)
    protected abstract static class ProfiledSubscriptSpecialBase extends RNode {

        protected static final int CACHE_LIMIT = 3;
        protected final boolean inReplacement;

        @Child protected AccessSpecial defaultAccessNode;

        protected ProfiledSubscriptSpecialBase(boolean inReplacement) {
            this.inReplacement = inReplacement;
        }

        protected AccessSpecial createAccessNode() {
            throw RInternalError.shouldNotReachHere();
        }

        @Specialization(limit = "getCacheSize(CACHE_LIMIT)", guards = "vector.getClass() == clazz")
        public Object access(RAbstractVector vector, Object index,
                        @Cached(value = "vector.getClass()") Class<?> clazz,
                        @Cached("createAccessNode()") AccessSpecial accessNodeCached) {
            return accessNodeCached.execute(clazz.cast(vector), index);
        }

        @Specialization(replaces = "access")
        public Object accessGeneric(Object vector, Object index) {
            if (defaultAccessNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                defaultAccessNode = insert(createAccessNode());
            }
            return defaultAccessNode.execute(vector, index);
        }
    }

    public abstract static class ProfiledSubscriptSpecial extends ProfiledSubscriptSpecialBase {

        protected ProfiledSubscriptSpecial(boolean inReplacement) {
            super(inReplacement);
        }

        @Override
        protected AccessSpecial createAccessNode() {
            return SubscriptSpecialNodeGen.create(inReplacement);
        }
    }

    public abstract static class ProfiledSubsetSpecial extends ProfiledSubscriptSpecialBase {

        protected ProfiledSubsetSpecial(boolean inReplacement) {
            super(inReplacement);
        }

        @Override
        protected AccessSpecial createAccessNode() {
            return SubsetSpecialNodeGen.create(inReplacement);
        }
    }

    @ImportStatic(DSLConfig.class)
    @NodeChild(value = "vector", type = RNode.class)
    @NodeChild(value = "index1", type = ConvertIndex.class)
    @NodeChild(value = "index2", type = ConvertIndex.class)
    public abstract static class ProfiledSubscriptSpecial2Base extends RNode {

        protected final boolean inReplacement;

        @Child protected AccessSpecial2 defaultAccessNode;

        protected ProfiledSubscriptSpecial2Base(boolean inReplacement) {
            this.inReplacement = inReplacement;
        }

        protected AccessSpecial2 createAccessNode() {
            throw RInternalError.shouldNotReachHere();
        }

        @Specialization(limit = "getVectorAccessCacheSize()", guards = "vector.getClass() == clazz")
        public Object access(RAbstractVector vector, Object index1, Object index2,
                        @Cached("vector.getClass()") Class<?> clazz,
                        @Cached("createAccessNode()") AccessSpecial2 accessNodeCached) {
            return accessNodeCached.execute(clazz.cast(vector), index1, index2);
        }

        @Specialization(replaces = "access")
        public Object accessGeneric(Object vector, Object index1, Object index2) {
            if (defaultAccessNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                defaultAccessNode = insert(createAccessNode());
            }
            return defaultAccessNode.execute(vector, index1, index2);
        }
    }

    public abstract static class ProfiledSubscriptSpecial2 extends ProfiledSubscriptSpecial2Base {

        protected ProfiledSubscriptSpecial2(boolean inReplacement) {
            super(inReplacement);
        }

        @Override
        protected AccessSpecial2 createAccessNode() {
            return SubscriptSpecial2NodeGen.create(inReplacement);
        }
    }

    public abstract static class ProfiledSubsetSpecial2 extends ProfiledSubscriptSpecial2Base {

        protected ProfiledSubsetSpecial2(boolean inReplacement) {
            super(inReplacement);
        }

        @Override
        protected AccessSpecial2 createAccessNode() {
            return SubsetSpecial2NodeGen.create(inReplacement);
        }
    }

    @ImportStatic(DSLConfig.class)
    @NodeChild(value = "vector", type = RNode.class)
    @NodeChild(value = "index", type = ConvertIndex.class)
    @NodeChild(value = "value", type = ConvertValue.class)
    public abstract static class ProfiledUpdateSubscriptSpecialBase extends RNode {

        protected static final int CACHE_LIMIT = 3;
        protected final boolean inReplacement;

        public abstract Object execute(VirtualFrame frame, Object vector, Object index, Object value);

        @Child protected UpdateSubscriptSpecial defaultAccessNode;

        protected ProfiledUpdateSubscriptSpecialBase(boolean inReplacement) {
            this.inReplacement = inReplacement;
        }

        protected UpdateSubscriptSpecial createAccessNode() {
            return UpdateSubscriptSpecialNodeGen.create(inReplacement);
        }

        @Specialization(limit = "getCacheSize(CACHE_LIMIT)", guards = "vector.getClass() == clazz")
        public Object access(VirtualFrame frame, Object vector, Object index, Object value,
                        @Cached("vector.getClass()") Class<?> clazz,
                        @Cached("createAccessNode()") UpdateSubscriptSpecial accessNodeCached) {
            return accessNodeCached.execute(frame, clazz.cast(vector), index, value);
        }

        @Specialization(replaces = "access")
        public Object accessGeneric(VirtualFrame frame, Object vector, Object index, Object value) {
            if (defaultAccessNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                defaultAccessNode = insert(createAccessNode());
            }
            return defaultAccessNode.execute(frame, vector, index, value);
        }
    }

    @ImportStatic(DSLConfig.class)
    @NodeChild(value = "vector", type = RNode.class)
    @NodeChild(value = "index1", type = ConvertIndex.class)
    @NodeChild(value = "index2", type = ConvertIndex.class)
    @NodeChild(value = "value", type = ConvertValue.class)
    public abstract static class ProfiledUpdateSubscriptSpecial2 extends RNode {

        protected static final int CACHE_LIMIT = 3;
        protected final boolean inReplacement;

        public abstract Object execute(VirtualFrame frame, Object vector, Object index1, Object index2, Object value);

        @Child protected UpdateSubscriptSpecial2 defaultAccessNode;

        protected ProfiledUpdateSubscriptSpecial2(boolean inReplacement) {
            this.inReplacement = inReplacement;
        }

        protected UpdateSubscriptSpecial2 createAccessNode() {
            return UpdateSubscriptSpecial2NodeGen.create(inReplacement);
        }

        @Specialization(limit = "getCacheSize(CACHE_LIMIT)", guards = "vector.getClass() == clazz")
        public Object access(VirtualFrame frame, Object vector, Object index1, Object index2, Object value,
                        @Cached("vector.getClass()") Class<?> clazz,
                        @Cached("createAccessNode()") UpdateSubscriptSpecial2 accessNodeCached) {
            return accessNodeCached.execute(frame, clazz.cast(vector), index1, index2, value);
        }

        @Specialization(replaces = "access")
        public Object accessGeneric(VirtualFrame frame, Object vector, Object index1, Object index2, Object value) {
            if (defaultAccessNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                defaultAccessNode = insert(createAccessNode());
            }
            return defaultAccessNode.execute(frame, vector, index1, index2, value);
        }
    }
}
