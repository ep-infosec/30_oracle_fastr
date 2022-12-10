/*
 * Copyright (c) 2017, 2022, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.ValueProfile;
import com.oracle.truffle.r.runtime.context.RContext;
import com.oracle.truffle.r.runtime.env.REnvironment;

/**
 * Helper node to efficiently retrieve base environment's frame, e.g. for use as
 * {@code genericDefFrame} parameter in {@link S3FunctionLookupNode}.
 */
public final class GetBaseEnvFrameNode extends Node {
    private final ValueProfile frameAccessProfile = ValueProfile.createClassProfile();
    private final ValueProfile frameProfile = ValueProfile.createClassProfile();
    private final ValueProfile baseEnvProfile = ValueProfile.createIdentityProfile();

    public static GetBaseEnvFrameNode create() {
        return new GetBaseEnvFrameNode();
    }

    public MaterializedFrame execute() {
        REnvironment baseEnv = baseEnvProfile.profile(REnvironment.baseEnv(RContext.getInstance(this)));
        return frameProfile.profile(baseEnv.getFrame(frameAccessProfile));
    }
}
