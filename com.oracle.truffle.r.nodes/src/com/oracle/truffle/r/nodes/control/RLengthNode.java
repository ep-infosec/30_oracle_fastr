/*
 * Copyright (c) 2015, 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.r.nodes.control;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.r.nodes.profile.VectorLengthProfile;
import com.oracle.truffle.r.runtime.DSLConfig;
import com.oracle.truffle.r.runtime.RRuntime;
import com.oracle.truffle.r.runtime.data.CharSXPWrapper;
import com.oracle.truffle.r.runtime.data.RArgsValuesAndNames;
import com.oracle.truffle.r.runtime.data.RComplex;
import com.oracle.truffle.r.runtime.data.RExternalPtr;
import com.oracle.truffle.r.runtime.data.RFunction;
import com.oracle.truffle.r.runtime.data.RNull;
import com.oracle.truffle.r.runtime.data.RRaw;
import com.oracle.truffle.r.runtime.data.RS4Object;
import com.oracle.truffle.r.runtime.data.RSymbol;
import com.oracle.truffle.r.runtime.data.VectorDataLibrary;
import com.oracle.truffle.r.runtime.data.model.RAbstractContainer;
import com.oracle.truffle.r.runtime.env.REnvironment;
import com.oracle.truffle.r.runtime.nodes.RBaseNode;

/**
 * Gets length of given container. Does not actually dispatch to the 'length' function, which may be
 * overridden for some S3/S4 classes. Check if you need to get actual length, or what the 'length'
 * function returns, like in {@code seq_along}.
 */
@GenerateUncached
@ImportStatic(DSLConfig.class)
public abstract class RLengthNode extends RBaseNode {

    public abstract int executeInteger(Object value);

    public static RLengthNode create() {
        return RLengthNodeGen.create();
    }

    @Specialization
    protected int doNull(@SuppressWarnings("unused") RNull operand) {
        return 0;
    }

    @Specialization
    protected int doLogical(@SuppressWarnings("unused") byte operand) {
        return 1;
    }

    @Specialization
    protected int doInteger(@SuppressWarnings("unused") int operand) {
        return 1;
    }

    @Specialization
    protected int doDouble(@SuppressWarnings("unused") double operand) {
        return 1;
    }

    @Specialization
    protected int doString(@SuppressWarnings("unused") String operand) {
        return 1;
    }

    @Specialization
    protected int doSymbol(@SuppressWarnings("unused") RSymbol operand) {
        return 1;
    }

    @Specialization
    protected int doChar(@SuppressWarnings("unused") CharSXPWrapper operand) {
        return 1;
    }

    @Specialization
    protected int doChar(@SuppressWarnings("unused") RRaw operand) {
        return 1;
    }

    @Specialization
    protected int doComplex(@SuppressWarnings("unused") RComplex operand) {
        return 1;
    }

    @Specialization(limit = "getGenericVectorAccessCacheSize()")
    protected int doContainer(RAbstractContainer operand,
                    @Cached VectorLengthProfile lengthProfile,
                    @CachedLibrary("operand.getData()") VectorDataLibrary lib) {
        return lengthProfile.profile(lib.getLength(operand.getData()));
    }

    @Specialization
    protected int getLength(REnvironment env,
                    @Cached(allowUncached = true) VectorLengthProfile lengthProfile) {
        /*
         * This is a bit wasteful but only in the creation of the RStringVector; all the logic to
         * decide whether to include a name is still necessary
         */
        return lengthProfile.profile(env.ls(true, null, false).getLength());
    }

    @Specialization
    protected int getLength(RArgsValuesAndNames vargs) {
        return vargs.getLength();
    }

    @Specialization
    protected int getLength(@SuppressWarnings("unused") RFunction func) {
        return 1;
    }

    @Specialization
    protected int getLength(@SuppressWarnings("unused") RS4Object obj) {
        return 1;
    }

    @Specialization
    protected int getLength(@SuppressWarnings("unused") RExternalPtr ptr) {
        return 1;
    }

    protected static boolean isForeignObject(TruffleObject object) {
        return RRuntime.isForeignObject(object);
    }

    @Specialization(guards = {"isForeignObject(object)", "interop.hasArrayElements(object)"}, limit = "getInteropLibraryCacheSize()")
    protected int getForeignArraySize(TruffleObject object,
                    @CachedLibrary("object") InteropLibrary interop) {
        return RRuntime.getForeignArraySize(object, interop);
    }

    @Specialization(guards = {"isForeignObject(object)", "!interop.hasArrayElements(object)"}, limit = "getInteropLibraryCacheSize()")
    protected int getForeignObjectSize(@SuppressWarnings("unused") TruffleObject object,
                    @SuppressWarnings("unused") @CachedLibrary("object") InteropLibrary interop) {
        return 1;
    }

}
