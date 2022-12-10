/*
 * Copyright (c) 2014, 2022, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.truffle.r.nodes.builtin.CastBuilder.Predef.toBoolean;
import static com.oracle.truffle.r.runtime.RVisibility.OFF;
import static com.oracle.truffle.r.runtime.builtins.RBehavior.COMPLEX;
import static com.oracle.truffle.r.runtime.builtins.RBuiltinKind.PRIMITIVE;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.FrameSlotTypeException;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.api.profiles.ConditionProfile;
import com.oracle.truffle.r.nodes.access.ConstantNode;
import com.oracle.truffle.r.nodes.builtin.RBuiltinNode;
import com.oracle.truffle.r.runtime.RInternalError;
import com.oracle.truffle.r.runtime.RRuntime;
import com.oracle.truffle.r.runtime.builtins.RBuiltin;
import com.oracle.truffle.r.runtime.data.RDataFactory;
import com.oracle.truffle.r.runtime.data.RNull;
import com.oracle.truffle.r.runtime.data.RPairList;
import com.oracle.truffle.r.runtime.data.RPromise;
import com.oracle.truffle.r.runtime.env.frame.FrameIndex;
import com.oracle.truffle.r.runtime.env.frame.FrameSlotChangeMonitor;
import com.oracle.truffle.r.runtime.env.frame.RFrameSlot;

@RBuiltin(name = "on.exit", visibility = OFF, kind = PRIMITIVE, parameterNames = {"expr", "add"}, nonEvalArgs = 0, behavior = COMPLEX)
public abstract class OnExit extends RBuiltinNode.Arg2 {

    @CompilationFinal private int onExitFrameIndex = RFrameSlot.OnExit.getFrameIdx();

    private final ConditionProfile addProfile = ConditionProfile.createBinaryProfile();
    private final ConditionProfile newProfile = ConditionProfile.createBinaryProfile();
    private final ConditionProfile emptyPromiseProfile = ConditionProfile.createBinaryProfile();

    static {
        Casts casts = new Casts(OnExit.class);
        casts.arg("add").asLogicalVector().findFirst(RRuntime.LOGICAL_FALSE).mustNotBeNA().map(toBoolean());
    }

    @Override
    public Object[] getDefaultParameterValues() {
        return new Object[]{RNull.instance, RRuntime.LOGICAL_FALSE};
    }

    @Specialization
    protected Object onExit(VirtualFrame frame, RPromise expr, boolean add,
                    @Cached BranchProfile appendToEndProfile) {

        if (FrameIndex.isUninitializedIndex(onExitFrameIndex)) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            onExitFrameIndex = FrameSlotChangeMonitor.findOrAddAuxiliaryFrameSlot(frame.getFrameDescriptor(), RFrameSlot.OnExit);
        }
        assert FrameSlotChangeMonitor.containsIdentifier(frame.getFrameDescriptor(), RFrameSlot.OnExit);

        // the empty (RNull.instance) expression is used to clear on.exit
        if (emptyPromiseProfile.profile(expr.isDefaultArgument())) {
            assert expr.getRep() instanceof ConstantNode : "only ConstantNode expected for defaulted promise";
            FrameSlotChangeMonitor.setObject(frame, onExitFrameIndex, RDataFactory.createPairList());
        } else {
            // if optimized then evaluated is already true,
            // otherwise the expression has to be evaluated exactly at this point
            assert expr.isOptimized() || !expr.isEvaluated() : "promise cannot be evaluated anymore";
            Object value;
            try {
                value = FrameSlotChangeMonitor.getObject(frame, onExitFrameIndex);
            } catch (FrameSlotTypeException e) {
                throw RInternalError.shouldNotReachHere();
            }
            RPairList list;
            if (newProfile.profile(value == null)) {
                // initialize the list of exit handlers
                FrameSlotChangeMonitor.setObject(frame, onExitFrameIndex, list = RDataFactory.createPairList());
            } else {
                list = (RPairList) value;
                if (addProfile.profile(!add)) {
                    // add is false, so clear the existing list
                    assert !list.isShared();
                    list.setCdr(RNull.instance);
                    list.setCar(RNull.instance);
                }
            }
            list.appendToEnd(RDataFactory.createPairList(expr.getRep()), appendToEndProfile);
        }
        return RNull.instance;
    }
}
