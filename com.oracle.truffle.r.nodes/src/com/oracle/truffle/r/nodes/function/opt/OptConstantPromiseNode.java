/*
 * Copyright (c) 2014, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.r.nodes.function.opt;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.r.nodes.function.PromiseNode;
import com.oracle.truffle.r.runtime.data.Closure;
import com.oracle.truffle.r.runtime.data.RDataFactory;
import com.oracle.truffle.r.runtime.data.RPromise;
import com.oracle.truffle.r.runtime.data.RPromise.PromiseState;
import com.oracle.truffle.r.runtime.nodes.RBaseNode;
import com.oracle.truffle.r.runtime.nodes.RSyntaxNode;

/**
 * A optimizing {@link PromiseNode}: It evaluates a constant directly.
 */
public final class OptConstantPromiseNode extends PromiseNode {

    private final PromiseState state;
    private final Closure constantExpression;
    private final Object constantValue;

    public OptConstantPromiseNode(PromiseState state, RBaseNode constantExpression, Object constantValue) {
        super(null);
        this.state = state;
        this.constantExpression = Closure.createPromiseClosure(constantExpression);
        this.constantValue = constantValue;
    }

    /**
     * Creates a new {@link RPromise} every time.
     */
    @Override
    public Object execute(VirtualFrame frame) {
        return RDataFactory.createEvaluatedPromise(state, constantExpression, constantValue, frame.materialize());
    }

    @Override
    public RSyntaxNode getPromiseExpr() {
        return (RSyntaxNode) constantExpression.getExpr();
    }
}
