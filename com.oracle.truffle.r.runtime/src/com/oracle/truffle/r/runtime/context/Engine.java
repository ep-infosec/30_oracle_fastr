/*
 * Copyright (c) 2013, 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.r.runtime.context;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.exception.AbstractTruffleException;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.interop.ExceptionType;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.ExecutableNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.r.runtime.ArgumentsSignature;
import com.oracle.truffle.r.runtime.RCaller;
import com.oracle.truffle.r.runtime.RError;
import com.oracle.truffle.r.runtime.RInternalError;
import com.oracle.truffle.r.runtime.RSource;
import com.oracle.truffle.r.runtime.context.RContext.ConsoleIO;
import com.oracle.truffle.r.runtime.data.RExpression;
import com.oracle.truffle.r.runtime.data.RFunction;
import com.oracle.truffle.r.runtime.data.RPairList;
import com.oracle.truffle.r.runtime.data.RPromise;
import com.oracle.truffle.r.runtime.env.REnvironment;
import com.oracle.truffle.r.runtime.nodes.RNode;

public interface Engine {

    Source GET_CONTEXT = RSource.fromTextInternal("<<<get_context>>>", RSource.Internal.GET_CONTEXT);

    @ExportLibrary(InteropLibrary.class)
    class ParseException extends AbstractTruffleException {
        private static final long serialVersionUID = 1L;

        @SuppressWarnings("serial") private final Source source;
        private final String token;
        private final String substring;
        private final int line;

        public ParseException(Throwable cause, Source source, String token, String substring, int line) {
            super("parse exception", cause, UNLIMITED_STACK_TRACE, getLocation(line, source));
            this.source = source;
            this.token = token;
            this.substring = substring;
            this.line = line;
        }

        @ExportMessage
        @SuppressWarnings("static-method")
        final boolean isException() {
            return true;
        }

        @ExportMessage
        final RuntimeException throwException() {
            throw this;
        }

        @ExportMessage
        @SuppressWarnings("static-method")
        final ExceptionType getExceptionType() {
            return ExceptionType.PARSE_ERROR;
        }

        @ExportMessage
        final boolean hasSourceLocation() {
            return source != null;
        }

        @TruffleBoundary
        @ExportMessage(name = "getSourceLocation")
        final SourceSection getSourceSection() throws UnsupportedMessageException {
            if (source == null) {
                throw UnsupportedMessageException.create();
            }
            return source.createSection(line);
        }

        @TruffleBoundary
        public final RError throwAsRError() {
            if (source.getLineCount() == 1) {
                throw RError.error(RError.NO_CALLER, RError.Message.UNEXPECTED, token, substring);
            } else {
                throw RError.error(RError.NO_CALLER, RError.Message.UNEXPECTED_LINE, token, substring, line);
            }
        }

        @TruffleBoundary
        public final void report(OutputStream output) {
            try {
                output.write(getErrorMessage().getBytes(StandardCharsets.UTF_8));
            } catch (IOException e) {
                throw new RInternalError(e, "error while printing parse exception");
            }
        }

        @TruffleBoundary
        public final void report(ConsoleIO console) {
            console.println(getErrorMessage());
        }

        public final String getErrorMessage() {
            String msg;
            if (source.getLineCount() == 1) {
                msg = String.format(RError.Message.UNEXPECTED.message, token, substring);
            } else {
                msg = String.format(RError.Message.UNEXPECTED_LINE.message, token, substring, line);
            }
            return "Error: " + msg;
        }

        private static Node getLocation(int line, Source source) {
            if (line <= 0 || line > source.getLineCount()) {
                return null;
            } else {
                SourceSection section = source.createSection(line);
                return new Node() {
                    @Override
                    public SourceSection getSourceSection() {
                        return section;
                    }
                };
            }
        }
    }

    @ExportLibrary(InteropLibrary.class)
    final class IncompleteSourceException extends ParseException {
        private static final long serialVersionUID = -6688699706193438722L;

        public IncompleteSourceException(Throwable cause, Source source, String token, String substring, int line) {
            super(cause, source, token, substring, line);
        }

        @ExportMessage
        @SuppressWarnings("static-method")
        boolean isExceptionIncompleteSource() {
            return true;
        }
    }

    /**
     * Make the engine ready for evaluations.
     */
    void activate(REnvironment.ContextStateImpl stateREnvironment);

    void deactivate();

    interface Timings {
        /**
         * Elapsed time of runtime.
         *
         * @return elapsed time in nanosecs.
         */
        long elapsedTimeInNanos();

        /**
         * Return user and system times for any spawned child processes in nanosecs, {@code < 0}
         * means not available.
         */
        long[] childTimesInNanos();

        /**
         * Return user/sys time for this engine, {@code < 0} means not available..
         */
        long[] userSysTimeInNanos();

    }

    /**
     * Wrapper for GNU-R compatible metadata about the parse tree of R code.
     *
     * @see com.oracle.truffle.r.runtime.nodes.RCodeBuilder
     */
    final class ParserMetadata {
        private final int[] data;
        private final String[] tokens;
        private final String[] text;

        public ParserMetadata(int[] data, String[] tokens, String[] text) {
            this.data = data;
            this.tokens = tokens;
            this.text = text;
        }

        public int[] getData() {
            return data;
        }

        public String[] getTokens() {
            return tokens;
        }

        public String[] getText() {
            return text;
        }
    }

    final class ParsedExpression {
        private final RExpression expr;
        private final ParserMetadata parseData;

        public ParsedExpression(RExpression expr, ParserMetadata parseData) {
            this.expr = expr;
            this.parseData = parseData;
        }

        public RExpression getExpression() {
            return expr;
        }

        public ParserMetadata getParseData() {
            return parseData;
        }
    }

    /**
     * Return the timing information for this engine.
     */
    Timings getTimings();

    /**
     * Parse an R expression and return an {@link RExpression} object representing the Truffle ASTs
     * for the components.
     */
    ParsedExpression parse(Source source, boolean keepSource) throws ParseException;

    /**
     * This is the external interface from
     * {@link org.graalvm.polyglot.Context#eval(org.graalvm.polyglot.Source)}. It is required to
     * return a {@link CallTarget} which may be cached for future use, and the
     * {@link org.graalvm.polyglot.Context} is responsible for actually invoking the call target.
     */
    CallTarget parseToCallTarget(Source source, MaterializedFrame executionFrame) throws ParseException;

    /**
     * This is the external interface from the truffle language implementation. It is required to
     * return a {@link CallTarget} which may be cached for future use, and it accepts given
     * arguments.
     */
    CallTarget parseToCallTargetWithArguments(Source source, List<String> argumentNames) throws ParseException;

    /**
     * Returns ASTs representing given source. The node is meant to be inserted into existing AST
     * and executed as part of it.
     */
    ExecutableNode parseToExecutableNode(Source source) throws ParseException;

    /**
     * Parse and evaluate {@code rscript} in {@code frame}. {@code printResult == true}, the result
     * of the evaluation is printed to the console.
     *
     * @param sourceDesc a {@link Source} object that describes the input to be parsed
     * @param frame the frame in which to evaluate the input
     * @param printResult {@code true} iff the result of the evaluation should be printed to the
     *            console
     * @return the object returned by the evaluation or {@code null} if an error occurred.
     */
    Object parseAndEval(Source sourceDesc, MaterializedFrame frame, boolean printResult) throws ParseException;

    default Object eval(RExpression expr, REnvironment envir, RCaller caller) {
        return eval(expr, envir, null, caller, null);
    }

    /**
     * Support for the {@code eval} {@code .Internal}. If the {@code caller} argument is null, it is
     * taken from the environment's frame.
     */
    Object eval(RExpression expr, REnvironment envir, Object callerFrame, RCaller caller, RFunction function);

    default Object eval(RPairList expr, REnvironment envir, RCaller caller) {
        return eval(expr, envir, null, caller, null);
    }

    /**
     * Variant of {@link #eval(RExpression, REnvironment, RCaller)} for a single language element.
     * If the {@code caller} argument is null, it is taken from the environment's frame.
     */
    Object eval(RPairList expr, REnvironment envir, Object callerFrame, RCaller caller, RFunction function);

    /**
     * Evaluate {@code expr} in {@code frame}.
     */
    Object eval(RExpression expr, MaterializedFrame frame);

    /**
     * Variant of {@link #eval(RExpression, MaterializedFrame)} for a single language element.
     */
    Object eval(RPairList expr, MaterializedFrame frame);

    /**
     * Variant of {@link #eval(RPairList, MaterializedFrame)} where we already have the
     * {@link RFunction} and the evaluated arguments. {@code frame} may be {@code null} in which
     * case the current frame is used). In many cases {@code frame} may not represent the current
     * call stack, for example many S4-related evaluations set {@code frame} to the {@code methods}
     * namespace, but the current stack is not empty. So when {@code frame} is not {@code null} a
     * {@code caller} should be passed to maintain the call stack correctly. {@code names} string
     * vector describing (optional) argument names
     *
     * @param names signature of the given parameters, may be {@code null} in which case the empty
     *            signature of correct cardinality shall be used.
     * @param evalPromises whether to evaluate promises in args array before calling the function.
     */
    Object evalFunction(RFunction func, MaterializedFrame frame, RCaller caller, boolean evalPromises, ArgumentsSignature names, Object... args);

    Object evalPromise(RPromise promise);

    /**
     * Checks for the existence of (startup/shutdown) function {@code name} and, if present, invokes
     * it using the given code.
     */
    void checkAndRunStartupShutdownFunction(String name, String code);

    /**
     * Wraps the Truffle AST in {@code body} in an anonymous function and returns a
     * {@link RootCallTarget} for it.
     *
     * N.B. For certain expressions, there might be some value in enclosing the wrapper function in
     * a specific lexical scope. E.g., as a way to access names in the expression known to be
     * defined in that scope.
     *
     * @param body The AST for the body of the wrapper, i.e., the expression being evaluated.
     */
    RootCallTarget makePromiseCallTarget(RNode body, String funName);

    /**
     * Used by Truffle debugger; invokes the internal "print" support in R for {@code value}.
     * Essentially this is equivalent to {@link #evalFunction} using the {@code "print"} function.
     */
    void printResult(RContext ctx, Object value);

    /**
     * Return the "global" frame for this {@link Engine}, aka {@code globalEnv}.
     *
     */
    MaterializedFrame getGlobalFrame();
}
