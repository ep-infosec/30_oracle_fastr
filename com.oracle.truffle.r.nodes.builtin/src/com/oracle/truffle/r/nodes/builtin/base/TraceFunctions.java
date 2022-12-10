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
package com.oracle.truffle.r.nodes.builtin.base;

import static com.oracle.truffle.r.nodes.builtin.CastBuilder.Predef.asStringVector;
import static com.oracle.truffle.r.nodes.builtin.CastBuilder.Predef.chain;
import static com.oracle.truffle.r.nodes.builtin.CastBuilder.Predef.findFirst;
import static com.oracle.truffle.r.nodes.builtin.CastBuilder.Predef.instanceOf;
import static com.oracle.truffle.r.nodes.builtin.CastBuilder.Predef.size;
import static com.oracle.truffle.r.nodes.builtin.CastBuilder.Predef.stringValue;
import static com.oracle.truffle.r.runtime.RVisibility.CUSTOM;
import static com.oracle.truffle.r.runtime.RVisibility.OFF;
import static com.oracle.truffle.r.runtime.builtins.RBehavior.COMPLEX;
import static com.oracle.truffle.r.runtime.builtins.RBuiltinKind.INTERNAL;
import static com.oracle.truffle.r.runtime.builtins.RBuiltinKind.PRIMITIVE;

import java.io.IOException;
import java.util.HashSet;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.FrameInstance.FrameAccess;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.r.nodes.builtin.NodeWithArgumentCasts.Casts;
import com.oracle.truffle.r.nodes.builtin.RBuiltinNode;
import com.oracle.truffle.r.nodes.builtin.base.GetFunctionsFactory.GetNodeGen;
import com.oracle.truffle.r.nodes.builtin.helpers.TraceHandling;
import com.oracle.truffle.r.nodes.function.visibility.SetVisibilityNode;
import com.oracle.truffle.r.runtime.RArguments;
import com.oracle.truffle.r.runtime.RCaller;
import com.oracle.truffle.r.runtime.RError;
import com.oracle.truffle.r.runtime.RError.Message;
import com.oracle.truffle.r.runtime.RRuntime;
import com.oracle.truffle.r.runtime.RType;
import com.oracle.truffle.r.runtime.builtins.RBuiltin;
import com.oracle.truffle.r.runtime.conn.StdConnections;
import com.oracle.truffle.r.runtime.context.RContext;
import com.oracle.truffle.r.runtime.data.MemoryCopyTracer;
import com.oracle.truffle.r.runtime.data.RFunction;
import com.oracle.truffle.r.runtime.data.RMissing;
import com.oracle.truffle.r.runtime.data.RNull;
import com.oracle.truffle.r.runtime.data.RStringVector;
import com.oracle.truffle.r.runtime.data.model.RAbstractVector;

public class TraceFunctions {

    protected static Casts createCasts(Class<? extends RBuiltinNode> extCls) {
        Casts casts = new Casts(extCls);
        casts.arg("what").mustBe(instanceOf(RFunction.class).or(stringValue()), Message.ARG_MUST_BE_FUNCTION).mapIf(stringValue(),
                        chain(asStringVector()).with(findFirst().stringElement()).end());
        return casts;
    }

    private abstract static class PrimTraceAdapter extends RBuiltinNode.Arg1 {
        @Child private GetFunctions.Get getNode;

        protected Object getFunction(VirtualFrame frame, String funcName) {
            if (getNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                getNode = insert(GetNodeGen.create());
            }
            return getNode.execute(frame, funcName, getRContext().stateREnvironment.getGlobalEnv(), RType.Function.getName(), true);
        }
    }

    @RBuiltin(name = ".primTrace", visibility = OFF, kind = PRIMITIVE, parameterNames = "what", behavior = COMPLEX)
    public abstract static class PrimTrace extends PrimTraceAdapter {

        static {
            createCasts(PrimTrace.class);
        }

        public abstract Object execute(VirtualFrame frame, RFunction func);

        @Specialization
        protected RNull primTrace(VirtualFrame frame, RStringVector funcName) {
            return primTrace((RFunction) getFunction(frame, funcName.getDataAt(0)));
        }

        @Specialization
        @TruffleBoundary
        protected RNull primTrace(RFunction func) {
            if (!func.isBuiltin()) {
                TraceHandling.enableTrace(func);
            } else {
                throw error(RError.Message.GENERIC, "builtin functions cannot be traced");
            }
            return RNull.instance;
        }
    }

    @RBuiltin(name = ".primUntrace", visibility = OFF, kind = PRIMITIVE, parameterNames = "what", behavior = COMPLEX)
    public abstract static class PrimUnTrace extends PrimTraceAdapter {

        static {
            createCasts(PrimUnTrace.class);
        }

        public abstract Object execute(VirtualFrame frame, RFunction func);

        @Specialization
        protected RNull primUnTrace(VirtualFrame frame, RStringVector funcName) {
            return primUnTrace((RFunction) getFunction(frame, funcName.getDataAt(0)));
        }

        @Specialization
        @TruffleBoundary
        protected RNull primUnTrace(RFunction func) {
            if (!func.isBuiltin()) {
                TraceHandling.disableTrace(func);
            }
            return RNull.instance;
        }
    }

    @RBuiltin(name = "traceOnOff", kind = INTERNAL, parameterNames = "state", behavior = COMPLEX)
    public abstract static class TraceOnOff extends RBuiltinNode.Arg1 {

        static {
            Casts.noCasts(TraceOnOff.class);
        }

        @Specialization
        @TruffleBoundary
        protected byte traceOnOff(byte state) {
            /* TODO GnuR appears to accept ANY value as an argument */
            boolean prevState = getRContext().stateInstrumentation.getTracingState();
            boolean newState = RRuntime.fromLogical(state);
            if (newState != prevState) {
                getRContext().stateInstrumentation.setTracingState(newState);
                MemoryCopyTracer.setTracingState(newState);
            }
            return RRuntime.asLogical(prevState);
        }

        @Specialization
        @TruffleBoundary
        protected byte traceOnOff(@SuppressWarnings("unused") RNull state) {
            return RRuntime.asLogical(getRContext().stateInstrumentation.getTracingState());
        }
    }

    static {
        MemoryCopyTracer.addListener(new TracememListener());
    }

    @TruffleBoundary
    protected static HashSet<Object> getTracedObjects() {
        return RContext.getInstance().getInstrumentationState().getTracemem().getTracedObjects();
    }

    @TruffleBoundary
    protected static String formatHashCode(Object x) {
        return String.format("<0x%x>", x.hashCode());
    }

    @TruffleBoundary
    protected static void startTracing(Object x) {
        /*
         * There is no explicit command to enable tracing, it is implicit in the call to tracemem.
         * However, it can be disabled by tracingState(F), so we can't unilaterally turn on tracing
         * here.
         */
        getTracedObjects().add(x);
        boolean tracingState = RContext.getInstance().stateInstrumentation.getTracingState();
        if (tracingState) {
            MemoryCopyTracer.setTracingState(true);
        }
    }

    @TruffleBoundary
    protected static void printToStdout(String msg) {
        try {
            StdConnections.getStdout().writeString(msg, true);
        } catch (IOException ex) {
            throw RError.error(RError.NO_CALLER, RError.Message.GENERIC, ex.getMessage());
        }
    }

    @TruffleBoundary
    protected static String getStackTrace() {
        final StringBuilder result = new StringBuilder();
        Truffle.getRuntime().iterateFrames(frame -> {
            Frame unwrapped = RArguments.unwrap(frame.getFrame(FrameAccess.READ_ONLY));
            if (RArguments.isRFrame(unwrapped)) {
                RCaller call = RArguments.getCall(unwrapped);
                if (call != null && call.isValidCaller()) {
                    result.append(RContext.getRRuntimeASTAccess().getCallerSource(call));
                    result.append(' ');
                }
            }
            return null;
        });
        return result.toString();
    }

    private static final class TracememListener implements MemoryCopyTracer.Listener {
        @TruffleBoundary
        @Override
        public void reportCopying(RAbstractVector src, RAbstractVector dest) {
            if (getTracedObjects().contains(src)) {
                printToStdout(String.format("tracemem[0x%x -> 0x%x]: %s", src.hashCode(), dest.hashCode(), getStackTrace()));
            }
        }
    }

    /**
     * Note: tracemem does not support scalars: these are stored in frame directly not wrapped in a
     * vector class. When these are manipulated as 'vectors', they are wrapped temporarily, such
     * temporary vector wrappers cannot be traced however.
     */
    @RBuiltin(name = "tracemem", kind = PRIMITIVE, parameterNames = "x", behavior = COMPLEX)
    public abstract static class Tracemem extends RBuiltinNode.Arg1 {

        static {
            Casts casts = new Casts(Tracemem.class);
            casts.arg("x").mustNotBeNull(Message.TRACEMEM_NOT_NULL);
        }

        @Specialization
        protected String execute(Object x) {
            startTracing(x);
            return formatHashCode(x);
        }
    }

    /**
     * {@code retracemem} differences from {@code tracemem} are that it fails silently when R is not
     * build with memory tracing support or x is NULL and it has additional parameter with an
     * unclear meaning.
     */
    @RBuiltin(name = "retracemem", kind = PRIMITIVE, visibility = CUSTOM, parameterNames = {"x", "previous"}, behavior = COMPLEX)
    public abstract static class Retracemem extends RBuiltinNode.Arg2 {

        @Child private SetVisibilityNode visibility = SetVisibilityNode.create();

        static {
            Casts casts = new Casts(Retracemem.class);
            casts.arg("previous").defaultError(Message.INVALID_ARGUMENT, "previous").allowNullAndMissing().mustBe(stringValue()).asStringVector().mustBe(size(1)).findFirst();
        }

        @Specialization
        protected Object execute(VirtualFrame frame, Object x, @SuppressWarnings("unused") RNull previous) {
            CompilerDirectives.transferToInterpreter();
            return getResult(frame, x);
        }

        @Specialization
        protected Object execute(VirtualFrame frame, Object x, @SuppressWarnings("unused") RMissing previous) {
            CompilerDirectives.transferToInterpreter();
            return getResult(frame, x);
        }

        @Specialization
        protected Object execute(VirtualFrame frame, Object x, String previous) {
            CompilerDirectives.transferToInterpreter();
            Object result = getResult(frame, x);
            if (x != null && x != RNull.instance) {
                startTracing(x);
                printToStdout(String.format("tracemem[%s -> 0x%x]: %s", previous, x.hashCode(), getStackTrace()));
            }
            return result;
        }

        private Object getResult(VirtualFrame frame, Object x) {
            if (!isRNull(x) && getTracedObjects().contains(x)) {
                visibility.execute(frame, true);
                return formatHashCode(x);
            } else {
                visibility.execute(frame, false);
                return RNull.instance;
            }
        }
    }

    @RBuiltin(name = "untracemem", kind = PRIMITIVE, visibility = OFF, parameterNames = "x", behavior = COMPLEX)
    public abstract static class Untracemem extends RBuiltinNode.Arg1 {

        static {
            Casts.noCasts(Untracemem.class);
        }

        @Specialization
        @TruffleBoundary
        protected RNull execute(Object x) {
            getTracedObjects().remove(x);
            return RNull.instance;
        }
    }
}
