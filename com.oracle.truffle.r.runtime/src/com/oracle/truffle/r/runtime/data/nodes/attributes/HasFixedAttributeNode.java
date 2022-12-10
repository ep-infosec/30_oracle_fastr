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
package com.oracle.truffle.r.runtime.data.nodes.attributes;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.object.DynamicObject;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.r.runtime.data.nodes.attributes.FixedAttributeAccessNode.GenericFixedAttributeAccessNode;
import com.oracle.truffle.r.runtime.RRuntime;
import com.oracle.truffle.r.runtime.data.RAttributable;

/**
 * This node is responsible for determining the existence of the predefined (fixed) attribute. It
 * accepts both {@link DynamicObject} and {@link RAttributable} instances as the first argument. If
 * the first argument is {@link RAttributable} and its attributes are initialized, the recursive
 * instance of this class is used to determine the existence from the attributes.
 */
public abstract class HasFixedAttributeNode extends GenericFixedAttributeAccessNode {

    protected HasFixedAttributeNode(String name) {
        super(name);
    }

    public static HasFixedAttributeNode create(String name) {
        return HasFixedAttributeNodeGen.create(name);
    }

    public static HasFixedAttributeNode createDim() {
        return HasFixedAttributeNodeGen.create(RRuntime.DIM_ATTR_KEY);
    }

    public static HasFixedAttributeNode createClass() {
        return HasFixedAttributeNodeGen.create(RRuntime.CLASS_ATTR_KEY);
    }

    public abstract boolean execute(RAttributable attr);

    @Specialization
    protected boolean hasAttrFromAttributable(RAttributable x,
                    @Cached("create()") BranchProfile attrNullProfile,
                    @Cached("create(getAttributeName())") HasFixedPropertyNode hasFixedPropertyNode) {
        DynamicObject attributes = x.getAttributes();
        if (attributes == null) {
            attrNullProfile.enter();
            return false;
        }
        return hasFixedPropertyNode.execute(attributes);
    }
}
