/*
 * Copyright (c) 2014, 2020, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.truffle.r.nodes.builtin.CastBuilder.Predef.size;
import static com.oracle.truffle.r.nodes.builtin.CastBuilder.Predef.stringValue;
import static com.oracle.truffle.r.runtime.RDispatch.INTERNAL_GENERIC;
import static com.oracle.truffle.r.runtime.builtins.RBehavior.PURE;
import static com.oracle.truffle.r.runtime.builtins.RBuiltinKind.INTERNAL;
import static com.oracle.truffle.r.runtime.builtins.RBuiltinKind.PRIMITIVE;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.object.DynamicObject;
import com.oracle.truffle.api.profiles.ConditionProfile;
import com.oracle.truffle.r.runtime.data.nodes.attributes.GetFixedAttributeNode;
import com.oracle.truffle.r.runtime.data.nodes.attributes.SpecialAttributesFunctions.GetClassAttributeNode;
import com.oracle.truffle.r.runtime.data.nodes.attributes.SpecialAttributesFunctions.GetDimAttributeNode;
import com.oracle.truffle.r.nodes.builtin.NodeWithArgumentCasts.Casts;
import com.oracle.truffle.r.nodes.builtin.RBuiltinNode;
import com.oracle.truffle.r.nodes.helpers.InheritsCheckNode;
import com.oracle.truffle.r.nodes.unary.IsFactorNode;
import com.oracle.truffle.r.runtime.RError;
import com.oracle.truffle.r.runtime.RRuntime;
import com.oracle.truffle.r.runtime.RType;
import com.oracle.truffle.r.runtime.builtins.RBuiltin;
import com.oracle.truffle.r.runtime.data.RAttributable;
import com.oracle.truffle.r.runtime.data.RDoubleVector;
import com.oracle.truffle.r.runtime.data.RExpression;
import com.oracle.truffle.r.runtime.data.RExternalPtr;
import com.oracle.truffle.r.runtime.data.RFunction;
import com.oracle.truffle.r.runtime.data.RList;
import com.oracle.truffle.r.runtime.data.RNull;
import com.oracle.truffle.r.runtime.data.RPairList;
import com.oracle.truffle.r.runtime.data.RSymbol;
import com.oracle.truffle.r.runtime.data.model.RAbstractAtomicVector;
import com.oracle.truffle.r.runtime.data.RComplexVector;
import com.oracle.truffle.r.runtime.data.RIntVector;
import com.oracle.truffle.r.runtime.data.model.RAbstractListBaseVector;
import com.oracle.truffle.r.runtime.data.RLogicalVector;
import com.oracle.truffle.r.runtime.data.RRawVector;
import com.oracle.truffle.r.runtime.data.RStringVector;
import com.oracle.truffle.r.runtime.data.model.RAbstractVector;

/**
 * Handles all builtin functions of the form {@code is.xxx}, where is {@code xxx} is a "type".
 */
public class IsTypeFunctions {

    protected static Casts createCasts(Class<? extends RBuiltinNode> extCls) {
        Casts casts = new Casts(extCls);
        casts.arg("x").mustNotBeMissing(RError.Message.ARGUMENT_MISSING, "x");
        return casts;
    }

    @RBuiltin(name = "is.array", kind = PRIMITIVE, parameterNames = {"x"}, dispatch = INTERNAL_GENERIC, behavior = PURE)
    public abstract static class IsArray extends RBuiltinNode.Arg1 {

        static {
            createCasts(IsArray.class);
        }

        private final ConditionProfile isArrayProfile = ConditionProfile.createBinaryProfile();
        @Child private GetDimAttributeNode getDim = GetDimAttributeNode.create();

        public abstract byte execute(Object value);

        @Specialization
        protected byte isType(RAbstractVector vector) {
            return RRuntime.asLogical(isArrayProfile.profile(getDim.isArray(vector)));
        }

        @Fallback
        protected byte isType(@SuppressWarnings("unused") Object value) {
            return RRuntime.LOGICAL_FALSE;
        }
    }

    @ImportStatic(RRuntime.class)
    @RBuiltin(name = "is.recursive", kind = PRIMITIVE, parameterNames = {"x"}, behavior = PURE)
    public abstract static class IsRecursive extends RBuiltinNode.Arg1 {

        static {
            createCasts(IsRecursive.class);
        }

        @Specialization
        protected byte isRecursive(@SuppressWarnings("unused") RNull arg) {
            return RRuntime.LOGICAL_FALSE;
        }

        @Specialization(guards = {"!isRList(arg)", "!isRExpression(arg)"})
        protected byte isRecursive(@SuppressWarnings("unused") RAbstractVector arg) {
            return RRuntime.LOGICAL_FALSE;
        }

        @Specialization
        protected byte isRecursive(@SuppressWarnings("unused") RAbstractListBaseVector arg) {
            return RRuntime.LOGICAL_TRUE;
        }

        @Specialization(guards = "isForeignObject(obj)")
        protected byte isRecursive(@SuppressWarnings("unused") TruffleObject obj) {
            return RRuntime.LOGICAL_FALSE;
        }

        @Specialization
        protected byte isRecursive(@SuppressWarnings("unused") RExternalPtr obj) {
            return RRuntime.LOGICAL_FALSE;
        }

        @Specialization
        protected byte isRecursive(@SuppressWarnings("unused") RSymbol symbol) {
            return RRuntime.LOGICAL_FALSE;
        }

        @Fallback
        protected byte isRecursiveFallback(@SuppressWarnings("unused") Object value) {
            return RRuntime.LOGICAL_TRUE;
        }
    }

    @RBuiltin(name = "is.atomic", kind = PRIMITIVE, parameterNames = {"x"}, behavior = PURE)
    public abstract static class IsAtomic extends RBuiltinNode.Arg1 {

        static {
            createCasts(IsAtomic.class);
        }

        @Specialization
        protected byte isAtomic(@SuppressWarnings("unused") RNull arg) {
            return RRuntime.LOGICAL_TRUE;
        }

        @Specialization
        protected byte isAtomic(@SuppressWarnings("unused") RAbstractAtomicVector arg) {
            return RRuntime.LOGICAL_TRUE;
        }

        @Fallback
        protected byte isType(@SuppressWarnings("unused") Object value) {
            return RRuntime.LOGICAL_FALSE;
        }
    }

    @RBuiltin(name = "is.call", kind = PRIMITIVE, parameterNames = {"x"}, behavior = PURE)
    public abstract static class IsCall extends RBuiltinNode.Arg1 {

        static {
            Casts.noCasts(IsCall.class);
        }

        @Specialization
        protected byte isType(RPairList lang) {
            return RRuntime.asLogical(lang.isLanguage());
        }

        @Fallback
        protected byte isType(@SuppressWarnings("unused") Object value) {
            return RRuntime.LOGICAL_FALSE;
        }
    }

    @RBuiltin(name = "is.character", kind = PRIMITIVE, parameterNames = {"x"}, behavior = PURE)
    public abstract static class IsCharacter extends RBuiltinNode.Arg1 {

        static {
            createCasts(IsCharacter.class);
        }

        @Specialization
        protected byte isType(@SuppressWarnings("unused") RStringVector value) {
            return RRuntime.LOGICAL_TRUE;
        }

        @Fallback
        protected byte isType(@SuppressWarnings("unused") Object value) {
            return RRuntime.LOGICAL_FALSE;
        }
    }

    @RBuiltin(name = "is.complex", kind = PRIMITIVE, parameterNames = {"x"}, behavior = PURE)
    public abstract static class IsComplex extends RBuiltinNode.Arg1 {

        static {
            createCasts(IsComplex.class);
        }

        @Specialization
        protected byte isType(@SuppressWarnings("unused") RComplexVector value) {
            return RRuntime.LOGICAL_TRUE;
        }

        @Fallback
        protected byte isType(@SuppressWarnings("unused") Object value) {
            return RRuntime.LOGICAL_FALSE;
        }
    }

    @RBuiltin(name = "is.double", kind = PRIMITIVE, parameterNames = {"x"}, behavior = PURE)
    public abstract static class IsDouble extends RBuiltinNode.Arg1 {

        static {
            createCasts(IsDouble.class);
        }

        @Specialization
        protected byte isType(@SuppressWarnings("unused") RDoubleVector value) {
            return RRuntime.LOGICAL_TRUE;
        }

        @Fallback
        protected byte isType(@SuppressWarnings("unused") Object value) {
            return RRuntime.LOGICAL_FALSE;
        }
    }

    @RBuiltin(name = "is.expression", kind = PRIMITIVE, parameterNames = {"x"}, behavior = PURE)
    public abstract static class IsExpression extends RBuiltinNode.Arg1 {

        static {
            createCasts(IsExpression.class);
        }

        @Specialization
        protected byte isType(@SuppressWarnings("unused") RExpression expr) {
            return RRuntime.LOGICAL_TRUE;
        }

        @Fallback
        protected byte isType(@SuppressWarnings("unused") Object value) {
            return RRuntime.LOGICAL_FALSE;
        }
    }

    @ImportStatic(RRuntime.class)
    @RBuiltin(name = "is.function", kind = PRIMITIVE, parameterNames = {"x"}, behavior = PURE)
    public abstract static class IsFunction extends RBuiltinNode.Arg1 {

        static {
            createCasts(IsFunction.class);
        }

        @Specialization
        protected byte isFunction(@SuppressWarnings("unused") RFunction value) {
            return RRuntime.LOGICAL_TRUE;
        }

        @Specialization(guards = {"isForeignObject(value)", "interop.isExecutable(value)"}, limit = "getInteropLibraryCacheSize()")
        protected byte isFunction(@SuppressWarnings("unused") TruffleObject value,
                        @SuppressWarnings("unused") @CachedLibrary("value") InteropLibrary interop) {
            return RRuntime.LOGICAL_TRUE;
        }

        @Fallback
        protected byte isFunction(@SuppressWarnings("unused") Object value) {
            return RRuntime.LOGICAL_FALSE;
        }
    }

    @RBuiltin(name = "is.integer", kind = PRIMITIVE, parameterNames = {"x"}, behavior = PURE)
    public abstract static class IsInteger extends RBuiltinNode.Arg1 {

        static {
            createCasts(IsInteger.class);
        }

        @Specialization
        protected byte isInteger(@SuppressWarnings("unused") int value) {
            return RRuntime.LOGICAL_TRUE;
        }

        @Specialization
        protected byte isInteger(RIntVector value,
                        @Cached("createIsFactorNode()") IsFactorNode isFactorNode,
                        @Cached("createBinaryProfile()") ConditionProfile isFactor) {
            if (isFactor.profile(isFactorNode.executeIsFactor(value))) {
                return RRuntime.LOGICAL_FALSE;
            } else {
                return RRuntime.LOGICAL_TRUE;
            }
        }

        @Fallback
        protected byte isInteger(@SuppressWarnings("unused") Object value) {
            return RRuntime.LOGICAL_FALSE;
        }

        protected IsFactorNode createIsFactorNode() {
            return new IsFactorNode();
        }
    }

    @RBuiltin(name = "is.language", kind = PRIMITIVE, parameterNames = {"x"}, behavior = PURE)
    public abstract static class IsLanguage extends RBuiltinNode.Arg1 {

        static {
            createCasts(IsLanguage.class);
        }

        @Specialization
        protected byte isType(@SuppressWarnings("unused") RSymbol value) {
            return RRuntime.LOGICAL_TRUE;
        }

        @Specialization
        protected byte isType(@SuppressWarnings("unused") RExpression value) {
            return RRuntime.LOGICAL_TRUE;
        }

        @Specialization
        protected byte isType(RPairList value) {
            return RRuntime.asLogical(value.isLanguage());
        }

        @Fallback
        protected byte isType(@SuppressWarnings("unused") Object value) {
            return RRuntime.LOGICAL_FALSE;
        }
    }

    @RBuiltin(name = "is.list", kind = PRIMITIVE, parameterNames = {"x"}, behavior = PURE)
    public abstract static class IsList extends RBuiltinNode.Arg1 {

        static {
            createCasts(IsList.class);
        }

        public abstract byte execute(Object value);

        @Specialization
        protected byte isType(@SuppressWarnings("unused") RList value) {
            return RRuntime.LOGICAL_TRUE;
        }

        @Specialization
        protected byte isType(RPairList pl) {
            return RRuntime.asLogical(!pl.isLanguage());
        }

        @Fallback
        protected byte isType(@SuppressWarnings("unused") Object value) {
            return RRuntime.LOGICAL_FALSE;
        }
    }

    @RBuiltin(name = "is.logical", kind = PRIMITIVE, parameterNames = {"x"}, behavior = PURE)
    public abstract static class IsLogical extends RBuiltinNode.Arg1 {

        static {
            createCasts(IsLogical.class);
        }

        @Specialization
        protected byte isType(@SuppressWarnings("unused") RLogicalVector value) {
            return RRuntime.LOGICAL_TRUE;
        }

        @Fallback
        protected byte isType(@SuppressWarnings("unused") Object value) {
            return RRuntime.LOGICAL_FALSE;
        }
    }

    @RBuiltin(name = "is.matrix", kind = PRIMITIVE, parameterNames = {"x"}, dispatch = INTERNAL_GENERIC, behavior = PURE)
    public abstract static class IsMatrix extends RBuiltinNode.Arg1 {

        private final ConditionProfile isMatrixProfile = ConditionProfile.createBinaryProfile();
        @Child private GetDimAttributeNode getDim = GetDimAttributeNode.create();

        static {
            createCasts(IsMatrix.class);
        }

        @Specialization
        protected byte isType(RAbstractVector vector) {
            return RRuntime.asLogical(isMatrixProfile.profile(getDim.isMatrix(vector)));
        }

        @Fallback
        protected byte isType(@SuppressWarnings("unused") Object value) {
            return RRuntime.LOGICAL_FALSE;
        }
    }

    @RBuiltin(name = "is.name", aliases = {"is.symbol"}, kind = PRIMITIVE, parameterNames = {"x"}, behavior = PURE)
    public abstract static class IsName extends RBuiltinNode.Arg1 {

        static {
            createCasts(IsName.class);
        }

        @Specialization
        protected byte isType(@SuppressWarnings("unused") RSymbol value) {
            return RRuntime.LOGICAL_TRUE;
        }

        @Fallback
        protected byte isType(@SuppressWarnings("unused") Object value) {
            return RRuntime.LOGICAL_FALSE;
        }
    }

    @RBuiltin(name = "is.numeric", kind = PRIMITIVE, parameterNames = {"x"}, dispatch = INTERNAL_GENERIC, behavior = PURE)
    public abstract static class IsNumeric extends RBuiltinNode.Arg1 {

        static {
            createCasts(IsNumeric.class);
        }

        @Child private InheritsCheckNode inheritsCheck = InheritsCheckNode.create(RRuntime.CLASS_FACTOR);

        protected boolean isFactor(Object o) {
            return inheritsCheck.execute(o);
        }

        @Specialization
        protected byte isType(RIntVector value,
                        @Cached("createBinaryProfile()") ConditionProfile profile) {
            return RRuntime.asLogical(!profile.profile(isFactor(value)));
        }

        @Specialization
        protected byte isType(@SuppressWarnings("unused") RDoubleVector value) {
            return RRuntime.LOGICAL_TRUE;
        }

        @Fallback
        protected byte isType(@SuppressWarnings("unused") Object value) {
            return RRuntime.LOGICAL_FALSE;
        }
    }

    @ImportStatic({RRuntime.class})
    @RBuiltin(name = "is.null", kind = PRIMITIVE, parameterNames = {"x"}, behavior = PURE)
    public abstract static class IsNull extends RBuiltinNode.Arg1 {

        static {
            createCasts(IsNull.class);
        }

        @Specialization
        protected byte isType(@SuppressWarnings("unused") RNull value) {
            return RRuntime.LOGICAL_TRUE;
        }

        @Specialization(guards = "isForeignObject(value)", limit = "getInteropLibraryCacheSize()")
        protected byte isType(Object value,
                        @CachedLibrary("value") InteropLibrary interop) {
            return RRuntime.asLogical(interop.isNull(value));
        }

        @Fallback
        protected byte isType(@SuppressWarnings("unused") Object value) {
            return RRuntime.LOGICAL_FALSE;
        }
    }

    /**
     * The specification is not quite what you might expect. Several builtin types, e.g.,
     * {@code expression} respond to {@code class(e)} but return {@code FALSE} to {@code is.object}.
     * Essentially, this method should only return {@code TRUE} if a {@code class} attribute has
     * been added explicitly to the object. If the attribute is removed, it should return
     * {@code FALSE}.
     */
    @ImportStatic(RRuntime.class)
    @RBuiltin(name = "is.object", kind = PRIMITIVE, parameterNames = {"x"}, behavior = PURE)
    public abstract static class IsObject extends RBuiltinNode.Arg1 {

        @Child private GetClassAttributeNode getClassNode = GetClassAttributeNode.create();

        static {
            Casts.noCasts(IsObject.class);
        }

        public abstract byte execute(Object value);

        @Specialization
        protected byte isObject(RAttributable arg) {
            return RRuntime.asLogical(getClassNode.isObject(arg));
        }

        @Specialization(guards = "isForeignObject(arg)")
        protected byte isObject(@SuppressWarnings("unused") TruffleObject arg) {
            return RRuntime.LOGICAL_TRUE;
        }

        @Fallback
        protected byte isType(@SuppressWarnings("unused") Object value) {
            return RRuntime.LOGICAL_FALSE;
        }
    }

    @RBuiltin(name = "is.pairlist", kind = PRIMITIVE, parameterNames = {"x"}, behavior = PURE)
    public abstract static class IsPairList extends RBuiltinNode.Arg1 {

        static {
            createCasts(IsPairList.class);
        }

        @Specialization
        protected byte isType(@SuppressWarnings("unused") RNull value) {
            return RRuntime.LOGICAL_TRUE;
        }

        @Specialization
        protected byte isType(RPairList value) {
            return RRuntime.asLogical(!value.isLanguage());
        }

        @Fallback
        protected byte isType(@SuppressWarnings("unused") Object value) {
            return RRuntime.LOGICAL_FALSE;
        }
    }

    @RBuiltin(name = "is.raw", kind = PRIMITIVE, parameterNames = {"x"}, behavior = PURE)
    public abstract static class IsRaw extends RBuiltinNode.Arg1 {

        static {
            createCasts(IsRaw.class);
        }

        @Specialization
        protected byte isType(@SuppressWarnings("unused") RRawVector value) {
            return RRuntime.LOGICAL_TRUE;
        }

        @Fallback
        protected byte isType(@SuppressWarnings("unused") Object value) {
            return RRuntime.LOGICAL_FALSE;
        }
    }

    @RBuiltin(name = "is.vector", kind = INTERNAL, parameterNames = {"x", "mode"}, behavior = PURE)
    public abstract static class IsVector extends RBuiltinNode.Arg2 {

        private final ConditionProfile attrNull = ConditionProfile.createBinaryProfile();
        private final ConditionProfile attrEmpty = ConditionProfile.createBinaryProfile();
        private final ConditionProfile attrNames = ConditionProfile.createBinaryProfile();
        @Child private GetFixedAttributeNode namesGetter = GetFixedAttributeNode.createNames();

        static {
            Casts casts = new Casts(IsVector.class);
            casts.arg("x").mustNotBeMissing(RError.Message.ARGUMENT_MISSING, "x");
            casts.arg("mode").defaultError(RError.Message.INVALID_ARGUMENT, "mode").mustBe(stringValue()).asStringVector().mustBe(size(1)).findFirst();
        }

        @TruffleBoundary
        protected static RType typeFromMode(String mode) {
            return RType.fromMode(mode, true);
        }

        @Specialization(limit = "getCacheSize(5)", guards = "cachedMode == mode")
        protected byte isVectorCached(RAbstractVector x, @SuppressWarnings("unused") String mode,
                        @Cached("mode") @SuppressWarnings("unused") String cachedMode,
                        @Cached("typeFromMode(mode)") RType type) {
            if (namesOnlyOrNoAttr(x) && (type == RType.Any || typesMatch(type, x.getRType()))) {
                return RRuntime.LOGICAL_TRUE;
            } else {
                return RRuntime.LOGICAL_FALSE;
            }
        }

        @Specialization(replaces = "isVectorCached")
        protected byte isVector(RAbstractVector x, String mode) {
            return isVectorCached(x, mode, mode, typeFromMode(mode));
        }

        @Fallback
        protected byte isVector(@SuppressWarnings("unused") Object x, @SuppressWarnings("unused") Object mode) {
            return RRuntime.LOGICAL_FALSE;
        }

        private static boolean typesMatch(RType expected, RType actual) {
            return expected == RType.Numeric ? actual == RType.Integer || actual == RType.Double : actual == expected;
        }

        private boolean namesOnlyOrNoAttr(RAbstractVector x) {
            DynamicObject attributes = x.getAttributes();
            if (attrNull.profile(attributes == null) || attrEmpty.profile(attributes.getShape().getPropertyCount() == 0)) {
                return true;
            } else {
                return attributes.getShape().getPropertyCount() == 1 && attrNames.profile(namesGetter.execute(x) != null);
            }
        }
    }
}
