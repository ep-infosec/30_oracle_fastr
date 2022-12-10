/*
 * Copyright (c) 2015, 2022, Oracle and/or its affiliates. All rights reserved.
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
import static com.oracle.truffle.r.runtime.builtins.RBuiltinKind.INTERNAL;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.r.nodes.builtin.RBuiltinNode;
import com.oracle.truffle.r.runtime.builtins.RBuiltin;
import com.oracle.truffle.r.runtime.context.RContext.ConsoleIO;
import com.oracle.truffle.r.runtime.data.RStringVector;

@RBuiltin(name = "readline", kind = INTERNAL, parameterNames = "prompt", behavior = IO)
public abstract class Readline extends RBuiltinNode.Arg1 {

    static {
        Casts casts = new Casts(Readline.class);
        casts.arg("prompt").asStringVector().findFirst("");
    }

    @Specialization
    @TruffleBoundary
    protected String readline(RStringVector prompt) {
        if (!getRContext().isInteractive()) {
            return "";
        }
        ConsoleIO consoleHandler = getRContext().getConsole();
        String savedPrompt = consoleHandler.getPrompt();
        consoleHandler.setPrompt(prompt.getDataAt(0));
        String input = consoleHandler.readLine();
        consoleHandler.setPrompt(savedPrompt);
        return input;
    }
}
