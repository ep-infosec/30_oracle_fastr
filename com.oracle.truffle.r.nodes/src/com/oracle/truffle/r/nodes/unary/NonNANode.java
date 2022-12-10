/*
 * Copyright (c) 2013, 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.r.nodes.unary;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.r.runtime.MessageData;
import com.oracle.truffle.r.runtime.RRuntime;
import com.oracle.truffle.r.runtime.data.RComplex;
import com.oracle.truffle.r.runtime.data.RMissing;
import com.oracle.truffle.r.runtime.data.RNull;
import com.oracle.truffle.r.runtime.data.model.RAbstractAtomicVector;
import com.oracle.truffle.r.runtime.data.model.RAbstractContainer;
import com.oracle.truffle.r.runtime.data.nodes.VectorAccess;
import com.oracle.truffle.r.runtime.data.nodes.VectorAccess.SequentialIterator;
import com.oracle.truffle.r.runtime.nodes.unary.CastNode;

public abstract class NonNANode extends CastNode {

    private final MessageData message;
    private final Object naReplacement;

    private final BranchProfile warningProfile = BranchProfile.create();

    protected NonNANode(MessageData message, Object naReplacement) {
        this.message = message;
        this.naReplacement = naReplacement;
        assert message != null || naReplacement != null;
    }

    protected NonNANode(Object naReplacement) {
        this(null, naReplacement);
    }

    public Object getNAReplacement() {
        return naReplacement;
    }

    private Object handleNA(Object arg) {
        if (naReplacement != null) {
            if (message != null) {
                warningProfile.enter();
                handleArgumentWarning(arg, message);
            }
            return naReplacement;
        } else {
            throw handleArgumentError(arg, message);
        }
    }

    @Specialization(guards = "!isLogicalNA(x)")
    protected Object onLogicalNonNA(byte x) {
        return x;
    }

    @Specialization(guards = "isLogicalNA(x)")
    protected Object onLogicalNA(byte x) {
        return handleNA(x);
    }

    protected boolean isLogicalNA(byte x) {
        return RRuntime.isNA(x);
    }

    @Specialization
    protected Object onBoolean(boolean x) {
        return x;
    }

    @Specialization(guards = "!isIntegerNA(x)")
    protected Object onIntegerNonNA(int x) {
        return x;
    }

    @Specialization(guards = "isIntegerNA(x)")
    protected Object onIntegerNA(int x) {
        return handleNA(x);
    }

    protected boolean isIntegerNA(int x) {
        return RRuntime.isNA(x);
    }

    @Specialization(guards = "!isDoubleNA(x)")
    protected Object onDoubleNonNA(double x) {
        return x;
    }

    @Specialization(guards = "isDoubleNA(x)")
    protected Object onDoubleNA(double x) {
        return handleNA(x);
    }

    protected boolean isDoubleNA(double x) {
        return RRuntime.isNAorNaN(x);
    }

    @Specialization(guards = "!isComplexNA(x)")
    protected Object onComplexNonNA(RComplex x) {
        return x;
    }

    @Specialization(guards = "isComplexNA(x)")
    protected Object onComplex(RComplex x) {
        return handleNA(x);
    }

    protected boolean isComplexNA(RComplex x) {
        return RRuntime.isNA(x);
    }

    @Specialization(guards = "!isStringNA(x)")
    protected Object onStringNonNA(String x) {
        return x;
    }

    @Specialization(guards = "isStringNA(x)")
    protected Object onStringNA(String x) {
        return handleNA(x);
    }

    protected boolean isStringNA(String x) {
        return RRuntime.isNA(x);
    }

    @Specialization
    protected Object onNull(RNull x) {
        return x;
    }

    @Specialization
    protected Object onMissing(RMissing x) {
        return x;
    }

    @Specialization(guards = "x.isComplete()")
    protected Object onCompleteContainer(RAbstractContainer x) {
        return x;
    }

    @Specialization(guards = {"access.supports(x)", "!x.isComplete()"})
    protected RAbstractAtomicVector onPossiblyIncompleteContainerCached(RAbstractAtomicVector x,
                    @Cached("x.access()") VectorAccess access) {
        SequentialIterator iter = access.access(x);
        while (access.next(iter)) {
            if (access.isNA(iter)) {
                handleNA(x);
            }
        }
        return x;
    }

    @Specialization(replaces = "onPossiblyIncompleteContainerCached", guards = "!x.isComplete()")
    protected RAbstractAtomicVector onPossiblyIncompleteContainerGeneric(RAbstractAtomicVector x) {
        return onPossiblyIncompleteContainerCached(x, x.slowPathAccess());
    }
}
