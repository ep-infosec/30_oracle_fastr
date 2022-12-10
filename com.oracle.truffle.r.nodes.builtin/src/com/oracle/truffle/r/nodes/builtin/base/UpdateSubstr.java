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
package com.oracle.truffle.r.nodes.builtin.base;

import static com.oracle.truffle.r.nodes.builtin.CastBuilder.Predef.nullValue;
import static com.oracle.truffle.r.nodes.builtin.CastBuilder.Predef.stringValue;
import static com.oracle.truffle.r.runtime.builtins.RBehavior.PURE;
import static com.oracle.truffle.r.runtime.builtins.RBuiltinKind.INTERNAL;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.r.nodes.builtin.RBuiltinNode;
import com.oracle.truffle.r.runtime.RError;
import com.oracle.truffle.r.runtime.RInternalError;
import com.oracle.truffle.r.runtime.RRuntime;
import com.oracle.truffle.r.runtime.builtins.RBuiltin;
import com.oracle.truffle.r.runtime.data.RDataFactory;
import com.oracle.truffle.r.runtime.data.RNull;
import com.oracle.truffle.r.runtime.data.RIntVector;
import com.oracle.truffle.r.runtime.data.RStringVector;
import com.oracle.truffle.r.runtime.data.model.RAbstractVector;
import com.oracle.truffle.r.runtime.ops.na.NACheck;

@RBuiltin(name = "substr<-", kind = INTERNAL, parameterNames = {"x", "start", "stop", "value"}, behavior = PURE)
public abstract class UpdateSubstr extends RBuiltinNode.Arg4 {

    private final NACheck na = NACheck.create();

    private final BranchProfile everSeenIllegalRange = BranchProfile.create();

    static {
        Casts casts = new Casts(UpdateSubstr.class);
        casts.arg("x").mustBe(nullValue().not().and(stringValue()), RError.Message.REPLACING_IN_NON_CHAR_OBJ).asStringVector();
    }

    private static boolean rangeOk(String x, int start, int stop) {
        return start <= stop && start > 0 && stop > 0 && start <= x.length() && stop <= x.length();
    }

    @TruffleBoundary
    private static String replaceSubstring(String x, String value, int actualStart, int replacementLength, int actualStop) {
        return x.substring(0, actualStart - 1) + value.substring(0, replacementLength) + x.substring(actualStop, x.length());
    }

    @TruffleBoundary
    private String substr0(String x, int start, int stop, String value) {
        if (na.check(x) || na.check(start) || na.check(stop)) {
            return RRuntime.STRING_NA;
        }
        int actualStart = start;
        int actualStop = stop;
        if (!rangeOk(x, start, stop)) {
            everSeenIllegalRange.enter();
            if (start > x.length()) {
                return x;
            }
            if (start < 1) {
                actualStart = 1;
            }
            if (stop > x.length() || stop < 0) {
                actualStop = x.length();
            }
            if (actualStart > actualStop) {
                return x;
            }
        }
        int replacementLength = actualStop - (actualStart - 1);
        if (replacementLength > value.length()) {
            actualStop -= (replacementLength - value.length());
            replacementLength = value.length();
        }
        return replaceSubstring(x, value, actualStart, replacementLength, actualStop);
    }

    @SuppressWarnings("unused")
    @Specialization(guards = "emptyArg(arg)")
    @TruffleBoundary
    protected RStringVector substrEmptyArg(RStringVector arg, RIntVector start, RIntVector stop, Object value) {
        return RDataFactory.createEmptyStringVector();
    }

    @SuppressWarnings("unused")
    @Specialization(guards = {"!emptyArg(arg)", "wrongParams(start, stop)"})
    @TruffleBoundary
    protected RNull substrWrongParams(RStringVector arg, RIntVector start, RIntVector stop, Object value) {
        RInternalError.shouldNotReachHere();
        return RNull.instance; // dummy
    }

    @SuppressWarnings("unused")
    @Specialization(guards = {"!emptyArg(arg)", "!wrongParams(start, stop)"})
    @TruffleBoundary
    protected RStringVector substr(RStringVector arg, RIntVector start, RIntVector stop, RNull value) {
        throw error(RError.Message.INVALID_UNNAMED_VALUE);
    }

    @SuppressWarnings("unused")
    @Specialization(guards = {"!emptyArg(arg)", "!wrongParams(start, stop)", "!isRStringVector(value) || value.getLength() == 0"})
    protected RStringVector substr(RStringVector arg, RIntVector start, RIntVector stop, RAbstractVector value) {
        CompilerDirectives.transferToInterpreter();
        throw error(RError.Message.INVALID_UNNAMED_VALUE);
    }

    @Specialization(guards = {"!emptyArg(arg)", "!wrongParams(start, stop)", "value.getLength() > 0"})
    @TruffleBoundary
    protected RStringVector substr(RStringVector arg, RIntVector start, RIntVector stop, RStringVector value) {
        String[] res = new String[arg.getLength()];
        na.enable(arg);
        na.enable(start);
        na.enable(stop);
        int startLength = start.getLength();
        int stopLength = stop.getLength();
        int valueLength = value.getLength();
        for (int i = 0; i < res.length; i++) {
            int j = i % startLength;
            int k = i % stopLength;
            int l = i % valueLength;
            res[i] = substr0(arg.getDataAt(i), start.getDataAt(j), stop.getDataAt(k), value.getDataAt(l));
        }
        return RDataFactory.createStringVector(res, na.neverSeenNA());

    }

    protected boolean emptyArg(RStringVector arg) {
        return arg.getLength() == 0;
    }

    protected boolean wrongParams(RIntVector start, RIntVector stop) {
        if (start.getLength() == 0 || stop.getLength() == 0) {
            throw error(RError.Message.INVALID_ARGUMENTS_NO_QUOTE, "substring");
        }
        return false;
    }
}
