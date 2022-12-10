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
package com.oracle.truffle.r.ffi.impl.nodes;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.TypeSystemReference;
import com.oracle.truffle.r.nodes.unary.CastLogicalNode;
import com.oracle.truffle.r.runtime.RInternalError;
import com.oracle.truffle.r.runtime.RRuntime;
import com.oracle.truffle.r.runtime.data.RTypes;
import com.oracle.truffle.r.runtime.data.model.RAbstractAtomicVector;
import com.oracle.truffle.r.runtime.data.RLogicalVector;

@TypeSystemReference(RTypes.class)
public abstract class AsLogicalNode extends FFIUpCallNode.Arg1 {

    public abstract int execute(Object obj);

    @Specialization
    protected int asLogical(byte b) {
        return RRuntime.isNA(b) ? RRuntime.INT_NA : b;
    }

    @Specialization
    protected int asLogical(RLogicalVector obj) {
        if (obj.getLength() == 0) {
            return RRuntime.INT_NA;
        }
        byte result = obj.getDataAt(0);
        return RRuntime.isNA(result) ? RRuntime.INT_NA : result;
    }

    @Specialization(guards = "obj.getLength() > 0")
    protected int asLogical(RAbstractAtomicVector obj,
                    @Cached("createNonPreserving()") CastLogicalNode castLogicalNode) {
        Object castObj = castLogicalNode.doCast(obj);
        byte result;
        if (castObj instanceof Byte) {
            result = (byte) castObj;
        } else if (castObj instanceof RLogicalVector) {
            result = ((RLogicalVector) castObj).getDataAt(0);
        } else {
            throw RInternalError.shouldNotReachHere();
        }
        return RRuntime.isNA(result) ? RRuntime.INT_NA : result;
    }

    @Fallback
    protected int asLogicalFallback(@SuppressWarnings("unused") Object obj) {
        return RRuntime.INT_NA;
    }

    public static AsLogicalNode create() {
        return AsLogicalNodeGen.create();
    }
}
