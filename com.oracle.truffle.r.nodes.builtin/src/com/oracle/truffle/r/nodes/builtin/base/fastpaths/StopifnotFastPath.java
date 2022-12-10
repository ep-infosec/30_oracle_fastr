/*
 * Copyright (c) 2019, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.r.nodes.builtin.base.fastpaths;

import static com.oracle.truffle.r.runtime.builtins.RBehavior.COMPLEX;
import static com.oracle.truffle.r.runtime.builtins.RBuiltinKind.SUBSTITUTE;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.profiles.ValueProfile;
import com.oracle.truffle.r.nodes.binary.BoxPrimitiveNode;
import com.oracle.truffle.r.nodes.function.PromiseHelperNode;
import com.oracle.truffle.r.runtime.RRuntime;
import com.oracle.truffle.r.runtime.RVisibility;
import com.oracle.truffle.r.runtime.builtins.RBuiltin;
import com.oracle.truffle.r.runtime.data.RArgsValuesAndNames;
import com.oracle.truffle.r.runtime.data.RMissing;
import com.oracle.truffle.r.runtime.data.RNull;
import com.oracle.truffle.r.runtime.data.RPromise;
import com.oracle.truffle.r.runtime.data.RLogicalVector;
import com.oracle.truffle.r.runtime.nodes.RFastPathNode;

/**
 * {@code stopifnot} is often used to check some unlikely conditions. It is complex R function that
 * always uses stack introspection and deparsing even if the condition evaluates to {@code TRUE},
 * which is very expensive for "unlikely" condition check. This fast-path tries to avoid all this
 * complexity for cases when the condition is single value that evaluates to {@code TRUE}.
 */
@RBuiltin(name = "stopifnot", kind = SUBSTITUTE, parameterNames = {"...", "exprs", "local"}, nonEvalArgs = {0, 1, 2}, behavior = COMPLEX, visibility = RVisibility.OFF)
public class StopifnotFastPath extends RFastPathNode {
    @Child private PromiseHelperNode promiseHelperNode = new PromiseHelperNode();
    @Child private BoxPrimitiveNode boxPrimitiveNode = BoxPrimitiveNode.create();
    private final ValueProfile resultProfile = ValueProfile.createClassProfile();

    @Override
    public Object execute(VirtualFrame frame, Object... args) {
        if (!(args[0] instanceof RArgsValuesAndNames) || args[1] != RMissing.instance || args[2] != RMissing.instance) {
            // Arguments "exprs" and "local" have default values
            return null;
        }
        RArgsValuesAndNames dotdotdot = (RArgsValuesAndNames) args[0];
        if (dotdotdot.getLength() != 1 || !(dotdotdot.getArgument(0) instanceof RPromise)) {
            return null;
        }
        RPromise resultPromise = (RPromise) dotdotdot.getArgument(0);
        Object result = resultProfile.profile(boxPrimitiveNode.execute(promiseHelperNode.evaluate(frame, resultPromise)));
        if (!(result instanceof RLogicalVector)) {
            // We fallback to the R code, the promise will be already evaluated and not re-evaluated
            // Since the other arguments have only default values, there could not be any visible
            // side effect between now and the point when the promise should have been really
            // evaluated later in stopifnot R code
            return null;
        }
        RLogicalVector resultVec = (RLogicalVector) result;
        if (resultVec.getLength() != 1 || !RRuntime.fromLogical(resultVec.getDataAt(0))) {
            return null;
        }
        return RNull.instance;
    }
}
