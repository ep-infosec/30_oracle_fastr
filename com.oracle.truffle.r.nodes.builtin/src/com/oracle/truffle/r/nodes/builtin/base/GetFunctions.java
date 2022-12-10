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
package com.oracle.truffle.r.nodes.builtin.base;

import static com.oracle.truffle.r.nodes.builtin.CastBuilder.Predef.asIntegerVector;
import static com.oracle.truffle.r.nodes.builtin.CastBuilder.Predef.chain;
import static com.oracle.truffle.r.nodes.builtin.CastBuilder.Predef.doubleValue;
import static com.oracle.truffle.r.nodes.builtin.CastBuilder.Predef.findFirst;
import static com.oracle.truffle.r.nodes.builtin.CastBuilder.Predef.instanceOf;
import static com.oracle.truffle.r.nodes.builtin.CastBuilder.Predef.integerValue;
import static com.oracle.truffle.r.nodes.builtin.CastBuilder.Predef.stringValue;
import static com.oracle.truffle.r.nodes.builtin.CastBuilder.Predef.toBoolean;
import static com.oracle.truffle.r.runtime.builtins.RBehavior.COMPLEX;
import static com.oracle.truffle.r.runtime.builtins.RBuiltinKind.INTERNAL;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.api.profiles.ConditionProfile;
import com.oracle.truffle.r.nodes.access.variables.ReadVariableNode;
import com.oracle.truffle.r.runtime.data.nodes.attributes.TypeFromModeNode;
import com.oracle.truffle.r.runtime.data.nodes.attributes.TypeFromModeNodeGen;
import com.oracle.truffle.r.nodes.binary.CastTypeNode;
import com.oracle.truffle.r.nodes.binary.CastTypeNodeGen;
import com.oracle.truffle.r.nodes.builtin.RBuiltinNode;
import com.oracle.truffle.r.nodes.builtin.base.Eval.EvalEnvCast;
import com.oracle.truffle.r.nodes.builtin.base.GetFunctionsFactory.GetNodeGen;
import com.oracle.truffle.r.nodes.function.PromiseHelperNode;
import com.oracle.truffle.r.nodes.function.call.RExplicitCallNode;
import com.oracle.truffle.r.nodes.objects.GetS4DataSlot;
import com.oracle.truffle.r.runtime.nodes.unary.CastNode;
import com.oracle.truffle.r.runtime.ArgumentsSignature;
import com.oracle.truffle.r.runtime.RError;
import com.oracle.truffle.r.runtime.RError.Message;
import com.oracle.truffle.r.runtime.RRuntime;
import com.oracle.truffle.r.runtime.RType;
import com.oracle.truffle.r.runtime.builtins.RBuiltin;
import com.oracle.truffle.r.runtime.data.RArgsValuesAndNames;
import com.oracle.truffle.r.runtime.data.RDataFactory;
import com.oracle.truffle.r.runtime.data.RFunction;
import com.oracle.truffle.r.runtime.data.RList;
import com.oracle.truffle.r.runtime.data.RMissing;
import com.oracle.truffle.r.runtime.data.RNull;
import com.oracle.truffle.r.runtime.data.RPromise;
import com.oracle.truffle.r.runtime.data.RS4Object;
import com.oracle.truffle.r.runtime.data.RSymbol;
import com.oracle.truffle.r.runtime.data.RStringVector;
import com.oracle.truffle.r.runtime.env.REnvironment;
import com.oracle.truffle.r.runtime.nodes.RBaseNode;

/**
 * assert: not expected to be fast even when called as, e.g., {@code get("x")}.
 */
public class GetFunctions {
    private static final class Helper extends RBaseNode {

        protected final BranchProfile recursiveProfile = BranchProfile.create();

        @Child private PromiseHelperNode promiseHelper = new PromiseHelperNode();
        @Child protected TypeFromModeNode typeFromMode = TypeFromModeNodeGen.create();

        @CompilationFinal private boolean firstExecution = true;

        protected void unknownObject(String x, RType modeType, String modeString) throws RError {
            CompilerDirectives.transferToInterpreter();
            if (modeType == RType.Any) {
                throw error(RError.Message.UNKNOWN_OBJECT, x);
            } else {
                throw error(RError.Message.UNKNOWN_OBJECT_MODE, x, modeType == null ? modeString : modeType.getName());
            }
        }

        protected Object checkPromise(VirtualFrame frame, Object r, String identifier) {
            if (r instanceof RPromise) {
                if (firstExecution) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    firstExecution = false;
                    return ReadVariableNode.evalPromiseSlowPathWithName(identifier, frame, (RPromise) r);
                }
                return promiseHelper.evaluate(frame, (RPromise) r);
            } else {
                return r;
            }
        }

        protected Object getAndCheck(VirtualFrame frame, String x, REnvironment env, RType modeType, String modeString, boolean fail) throws RError {
            Object obj = checkPromise(frame, env.get(x), x);
            if (obj != null && obj != RMissing.instance && RRuntime.checkType(obj, modeType)) {
                return obj;
            } else {
                if (fail) {
                    unknownObject(x, modeType, modeString);
                }
                return null;
            }
        }

        protected Object getInherits(VirtualFrame frame, String x, REnvironment envir, RType modeType, String modeString, boolean fail) {
            Object r = getAndCheck(frame, x, envir, modeType, modeString, false);
            if (r == null) {
                recursiveProfile.enter();
                REnvironment env = envir;
                while (env != REnvironment.emptyEnv()) {
                    env = env.getParent();
                    if (env != REnvironment.emptyEnv()) {
                        r = checkPromise(frame, env.get(x), x);
                        if (r != null && RRuntime.checkType(r, modeType)) {
                            break;
                        }
                    }
                }
                if ((r == null || r == RMissing.instance) && fail) {
                    unknownObject(x, modeType, modeString);
                }
            }
            return r;
        }
    }

    public static final class S4ToEnvNode extends CastNode {

        @Child private GetS4DataSlot getS4Data = GetS4DataSlot.create(RType.Environment);

        @Override
        public Object execute(Object obj) {
            RS4Object s4Obj = (RS4Object) obj;
            assert s4Obj.isS4() : "unexpected non-S4 RS4Object";
            Object value = getS4Data.executeObject(s4Obj);
            if (value == RNull.instance) {
                CompilerDirectives.transferToInterpreter();
                throw RError.error(RError.SHOW_CALLER, Message.USE_NULL_ENV_DEFUNCT);
            }
            return value;
        }
    }

    @RBuiltin(name = "get", kind = INTERNAL, parameterNames = {"x", "envir", "mode", "inherits"}, behavior = COMPLEX)
    public abstract static class Get extends RBuiltinNode.Arg4 {

        @Child private Helper helper = new Helper();

        private final ConditionProfile inheritsProfile = ConditionProfile.createBinaryProfile();

        public static Get create() {
            return GetNodeGen.create();
        }

        public abstract Object execute(VirtualFrame frame, Object what, Object where, String name, boolean inherits);

        static {
            Casts casts = new Casts(Get.class);
            casts.arg("x").mustBe(stringValue()).asStringVector().findFirst();
            casts.arg("envir").mustBe(instanceOf(REnvironment.class).or(integerValue()).or(doubleValue()).or(instanceOf(RS4Object.class))).mapIf(integerValue().or(doubleValue()),
                            chain(asIntegerVector()).with(findFirst().integerElement()).end());
            casts.arg("mode").mustBe(stringValue()).asStringVector().findFirst();
            casts.arg("inherits").asLogicalVector().findFirst().map(toBoolean());
        }

        @Specialization
        public Object get(VirtualFrame frame, String x, REnvironment envir, String mode, boolean inherits) {
            RType modeType = helper.typeFromMode.execute(mode);
            if (inheritsProfile.profile(inherits)) {
                return helper.getInherits(frame, x, envir, modeType, mode, true);
            } else {
                return helper.getAndCheck(frame, x, envir, modeType, mode, true);
            }
        }

        @Specialization
        public Object get(VirtualFrame frame, String x, RS4Object s4Envir, String mode, boolean inherits,
                        @Cached("new()") S4ToEnvNode s4ToEnv) {
            return get(frame, x, (REnvironment) s4ToEnv.execute(s4Envir), mode, inherits);
        }

        @Specialization
        protected Object get(VirtualFrame frame, String x, int envir, String mode, boolean inherits,
                        @Cached("create()") EvalEnvCast envCast) {
            Object env = envCast.execute(frame, envir, RMissing.instance);
            return get(frame, x, (REnvironment) env, mode, inherits);
        }
    }

    @RBuiltin(name = "get0", kind = INTERNAL, parameterNames = {"x", "envir", "mode", "inherits", "ifnotfound"}, behavior = COMPLEX)
    public abstract static class Get0 extends RBuiltinNode.Arg5 {

        @Child private Helper helper = new Helper();

        private final ConditionProfile inheritsProfile = ConditionProfile.createBinaryProfile();

        static {
            Casts casts = new Casts(Get0.class);
            casts.arg("x").mustBe(stringValue()).asStringVector().findFirst();
            casts.arg("envir").mustBe(instanceOf(REnvironment.class).or(integerValue()).or(doubleValue()).or(instanceOf(RS4Object.class))).mapIf(integerValue().or(doubleValue()),
                            chain(asIntegerVector()).with(findFirst().integerElement()).end());
            casts.arg("mode").mustBe(stringValue()).asStringVector().findFirst();
            casts.arg("inherits").asLogicalVector().findFirst().map(toBoolean());
        }

        @Specialization
        protected Object get0(VirtualFrame frame, String x, REnvironment envir, String mode, boolean inherits, Object ifnotfound) {
            Object result;
            RType modeType = helper.typeFromMode.execute(mode);
            if (inheritsProfile.profile(inherits)) {
                result = helper.getInherits(frame, x, envir, modeType, mode, false);
            } else {
                result = helper.getAndCheck(frame, x, envir, modeType, mode, false);
            }
            if (result == null) {
                result = ifnotfound;
            }
            return result;
        }

        @Specialization
        protected Object get0(VirtualFrame frame, String x, RS4Object s4Envir, String mode, boolean inherits, Object ifnotfound,
                        @Cached("new()") S4ToEnvNode s4ToEnv) {
            return get0(frame, x, (REnvironment) s4ToEnv.execute(s4Envir), mode, inherits, ifnotfound);
        }

        @Specialization
        protected Object get0(VirtualFrame frame, String x, int envir, String mode, boolean inherits, Object ifnotfound,
                        @Cached("create()") EvalEnvCast envCast) {
            Object env = envCast.execute(frame, envir, RMissing.instance);
            return get0(frame, x, (REnvironment) env, mode, inherits, ifnotfound);
        }
    }

    @RBuiltin(name = "mget", kind = INTERNAL, parameterNames = {"x", "envir", "mode", "ifnotfound", "inherits"}, behavior = COMPLEX)
    public abstract static class MGet extends RBuiltinNode.Arg5 {

        @Child private Helper helper = new Helper();

        private final BranchProfile wrongLengthErrorProfile = BranchProfile.create();

        @Child private TypeFromModeNode typeFromMode = TypeFromModeNodeGen.create();
        @Child private RExplicitCallNode explicitCallNode;

        static {
            Casts casts = new Casts(MGet.class);
            casts.arg("x").mustBe(stringValue()).asStringVector();
            casts.arg("envir").mustBe(instanceOf(REnvironment.class).or(instanceOf(RS4Object.class)), RError.Message.MUST_BE_ENVIRON2, "second argument");
            casts.arg("mode").mustBe(stringValue()).asStringVector();
            casts.arg("inherits").asLogicalVector().findFirst().map(toBoolean());
        }

        private static class State {
            final int svLength;
            final int modeLength;
            final int ifNotFoundLength;
            final RFunction ifnFunc;
            final Object[] data;
            final String[] names;
            boolean complete = RDataFactory.COMPLETE_VECTOR;

            State(RStringVector xv, RStringVector mode, RList ifNotFound) {
                this.svLength = xv.getLength();
                this.modeLength = mode.getLength();
                this.ifNotFoundLength = ifNotFound.getLength();
                if (ifNotFoundLength == 1 && ifNotFound.getDataAt(0) instanceof RFunction) {
                    ifnFunc = (RFunction) ifNotFound.getDataAt(0);
                } else {
                    ifnFunc = null;
                }
                data = new Object[svLength];
                names = new String[svLength];
            }

            String checkNA(String x) {
                if (RRuntime.isNA(x)) {
                    complete = RDataFactory.INCOMPLETE_VECTOR;
                }
                return x;
            }

            RList getResult() {
                return RDataFactory.createList(data, RDataFactory.createStringVector(names, complete));
            }
        }

        private State checkArgs(RStringVector xv, RStringVector mode, RList ifNotFound) {
            State state = new State(xv, mode, ifNotFound);
            if (!(state.modeLength == 1 || state.modeLength == state.svLength)) {
                wrongLengthErrorProfile.enter();
                throw error(RError.Message.WRONG_LENGTH_ARG, "mode");
            }
            if (!(state.ifNotFoundLength == 1 || state.ifNotFoundLength == state.svLength)) {
                wrongLengthErrorProfile.enter();
                throw error(RError.Message.WRONG_LENGTH_ARG, "ifnotfound");
            }
            return state;
        }

        @Specialization
        protected RList mget(VirtualFrame frame, RStringVector xv, REnvironment envir, RStringVector mode, RList ifNotFound, boolean inherits,
                        @Cached("createBinaryProfile()") ConditionProfile argsAndValuesProfile,
                        @Cached("createBinaryProfile()") ConditionProfile missingProfile,
                        @Cached("createBinaryProfile()") ConditionProfile inheritsProfile) {
            State state = checkArgs(xv, mode, ifNotFound);
            for (int i = 0; i < state.svLength; i++) {
                String x = state.checkNA(xv.getDataAt(i));
                state.names[i] = x;
                RType modeType = typeFromMode.execute(mode.getDataAt(state.modeLength == 1 ? 0 : i));
                if (inheritsProfile.profile(inherits)) {
                    Object r = envir.get(x);
                    if (r == null || !RRuntime.checkType(r, modeType)) {
                        helper.recursiveProfile.enter();
                        REnvironment env = envir;
                        while (env != REnvironment.emptyEnv()) {
                            env = env.getParent();
                            if (env != REnvironment.emptyEnv()) {
                                r = handleMissingAndVarargs(helper.checkPromise(frame, env.get(x), x), argsAndValuesProfile, missingProfile);
                                if (r != null && RRuntime.checkType(r, modeType)) {
                                    break;
                                }
                            }
                        }
                    }
                    if (r == null) {
                        doIfNotFound(frame, state, i, x, ifNotFound);
                    } else {
                        state.data[i] = r;
                    }
                } else {
                    Object r = handleMissingAndVarargs(helper.checkPromise(frame, envir.get(x), x), argsAndValuesProfile, missingProfile);
                    if (r != null && RRuntime.checkType(r, modeType)) {
                        state.data[i] = r;
                    } else {
                        doIfNotFound(frame, state, i, x, ifNotFound);
                    }
                }
            }
            return state.getResult();
        }

        @Specialization
        protected RList mget(VirtualFrame frame, RStringVector xv, RS4Object s4Envir, RStringVector mode, RList ifNotFound, boolean inherits,
                        @Cached("createBinaryProfile()") ConditionProfile argsAndValuesProfile,
                        @Cached("createBinaryProfile()") ConditionProfile missingProfile,
                        @Cached("createBinaryProfile()") ConditionProfile inheritsProfile,
                        @Cached("new()") S4ToEnvNode s4ToEnv) {
            return mget(frame, xv, (REnvironment) s4ToEnv.execute(s4Envir), mode, ifNotFound, inherits, argsAndValuesProfile, missingProfile, inheritsProfile);
        }

        @Specialization
        protected RList mget(VirtualFrame frame, RStringVector xv, REnvironment env, RStringVector mode, Object ifNotFound, boolean inherits,
                        @Cached("createBinaryProfile()") ConditionProfile argsAndValuesProfile,
                        @Cached("createBinaryProfile()") ConditionProfile missingProfile,
                        @Cached("createBinaryProfile()") ConditionProfile inheritsProfile,
                        @Cached("createCastType()") CastTypeNode castTypeNode) {
            Object l = castTypeNode.execute(ifNotFound, RType.List);
            return mget(frame, xv, env, mode, (RList) l, inherits, argsAndValuesProfile, missingProfile, inheritsProfile);
        }

        protected static CastTypeNode createCastType() {
            return CastTypeNodeGen.create();
        }

        private void doIfNotFound(VirtualFrame frame, State state, int i, String x, RList ifNotFound) {
            if (state.ifnFunc != null) {
                state.data[i] = call(frame, state.ifnFunc, x);
            } else {
                state.data[i] = ifNotFound.getDataAt(state.ifNotFoundLength == 1 ? 0 : i);
            }
        }

        private Object call(VirtualFrame frame, RFunction ifnFunc, String x) {
            if (explicitCallNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                explicitCallNode = insert(RExplicitCallNode.create());
            }
            RArgsValuesAndNames args = new RArgsValuesAndNames(new Object[]{x}, ArgumentsSignature.empty(1));
            return explicitCallNode.call(frame, ifnFunc, args);
        }

        private static Object handleMissingAndVarargs(Object value, ConditionProfile argsAndValuesProfile, ConditionProfile missingProfile) {
            if (argsAndValuesProfile.profile(value instanceof RArgsValuesAndNames)) {
                if (((RArgsValuesAndNames) value).getLength() == 0) {
                    return RSymbol.MISSING;
                } else {
                    // GNUR also puts raw promises in the list
                    return RDataFactory.createList(((RArgsValuesAndNames) value).getArguments());
                }
            }
            if (missingProfile.profile(value == RMissing.instance)) {
                return RSymbol.MISSING;
            }
            return value;
        }

    }
}
