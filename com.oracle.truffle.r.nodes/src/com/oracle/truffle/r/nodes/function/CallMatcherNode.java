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
 * Copyright (c) 2014, 2022, Oracle and/or its affiliates
 *
 * All rights reserved.
 */

package com.oracle.truffle.r.nodes.function;

import static com.oracle.truffle.r.runtime.context.FastROptions.RestrictForceSplitting;

import java.util.function.Supplier;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.NodeCost;
import com.oracle.truffle.api.nodes.NodeInfo;
import com.oracle.truffle.api.profiles.ConditionProfile;
import com.oracle.truffle.r.nodes.RRootNode;
import com.oracle.truffle.r.nodes.builtin.RBuiltinNode;
import com.oracle.truffle.r.nodes.function.ArgumentMatcher.MatchPermutation;
import com.oracle.truffle.r.nodes.function.call.CallRBuiltinCachedNode;
import com.oracle.truffle.r.nodes.function.call.CallRFunctionCachedNode;
import com.oracle.truffle.r.nodes.function.call.CallRFunctionCachedNodeGen;
import com.oracle.truffle.r.nodes.function.call.CallRFunctionNode;
import com.oracle.truffle.r.nodes.function.signature.VarArgsHelper;
import com.oracle.truffle.r.nodes.function.visibility.SetVisibilityNode;
import com.oracle.truffle.r.runtime.ArgumentsSignature;
import com.oracle.truffle.r.runtime.DSLConfig;
import com.oracle.truffle.r.runtime.RArguments;
import com.oracle.truffle.r.runtime.RArguments.DispatchArgs;
import com.oracle.truffle.r.runtime.RCaller;
import com.oracle.truffle.r.runtime.RInternalError;
import com.oracle.truffle.r.runtime.RVisibility;
import com.oracle.truffle.r.runtime.builtins.FastPathFactory;
import com.oracle.truffle.r.runtime.builtins.RBuiltinDescriptor;
import com.oracle.truffle.r.runtime.context.RContext;
import com.oracle.truffle.r.runtime.data.RArgsValuesAndNames;
import com.oracle.truffle.r.runtime.data.REmpty;
import com.oracle.truffle.r.runtime.data.RFunction;
import com.oracle.truffle.r.runtime.data.RMissing;
import com.oracle.truffle.r.runtime.data.RPromise;
import com.oracle.truffle.r.runtime.nodes.RBaseNode;
import com.oracle.truffle.r.runtime.nodes.RFastPathNode;
import com.oracle.truffle.r.runtime.nodes.RSyntaxElement;

public abstract class CallMatcherNode extends RBaseNode {

    protected final boolean argsAreEvaluated;

    @Child private PromiseHelperNode promiseHelper;

    protected final ConditionProfile missingArgProfile = ConditionProfile.createBinaryProfile();
    protected final ConditionProfile emptyArgProfile = ConditionProfile.createBinaryProfile();

    private CallMatcherNode(boolean argsAreEvaluated) {
        this.argsAreEvaluated = argsAreEvaluated;
    }

    private static final int MAX_CACHE_DEPTH = 4;

    public static CallMatcherNode create(boolean argsAreEvaluated) {
        return new CallMatcherUninitializedNode(argsAreEvaluated, 0);
    }

    public abstract Object execute(VirtualFrame frame, RCaller parentCaller, RCaller dispatchCaller, ArgumentsSignature suppliedSignature, Object[] suppliedArguments, RFunction function,
                    String functionName, DispatchArgs dispatchArgs);

    protected CallMatcherCachedNode specialize(ArgumentsSignature suppliedSignature, Object[] suppliedArguments, RFunction function, CallMatcherNode next) {

        // Note: suppliedSignature and suppliedArguments are in the form as they would be
        // used for the dispatch method (e.g. the method that invokes UseMethod, supplied arguments
        // contains supplied names for the arguments, but in the order according to the formal
        // signature, see ArgumentMatcher and MatchPermutation for details).
        //
        // So if the dispatch method had three formal arguments, these two arrays will have three
        // elements each, but some of these elements may be varargs effectively carrying another
        // array of arguments and their signature in them. What we do here is that we unfold varargs
        // which gives us all the arguments and their signature in one flat array, then we match
        // this to the formal signature of the next method that we want to invoke (e.g. what
        // UseMethod invokes), and we save the information about how we permuted the arguments to
        // the newly created CallMatcherCachedNode.

        int argCount = suppliedArguments.length;

        // extract vararg signatures from the arguments
        VarArgsHelper varArgsInfo = VarArgsHelper.create(suppliedSignature, suppliedArguments);
        int argListSize = varArgsInfo.getArgListSize();

        // see flattenIndexes for the interpretation of the values
        long[] preparePermutation;
        ArgumentsSignature resultSignature;
        if (varArgsInfo.hasVarArgs()) {
            resultSignature = varArgsInfo.flattenNames(suppliedSignature);
            preparePermutation = varArgsInfo.flattenIndexes(suppliedSignature);
        } else {
            preparePermutation = new long[argListSize];
            String[] newSuppliedSignature = new String[argListSize];
            int index = 0;
            for (int i = 0; i < argCount; i++) {
                if (!suppliedSignature.isUnmatched(i)) {
                    preparePermutation[index] = i;
                    newSuppliedSignature[index] = suppliedSignature.getName(i);
                    index++;
                }
            }
            resultSignature = ArgumentsSignature.get(newSuppliedSignature);
        }

        assert resultSignature != null;
        ArgumentsSignature formalSignature = ArgumentMatcher.getFunctionSignature(function);
        MatchPermutation permutation = ArgumentMatcher.matchArguments(resultSignature, formalSignature, this, function.getRBuiltin());

        return new CallMatcherCachedNode(suppliedSignature, varArgsInfo.getVarArgsSignatures(), function, preparePermutation, permutation, argsAreEvaluated, next);
    }

    protected final void evaluatePromises(VirtualFrame frame, RFunction function, Object[] args, int varArgIndex) {
        if (function.isBuiltin()) {
            if (!argsAreEvaluated) {
                for (int i = 0; i < args.length; i++) {
                    Object arg = args[i];
                    if (arg instanceof RPromise) {
                        if (promiseHelper == null) {
                            CompilerDirectives.transferToInterpreterAndInvalidate();
                            promiseHelper = insert(new PromiseHelperNode());
                        }
                        args[i] = promiseHelper.evaluate(frame, (RPromise) arg);
                    } else if (varArgIndex == i && arg instanceof RArgsValuesAndNames) {
                        evaluatePromises(frame, (RArgsValuesAndNames) arg);
                    }
                }
            }
            replaceMissingArguments(function, args);
        }
    }

    private void evaluatePromises(VirtualFrame frame, RArgsValuesAndNames argsValuesAndNames) {
        Object[] args = argsValuesAndNames.getArguments();
        for (int i = 0; i < args.length; i++) {
            Object arg = args[i];
            if (arg instanceof RPromise) {
                if (promiseHelper == null) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    promiseHelper = insert(new PromiseHelperNode());
                }
                args[i] = promiseHelper.evaluate(frame, (RPromise) arg);
            }
        }
    }

    // The implementation only differs in whether it has @ExplodeLoop annotation
    // The two implementations are identical and should be kept in sync
    protected abstract void replaceMissingArguments(RFunction function, Object[] args);

    @NodeInfo(cost = NodeCost.UNINITIALIZED)
    private static final class CallMatcherUninitializedNode extends CallMatcherNode {
        CallMatcherUninitializedNode(boolean argsAreEvaluated, int depth) {
            super(argsAreEvaluated);
            this.depth = depth;
        }

        private final int depth;

        @Override
        public Object execute(VirtualFrame frame, RCaller parentCaller, RCaller dispatchCaller, ArgumentsSignature suppliedSignature, Object[] suppliedArguments, RFunction function,
                        String functionName, DispatchArgs dispatchArgs) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            if (depth >= DSLConfig.getCacheSize(MAX_CACHE_DEPTH)) {
                return replace(new CallMatcherGenericNode(argsAreEvaluated)).execute(frame, parentCaller, dispatchCaller, suppliedSignature, suppliedArguments, function, functionName, dispatchArgs);
            } else {
                CallMatcherCachedNode cachedNode = replace(specialize(suppliedSignature, suppliedArguments, function, new CallMatcherUninitializedNode(argsAreEvaluated, depth + 1)));
                // for splitting if necessary
                if (cachedNode.call != null && RCallNode.needsSplitting(function.getTarget())) {
                    if (!RContext.getInstance(this).getOption(RestrictForceSplitting)) {
                        cachedNode.call.getCallNode().cloneCallTarget();
                    }
                }
                return cachedNode.execute(frame, parentCaller, dispatchCaller, suppliedSignature, suppliedArguments, function, functionName, dispatchArgs);
            }
        }

        @Override
        protected void replaceMissingArguments(RFunction function, Object[] args) {
            throw RInternalError.shouldNotReachHere();
        }
    }

    private static final class CallMatcherCachedNode extends CallMatcherNode {

        @Child private CallMatcherNode next;
        @Child private CallRFunctionNode call;
        @Child private RBuiltinNode builtin;
        @Child private SetVisibilityNode visibility = SetVisibilityNode.create();

        private final RBuiltinDescriptor builtinDescriptor;
        private final ArgumentsSignature cachedSuppliedSignature;
        private final ArgumentsSignature[] cachedVarArgSignatures;
        private final RFunction cachedFunction;
        /**
         * {@link VarArgsHelper#flattenNames(ArgumentsSignature)} for the interpretation of the
         * values.
         */
        @CompilationFinal(dimensions = 1) private final long[] preparePermutation;
        private final MatchPermutation permutation;
        private final FormalArguments formals;
        @Child private RFastPathNode fastPath;
        private final RVisibility fastPathVisibility;

        CallMatcherCachedNode(ArgumentsSignature suppliedSignature, ArgumentsSignature[] varArgSignatures, RFunction function, long[] preparePermutation, MatchPermutation permutation,
                        boolean argsAreEvaluated, CallMatcherNode next) {
            super(argsAreEvaluated);
            this.cachedSuppliedSignature = suppliedSignature;
            this.cachedVarArgSignatures = varArgSignatures;
            this.cachedFunction = function;
            this.preparePermutation = preparePermutation;
            this.permutation = permutation;
            this.next = next;
            RRootNode root = (RRootNode) cachedFunction.getRootNode();
            this.formals = root.getFormalArguments();
            if (function.isBuiltin()) {
                this.builtinDescriptor = function.getRBuiltin();
                this.builtin = RBuiltinNode.inline(builtinDescriptor);
                this.fastPath = null;
                this.fastPathVisibility = null;
            } else {
                this.call = CallRFunctionNode.create(function.getTarget());
                this.builtinDescriptor = null;
                FastPathFactory fastPathFactory = root.getFastPath();
                this.fastPath = fastPathFactory == null ? null : fastPathFactory.create();
                this.fastPathVisibility = fastPathFactory == null ? null : fastPathFactory.getVisibility();
            }
        }

        @Override
        public RBaseNode getErrorContext() {
            return builtin == null ? super.getErrorContext() : builtin.getErrorContext();
        }

        @Override
        public Object execute(VirtualFrame frame, RCaller parentCaller, RCaller dispatchCaller, ArgumentsSignature suppliedSignature, Object[] suppliedArguments, RFunction function,
                        String functionName, DispatchArgs dispatchArgs) {
            if (suppliedSignature == cachedSuppliedSignature && function == cachedFunction && checkLastArgSignature(cachedSuppliedSignature, suppliedArguments)) {

                // Note: see CallMatcherNode#specialize for details on suppliedSignature/Arguments

                // this unrolls all varargs instances in suppliedArgs into a flat array of arguments
                Object[] preparedArguments = prepareSuppliedArgument(preparePermutation, suppliedArguments);

                // This is then matched to formal signature: the result is non-flat array of
                // arguments possibly containing varargs -- something that argument matching for a
                // direct function call would create would this be a direct function call
                RArgsValuesAndNames matchedArgs = ArgumentMatcher.matchArgumentsEvaluated(permutation, preparedArguments, null, formals);
                Object[] reorderedArgs = matchedArgs.getArguments();
                evaluatePromises(frame, cachedFunction, reorderedArgs, formals.getSignature().getVarArgIndex());
                if (call != null) {
                    if (fastPath != null) {
                        Object result = fastPath.execute(frame, reorderedArgs);
                        if (result != null) {
                            assert fastPathVisibility != null;
                            visibility.execute(frame, fastPathVisibility);
                            return result;
                        }
                        CompilerDirectives.transferToInterpreterAndInvalidate();
                        fastPath = null;
                    }

                    RCaller parent = dispatchCaller != null ? dispatchCaller : RArguments.getCall(frame).getPrevious();
                    String genFunctionName = functionName == null ? function.getName() : functionName;
                    Supplier<RSyntaxElement> argsSupplier = RCallerHelper.createFromArguments(genFunctionName, preparePermutation, suppliedArguments, suppliedSignature);
                    RCaller caller;
                    if (genFunctionName == null) {
                        caller = RCaller.createInvalid(frame, parent);
                    } else {
                        caller = RCaller.createForGenericFunctionCall(parent, argsSupplier, parentCaller != null ? parentCaller : RArguments.getCall(frame));
                    }
                    try {
                        return call.execute(frame, cachedFunction, caller, null, reorderedArgs, matchedArgs.getSignature(), cachedFunction.getEnclosingFrame(), dispatchArgs);
                    } finally {
                        visibility.executeAfterCall(frame, caller);
                    }
                } else {
                    Object result = builtin.call(frame, reorderedArgs);
                    visibility.execute(frame, builtinDescriptor.getVisibility());
                    return result;
                }
            } else {
                return next.execute(frame, parentCaller, dispatchCaller, suppliedSignature, suppliedArguments, function, functionName, dispatchArgs);
            }
        }

        // The implementation only differs in whether it has @ExplodeLoop annotation
        // The two implementations are identical and should be kept in sync
        @Override
        @ExplodeLoop
        protected void replaceMissingArguments(RFunction function, Object[] args) {
            for (int i = 0; i < formals.getSignature().getLength(); i++) {
                Object arg = args[i];
                if (formals.getInternalDefaultArgumentAt(i) != RMissing.instance && (missingArgProfile.profile(arg == RMissing.instance) || emptyArgProfile.profile(arg == REmpty.instance))) {
                    args[i] = formals.getInternalDefaultArgumentAt(i);
                }
            }
        }

        @ExplodeLoop
        private boolean checkLastArgSignature(ArgumentsSignature cachedSuppliedSignature2, Object[] arguments) {
            for (int i = 0; i < cachedSuppliedSignature2.getLength(); i++) {
                Object arg = arguments[i];
                if (arg instanceof RArgsValuesAndNames) {
                    if (cachedVarArgSignatures == null || cachedVarArgSignatures[i] != ((RArgsValuesAndNames) arg).getSignature()) {
                        return false;
                    }
                } else {
                    if (cachedVarArgSignatures != null && cachedVarArgSignatures[i] != null) {
                        return false;
                    }
                }
            }
            return true;
        }

        @ExplodeLoop
        private static Object[] prepareSuppliedArgument(long[] preparePermutation, Object[] arguments) {
            Object[] values = new Object[preparePermutation.length];
            for (int i = 0; i < values.length; i++) {
                long source = preparePermutation[i];
                if (VarArgsHelper.isVarArgsIndex(source)) {
                    int varArgsIdx = VarArgsHelper.extractVarArgsIndex(source);
                    int argsIdx = VarArgsHelper.extractVarArgsArgumentIndex(source);
                    RArgsValuesAndNames varargs = (RArgsValuesAndNames) arguments[varArgsIdx];
                    values[i] = varargs.getArguments()[argsIdx];
                } else {
                    values[i] = arguments[(int) source];
                }
            }
            return values;
        }
    }

    public static final class CallMatcherGenericNode extends CallMatcherNode {

        CallMatcherGenericNode(boolean argsAreEvaluated) {
            super(argsAreEvaluated);
        }

        @Child private CallRFunctionCachedNode call = CallRFunctionCachedNodeGen.create(2);
        @Child private CallRBuiltinCachedNode callRBuiltin = CallRBuiltinCachedNode.create(2);

        @Override
        public Object execute(VirtualFrame frame, RCaller parentCaller, RCaller dispatchCaller, ArgumentsSignature suppliedSignature, Object[] suppliedArguments, RFunction function,
                        String functionName, DispatchArgs dispatchArgs) {
            RArgsValuesAndNames reorderedArgs = reorderArguments(suppliedArguments, function, suppliedSignature, this);
            evaluatePromises(frame, function, reorderedArgs.getArguments(), ((RRootNode) function.getRootNode()).getFormalArguments().getSignature().getVarArgIndex());

            RCaller parent = dispatchCaller != null ? dispatchCaller : RArguments.getCall(frame).getPrevious();
            String genFunctionName = functionName == null ? function.getName() : functionName;

            RCaller caller;
            if (genFunctionName == null) {
                caller = RCaller.createInvalid(frame, parent);
            } else {
                Supplier<RSyntaxElement> argsSupplier = RCallerHelper.createFromArguments(genFunctionName, new RArgsValuesAndNames(suppliedArguments, suppliedSignature));
                caller = RCaller.createForGenericFunctionCall(parent, argsSupplier, parentCaller != null ? parentCaller : RArguments.getCall(frame));
            }

            if (function.isBuiltin()) {
                return callRBuiltin.execute(frame, function, reorderedArgs.getArguments());
            } else {
                return call.execute(frame, function, caller, null, reorderedArgs.getArguments(), reorderedArgs.getSignature(), function.getEnclosingFrame(), dispatchArgs);
            }
        }

        // The implementation only differs in whether it has @ExplodeLoop annotation
        // The two implementations are identical and should be kept in sync
        @Override
        protected void replaceMissingArguments(RFunction function, Object[] args) {
            FormalArguments formals = ((RRootNode) function.getRootNode()).getFormalArguments();
            for (int i = 0; i < formals.getSignature().getLength(); i++) {
                Object arg = args[i];
                if (formals.getInternalDefaultArgumentAt(i) != RMissing.instance && (missingArgProfile.profile(arg == RMissing.instance) || emptyArgProfile.profile(arg == REmpty.instance))) {
                    args[i] = formals.getInternalDefaultArgumentAt(i);
                }
            }
        }

        @TruffleBoundary
        public static RArgsValuesAndNames reorderArguments(Object[] args, RFunction function, ArgumentsSignature paramSignature, RBaseNode callingNode) {
            assert paramSignature.getLength() == args.length;

            int argCount = args.length;
            int argListSize = argCount;

            boolean hasVarArgs = false;
            boolean hasUnmatched = false;
            for (int fi = 0; fi < argCount; fi++) {
                Object arg = args[fi];
                if (arg instanceof RArgsValuesAndNames) {
                    hasVarArgs = true;
                    argListSize += ((RArgsValuesAndNames) arg).getLength() - 1;
                } else if (paramSignature.isUnmatched(fi)) {
                    hasUnmatched = true;
                    argListSize--;
                }
            }

            Object[] argValues;
            ArgumentsSignature signature;
            if (hasVarArgs) {
                argValues = new Object[argListSize];
                String[] argNames = new String[argListSize];
                int index = 0;
                for (int fi = 0; fi < argCount; fi++) {
                    Object arg = args[fi];
                    if (arg instanceof RArgsValuesAndNames) {
                        RArgsValuesAndNames varArgs = (RArgsValuesAndNames) arg;
                        Object[] varArgValues = varArgs.getArguments();
                        ArgumentsSignature varArgSignature = varArgs.getSignature();
                        for (int i = 0; i < varArgs.getLength(); i++) {
                            argNames[index] = varArgSignature.getName(i);
                            argValues[index++] = varArgValues[i];
                        }
                    } else if (!paramSignature.isUnmatched(fi)) {
                        argNames[index] = paramSignature.getName(fi);
                        argValues[index++] = arg;
                    }
                }
                signature = ArgumentsSignature.get(argNames);
            } else {
                argValues = new Object[argListSize];
                String[] newSignature = hasUnmatched ? new String[argListSize] : null;
                int index = 0;
                for (int i = 0; i < argCount; i++) {
                    if (!hasUnmatched || !paramSignature.isUnmatched(i)) {
                        argValues[index] = args[i];
                        if (hasUnmatched) {
                            newSignature[index] = paramSignature.getName(i);
                        }
                        index++;
                    }
                }
                signature = hasUnmatched ? ArgumentsSignature.get(newSignature) : paramSignature;
            }

            // ...and use them as 'supplied' arguments...
            RArgsValuesAndNames evaledArgs = new RArgsValuesAndNames(argValues, signature);

            // ...to match them against the chosen function's formal arguments
            return ArgumentMatcher.matchArgumentsEvaluated((RRootNode) function.getRootNode(), evaledArgs, null, callingNode);
        }

        protected static Object checkMissing(Object value) {
            return RMissingHelper.isMissing(value) || (value instanceof RPromise && RMissingHelper.isMissingName((RPromise) value)) ? null : value;
        }
    }
}
