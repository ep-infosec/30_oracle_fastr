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

import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.InvalidAssumptionException;
import com.oracle.truffle.api.nodes.UnexpectedResultException;
import com.oracle.truffle.api.profiles.ConditionProfile;
import com.oracle.truffle.api.profiles.ValueProfile;
import com.oracle.truffle.r.nodes.function.PromiseHelperNode;
import com.oracle.truffle.r.nodes.function.call.RExplicitCallNode;
import com.oracle.truffle.r.runtime.RError;
import com.oracle.truffle.r.runtime.data.RArgsValuesAndNames;
import com.oracle.truffle.r.runtime.data.RMissing;
import com.oracle.truffle.r.runtime.data.RPromise;
import com.oracle.truffle.r.runtime.data.RTypesGen;
import com.oracle.truffle.r.runtime.env.frame.ActiveBinding;
import com.oracle.truffle.r.runtime.env.frame.FrameIndex;
import com.oracle.truffle.r.runtime.env.frame.FrameSlotChangeMonitor;

public final class LocalReadVariableNode extends ReadVariableNodeBase {

    @Child private PromiseHelperNode promiseHelper;
    @Child private RExplicitCallNode readActiveBinding;

    private final Object identifier;
    private final boolean forceResult;

    @CompilationFinal private ValueProfile valueProfile;
    @CompilationFinal private ConditionProfile isNullProfile;
    @CompilationFinal private ConditionProfile isMissingProfile;
    @CompilationFinal private ConditionProfile isPromiseProfile;

    @CompilationFinal private int frameIndex = FrameIndex.UNITIALIZED_INDEX;
    // null iff frameIndex is initialized. I.e. it is created only if the identifier is
    // not already in the frame.
    @CompilationFinal private Assumption notInFrameAssumption;
    @CompilationFinal private FrameDescriptor frameDescriptor;
    @CompilationFinal private Assumption containsNoActiveBindingAssumption;

    private final ValueProfile frameProfile = ValueProfile.createClassProfile();

    public static LocalReadVariableNode create(Object identifier, boolean forceResult) {
        return new LocalReadVariableNode(identifier, forceResult);
    }

    private LocalReadVariableNode(Object identifier, boolean forceResult) {
        assert identifier != null : "LocalReadVariableNode identifier is null";
        this.identifier = identifier;
        this.forceResult = forceResult;
    }

    public Object getIdentifier() {
        return identifier;
    }

    public Object execute(VirtualFrame frame) {
        return execute(frame, frame);
    }

    private void initializeFrameIndexAndAssumption() {
        frameIndex = FrameSlotChangeMonitor.getIndexOfIdentifier(frameDescriptor, identifier);
        notInFrameAssumption = FrameIndex.isUninitializedIndex(frameIndex) ? FrameSlotChangeMonitor.getNotInFrameAssumption(frameDescriptor, identifier) : null;
    }

    public Object execute(VirtualFrame frame, Frame variableFrame) {
        Frame profiledVariableFrame = frameProfile.profile(variableFrame);
        if (FrameIndex.isUninitializedIndex(frameIndex) && notInFrameAssumption == null || frameDescriptor != profiledVariableFrame.getFrameDescriptor()) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            if (identifier.toString().isEmpty()) {
                throw RError.error(RError.NO_CALLER, RError.Message.ZERO_LENGTH_VARIABLE);
            }
            frameDescriptor = profiledVariableFrame.getFrameDescriptor();
            initializeFrameIndexAndAssumption();
        }

        // check if the slot is missing / wrong type in current frame
        if (FrameIndex.isUninitializedIndex(frameIndex)) {
            assert notInFrameAssumption != null : "If frameIndex is unitialized, notInFrameAssumptions must not be null";
            try {
                notInFrameAssumption.check();
            } catch (InvalidAssumptionException e) {
                initializeFrameIndexAndAssumption();
            }
        }

        if (FrameIndex.isUninitializedIndex(frameIndex)) {
            return null;
        }

        if (isMissingProfile == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            valueProfile = ValueProfile.createClassProfile();
            isNullProfile = ConditionProfile.createBinaryProfile();
            isMissingProfile = ConditionProfile.createBinaryProfile();
        }
        Object result = valueProfile.profile(profiledGetValue(profiledVariableFrame, frameIndex));
        if (isNullProfile.profile(result == null) || isMissingProfile.profile(result == RMissing.instance)) {
            return null;
        }

        if (containsNoActiveBindingAssumption == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            containsNoActiveBindingAssumption = FrameSlotChangeMonitor.getContainsNoActiveBindingAssumption(profiledVariableFrame.getFrameDescriptor());
        }
        // special treatment for active binding: call bound function
        if (!containsNoActiveBindingAssumption.isValid() && ActiveBinding.isActiveBinding(result)) {
            if (readActiveBinding == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                readActiveBinding = insert(RExplicitCallNode.create());
            }
            ActiveBinding binding = (ActiveBinding) result;
            if (binding.isHidden() && !binding.isInitialized()) {
                return null;
            }
            Object readValue = readActiveBinding.call(frame, binding.getFunction(), RArgsValuesAndNames.EMPTY);
            if (readValue == RMissing.instance) {
                return null;
            }
            return readValue;
        }

        if (forceResult) {
            if (isPromiseProfile == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                isPromiseProfile = ConditionProfile.createBinaryProfile();
            }
            if (isPromiseProfile.profile(result instanceof RPromise)) {
                if (promiseHelper == null) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    promiseHelper = insert(new PromiseHelperNode());
                }
                result = promiseHelper.visibleEvaluate(frame, (RPromise) result);
            }
        }
        return result;
    }

    public int executeInteger(VirtualFrame frame) throws UnexpectedResultException {
        return RTypesGen.expectInteger(execute(frame));
    }

    public double executeDouble(VirtualFrame frame) throws UnexpectedResultException {
        return RTypesGen.expectDouble(execute(frame));
    }

    public byte executeByte(VirtualFrame frame) throws UnexpectedResultException {
        return RTypesGen.expectByte(execute(frame));
    }
}
