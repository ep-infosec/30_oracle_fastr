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
package com.oracle.truffle.r.nodes.unary;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.r.runtime.DSLConfig;
import com.oracle.truffle.r.runtime.RRuntime;
import com.oracle.truffle.r.runtime.RType;
import com.oracle.truffle.r.runtime.data.CharSXPWrapper;
import com.oracle.truffle.r.runtime.data.RBaseObject;
import com.oracle.truffle.r.runtime.data.RComplex;
import com.oracle.truffle.r.runtime.data.RMissing;
import com.oracle.truffle.r.runtime.data.RRaw;
import com.oracle.truffle.r.runtime.nodes.unary.UnaryNode;

@ImportStatic(DSLConfig.class)
public abstract class TypeofNode extends UnaryNode {

    protected static final int NUMBER_OF_CACHED_CLASSES = 5;

    public abstract RType execute(Object x);

    @Specialization
    protected static RType doLogical(@SuppressWarnings("unused") byte x) {
        return RType.Logical;
    }

    @Specialization
    protected static RType doInt(@SuppressWarnings("unused") int s) {
        return RType.Integer;
    }

    @Specialization
    protected static RType doDouble(@SuppressWarnings("unused") double x) {
        return RType.Double;
    }

    @Specialization
    protected static RType doString(@SuppressWarnings("unused") String x) {
        return RType.Character;
    }

    @Specialization
    protected static RType doCharSXP(@SuppressWarnings("unused") CharSXPWrapper x) {
        return RType.Char;
    }

    @Specialization
    protected static RType doMissing(@SuppressWarnings("unused") RMissing x) {
        return RType.Missing;
    }

    @Specialization
    protected static RType doRRaw(@SuppressWarnings("unused") RRaw x) {
        return RType.Raw;
    }

    @Specialization
    protected static RType doRRaw(@SuppressWarnings("unused") RComplex x) {
        return RType.Complex;
    }

    @Specialization(guards = "isForeignObject(object)")
    protected RType doTruffleObject(@SuppressWarnings("unused") TruffleObject object) {
        return RType.TruffleObject;
    }

    @Specialization(guards = {"operand.getClass() == cachedClass"}, limit = "getCacheSize(NUMBER_OF_CACHED_CLASSES)")
    protected static RType doCachedTyped(Object operand,
                    @Cached("getTypedValueClass(operand)") Class<? extends RBaseObject> cachedClass) {
        return cachedClass.cast(operand).getRType();
    }

    protected static Class<? extends RBaseObject> getTypedValueClass(Object operand) {
        CompilerAsserts.neverPartOfCompilation();
        if (operand instanceof RBaseObject) {
            return ((RBaseObject) operand).getClass();
        } else {
            throw new AssertionError("Invalid untyped value " + operand.getClass() + ".");
        }
    }

    @Specialization(replaces = {"doCachedTyped"})
    protected static RType doGenericTyped(RBaseObject operand) {
        return operand.getRType();
    }

    protected static boolean isForeignObject(Object obj) {
        return RRuntime.isForeignObject(obj);
    }

    public static RType getTypeof(Object operand) {
        CompilerAsserts.neverPartOfCompilation();
        return ((RBaseObject) RRuntime.asAbstractVector(operand)).getRType();
    }

    public static TypeofNode create() {
        return TypeofNodeGen.create();
    }
}
