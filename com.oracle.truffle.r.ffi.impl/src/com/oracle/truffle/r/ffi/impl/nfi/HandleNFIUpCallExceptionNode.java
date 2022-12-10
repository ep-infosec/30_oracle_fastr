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
package com.oracle.truffle.r.ffi.impl.nfi;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.ConditionProfile;
import com.oracle.truffle.r.ffi.impl.nfi.TruffleNFI_DownCallNodeFactory.NFIDownCallNode;
import com.oracle.truffle.r.ffi.impl.upcalls.UpCallsRFFI;
import com.oracle.truffle.r.runtime.context.RContext;
import com.oracle.truffle.r.runtime.ffi.NativeFunction;

@GenerateUncached
public abstract class HandleNFIUpCallExceptionNode extends Node implements UpCallsRFFI.HandleUpCallExceptionNode {

    @Override
    public abstract void execute(Throwable originalEx);

    @TruffleBoundary
    @Specialization
    public void handle(Throwable originalEx,
                    @Cached() NFIDownCallNode setFlagNode,
                    @Cached("createBinaryProfile()") ConditionProfile isEmbeddedTopLevel) {
        if (isEmbeddedTopLevel.profile(RContext.isEmbedded() && isTopLevel())) {
            return;
        }
        setFlagNode.call(NativeFunction.set_exception_flag);
        RuntimeException ex;
        if (originalEx instanceof RuntimeException) {
            ex = (RuntimeException) originalEx;
        } else {
            ex = new RuntimeException(originalEx);
        }
        TruffleNFI_Context.getInstance().setLastUpCallException(ex);
    }

    private static boolean isTopLevel() {
        return RContext.getInstance().getRFFI(TruffleNFI_Context.class).getCallDepth() == 0;
    }
}
