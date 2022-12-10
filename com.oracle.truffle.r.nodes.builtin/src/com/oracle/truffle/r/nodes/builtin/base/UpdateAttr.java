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

import static com.oracle.truffle.r.nodes.builtin.CastBuilder.Predef.stringValue;
import static com.oracle.truffle.r.runtime.RError.Message.MUST_BE_NONNULL_STRING;
import static com.oracle.truffle.r.runtime.builtins.RBehavior.PURE;
import static com.oracle.truffle.r.runtime.builtins.RBuiltinKind.PRIMITIVE;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.r.runtime.data.nodes.attributes.RemoveAttributeNode;
import com.oracle.truffle.r.runtime.data.nodes.attributes.SetAttributeNode;
import com.oracle.truffle.r.runtime.data.nodes.attributes.SpecialAttributesFunctions.SetClassAttributeNode;
import com.oracle.truffle.r.runtime.data.nodes.attributes.SpecialAttributesFunctions.SetCommentAttributeNode;
import com.oracle.truffle.r.runtime.data.nodes.attributes.SpecialAttributesFunctions.SetDimAttributeNode;
import com.oracle.truffle.r.runtime.data.nodes.attributes.SpecialAttributesFunctions.SetNamesAttributeNode;
import com.oracle.truffle.r.runtime.data.nodes.attributes.SpecialAttributesFunctions.SetRowNamesAttributeNode;
import com.oracle.truffle.r.runtime.data.nodes.attributes.SpecialAttributesFunctions.SetTspAttributeNode;
import com.oracle.truffle.r.nodes.builtin.RBuiltinNode;
import com.oracle.truffle.r.nodes.unary.CastDoubleNode;
import com.oracle.truffle.r.nodes.unary.CastIntegerNode;
import com.oracle.truffle.r.nodes.unary.CastIntegerNodeGen;
import com.oracle.truffle.r.runtime.nodes.unary.CastToVectorNode;
import com.oracle.truffle.r.runtime.nodes.unary.CastToVectorNodeGen;
import com.oracle.truffle.r.nodes.unary.InternStringNode;
import com.oracle.truffle.r.runtime.RError;
import com.oracle.truffle.r.runtime.RError.Message;
import com.oracle.truffle.r.runtime.RRuntime;
import com.oracle.truffle.r.runtime.Utils;
import com.oracle.truffle.r.runtime.builtins.RBuiltin;
import com.oracle.truffle.r.runtime.data.RAttributable;
import com.oracle.truffle.r.runtime.data.RDataFactory;
import com.oracle.truffle.r.runtime.data.RDoubleVector;
import com.oracle.truffle.r.runtime.data.RNull;
import com.oracle.truffle.r.runtime.data.RSharingAttributeStorage;
import com.oracle.truffle.r.runtime.data.model.RAbstractContainer;
import com.oracle.truffle.r.runtime.data.RIntVector;
import com.oracle.truffle.r.runtime.data.RStringVector;
import com.oracle.truffle.r.runtime.data.model.RAbstractVector;
import com.oracle.truffle.r.runtime.data.nodes.VectorReuse;

@RBuiltin(name = "attr<-", kind = PRIMITIVE, parameterNames = {"x", "which", "value"}, behavior = PURE)
public abstract class UpdateAttr extends RBuiltinNode.Arg3 {

    @Child private SetNamesAttributeNode updateNames;
    @Child private UpdateDimNames updateDimNames;
    @Child private CastIntegerNode castInteger;
    @Child private CastDoubleNode castDouble;
    @Child private CastToVectorNode castVector;
    @Child private SetClassAttributeNode setClassAttrNode;
    @Child private SetRowNamesAttributeNode setRowNamesAttrNode;
    @Child private SetTspAttributeNode setTspAttrNode;
    @Child private SetCommentAttributeNode setCommentAttrNode;
    @Child private SetAttributeNode setGenAttrNode;
    @Child private SetDimAttributeNode setDimNode;

    @Child private InternStringNode intern = InternStringNode.create();

    static {
        Casts casts = new Casts(UpdateAttr.class);
        // Note: cannot check 'attributability' easily because atomic values, e.g int, are not
        // RAttributable.
        casts.arg("x");
        casts.arg("which").defaultError(MUST_BE_NONNULL_STRING, "name").mustBe(stringValue()).asStringVector().findFirst();
    }

    private RAbstractContainer updateNames(RAbstractContainer container, Object o) {
        if (updateNames == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            updateNames = insert(SetNamesAttributeNode.create());
        }
        updateNames.setAttr(container, o);
        return container;
    }

    private RAbstractContainer updateDimNames(RAbstractContainer container, Object o) {
        if (updateDimNames == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            updateDimNames = insert(UpdateDimNamesNodeGen.create());
        }
        return updateDimNames.executeRAbstractContainer(container, o);
    }

    private RIntVector castInteger(RAbstractVector vector) {
        if (castInteger == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            castInteger = insert(CastIntegerNodeGen.create(true, false, false));
        }
        return (RIntVector) castInteger.doCast(vector);
    }

    private RDoubleVector castDouble(Object o) {
        if (castDouble == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            castDouble = insert(CastDoubleNode.createNonPreserving());
        }
        return (RDoubleVector) castDouble.doCast(o);
    }

    private RAbstractVector castVector(Object value) {
        if (castVector == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            castVector = insert(CastToVectorNodeGen.create(false));
        }
        return (RAbstractVector) castVector.doCast(value);
    }

    @Specialization
    protected RNull updateAttr(@SuppressWarnings("unused") RNull nullTarget, @SuppressWarnings("unused") String attrName, @SuppressWarnings("unused") RNull nullAttrVal) {
        return RNull.instance;
    }

    @Specialization(guards = "vectorReuse.supports(container)", limit = "getCacheSize(3)")
    protected RAbstractContainer removeAttr(RAbstractContainer container, String name, RNull value,
                    @Cached("createNonShared(container)") VectorReuse vectorReuse,
                    @Cached("create()") RemoveAttributeNode removeAttrNode) {
        String internedName = intern.execute(name);
        RAbstractContainer result = vectorReuse.getMaterializedResult(container);
        // the name is interned, so identity comparison is sufficient
        if (Utils.identityEquals(internedName, RRuntime.DIM_ATTR_KEY)) {
            if (setDimNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                setDimNode = insert(SetDimAttributeNode.create());
            }
            setDimNode.setDimensions(result, null);
        } else if (Utils.identityEquals(internedName, RRuntime.NAMES_ATTR_KEY)) {
            return updateNames(result, value);
        } else if (Utils.identityEquals(internedName, RRuntime.DIMNAMES_ATTR_KEY)) {
            return updateDimNames(result, value);
        } else if (Utils.identityEquals(internedName, RRuntime.CLASS_ATTR_KEY)) {
            if (setClassAttrNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                setClassAttrNode = insert(SetClassAttributeNode.create());
            }
            setClassAttrNode.reset(result);
            return result;
        } else if (Utils.identityEquals(internedName, RRuntime.ROWNAMES_ATTR_KEY)) {
            if (setRowNamesAttrNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                setRowNamesAttrNode = insert(SetRowNamesAttributeNode.create());
            }
            setRowNamesAttrNode.setRowNames(result, null);
        } else if (Utils.identityEquals(internedName, RRuntime.TSP_ATTR_KEY)) {
            if (setTspAttrNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                setTspAttrNode = insert(SetTspAttributeNode.create());
            }
            setTspAttrNode.setTsp(result, null);
        } else if (Utils.identityEquals(internedName, RRuntime.COMMENT_ATTR_KEY)) {
            if (setCommentAttrNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                setCommentAttrNode = insert(SetCommentAttributeNode.create());
            }
            setCommentAttrNode.setComment(result, null);
        } else if (result.getAttributes() != null) {
            removeAttrNode.execute(result, internedName);
        }
        return result;
    }

    @Specialization(replaces = "removeAttr")
    protected RAbstractContainer removeAttrGeneric(RAbstractContainer container, String name, RNull value,
                    @Cached("createNonSharedGeneric()") VectorReuse vectorReuse,
                    @Cached("create()") RemoveAttributeNode removeAttrNode) {
        return removeAttr(container, name, value, vectorReuse, removeAttrNode);
    }

    @TruffleBoundary
    protected static RStringVector convertClassAttrFromObject(Object value) {
        if (value instanceof RStringVector) {
            return (RStringVector) value;
        } else if (value instanceof RStringVector) {
            return ((RStringVector) value).materialize();
        } else if (value instanceof String) {
            return RDataFactory.createStringVector((String) value);
        } else {
            throw RError.error(RError.SHOW_CALLER, RError.Message.SET_INVALID_ATTR, "class");
        }
    }

    @Specialization(guards = {"!isRNull(value)", "vectorReuse.supports(container)"}, limit = "getCacheSize(3)")
    protected RAbstractContainer updateAttr(RAbstractContainer container, String name, Object value,
                    @Cached("createNonShared(container)") VectorReuse vectorReuse) {
        String internedName = intern.execute(name);
        RAbstractContainer result = vectorReuse.getMaterializedResult(container);
        // the name is interned, so identity comparison is sufficient
        if (Utils.identityEquals(internedName, RRuntime.DIM_ATTR_KEY)) {
            RIntVector dimsVector = castInteger(castVector(value));
            if (dimsVector.getLength() == 0) {
                throw error(RError.Message.LENGTH_ZERO_DIM_INVALID);
            }
            if (setDimNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                setDimNode = insert(SetDimAttributeNode.create());
            }
            setDimNode.setDimensions(result, dimsVector.materialize().getDataCopy());
        } else if (Utils.identityEquals(internedName, RRuntime.NAMES_ATTR_KEY)) {
            return updateNames(result, value);
        } else if (Utils.identityEquals(internedName, RRuntime.DIMNAMES_ATTR_KEY)) {
            return updateDimNames(result, value);
        } else if (Utils.identityEquals(internedName, RRuntime.CLASS_ATTR_KEY)) {
            if (setClassAttrNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                setClassAttrNode = insert(SetClassAttributeNode.create());
            }
            setClassAttrNode.setAttr(result, convertClassAttrFromObject(value));
            return result;
        } else if (Utils.identityEquals(internedName, RRuntime.ROWNAMES_ATTR_KEY)) {
            if (setRowNamesAttrNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                setRowNamesAttrNode = insert(SetRowNamesAttributeNode.create());
            }
            setRowNamesAttrNode.setRowNames(result, castVector(value));
        } else if (Utils.identityEquals(internedName, RRuntime.TSP_ATTR_KEY)) {
            RDoubleVector tsp = castDouble(castVector(value));
            if (setTspAttrNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                setTspAttrNode = insert(SetTspAttributeNode.create());
            }
            setTspAttrNode.setTsp(result, tsp);
        } else if (Utils.identityEquals(internedName, RRuntime.COMMENT_ATTR_KEY)) {
            if (setCommentAttrNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                setCommentAttrNode = insert(SetCommentAttributeNode.create());
            }
            setCommentAttrNode.setComment(result, value);
        } else {
            // generic attribute
            if (setGenAttrNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                setGenAttrNode = insert(SetAttributeNode.create());
            }
            setGenAttrNode.execute(result, internedName, value);
        }

        return result;
    }

    @Specialization(guards = "!isRNull(value)")
    protected RAbstractContainer updateAttrGeneric(RAbstractContainer container, String name, Object value,
                    @Cached("createNonSharedGeneric()") VectorReuse vectorReuse) {
        return updateAttr(container, name, value, vectorReuse);
    }

    /**
     * All other, non-performance centric, {@link RAttributable} types and {@link RNull}.
     */
    @Specialization(guards = "!isRAbstractContainer(obj)")
    @TruffleBoundary
    protected Object updateAttrOthers(Object obj, Object name, Object value) {
        assert name instanceof String : "casts should not pass anything but String";
        Object object = obj;
        if (RSharingAttributeStorage.isShareable(object)) {
            object = ((RSharingAttributeStorage) object).getNonShared();
        }
        String internedName = intern.execute((String) name);
        if (object instanceof RAttributable) {
            RAttributable attributable = (RAttributable) object;
            if (value == RNull.instance) {
                attributable.removeAttr(internedName);
            } else {
                attributable.setAttr(internedName, value);
            }
            return object;
        } else if (RRuntime.isForeignObject(obj)) {
            throw RError.error(this, Message.OBJ_CANNOT_BE_ATTRIBUTED);
        } else if (obj == RNull.instance) {
            throw RError.error(this, Message.SET_ATTRIBUTES_ON_NULL);
        } else {
            throw RError.nyi(this, "object cannot be attributed: ");
        }
    }
}
