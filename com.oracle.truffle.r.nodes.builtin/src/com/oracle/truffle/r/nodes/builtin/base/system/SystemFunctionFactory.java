/*
 * Copyright (c) 2016, 2022, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.truffle.r.runtime.RLogger.LOGGER_SYSTEM_FUNCTION;

import java.util.ArrayList;
import java.util.logging.Level;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleLogger;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.r.runtime.REnvVars;
import com.oracle.truffle.r.runtime.RLogger;
import com.oracle.truffle.r.runtime.RSuicide;
import com.oracle.truffle.r.runtime.SuppressFBWarnings;
import com.oracle.truffle.r.runtime.Utils;
import com.oracle.truffle.r.runtime.context.RContext;

public abstract class SystemFunctionFactory {
    private static String kind;
    private static SystemFunctionFactory theInstance;

    static {
        String className = System.getProperty("fastr.systemfunction.factory.class", "com.oracle.truffle.r.nodes.builtin.base.system.ProcessSystemFunctionFactory");
        try {
            theInstance = (SystemFunctionFactory) Class.forName(className).getDeclaredConstructor().newInstance();
        } catch (Exception ex) {
            // CheckStyle: stop system..print check
            RSuicide.rSuicide("Failed to instantiate class: " + className);
        }
    }

    private static final TruffleLogger LOGGER = RLogger.getLogger(LOGGER_SYSTEM_FUNCTION);

    @TruffleBoundary
    public static SystemFunctionFactory getInstance() {
        return theInstance;
    }

    /**
     * Implements the system {@code .Internal}. If {@code intern} is {@code true} the result is a
     * character vector containing the output of the process, with a {@code status} attribute
     * carrying the return code, else it is just the return code.
     *
     * {@code command} is a string with args separated by spaces with the first element enclosed in
     * single quotes.
     */
    public abstract Object execute(VirtualFrame frame, String command, boolean intern, int timeoutSecs, RContext context);

    protected void log(String command, String useKind) {
        checkObsoleteEnvVar();
        if (LOGGER.isLoggable(Level.FINE)) {
            LOGGER.log(Level.FINE, "FastR system ({0}): {1}", new Object[]{useKind, command});
        }
    }

    @TruffleBoundary
    private static void checkObsoleteEnvVar() {
        if (RContext.getInstance().stateREnvVars.getMap().get("FASTR_LOG_SYSTEM") != null) {
            System.out.println("WARNING: The FASTR_LOG_SYSTEM env variable was discontinued.\n" +
                            "You can rerun FastR with --log.R.com.oracle.truffle.r.systemFunction.level=FINE");
        }
    }

    @SuppressFBWarnings(value = "UWF_UNWRITTEN_FIELD", justification = "incomplete implementation")
    protected void log(String command) {
        log(command, kind);
    }

    /**
     * Encodes information collected by analyzing the command pass to {@code .Internal(system)}.
     */
    public static final class CommandInfo {
        public final String[] envDefs;
        public final String command;
        public final String[] args;

        public CommandInfo(String[] envDefs, String command, String[] args) {
            this.envDefs = envDefs;
            this.command = command;
            this.args = args;
        }
    }

    /**
     * Analyzes {@code command} to see if it is an R command that we can execute in a context.
     *
     * @return a {@link CommandInfo} object if the command can be executed in a context, else
     *         {@code null}.
     */
    @TruffleBoundary
    public static CommandInfo checkRCommand(RContext context, String command) {
        CommandInfo commandInfo = null;
        String[] parts = command.split(" ");
        /* The actual command may be prefixed by environment variable settings of the form X=Y */
        int i = 0;
        while (parts[i].contains("=")) {
            i++;
        }
        String rcommand = isFastR(context, parts[i]);
        if (rcommand == null) {
            return null;
        } else {
            String[] envDefs = new String[i];
            if (i != 0) {
                System.arraycopy(parts, 0, envDefs, 0, i);
            }
            String[] args = new String[parts.length - i - 1];
            if (args.length > 0) {
                System.arraycopy(parts, i + 1, args, 0, args.length);
            }
            commandInfo = new CommandInfo(envDefs, rcommand, args);
        }
        // check for and emulate selected R CMD cmd commands
        if (commandInfo.args.length > 0) {
            if (commandInfo.args[0].equals("CMD")) {
                switch (commandInfo.args[1]) {
                    case "INSTALL":
                        // INSTALL pipes in "tools:::.install_packages()"
                        // We use "-e tools:::.install_packages()" as its simpler
                        ArrayList<String> newArgsList = new ArrayList<>();
                        newArgsList.add("--no-restore");
                        newArgsList.add("--no-echo");
                        newArgsList.add("-e");
                        newArgsList.add("tools:::.install_packages()");
                        newArgsList.add("--args");
                        StringBuilder sb = new StringBuilder();
                        i = 2;
                        while (i < commandInfo.args.length) {
                            String arg = commandInfo.args[i];
                            if (arg.equals("<") || arg.contains(">")) {
                                break;
                            }
                            sb.append("nextArg");
                            sb.append(arg);
                            i++;
                        }
                        if (sb.length() > 0) {
                            newArgsList.add(sb.toString());
                        }
                        while (i < commandInfo.args.length) {
                            newArgsList.add(commandInfo.args[i]);
                            i++;
                        }
                        String[] newArgs = new String[newArgsList.size()];
                        newArgsList.toArray(newArgs);
                        commandInfo = new CommandInfo(commandInfo.envDefs, commandInfo.command, newArgs);
                        break;

                    default:
                        commandInfo = null;

                }
            }
        }
        return commandInfo;
    }

    /**
     * Returns {@code true} iff, {@code command} is {@code R} or {@code Rscript}.
     */
    private static String isFastR(RContext context, String command) {
        // strip off quotes
        String xc = Utils.unShQuote(command);
        if (xc.equals("R") || xc.equals("Rscript")) {
            return xc;
        }
        // often it is an absolute path
        String rhome = REnvVars.rHome(context);
        if (isFullPath(context, rhome, "Rscript", xc)) {
            return "Rscript";
        }
        if (isFullPath(context, rhome, "R", xc)) {
            return "R";
        }
        return null;
    }

    private static boolean isFullPath(RContext context, String rhome, String rcmd, String command) {
        String rpath = context.getSafeTruffleFile(rhome).resolve("bin").resolve(rcmd).resolve(rcmd).toString();
        String cpath = context.getSafeTruffleFile(command).getAbsoluteFile().toString();
        if (cpath.equals(rpath)) {
            return true;
        }
        return false;
    }
}
