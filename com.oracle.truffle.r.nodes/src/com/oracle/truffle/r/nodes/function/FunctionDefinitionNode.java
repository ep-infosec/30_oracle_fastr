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
package com.oracle.truffle.r.nodes.function;

import java.util.ArrayList;
import java.util.List;

import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameSlotTypeException;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.nodes.NodeUtil;
import com.oracle.truffle.api.nodes.NodeUtil.NodeCountFilter;
import com.oracle.truffle.api.object.DynamicObject;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.api.profiles.ConditionProfile;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.r.nodes.InlineCacheNode;
import com.oracle.truffle.r.nodes.RASTBuilder;
import com.oracle.truffle.r.nodes.RASTUtils;
import com.oracle.truffle.r.nodes.RRootNode;
import com.oracle.truffle.r.nodes.access.FrameIndexNode;
import com.oracle.truffle.r.nodes.access.variables.ReadVariableNode;
import com.oracle.truffle.r.nodes.builtin.RBuiltinFactory;
import com.oracle.truffle.r.nodes.control.BreakException;
import com.oracle.truffle.r.nodes.control.NextException;
import com.oracle.truffle.r.nodes.function.visibility.SetVisibilityNode;
import com.oracle.truffle.r.runtime.ArgumentsSignature;
import com.oracle.truffle.r.runtime.DSLConfig;
import com.oracle.truffle.r.runtime.ExitException;
import com.oracle.truffle.r.runtime.JumpToTopLevelException;
import com.oracle.truffle.r.runtime.RArguments;
import com.oracle.truffle.r.runtime.RDeparse;
import com.oracle.truffle.r.runtime.RError;
import com.oracle.truffle.r.runtime.RErrorHandling;
import com.oracle.truffle.r.runtime.RInternalError;
import com.oracle.truffle.r.runtime.RType;
import com.oracle.truffle.r.runtime.ReturnException;
import com.oracle.truffle.r.runtime.RootBodyNode;
import com.oracle.truffle.r.runtime.Utils;
import com.oracle.truffle.r.runtime.Utils.DebugExitException;
import com.oracle.truffle.r.runtime.builtins.RBuiltinDescriptor;
import com.oracle.truffle.r.runtime.context.RContext;
import com.oracle.truffle.r.runtime.context.TruffleRLanguage;
import com.oracle.truffle.r.runtime.data.ClosureCache.RNodeClosureCache;
import com.oracle.truffle.r.runtime.data.RNull;
import com.oracle.truffle.r.runtime.data.RPairList;
import com.oracle.truffle.r.runtime.data.RPairList.PairListIterator;
import com.oracle.truffle.r.runtime.data.RPairListLibrary;
import com.oracle.truffle.r.runtime.env.frame.CannotOptimizePromise;
import com.oracle.truffle.r.runtime.env.frame.FrameIndex;
import com.oracle.truffle.r.runtime.env.frame.FrameSlotChangeMonitor;
import com.oracle.truffle.r.runtime.env.frame.RFrameSlot;
import com.oracle.truffle.r.runtime.interop.FastRInteropTryException;
import com.oracle.truffle.r.runtime.nodes.RCodeBuilder;
import com.oracle.truffle.r.runtime.nodes.RNode;
import com.oracle.truffle.r.runtime.nodes.RSyntaxCall;
import com.oracle.truffle.r.runtime.nodes.RSyntaxConstant;
import com.oracle.truffle.r.runtime.nodes.RSyntaxElement;
import com.oracle.truffle.r.runtime.nodes.RSyntaxFunction;
import com.oracle.truffle.r.runtime.nodes.RSyntaxLookup;
import com.oracle.truffle.r.runtime.nodes.RSyntaxNode;
import com.oracle.truffle.r.runtime.nodes.RSyntaxVisitor;
import com.oracle.truffle.r.runtime.parsermetadata.FunctionScope;

public final class FunctionDefinitionNode extends RRootNode implements RSyntaxNode, RSyntaxFunction {

    private final FormalArguments formalArguments;

    @Child private RootBodyNode body;

    @CompilationFinal private DynamicObject attributes;

    /**
     * This exists for debugging purposes. It is set initially when the function is defined to
     * either:
     * <ul>
     * <li>The name of the variable that the function definition is assigned to, e.g,
     * {@code f <- function}.
     * <li>The first several characters of the function definition for anonymous functions.
     * </ul>
     * It can be updated later by calling {@link #setName}, which is useful for functions lazily
     * loaded from packages, where at the point of definition any assignee variable is unknown.
     */
    private String name;
    private SourceSection sourceSectionR;
    private final SourceSection[] argSourceSections;

    private final RNodeClosureCache closureCache = new RNodeClosureCache();

    @Child private FrameIndexNode onExitSlotIndexNode;
    @Child private InlineCacheNode onExitExpressionCache;
    @Child private InteropLibrary exceptionInterop;
    @Child private RPairListLibrary exitSlotsPlLib;
    private final ConditionProfile onExitProfile = ConditionProfile.createBinaryProfile();
    private final BranchProfile resetArgs = BranchProfile.create();
    private final BranchProfile normalExit = BranchProfile.create();
    private final BranchProfile breakProfile = BranchProfile.create();
    private final BranchProfile nextProfile = BranchProfile.create();

    @Child private SetVisibilityNode visibility = SetVisibilityNode.create();

    @Child private PostProcessArgumentsNode argPostProcess;

    private final boolean needsSplitting;

    @CompilationFinal private boolean containsDispatch;

    private final Assumption noHandlerStackSlot = Truffle.getRuntime().createAssumption("noHandlerStackSlot");
    private final Assumption noRestartStackSlot = Truffle.getRuntime().createAssumption("noRestartStackSlot");
    @CompilationFinal private int handlerStackFrameIdx = FrameIndex.UNITIALIZED_INDEX;
    @CompilationFinal private int restartStackFrameIdx = FrameIndex.UNITIALIZED_INDEX;

    // Profiling for catching ReturnException thrown from an exit handler
    @CompilationFinal private ConditionProfile returnTopLevelProfile;

    public static FunctionDefinitionNode create(TruffleRLanguage language, SourceSection src, FrameDescriptor frameDesc, SourceSection[] argSourceSections, SaveArgumentsNode saveArguments,
                    RSyntaxNode body,
                    FormalArguments formals, String name, PostProcessArgumentsNode argPostProcess) {
        return new FunctionDefinitionNode(language, src, frameDesc, argSourceSections, saveArguments, body, formals, name, argPostProcess);
    }

    private FunctionDefinitionNode(TruffleRLanguage language, SourceSection src, FrameDescriptor frameDesc, SourceSection[] argSourceSections, SaveArgumentsNode saveArguments, RSyntaxNode body,
                    FormalArguments formals,
                    String name, PostProcessArgumentsNode argPostProcess) {
        super(language, frameDesc, RASTBuilder.createFunctionFastPath(body, formals.getSignature()));
        this.formalArguments = formals;
        this.argSourceSections = argSourceSections;
        assert FrameSlotChangeMonitor.assertValidFrameDescriptor(frameDesc);
        assert src != null;
        this.sourceSectionR = src;
        this.body = new FunctionBodyNode(saveArguments, body.asRNode());
        this.name = name;
        this.onExitSlotIndexNode = FrameIndexNode.createInitialized(frameDesc, RFrameSlot.OnExit, false);
        this.needsSplitting = needsAnyBuiltinSplitting();
        this.containsDispatch = containsAnyDispatch(body);
        this.argPostProcess = argPostProcess;
    }

    @Override
    public boolean isInternal() {
        return RSyntaxNode.isInternal(sourceSectionR);
    }

    @Override
    public FormalArguments getFormalArguments() {
        return formalArguments;
    }

    @Override
    public ArgumentsSignature getSignature() {
        return formalArguments.getSignature();
    }

    @Override
    public boolean isCaptureFramesForTrace() {
        return true;
    }

    @Override
    public RootCallTarget duplicateWithNewFrameDescriptor() {
        RCodeBuilder<RSyntaxNode> builder = RContext.getASTBuilder();

        List<RCodeBuilder.Argument<RSyntaxNode>> args = new ArrayList<>();
        for (int i = 0; i < getFormalArguments().getLength(); i++) {
            RNode value = getFormalArguments().getArgument(i);
            SourceSection source = argSourceSections == null ? getLazySourceSection() : argSourceSections[i];
            args.add(RCodeBuilder.argument(source, getFormalArguments().getSignature().getName(i), value == null ? null : builder.process(value.asRSyntaxNode())));
        }
        RootCallTarget callTarget = builder.rootFunction(getRLanguage(), getLazySourceSection(), args, builder.process(getBody()), name, FunctionScope.EMPTY_SCOPE);
        return callTarget;
    }

    public TruffleRLanguage getRLanguage() {
        // getLanguage deprecation: we cannot remove this now, because the new API does not work
        // with our unit tests
        return getLanguage(RContext.getTruffleRLanguage());
    }

    private static boolean containsAnyDispatch(RSyntaxNode body) {
        NodeCountFilter dispatchingMethodsFilter = node -> {
            if (node instanceof ReadVariableNode) {
                ReadVariableNode rvn = (ReadVariableNode) node;
                String identifier = rvn.getIdentifier();
                // TODO: can we also do this for S4 new?
                return rvn.getMode() == RType.Function && ("UseMethod".equals(identifier) || "standardGeneric".equals(identifier));
            }
            return false;
        };
        return NodeUtil.countNodes(body.asRNode(), dispatchingMethodsFilter) > 0;
    }

    @Override
    public boolean containsDispatch() {
        return containsDispatch;
    }

    @Override
    public void setContainsDispatch(boolean containsDispatch) {
        this.containsDispatch = containsDispatch;
    }

    private boolean needsAnyBuiltinSplitting() {
        RSyntaxVisitor<Boolean> visitor = new RSyntaxVisitor<>() {

            @Override
            protected Boolean visit(RSyntaxCall element) {
                RSyntaxElement lhs = element.getSyntaxLHS();
                RSyntaxElement[] arguments = element.getSyntaxArguments();
                if (lhs instanceof RSyntaxLookup) {
                    String function = ((RSyntaxLookup) lhs).getIdentifier();
                    /*
                     * TODO This is a hack to make sapply split lapply. We need to find better ways
                     * to do this. If a function uses lapply anywhere as name then it gets split.
                     * This could get exploited.
                     */
                    RBuiltinDescriptor directBuiltin = RContext.lookupBuiltinDescriptor(function);
                    if (directBuiltin != null && directBuiltin.isSplitCaller()) {
                        return true;
                    }
                }
                for (RSyntaxElement arg : arguments) {
                    if (arg != null && accept(arg)) {
                        return true;
                    }
                }
                return false;
            }

            @Override
            protected Boolean visit(RSyntaxConstant element) {
                return false;
            }

            @Override
            protected Boolean visit(RSyntaxLookup element) {
                return false;
            }

            @Override
            protected Boolean visit(RSyntaxFunction element) {
                return false;
            }
        };
        return visitor.accept(getSyntaxBody());
    }

    @Override
    public boolean needsSplitting() {
        return needsSplitting;
    }

    public RSyntaxNode getBody() {
        return body.getBody().asRSyntaxNode();
    }

    public PostProcessArgumentsNode getArgPostProcess() {
        return argPostProcess;
    }

    @Override
    public Object execute(VirtualFrame frame) {
        boolean runOnExitHandlers = true;
        try {
            verifyEnclosingAssumptions(frame);
            Object result = body.visibleExecute(frame);
            normalExit.enter();
            if (CompilerDirectives.inInterpreter() && result == null) {
                throw RInternalError.shouldNotReachHere("invalid null in result of " + this);
            }
            return result;
        } catch (ReturnException ex) {
            // here we just re-throw, the check whether this function is the target of the return is
            // done in the function body node
            throw ex;
        } catch (BreakException e) {
            breakProfile.enter();
            throw e;
        } catch (NextException e) {
            nextProfile.enter();
            throw e;
        } catch (RError e) {
            CompilerDirectives.transferToInterpreter();
            SetVisibilityNode.executeSlowPath(frame, false);
            if (onExitProfile.profile(onExitSlotIndexNode.hasValue(frame))) {
                Utils.writeStderr(e.getMessage(), true);
                e.setPrinted(true);
            }
            throw e;
        } catch (Throwable e) {
            CompilerDirectives.transferToInterpreter();
            if (e instanceof DebugExitException || e instanceof JumpToTopLevelException || e instanceof ExitException || e instanceof ThreadDeath) {
                /*
                 * These relate to debugging support and various other reasons for returning to the
                 * top level. exitHandlers must be suppressed and the exceptions must pass through
                 * unchanged; they are not errors
                 */
                runOnExitHandlers = false;
                throw e;
            } else if (e instanceof CannotOptimizePromise) {
                assert RArguments.getCall(frame).evaluateOnlyEagerPromises();
                runOnExitHandlers = false;
                throw e;
            } else if (e instanceof FastRInteropTryException) {
                assert !RArguments.getCall(frame).evaluateOnlyEagerPromises();
                throw e;
            } else if (getExceptionInterop().isException(e)) {
                try {
                    throw getExceptionInterop().throwException(e);
                } catch (UnsupportedMessageException internalEx) {
                    runOnExitHandlers = false;
                    throw CompilerDirectives.shouldNotReachHere();
                }
            } else {
                runOnExitHandlers = false;
                throw e instanceof RInternalError ? (RInternalError) e : new RInternalError(e, e.toString());
            }
        } finally {
            /*
             * Although a user function may throw an exception from an onExit handler, all
             * evaluations are wrapped in an anonymous function (see REngine.makeCallTarget) that
             * has no exit handlers (by fiat), so any exceptions from onExits handlers will be
             * caught above.
             */
            if (argPostProcess != null) {
                resetArgs.enter();
                argPostProcess.execute(frame);
            }
            if (runOnExitHandlers) {
                visibility.executeEndOfFunction(frame);
                boolean actualVisibility = RArguments.getCall(frame).getVisibility();

                if (!noHandlerStackSlot.isValid()) {
                    int handlerFrameIndex = getHandlerFrameIndex(frame);
                    if (FrameSlotChangeMonitor.isObject(frame, handlerFrameIndex)) {
                        try {
                            RErrorHandling.restoreHandlerStack(FrameSlotChangeMonitor.getObject(frame, handlerFrameIndex), RContext.getInstance(this));
                        } catch (FrameSlotTypeException e) {
                            throw RInternalError.shouldNotReachHere();
                        }
                    }
                }
                if (!noRestartStackSlot.isValid()) {
                    int restartFrameIndex = getRestartFrameIndex(frame);
                    if (FrameSlotChangeMonitor.isObject(frame, restartFrameIndex)) {
                        try {
                            RErrorHandling.restoreRestartStack(FrameSlotChangeMonitor.getObject(frame, restartFrameIndex), RContext.getInstance(this));
                        } catch (FrameSlotTypeException e) {
                            throw RInternalError.shouldNotReachHere();
                        }
                    }
                }

                if (onExitProfile.profile(onExitSlotIndexNode.hasValue(frame))) {
                    if (onExitExpressionCache == null) {
                        CompilerDirectives.transferToInterpreterAndInvalidate();
                        onExitExpressionCache = insert(InlineCacheNode.create(DSLConfig.getCacheSize(3)));
                    }
                    RPairList current = getCurrentOnExitList(frame, onExitSlotIndexNode.executeFrameIndex(frame));
                    /*
                     * We do not need to preserve visibility, since visibility.executeEndOfFunction
                     * was already called.
                     */
                    try {
                        RPairListLibrary plib = getExitSlotsPlLib();
                        PairListIterator it = current.iterator();
                        while (it.hasNext()) {
                            RPairList expr = it.next(plib);
                            if (expr.car() != RNull.instance) {
                                if (!(expr.car() instanceof RNode)) {
                                    CompilerDirectives.transferToInterpreter();
                                    RInternalError.shouldNotReachHere("unexpected type for on.exit entry: " + expr.car());
                                }
                                onExitExpressionCache.execute(frame, closureCache.getOrCreateLanguageClosure((RNode) expr.car()));
                            }
                        }
                    } catch (ReturnException ex) {
                        if (profileReturnToTopLevel(ex.getTarget() == RArguments.getCall(frame))) {
                            return ex.getResult();
                        } else {
                            throw ex;
                        }
                    }

                    // Restore visibility flag because an on.exit call may have changed it.
                    RArguments.getCall(frame).setVisibility(actualVisibility);
                }
            }
        }
    }

    public boolean profileReturnToTopLevel(boolean condition) {
        if (returnTopLevelProfile == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            returnTopLevelProfile = ConditionProfile.createBinaryProfile();
        }
        return returnTopLevelProfile.profile(condition);
    }

    private static RPairList getCurrentOnExitList(VirtualFrame frame, int frameIndex) {
        try {
            return (RPairList) FrameSlotChangeMonitor.getObject(frame, frameIndex);
        } catch (FrameSlotTypeException e) {
            throw RInternalError.shouldNotReachHere();
        }
    }

    @Override
    public String getName() {
        return name == null ? "<no source>" : name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @Override
    public RBuiltinFactory getBuiltin() {
        return null;
    }

    @Override
    public void setSourceSection(SourceSection sourceSection) {
        assert sourceSection != null;
        this.sourceSectionR = sourceSection;
    }

    @Override
    public SourceSection getLazySourceSection() {
        return sourceSectionR;
    }

    @Override
    public SourceSection getSourceSection() {
        RDeparse.ensureSourceSection(this);
        return sourceSectionR;
    }

    @Override
    public ArgumentsSignature getSyntaxSignature() {
        return getFormalArguments().getSignature();
    }

    @Override
    public RSyntaxElement[] getSyntaxArgumentDefaults() {
        return RASTUtils.asSyntaxNodes(getFormalArguments().getArguments());
    }

    @Override
    public RSyntaxElement getSyntaxBody() {
        return getBody();
    }

    @Override
    public String getSyntaxDebugName() {
        return name;
    }

    public int getRestartFrameIndex(VirtualFrame frame) {
        if (noRestartStackSlot.isValid()) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            noRestartStackSlot.invalidate();
        }
        if (FrameIndex.isUninitializedIndex(restartStackFrameIdx)) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            restartStackFrameIdx = FrameSlotChangeMonitor.findOrAddAuxiliaryFrameSlot(frame.getFrameDescriptor(), RFrameSlot.RestartStack);
        }
        return restartStackFrameIdx;
    }

    public int getHandlerFrameIndex(VirtualFrame frame) {
        if (noHandlerStackSlot.isValid()) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            noHandlerStackSlot.invalidate();
        }
        if (FrameIndex.isUninitializedIndex(handlerStackFrameIdx)) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            handlerStackFrameIdx = FrameSlotChangeMonitor.findOrAddAuxiliaryFrameSlot(frame.getFrameDescriptor(), RFrameSlot.HandlerStack);
        }
        return handlerStackFrameIdx;
    }

    public InteropLibrary getExceptionInterop() {
        if (exceptionInterop == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            exceptionInterop = insert(InteropLibrary.getFactory().createDispatched(3));
        }
        return exceptionInterop;
    }

    public RPairListLibrary getExitSlotsPlLib() {
        if (exitSlotsPlLib == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            exitSlotsPlLib = insert(RPairListLibrary.getFactory().createDispatched(1));
        }
        return exitSlotsPlLib;
    }

    @Override
    public void setAttributes(DynamicObject attributes) {
        this.attributes = attributes;
    }

    @Override
    public DynamicObject getAttributes() {
        return attributes;
    }

}
