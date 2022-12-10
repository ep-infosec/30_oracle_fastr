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
package com.oracle.truffle.r.runtime.data.nodes;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.profiles.ConditionProfile;
import com.oracle.truffle.r.runtime.data.RSharingAttributeStorage;
import com.oracle.truffle.r.runtime.nodes.RBaseNode;

/**
 * Implements a fast-path version of {@code UpdateShareableChildValue}.
 */
@GenerateUncached
public abstract class UpdateShareableChildValueNode extends RBaseNode {

    public abstract void execute(Object owner, Object attrValue);

    public final <T> T updateState(Object owner, T item) {
        execute(owner, item);
        return item;
    }

    public static UpdateShareableChildValueNode create() {
        return UpdateShareableChildValueNodeGen.create();
    }

    @Specialization(guards = {"isShareable(owner)", "isShareable(value)"})
    protected void doShareableValues(RSharingAttributeStorage owner, RSharingAttributeStorage value,
                    @Cached("createBinaryProfile()") ConditionProfile sharedValue,
                    @Cached("createBinaryProfile()") ConditionProfile temporaryOwner) {
        if (sharedValue.profile(value.isShared())) {
            // it is already shared, not need to do anything
            return;
        }
        if (temporaryOwner.profile(owner.isTemporary())) {
            // This can happen, for example, when we immediately extract out of a temporary
            // list that was returned by a built-in, like: strsplit(...)[[1L]]. We do not need
            // to transition the element, it may stay temporary.
            return;
        }

        // the owner is at least non-shared
        if (value.isTemporary()) {
            // make it at least non-shared (the owner must be also at least non-shared)
            value.incRefCount();
        }
        if (owner.isShared()) {
            // owner is shared, make the attribute value shared too
            value.incRefCount();
        }
    }

    @Fallback
    protected void doFallback(@SuppressWarnings("unused") Object owner, @SuppressWarnings("unused") Object value) {
    }

    protected static boolean isShareable(Object o) {
        return RSharingAttributeStorage.isShareable(o);
    }
}
