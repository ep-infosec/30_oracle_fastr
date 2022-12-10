/*
 * This program is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation; either version 2 of the License, or (at your option)
 * any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License for
 * more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program; if not, write to the Free Software Foundation, Inc., 51
 * Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 *
 * Copyright (c) 2014, Purdue University
 * Copyright (c) 2014, 2021, Oracle and/or its affiliates
 *
 * All rights reserved.
 */

package com.oracle.truffle.r.nodes.builtin.base;

import static com.oracle.truffle.r.nodes.builtin.CastBuilder.Predef.foreign;
import static com.oracle.truffle.r.nodes.builtin.CastBuilder.Predef.missingValue;
import static com.oracle.truffle.r.runtime.builtins.RBehavior.PURE;
import static com.oracle.truffle.r.runtime.builtins.RBuiltinKind.PRIMITIVE;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.r.nodes.binary.CastTypeNode;
import com.oracle.truffle.r.nodes.binary.CastTypeNodeGen;
import com.oracle.truffle.r.nodes.builtin.RBuiltinNode;
import com.oracle.truffle.r.nodes.unary.TypeofNode;
import com.oracle.truffle.r.runtime.DSLConfig;
import com.oracle.truffle.r.runtime.RError;
import com.oracle.truffle.r.runtime.RType;
import com.oracle.truffle.r.runtime.builtins.RBuiltin;
import com.oracle.truffle.r.runtime.data.RDataFactory;
import com.oracle.truffle.r.runtime.data.RExternalPtr;
import com.oracle.truffle.r.runtime.data.RFunction;
import com.oracle.truffle.r.runtime.data.RNull;
import com.oracle.truffle.r.runtime.data.RS4Object;
import com.oracle.truffle.r.runtime.data.RSharingAttributeStorage;
import com.oracle.truffle.r.runtime.data.RStringVector;
import com.oracle.truffle.r.runtime.data.RSymbol;
import com.oracle.truffle.r.runtime.data.model.RAbstractContainer;
import com.oracle.truffle.r.runtime.data.model.RAbstractVector;
import com.oracle.truffle.r.runtime.data.nodes.attributes.SpecialAttributesFunctions.GetClassAttributeNode;
import com.oracle.truffle.r.runtime.data.nodes.attributes.SpecialAttributesFunctions.SetClassAttributeNode;
import com.oracle.truffle.r.runtime.data.nodes.attributes.TypeFromModeNode;
import com.oracle.truffle.r.runtime.env.REnvironment;

@ImportStatic(DSLConfig.class)
@RBuiltin(name = "class<-", kind = PRIMITIVE, parameterNames = {"x", "value"}, behavior = PURE)
public abstract class UpdateClass extends RBuiltinNode.Arg2 {

    protected static final int CACHE_LIMIT = 2;

    @Child private CastTypeNode castTypeNode;
    @Child private TypeofNode typeof;
    @Child private SetClassAttributeNode setClassAttrNode = SetClassAttributeNode.create();

    static {
        Casts casts = new Casts(UpdateClass.class);
        casts.arg("x").mustBe(missingValue().not(), RError.Message.ARGUMENT_EMPTY, 1).mustBe(foreign().not(), RError.Message.INVALID_TYPE_ARGUMENT, "polyglot.value");
        casts.arg("value").mustBe(missingValue().not(), RError.Message.ARGUMENT_EMPTY, 2).asStringVector();
    }

    @Specialization
    @TruffleBoundary
    protected Object setClass(RAbstractContainer arg, @SuppressWarnings("unused") RNull className) {
        RAbstractContainer result = reuseNonShared(arg);
        setClassAttrNode.reset(result);
        return result;
    }

    @Specialization
    protected Object setClass(@SuppressWarnings("unused") RNull arg, Object className) {
        if (className != RNull.instance) {
            throw error(RError.Message.SET_ATTRIBUTES_ON_NULL);
        }
        return RNull.instance;
    }

    @Specialization(limit = "getCacheSize(CACHE_LIMIT)", guards = "cachedClassName == className")
    protected Object setClassCached(RAbstractContainer arg, @SuppressWarnings("unused") String className,
                    @Cached("className") String cachedClassName,
                    @Cached("fromMode(className)") RType cachedMode,
                    @Cached("create()") GetClassAttributeNode getClassNode) {
        return setClassInternal(arg, cachedClassName, cachedMode, getClassNode);
    }

    @Specialization(replaces = "setClassCached")
    protected Object setClass(RAbstractContainer arg, String className,
                    @Cached("create()") TypeFromModeNode typeFromMode,
                    @Cached("create()") GetClassAttributeNode getClassNode) {
        RType mode = typeFromMode.execute(className);
        return setClassInternal(arg, className, mode, getClassNode);
    }

    private Object setClassInternal(RAbstractContainer arg, String className, RType mode, GetClassAttributeNode getClassNode) {
        if (!getClassNode.isObject(arg)) {
            initTypeof();
            RType argType = typeof.execute(arg);
            if (argType.getClazz().equals(className) || (mode == RType.Double && (argType == RType.Integer || argType == RType.Double))) {
                // "explicit" attribute might have been set (e.g. by oldClass<-)
                return setClass(arg, RNull.instance);
            }
        }
        if (mode != null) {
            initCastTypeNode();
            Object result = castTypeNode.execute(arg, mode);
            if (result != null) {
                return setClass((RAbstractVector) result, RNull.instance);
            }
        }

        RAbstractContainer result = reuseNonShared(arg);
        if (result instanceof RAbstractVector) {
            RAbstractVector resultVector = (RAbstractVector) result;
            if (RType.Matrix.getName().equals(className)) {
                if (resultVector.isMatrix()) {
                    return setClass(resultVector, RNull.instance);
                }
                CompilerDirectives.transferToInterpreter();
                int[] dimensions = resultVector.getDimensions();
                throw error(RError.Message.NOT_A_MATRIX_UPDATE_CLASS, dimensions == null ? 0 : dimensions.length);
            }
            if (RType.Array.getName().equals(className)) {
                if (resultVector.isArray()) {
                    return setClass(resultVector, RNull.instance);
                }
                CompilerDirectives.transferToInterpreter();
                throw error(RError.Message.NOT_ARRAY_UPDATE_CLASS);
            }
        }

        setClassAttrNode.setAttr(result, RDataFactory.createStringVector(className));
        return result;
    }

    @Specialization(guards = "className.getLength() == 1")
    @TruffleBoundary
    protected Object setClassLengthOne(RAbstractContainer arg, RStringVector className,
                    @Cached("create()") TypeFromModeNode typeFromMode,
                    @Cached("create()") GetClassAttributeNode getClassNode) {
        RType mode = typeFromMode.execute(className.getDataAt(0));
        return setClassInternal(arg, className.getDataAt(0), mode, getClassNode);
    }

    @Specialization(guards = "className.getLength() != 1")
    @TruffleBoundary
    protected Object setClass(RAbstractContainer arg, RStringVector className) {
        RAbstractContainer result = reuseNonShared(arg);
        setClassAttrNode.setAttr(result, className);
        return result;
    }

    @Specialization
    protected Object setClass(RFunction arg, RStringVector className) {
        setClassAttrNode.setAttr(arg, className.materialize());
        return arg;
    }

    @Specialization
    protected Object setClass(RFunction arg, @SuppressWarnings("unused") RNull className) {
        setClassAttrNode.reset(arg);
        return arg;
    }

    @Specialization
    protected Object setClass(REnvironment arg, RStringVector className) {
        setClassAttrNode.setAttr(arg, className.materialize());
        return arg;
    }

    @Specialization
    protected Object setClass(REnvironment arg, @SuppressWarnings("unused") RNull className) {
        setClassAttrNode.reset(arg);
        return arg;
    }

    @Specialization
    protected Object setClass(RSymbol arg, RStringVector className) {
        setClassAttrNode.setAttr(arg, className.materialize());
        return arg;
    }

    @Specialization
    protected Object setClass(RSymbol arg, @SuppressWarnings("unused") RNull className) {
        setClassAttrNode.reset(arg);
        return arg;
    }

    @Specialization
    protected Object setClass(RExternalPtr arg, RStringVector className) {
        setClassAttrNode.setAttr(arg, className.materialize());
        return arg;
    }

    @Specialization
    protected Object setClass(RExternalPtr arg, @SuppressWarnings("unused") RNull className) {
        setClassAttrNode.reset(arg);
        return arg;
    }

    @Specialization
    protected Object setClass(RS4Object arg, RStringVector className) {
        return setClassOnObject(arg, className);
    }

    @Specialization
    protected Object setClass(RS4Object arg, @SuppressWarnings("unused") RNull className) {
        return setClassOnObject(arg, null);
    }

    private RS4Object setClassOnObject(RS4Object object, RStringVector classNames) {
        RS4Object result = reuseNonShared(object);
        if (classNames != null) {
            setClassAttrNode.setAttr(result, classNames.materialize());
        } else {
            setClassAttrNode.reset(result);
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private static <T extends RSharingAttributeStorage> T reuseNonShared(T obj) {
        return (T) obj.getNonShared();
    }

    private void initCastTypeNode() {
        if (castTypeNode == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            castTypeNode = insert(CastTypeNodeGen.create());
        }
    }

    private void initTypeof() {
        if (typeof == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            typeof = insert(TypeofNode.create());
        }
    }
}
