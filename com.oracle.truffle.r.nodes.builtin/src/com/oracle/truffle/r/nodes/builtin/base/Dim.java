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

import static com.oracle.truffle.r.runtime.RDispatch.INTERNAL_GENERIC;
import static com.oracle.truffle.r.runtime.builtins.RBehavior.PURE;
import static com.oracle.truffle.r.runtime.builtins.RBuiltinKind.PRIMITIVE;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.profiles.ConditionProfile;
import com.oracle.truffle.r.runtime.data.nodes.attributes.SpecialAttributesFunctions.GetDimAttributeNode;
import com.oracle.truffle.r.nodes.builtin.RBuiltinNode;
import com.oracle.truffle.r.runtime.RRuntime;
import com.oracle.truffle.r.runtime.builtins.RBuiltin;
import com.oracle.truffle.r.runtime.data.RDataFactory;
import com.oracle.truffle.r.runtime.data.RNull;
import com.oracle.truffle.r.runtime.data.model.RAbstractContainer;
import com.oracle.truffle.r.runtime.interop.InspectForeignArrayNode;

@ImportStatic(RRuntime.class)
@RBuiltin(name = "dim", kind = PRIMITIVE, parameterNames = {"x"}, dispatch = INTERNAL_GENERIC, behavior = PURE)
public abstract class Dim extends RBuiltinNode.Arg1 {

    static {
        Casts.noCasts(Dim.class);
    }

    @Specialization
    protected Object dim(RAbstractContainer container,
                    @Cached("createBinaryProfile()") ConditionProfile hasDimensionsProfile,
                    @Cached("create()") GetDimAttributeNode getDimsNode) {
        int[] dims = getDimsNode.getDimensions(container);
        if (hasDimensionsProfile.profile(dims != null && dims.length > 0)) {
            return RDataFactory.createIntVector(dims, RDataFactory.COMPLETE_VECTOR);
        } else {
            return RNull.instance;
        }
    }

    @Specialization(guards = "isForeignObject(obj)")
    protected Object dimForeign(TruffleObject obj,
                    @Cached("createBinaryProfile()") ConditionProfile hasDimensionsProfile,
                    @Cached("create()") InspectForeignArrayNode inspectForeignNode) {
        InspectForeignArrayNode.ArrayInfo info = inspectForeignNode.getArrayInfo(obj);
        int[] dims = info != null ? info.getDims() : null;
        if (hasDimensionsProfile.profile(dims != null)) {
            return RDataFactory.createIntVector(dims, RDataFactory.COMPLETE_VECTOR);
        } else {
            return RNull.instance;
        }
    }

    @Specialization(guards = {"!isRAbstractContainer(vector)", "!isForeignObject(vector)"})
    protected RNull dim(@SuppressWarnings("unused") Object vector) {
        return RNull.instance;
    }
}
