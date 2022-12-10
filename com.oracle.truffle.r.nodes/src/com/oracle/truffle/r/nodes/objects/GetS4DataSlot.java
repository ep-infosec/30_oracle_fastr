/*
 * Copyright (c) 1995, 1996, 1997  Robert Gentleman and Ross Ihaka
 * Copyright (c) 1995-2014, The R Core Team
 * Copyright (c) 2002-2008, The R Foundation
 * Copyright (c) 2015, 2020, Oracle and/or its affiliates
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, a copy is available at
 * https://www.R-project.org/Licenses/
 */
package com.oracle.truffle.r.nodes.objects;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.r.runtime.data.nodes.attributes.GetFixedAttributeNode;
import com.oracle.truffle.r.runtime.data.nodes.attributes.RemoveFixedAttributeNode;
import com.oracle.truffle.r.runtime.data.nodes.attributes.SpecialAttributesFunctions.SetClassAttributeNode;
import com.oracle.truffle.r.runtime.nodes.unary.CastToVectorNode;
import com.oracle.truffle.r.nodes.unary.TypeofNode;
import com.oracle.truffle.r.runtime.RRuntime;
import com.oracle.truffle.r.runtime.RType;
import com.oracle.truffle.r.runtime.data.RAttributable;
import com.oracle.truffle.r.runtime.data.RBaseObject;
import com.oracle.truffle.r.runtime.data.RNull;
import com.oracle.truffle.r.runtime.data.RS4Object;
import com.oracle.truffle.r.runtime.data.RSharingAttributeStorage;

// transcribed from src/main/attrib.c
public final class GetS4DataSlot extends Node {

    @Child private GetFixedAttributeNode s3ClassAttrAccess;
    @Child private RemoveFixedAttributeNode s3ClassAttrRemove;
    @Child private CastToVectorNode castToVector;
    @Child private GetFixedAttributeNode dotDataAttrAccess;
    @Child private GetFixedAttributeNode dotXDataAttrAccess;
    @Child private TypeofNode typeOf = TypeofNode.create();
    @Child private SetClassAttributeNode setClassAttrNode;

    private final BranchProfile shareable = BranchProfile.create();

    private final RType type;

    private GetS4DataSlot(RType type) {
        this.type = type;
    }

    public static GetS4DataSlot create(RType type) {
        return new GetS4DataSlot(type);
    }

    public static GetS4DataSlot createEnvironment() {
        return new GetS4DataSlot(RType.Environment);
    }

    public RBaseObject executeObject(RAttributable attrObj) {
        RAttributable obj = attrObj;
        Object value = null;
        if (!(obj instanceof RS4Object) || type == RType.S4Object) {
            Object s3Class = null;
            if (s3ClassAttrAccess == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                s3ClassAttrAccess = insert(GetFixedAttributeNode.createFor(RRuntime.DOT_S3_CLASS));
            }
            s3Class = s3ClassAttrAccess.execute(obj);
            if (s3Class == null && type == RType.S4Object) {
                return RNull.instance;
            }
            if (RSharingAttributeStorage.isShareable(obj) && ((RSharingAttributeStorage) obj).isShared()) {
                shareable.enter();
                obj = ((RSharingAttributeStorage) obj).copy();
            }

            if (setClassAttrNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                setClassAttrNode = insert(SetClassAttributeNode.create());
            }

            if (s3Class != null) {
                if (s3ClassAttrRemove == null) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    s3ClassAttrRemove = insert(RemoveFixedAttributeNode.createFor(RRuntime.DOT_S3_CLASS));
                }
                if (castToVector == null) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    castToVector = insert(CastToVectorNode.create());
                }
                s3ClassAttrRemove.execute(obj);
                setClassAttrNode.setAttr(obj, castToVector.doCast(s3Class));
            } else {
                setClassAttrNode.reset(obj);
            }
            obj.unsetS4();
            if (type == RType.S4Object) {
                return obj;
            }
            value = obj;
        } else {
            if (dotDataAttrAccess == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                dotDataAttrAccess = insert(GetFixedAttributeNode.createFor(RRuntime.DOT_DATA));
            }
            value = dotDataAttrAccess.execute(obj);
        }
        if (value == null) {
            if (dotXDataAttrAccess == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                dotXDataAttrAccess = insert(GetFixedAttributeNode.createFor(RRuntime.DOT_XDATA));
            }
            value = dotXDataAttrAccess.execute(obj);
        }
        if (value != null && (type == RType.Any || type == typeOf.execute(value))) {
            return (RBaseObject) value;
        } else {
            return RNull.instance;
        }
    }
}
