/*
 * Copyright (c) 2016, 2018, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.r.nodes.builtin.fastr.memprof;

import static com.oracle.truffle.r.nodes.builtin.CastBuilder.Predef.eq;
import static com.oracle.truffle.r.nodes.builtin.CastBuilder.Predef.singleElement;
import static com.oracle.truffle.r.nodes.builtin.CastBuilder.Predef.toBoolean;
import static com.oracle.truffle.r.runtime.RVisibility.OFF;
import static com.oracle.truffle.r.runtime.builtins.RBehavior.IO;
import static com.oracle.truffle.r.runtime.builtins.RBuiltinKind.PRIMITIVE;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.r.nodes.builtin.RBuiltinNode;
import com.oracle.truffle.r.runtime.RError.Message;
import com.oracle.truffle.r.runtime.builtins.RBuiltin;

@RBuiltin(name = ".fastr.profmem", visibility = OFF, kind = PRIMITIVE, parameterNames = {"on"}, behavior = IO)
public abstract class FastRprofmem extends RBuiltinNode.Arg1 {

    public static final String STACKS_VIEW = "stacks";
    public static final String HOTSPOTS_VIEW = "hotspots";

    static {
        Casts casts = new Casts(FastRprofmem.class);
        casts.arg("on").asLogicalVector().mustBe(singleElement()).findFirst().map(toBoolean());
    }

    static void castViewArg(Casts casts) {
        casts.arg("view").asStringVector().mustBe(singleElement()).findFirst().mustBe(eq("stacks").or(eq("hotspots")));
    }

    static void castSnapshotArg(Casts casts) {
        casts.arg("snapshot").mustBe(TruffleObject.class);
    }

    @Specialization
    @TruffleBoundary
    public Object doProfMem(@SuppressWarnings("unused") boolean on) {
        // TODO: port to new instrumentation API, original code can be found in git history
        throw error(Message.GENERIC, ".fastr.profmem is not available.");
    }
}
