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
package com.oracle.truffle.r.nodes.access;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.r.nodes.access.variables.ReadVariableNode;
import com.oracle.truffle.r.nodes.function.PromiseHelperNode.PromiseCheckHelperNode;
import com.oracle.truffle.r.nodes.function.visibility.SetVisibilityNode;
import com.oracle.truffle.r.runtime.ArgumentsSignature;
import com.oracle.truffle.r.runtime.RError;
import com.oracle.truffle.r.runtime.RType;
import com.oracle.truffle.r.runtime.data.RArgsValuesAndNames;
import com.oracle.truffle.r.runtime.data.RMissing;
import com.oracle.truffle.r.runtime.nodes.RNode;
import com.oracle.truffle.r.runtime.nodes.RSourceSectionNode;
import com.oracle.truffle.r.runtime.nodes.RSyntaxLookup;
import com.oracle.truffle.r.runtime.nodes.RSyntaxNode;

/**
 * An {@link RNode} that handles accesses to components of the variadic argument (..1, ..2, etc.).
 */
public final class ReadVariadicComponentNode extends RSourceSectionNode implements RSyntaxNode, RSyntaxLookup {

    @Child private ReadVariableNode lookup = ReadVariableNode.createSilent(ArgumentsSignature.VARARG_NAME, RType.Any);
    @Child private PromiseCheckHelperNode promiseHelper = new PromiseCheckHelperNode();
    @Child private SetVisibilityNode visibility = SetVisibilityNode.create();

    private final int index;
    private final String name;

    public ReadVariadicComponentNode(SourceSection src, int index) {
        super(src);
        this.index = index;
        this.name = RSyntaxLookup.getVariadicComponentSymbol(index + 1);
    }

    @Override
    public Object execute(VirtualFrame frame) {
        visibility.execute(frame, true);

        Object args = lookup.execute(frame);
        if (args == null) {
            throw error(RError.Message.NO_DOT_DOT, index + 1);
        }
        RArgsValuesAndNames argsValuesAndNames = (RArgsValuesAndNames) args;
        if (argsValuesAndNames.isEmpty()) {
            throw RError.error(RError.SHOW_CALLER, RError.Message.DOT_DOT_NONE);
        }
        if (argsValuesAndNames.getLength() <= index) {
            throw error(RError.Message.DOT_DOT_SHORT, index + 1);
        }
        Object ret = argsValuesAndNames.getArgument(index);
        return ret == null ? RMissing.instance : promiseHelper.checkVisibleEvaluate(frame, ret);
    }

    public String getPrintForm() {
        return name;
    }

    @Override
    public String getIdentifier() {
        return name;
    }

    @Override
    public boolean isFunctionLookup() {
        return false;
    }
}
