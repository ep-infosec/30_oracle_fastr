/*
 * Copyright (c) 2016, 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.r.library.methods;

import static com.oracle.truffle.r.nodes.builtin.CastBuilder.Predef.instanceOf;
import static com.oracle.truffle.r.runtime.RError.Message.INVALID_LIST_FOR_SUBSTITUTION;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.r.nodes.RASTUtils;
import com.oracle.truffle.r.nodes.builtin.EnvironmentNodes.RList2EnvNode;
import com.oracle.truffle.r.nodes.builtin.RExternalBuiltinNode;
import com.oracle.truffle.r.runtime.RError;
import com.oracle.truffle.r.runtime.RSubstitute;
import com.oracle.truffle.r.runtime.data.RDataFactory;
import com.oracle.truffle.r.runtime.data.RList;
import com.oracle.truffle.r.runtime.data.RPairList;
import com.oracle.truffle.r.runtime.data.model.RAbstractListVector;
import com.oracle.truffle.r.runtime.env.REnvironment;
import com.oracle.truffle.r.runtime.nodes.RBaseNode;

public abstract class SubstituteDirect extends RExternalBuiltinNode.Arg2 {

    static {
        Casts casts = new Casts(SubstituteDirect.class);
        casts.arg(1).defaultError(INVALID_LIST_FOR_SUBSTITUTION).mustBe(instanceOf(RAbstractListVector.class).or(instanceOf(REnvironment.class)));
    }

    @Override
    public RBaseNode getErrorContext() {
        return RError.SHOW_CALLER;
    }

    @Specialization
    @TruffleBoundary
    protected Object substituteDirect(Object object, REnvironment env) {
        if ((object instanceof RPairList && ((RPairList) object).isLanguage())) {
            RPairList lang = (RPairList) object;
            return RASTUtils.createLanguageElement(RSubstitute.substitute(env, lang.getSyntaxElement(), getRLanguage()));
        } else {
            return object;
        }
    }

    @Specialization(guards = {"list.getNames() == null || list.getNames().getLength() == 0"})
    @TruffleBoundary
    protected Object substituteDirect(Object object, @SuppressWarnings("unused") RList list) {
        return substituteDirect(object, createNewEnvironment());
    }

    @Specialization(guards = {"list.getNames() != null", "list.getNames().getLength() > 0"})
    @TruffleBoundary
    protected Object substituteDirect(Object object, RList list,
                    @Cached("createList2EnvNode()") RList2EnvNode list2Env) {
        return substituteDirect(object, createEnvironment(list, list2Env));
    }

    @Fallback
    protected Object substituteDirect(@SuppressWarnings("unused") Object object, @SuppressWarnings("unused") Object env) {
        throw error(RError.Message.INVALID_OR_UNIMPLEMENTED_ARGUMENTS);
    }

    @TruffleBoundary
    public static REnvironment createNewEnvironment() {
        return createEnvironment(null, null);
    }

    @TruffleBoundary
    public static REnvironment createEnvironment(RList list, RList2EnvNode list2Env) {
        if (list2Env != null) {
            return list2Env.execute(list, null, null, REnvironment.baseEnv());
        } else {
            REnvironment env = RDataFactory.createNewEnv(null);
            env.setParent(REnvironment.baseEnv());
            return env;
        }
    }

    protected static RList2EnvNode createList2EnvNode() {
        return RList2EnvNode.create(true);
    }
}
