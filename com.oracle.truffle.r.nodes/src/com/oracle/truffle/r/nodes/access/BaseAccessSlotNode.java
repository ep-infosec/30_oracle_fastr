/*
 * Copyright (c) 1995, 1996, 1997  Robert Gentleman and Ross Ihaka
 * Copyright (c) 1995-2014, The R Core Team
 * Copyright (c) 2002-2008, The R Foundation
 * Copyright (c) 2017, 2022, Oracle and/or its affiliates
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
package com.oracle.truffle.r.nodes.access;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.r.nodes.RASTUtils;
import com.oracle.truffle.r.nodes.function.ClassHierarchyNode;
import com.oracle.truffle.r.nodes.function.ClassHierarchyNodeGen;
import com.oracle.truffle.r.nodes.unary.TypeofNode;
import com.oracle.truffle.r.runtime.RCaller;
import com.oracle.truffle.r.runtime.RError;
import com.oracle.truffle.r.runtime.RRuntime;
import com.oracle.truffle.r.runtime.Utils;
import com.oracle.truffle.r.runtime.context.RContext;
import com.oracle.truffle.r.runtime.data.RAttributable;
import com.oracle.truffle.r.runtime.data.RFunction;
import com.oracle.truffle.r.runtime.data.RNull;
import com.oracle.truffle.r.runtime.data.RStringVector;
import com.oracle.truffle.r.runtime.data.RSymbol;
import com.oracle.truffle.r.runtime.data.model.RAbstractVector;
import com.oracle.truffle.r.runtime.data.nodes.attributes.GetAttributeNode;
import com.oracle.truffle.r.runtime.data.nodes.attributes.SpecialAttributesFunctions.GetClassAttributeNode;
import com.oracle.truffle.r.runtime.env.REnvironment;
import com.oracle.truffle.r.runtime.nodes.RBaseNode;

public abstract class BaseAccessSlotNode extends RBaseNode {

    @Child private ClassHierarchyNode classHierarchy;

    private final BranchProfile noSlot = BranchProfile.create();
    private final BranchProfile symbolValue = BranchProfile.create();
    protected final boolean asOperator;

    protected BaseAccessSlotNode(boolean asOperator) {
        this.asOperator = asOperator;
    }

    @TruffleBoundary
    protected static RFunction getDataPartFunction(REnvironment methodsNamespace) {
        String name = "getDataPart";
        Object f = methodsNamespace.findFunction(name);
        return (RFunction) RContext.getRRuntimeASTAccess().forcePromise(name, f);
    }

    protected GetAttributeNode createAttrAccess() {
        return GetAttributeNode.create();
    }

    protected Object getSlotS4Internal(RAttributable object, String name, Object value, GetClassAttributeNode getClassNode) {
        if (value == null) {
            noSlot.enter();
            assert Utils.isInterned(name);
            if (Utils.identityEquals(name, RRuntime.DOT_S3_CLASS)) {
                if (classHierarchy == null) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    classHierarchy = insert(ClassHierarchyNodeGen.create(true, false));
                }
                return classHierarchy.execute(object);
            } else if (Utils.identityEquals(name, RRuntime.DOT_DATA)) {
                // TODO: any way to cache it or use a mechanism similar to overrides?
                REnvironment methodsNamespace = REnvironment.getRegisteredNamespace(getRContext(), "methods");
                RFunction dataPart = getDataPartFunction(methodsNamespace);
                return getRContext().getThisEngine().evalFunction(dataPart, methodsNamespace.getFrame(), RCaller.create(null, RASTUtils.getOriginalCall(this)), true, null, object);
            } else if (Utils.identityEquals(name, RRuntime.NAMES_ATTR_KEY) && object instanceof RAbstractVector) {
                assert false; // RS4Object can never be a vector?
                return RNull.instance;
            }

            CompilerDirectives.transferToInterpreter();
            RStringVector classAttr = getClassNode.getClassAttr(object);
            if (classAttr == null) {
                throw error(RError.Message.SLOT_CANNOT_GET, name, TypeofNode.getTypeof(object).getName());
            } else {
                throw error(RError.Message.SLOT_NONE, name, classAttr.getLength() == 0 ? RRuntime.STRING_NA : classAttr.getDataAt(0));
            }
        }
        if (value instanceof RSymbol) {
            symbolValue.enter();
            if (value == RRuntime.PSEUDO_NULL) {
                return RNull.instance;
            }
        }
        return value;
    }

    protected boolean isDotData(String name) {
        return Utils.identityEquals(name, RRuntime.DOT_DATA);
    }

    protected boolean slotAccessAllowed(RAttributable object) {
        return object.isS4() || !asOperator;
    }

}
