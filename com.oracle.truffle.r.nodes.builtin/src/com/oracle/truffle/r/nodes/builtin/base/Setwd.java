/*
 * Copyright (c) 2014, 2022, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.truffle.r.nodes.builtin.CastBuilder.Predef.notEmpty;
import static com.oracle.truffle.r.nodes.builtin.CastBuilder.Predef.stringValue;
import static com.oracle.truffle.r.runtime.RError.Message.CHAR_ARGUMENT;
import static com.oracle.truffle.r.runtime.RVisibility.OFF;
import static com.oracle.truffle.r.runtime.builtins.RBehavior.IO;
import static com.oracle.truffle.r.runtime.builtins.RBuiltinKind.INTERNAL;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleFile;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.r.nodes.builtin.RBuiltinNode;
import com.oracle.truffle.r.runtime.RError;
import com.oracle.truffle.r.runtime.builtins.RBuiltin;
import com.oracle.truffle.r.runtime.context.RContext;
import com.oracle.truffle.r.runtime.data.RNull;
import com.oracle.truffle.r.runtime.ffi.BaseRFFI;
import com.oracle.truffle.r.runtime.ffi.RFFIFactory;

@RBuiltin(name = "setwd", visibility = OFF, kind = INTERNAL, parameterNames = "path", behavior = IO)
public abstract class Setwd extends RBuiltinNode.Arg1 {

    static {
        Casts casts = new Casts(Setwd.class);
        casts.arg("path").defaultError(CHAR_ARGUMENT).mustBe(stringValue()).asStringVector().mustBe(notEmpty()).findFirst();
    }

    @Child private BaseRFFI.GetwdNode getwdNode = RFFIFactory.getBaseRFFI().createGetwdNode();
    @Child private BaseRFFI.SetwdNode setwdNode = RFFIFactory.getBaseRFFI().createSetwdNode();

    @Specialization
    @TruffleBoundary
    protected Object setwd(String path) {
        String owd = getwdNode.execute();
        RContext context = getRContext();
        TruffleFile nwd = context.getSafeTruffleFile(path);
        int rc = setwdNode.execute(nwd.toString());
        if (rc != 0) {
            throw error(RError.Message.CANNOT_CHANGE_DIRECTORY);
        } else {
            context.getEnv().setCurrentWorkingDirectory(nwd.getAbsoluteFile());
            return owd != null ? owd : RNull.instance;
        }
    }
}
