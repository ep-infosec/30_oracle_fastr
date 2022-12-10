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

import static com.oracle.truffle.r.nodes.builtin.CastBuilder.Predef.instanceOf;
import static com.oracle.truffle.r.nodes.builtin.CastBuilder.Predef.nullValue;
import static com.oracle.truffle.r.runtime.RError.Message.ATTRIBUTES_LIST_OR_NULL;
import static com.oracle.truffle.r.runtime.builtins.RBehavior.PURE;
import static com.oracle.truffle.r.runtime.builtins.RBuiltinKind.PRIMITIVE;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.profiles.ConditionProfile;
import com.oracle.truffle.r.runtime.data.nodes.attributes.RemoveAttributeNode;
import com.oracle.truffle.r.runtime.data.nodes.attributes.SetAttributeNode;
import com.oracle.truffle.r.runtime.data.nodes.attributes.SpecialAttributesFunctions;
import com.oracle.truffle.r.runtime.data.nodes.attributes.SpecialAttributesFunctions.GetNamesAttributeNode;
import com.oracle.truffle.r.runtime.data.nodes.attributes.SpecialAttributesFunctions.SetClassAttributeNode;
import com.oracle.truffle.r.runtime.data.nodes.attributes.SpecialAttributesFunctions.SetDimAttributeNode;
import com.oracle.truffle.r.runtime.data.nodes.attributes.SpecialAttributesFunctions.SetRowNamesAttributeNode;
import com.oracle.truffle.r.nodes.builtin.RBuiltinNode;
import com.oracle.truffle.r.nodes.unary.CastDoubleNode;
import com.oracle.truffle.r.nodes.unary.CastIntegerNode;
import com.oracle.truffle.r.nodes.unary.CastIntegerNodeGen;
import com.oracle.truffle.r.runtime.nodes.unary.CastToVectorNode;
import com.oracle.truffle.r.runtime.nodes.unary.CastToVectorNodeGen;
import com.oracle.truffle.r.nodes.unary.GetNonSharedNode;
import com.oracle.truffle.r.runtime.RError;
import com.oracle.truffle.r.runtime.RRuntime;
import com.oracle.truffle.r.runtime.Utils;
import com.oracle.truffle.r.runtime.builtins.RBuiltin;
import com.oracle.truffle.r.runtime.data.RAttributable;
import com.oracle.truffle.r.runtime.data.RComplex;
import com.oracle.truffle.r.runtime.data.RDataFactory;
import com.oracle.truffle.r.runtime.data.RDoubleVector;
import com.oracle.truffle.r.runtime.data.RList;
import com.oracle.truffle.r.runtime.data.RNull;
import com.oracle.truffle.r.runtime.data.RRaw;
import com.oracle.truffle.r.runtime.data.RSharingAttributeStorage;
import com.oracle.truffle.r.runtime.data.RStringVector;
import com.oracle.truffle.r.runtime.data.model.RAbstractContainer;
import com.oracle.truffle.r.runtime.data.RIntVector;
import com.oracle.truffle.r.runtime.data.model.RAbstractVector;

@RBuiltin(name = "attributes<-", kind = PRIMITIVE, parameterNames = {"obj", "value"}, behavior = PURE)
public abstract class UpdateAttributes extends RBuiltinNode.Arg2 {

    @Child private GetNamesAttributeNode getNamesNode = GetNamesAttributeNode.create();
    @Child private UpdateNames updateNames;
    @Child private UpdateDimNames updateDimNames;
    @Child private CastIntegerNode castInteger;
    @Child private CastDoubleNode castDouble;
    @Child private CastToVectorNode castVector;
    @Child private SetAttributeNode setAttrNode;
    @Child private SetClassAttributeNode setClassNode;
    @Child private SetDimAttributeNode setDimNode;
    @Child private SetRowNamesAttributeNode setRowNamesNode;
    @Child private SpecialAttributesFunctions.SetTspAttributeNode setTspAttrNode;
    @Child private SpecialAttributesFunctions.SetCommentAttributeNode setCommentAttrNode;
    @Child private RemoveAttributeNode removeAttrNode;

    static {
        Casts casts = new Casts(UpdateAttributes.class);
        // Note: cannot check 'attributability' easily because atomic values, e.g int, are not
        // RAttributable.
        casts.arg("obj"); // by default disallows RNull
        casts.arg("value").mustBe(nullValue().or(instanceOf(RList.class)), ATTRIBUTES_LIST_OR_NULL);
    }

    // it's OK for the following two methods to update attributes in-place as the container has been
    // already materialized to non-shared

    private RAbstractContainer updateNames(RAbstractContainer container, Object o) {
        if (updateNames == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            updateNames = insert(UpdateNamesNodeGen.create());
        }
        return (RAbstractContainer) updateNames.executeStringVector(null, container, o);
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

    private RDoubleVector castDouble(RAbstractVector vector) {
        if (castDouble == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            castDouble = insert(CastDoubleNode.createNonPreserving());
        }
        return (RDoubleVector) castDouble.doCast(vector);
    }

    private RAbstractVector castVector(Object value) {
        if (castVector == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            castVector = insert(CastToVectorNodeGen.create(false));
        }
        return (RAbstractVector) castVector.doCast(value);
    }

    @Specialization
    protected RAbstractContainer updateAttributes(RAbstractContainer abstractContainer, @SuppressWarnings("unused") RNull list,
                    @Cached("create()") GetNonSharedNode nonShared) {
        RAbstractContainer resultVector = ((RAbstractContainer) nonShared.execute(abstractContainer)).materialize();
        resultVector.resetAllAttributes(true);
        return resultVector;
    }

    @Specialization
    protected RAbstractContainer updateAttributes(RAbstractContainer container, RList list,
                    @Cached("create()") GetNonSharedNode nonShared,
                    @Cached("createBinaryProfile()") ConditionProfile emptyListProfile) {
        RAbstractContainer result = ((RAbstractContainer) nonShared.execute(container)).materialize();
        if (emptyListProfile.profile(list.getLength() == 0)) {
            result.resetAllAttributes(true);
        } else {
            RStringVector listNames = getNamesNode.getNames(list);
            if (listNames == null) {
                throw error(RError.Message.ATTRIBUTES_NAMED);
            }
            result.resetAllAttributes(false);
            // error checking is a little weird - seems easier to separate it than weave it into the
            // update loop
            if (listNames.getLength() > 1) {
                checkAttributeForEmptyValue(list);
            }
            // has to be reported if no other name is undefined
            if (listNames.getDataAt(0).equals(RRuntime.NAMES_ATTR_EMPTY_VALUE)) {
                throw error(RError.Message.ZERO_LENGTH_VARIABLE);
            }
            // set the dim attribute first
            setDimAttribute(result, list);
            // set the remaining attributes in order
            result = setRemainingAttributes(result, list);
        }
        return result;
    }

    @TruffleBoundary
    private void checkAttributeForEmptyValue(RList rlist) {
        RStringVector listNames = rlist.getNames();
        int length = rlist.getLength();
        assert length > 0 : "Length should be > 0 for ExplodeLoop";
        for (int i = 1; i < length; i++) {
            String attrName = listNames.getDataAt(i);
            if (attrName.equals(RRuntime.NAMES_ATTR_EMPTY_VALUE)) {
                throw error(RError.Message.ALL_ATTRIBUTES_NAMES, i + 1);
            }
        }
    }

    private void setDimAttribute(RAbstractContainer result, RList sourceList) {
        RStringVector listNames = getNamesNode.getNames(sourceList);
        int length = sourceList.getLength();
        assert length > 0 : "Length should be > 0 for ExplodeLoop";
        for (int i = 0; i < sourceList.getLength(); i++) {
            Object value = sourceList.getDataAt(i);
            String attrName = listNames.getDataAt(i);
            if (attrName.equals(RRuntime.DIM_ATTR_KEY)) {

                if (setDimNode == null) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    setDimNode = insert(SetDimAttributeNode.create());
                }

                if (value == RNull.instance) {
                    setDimNode.setDimensions(result, null);
                } else {
                    RIntVector dimsVector = castInteger(castVector(value));
                    if (dimsVector.getLength() == 0) {
                        throw error(RError.Message.LENGTH_ZERO_DIM_INVALID);
                    }
                    setDimNode.setDimensions(result, dimsVector.materialize().getDataCopy());
                }
            }
        }
    }

    private RAbstractContainer setRemainingAttributes(RAbstractContainer result, RList sourceList) {
        RStringVector listNames = getNamesNode.getNames(sourceList);
        int length = sourceList.getLength();
        assert length > 0 : "Length should be > 0 for ExplodeLoop";
        RAbstractContainer res = result;
        for (int i = 0; i < sourceList.getLength(); i++) {
            Object value = sourceList.getDataAt(i);
            String attrName = listNames.getDataAt(i);
            if (attrName.equals(RRuntime.DIM_ATTR_KEY)) {
                continue;
            } else if (attrName.equals(RRuntime.NAMES_ATTR_KEY)) {
                res = updateNames(res, value);
            } else if (attrName.equals(RRuntime.DIMNAMES_ATTR_KEY)) {
                res = updateDimNames(res, value);
            } else if (attrName.equals(RRuntime.CLASS_ATTR_KEY)) {
                if (setClassNode == null) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    setClassNode = insert(SetClassAttributeNode.create());
                }
                if (value == RNull.instance) {
                    setClassNode.reset(res);
                } else {
                    setClassNode.setAttr(res, UpdateAttr.convertClassAttrFromObject(value));
                }
            } else if (attrName.equals(RRuntime.ROWNAMES_ATTR_KEY)) {
                if (setRowNamesNode == null) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    setRowNamesNode = insert(SetRowNamesAttributeNode.create());
                }
                setRowNamesNode.setRowNames(res, castVector(value));
            } else if (attrName.equals(RRuntime.TSP_ATTR_KEY)) {
                RDoubleVector tsp;
                if (value != RNull.instance) {
                    tsp = castDouble(castVector(value));
                } else {
                    tsp = null;
                }
                if (setTspAttrNode == null) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    setTspAttrNode = insert(SpecialAttributesFunctions.SetTspAttributeNode.create());
                }
                setTspAttrNode.setTsp(result, tsp);
            } else if (attrName.equals(RRuntime.COMMENT_ATTR_KEY)) {
                if (setCommentAttrNode == null) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    setCommentAttrNode = insert(SpecialAttributesFunctions.SetCommentAttributeNode.create());
                }
                setCommentAttrNode.setComment(result, value);
            } else {
                if (value == RNull.instance) {
                    if (removeAttrNode == null) {
                        CompilerDirectives.transferToInterpreterAndInvalidate();
                        removeAttrNode = insert(RemoveAttributeNode.create());
                    }
                    removeAttrNode.execute(res, attrName);
                } else {
                    if (setAttrNode == null) {
                        CompilerDirectives.transferToInterpreterAndInvalidate();
                        setAttrNode = insert(SetAttributeNode.create());
                    }
                    setAttrNode.execute(res, Utils.intern(attrName), value);
                }
            }
        }
        return res;
    }

    /**
     * All other, non-performance centric, {@link RAttributable} types, or error case for RNull
     * value.
     */
    @Specialization(guards = "!isRAbstractContainer(o)")
    @TruffleBoundary
    protected Object doOtherNull(RAttributable o, Object operand) {
        RAttributable obj = getNonShared(o);
        obj.removeAllAttributes();

        if (operand == RNull.instance) {
            obj.setClassAttr(null);
        } else {
            RList list = (RList) operand;
            RStringVector listNames = list.getNames();
            if (listNames == null) {
                throw error(RError.Message.ATTRIBUTES_NAMED);
            }
            for (int i = 0; i < list.getLength(); i++) {
                String attrName = listNames.getDataAt(i);
                if (attrName == null) {
                    throw error(RError.Message.ATTRIBUTES_NAMED);
                }
                if (RRuntime.CLASS_ATTR_KEY.equals(attrName)) {
                    Object attrValue = list.getDataAt(i);
                    if (attrValue == null) {
                        throw error(RError.Message.SET_INVALID_ATTR, "class");
                    }
                    obj.setClassAttr(UpdateAttr.convertClassAttrFromObject(attrValue));
                } else if (RRuntime.TSP_ATTR_KEY.equals(attrName) && o instanceof RAbstractContainer && ((RAbstractContainer) o).getLength() == 0) {
                    // Can't assign tsp to zero-length vector
                    throw error(RError.Message.CANNOT_ASSIGN_EMPTY_VECTOR, "tsp");
                } else {
                    obj.setAttr(Utils.intern(attrName), list.getDataAt(i));
                }
            }
        }
        return obj;
    }

    @Specialization(guards = {"!isRAttributable(o)", "!isScalar(o)"})
    protected Object doFallback(@SuppressWarnings("unused") Object o, @SuppressWarnings("unused") Object operand) {
        if (o == RNull.instance) {
            return (operand != RNull.instance) ? doOtherNull(RDataFactory.createList(), operand) : o;
        } else {
            throw error(RError.Message.INVALID_OR_UNIMPLEMENTED_ARGUMENTS);
        }
    }

    protected static boolean isScalar(Object o) {
        return o instanceof Integer || o instanceof Double || o instanceof Byte || o instanceof RRaw || o instanceof RComplex;
    }

    private static RAttributable getNonShared(RAttributable obj) {
        if (RSharingAttributeStorage.isShareable(obj)) {
            return (RAttributable) ((RSharingAttributeStorage) obj).getNonShared();
        }
        return obj;
    }
}
