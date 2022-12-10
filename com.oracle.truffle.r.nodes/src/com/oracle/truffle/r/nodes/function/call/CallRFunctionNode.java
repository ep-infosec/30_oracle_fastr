/*
 * Copyright (c) 2016, 2019, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.NodeCost;
import com.oracle.truffle.api.nodes.NodeInfo;
import com.oracle.truffle.r.nodes.function.visibility.SetVisibilityNode;
import com.oracle.truffle.r.runtime.ArgumentsSignature;
import com.oracle.truffle.r.runtime.RArguments;
import com.oracle.truffle.r.runtime.RArguments.DispatchArgs;
import com.oracle.truffle.r.runtime.RCaller;
import com.oracle.truffle.r.runtime.data.RFunction;

/**
 * It executes a given function via the cached call target (held by the {@link DirectCallNode} child
 * node). Using {@link DirectCallNode} enables to inline the callee's AST.
 */
@NodeInfo(cost = NodeCost.NONE)
public final class CallRFunctionNode extends CallerFrameClosureProvider {

    @Child private DirectCallNode callNode;
    @Child private SetVisibilityNode visibility = SetVisibilityNode.create();

    private CallRFunctionNode(CallTarget callTarget) {
        this.callNode = Truffle.getRuntime().createDirectCallNode(callTarget);
    }

    public static CallRFunctionNode create(CallTarget callTarget) {
        return new CallRFunctionNode(callTarget);
    }

    public Object execute(VirtualFrame frame, RFunction function, RCaller caller, Object callerFrameCandidate, Object[] evaluatedArgs, ArgumentsSignature suppliedSignature,
                    MaterializedFrame enclosingFrame, DispatchArgs dispatchArgs) {

        boolean topLevel = caller == null || caller.getDepth() == 0;
        Object[] callArgs = RArguments.create(function, caller, getCallerFrameObject(frame, callerFrameCandidate, topLevel), evaluatedArgs, suppliedSignature, enclosingFrame, dispatchArgs);
        try {
            return callNode.call(callArgs);
        } finally {
            if (!topLevel) {
                visibility.executeAfterCall(frame, caller);
            }
        }
    }

    public Object execute(VirtualFrame frame, RFunction function, RCaller caller, Object[] evaluatedArgs, ArgumentsSignature suppliedSignature,
                    MaterializedFrame enclosingFrame, DispatchArgs dispatchArgs) {
        Object[] callArgs = RArguments.create(function, caller, getCallerFrameObject(frame), evaluatedArgs, suppliedSignature, enclosingFrame, dispatchArgs);
        try {
            return callNode.call(callArgs);
        } finally {
            visibility.executeAfterCall(frame, caller);
        }
    }

    public DirectCallNode getCallNode() {
        return callNode;
    }

    public static Object executeSlowpath(RFunction function, RCaller caller, MaterializedFrame callerFrame, Object[] evaluatedArgs, ArgumentsSignature suppliedSignature, DispatchArgs dispatchArgs) {
        assert callerFrame != null;
        Object[] callArgs = RArguments.create(function, caller, callerFrame, evaluatedArgs, suppliedSignature, function.getEnclosingFrame(), dispatchArgs);
        return executeSlowpath(function, caller, callerFrame, callArgs);
    }

    public static Object executeSlowpath(RFunction function, RCaller caller, MaterializedFrame callerFrame, Object[] evaluatedArgs, DispatchArgs dispatchArgs) {
        assert callerFrame != null;
        Object[] callArgs = RArguments.create(function, caller, callerFrame, evaluatedArgs, dispatchArgs);
        return executeSlowpath(function, caller, callerFrame, callArgs);
    }

    private static Object executeSlowpath(RFunction function, RCaller caller, MaterializedFrame callerFrame, Object[] callArgs) {
        try {
            return function.getTarget().call(callArgs);
        } finally {
            SetVisibilityNode.executeAfterCallSlowPath(callerFrame, caller);
        }
    }
}
