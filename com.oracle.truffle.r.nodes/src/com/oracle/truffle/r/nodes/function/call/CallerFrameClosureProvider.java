/*
 * Copyright (c) 2017, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.r.nodes.function.call;

import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.ConditionProfile;
import com.oracle.truffle.r.runtime.CallerFrameClosure;

/**
 * There are some situations in R where we need to access the frame of other than the current
 * function, for this we need to materialize such frames and pass it in the arguments array or use
 * {@code TruffleRuntime.iterateFrames(FrameInstanceVisitor)}, which causes the inspected functions
 * to be deoptimized.
 *
 * In order to avoid to unnecessarily materialize the current function frame, we only pass
 * {@link CallerFrameClosure} to the callee. If the callee needs to access the caller frame, it asks
 * the {@link CallerFrameClosure}, where we invalidate the assumption that we do not have to
 * materialize the current frame and pass it to the callee.
 */
public abstract class CallerFrameClosureProvider extends Node {

    protected final Assumption needsNoCallerFrame = Truffle.getRuntime().createAssumption("no caller frame");
    protected final CallerFrameClosure invalidateNoCallerFrame = new InvalidateNoCallerFrame(needsNoCallerFrame);
    @CompilationFinal private ConditionProfile topLevelProfile = ConditionProfile.createBinaryProfile();
    private static final CallerFrameClosure DUMMY = new DummyCallerFrameClosure();

    public boolean setNeedsCallerFrame() {
        boolean value = !needsNoCallerFrame.isValid();
        needsNoCallerFrame.invalidate();
        return value;
    }

    private Object getCallerFrameClosure(MaterializedFrame callerFrame) {
        if (CompilerDirectives.inInterpreter()) {
            return new InvalidateNoCallerFrame(needsNoCallerFrame, callerFrame);
        }
        return invalidateNoCallerFrame;
    }

    private Object getCallerFrameClosure(VirtualFrame callerFrame) {
        if (CompilerDirectives.inInterpreter()) {
            // In the interpreter we always pass the caller frame to the callee via the
            // CallerFrameClosure. The callee may still invalidate needsNoCallerFrame assumption,
            // but it should always get direct access to the caller frame.
            return new InvalidateNoCallerFrame(needsNoCallerFrame, callerFrame != null ? callerFrame.materialize() : null);
        }
        // In the compiler, we want to avoid materializing the caller frame, if it has not been
        // needed so far, we only pass the CallerFrameClosure to the callee.
        return invalidateNoCallerFrame;
    }

    protected final Object getCallerFrameObject(VirtualFrame curFrame, Object callerFrame, boolean topLevel) {
        if (callerFrame instanceof CallerFrameClosure) {
            // Someone is already giving us a closure
            return callerFrame;
        }
        // callerFrame must be either a closure or the frame itself
        assert callerFrame == null || callerFrame instanceof MaterializedFrame;
        if (needsNoCallerFrame.isValid()) {
            return getCallerFrameClosure((MaterializedFrame) callerFrame);
        } else {
            if (callerFrame != null) {
                return callerFrame;
            } else if (getTopLevelProfile().profile(topLevel)) {
                return DUMMY;
            }
            return curFrame.materialize();
        }
    }

    protected final Object getCallerFrameObject(VirtualFrame callerFrame) {
        return needsNoCallerFrame.isValid() ? getCallerFrameClosure(callerFrame) : callerFrame.materialize();
    }

    private ConditionProfile getTopLevelProfile() {
        if (topLevelProfile == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            topLevelProfile = ConditionProfile.createBinaryProfile();
        }
        return topLevelProfile;
    }

    private static final class DummyCallerFrameClosure extends CallerFrameClosure {

        @Override
        public boolean setNeedsCallerFrame() {
            return false;
        }

        @Override
        public MaterializedFrame getMaterializedCallerFrame() {
            return null;
        }

    }

    public static final class InvalidateNoCallerFrame extends CallerFrameClosure {

        private final Assumption needsNoCallerFrame;
        private final MaterializedFrame frame;

        protected InvalidateNoCallerFrame(Assumption needsNoCallerFrame) {
            this.needsNoCallerFrame = needsNoCallerFrame;
            this.frame = null;
        }

        protected InvalidateNoCallerFrame(Assumption needsNoCallerFrame, MaterializedFrame frame) {
            this.needsNoCallerFrame = needsNoCallerFrame;
            this.frame = frame;
        }

        @Override
        public boolean setNeedsCallerFrame() {
            if (needsNoCallerFrame.isValid()) {
                needsNoCallerFrame.invalidate();
                return true;
            }
            return false;
        }

        @Override
        public MaterializedFrame getMaterializedCallerFrame() {
            return frame;
        }

    }

}
