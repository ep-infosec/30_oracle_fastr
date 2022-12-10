/*
 * This program is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation; either version 2 of the License, or (at your option)
 * any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License for
 * more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program; if not, write to the Free Software Foundation, Inc., 51
 * Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 *
 * Copyright (c) 2014, Purdue University
 * Copyright (c) 2014, 2020, Oracle and/or its affiliates
 *
 * All rights reserved.
 */

package com.oracle.truffle.r.nodes.binary;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.TypeSystemReference;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.r.nodes.unary.CastComplexNodeGen;
import com.oracle.truffle.r.nodes.unary.CastDoubleNodeGen;
import com.oracle.truffle.r.nodes.unary.CastIntegerNodeGen;
import com.oracle.truffle.r.nodes.unary.CastListNodeGen;
import com.oracle.truffle.r.nodes.unary.CastLogicalNodeGen;
import com.oracle.truffle.r.runtime.nodes.unary.CastNode;
import com.oracle.truffle.r.nodes.unary.CastRawNodeGen;
import com.oracle.truffle.r.nodes.unary.CastStringNodeGen;
import com.oracle.truffle.r.nodes.unary.TypeofNode;
import com.oracle.truffle.r.runtime.RError;
import com.oracle.truffle.r.runtime.RType;
import com.oracle.truffle.r.runtime.data.RPairList;
import com.oracle.truffle.r.runtime.data.RTypes;
import com.oracle.truffle.r.runtime.data.model.RAbstractVector;
import com.oracle.truffle.r.runtime.env.REnvironment;
import com.oracle.truffle.r.runtime.interop.ConvertForeignObjectNode;
import com.oracle.truffle.r.runtime.nodes.RBaseNode;

@TypeSystemReference(RTypes.class)
@ImportStatic({ConvertForeignObjectNode.class})
public abstract class CastTypeNode extends RBaseNode {

    protected static final int NUMBER_OF_TYPES = RType.values().length;

    @Child protected TypeofNode typeof = TypeofNode.create();

    public abstract Object execute(Object value, RType type);

    @SuppressWarnings("unused")
    @Specialization(guards = "typeof.execute(value) == type")
    protected static RAbstractVector doPass(RAbstractVector value, RType type) {
        return value;
    }

    @SuppressWarnings("unused")
    @Specialization(guards = {"typeof.execute(value) != type", "type == cachedType", "!isNull(cast)"}, limit = "NUMBER_OF_TYPES")
    protected static Object doCast(RAbstractVector value, RType type,
                    @Cached("type") RType cachedType,
                    @Cached("createCast(cachedType)") CastNode cast) {
        return cast.doCast(value);
    }

    @SuppressWarnings("unused")
    @Specialization(guards = "isNull(createCast(type))")
    @TruffleBoundary
    protected static Object doCastUnknown(RAbstractVector value, RType type) {
        // FIXME should we really return null here?
        return null;
    }

    @SuppressWarnings("unused")
    @Specialization()
    @TruffleBoundary
    protected Object doCastREnvironment(REnvironment value, RType type) {
        throw RError.error(getErrorContext(), RError.Message.ENVIRONMENTS_COERCE);
    }

    @SuppressWarnings("unused")
    @Specialization(guards = "!isLanguage(value)")
    @TruffleBoundary
    protected Object doCastRPairList(RPairList value, RType type,
                    @Cached("create()") CastTypeNode recurse) {
        return recurse.execute(value.toRList(), type);
    }

    @SuppressWarnings("unused")
    @Specialization(guards = "isLanguage(value)")
    @TruffleBoundary
    protected Object doCastLanguage(RPairList value, RType type) {
        throw RError.error(getErrorContext(), RError.Message.CANNOT_COERCE_QUOTED, RType.PairList.getName(), type.getName());
    }

    @SuppressWarnings("unused")
    @Specialization(guards = {"isForeignArray(value, interop)", "typeof.execute(value) != type",
                    "type == cachedType", "!isNull(cast)"}, limit = "NUMBER_OF_TYPES")
    protected static Object doCast(TruffleObject value, RType type,
                    @Cached("type") RType cachedType,
                    @Cached("createCast(cachedType)") CastNode cast,
                    @CachedLibrary("value") InteropLibrary interop,
                    @Cached("create()") ConvertForeignObjectNode convertForeign) {
        return cast.doCast(convertForeign.convert(value));
    }

    @TruffleBoundary
    protected static CastNode createCast(RType type) {
        return createCast(type, false, false, false, false);
    }

    public static CastTypeNode create() {
        return CastTypeNodeGen.create();
    }

    @TruffleBoundary
    public static CastNode createCast(RType type, boolean preserveNames, boolean preserveDimensions, boolean preserveAttributes, boolean reuseNonShared) {
        switch (type) {
            case Character:
                return CastStringNodeGen.create(preserveNames, preserveDimensions, preserveAttributes);
            case Complex:
                return CastComplexNodeGen.create(preserveNames, preserveDimensions, preserveAttributes);
            case Double:
                return CastDoubleNodeGen.create(preserveNames, preserveDimensions, preserveAttributes, false, reuseNonShared);
            case Integer:
                return CastIntegerNodeGen.create(preserveNames, preserveDimensions, preserveAttributes, false, reuseNonShared);
            case Logical:
                return CastLogicalNodeGen.create(preserveNames, preserveDimensions, preserveAttributes);
            case Raw:
                return CastRawNodeGen.create(preserveNames, preserveDimensions, preserveAttributes);
            case List:
                return CastListNodeGen.create(preserveNames, preserveDimensions, preserveAttributes);
            default:
                return null;

        }
    }

    protected static boolean isNull(Object value) {
        return value == null;
    }

    protected static boolean isLanguage(RPairList list) {
        return list.getRType() == RType.Language;
    }
}
