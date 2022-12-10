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
package com.oracle.truffle.r.nodes.function;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.NodeCost;
import com.oracle.truffle.api.nodes.NodeInfo;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.r.runtime.data.nodes.attributes.GetFixedAttributeNode;
import com.oracle.truffle.r.nodes.function.visibility.SetVisibilityNode;
import com.oracle.truffle.r.runtime.Arguments;
import com.oracle.truffle.r.runtime.ArgumentsSignature;
import static com.oracle.truffle.r.runtime.context.FastROptions.UseSpecials;
import com.oracle.truffle.r.runtime.RDeparse;
import com.oracle.truffle.r.runtime.RDispatch;
import com.oracle.truffle.r.runtime.RInternalError;
import com.oracle.truffle.r.runtime.RRuntime;
import com.oracle.truffle.r.runtime.RVisibility;
import com.oracle.truffle.r.runtime.builtins.RBuiltinDescriptor;
import com.oracle.truffle.r.runtime.builtins.RSpecialFactory;
import com.oracle.truffle.r.runtime.context.RContext;
import com.oracle.truffle.r.runtime.data.RAttributable;
import com.oracle.truffle.r.runtime.data.RFunction;
import com.oracle.truffle.r.runtime.nodes.RBaseNode;
import com.oracle.truffle.r.runtime.nodes.RNode;
import com.oracle.truffle.r.runtime.nodes.RSyntaxCall;
import com.oracle.truffle.r.runtime.nodes.RSyntaxConstant;
import com.oracle.truffle.r.runtime.nodes.RSyntaxElement;
import com.oracle.truffle.r.runtime.nodes.RSyntaxLookup;
import com.oracle.truffle.r.runtime.nodes.RSyntaxNode;

@NodeChild(value = "delegate", type = RNode.class)
abstract class ClassCheckNode extends RNode {

    public abstract RNode getDelegate();

    @Override
    protected RSyntaxNode getRSyntaxNode() {
        return getDelegate().asRSyntaxNode();
    }

    @Specialization
    protected static int doInt(int value) {
        return value;
    }

    @Specialization
    protected static double doDouble(double value) {
        return value;
    }

    @Specialization
    protected static byte doLogical(byte value) {
        return value;
    }

    @Specialization
    protected static String doString(String value) {
        return value;
    }

    @Specialization(guards = "!hasAttributes(storage)")
    protected static RAttributable doEmptyAttrStorage(RAttributable storage) {
        return storage;
    }

    @Specialization(guards = "hasAttributes(storage)")
    protected static RAttributable doEmptyAttrStorage(RAttributable storage,
                    @Cached("createClass()") GetFixedAttributeNode getClassAttrNode) {
        if (getClassAttrNode.execute(storage) != null) {
            throw RSpecialFactory.throwFullCallNeeded();
        }
        return storage;
    }

    protected static boolean hasAttributes(RAttributable storage) {
        return storage.getAttributes() != null;
    }

    protected static boolean isAttributableStorage(Object obj) {
        return obj instanceof RAttributable;
    }

    @Specialization(guards = "!isAttributableStorage(value)")
    public Object doGeneric(Object value,
                    @Cached("create()") ClassHierarchyNode classHierarchy) {
        if (classHierarchy.execute(value) != null) {
            throw RSpecialFactory.throwFullCallNeeded();
        }
        return value;
    }
}

@NodeInfo(cost = NodeCost.NONE)
public final class RCallSpecialNode extends RCallBaseNode implements RSyntaxNode, RSyntaxCall {

    // currently cannot be RSourceSectionNode because of TruffleDSL restrictions
    @CompilationFinal private SourceSection sourceSection;

    @Override
    public Arguments<RSyntaxNode> getArguments() {
        return Arguments.create(arguments, signature);
    }

    @Override
    public RNode getFunction() {
        return functionNode;
    }

    @Override
    public void setSourceSection(SourceSection sourceSection) {
        assert sourceSection != null;
        this.sourceSection = sourceSection;
    }

    @Override
    public SourceSection getLazySourceSection() {
        return sourceSection;
    }

    @Override
    public SourceSection getSourceSection() {
        RDeparse.ensureSourceSection(this);
        return sourceSection;
    }

    @Override
    public RBaseNode getErrorContext() {
        return this;
    }

    @Child private RNode functionNode;
    @Child private RNode special;
    @Child private SetVisibilityNode visibility;

    private final RSyntaxNode[] arguments;
    private final ArgumentsSignature signature;
    private final RBuiltinDescriptor expectedFunction;
    private final RVisibility visible;

    /**
     * If this is true, then any bailout should simply be forwarded by re-throwing the exception.
     */
    private boolean propagateFullCallNeededException;

    /**
     * If this is non-null, then any bailout should lead to be forwarded by re-throwing the
     * exception after replacing itself with a proper call node.
     */
    private RCallSpecialNode callSpecialParent;

    private RCallSpecialNode(SourceSection sourceSection, RNode functionNode, RBuiltinDescriptor expectedFunction, RSyntaxNode[] arguments, ArgumentsSignature signature, RNode special) {
        this.sourceSection = sourceSection;
        this.expectedFunction = expectedFunction;
        this.special = special;
        this.functionNode = functionNode;
        this.arguments = arguments;
        this.signature = signature;
        this.visible = expectedFunction.getVisibility();
    }

    /**
     * This passes {@code true} for the isReplacement parameter and ignores the specified arguments,
     * i.e., does not modify them in any way before passing it to
     * {@link RSpecialFactory#create(ArgumentsSignature, RNode[], boolean)}.
     */
    public static RSyntaxNode createCallInReplace(SourceSection sourceSection, RNode functionNode, ArgumentsSignature signature, RSyntaxNode[] arguments,
                    int... ignoredArguments) {
        return createCall(sourceSection, functionNode, signature, arguments, true, ignoredArguments);
    }

    public static RSyntaxNode createCall(SourceSection sourceSection, RNode functionNode, ArgumentsSignature signature, RSyntaxNode[] arguments) {
        return createCall(sourceSection, functionNode, signature, arguments, false);
    }

    private static RSyntaxNode createCall(SourceSection sourceSection, RNode functionNode, ArgumentsSignature signature, RSyntaxNode[] arguments, boolean inReplace, int... ignoredArguments) {
        RCallSpecialNode special = null;
        if (RContext.getInstance().getOption(UseSpecials)) {
            special = tryCreate(sourceSection, functionNode, signature, arguments, inReplace, ignoredArguments);
        }
        if (special != null) {
            return special;
        } else {
            return RCallNode.createCall(sourceSection, functionNode, signature, arguments);
        }
    }

    private static RCallSpecialNode tryCreate(SourceSection sourceSection, RNode functionNode, ArgumentsSignature signature, RSyntaxNode[] arguments,
                    boolean inReplace, int[] ignoredArguments) {
        RSyntaxNode syntaxFunction = functionNode.asRSyntaxNode();
        if (!(syntaxFunction instanceof RSyntaxLookup)) {
            // LHS is not a simple lookup -> bail out
            return null;
        }
        for (int i = 0; i < arguments.length; i++) {
            if (contains(ignoredArguments, i)) {
                continue;
            }
            if (!(arguments[i] instanceof RSyntaxLookup || arguments[i] instanceof RSyntaxConstant || arguments[i] instanceof RCallSpecialNode)) {
                // argument is not a simple lookup or constant value or another special -> bail out
                return null;
            }
        }
        String name = ((RSyntaxLookup) syntaxFunction).getIdentifier();
        RBuiltinDescriptor builtinDescriptor = RContext.lookupBuiltinDescriptor(name);
        if (builtinDescriptor == null) {
            // no builtin -> bail out
            return null;
        }
        RDispatch dispatch = builtinDescriptor.getDispatch();
        // it's ok to evaluate promises for args that would be forced by dispatch anyway
        int evaluatedArgs = dispatch == RDispatch.OPS_GROUP_GENERIC ? 2 : (dispatch == RDispatch.INTERNAL_GENERIC || dispatch.isGroupGeneric()) ? 1 : 0;
        RSpecialFactory specialCall = builtinDescriptor.getSpecialCall();
        if (specialCall == null) {
            // no special call definition -> bail out
            return null;
        }
        RNode[] localArguments = new RNode[arguments.length];
        for (int i = 0; i < arguments.length; i++) {
            RSyntaxNode arg = arguments[i];
            if (inReplace && contains(ignoredArguments, i)) {
                localArguments[i] = arg.asRNode();
            } else {
                if (arg instanceof RSyntaxLookup) {
                    String lookup = ((RSyntaxLookup) arg).getIdentifier();
                    if (ArgumentsSignature.VARARG_NAME.equals(lookup)) {
                        // cannot map varargs
                        return null;
                    }
                    if (i == 1 && builtinDescriptor.isFieldAccess()) {
                        localArguments[i] = RContext.getASTBuilder().constant(arg.getSourceSection(), lookup).asRNode();
                    } else if (i < evaluatedArgs) {
                        localArguments[i] = arg.asRNode();
                    } else {
                        localArguments[i] = new PeekLocalVariableNode(lookup);
                    }
                } else if (arg instanceof RSyntaxConstant) {
                    localArguments[i] = RContext.getASTBuilder().process(arg).asRNode();
                } else {
                    assert arg instanceof RCallSpecialNode;
                    if (i == 1 && builtinDescriptor.isFieldAccess()) {
                        return null;
                    }
                    localArguments[i] = arg.asRNode();
                }
                if (dispatch.isGroupGeneric() || dispatch == RDispatch.INTERNAL_GENERIC && i == 0) {
                    if (localArguments[i] instanceof RSyntaxConstant) {
                        Object value = ((RSyntaxConstant) localArguments[i]).getValue();
                        if (value instanceof RAttributable && ((RAttributable) value).getAttr(RRuntime.CLASS_ATTR_KEY) != null) {
                            return null;
                        }
                    } else {
                        localArguments[i] = ClassCheckNodeGen.create(localArguments[i]);
                    }
                }
            }
        }
        RNode special = specialCall.create(signature, localArguments, inReplace);
        if (special == null) {
            // the factory refused to create a special call -> bail out
            return null;
        }
        RBuiltinDescriptor expectedFunction = RContext.lookupBuiltinDescriptor(name);
        RInternalError.guarantee(expectedFunction != null);

        RCallSpecialNode callSpecial = new RCallSpecialNode(sourceSection, functionNode, expectedFunction, arguments, signature, special);
        for (int i = 0; i < arguments.length; i++) {
            if (!inReplace || !contains(ignoredArguments, i)) {
                if (arguments[i] instanceof RCallSpecialNode) {
                    ((RCallSpecialNode) arguments[i]).setCallSpecialParent(callSpecial);
                }
            }
        }
        return callSpecial;
    }

    private static boolean contains(int[] ignoredArguments, int index) {
        for (int i = 0; i < ignoredArguments.length; i++) {
            if (ignoredArguments[i] == index) {
                return true;
            }
        }
        return false;
    }

    @Override
    public Object execute(VirtualFrame frame, Object function) {
        try {
            if (!(function instanceof RFunction) || ((RFunction) function).getRBuiltin() != expectedFunction) {
                // the actual function differs from the expected function
                throw RSpecialFactory.throwFullCallNeeded();
            }
            return special.execute(frame);
        } catch (RSpecialFactory.FullCallNeededException e) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            if (propagateFullCallNeededException) {
                throw e;
            }
            RCallNode callNode = getRCallNode();
            for (RSyntaxElement arg : arguments) {
                if (arg instanceof RCallSpecialNode) {
                    ((RCallSpecialNode) arg).setCallSpecialParent(null);
                }
            }
            if (callSpecialParent != null) {
                RSyntaxNode[] args = callSpecialParent.arguments;
                for (int i = 0; i < args.length; i++) {
                    if (args[i] == this) {
                        args[i] = callNode;
                    }
                }
                throw e;
            }
            return replace(callNode).execute(frame, function);
        }
    }

    private RCallNode getRCallNode(RSyntaxNode[] newArguments) {
        return RCallNode.createCall(sourceSection, functionNode, signature, newArguments);
    }

    private RCallNode getRCallNode() {
        return getRCallNode(arguments);
    }

    /**
     * see {@link #propagateFullCallNeededException}.
     */
    public void setPropagateFullCallNeededException() {
        propagateFullCallNeededException = true;
    }

    /**
     * see {@link #callSpecialParent}.
     */
    private void setCallSpecialParent(RCallSpecialNode call) {
        callSpecialParent = call;
    }

    @Override
    public Object execute(VirtualFrame frame) {
        return execute(frame, functionNode.execute(frame));
    }

    @Override
    public Object visibleExecute(VirtualFrame frame) {
        Object result = execute(frame, functionNode.execute(frame));
        if (visibility == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            visibility = insert(SetVisibilityNode.create());
        }
        visibility.execute(frame, visible);
        return result;
    }

    @Override
    public RSyntaxElement getSyntaxLHS() {
        return functionNode == null ? RSyntaxLookup.createDummyLookup(RSyntaxNode.LAZY_DEPARSE, "FUN", true) : functionNode.asRSyntaxNode();
    }

    @Override
    public ArgumentsSignature getSyntaxSignature() {
        return signature == null ? ArgumentsSignature.empty(1) : signature;
    }

    @Override
    public RSyntaxElement[] getSyntaxArguments() {
        return arguments == null ? new RSyntaxElement[]{RSyntaxLookup.createDummyLookup(RSyntaxNode.LAZY_DEPARSE, "...", false)} : arguments;
    }
}
