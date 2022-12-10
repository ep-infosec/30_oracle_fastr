/*
 * This program is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation; either version 2 of the License, or (at your option)
 * any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License for
 * more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program; if not, write to the Free Software Foundation, Inc., 51
 * Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 *
 * Copyright (c) 2014, Purdue University
 * Copyright (c) 2014, 2020, Oracle and/or its affiliates
 *
 * All rights reserved.
 */
package com.oracle.truffle.r.nodes.builtin.base;

import static com.oracle.truffle.r.runtime.RVisibility.CUSTOM;
import static com.oracle.truffle.r.runtime.builtins.RBehavior.COMPLEX;
import static com.oracle.truffle.r.runtime.builtins.RBuiltinKind.PRIMITIVE;
import static com.oracle.truffle.r.runtime.builtins.RBuiltinKind.SUBSTITUTE;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.api.profiles.ConditionProfile;
import com.oracle.truffle.api.profiles.ValueProfile;
import com.oracle.truffle.r.nodes.access.variables.LocalReadVariableNode;
import com.oracle.truffle.r.nodes.builtin.RBuiltinNode;
import com.oracle.truffle.r.nodes.function.CallMatcherNode;
import com.oracle.truffle.r.nodes.function.ClassHierarchyNode;
import com.oracle.truffle.r.nodes.function.GetCallerFrameNode;
import com.oracle.truffle.r.nodes.function.PromiseHelperNode;
import com.oracle.truffle.r.nodes.function.PromiseHelperNode.PromiseCheckHelperNode;
import com.oracle.truffle.r.nodes.function.RCallerHelper;
import com.oracle.truffle.r.nodes.function.S3FunctionLookupNode;
import com.oracle.truffle.r.nodes.function.S3FunctionLookupNode.Result;
import com.oracle.truffle.r.nodes.function.signature.CollectArgumentsNode;
import com.oracle.truffle.r.nodes.function.signature.CollectArgumentsNodeGen;
import com.oracle.truffle.r.nodes.function.signature.CombineSignaturesNode;
import com.oracle.truffle.r.nodes.function.signature.CombineSignaturesNodeGen;
import com.oracle.truffle.r.runtime.ArgumentsSignature;
import com.oracle.truffle.r.runtime.RArguments;
import com.oracle.truffle.r.runtime.RArguments.S3Args;
import com.oracle.truffle.r.runtime.RCaller;
import com.oracle.truffle.r.runtime.RDispatch;
import com.oracle.truffle.r.runtime.RError;
import com.oracle.truffle.r.runtime.RRuntime;
import com.oracle.truffle.r.runtime.ReturnException;
import com.oracle.truffle.r.runtime.Utils;
import com.oracle.truffle.r.runtime.builtins.RBuiltin;
import com.oracle.truffle.r.runtime.data.RArgsValuesAndNames;
import com.oracle.truffle.r.runtime.data.RDataFactory;
import com.oracle.truffle.r.runtime.data.RMissing;
import com.oracle.truffle.r.runtime.data.RNull;
import com.oracle.truffle.r.runtime.data.RPromise;
import com.oracle.truffle.r.runtime.data.model.RAbstractContainer;
import com.oracle.truffle.r.runtime.data.RStringVector;
import com.oracle.truffle.r.runtime.nodes.RBaseNode;

public abstract class S3DispatchFunctions {

    private static final class Helper extends RBaseNode {
        @Child private S3FunctionLookupNode methodLookup;
        @Child private CallMatcherNode callMatcher;
        private final ConditionProfile isOpsGeneric = ConditionProfile.createBinaryProfile();
        @CompilationFinal private ValueProfile dotMethodClassProfile;
        @Child private LocalReadVariableNode rvnMethod;
        private final boolean nextMethod;

        protected Helper(boolean nextMethod) {
            this.nextMethod = nextMethod;
            this.methodLookup = S3FunctionLookupNode.create(true, nextMethod);
            this.callMatcher = CallMatcherNode.create(false);
        }

        protected Object dispatch(VirtualFrame frame, RCaller parentCaller, String generic, RStringVector type, String group, MaterializedFrame callerFrame, MaterializedFrame genericDefFrame,
                        ArgumentsSignature suppliedSignature, Object[] suppliedArguments) {
            Result lookupResult = methodLookup.execute(frame, generic, type, group, callerFrame, genericDefFrame);

            Object dotMethod = lookupResult.targetFunctionName;
            if (isOpsGeneric.profile(Utils.identityEquals(group, RDispatch.OPS_GROUP_GENERIC.getGroupGenericName()))) {
                dotMethod = patchDotMethod(frame, lookupResult, dotMethod);
            }
            S3Args s3Args = lookupResult.createS3Args(dotMethod, callerFrame, genericDefFrame, group);
            RCaller dispatchCaller = RArguments.getCall(callerFrame);

            if (!RCaller.isValidCaller(dispatchCaller)) {
                // If callerFrame does not contain a valid caller, take the logical grand-parent of
                // parentCaller as the dispatch parent
                RCaller tmpCaller = parentCaller.getLogicalParent();
                tmpCaller = tmpCaller != null ? tmpCaller.getLogicalParent() : null;
                dispatchCaller = tmpCaller != null ? tmpCaller : dispatchCaller;
            }
            RCaller actualParentCaller = parentCaller;
            if (!nextMethod && actualParentCaller.getPrevious() != dispatchCaller &&
                            RCaller.isValidCaller(dispatchCaller) && !actualParentCaller.isPromise()) {
                // If dispatchCaller differs from the previous caller of actualParentCaller, create
                // a new actualParentCaller with the dispatchCaller as the logical parent. It
                // guarantees that the S3 generic method and a specific method have the same logical
                // parents. NB: In the case of NextMethod, the logical parent of parentCaller should
                // be the same as dispatchCaller thanks to using
                // RCaller.createForGenericFunctionCall.
                actualParentCaller = actualParentCaller.withLogicalParent(dispatchCaller);
            }
            Object result = callMatcher.execute(frame, actualParentCaller, dispatchCaller, suppliedSignature, suppliedArguments, lookupResult.function, lookupResult.targetFunctionName,
                            s3Args);
            return result;
        }

        private Object patchDotMethod(VirtualFrame frame, Result lookupResult, Object dotMethod) {
            // ".Method" variable should be vector of two strings for Ops group. If the first
            // argument's class was used for dispatch, then the value is ["the-class", ""]. Both
            // argument's classes can be used for dispatch as long as they are equal.
            //
            // Here we do not know which of the two argument's were used for the dispatch, but we
            // can find out by inspecting the ".Method" variable.
            Object origDotMethod = readDotMethod(frame);
            if (!(origDotMethod instanceof RStringVector)) {
                assert false : "Unexpected value of .Method in Ops generic after NextMethod or UseMethod: " + origDotMethod;
                return dotMethod;
            }
            RStringVector origVec = profileDotMethod(origDotMethod);
            if (origVec.getLength() != 2) {
                assert false : "Unexpected length of .Method: " + origVec.getLength();
                return dotMethod;
            }
            String[] data = new String[2];
            data[0] = origVec.getDataAt(0).isEmpty() ? "" : lookupResult.targetFunctionName;
            data[1] = origVec.getDataAt(1).isEmpty() ? "" : lookupResult.targetFunctionName;
            return RDataFactory.createStringVector(data, RDataFactory.COMPLETE_VECTOR);
        }

        private Object readDotMethod(VirtualFrame frame) {
            if (rvnMethod == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                rvnMethod = insert(LocalReadVariableNode.create(RRuntime.R_DOT_METHOD, false));
            }
            return rvnMethod.execute(frame);
        }

        private RStringVector profileDotMethod(Object origDotMethod) {
            if (dotMethodClassProfile == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                dotMethodClassProfile = ValueProfile.createClassProfile();
            }
            return dotMethodClassProfile.profile((RStringVector) origDotMethod);
        }
    }

    @RBuiltin(name = "UseMethod", visibility = CUSTOM, kind = PRIMITIVE, parameterNames = {"generic", "object"}, behavior = COMPLEX)
    public abstract static class UseMethod extends RBuiltinNode.Arg2 {

        /*
         * TODO: If more than two parameters are passed to UseMethod the extra parameters are
         * ignored and a warning is generated.
         */

        @Child private ClassHierarchyNode classHierarchyNode = ClassHierarchyNode.createForDispatch(true);
        @Child private PromiseCheckHelperNode promiseCheckHelper;
        @Child private GetCallerFrameNode getCallerFrameNode = GetCallerFrameNode.create();
        @Child private Helper helper = new Helper(false);

        private final BranchProfile firstArgMissing = BranchProfile.create();
        private final ConditionProfile argMissingProfile = ConditionProfile.createBinaryProfile();
        private final ConditionProfile argsValueAndNamesProfile = ConditionProfile.createBinaryProfile();

        static {
            Casts.noCasts(UseMethod.class);
        }

        @Specialization
        protected Object execute(VirtualFrame frame, String generic, Object arg) {
            Object dispatchedObject;
            if (argMissingProfile.profile(arg == RMissing.instance)) {
                // For S3Dispatch, we have to evaluate the the first argument
                dispatchedObject = getEnclosingArg(frame, generic);
            } else {
                dispatchedObject = arg;
            }

            RStringVector type = dispatchedObject == null ? RDataFactory.createEmptyStringVector() : classHierarchyNode.execute(dispatchedObject);
            MaterializedFrame callerFrame = getCallerFrameNode.execute(frame);
            MaterializedFrame genericDefFrame = RArguments.getEnclosingFrame(frame);

            ArgumentsSignature suppliedSignature = RArguments.getSuppliedSignature(frame);
            Object[] suppliedArguments = RArguments.getArguments(frame);
            Object result = helper.dispatch(frame, RArguments.getCall(frame), generic, type, null, callerFrame, genericDefFrame, suppliedSignature, suppliedArguments);
            throw new ReturnException(result, RArguments.getCall(frame));
        }

        /**
         * Get the first (logical) argument in the frame, and handle {@link RPromise}s and
         * {@link RArgsValuesAndNames}. If there is no actual argument, returns null. If there are
         * no formal arguments, throws the appropriate error.
         */
        private Object getEnclosingArg(VirtualFrame frame, String generic) {
            if (RArguments.getArgumentsLength(frame) == 0 || RArguments.getArgument(frame, 0) == null) {
                CompilerDirectives.transferToInterpreter();
                throw error(RError.Message.UNKNOWN_FUNCTION_USE_METHOD, generic, RRuntime.toString(RNull.instance));
            }
            Object enclosingArg = RArguments.getArgument(frame, 0);
            if (argsValueAndNamesProfile.profile(enclosingArg instanceof RArgsValuesAndNames)) {
                enclosingArg = getFirstVarArg((RArgsValuesAndNames) enclosingArg);
            } else if (enclosingArg == RMissing.instance) {
                firstArgMissing.enter();
                enclosingArg = getFirstNonMissingArg(frame, 1);
                if (enclosingArg == null) {
                    return null;
                }
            }

            if (promiseCheckHelper == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                promiseCheckHelper = insert(new PromiseCheckHelperNode());
            }
            return promiseCheckHelper.checkVisibleEvaluate(frame, enclosingArg);
        }

        private static Object getFirstNonMissingArg(VirtualFrame frame, int startIdx) {
            for (int i = startIdx; i < RArguments.getArgumentsLength(frame); i++) {
                Object arg = RArguments.getArgument(frame, i);
                if (arg instanceof RArgsValuesAndNames) {
                    return getFirstVarArg((RArgsValuesAndNames) arg);
                } else if (arg != RMissing.instance) {
                    return arg;
                }
            }
            // Maybe there is an argument with a default value different than NULL, but even GNU-R
            // just uses "NULL" class in this case...
            return RNull.instance;
        }

        private static Object getFirstVarArg(RArgsValuesAndNames varArgs) {
            return varArgs.isEmpty() ? RNull.instance : varArgs.getArgument(0);
        }
    }

    @RBuiltin(name = "NextMethod", visibility = CUSTOM, kind = SUBSTITUTE, parameterNames = {"generic", "object", "..."}, behavior = COMPLEX)
    public abstract static class NextMethod extends RBuiltinNode.Arg3 {

        @Child private LocalReadVariableNode rvnGroup = LocalReadVariableNode.create(RRuntime.R_DOT_GROUP, false);
        @Child private LocalReadVariableNode rvnClass = LocalReadVariableNode.create(RRuntime.R_DOT_CLASS, false);
        @Child private LocalReadVariableNode rvnGeneric = LocalReadVariableNode.create(RRuntime.R_DOT_GENERIC, false);
        @Child private LocalReadVariableNode rvnCall = LocalReadVariableNode.create(RRuntime.R_DOT_GENERIC_CALL_ENV, false);
        @Child private LocalReadVariableNode rvnDef = LocalReadVariableNode.create(RRuntime.R_DOT_GENERIC_DEF_ENV, false);

        @Child private CombineSignaturesNode combineSignatures;
        @Child private CollectArgumentsNode collectArguments = CollectArgumentsNodeGen.create();

        @Child private PromiseHelperNode promiseHelper;
        @Child private ClassHierarchyNode hierarchy;
        @Child private Helper helper = new Helper(true);

        private final ConditionProfile emptyArgsProfile = ConditionProfile.createBinaryProfile();
        private final ConditionProfile genericCallFrameNullProfile = ConditionProfile.createBinaryProfile();
        private final ConditionProfile genericDefFrameNullProfile = ConditionProfile.createBinaryProfile();
        private final ConditionProfile alternateClassHeaderProfile = ConditionProfile.createBinaryProfile();
        private ConditionProfile needToBoxStringProfile = ConditionProfile.createBinaryProfile();

        private final ValueProfile parameterSignatureProfile = ValueProfile.createIdentityProfile();
        private final ValueProfile suppliedParameterSignatureProfile = ValueProfile.createIdentityProfile();
        private final ValueProfile rootNodeProfile = ValueProfile.createClassProfile();

        static {
            Casts.noCasts(NextMethod.class);
        }

        @Override
        public Object[] getDefaultParameterValues() {
            return new Object[]{RNull.instance, RNull.instance, RArgsValuesAndNames.EMPTY};
        }

        /**
         * When {@code NextMethod} is invoked with first argument which is not a string, the
         * argument is swallowed and ignored.
         */
        @Specialization(guards = "isNotString(ignoredGeneric)")
        protected Object nextMethod(VirtualFrame frame, @SuppressWarnings("unused") Object ignoredGeneric, Object obj, RArgsValuesAndNames args) {
            String generic = (String) rvnGeneric.execute(frame);
            if (generic == null || generic.isEmpty()) {
                throw error(RError.Message.GEN_FUNCTION_NOT_SPECIFIED);
            }
            return nextMethod(frame, generic, obj, args);
        }

        protected static boolean isNotString(Object obj) {
            // Note: if RStringVector becomes expected, then it must have length == 1, GnuR
            // ignores character vectors longer than 1 as the "generic" argument of NextMethod
            assert !(obj instanceof RStringVector) || ((RStringVector) obj).getLength() != 1 : "unexpected RStringVector with length != 1";
            return !(obj instanceof String);
        }

        @SuppressWarnings("unused")
        @Specialization
        protected Object nextMethod(VirtualFrame frame, String generic, Object obj, RArgsValuesAndNames args) {
            MaterializedFrame genericCallFrame = getCallFrame(frame);
            MaterializedFrame genericDefFrame = getDefFrame(frame);
            String group = (String) rvnGroup.execute(frame);

            // The signature that will be used for the target of NextMethod is concatenation of the
            // actual signature used when invoking the S3 dispatch function combined with any named
            // arguments passed to NextMethod, the later override the former on a name clash
            ArgumentsSignature finalSignature;
            ArgumentsSignature suppliedSignature = suppliedParameterSignatureProfile.profile(RArguments.getSuppliedSignature(frame));
            Object[] suppliedArguments = collectArguments.execute(frame, parameterSignatureProfile.profile(RArguments.getSignature(frame, rootNodeProfile)));
            if (emptyArgsProfile.profile(args == RArgsValuesAndNames.EMPTY)) {
                finalSignature = suppliedSignature;
            } else {
                if (combineSignatures == null) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    combineSignatures = insert(CombineSignaturesNodeGen.create());
                }
                RArgsValuesAndNames combinedResult = combineSignatures.execute(suppliedSignature, suppliedArguments, args.getSignature(), args.getArguments());
                suppliedArguments = combinedResult.getArguments();
                finalSignature = combinedResult.getSignature();
            }
            // In GNU-R NextMethod is "Internal" so there is actually a frame created for it.
            // In FastR we only create an artificial RCaller, so that NextMethod appears in some of
            // the sys.* functions, but sys.frame is not going to work for it.
            RCaller currentCall = RArguments.getCall(frame);
            RCaller dispatchingCaller = RArguments.getCall(genericCallFrame);
            RCaller parentCaller = RCaller.createForGenericFunctionCall(dispatchingCaller, RCallerHelper.createFromArguments("NextMethod", RArgsValuesAndNames.EMPTY), currentCall);
            return helper.dispatch(frame, parentCaller, generic, readType(frame), group, genericCallFrame, genericDefFrame, finalSignature, suppliedArguments);
        }

        private MaterializedFrame getDefFrame(VirtualFrame frame) {
            MaterializedFrame genericDefFrame = (MaterializedFrame) rvnDef.execute(frame);
            if (genericDefFrameNullProfile.profile(genericDefFrame == null)) {
                genericDefFrame = RArguments.getEnclosingFrame(frame);
            }
            return genericDefFrame;
        }

        private MaterializedFrame getCallFrame(VirtualFrame frame) {
            MaterializedFrame genericCallFrame = (MaterializedFrame) rvnCall.execute(frame);
            if (genericCallFrameNullProfile.profile(genericCallFrame == null)) {
                genericCallFrame = frame.materialize();
            }
            return genericCallFrame;
        }

        private RStringVector readType(VirtualFrame frame) {
            Object storedClass = rvnClass.execute(frame);
            if (alternateClassHeaderProfile.profile(storedClass == null || storedClass == RNull.instance)) {
                return getAlternateClassHr(frame);
            } else {
                return boxString(storedClass);
            }
        }

        private RStringVector boxString(Object value) {
            if (needToBoxStringProfile.profile(value instanceof String)) {
                return RDataFactory.createStringVector((String) value);
            }
            return (RStringVector) value;
        }

        private RStringVector getAlternateClassHr(VirtualFrame frame) {
            if (promiseHelper == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                promiseHelper = insert(new PromiseHelperNode());
            }
            if (RArguments.getArgumentsLength(frame) == 0 || RArguments.getArgument(frame, 0) == null ||
                            (!(RArguments.getArgument(frame, 0) instanceof RAbstractContainer) && !(RArguments.getArgument(frame, 0) instanceof RPromise))) {
                throw error(RError.Message.OBJECT_NOT_SPECIFIED);
            }
            Object arg = RArguments.getArgument(frame, 0);
            if (arg instanceof RPromise) {
                arg = promiseHelper.evaluate(frame, (RPromise) arg);
            }
            if (hierarchy == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                hierarchy = insert(ClassHierarchyNode.createForDispatch(false));
            }
            return hierarchy.execute(arg);
        }
    }
}
