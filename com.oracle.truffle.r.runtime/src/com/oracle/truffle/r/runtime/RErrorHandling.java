/*
 * Copyright (c) 1995-2015, The R Core Team
 * Copyright (c) 2003, The R Foundation
 * Copyright (c) 2015, 2022, Oracle and/or its affiliates
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, a copy is available at
 * https://www.R-project.org/Licenses/
 */
package com.oracle.truffle.r.runtime;

import static com.oracle.truffle.r.runtime.RError.NO_CALLER;
import static com.oracle.truffle.r.runtime.RError.findParentRBase;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.TruffleStackTrace;
import com.oracle.truffle.api.TruffleStackTraceElement;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.r.runtime.RError.Message;
import com.oracle.truffle.r.runtime.context.RContext;
import com.oracle.truffle.r.runtime.data.RDataFactory;
import com.oracle.truffle.r.runtime.data.RExpression;
import com.oracle.truffle.r.runtime.data.RFunction;
import com.oracle.truffle.r.runtime.data.RIntVector;
import com.oracle.truffle.r.runtime.data.RList;
import com.oracle.truffle.r.runtime.data.RMissing;
import com.oracle.truffle.r.runtime.data.RNull;
import com.oracle.truffle.r.runtime.data.RPairList;
import com.oracle.truffle.r.runtime.data.RStringVector;
import com.oracle.truffle.r.runtime.env.REnvironment;
import com.oracle.truffle.r.runtime.env.REnvironment.PutException;
import com.oracle.truffle.r.runtime.interop.FastRInteropTryException;
import com.oracle.truffle.r.runtime.nodes.RBaseNode;

/**
 * The details of error handling, including condition handling. Derived from GnUR src/main/errors.c.
 * The public methods in this class are primarily intended for use by the {@code .Internal}
 * functions in {@code ConditionFunctions}. Generally the {@link RError} class should be used for
 * error and warning reporting.
 *
 * FastR does not have access to the call that generated the error or warning as an AST (cf GnuR's
 * CLOSXP pairlist), only the {@link SourceSection} associated with the call. There is a need to
 * pass a value that denotes the call back out to R, e.g. as an argument to
 * {@code .handleSimpleError}. The R code mostly treats this as an opaque object, typically passing
 * it back into a {@code .Internal} that calls this class, but it sometimes calls {@code deparse} on
 * the value if it is not {@link RNull#instance}. Either way it must be a valid {@link RType} to be
 * passed as an argument. For better or worse, we use an {@link RPairList}. We handle the
 * {@code deparse} special case explicitly in our implementation of {@code deparse}.
 * <p>
 * TODO Consider using an {@link RPairList} object to denote the call (somehow).
 */
public class RErrorHandling {

    private static final int IN_HANDLER = 3;
    private static final RStringVector RESTART_CLASS = RDataFactory.createStringVectorFromScalar("restart");

    private static class Warnings {
        private final ArrayList<Warning> list = new ArrayList<>();

        int size() {
            return list.size();
        }

        Warning get(int index) {
            return list.get(index);
        }

        void add(Warning warning) {
            list.add(warning);
        }

        void clear() {
            list.clear();
        }
    }

    /**
     * Holds all the context-specific state that is relevant for error/warnings. Simple value class
     * for which geterrs/setters are unnecessary.
     */
    public static class ContextStateImpl implements RContext.ContextState {
        /**
         * Values is either NULL or an RPairList, for {@code restarts}.
         */
        private Object restartStack = RNull.instance;
        /**
         * Values is either NULL or an RPairList, for {@code conditions}.
         */
        private Object handlerStack = RNull.instance;
        /**
         * Current list of (deferred) warnings.
         */
        private final Warnings warnings = new Warnings();
        /**
         * Max warnings accumulated.
         */
        private final int maxWarnings = 50;
        /**
         * Set/get by seterrmessage/geterrmessage builtins.
         */
        private String errMsg;
        /**
         * {@code true} if we are already processing an error.
         */
        private int inError;
        /**
         * {@code true} if we are already processing a warning.
         */
        private boolean inWarning;
        /**
         * {@code true} if the warning should be output immediately.
         */
        private boolean immediateWarning;
        /**
         * {@code true} if the warning should be output on one line.
         */
        @SuppressWarnings("unused") private boolean noBreakWarning;
        /**
         * {@code true} if in {@link #printWarnings}.
         */
        private boolean inPrintWarning;

        /**
         * {@code .signalSimpleWarning} in "conditions.R".
         */
        private RFunction dotSignalSimpleWarning;
        private RFunction dotHandleSimpleError;

        /**
         * Initialize and return the value of {@link #dotSignalSimpleWarning}. This is lazy because
         * when this instance is created, the {@link REnvironment} context state has not been set
         * up, so we can't look up anything in the base env.
         */
        private RFunction getDotSignalSimpleWarning() {
            if (dotSignalSimpleWarning == null) {
                CompilerDirectives.transferToInterpreter();
                String name = ".signalSimpleWarning";
                Object f = REnvironment.baseEnv().findFunction(name);
                dotSignalSimpleWarning = (RFunction) RContext.getRRuntimeASTAccess().forcePromise(name, f);
            }
            return dotSignalSimpleWarning;
        }

        private RFunction getDotHandleSimpleError() {
            if (dotHandleSimpleError == null) {
                CompilerDirectives.transferToInterpreter();
                String name = ".handleSimpleError";
                Object f = REnvironment.baseEnv().findFunction(name);
                dotHandleSimpleError = (RFunction) RContext.getRRuntimeASTAccess().forcePromise(name, f);
            }
            return dotHandleSimpleError;
        }

        public static ContextStateImpl newContextState() {
            return new ContextStateImpl();
        }
    }

    /**
     * A temporary class used to accumulate warnings in deferred mode, Eventually these are
     * converted to a list and stored in {@code last.warning} in {@code baseenv}.
     */
    private static class Warning {
        final String message;
        final Object call;

        Warning(String message, Object call) {
            this.message = message;
            this.call = call;
        }
    }

    public static final class HandlerStacks {
        public final Object handlerStack;
        public final Object restartStack;

        private HandlerStacks(Object handlerStack, Object restartStack) {
            this.handlerStack = handlerStack;
            this.restartStack = restartStack;
        }
    }

    private static final Object RESTART_TOKEN = new Object();

    private static ContextStateImpl getRErrorHandlingState() {
        return getRErrorHandlingState(RContext.getInstance());
    }

    private static ContextStateImpl getRErrorHandlingState(RContext ctx) {
        return ctx.stateRErrorHandling;
    }

    public static HandlerStacks resetAndGetHandlerStacks() {
        HandlerStacks result = new HandlerStacks(getRErrorHandlingState().handlerStack, getRErrorHandlingState().restartStack);
        resetStacks();
        return result;
    }

    public static Object getHandlerStack() {
        return getHandlerStack(RContext.getInstance());
    }

    public static Object getHandlerStack(RContext ctx) {
        return getRErrorHandlingState(ctx).handlerStack;
    }

    public static Object getRestartStack() {
        return getRestartStack(RContext.getInstance());
    }

    public static Object getRestartStack(RContext ctx) {
        return getRErrorHandlingState(ctx).restartStack;
    }

    /**
     * Resets the handler stacks for a "top-level" evaluation ({@code Rf_tryEval} in the R FFI. This
     * must be preceded by calls to {@link #getHandlerStack} and {@link #getRestartStack()} and
     * followed by {@link #restoreStacks} after the evaluation completes.
     */
    public static void resetStacks() {
        resetStacks(RContext.getInstance());
    }

    public static void resetStacks(RContext ctx) {
        ContextStateImpl errorHandlingState = getRErrorHandlingState(ctx);
        errorHandlingState.handlerStack = RNull.instance;
        errorHandlingState.restartStack = RNull.instance;
    }

    public static void restoreHandlerStacks(HandlerStacks handlerStacks) {
        restoreStacks(handlerStacks.handlerStack, handlerStacks.restartStack);
    }

    @TruffleBoundary
    public static void restoreStacks(Object savedHandlerStack, Object savedRestartStack) {
        ContextStateImpl errorHandlingState = getRErrorHandlingState();
        errorHandlingState.handlerStack = savedHandlerStack;
        errorHandlingState.restartStack = savedRestartStack;
    }

    public static void restoreHandlerStack(Object savedHandlerStack) {
        ContextStateImpl errorHandlingState = getRErrorHandlingState();
        errorHandlingState.handlerStack = savedHandlerStack;
    }

    /**
     * Fast-path version of {@link #restoreHandlerStack(Object)}.
     */
    public static void restoreHandlerStack(Object savedHandlerStack, RContext ctx) {
        ContextStateImpl errorHandlingState = getRErrorHandlingState(ctx);
        errorHandlingState.handlerStack = savedHandlerStack;
    }

    public static void restoreRestartStack(Object savedRestartStack) {
        ContextStateImpl errorHandlingState = getRErrorHandlingState();
        errorHandlingState.restartStack = savedRestartStack;
    }

    /**
     * Fast-path version of {@link #restoreRestartStack(Object)}.
     */
    public static void restoreRestartStack(Object savedRestartStack, RContext ctx) {
        ContextStateImpl errorHandlingState = getRErrorHandlingState(ctx);
        errorHandlingState.restartStack = savedRestartStack;
    }

    public static Object createHandlers(RStringVector classes, RList handlers, REnvironment parentEnv, Object target, byte calling) {
        CompilerAsserts.neverPartOfCompilation();
        Object oldStack = getHandlerStack();
        Object newStack = oldStack;
        RList result = RDataFactory.createList(new Object[]{RNull.instance, RNull.instance, RNull.instance});
        int n = handlers.getLength();
        for (int i = n - 1; i >= 0; i--) {
            String klass = classes.getDataAt(i);
            Object handler = handlers.getDataAt(i);
            RList entry = mkHandlerEntry(klass, parentEnv, handler, target, result, calling);
            newStack = RDataFactory.createPairList(entry, newStack);
        }
        getRErrorHandlingState().handlerStack = newStack;
        return oldStack;
    }

    private static final int ENTRY_CLASS = 0;
    private static final int ENTRY_CALLING_ENVIR = 1;
    private static final int ENTRY_HANDLER = 2;
    private static final int ENTRY_TARGET_ENVIR = 3;
    private static final int ENTRY_RETURN_RESULT = 4;

    private static final int RESULT_COND = 0;
    private static final int RESULT_CALL = 1;
    private static final int RESULT_HANDLER = 2;

    private static RList mkHandlerEntry(String klass, REnvironment parentEnv, Object handler, Object rho, RList result, byte calling) {
        Object[] data = new Object[5];
        data[ENTRY_CLASS] = klass;
        data[ENTRY_CALLING_ENVIR] = parentEnv;
        data[ENTRY_HANDLER] = handler;
        data[ENTRY_TARGET_ENVIR] = rho;
        data[ENTRY_RETURN_RESULT] = result;
        RList entry = RDataFactory.createList(data);
        entry.setGPBits(calling);
        return entry;
    }

    private static boolean isCallingEntry(RList entry) {
        return entry.getGPBits() != 0;
    }

    @TruffleBoundary
    public static String geterrmessage() {
        return getRErrorHandlingState().errMsg;
    }

    @TruffleBoundary
    public static void seterrmessage(String msg) {
        getRErrorHandlingState().errMsg = msg;
    }

    @TruffleBoundary
    public static void addRestart(RList restart) {
        assert restartExit(restart) instanceof String;
        getRErrorHandlingState().restartStack = RDataFactory.createPairList(restart, getRestartStack());
    }

    private static Object restartExit(RList restart) {
        CompilerAsserts.neverPartOfCompilation();
        Object dataAt = restart.getDataAt(0);
        if (dataAt == RNull.instance) {
            return dataAt;
        } else if (dataAt instanceof String) {
            return dataAt;
        } else if (dataAt instanceof RStringVector && ((RStringVector) dataAt).getLength() >= 1) {
            return ((RStringVector) dataAt).getDataAt(0);
        } else {
            throw RInternalError.shouldNotReachHere(Objects.toString(dataAt));
        }
    }

    private static MaterializedFrame restartFrame(RList restart) {
        return ((REnvironment) restart.getDataAt(1)).getFrame();
    }

    public static Object getRestart(int index, RContext context) {
        Object list = getRestartStack(context);
        int i = index;
        while (list != RNull.instance && i > 1) {
            RPairList pList = (RPairList) list;
            list = pList.cdr();
            i--;
        }
        if (list != RNull.instance) {
            return ((RPairList) list).car();
        } else if (i == 1) {
            Object[] data = new Object[]{"abort", RNull.instance};
            RList result = RDataFactory.createList(data);
            setClassAttr(result);
            return result;
        } else {
            return RNull.instance;
        }
    }

    @TruffleBoundary
    private static void setClassAttr(RList result) {
        result.setClassAttr(RESTART_CLASS);
    }

    public static void invokeRestart(RList restart, Object args) {
        ContextStateImpl errorHandlingState = getRErrorHandlingState();
        Object exit = restartExit(restart);
        if (exit == RNull.instance) {
            errorHandlingState.restartStack = RNull.instance;
            // jump to top top level
            throw RInternalError.unimplemented();
        } else {
            while (errorHandlingState.restartStack != RNull.instance) {
                RPairList pList = (RPairList) errorHandlingState.restartStack;
                RList car = (RList) pList.car();
                if (exit.equals(restartExit(car))) {
                    errorHandlingState.restartStack = pList.cdr();
                    throw new ReturnException(args, RArguments.getCall(restartFrame(restart)));
                }
                errorHandlingState.restartStack = pList.cdr();
            }
        }
    }

    @TruffleBoundary
    public static void signalCondition(RList cond, String msg, Object call) {
        ContextStateImpl errorHandlingState = getRErrorHandlingState();
        Object oldStack = errorHandlingState.handlerStack;
        try {
            RPairList pList;
            while ((pList = findConditionHandler(cond)) != null) {
                RList entry = (RList) pList.car();
                errorHandlingState.handlerStack = pList.cdr();
                if (isCallingEntry(entry)) {
                    Object h = entry.getDataAt(ENTRY_HANDLER);
                    if (h == RESTART_TOKEN) {
                        errorcallDfltWithCall(null, fromCall(call), Message.GENERIC, msg);
                    } else {
                        RFunction hf = (RFunction) h;
                        RContext.getEngine().evalFunction(hf, null, null, true, null, cond);
                    }
                } else {
                    throw gotoExitingHandler(cond, call, entry);
                }
            }
        } finally {
            errorHandlingState.handlerStack = oldStack;
        }
    }

    /**
     * Called from {@link RError} to initiate the condition handling logic.
     *
     */
    static void signalError(RBaseNode callObj, Message msg, Object... args) {
        Object call = findCaller(callObj);
        String fMsg = formatMessage(msg, args);
        ContextStateImpl errorHandlingState = getRErrorHandlingState();
        Object oldStack = errorHandlingState.handlerStack;
        try {
            RPairList pList;
            while ((pList = findSimpleErrorHandler()) != null) {
                RList entry = (RList) pList.car();
                errorHandlingState.handlerStack = pList.cdr();
                errorHandlingState.errMsg = fMsg;
                if (isCallingEntry(entry)) {
                    if (entry.getDataAt(ENTRY_HANDLER) == RESTART_TOKEN) {
                        return;
                    } else {
                        RFunction handler = (RFunction) entry.getDataAt(2);
                        RStringVector errorMsgVec = RDataFactory.createStringVectorFromScalar(fMsg);
                        RFunction f = errorHandlingState.getDotHandleSimpleError();
                        assert f != null;
                        RContext.getRRuntimeASTAccess().callback(f, RContext.getInstance(callObj), new Object[]{handler, errorMsgVec, call});
                    }
                } else {
                    throw gotoExitingHandler(RNull.instance, call, entry);
                }
            }
        } finally {
            errorHandlingState.handlerStack = oldStack;
        }
    }

    private static ReturnException gotoExitingHandler(Object cond, Object call, RList entry) throws ReturnException {
        REnvironment rho = (REnvironment) entry.getDataAt(ENTRY_TARGET_ENVIR);
        RList result = (RList) entry.getDataAt(ENTRY_RETURN_RESULT);
        result.setDataAt(RESULT_COND, cond);
        result.setDataAt(RESULT_CALL, call);
        result.setDataAt(RESULT_HANDLER, entry.getDataAt(ENTRY_HANDLER));
        throw new ReturnException(result, RArguments.getCall(rho.getFrame()));
    }

    private static RPairList findSimpleErrorHandler() {
        Object list = getHandlerStack();
        while (list != RNull.instance) {
            RPairList pList = (RPairList) list;
            RList entry = (RList) pList.car();
            String klass = (String) entry.getDataAt(0);
            if (klass.equals("simpleError") || klass.equals("error") || klass.equals("condition")) {
                return pList;
            }
            list = pList.cdr();
        }
        return null;
    }

    private static RPairList findConditionHandler(RList cond) {
        // GnuR checks whether this is a string vector - in FastR it's statically typed to be
        RStringVector classes = RContext.getRRuntimeASTAccess().getClassHierarchy(cond);
        Object list = getHandlerStack();
        while (list != RNull.instance) {
            RPairList pList = (RPairList) list;
            RList entry = (RList) pList.car();
            String klass = (String) entry.getDataAt(0);
            for (int i = 0; i < classes.getLength(); i++) {
                if (klass.equals(classes.getDataAt(i))) {
                    return pList;
                }
            }
            list = pList.cdr();
        }
        return null;

    }

    @TruffleBoundary
    public static void dfltStop(String msg, Object call) {
        errorcallDfltWithCall(null, fromCall(call), Message.GENERIC, msg);
    }

    @TruffleBoundary
    public static void dfltWarn(String msg, Object call) {
        warningcallDfltWithCall(fromCall(call), Message.GENERIC, msg);
    }

    /**
     * Check a {@code call} value.
     *
     * @param call Either {@link RNull#instance} or an {@link RPairList}.
     * @return {@code null} iff {@code call == RNull.instance} else cast to {@link RPairList}.
     */
    private static Object fromCall(Object call) {
        if (!(call == RNull.instance || (call instanceof RPairList && ((RPairList) call).isLanguage()))) {
            throw RInternalError.shouldNotReachHere();
        }
        return call;
    }

    private static Object findCaller(RBaseNode callObj) {
        return RContext.getRRuntimeASTAccess().findCaller(callObj);
    }

    static RError errorcallDflt(boolean showCall, RBaseNode callObj, Message msg, Object... objects) {
        return errorcallDfltWithCall(callObj, showCall ? findCaller(callObj) : RNull.instance, msg, objects);
    }

    static RError errorcallDflt(RuntimeException customException, boolean showCall, RBaseNode callObj, Message msg, Object... objects) {
        return errorcallDfltWithCall(customException, callObj, showCall ? findCaller(callObj) : RNull.instance, msg, objects);
    }

    private static RError errorcallDfltWithCall(Node location, Object call, Message msg, Object... objects) {
        return errorcallDfltWithCall(null, location, call, msg, objects);
    }

    /**
     * The default error handler. This is where all the error message formatting is done and the
     * output.
     */
    private static RError errorcallDfltWithCall(RuntimeException customException, Node location, Object call, Message msg, Object... objects) {
        String fmsg = formatMessage(msg, objects);

        RContext context = RContext.getInstance();
        String errorMessage;
        if (customException == null) {
            errorMessage = createErrorMessage(call, fmsg);
        } else {
            errorMessage = getPolyglotErrorMessage(context, customException);
        }

        ContextStateImpl errorHandlingState = getRErrorHandlingState();
        if (errorHandlingState.inError > 0) {
            // recursive error
            if (errorHandlingState.inError == IN_HANDLER) {
                Utils.writeStderr("Error during wrapup: ", false);
                Utils.writeStderr(errorMessage, true);
            }
            if (errorHandlingState.warnings.size() > 0) {
                errorHandlingState.warnings.clear();
                Utils.writeStderr("Lost warning messages", true);
            }
            throw new RError(null, location);
        }

        Object errorExpr = context.stateROptions.getValue("error");
        boolean printNow = errorExpr != RNull.instance || RContext.isEmbedded();

        if (printNow) {
            // We print immediately if we are in the GNU-R compatible embedding mode or when there
            // is some user code going to be run before our exception would reach whoever is
            // embedding FastR via GraalSDK including the launcher. Note that if there is tryCatch,
            // we do not even reach here -- that is handled in signalError
            Utils.writeStderr(errorMessage, true);
            if (getRErrorHandlingState().warnings.size() > 0) {
                Utils.writeStderr("In addition: ", false);
                printWarnings(false);
            }
        } else {
            if (getRErrorHandlingState().warnings.size() > 0) {
                StringBuilder sb = new StringBuilder(errorMessage).append("\nIn addition: ");
                printWarnings(sb, false);
                errorMessage = sb.toString();
            }
        }

        // we are not quite done - need to check for options(error=expr)
        if (errorExpr != RNull.instance) {
            int oldInError = errorHandlingState.inError;
            try {
                errorHandlingState.inError = IN_HANDLER;
                MaterializedFrame materializedFrame = safeCurrentFrame();
                // type already checked in ROptions
                if (errorExpr instanceof RFunction) {
                    // Called with no arguments, but defaults will be applied
                    RFunction errorFunction = (RFunction) errorExpr;
                    ArgumentsSignature argsSig = RContext.getRRuntimeASTAccess().getArgumentsSignature(errorFunction);
                    Object[] evaluatedArgs;
                    if (errorFunction.isBuiltin()) {
                        evaluatedArgs = RContext.getRRuntimeASTAccess().getBuiltinDefaultParameterValues(errorFunction);
                    } else {
                        evaluatedArgs = new Object[argsSig.getLength()];
                        for (int i = 0; i < evaluatedArgs.length; i++) {
                            evaluatedArgs[i] = RMissing.instance;
                        }
                    }
                    RContext.getEngine().evalFunction(errorFunction, null, null, true, null, evaluatedArgs);
                } else if ((errorExpr instanceof RPairList && ((RPairList) errorExpr).isLanguage()) || errorExpr instanceof RExpression) {
                    if ((errorExpr instanceof RPairList && ((RPairList) errorExpr).isLanguage())) {
                        RContext.getEngine().eval((RPairList) errorExpr, materializedFrame);
                    } else if (errorExpr instanceof RExpression) {
                        RContext.getEngine().eval((RExpression) errorExpr, materializedFrame);
                    }
                } else {
                    // Checked when set
                    throw RInternalError.shouldNotReachHere();
                }
            } finally {
                errorHandlingState.inError = oldInError;
            }
        }

        if (context.isInteractive() || errorExpr != RNull.instance) {
            Object lastInteropTrace = getLastInteropTrace(context);
            Object trace = lastInteropTrace != null ? lastInteropTrace : Utils.createTraceback(0);
            try {
                // TODO: create second traceback with all interop/native frames -> put into
                // .FastRTraceback (or it can be something in RContext not env)
                // fastr.traceback without argument -> use .FastRTraceback
                REnvironment env = RContext.getInstance().stateREnvironment.getBaseEnv();
                env.put(".Traceback", trace);
            } catch (PutException x) {
                throw RInternalError.shouldNotReachHere("cannot write .Traceback");
            }
        }
        context.lastInteropTrace = null;
        throw customException != null ? customException : new RError(printNow ? null : errorMessage, location);
    }

    public static Object getLastInteropTrace(RContext context) {
        if (context.lastInteropTrace != null) {
            Object pl = Utils.toPairList(context.lastInteropTrace, 0);
            if (pl instanceof RPairList) {
                return pl;
            }
        }
        return null;
    }

    private static String getPolyglotErrorMessage(RContext context, RuntimeException customException) {
        String errorMessage;
        String str;
        TruffleLanguage.Env env = context.getEnv();
        if (env.isHostException(customException)) {
            str = env.asHostException(customException).toString();
        } else {
            str = customException.getMessage();
        }
        errorMessage = "Error in polyglot evaluation : " + str;
        return errorMessage;
    }

    private static MaterializedFrame safeCurrentFrame() {
        Frame frame = Utils.getActualCurrentFrame();
        return frame == null ? REnvironment.globalEnv().getFrame() : frame.materialize();
    }

    @TruffleBoundary
    public static RError handleInteropException(Node callObj, RuntimeException e) {
        // ClassNotFoundException might be raised internaly by FastR in FastRInterop$JavaType
        if (InteropLibrary.getUncached().isException(e) || e.getCause() instanceof ClassNotFoundException) {
            if (RContext.getInstance().stateInteropTry.isInTry()) {
                // will be caught and handled in .fastr.interop.try builtin
                throw new FastRInteropTryException(e);
            } else {
                if (e.getCause() instanceof ClassNotFoundException) {
                    // CCE thrown in FastrInterop.JavaType
                    Throwable cause = e.getCause();
                    throw RError.error(callObj, Message.GENERIC, cause.getClass().getName() + ": " + cause.getMessage());
                }
            }
        }

        // TODO: probably some special handling for case e instanceof HostException

        // Save the interop trace so that we can use it in traceback(..., interop=T)
        List<TruffleStackTraceElement> interopTrace = TruffleStackTrace.getStackTrace(e);
        RContext.getInstance().lastInteropTrace = interopTrace;

        // Run the R error handling machinery:
        RBaseNode parentRBase = findParentRBase(callObj);
        String message = getPolyglotErrorMessage(RContext.getInstance(), e);
        // Runs signal handlers, e.g., "error" handler in tryCatch(some-expression, error =
        // function(...) ...)
        // The handler usually throws another exception and so the following step is skipped
        // (In the case of tryCatch(...) the handler throws ReturnException that falls through to
        // the tryCatch R function
        RErrorHandling.signalError(parentRBase, Message.GENERIC, message);
        // Default error handling: print the error and run options(error = handler) if set, also
        // throw Java exception which will fall-through to the REPL/embedder
        return RErrorHandling.errorcallDflt(e, callObj != NO_CALLER, parentRBase, Message.GENERIC, message);
    }

    /**
     * Entry point for the {@code warning} {@code .Internal}.
     *
     * @param showCall {@true} iff call to be included in message
     * @param message the message
     * @param immediate {@code true} iff the output should be immediate
     * @param noBreakWarning TODOx
     */
    public static void warningcallInternal(boolean showCall, String message, boolean immediate, boolean noBreakWarning) {
        // TODO handle noBreakWarning
        ContextStateImpl errorHandlingState = getRErrorHandlingState();
        boolean immediateWarningSave = errorHandlingState.immediateWarning;
        try {
            errorHandlingState.immediateWarning = immediate;
            warningcall(showCall, RError.SHOW_CALLER2, RError.Message.GENERIC, message);
        } finally {
            errorHandlingState.immediateWarning = immediateWarningSave;
        }
    }

    /**
     * Entry point for Rf_warningCall from RFFI.
     */
    public static void warningcallRFFI(Object call, RContext context, String message) {
        warningCallInvoke(call, context, RDataFactory.createStringVectorFromScalar(message));
    }

    /**
     * Entry point for Rf_errorCall from RFFI.
     */
    public static void errorcallRFFI(Object call, String message) {
        errorCallInvoke(call, RDataFactory.createStringVectorFromScalar(message));
    }

    private static void errorCallInvoke(Object call, RStringVector errorMessage) {
        errorcallDfltWithCall(null, call, Message.GENERIC, errorMessage, new Object[]{errorMessage});
    }

    static void warningcall(boolean showCall, RBaseNode callObj, Message msg, Object... args) {
        Object call = showCall ? findCaller(callObj) : RNull.instance;
        RStringVector warningMessage = RDataFactory.createStringVectorFromScalar(formatMessage(msg, args));
        warningCallInvoke(call, RContext.getInstance(callObj), warningMessage);
    }

    private static void warningCallInvoke(Object call, RContext context, RStringVector warningMessage) {
        /*
         * Warnings generally do not prevent results being printed. However, this call into R will
         * destroy any visibility setting made by the calling builtin prior to this call.
         */
        ContextStateImpl errorHandlingState = getRErrorHandlingState();
        RFunction f = errorHandlingState.getDotSignalSimpleWarning();
        if (f != null) {
            RContext.getRRuntimeASTAccess().callback(f, context, new Object[]{warningMessage, call});
        }
        // otherwise the subsystem is not initialized yet - no warning
    }

    private static void warningcallDfltWithCall(Object call, Message msg, Object... args) {
        ContextStateImpl errorHandlingState = getRErrorHandlingState();
        if (errorHandlingState.inWarning) {
            return;
        }
        Object s = RContext.getInstance().stateROptions.getValue("warning.expression");
        if (s != RNull.instance) {
            if (!((s instanceof RPairList && ((RPairList) s).isLanguage()) || s instanceof RExpression)) {
                // TODO
            }
            throw RInternalError.unimplemented();
        }

        // ensured in ROptions

        Object value = RContext.getInstance().stateROptions.getValue("warn");
        int w = 0;
        if (value != RNull.instance) {
            w = ((RIntVector) value).getDataAt(0);
        }
        if (w == RRuntime.INT_NA) {
            w = 0;
        }
        if (w <= 0 && errorHandlingState.immediateWarning) {
            w = 1;
        }

        if (w < 0 || errorHandlingState.inWarning || errorHandlingState.inError > 0) {
            /*
             * ignore if w<0 or already in here
             */
            return;
        }

        try {
            errorHandlingState.inWarning = true;
            String fmsg = formatMessage(msg, args);
            String message = createWarningMessage(call, fmsg);
            if (w >= 2) {
                throw errorcallDfltWithCall(null, call, RError.Message.CONVERTED_FROM_WARNING, fmsg);
            } else if (w == 1) {
                Utils.writeStderr(message, true);
            } else if (w == 0 && errorHandlingState.warnings.size() < errorHandlingState.maxWarnings) {
                errorHandlingState.warnings.add(new Warning(fmsg, call));
            }
        } finally {
            errorHandlingState.inWarning = false;
        }
    }

    @TruffleBoundary
    public static void printWarnings(boolean suppress) {
        StringBuilder sb = new StringBuilder();
        printWarnings(sb, suppress);
        if (sb.length() > 0) {
            sb.append('\n');
            Utils.writeStderr(sb.toString(), false);
        }
    }

    @TruffleBoundary
    public static void printWarnings(StringBuilder sb, boolean suppress) {
        ContextStateImpl errorHandlingState = getRErrorHandlingState();
        Warnings warnings = errorHandlingState.warnings;
        if (suppress) {
            warnings.clear();
            return;
        }
        int nWarnings = warnings.size();
        if (nWarnings == 0) {
            return;
        }
        if (errorHandlingState.inPrintWarning) {
            if (nWarnings > 0) {
                warnings.clear();
                sb.append("Lost warning messages");
            }
            return;
        }
        try {
            errorHandlingState.inPrintWarning = true;
            if (nWarnings == 1) {
                sb.append("Warning message:").append('\n');
                Warning warning = warnings.get(0);
                if (warning.call == RNull.instance) {
                    sb.append(warning.message);
                } else {
                    printWarningMessage(sb, "In ", warning, 69);
                }
            } else if (nWarnings <= 10) {
                sb.append("Warning messages:\n");
                for (int i = 0; i < nWarnings; i++) {
                    Warning warning = warnings.get(i);
                    if (warning.call == RNull.instance) {
                        sb.append((i + 1)).append(':').append('\n');
                        sb.append("  ").append(warning.message);
                    } else {
                        sb.append(i + 1);
                        printWarningMessage(sb, ": In ", warning, 65);
                    }
                    if (i < nWarnings - 1) {
                        sb.append('\n');
                    }
                }
            } else {
                if (nWarnings < errorHandlingState.maxWarnings) {
                    sb.append(String.format("There were %d warnings (use warnings() to see them)", nWarnings));
                } else {
                    assert nWarnings == errorHandlingState.maxWarnings : "warnings above the limit should not have been added";
                    sb.append(String.format("There were %d or more warnings (use warnings() to see the first %d)", nWarnings, nWarnings));
                }
            }
            Object[] wData = new Object[nWarnings];
            String[] names = new String[nWarnings];
            for (int i = 0; i < nWarnings; i++) {
                wData[i] = warnings.get(i).call;
                names[i] = warnings.get(i).message;
            }
            RList lw = RDataFactory.createList(wData, RDataFactory.createStringVector(names, RDataFactory.COMPLETE_VECTOR));
            REnvironment.baseEnv().safePut("last.warning", lw);
        } finally {
            errorHandlingState.inPrintWarning = false;
            warnings.clear();
        }
    }

    private static void printWarningMessage(StringBuilder sb, String prefix, Warning warning, int maxLen) {
        String callString = RContext.getRRuntimeASTAccess().getCallerSource((RPairList) warning.call);

        String message = warning.message;
        int firstLineLength = message.contains("\n") ? message.indexOf('\n') : message.length();
        if (callString.length() + firstLineLength > maxLen) {
            // split long lines
            sb.append(prefix).append(callString).append(" :\n");
            sb.append("  ").append(message);
        } else {
            sb.append(prefix).append(callString).append(" : ").append(message);
        }
    }

    public static void printDeferredWarnings() {
        if (getRErrorHandlingState().warnings.size() > 0) {
            Utils.writeStderr("In addition: ", false);
            printWarnings(false);
        }
    }

    /**
     * Converts a {@link RError.Message}, that possibly requires arguments into a {@link String}.
     */
    static String formatMessage(RError.Message msg, Object... args) {
        return msg.hasArgs ? String.format(msg.message, args) : msg.message;
    }

    private static String wrapMessage(String preamble, String message) {
        // TODO find out about R's line-wrap policy
        // (is 74 a given percentage of console width?)
        if (preamble.length() + 1 + message.length() >= 74) {
            // +1 is for the extra space following the colon
            return preamble + "\n  " + message;
        } else {
            return preamble + " " + message;
        }
    }

    private static String createErrorMessage(Object call, String formattedMsg) {
        return createKindMessage("Error", call, formattedMsg);
    }

    private static String createWarningMessage(Object call, String formattedMsg) {
        return createKindMessage("Warning", call, formattedMsg);
    }

    /**
     * Creates an error message suitable for output to the user, taking into account {@code src},
     * which may be {@code null}.
     */
    private static String createKindMessage(String kind, Object call, String formattedMsg) {
        String preamble = kind;
        String errorMsg = null;
        assert call instanceof RNull || (call instanceof RPairList && ((RPairList) call).isLanguage());
        if (call == RNull.instance) {
            // generally means top-level of shell or similar
            preamble += ": ";
            errorMsg = preamble + formattedMsg;
        } else {
            RPairList rl = (RPairList) call;
            preamble += " in " + RContext.getRRuntimeASTAccess().getCallerSource(rl) + " :";
            errorMsg = wrapMessage(preamble, formattedMsg);
        }
        return errorMsg;
    }
}
