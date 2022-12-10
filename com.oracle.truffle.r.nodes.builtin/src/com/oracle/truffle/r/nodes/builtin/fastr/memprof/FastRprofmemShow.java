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

import static com.oracle.truffle.r.nodes.builtin.CastBuilder.Predef.nullValue;
import static com.oracle.truffle.r.nodes.builtin.CastBuilder.Predef.singleElement;
import static com.oracle.truffle.r.nodes.builtin.CastBuilder.Predef.toBoolean;
import static com.oracle.truffle.r.nodes.builtin.fastr.memprof.FastRprofmem.castSnapshotArg;
import static com.oracle.truffle.r.nodes.builtin.fastr.memprof.FastRprofmem.castViewArg;
import static com.oracle.truffle.r.runtime.RVisibility.OFF;
import static com.oracle.truffle.r.runtime.builtins.RBehavior.IO;
import static com.oracle.truffle.r.runtime.builtins.RBuiltinKind.PRIMITIVE;

import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.r.nodes.builtin.RBuiltinNode;
import com.oracle.truffle.r.runtime.RError.Message;
import com.oracle.truffle.r.runtime.RRuntime;
import com.oracle.truffle.r.runtime.builtins.RBuiltin;
import com.oracle.truffle.r.runtime.data.RNull;

@RBuiltin(name = ".fastr.profmem.show", visibility = OFF, kind = PRIMITIVE, parameterNames = {"levels", "desc", "id", "printParents", "view", "snapshot"}, behavior = IO)
public abstract class FastRprofmemShow extends RBuiltinNode.Arg6 {

    static {
        Casts casts = new Casts(FastRprofmemShow.class);
        casts.arg("levels").asIntegerVector().mustBe(singleElement()).findFirst().replaceNA(Integer.MAX_VALUE);
        casts.arg("desc").asLogicalVector().mustBe(singleElement()).findFirst().map(toBoolean());
        casts.arg("id").returnIf(nullValue()).asIntegerVector().mustBe(singleElement()).findFirst().replaceNA(Integer.MAX_VALUE);
        casts.arg("printParents").asLogicalVector().mustBe(singleElement()).findFirst().map(toBoolean());
        castViewArg(casts);
        castSnapshotArg(casts);
    }

    @Override
    public Object[] getDefaultParameterValues() {
        return new Object[]{RRuntime.INT_NA, RRuntime.LOGICAL_TRUE, RNull.instance, RRuntime.LOGICAL_FALSE, FastRprofmem.STACKS_VIEW, RNull.instance};
    }

    @Specialization
    @SuppressWarnings("unused")
    public Object doProfMem(int levels, boolean desc, RNull n, boolean printParents, String view, TruffleObject snapshot) {
        // TODO: port to new instrumentation API, original code can be found in git history
        throw error(Message.GENERIC, ".fastr.profmem.show is not available.");
    }

    @Specialization
    @SuppressWarnings("unused")
    public Object doProfMem(int levels, boolean desc, int entryId, boolean printParents, String view, TruffleObject snapshot) {
        // TODO: port to new instrumentation API, original code can be found in git history
        throw error(Message.GENERIC, ".fastr.profmem.show is not available.");
    }
}
