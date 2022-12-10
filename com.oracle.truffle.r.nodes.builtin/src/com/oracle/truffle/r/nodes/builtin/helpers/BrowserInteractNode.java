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
package com.oracle.truffle.r.nodes.builtin.helpers;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.r.runtime.data.nodes.attributes.GetAttributeNode;
import com.oracle.truffle.r.runtime.JumpToTopLevelException;
import com.oracle.truffle.r.runtime.RArguments;
import com.oracle.truffle.r.runtime.RCaller;
import com.oracle.truffle.r.runtime.RError;
import com.oracle.truffle.r.runtime.RRuntime;
import com.oracle.truffle.r.runtime.RSource;
import com.oracle.truffle.r.runtime.RSrcref;
import com.oracle.truffle.r.runtime.ReturnException;
import com.oracle.truffle.r.runtime.Utils;
import com.oracle.truffle.r.runtime.context.Engine.IncompleteSourceException;
import com.oracle.truffle.r.runtime.context.Engine.ParseException;
import com.oracle.truffle.r.runtime.context.RContext;
import com.oracle.truffle.r.runtime.context.RContext.ConsoleIO;
import com.oracle.truffle.r.runtime.data.RFunction;
import com.oracle.truffle.r.runtime.data.RIntVector;
import com.oracle.truffle.r.runtime.data.RNull;
import com.oracle.truffle.r.runtime.data.RPairList;
import com.oracle.truffle.r.runtime.data.RStringVector;
import com.oracle.truffle.r.runtime.env.REnvironment;
import com.oracle.truffle.r.runtime.instrument.InstrumentationState.BrowserState;
import com.oracle.truffle.r.runtime.nodes.RCodeBuilder;
import com.oracle.truffle.r.runtime.nodes.RSyntaxNode;

/**
 * The interactive component of the {@code browser} function.
 *
 * This is called in two ways:
 * <ol>
 * <li>implicitly when a function has had {@code debug} called</li>
 * <li>explicitly by a call in the source code. N.B. in this case we must enable debugging
 * (instrumentation) because a {@code n} command must stop at the next statement.</li>
 * </ol>
 *
 */
@GenerateUncached
public abstract class BrowserInteractNode extends Node {

    public static final int STEP = 0;
    public static final int NEXT = 1;
    public static final int CONTINUE = 2;
    public static final int FINISH = 3;

    public abstract int execute(MaterializedFrame frame, RCaller caller);

    @Specialization
    protected static int interact(MaterializedFrame mFrame, RCaller caller,
                    @Cached GetAttributeNode getSrcRefAttrNode,
                    @Cached GetAttributeNode getSrcFileAttrNode) {
        CompilerDirectives.transferToInterpreter();
        ConsoleIO ch = RContext.getInstance().getConsole();
        BrowserState browserState = RContext.getInstance().stateInstrumentation.getBrowserState();
        String savedPrompt = ch.getPrompt();
        RFunction callerFunction = RArguments.getFunction(mFrame);
        // we may be at top level where there is not caller
        boolean callerIsDebugged = callerFunction == null || DebugHandling.isDebugged(callerFunction);
        int exitMode = NEXT;
        RCaller currentCaller = caller;
        if (currentCaller == null) {
            currentCaller = RCaller.topLevel;
        }
        RCodeBuilder<RSyntaxNode> builder = RContext.getASTBuilder();
        RCaller browserCaller = RCaller.create(null, currentCaller, builder.call(RSyntaxNode.INTERNAL, builder.lookup(RSyntaxNode.INTERNAL, "browser", true)));
        try {
            browserState.setInBrowser(browserCaller);
            LW: while (true) {
                ch.setPrompt(browserPrompt(currentCaller.getDepth()));
                String input = ch.readLine();
                if (input != null) {
                    input = input.trim();
                }
                if (input == null || input.length() == 0) {
                    byte browserNLdisabledVec = RRuntime.asLogicalObject(RContext.getInstance().stateROptions.getValue("browserNLdisabled"));
                    if (!RRuntime.fromLogical(browserNLdisabledVec)) {
                        input = browserState.lastEmptyLineCommand();
                    }
                }
                switch (input) {
                    case "c":
                    case "cont":
                        exitMode = CONTINUE;
                        break LW;
                    case "n":
                        exitMode = NEXT;
                        // don't enable debugging if at top level
                        if (!callerIsDebugged) {
                            DebugHandling.enableDebug(callerFunction, "", "", true, true);
                        }
                        browserState.setLastEmptyLineCommand("n");
                        break LW;
                    case "s":
                        exitMode = STEP;
                        // don't enable debugging if at top level
                        if (!callerIsDebugged) {
                            DebugHandling.enableDebug(callerFunction, "", "", true, true);
                        }
                        browserState.setLastEmptyLineCommand("s");
                        break LW;
                    case "f":
                        exitMode = FINISH;
                        break LW;
                    case "Q":
                        throw new JumpToTopLevelException();
                    case "help":
                        printHelp(ch);
                        break;
                    case "where": {
                        if (currentCaller.getDepth() > 1) {
                            Object stack = Utils.createTraceback(0);
                            // browser inverts frame depth
                            int idepth = 1;
                            while (stack != RNull.instance) {
                                RPairList pl = (RPairList) stack;
                                RStringVector element = (RStringVector) pl.car();
                                ch.printf("where %d%s: %s%n", idepth, getSrcinfo(element, getSrcRefAttrNode, getSrcFileAttrNode), element.getDataAt(0));
                                idepth++;
                                stack = pl.cdr();
                            }
                        }
                        ch.println("");
                        break;
                    }

                    default:
                        StringBuilder sb = new StringBuilder(input);
                        while (true) {
                            try {
                                RContext.getEngine().parseAndEval(RSource.fromTextInternal(sb.toString(), RSource.Internal.BROWSER_INPUT), mFrame, true);
                            } catch (IncompleteSourceException e) {
                                // read another line of input
                                ch.setPrompt("+ ");
                                sb.append('\n');
                                sb.append(ch.readLine());
                                // The only continuation in the while loop
                                continue;
                            } catch (ParseException e) {
                                e.report(ch);
                                continue LW;
                            } catch (RError e) {
                                continue LW;
                            } catch (ReturnException e) {
                                exitMode = NEXT;
                                break LW;
                            }
                            continue LW;
                        }
                }
            }
        } finally {
            ch.setPrompt(savedPrompt);
            browserState.setInBrowser(null);
        }
        return exitMode;
    }

    private static String getSrcinfo(RStringVector element, GetAttributeNode getSrcRefAttrNode, GetAttributeNode getSrcFileAttrNode) {
        Object srcref = getSrcRefAttrNode.execute(element, RRuntime.R_SRCREF);
        if (srcref != null) {
            RIntVector lloc = (RIntVector) srcref;
            Object srcfile = getSrcFileAttrNode.execute(lloc, RRuntime.R_SRCFILE);
            if (srcfile != null) {
                REnvironment env = (REnvironment) srcfile;
                return " at " + RRuntime.asString(env.get(RSrcref.SrcrefFields.filename.name())) + "#" + lloc.getDataAt(0);
            }
        }
        return "";
    }

    private static String browserPrompt(int depth) {
        return "Browse[" + depth + "]> ";
    }

    private static void printHelp(ConsoleIO out) {
        out.println("n          next");
        out.println("s          step into");
        out.println("f          finish");
        out.println("c or cont  continue");
        out.println("Q          quit");
        out.println("where      show stack");
        out.println("help       show help");
        out.println("<expr>     evaluate expression");
    }
}
