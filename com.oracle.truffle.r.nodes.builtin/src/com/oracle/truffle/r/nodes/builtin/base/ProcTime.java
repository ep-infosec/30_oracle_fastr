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

import static com.oracle.truffle.r.runtime.builtins.RBehavior.IO;
import static com.oracle.truffle.r.runtime.builtins.RBuiltinKind.PRIMITIVE;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.r.runtime.data.nodes.attributes.SpecialAttributesFunctions.SetClassAttributeNode;
import com.oracle.truffle.r.nodes.builtin.RBuiltinNode;
import com.oracle.truffle.r.runtime.RError.Message;
import com.oracle.truffle.r.runtime.RRuntime;
import com.oracle.truffle.r.runtime.builtins.RBuiltin;
import com.oracle.truffle.r.runtime.context.Engine;
import com.oracle.truffle.r.runtime.context.RContext;
import com.oracle.truffle.r.runtime.data.RDataFactory;
import com.oracle.truffle.r.runtime.data.RDoubleVector;
import com.oracle.truffle.r.runtime.data.RStringVector;

@RBuiltin(name = "proc.time", kind = PRIMITIVE, parameterNames = {}, behavior = IO)
public abstract class ProcTime extends RBuiltinNode.Arg0 {

    private static final String[] NAMES = new String[]{"user.self", "sys.self", "elapsed", "user.child", "sys.child"};
    private static final RStringVector PROC_TIME_CLASS = RDataFactory.createStringVectorFromScalar("proc_time");

    private static RStringVector RNAMES;

    @Child private SetClassAttributeNode setClassAttrNode = SetClassAttributeNode.create();

    @Specialization
    @TruffleBoundary
    protected RDoubleVector procTime() {
        double[] data = new double[5];
        Engine.Timings timings = RContext.getEngine().getTimings();
        long nowInNanos = timings.elapsedTimeInNanos();
        long[] userSysTimeInNanos = timings.userSysTimeInNanos();
        long userTimeInNanos = userSysTimeInNanos != null ? userSysTimeInNanos[0] : -1;
        long sysTimeInNanos = userSysTimeInNanos != null ? userSysTimeInNanos[1] : -1;
        data[0] = userTimeInNanos < 0 ? RRuntime.DOUBLE_NA : asDoubleSecs(userTimeInNanos);
        data[1] = sysTimeInNanos < 0 ? RRuntime.DOUBLE_NA : asDoubleSecs(sysTimeInNanos);
        boolean complete = userTimeInNanos >= 0 && sysTimeInNanos >= 0;
        data[2] = asDoubleSecs(nowInNanos);
        long[] childTimes = timings.childTimesInNanos();
        boolean na = childTimes[0] < 0 || childTimes[1] < 0;
        complete &= !na;
        data[3] = na ? RRuntime.DOUBLE_NA : asDoubleSecs(childTimes[0]);
        data[4] = na ? RRuntime.DOUBLE_NA : asDoubleSecs(childTimes[1]);
        if (RNAMES == null) {
            RNAMES = RDataFactory.createStringVector(NAMES, RDataFactory.COMPLETE_VECTOR);
        }
        RDoubleVector result = RDataFactory.createDoubleVector(data, complete, RNAMES);
        setClassAttrNode.setAttr(result, PROC_TIME_CLASS);

        if (userSysTimeInNanos == null) {
            warning(Message.GENERIC, "Retrieving user and system time is not supported in this FastR configuration.");
        }
        return result;
    }

    private static final long T = 1000;

    private static double asDoubleSecs(long tInNanos) {
        long tInMillis = tInNanos / (T * T);
        // round to millis (spec says)
        long rtInMillis = tInMillis < T ? tInMillis : (tInMillis * T) / T;
        return (double) rtInMillis / T;
    }
}
