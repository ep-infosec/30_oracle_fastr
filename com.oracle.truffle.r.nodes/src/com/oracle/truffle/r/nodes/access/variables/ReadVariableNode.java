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
package com.oracle.truffle.r.nodes.access.variables;

import static com.oracle.truffle.r.runtime.RLogger.LOGGER_COMPLEX_LOOKUPS;
import static com.oracle.truffle.r.runtime.context.FastROptions.PrintComplexLookups;

import java.util.ArrayList;
import java.util.logging.Level;

import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleLogger;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.TypeSystemReference;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameSlotTypeException;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.InvalidAssumptionException;
import com.oracle.truffle.api.nodes.SlowPathException;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.api.profiles.ConditionProfile;
import com.oracle.truffle.api.profiles.ValueProfile;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.r.nodes.function.PromiseHelperNode;
import com.oracle.truffle.r.nodes.function.call.RExplicitCallNode;
import com.oracle.truffle.r.nodes.function.visibility.SetVisibilityNode;
import com.oracle.truffle.r.runtime.ArgumentsSignature;
import com.oracle.truffle.r.runtime.RArguments;
import com.oracle.truffle.r.runtime.RError;
import com.oracle.truffle.r.runtime.RInternalError;
import com.oracle.truffle.r.runtime.RLogger;
import com.oracle.truffle.r.runtime.RRuntime;
import com.oracle.truffle.r.runtime.RType;
import com.oracle.truffle.r.runtime.StableValue;
import com.oracle.truffle.r.runtime.Utils;
import com.oracle.truffle.r.runtime.data.RArgsValuesAndNames;
import com.oracle.truffle.r.runtime.data.RBaseObject;
import com.oracle.truffle.r.runtime.data.RComplexVector;
import com.oracle.truffle.r.runtime.data.RDoubleVector;
import com.oracle.truffle.r.runtime.data.RFunction;
import com.oracle.truffle.r.runtime.data.RIntVector;
import com.oracle.truffle.r.runtime.data.RMissing;
import com.oracle.truffle.r.runtime.data.RNull;
import com.oracle.truffle.r.runtime.data.RPromise;
import com.oracle.truffle.r.runtime.data.RRawVector;
import com.oracle.truffle.r.runtime.data.RStringVector;
import com.oracle.truffle.r.runtime.data.RTypes;
import com.oracle.truffle.r.runtime.data.model.RAbstractVector;
import com.oracle.truffle.r.runtime.env.frame.ActiveBinding;
import com.oracle.truffle.r.runtime.env.frame.FrameIndex;
import com.oracle.truffle.r.runtime.env.frame.FrameSlotChangeMonitor;
import com.oracle.truffle.r.runtime.env.frame.FrameSlotChangeMonitor.FrameAndIndexLookupResult;
import com.oracle.truffle.r.runtime.env.frame.FrameSlotChangeMonitor.LookupResult;
import com.oracle.truffle.r.runtime.env.frame.FrameSlotChangeMonitor.MultiSlotData;
import com.oracle.truffle.r.runtime.nodes.RBaseNode;
import com.oracle.truffle.r.runtime.nodes.RNode;
import com.oracle.truffle.r.runtime.nodes.RSourceSectionNode;
import com.oracle.truffle.r.runtime.nodes.RSyntaxLookup;
import com.oracle.truffle.r.runtime.nodes.RSyntaxNode;

final class LookupNode extends RSourceSectionNode implements RSyntaxNode, RSyntaxLookup {

    @Child private ReadVariableNode read;
    @Child private SetVisibilityNode visibility;

    LookupNode(SourceSection sourceSection, ReadVariableNode read) {
        super(sourceSection);
        this.read = read;
    }

    @Override
    public void voidExecute(VirtualFrame frame) {
        read.executeInternal(frame, frame);
    }

    @Override
    public Object execute(VirtualFrame frame) {
        return read.executeInternal(frame, frame);
    }

    @Override
    public Object visibleExecute(VirtualFrame frame) {
        if (visibility == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            visibility = insert(SetVisibilityNode.create());
        }
        visibility.execute(frame, true);
        return read.executeInternal(frame, frame);
    }

    @Override
    public String getIdentifier() {
        return read.getIdentifier();
    }

    @Override
    public boolean isFunctionLookup() {
        return read.isFunctionLookup();
    }

    LookupNode copyAsSilenMissing() {
        return new LookupNode(getLazySourceSection(), new ReadVariableNode(read, true));
    }
}

/**
 * This node is used to read a variable from the local or enclosing environments. It specializes to
 * a particular layout of frame descriptors and enclosing environments, and re-specializes in case
 * the layout changes.
 */
public final class ReadVariableNode extends ReadVariableNodeBase {

    private static final int MAX_INVALIDATION_COUNT = 8;

    private enum ReadKind {
        Normal,
        // return null (instead of throwing an error) if not found
        Silent,
        // copy semantics
        Copying,
        // start the lookup in the enclosing frame
        Super,
        // whether a promise should be forced to check its type or not during lookup
        ForcedTypeCheck
    }

    public static ReadVariableNode create(String name) {
        return new ReadVariableNode(name, RType.Any, ReadKind.Normal);
    }

    public static ReadVariableNode create(String name, boolean shouldCopyValue) {
        return new ReadVariableNode(name, RType.Any, shouldCopyValue ? ReadKind.Copying : ReadKind.Normal);
    }

    public static ReadVariableNode createLocalVariableLookup(String name, int frameIndex) {
        assert FrameIndex.isInitializedIndex(frameIndex);
        return new ReadVariableNode(name, RType.Any, ReadKind.Normal, false, frameIndex);
    }

    public static ReadVariableNode createForcedLocalFunctionLookup(String name, int frameIndex) {
        assert FrameIndex.isInitializedIndex(frameIndex);
        return new ReadVariableNode(name, RType.Function, ReadKind.ForcedTypeCheck, false, frameIndex);
    }

    public static ReadVariableNode createSilent(String name, RType mode) {
        return new ReadVariableNode(name, mode, ReadKind.Silent);
    }

    public static ReadVariableNode createSuperLookup(String name) {
        return new ReadVariableNode(name, RType.Any, ReadKind.Super);
    }

    public static ReadVariableNode createFunctionLookup(String identifier) {
        return new ReadVariableNode(identifier, RType.Function, ReadKind.Normal);
    }

    public static ReadVariableNode createForcedFunctionLookup(String name) {
        return new ReadVariableNode(name, RType.Function, ReadKind.ForcedTypeCheck);
    }

    public static RSyntaxNode wrap(SourceSection sourceSection, ReadVariableNode read) {
        return new LookupNode(sourceSection, read);
    }

    public static RNode wrapAsSilentMissing(RNode node) {
        if (node instanceof LookupNode) {
            return wrapAsSilentMissing((LookupNode) node);
        }
        return null;
    }

    public static RNode wrapAsSilentMissing(RSyntaxNode node) {
        if (node instanceof LookupNode) {
            return wrapAsSilentMissing((LookupNode) node);
        }
        return null;
    }

    private static RNode wrapAsSilentMissing(LookupNode node) {
        return node.copyAsSilenMissing();
    }

    @Child private PromiseHelperNode promiseHelper;
    @Child private CheckTypeNode checkTypeNode;
    @Child private RExplicitCallNode readActiveBinding;

    @CompilationFinal private FrameLevel read;
    @CompilationFinal private boolean needsCopying;

    private final ConditionProfile isPromiseProfile = ConditionProfile.createBinaryProfile();
    private final ConditionProfile isActiveBindingProfile = ConditionProfile.createBinaryProfile();
    private final ConditionProfile copyProfile;
    private final BranchProfile unexpectedMissingProfile;
    private final ValueProfile superEnclosingFrameProfile;

    private final Object identifier;
    private final String identifierAsString;
    private final RType mode;
    private final ReadKind kind;
    private int invalidationCount;
    // whether reads of RMissing should not throw error and just proceed, this is the case for
    // inlined varargs, which should not show missing value error
    private final boolean silentMissing;

    ReadVariableNode(ReadVariableNode node, boolean silentMissing) {
        this(node.identifier, node.mode, node.kind, silentMissing, FrameIndex.UNITIALIZED_INDEX);
    }

    private ReadVariableNode(Object identifier, RType mode, ReadKind kind) {
        this(identifier, mode, kind, false, FrameIndex.UNITIALIZED_INDEX);
    }

    private ReadVariableNode(Object identifier, RType mode, ReadKind kind, boolean silentMissing, int frameIndex) {
        this.identifier = identifier;
        this.identifierAsString = Utils.intern(identifier.toString());
        this.mode = mode;
        this.kind = kind;
        this.silentMissing = silentMissing;
        unexpectedMissingProfile = silentMissing ? null : BranchProfile.create();
        superEnclosingFrameProfile = kind == ReadKind.Super ? ValueProfile.createClassProfile() : null;

        this.copyProfile = kind != ReadKind.Copying ? null : ConditionProfile.createBinaryProfile();
        // This happens when we are creating a ReadVariableNode for a local variable and we store
        // the local variable in a pre-defined indexed slot in the frame.
        if (FrameIndex.isInitializedIndex(frameIndex)) {
            this.read = new Match(frameIndex);
            this.needsCopying = false;
            if (mode != RType.Any) {
                this.checkTypeNode = insert(CheckTypeNodeGen.create(mode));
            }
        }
    }

    public String getIdentifier() {
        return identifierAsString;
    }

    public RType getMode() {
        return mode;
    }

    public Object execute(VirtualFrame frame) {
        return executeInternal(frame, frame);
    }

    public Object execute(VirtualFrame frame, Frame variableFrame) {
        assert frame != null;
        return executeInternal(frame, variableFrame);
    }

    Object executeInternal(VirtualFrame frame, Frame initialFrame) {
        Frame variableFrame = kind == ReadKind.Super ? superEnclosingFrameProfile.profile(RArguments.getEnclosingFrame(initialFrame)) : initialFrame;

        Object result;
        if (read == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            initializeRead(frame, variableFrame, false);
        }
        try {
            result = read.execute(frame, variableFrame);
        } catch (InvalidAssumptionException | LayoutChangedException | FrameSlotTypeException e) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            int iterations = 0;
            while (true) {
                iterations++;
                initializeRead(frame, variableFrame, true);
                try {
                    result = read.execute(frame, variableFrame);
                } catch (InvalidAssumptionException | LayoutChangedException | FrameSlotTypeException e2) {
                    if (iterations > 10) {
                        CompilerDirectives.transferToInterpreter();
                        throw new RInternalError("too many iterations during RVN initialization: " + identifier + " " + e2 + " " + read + " " + getRootNode());
                    }
                    continue;
                }
                break;
            }
        }
        if (needsCopying && copyProfile.profile(result instanceof RAbstractVector)) {
            return ((RAbstractVector) result).copy();
        }
        if (isPromiseProfile.profile(result instanceof RPromise)) {
            if (promiseHelper == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                promiseHelper = insert(new PromiseHelperNode());
            }
            return promiseHelper.visibleEvaluate(frame, (RPromise) result);
        }
        if (isActiveBindingProfile.profile(ActiveBinding.isActiveBinding(result))) {
            if (readActiveBinding == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                readActiveBinding = insert(RExplicitCallNode.create());
            }
            ActiveBinding binding = (ActiveBinding) result;
            if (binding.isHidden() && !binding.isInitialized()) {
                throw error(mode == RType.Function ? RError.Message.UNKNOWN_FUNCTION : RError.Message.UNKNOWN_OBJECT, identifier);
            }
            Object readValue = readActiveBinding.call(frame, binding.getFunction(), RArgsValuesAndNames.EMPTY);
            if (readValue == RMissing.instance) {
                throw error(mode == RType.Function ? RError.Message.UNKNOWN_FUNCTION : RError.Message.UNKNOWN_OBJECT, identifier);
            }
            return readValue;
        }
        return result;
    }

    private synchronized void initializeRead(VirtualFrame frame, Frame variableFrame, boolean invalidate) {
        CompilerAsserts.neverPartOfCompilation();
        // do nothing if another thread initialized in the meantime
        if (invalidate || read == null) {
            read = initialize(frame, variableFrame);
            needsCopying = kind == ReadKind.Copying && !(read instanceof Match || read instanceof DescriptorStableMatch);
        }
    }

    private static final class LayoutChangedException extends SlowPathException {
        private static final long serialVersionUID = 3380913774357492013L;
    }

    private abstract static class FrameLevel {

        public abstract Object execute(VirtualFrame frame, Frame variableFrame) throws InvalidAssumptionException, LayoutChangedException, FrameSlotTypeException;
    }

    private abstract static class DescriptorLevel extends FrameLevel {

        @Override
        public Object execute(VirtualFrame frame, Frame variableFrame) throws InvalidAssumptionException, LayoutChangedException, FrameSlotTypeException {
            return execute(frame);
        }

        public abstract Object execute(VirtualFrame frame) throws InvalidAssumptionException, LayoutChangedException, FrameSlotTypeException;

    }

    private final class Mismatch extends FrameLevel {

        private final FrameLevel next;
        private final int frameIndex;
        private final ConditionProfile isNullProfile = ConditionProfile.createBinaryProfile();
        private final ValueProfile frameProfile = ValueProfile.createClassProfile();

        private Mismatch(FrameLevel next, int frameIndex) {
            this.next = next;
            this.frameIndex = frameIndex;
        }

        @Override
        public Object execute(VirtualFrame frame, Frame variableFrame) throws InvalidAssumptionException, LayoutChangedException, FrameSlotTypeException {
            Frame profiledVariableFrame = frameProfile.profile(variableFrame);
            Object value = profiledGetValue(profiledVariableFrame, frameIndex);
            if (checkType(frame, value, isNullProfile)) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw new LayoutChangedException();
            }
            return next.execute(frame, profiledVariableFrame);
        }

        @Override
        public String toString() {
            return "M" + next;
        }
    }

    private static final class DescriptorStableMatch extends DescriptorLevel {

        private final Assumption assumption;
        private final Object value;

        private DescriptorStableMatch(Assumption assumption, Object value) {
            this.assumption = assumption;
            this.value = value;
        }

        @Override
        public Object execute(VirtualFrame frame) throws InvalidAssumptionException {
            assumption.check();
            return value;
        }

        @Override
        public String toString() {
            return "f";
        }
    }

    private final class Match extends FrameLevel {

        private final int frameIndex;
        private final ConditionProfile isNullProfile = ConditionProfile.createBinaryProfile();
        private final ValueProfile frameProfile = ValueProfile.createClassProfile();
        private final ValueProfile valueProfile = ValueProfile.createClassProfile();

        private Match(int frameIndex) {
            this.frameIndex = frameIndex;
        }

        @Override
        public Object execute(VirtualFrame frame, Frame variableFrame) throws LayoutChangedException, FrameSlotTypeException {
            Object value = valueProfile.profile(profiledGetValue(frameProfile.profile(variableFrame), frameIndex));
            if (!checkType(frame, value, isNullProfile)) {
                throw new LayoutChangedException();
            }
            return value;
        }

        @Override
        public String toString() {
            return "F";
        }
    }

    private final class Unknown extends DescriptorLevel {

        @Override
        public Object execute(VirtualFrame frame) {
            if (kind == ReadKind.Silent) {
                return null;
            } else {
                throw RError.error(RError.SHOW_CALLER, mode == RType.Function ? RError.Message.UNKNOWN_FUNCTION : RError.Message.UNKNOWN_OBJECT, identifier);
            }
        }

        @Override
        public String toString() {
            return "U";
        }
    }

    private static final class SingletonFrameLevel extends DescriptorLevel {

        private final FrameLevel next;
        private final MaterializedFrame singletonFrame;

        private SingletonFrameLevel(FrameLevel next, MaterializedFrame singletonFrame) {
            this.next = next;
            this.singletonFrame = singletonFrame;
        }

        @Override
        public Object execute(VirtualFrame frame) throws InvalidAssumptionException, LayoutChangedException, FrameSlotTypeException {
            return next.execute(frame, singletonFrame);
        }

        @Override
        public String toString() {
            return "~>" + next;
        }
    }

    private static final class NextFrameLevel extends FrameLevel {

        private final FrameLevel next;
        private final FrameDescriptor nextDescriptor;
        private final ValueProfile frameProfile = ValueProfile.createClassProfile();

        private NextFrameLevel(FrameLevel next, FrameDescriptor nextDescriptor) {
            this.next = next;
            this.nextDescriptor = nextDescriptor;
        }

        @Override
        public Object execute(VirtualFrame frame, Frame variableFrame) throws InvalidAssumptionException, LayoutChangedException, FrameSlotTypeException {
            MaterializedFrame nextFrame = frameProfile.profile(RArguments.getEnclosingFrame(variableFrame));
            if (nextDescriptor == null) {
                if (nextFrame != null) {
                    throw new LayoutChangedException();
                }
                return next.execute(frame, null);
            } else {
                if (nextFrame == null) {
                    throw new LayoutChangedException();
                }
                if (nextFrame.getFrameDescriptor() != nextDescriptor) {
                    throw new LayoutChangedException();
                }
                return next.execute(frame, nextFrame);
            }
        }

        @Override
        public String toString() {
            return "=>" + next;
        }
    }

    private static final class MultiAssumptionLevel extends FrameLevel {

        private final FrameLevel next;
        @CompilationFinal(dimensions = 1) private final Assumption[] assumptions;

        private MultiAssumptionLevel(FrameLevel next, Assumption... assumptions) {
            this.next = next;
            this.assumptions = assumptions;
        }

        @Override
        @ExplodeLoop
        public Object execute(VirtualFrame frame, Frame variableFrame) throws InvalidAssumptionException, LayoutChangedException, FrameSlotTypeException {
            for (Assumption assumption : assumptions) {
                assumption.check();
            }
            return next.execute(frame, variableFrame);
        }

        @Override
        public String toString() {
            return "-" + assumptions.length + ">" + next;
        }
    }

    private final class Polymorphic extends FrameLevel {

        private final ConditionProfile isNullProfile = ConditionProfile.createBinaryProfile();
        @CompilationFinal private int frameIndex;
        @CompilationFinal private Assumption notInFrameAssumption;

        private Polymorphic(Frame variableFrame) {
            initializeFrameIndexAndAssumption(variableFrame.getFrameDescriptor());
        }

        @Override
        public Object execute(VirtualFrame frame, Frame variableFrame) throws LayoutChangedException, FrameSlotTypeException {
            // check if the slot is missing / wrong type in current frame
            if (FrameIndex.isUninitializedIndex(frameIndex)) {
                try {
                    notInFrameAssumption.check();
                } catch (InvalidAssumptionException e) {
                    initializeFrameIndexAndAssumption(variableFrame.getFrameDescriptor());
                }
            }

            if (FrameIndex.isInitializedIndex(frameIndex)) {
                Object value = FrameSlotChangeMonitor.getObject(variableFrame, frameIndex);
                if (checkType(frame, value, isNullProfile)) {
                    return value;
                }
            }
            // search enclosing frames if necessary
            MaterializedFrame current = RArguments.getEnclosingFrame(variableFrame);
            while (current != null) {
                Object value = getValue(current);
                if (checkType(frame, value, isNullProfile)) {
                    return value;
                }
                current = RArguments.getEnclosingFrame(current);
            }
            if (kind == ReadKind.Silent) {
                return null;
            } else {
                throw RError.error(RError.SHOW_CALLER, mode == RType.Function ? RError.Message.UNKNOWN_FUNCTION : RError.Message.UNKNOWN_OBJECT, identifier);
            }
        }

        private void initializeFrameIndexAndAssumption(FrameDescriptor variableFrameDescriptor) {
            frameIndex = FrameSlotChangeMonitor.getIndexOfIdentifier(variableFrameDescriptor, identifier);
            notInFrameAssumption = FrameIndex.isUninitializedIndex(frameIndex) ? FrameSlotChangeMonitor.getNotInFrameAssumption(variableFrameDescriptor, identifier) : null;
        }

        @TruffleBoundary
        private Object getValue(MaterializedFrame current) {
            int identifierFrameIndex = FrameSlotChangeMonitor.getIndexOfIdentifier(current.getFrameDescriptor(), identifier);
            if (FrameIndex.isUninitializedIndex(identifierFrameIndex)) {
                return null;
            } else {
                return FrameSlotChangeMonitor.getValue(current, identifierFrameIndex);
            }
        }

        @Override
        public String toString() {
            return "P";
        }
    }

    private final class LookupLevel extends DescriptorLevel {

        private final LookupResult lookup;
        private final ConditionProfile nullValueProfile = kind == ReadKind.Silent ? null : ConditionProfile.createBinaryProfile();

        private LookupLevel(LookupResult lookup) {
            this.lookup = lookup;
            assert !(lookup instanceof FrameAndIndexLookupResult);
        }

        @Override
        public Object execute(VirtualFrame frame) throws InvalidAssumptionException, LayoutChangedException, FrameSlotTypeException {
            Object value = lookup.getValue();
            if (kind != ReadKind.Silent && nullValueProfile.profile(value == null)) {
                throw RError.error(RError.SHOW_CALLER, mode == RType.Function ? RError.Message.UNKNOWN_FUNCTION : RError.Message.UNKNOWN_OBJECT, identifier);
            }
            return value;
        }

        @Override
        public String toString() {
            return "<LU>";
        }
    }

    private final class FrameAndSlotLookupLevel extends DescriptorLevel {

        private final FrameAndIndexLookupResult lookup;
        private final ValueProfile frameProfile = ValueProfile.createClassProfile();
        private final ConditionProfile isNullProfile = ConditionProfile.createBinaryProfile();

        private FrameAndSlotLookupLevel(FrameAndIndexLookupResult lookup) {
            this.lookup = lookup;
        }

        @Override
        public Object execute(VirtualFrame frame) throws InvalidAssumptionException, LayoutChangedException, FrameSlotTypeException {
            Object value = profiledGetValue(frameProfile.profile(lookup.getFrame()), lookup.getFrameIndex());
            if (!checkType(frame, value, isNullProfile)) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw new LayoutChangedException();
            }
            return value;
        }

        @Override
        public String toString() {
            return "<FSLU>";
        }
    }

    private FrameLevel initialize(VirtualFrame frame, Frame variableFrame) {
        if (identifierAsString.isEmpty()) {
            throw RError.error(RError.NO_CALLER, RError.Message.ZERO_LENGTH_VARIABLE);
        }

        /*
         * Check whether we need to go to the polymorphic case, which will not rely on any frame
         * descriptor assumptions (apart from the first frame).
         */
        if (++invalidationCount > MAX_INVALIDATION_COUNT) {
            RError.performanceWarning("polymorphic (slow path) lookup of symbol \"" + identifier + "\"");
            return new Polymorphic(variableFrame);
        }

        /*
         * Check whether we can fulfill the lookup by only looking at the current frame, and thus
         * without additional dependencies on frame descriptor layouts.
         */
        int localFrameIndex = FrameSlotChangeMonitor.getIndexOfIdentifier(variableFrame.getFrameDescriptor(), identifier);
        // non-local reads can only be handled in a simple way if they are successful
        if (FrameIndex.isInitializedIndex(localFrameIndex)) {
            Object val = getValue(variableFrame, localFrameIndex);
            if (checkTypeSlowPath(frame, val)) {
                if (val instanceof MultiSlotData) {
                    RError.performanceWarning("polymorphic (slow path) lookup of symbol \"" + identifier + "\" from a multi slot");
                    return new Polymorphic(variableFrame);
                } else {
                    return new Match(localFrameIndex);
                }
            }
        }

        /*
         * Check whether the frame descriptor information available in FrameSlotChangeMonitor is
         * enough to handle this lookup. This has the advantage of not depending on a specific
         * "enclosing frame descriptor" chain, so that attaching/detaching environments does not
         * necessarily invalidate lookups.
         */
        LookupResult lookup = FrameSlotChangeMonitor.lookup(variableFrame, identifier);
        if (lookup != null) {
            try {
                Object value = lookup.getValue();
                if (value instanceof RPromise) {
                    evalPromiseSlowPathWithName(identifierAsString, frame, (RPromise) value);
                }
                if (lookup instanceof FrameAndIndexLookupResult) {
                    if (checkTypeSlowPath(frame, value)) {
                        return new FrameAndSlotLookupLevel((FrameAndIndexLookupResult) lookup);
                    }
                } else {
                    if (value == null || checkTypeSlowPath(frame, value)) {
                        return new LookupLevel(lookup);
                    }
                }
            } catch (InvalidAssumptionException e) {
                // immediately invalidated...
            }
        }

        /*
         * If everything else fails: build the lookup from scratch, by recursively building
         * assumptions and checks.
         */
        ArrayList<Assumption> assumptions = new ArrayList<>();
        FrameLevel lastLevel = createLevels(frame, variableFrame, assumptions);
        if (!assumptions.isEmpty()) {
            lastLevel = new MultiAssumptionLevel(lastLevel, assumptions.toArray(new Assumption[0]));
        }

        if (getRContext().getOption(PrintComplexLookups)) {
            System.out.println("WARNING: The PrintComplexLookups option was discontinued.\n" +
                            "You can rerun FastR with --log.R.com.oracle.truffle.r.complexLookups.level=FINE");
        }
        TruffleLogger logger = RLogger.getLogger(LOGGER_COMPLEX_LOOKUPS);
        if (logger.isLoggable(Level.FINE)) {
            logger.log(Level.FINE, "{0} {1}", new Object[]{identifier, lastLevel});
        }
        return lastLevel;
    }

    /**
     * This function returns a "recipe" to find the value of this lookup, starting at varibleFrame.
     * It may record assumptions into the given ArrayList of assumptions. The result will be a
     * linked list of {@link FrameLevel} instances.
     */
    private FrameLevel createLevels(VirtualFrame frame, Frame variableFrame, ArrayList<Assumption> assumptions) {
        if (variableFrame == null) {
            // this means that we've arrived at the empty env during lookup
            return new Unknown();
        }
        // see if the current frame has a value of the given name
        FrameDescriptor variableFrameDescriptor = variableFrame.getFrameDescriptor();
        int frameIndex = FrameSlotChangeMonitor.getIndexOfIdentifier(variableFrameDescriptor, identifier);
        if (FrameIndex.isInitializedIndex(frameIndex)) {
            Object value = getValue(variableFrame, frameIndex);
            if (checkTypeSlowPath(frame, value)) {
                StableValue<Object> valueAssumption = FrameSlotChangeMonitor.getStableValueAssumption(variableFrame, frameIndex, value);
                if (valueAssumption != null) {
                    Assumption assumption = valueAssumption.getAssumption();
                    assert value == valueAssumption.getValue() || value.equals(valueAssumption.getValue()) : value + " vs. " + valueAssumption.getValue();
                    if (value instanceof RPromise) {
                        RPromise promise = (RPromise) value;
                        Object promiseValue = PromiseHelperNode.evaluateSlowPath(frame, promise);
                        if (promiseValue instanceof RFunction) {
                            value = promiseValue;
                        }
                    }
                    return new DescriptorStableMatch(assumption, value);
                } else {
                    return new Match(frameIndex);
                }
            }
        }

        // the identifier wasn't found in the current frame: try the next one
        MaterializedFrame next = RArguments.getEnclosingFrame(variableFrame);
        FrameLevel lastLevel = createLevels(frame, next, assumptions);

        /*
         * Here we look at the type of the recursive lookup result, to see if we need only a
         * specific FrameDescriptor (DescriptorLevel) or the actual frame (FrameLevel).
         */

        if (!(lastLevel instanceof DescriptorLevel)) {
            MaterializedFrame singleton = FrameSlotChangeMonitor.getSingletonFrame(next.getFrameDescriptor());
            if (singleton != null) {
                // use singleton frames to get from a frame descriptor to an actual frame
                lastLevel = new SingletonFrameLevel(lastLevel, singleton);
            }
        }

        Assumption enclosingDescriptorAssumption = FrameSlotChangeMonitor.getEnclosingFrameDescriptorAssumption(variableFrameDescriptor);
        if (lastLevel instanceof DescriptorLevel && enclosingDescriptorAssumption != null) {
            assumptions.add(enclosingDescriptorAssumption);
        } else {
            lastLevel = new NextFrameLevel(lastLevel, next == null ? null : next.getFrameDescriptor());
        }

        if (FrameIndex.isUninitializedIndex(frameIndex)) {
            var notInFrameAssumption = FrameSlotChangeMonitor.getNotInFrameAssumption(variableFrameDescriptor, identifier);
            assumptions.add(notInFrameAssumption);
        } else {
            StableValue<Object> valueAssumption = FrameSlotChangeMonitor.getStableValueAssumption(variableFrame, frameIndex, getValue(variableFrame, frameIndex));
            if (valueAssumption != null && lastLevel instanceof DescriptorLevel) {
                assumptions.add(valueAssumption.getAssumption());
            } else {
                lastLevel = new Mismatch(lastLevel, frameIndex);
            }
        }
        return lastLevel;
    }

    public static RFunction lookupFunction(String identifier, MaterializedFrame variableFrame) {
        return lookupFunction(identifier, variableFrame, false, true);
    }

    @TruffleBoundary
    public static RFunction lookupFunction(String identifier, MaterializedFrame variableFrame, boolean localOnly, boolean forcePromises) {
        Frame current = variableFrame;
        do {
            // see if the current frame has a value of the given name
            int frameIndex = FrameSlotChangeMonitor.getIndexOfIdentifier(current.getFrameDescriptor(), identifier);
            if (FrameIndex.isInitializedIndex(frameIndex)) {
                Object value = FrameSlotChangeMonitor.getValue(current, frameIndex);

                if (value != null) {
                    if (value == RMissing.instance) {
                        throw RError.error(RError.SHOW_CALLER, RError.Message.ARGUMENT_MISSING, identifier);
                    }
                    if (value instanceof RPromise) {
                        RPromise promise = (RPromise) value;
                        if (promise.isEvaluated()) {
                            value = promise.getValue();
                        } else {
                            if (forcePromises) {
                                value = evalPromiseSlowPathWithName(identifier, null, promise);
                                if (RRuntime.checkType(value, RType.Function)) {
                                    return (RFunction) value;
                                }
                            }
                            return null;
                        }
                    }
                    if (RRuntime.checkType(value, RType.Function)) {
                        return (RFunction) value;
                    }
                }
            }
            if (localOnly) {
                return null;
            }
            current = RArguments.getEnclosingFrame(current);
        } while (current != null);
        return null;
    }

    @TruffleBoundary
    public static Object lookupAny(String identifier, MaterializedFrame variableFrame, boolean localOnly) {
        Frame current = variableFrame;
        do {
            // see if the current frame has a value of the given name
            int frameIndex = FrameSlotChangeMonitor.getIndexOfIdentifier(current.getFrameDescriptor(), identifier);
            if (FrameIndex.isInitializedIndex(frameIndex)) {
                Object value = FrameSlotChangeMonitor.getValue(current, frameIndex);

                if (value != null) {
                    if (value == RMissing.instance) {
                        throw RError.error(RError.SHOW_CALLER, RError.Message.ARGUMENT_MISSING, identifier);
                    }
                    if (value instanceof RPromise) {
                        return PromiseHelperNode.evaluateSlowPath((RPromise) value);
                    }
                    return value;
                }
            }
            if (localOnly) {
                return null;
            }
            current = RArguments.getEnclosingFrame(current);
        } while (current != null);
        return null;
    }

    @TruffleBoundary
    public static RArgsValuesAndNames lookupVarArgs(MaterializedFrame variableFrame) {
        Frame current = variableFrame;
        do {
            // see if the current frame has a value of the given name
            int frameIndex = FrameSlotChangeMonitor.getIndexOfIdentifier(current.getFrameDescriptor(), ArgumentsSignature.VARARG_NAME);
            if (FrameIndex.isInitializedIndex(frameIndex)) {
                Object value = FrameSlotChangeMonitor.getValue(current, frameIndex);

                if (value != null) {
                    if (value == RNull.instance) {
                        return RArgsValuesAndNames.EMPTY;
                    } else if (value instanceof RArgsValuesAndNames) {
                        return (RArgsValuesAndNames) value;
                    } else {
                        return null;
                    }
                }
            }
            current = RArguments.getEnclosingFrame(current);
        } while (current != null);
        return null;
    }

    /**
     * This method checks the value a RVN just read. It is used to determine whether the value just
     * read matches the expected type or if we have to look in a frame up the lexical chain. It
     * might:
     * <ul>
     * <li>throw an {@link RError}: if 'objArg' is a missing argument and this is not allowed</li>
     * <li>return {@code true}: if the type of 'objArg' matches the description in 'type'</li>
     * <li>return {@code false}: if the type of 'objArg' does not match the description in 'type'
     * </li>
     * </ul>
     * However, there is the special case of 'objArg' being a {@link RPromise}: Normally, it is
     * expected to match type and simply returns {@code true}. But in case of 'forcePromise' ==
     * {@code true}, the promise is evaluated and the result checked for it's type. This is only
     * used for function lookup, as we need to be sure that we read a function.
     *
     * @param frame The frame to (eventually) evaluate the {@link RPromise} in
     * @param objArg The object to check for proper type
     * @return see above
     */
    private boolean checkType(VirtualFrame frame, Object objArg, ConditionProfile isNullProfile) {
        Object obj = objArg;
        if ((isNullProfile == null && obj == null) || (isNullProfile != null && isNullProfile.profile(obj == null))) {
            return false;
        }
        if (!silentMissing && obj == RMissing.instance) {
            unexpectedMissingProfile.enter();
            throw RError.error(RError.SHOW_CALLER, RError.Message.ARGUMENT_MISSING, getIdentifier());
        }
        if (mode == RType.Any) {
            return true;
        }
        if (isPromiseProfile.profile(obj instanceof RPromise)) {
            RPromise promise = (RPromise) obj;
            if (promiseHelper == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                promiseHelper = insert(new PromiseHelperNode());
            }
            if (!promiseHelper.isEvaluated(promise)) {
                if (kind != ReadKind.ForcedTypeCheck) {
                    // since we do not know what type the evaluates to, it may match.
                    // we recover from a wrong type later
                    return true;
                } else {
                    obj = promiseHelper.evaluate(frame, promise);
                }
            } else {
                obj = promise.getValue();
            }
        }
        if (checkTypeNode == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            checkTypeNode = insert(CheckTypeNodeGen.create(mode));
        }
        return checkTypeNode.executeBoolean(obj);
    }

    private static final ThreadLocal<String> slowPathEvaluationName = new ThreadLocal<>();

    private boolean checkTypeSlowPath(VirtualFrame frame, Object objArg) {
        CompilerAsserts.neverPartOfCompilation();
        Object obj = objArg;
        if (obj == null) {
            return false;
        }
        if (!silentMissing && obj == RMissing.instance) {
            throw RError.error(RError.SHOW_CALLER, RError.Message.ARGUMENT_MISSING, getIdentifier());
        }
        if (mode == RType.Any) {
            return true;
        }
        if (obj instanceof RPromise) {
            RPromise promise = (RPromise) obj;

            if (!promise.isEvaluated()) {
                if (kind != ReadKind.ForcedTypeCheck) {
                    // since we do not know what type the evaluates to, it may match.
                    // we recover from a wrong type later
                    return true;
                } else {
                    obj = evalPromiseSlowPathWithName(identifierAsString, frame, promise);
                }
            } else {
                obj = promise.getValue();
            }
        }
        return RRuntime.checkType(obj, mode);
    }

    public static Object evalPromiseSlowPathWithName(String identifier, VirtualFrame frame, RPromise promise) {
        Object obj;
        slowPathEvaluationName.set(identifier);
        try {
            obj = PromiseHelperNode.evaluateSlowPath(frame, promise);
        } finally {
            slowPathEvaluationName.set(null);
        }
        return obj;
    }

    public static String getSlowPathEvaluationName() {
        return slowPathEvaluationName.get();
    }

    public boolean isForceForTypeCheck() {
        return kind == ReadKind.ForcedTypeCheck;
    }

    public boolean isFunctionLookup() {
        return mode == RType.Function;
    }
}

/*
 * This is RRuntime.checkType in the node form.
 */
@TypeSystemReference(RTypes.class)
abstract class CheckTypeNode extends RBaseNode {

    public abstract boolean executeBoolean(Object o);

    private final RType type;

    CheckTypeNode(RType type) {
        assert type != RType.Any;
        this.type = type;
    }

    @Specialization
    boolean checkType(@SuppressWarnings("unused") RIntVector o) {
        return type == RType.Integer || type == RType.Double;
    }

    @Specialization
    boolean checkType(@SuppressWarnings("unused") RDoubleVector o) {
        return type == RType.Integer || type == RType.Double;
    }

    @Specialization
    boolean checkType(@SuppressWarnings("unused") RRawVector o) {
        return type == RType.Logical;
    }

    @Specialization
    boolean checkType(@SuppressWarnings("unused") RStringVector o) {
        return type == RType.Character;
    }

    @Specialization
    boolean checkType(@SuppressWarnings("unused") RComplexVector o) {
        return type == RType.Complex;
    }

    @Specialization
    boolean checkType(@SuppressWarnings("unused") RFunction o) {
        return type == RType.Function || type == RType.Closure || type == RType.Builtin || type == RType.Special;
    }

    @Specialization(guards = "isExternalObject(o)")
    boolean checkType(@SuppressWarnings("unused") TruffleObject o) {
        return type == RType.Function || type == RType.Closure || type == RType.Builtin || type == RType.Special;
    }

    protected static boolean isExternalObject(TruffleObject o) {
        return !(o instanceof RBaseObject);
    }

    @Fallback
    boolean checkType(@SuppressWarnings("unused") Object o) {
        return false;
    }
}
