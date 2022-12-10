/*
 * Copyright (c) 2014, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.r.runtime.ffi;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.NodeInterface;

/**
 * Explicit statically typed interface to user-supplied random number generators.
 */
public interface UserRngRFFI {
    interface InitNode extends NodeInterface {

        void execute(VirtualFrame frame, int seed);
    }

    interface RandNode extends NodeInterface {

        double execute(VirtualFrame frame);
    }

    interface NSeedNode extends NodeInterface {

        int execute(VirtualFrame frame);
    }

    interface SeedsNode extends NodeInterface {

        void execute(VirtualFrame frame, int[] n);
    }

    InitNode createInitNode();

    RandNode createRandNode();

    NSeedNode createNSeedNode();

    SeedsNode createSeedsNode();
}
