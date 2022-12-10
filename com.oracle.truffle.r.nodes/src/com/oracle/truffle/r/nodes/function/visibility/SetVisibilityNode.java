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
package com.oracle.truffle.r.nodes.function.visibility;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.FrameSlotTypeException;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeCost;
import com.oracle.truffle.api.nodes.NodeInfo;
import com.oracle.truffle.r.runtime.RArguments;
import com.oracle.truffle.r.runtime.RCaller;
import com.oracle.truffle.r.runtime.RInternalError;
import com.oracle.truffle.r.runtime.RVisibility;
import com.oracle.truffle.r.runtime.env.frame.FrameIndex;
import com.oracle.truffle.r.runtime.env.frame.FrameSlotChangeMonitor;
import com.oracle.truffle.r.runtime.env.frame.RFrameSlot;

/**
 * See {@link RFrameSlot#Visibility}.
 */
@NodeInfo(cost = NodeCost.NONE)
public final class SetVisibilityNode extends Node {

    @CompilationFinal private int frameIndex = RFrameSlot.Visibility.getFrameIdx();

    private SetVisibilityNode() {
    }

    public static SetVisibilityNode create() {
        return new SetVisibilityNode();
    }

    private void ensureFrameIndex(Frame frame) {
        if (FrameIndex.isUninitializedIndex(frameIndex)) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            frameIndex = FrameSlotChangeMonitor.findOrAddAuxiliaryFrameSlot(frame.getFrameDescriptor(), RFrameSlot.Visibility);
        }
        assert FrameSlotChangeMonitor.containsIdentifier(frame.getFrameDescriptor(), RFrameSlot.Visibility);
    }

    public void execute(Frame frame, boolean value) {
        ensureFrameIndex(frame);
        FrameSlotChangeMonitor.setBoolean(frame, frameIndex, value);
    }

    public void execute(VirtualFrame frame, RVisibility visibility) {
        if (visibility == RVisibility.ON) {
            execute(frame, true);
        } else if (visibility == RVisibility.OFF) {
            execute(frame, false);
        }
    }

    /**
     * Needs to be called after each call site, so that the visibility is transferred from the
     * {@link RCaller} to the current frame.
     */
    public void executeAfterCall(VirtualFrame frame, RCaller caller) {
        ensureFrameIndex(frame);
        FrameSlotChangeMonitor.setBoolean(frame, frameIndex, caller.getVisibility());
    }

    /**
     * Needs to be called at the end of each function, so that the visibility is transferred from
     * the current frame into the {@link RCaller}.
     */
    public void executeEndOfFunction(VirtualFrame frame) {
        ensureFrameIndex(frame);
        try {
            if (frame.isBoolean(frameIndex)) {
                boolean visibility = FrameSlotChangeMonitor.getBoolean(frame, frameIndex);
                RArguments.getCall(frame).setVisibility(visibility);
            }
        } catch (FrameSlotTypeException e) {
            throw RInternalError.shouldNotReachHere(e);
        }
    }

    /**
     * Slow-path version of {@link #executeAfterCall(VirtualFrame, RCaller)}.
     */
    public static void executeAfterCallSlowPath(Frame frame, RCaller caller) {
        CompilerAsserts.neverPartOfCompilation();
        int frameIndex = getOrAddVisibilityFrameIndex(frame);
        FrameSlotChangeMonitor.setBoolean(frame, frameIndex, caller.getVisibility());
    }

    /**
     * Slow-path version of {@link #execute(Frame, boolean)}.
     */
    public static void executeSlowPath(Frame frame, boolean visibility) {
        CompilerAsserts.neverPartOfCompilation();
        int frameIndex = getOrAddVisibilityFrameIndex(frame);
        FrameSlotChangeMonitor.setBoolean(frame, frameIndex, visibility);
    }

    private static int getOrAddVisibilityFrameIndex(Frame frame) {
        int frameIndex = RFrameSlot.Visibility.getFrameIdx();
        if (FrameIndex.isUninitializedIndex(frameIndex)) {
            frameIndex = FrameSlotChangeMonitor.findOrAddAuxiliaryFrameSlot(frame.getFrameDescriptor(), RFrameSlot.Visibility);
        }
        assert FrameSlotChangeMonitor.containsIdentifier(frame.getFrameDescriptor(), RFrameSlot.Visibility);
        assert FrameIndex.isInitializedIndex(frameIndex);
        return frameIndex;
    }
}
