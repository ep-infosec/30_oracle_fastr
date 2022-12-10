/*
 * Copyright (c) 2016, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.r.nodes.builtin.base.system;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.r.nodes.builtin.base.system.ContextSystemFunctionFactoryFactory.ContextRSystemFunctionNodeGen;
import com.oracle.truffle.r.nodes.builtin.base.system.ContextSystemFunctionFactoryFactory.ContextRscriptSystemFunctionNodeGen;
import com.oracle.truffle.r.nodes.builtin.fastr.FastRContext;
import com.oracle.truffle.r.nodes.builtin.fastr.FastRContextFactory;
import com.oracle.truffle.r.runtime.RError;
import com.oracle.truffle.r.runtime.Utils;
import com.oracle.truffle.r.runtime.context.RContext;
import com.oracle.truffle.r.runtime.data.RDataFactory;
import com.oracle.truffle.r.runtime.data.RStringVector;
import com.oracle.truffle.r.runtime.nodes.RBaseNode;

/**
 * A variant that uses {@code .fastr.context.r} for R sub-processes and throws an error otherwise.
 * Used in systems that do not support {@code ProcessBuilder} and for R commands by
 * {@link PreferContextSystemFunctionFactory}, for better performance.
 */
public class ContextSystemFunctionFactory extends SystemFunctionFactory {
    private abstract static class ContextSystemFunctionNode extends RBaseNode {
        public abstract Object execute(VirtualFrame frame, Object args, Object env, Object intern, int timeoutSecs);

    }

    public abstract static class ContextRSystemFunctionNode extends ContextSystemFunctionNode {
        @Child private FastRContext.R contextRNode;

        private void initContextRNode() {
            if (contextRNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                contextRNode = insert(FastRContextFactory.RNodeGen.create());
            }
        }

        @Specialization
        protected Object systemFunction(VirtualFrame frame, RStringVector args, RStringVector env, boolean intern, int timeoutSecs) {
            initContextRNode();
            Object result = contextRNode.execute(frame, args, env, intern, timeoutSecs);
            return result;
        }
    }

    public abstract static class ContextRscriptSystemFunctionNode extends ContextSystemFunctionNode {
        @Child private FastRContext.Rscript contextRscriptNode;

        private void initContextRscriptNode() {
            if (contextRscriptNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                contextRscriptNode = insert(FastRContextFactory.RscriptNodeGen.create());
            }
        }

        @Specialization
        protected Object systemFunction(RStringVector args, RStringVector env, boolean intern, int timeoutSecs) {
            initContextRscriptNode();
            Object result = contextRscriptNode.execute(args, env, intern, timeoutSecs);
            return result;
        }
    }

    @Override
    public Object execute(VirtualFrame frame, String command, boolean intern, int timeoutSecs, RContext context) {
        log(command, "Context");
        CommandInfo commandInfo = checkRCommand(context, command);
        if (commandInfo != null) {
            ContextSystemFunctionNode node = Utils.unShQuote(commandInfo.command).equals("R") ? ContextRSystemFunctionNodeGen.create() : ContextRscriptSystemFunctionNodeGen.create();
            Object result = node.execute(frame, RDataFactory.createStringVector(commandInfo.args, RDataFactory.COMPLETE_VECTOR),
                            RDataFactory.createStringVector(commandInfo.envDefs, RDataFactory.COMPLETE_VECTOR), intern, timeoutSecs);
            return result;
        } else {
            throw cannotExecute(command);
        }
    }

    private static RError cannotExecute(String command) throws RError {
        CompilerDirectives.transferToInterpreter();
        throw RError.error(RError.NO_CALLER, RError.Message.GENERIC, command + " cannot be executed in a context");
    }
}
