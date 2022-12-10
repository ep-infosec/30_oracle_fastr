/*
 * Copyright (c) 2014, 2021, Oracle and/or its affiliates. All rights reserved.
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
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.profiles.ConditionProfile;
import com.oracle.truffle.r.nodes.unary.CastStringNode;
import com.oracle.truffle.r.runtime.RInternalError;
import com.oracle.truffle.r.runtime.RRuntime;
import com.oracle.truffle.r.runtime.data.CharSXPWrapper;
import com.oracle.truffle.r.runtime.data.RComplexVector;
import com.oracle.truffle.r.runtime.data.RDoubleVector;
import com.oracle.truffle.r.runtime.data.RStringVector;
import com.oracle.truffle.r.runtime.data.RSymbol;
import com.oracle.truffle.r.runtime.data.RTypes;
import com.oracle.truffle.r.runtime.data.VectorDataLibrary;
import com.oracle.truffle.r.runtime.data.model.RAbstractAtomicVector;

@TypeSystemReference(RTypes.class)
public abstract class AsCharNode extends FFIUpCallNode.Arg1 {
    private static final CharSXPWrapper CharSXPWrapper_NA = CharSXPWrapper.create(RRuntime.STRING_NA);
    private static final CharSXPWrapper CharSXPWrapper_NAString = CharSXPWrapper.create("NA");

    public abstract CharSXPWrapper execute(Object obj);

    @Specialization
    protected CharSXPWrapper asChar(CharSXPWrapper obj) {
        return obj;
    }

    @Specialization
    protected CharSXPWrapper asChar(RStringVector obj,
                    @Cached("createBinaryProfile()") ConditionProfile zeroLengthProfile,
                    @Cached("createBinaryProfile()") ConditionProfile naProfile,
                    @CachedLibrary(limit = "getTypedVectorDataLibraryCacheSize()") VectorDataLibrary dataLib) {
        if (zeroLengthProfile.profile(obj.getLength() == 0)) {
            return CharSXPWrapper_NA;
        } else {
            Object newCharSXPData = null;
            try {
                newCharSXPData = dataLib.materializeCharSXPStorage(obj.getData());
            } catch (UnsupportedMessageException e) {
                throw RInternalError.shouldNotReachHere(e);
            }
            obj.setData(newCharSXPData);
            CharSXPWrapper result = dataLib.getCharSXPAt(obj.getData(), 0);
            return naProfile.profile(RRuntime.isNA(result.getContents())) ? CharSXPWrapper_NA : result;
        }
    }

    @Specialization
    protected CharSXPWrapper asChar(RSymbol obj) {
        return obj.getWrappedName();
    }

    @Specialization(guards = {"obj.getLength() > 0", "isNotStringVec(obj)"})
    protected CharSXPWrapper asChar(RAbstractAtomicVector obj,
                    @Cached("createNonPreserving()") CastStringNode castStringNode) {
        // for other than character vector, symbol or CHARSXP, the cast and creation of new vector
        // is inevitable and the user should know to take appropriate measures (i.e. PROTECT).
        Object castObj = castStringNode.executeString(obj);
        CharSXPWrapper result;
        if (castObj instanceof String) {
            result = CharSXPWrapper.create((String) castObj);
        } else if (castObj instanceof RStringVector) {
            result = CharSXPWrapper.create(((RStringVector) castObj).getDataAt(0));
        } else {
            throw RInternalError.shouldNotReachHere();
        }

        if (RRuntime.isNA(result.getContents())) {
            if (obj instanceof RComplexVector || obj instanceof RDoubleVector) {
                return CharSXPWrapper_NAString;
            } else {
                return CharSXPWrapper_NA;
            }
        } else {
            return result;
        }
    }

    @Fallback
    protected CharSXPWrapper asCharFallback(@SuppressWarnings("unused") Object obj) {
        return CharSXPWrapper_NA;
    }

    protected static boolean isNotStringVec(RAbstractAtomicVector obj) {
        // assertion: only materialized string vectors should ever appear in native code
        assert !(obj instanceof RStringVector) || ((RStringVector) obj).isMaterialized() : obj;
        return !(obj instanceof RStringVector);
    }

    public static AsCharNode create() {
        return AsCharNodeGen.create();
    }
}
