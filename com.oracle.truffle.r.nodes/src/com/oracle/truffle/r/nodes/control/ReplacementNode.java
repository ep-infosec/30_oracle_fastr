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

package com.oracle.truffle.r.nodes.control;

import java.util.ArrayList;
import java.util.List;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.r.nodes.access.RemoveAndAnswerNode;
import com.oracle.truffle.r.nodes.access.WriteVariableNode;
import com.oracle.truffle.r.nodes.access.variables.ReadVariableNode;
import com.oracle.truffle.r.nodes.function.PeekLocalVariableNode;
import com.oracle.truffle.r.nodes.function.RCallBaseNode;
import com.oracle.truffle.r.nodes.function.RCallSpecialNode;
import com.oracle.truffle.r.nodes.function.visibility.SetVisibilityNode;
import com.oracle.truffle.r.nodes.unary.GetNonSharedNode;
import com.oracle.truffle.r.runtime.ArgumentsSignature;
import com.oracle.truffle.r.runtime.RArguments;
import com.oracle.truffle.r.runtime.builtins.RBuiltinDescriptor;
import com.oracle.truffle.r.runtime.builtins.RSpecialFactory.FullCallNeededException;
import com.oracle.truffle.r.runtime.context.RContext;
import com.oracle.truffle.r.runtime.data.Closure;
import com.oracle.truffle.r.runtime.data.RDataFactory;
import com.oracle.truffle.r.runtime.data.RPairList;
import com.oracle.truffle.r.runtime.nodes.RCodeBuilder;
import com.oracle.truffle.r.runtime.nodes.RCodeBuilder.CodeBuilderContext;
import com.oracle.truffle.r.runtime.nodes.RNode;
import com.oracle.truffle.r.runtime.nodes.RSyntaxCall;
import com.oracle.truffle.r.runtime.nodes.RSyntaxElement;
import com.oracle.truffle.r.runtime.nodes.RSyntaxLookup;
import com.oracle.truffle.r.runtime.nodes.RSyntaxNode;

abstract class ReplacementNode extends OperatorNode {

    protected final RSyntaxElement lhs;

    ReplacementNode(SourceSection source, RSyntaxLookup operator, RSyntaxElement lhs) {
        super(source, operator);
        this.lhs = lhs;
    }

    public static ReplacementNode create(SourceSection source, RSyntaxLookup operator, RSyntaxLookup variable, RSyntaxElement lhs, RNode rhs, List<RSyntaxCall> calls,
                    boolean isSuper, int tempNamesStartIndex, boolean isVoid) {
        CompilerAsserts.neverPartOfCompilation();
        // Note: if specials are turned off in FastR, onlySpecials will never be true
        boolean createSpecial = hasOnlySpecialCalls(calls);
        if (createSpecial) {
            RSyntaxElement syntaxLHS = calls.get(0).getSyntaxLHS();
            String name;
            if (syntaxLHS instanceof RSyntaxLookup) {
                String symbol = ((RSyntaxLookup) syntaxLHS).getIdentifier();
                name = symbol + "<-";
            } else {
                // data types (and lengths) are verified in isNamespaceLookupCall
                RSyntaxElement[] args = ((RSyntaxCall) syntaxLHS).getSyntaxArguments();
                name = ((RSyntaxLookup) args[1]).getIdentifier() + "<-";
            }
            RBuiltinDescriptor builtinDescriptor = RContext.lookupBuiltinDescriptor(name);
            createSpecial &= builtinDescriptor != null && builtinDescriptor.getSpecialCall() != null;
        }

        if (createSpecial) {
            /*
             * This assumes that whenever there's a special call for the "extract", there's also a
             * special call for "replace".
             */
            if (isVoid) {
                return new SpecialVoidReplacementNode(source, operator, variable, lhs, rhs, calls, isSuper, tempNamesStartIndex);
            } else {
                return new SpecialReplacementNode(source, operator, variable, lhs, rhs, calls, isSuper, tempNamesStartIndex);
            }
        } else {
            return new GenericReplacementNode(source, operator, variable, lhs, rhs, calls, isSuper, tempNamesStartIndex);
        }
    }

    private static String getTargetTemp(int index) {
        return "*tmp*" + index;
    }

    private static String getRHSTemp(int index) {
        return "*rhs*" + index;
    }

    private static boolean hasOnlySpecialCalls(List<RSyntaxCall> calls) {
        for (int i = 0; i < calls.size(); i++) {
            if (!(calls.get(i) instanceof RCallSpecialNode)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Creates a call that looks like {@code fun} but has the first argument replaced with
     * {@code newLhs}.
     */
    private static RNode createSpecialFunctionQuery(RSyntaxCall fun, RSyntaxNode newFirstArg, CodeBuilderContext codeBuilderContext) {
        RCodeBuilder<RSyntaxNode> builder = RContext.getASTBuilder();
        RSyntaxElement[] arguments = fun.getSyntaxArguments();

        RSyntaxNode[] argNodes = new RSyntaxNode[arguments.length];
        for (int i = 0; i < arguments.length; i++) {
            argNodes[i] = i == 0 ? newFirstArg : builder.process(arguments[i], codeBuilderContext);
        }

        return RCallSpecialNode.createCallInReplace(fun.getLazySourceSection(), builder.process(fun.getSyntaxLHS(), codeBuilderContext).asRNode(), fun.getSyntaxSignature(), argNodes, 0).asRNode();
    }

    /**
     * Creates a call that looks like {@code fun}, but has its first argument replaced with
     * {@code newLhs}, its target turned into an update function ("foo<-"), with the given value
     * added to the arguments list.
     */
    private static RNode createFunctionUpdate(SourceSection source, RSyntaxNode newLhs, RSyntaxNode rhs, RSyntaxCall fun, CodeBuilderContext codeBuilderContext) {
        RCodeBuilder<RSyntaxNode> builder = RContext.getASTBuilder();
        RSyntaxElement[] arguments = fun.getSyntaxArguments();

        ArgumentsSignature signature = fun.getSyntaxSignature();
        RSyntaxNode[] argNodes = new RSyntaxNode[arguments.length + 1];
        String[] names = new String[argNodes.length];
        for (int i = 0; i < arguments.length; i++) {
            names[i] = signature.getName(i);
            argNodes[i] = i == 0 ? newLhs : builder.process(arguments[i], codeBuilderContext);
        }
        argNodes[argNodes.length - 1] = rhs;
        names[argNodes.length - 1] = "value";

        RSyntaxElement syntaxLHS = fun.getSyntaxLHS();
        RSyntaxNode newSyntaxLHS;
        if (syntaxLHS instanceof RSyntaxLookup) {
            RSyntaxLookup lookupLHS = (RSyntaxLookup) syntaxLHS;
            String symbol = lookupLHS.getIdentifier();
            newSyntaxLHS = builder.lookup(lookupLHS.getLazySourceSection(), symbol + "<-", true);
        } else {
            // data types (and lengths) are verified in isNamespaceLookupCall
            RSyntaxCall callLHS = (RSyntaxCall) syntaxLHS;
            RSyntaxElement[] oldArgs = callLHS.getSyntaxArguments();
            RSyntaxNode[] newArgs = new RSyntaxNode[2];
            newArgs[0] = (RSyntaxNode) oldArgs[0];
            newArgs[1] = builder.lookup(oldArgs[1].getLazySourceSection(), ((RSyntaxLookup) oldArgs[1]).getIdentifier() + "<-", true);
            newSyntaxLHS = RCallSpecialNode.createCall(callLHS.getLazySourceSection(), builder.process(callLHS.getSyntaxLHS(), codeBuilderContext).asRNode(), callLHS.getSyntaxSignature(), newArgs);
        }
        return RCallSpecialNode.createCallInReplace(source, newSyntaxLHS.asRNode(), ArgumentsSignature.get(names), argNodes, argNodes.length - 1).asRNode();
    }

    static RPairList getLanguage(WriteVariableNode wvn) {
        Node parent = wvn.getParent();
        if (parent instanceof ReplacementNode) {
            return RDataFactory.createLanguage(Closure.createLanguageClosure((ReplacementNode) parent));
        }
        return null;
    }

    private static RSyntaxNode createLookup(SourceSection source, String idenifier) {
        return RContext.getASTBuilder().lookup(source, idenifier, false);
    }

    private static RNode createReplacementTarget(RSyntaxLookup variable, boolean isSuper, boolean isSpecial) {
        if (isSuper) {
            return ReadVariableNode.wrap(variable.getLazySourceSection(), ReadVariableNode.createSuperLookup(variable.getIdentifier())).asRNode();
        } else {
            return isSpecial ? new PeekLocalVariableNode(variable.getIdentifier())
                            : ReadVariableNode.wrap(variable.getLazySourceSection(), ReadVariableNode.create(variable.getIdentifier(), true)).asRNode();
        }
    }

    private abstract static class ReplacementWithRhsNode extends ReplacementNode {

        @Child private WriteVariableNode storeRhs;
        @Child private RemoveAndAnswerNode removeRhs;
        @Child private SetVisibilityNode visibility;

        protected final RNode rhs;

        ReplacementWithRhsNode(SourceSection source, RSyntaxLookup operator, RSyntaxElement lhs, RNode rhs, int tempNamesStartIndex) {
            super(source, operator, lhs);
            this.rhs = rhs;

            this.storeRhs = WriteVariableNode.createAnonymous(getRHSTemp(tempNamesStartIndex), WriteVariableNode.Mode.INVISIBLE, rhs);
            this.removeRhs = RemoveAndAnswerNode.create(getRHSTemp(tempNamesStartIndex));
        }

        @Override
        public final Object execute(VirtualFrame frame) {
            RArguments.getCall(frame).checkEagerPromiseOnly();
            storeRhs.execute(frame);
            executeReplacement(frame);
            try {
                return removeRhs.execute(frame);
            } finally {
                if (visibility == null) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    visibility = insert(SetVisibilityNode.create());
                }
                visibility.execute(frame, false);
            }
        }

        @Override
        public final void voidExecute(VirtualFrame frame) {
            RArguments.getCall(frame).checkEagerPromiseOnly();
            storeRhs.execute(frame);
            executeReplacement(frame);
            removeRhs.execute(frame);
        }

        protected final void voidExecuteWithRhs(VirtualFrame frame, Object rhsValue) {
            storeRhs.execute(frame, rhsValue);
            executeReplacement(frame);
            removeRhs.execute(frame);
        }

        protected abstract void executeReplacement(VirtualFrame frame);

        @Override
        public final RSyntaxElement[] getSyntaxArguments() {
            return new RSyntaxElement[]{lhs, rhs.asRSyntaxNode()};
        }
    }

    /**
     * Replacement that is made of only special calls, if one of the special calls falls back to
     * full version, the replacement also falls back to {@link ReplacementNode}.
     */
    private static final class SpecialReplacementNode extends ReplacementWithRhsNode {

        @Child private RCallSpecialNode replaceCall;

        private final List<RSyntaxCall> calls;
        private final int tempNamesStartIndex;
        private final boolean isSuper;
        private final RSyntaxLookup variable;

        SpecialReplacementNode(SourceSection source, RSyntaxLookup operator, RSyntaxLookup variable, RSyntaxElement lhs, RNode rhs, List<RSyntaxCall> calls, boolean isSuper, int tempNamesStartIndex) {
            super(source, operator, lhs, rhs, tempNamesStartIndex);
            this.variable = variable;
            this.calls = calls;
            this.isSuper = isSuper;
            this.tempNamesStartIndex = tempNamesStartIndex;

            /*
             * Creates a replacement that consists only of {@link RCallSpecialNode} calls.
             */
            CodeBuilderContext codeBuilderContext = new CodeBuilderContext(tempNamesStartIndex + 2);
            RNode extractFunc = createReplacementTarget(variable, isSuper, true);
            for (int i = calls.size() - 1; i >= 1; i--) {
                extractFunc = createSpecialFunctionQuery(calls.get(i), extractFunc.asRSyntaxNode(), codeBuilderContext);
                ((RCallSpecialNode) extractFunc).setPropagateFullCallNeededException();
            }
            this.replaceCall = (RCallSpecialNode) createFunctionUpdate(source, extractFunc.asRSyntaxNode(), createLookup(RSyntaxNode.INTERNAL, getRHSTemp(tempNamesStartIndex)), calls.get(0),
                            codeBuilderContext);
            this.replaceCall.setPropagateFullCallNeededException();
        }

        @Override
        protected void executeReplacement(VirtualFrame frame) {
            try {
                // Note: the very last call is the actual assignment, e.g. [[<-, if this call's
                // argument is shared, it bails out. Moreover, if that call's argument is not
                // shared, it could not be extracted from a shared container (list), so we should be
                // OK with not calling any other update function and just update the value directly.
                replaceCall.execute(frame);
            } catch (FullCallNeededException e) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                replace(new GenericReplacementNode(getLazySourceSection(), operator, variable, lhs, RContext.getASTBuilder().process(rhs.asRSyntaxNode()).asRNode(), calls, isSuper,
                                tempNamesStartIndex)).executeReplacement(frame);
            }
        }
    }

    /**
     * Replacement that is made of only special calls, if one of the special calls falls back to
     * full version, the replacement also falls back to {@link ReplacementNode}. Additionally, this
     * type only works if the result of the call (the rhs) is not needed.
     */
    private static final class SpecialVoidReplacementNode extends ReplacementNode {

        @Child private RCallBaseNode replaceCall;

        private final RNode rhs;
        private final List<RSyntaxCall> calls;
        private final int tempNamesStartIndex;
        private final boolean isSuper;
        private final RSyntaxLookup variable;

        SpecialVoidReplacementNode(SourceSection source, RSyntaxLookup operator, RSyntaxLookup variable, RSyntaxElement lhs, RNode rhs, List<RSyntaxCall> calls, boolean isSuper,
                        int tempNamesStartIndex) {
            super(source, operator, lhs);
            this.variable = variable;
            this.rhs = rhs;
            this.calls = calls;
            this.isSuper = isSuper;
            this.tempNamesStartIndex = tempNamesStartIndex;

            /*
             * Creates a replacement that consists only of {@link RCallSpecialNode} calls.
             */
            CodeBuilderContext codeBuilderContext = new CodeBuilderContext(tempNamesStartIndex + 2);
            RNode extractFunc = createReplacementTarget(variable, isSuper, true);
            for (int i = calls.size() - 1; i >= 1; i--) {
                extractFunc = createSpecialFunctionQuery(calls.get(i), extractFunc.asRSyntaxNode(), codeBuilderContext);
                ((RCallSpecialNode) extractFunc).setPropagateFullCallNeededException();
            }
            RCallSpecialNode node = (RCallSpecialNode) createFunctionUpdate(source, extractFunc.asRSyntaxNode(), rhs.asRSyntaxNode(), calls.get(0), codeBuilderContext);
            node.setPropagateFullCallNeededException();
            this.replaceCall = node;
        }

        @Override
        public Object execute(VirtualFrame frame) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            GenericReplacementNode replacement = new GenericReplacementNode(getLazySourceSection(), operator, variable, lhs, RContext.getASTBuilder().process(rhs.asRSyntaxNode()).asRNode(), calls,
                            isSuper, tempNamesStartIndex);
            return replace(replacement).execute(frame);
        }

        @Override
        public void voidExecute(VirtualFrame frame) {
            try {
                // Note: the very last call is the actual assignment, e.g. [[<-, if this call's
                // argument is shared, it bails out. Moreover, if that call's argument is not
                // shared, it could not be extracted from a shared container (list), so we should be
                // OK with not calling any other update function and just update the value directly.
                replaceCall.execute(frame);
            } catch (FullCallNeededException e) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                GenericReplacementNode replacement = replace(
                                new GenericReplacementNode(getLazySourceSection(), operator, variable, lhs, RContext.getASTBuilder().process(rhs.asRSyntaxNode()).asRNode(),
                                                calls, isSuper, tempNamesStartIndex));

                if (e.rhsValue == null) {
                    // we haven't queried the rhs value yet
                    replacement.voidExecute(frame);
                } else {
                    // rhs was already queried, so pass it along
                    replacement.voidExecuteWithRhs(frame, e.rhsValue);
                }
            }
        }

        @Override
        public RSyntaxElement[] getSyntaxArguments() {
            return new RSyntaxElement[]{lhs, rhs.asRSyntaxNode()};
        }
    }

    /**
     * Holds the sequence of nodes created for R's replacement assignment.
     */
    private static final class GenericReplacementNode extends ReplacementWithRhsNode {

        @Child private RemoveAndAnswerNode targetTmpRemove;

        @Children private final WriteVariableNode[] updates;

        GenericReplacementNode(SourceSection source, RSyntaxLookup operator, RSyntaxLookup variable, RSyntaxElement lhs, RNode rhs, List<RSyntaxCall> calls, boolean isSuper, int tempNamesStartIndex) {
            super(source, operator, lhs, rhs, tempNamesStartIndex);
            /*
             * When there are more than two function calls in LHS, then we save some function calls
             * by saving the intermediate results into temporary variables and reusing them.
             */
            List<WriteVariableNode> instructions = new ArrayList<>();
            CodeBuilderContext codeBuilderContext = new CodeBuilderContext(tempNamesStartIndex + calls.size() + 1);

            int targetIndex = tempNamesStartIndex;
            RNode target = createReplacementTarget(variable, isSuper, false);
            instructions.add(WriteVariableNode.createAnonymous(getTargetTemp(targetIndex), WriteVariableNode.Mode.INVISIBLE, wrapForSlotUpdate(target, calls.get(calls.size() - 1))));
            /*
             * Create the calls that extract inner components - only needed for complex replacements
             * like "a(b(x)) <- z" (where we would extract "b(x)"). The extracted values are saved
             * into temporary variables *tmp*{index} indexed from tempNamesIndex to (tempNamesIndex
             * + calls.size()-1), the first such temporary variable holds the "target" of the
             * replacement, 'x' in our example (the assignment from 'x' is not done in this loop).
             */
            for (int i = calls.size() - 1; i >= 1; i--) {
                RSyntaxNode newFirstArg = createLookup(RSyntaxNode.INTERNAL, getTargetTemp(targetIndex));
                RNode extract = createSpecialFunctionQuery(calls.get(i), newFirstArg, codeBuilderContext);
                instructions.add(WriteVariableNode.createAnonymous(getTargetTemp(++targetIndex), WriteVariableNode.Mode.INVISIBLE, wrapForSlotUpdate(extract, calls.get(i - 1))));
            }
            /*
             * Create the update calls, for "a(b(x)) <- z", this would be `a<-` and `b<-`, the
             * intermediate results are stored to temporary variables *rhs*{index}.
             */
            int replacementIndex = tempNamesStartIndex;
            for (int i = 0; i < calls.size(); i++) {
                RNode update = createFunctionUpdate(source, createLookup(RSyntaxNode.INTERNAL, getTargetTemp(targetIndex--)), createLookup(RSyntaxNode.INTERNAL, getRHSTemp(replacementIndex)),
                                calls.get(i),
                                codeBuilderContext);
                if (i < calls.size() - 1) {
                    instructions.add(WriteVariableNode.createAnonymous(getRHSTemp(++replacementIndex), WriteVariableNode.Mode.INVISIBLE, update));
                } else {
                    instructions.add(WriteVariableNode.createAnonymous(variable.getIdentifier(), WriteVariableNode.Mode.REGULAR, update, isSuper));
                }
            }

            this.updates = instructions.toArray(new WriteVariableNode[instructions.size()]);
            this.targetTmpRemove = RemoveAndAnswerNode.create(getTargetTemp(tempNamesStartIndex));
        }

        /*
         * This is complicated, but at this point seems like the only way to get setClass to work
         * properly. the underlying problem is that slot<- and @<- modify shared values, whereas,
         * e.g., [[<- does not.
         */
        private static RNode wrapForSlotUpdate(RNode target, RSyntaxCall call) {
            RSyntaxElement syntaxLHS = call.getSyntaxLHS();
            if (syntaxLHS instanceof RSyntaxLookup) {
                String symbol = ((RSyntaxLookup) syntaxLHS).getIdentifier();
                if ("slot".equals(symbol) || "@".equals(symbol)) {
                    return new GetNonSharedNode.GetNonSharedSyntaxNode(target);
                }
            }
            return target;
        }

        @Override
        @ExplodeLoop
        protected void executeReplacement(VirtualFrame frame) {
            for (RNode update : updates) {
                update.execute(frame);
            }
            targetTmpRemove.execute(frame);
        }
    }

    @Override
    public ArgumentsSignature getSyntaxSignature() {
        return ArgumentsSignature.empty(2);
    }
}
