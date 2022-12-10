/*
 * Copyright (c) 2016, 2019, Oracle and/or its affiliates. All rights reserved.
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
/**
 * A variant that prefers to use {@code .fastr.context.r} for R sub-processes but backs off to
 * {@code ProcessBuilder}.
 */
package com.oracle.truffle.r.nodes.builtin.base.system;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.r.runtime.context.RContext;

public class PreferContextSystemFunctionFactory extends SystemFunctionFactory {
    private ContextSystemFunctionFactory contextSystemFunctionFactory;
    private ProcessSystemFunctionFactory processSystemFunctionFactory;

    @Override
    public Object execute(VirtualFrame frame, String command, boolean intern, int timeoutSecs, RContext context) {
        CommandInfo commandInfo = checkRCommand(context, command);
        if (commandInfo == null) {
            if (processSystemFunctionFactory == null) {
                processSystemFunctionFactory = new ProcessSystemFunctionFactory();
            }
            return processSystemFunctionFactory.execute(frame, command, intern, timeoutSecs, context);
        } else {
            if (contextSystemFunctionFactory == null) {
                contextSystemFunctionFactory = new ContextSystemFunctionFactory();
            }
            return contextSystemFunctionFactory.execute(frame, command, intern, timeoutSecs, context);
        }
    }
}
