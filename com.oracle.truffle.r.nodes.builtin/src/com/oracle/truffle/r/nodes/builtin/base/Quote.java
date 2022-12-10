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
package com.oracle.truffle.r.nodes.builtin.base;

import static com.oracle.truffle.r.runtime.builtins.RBehavior.PURE;
import static com.oracle.truffle.r.runtime.builtins.RBuiltinKind.PRIMITIVE;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.profiles.ConditionProfile;
import com.oracle.truffle.r.nodes.RASTUtils;
import com.oracle.truffle.r.nodes.builtin.RBuiltinNode;
import com.oracle.truffle.r.nodes.function.signature.QuoteNode;
import com.oracle.truffle.r.runtime.DSLConfig;
import com.oracle.truffle.r.runtime.builtins.RBuiltin;
import com.oracle.truffle.r.runtime.data.Closure;
import com.oracle.truffle.r.runtime.data.RMissing;
import com.oracle.truffle.r.runtime.data.RPromise;
import com.oracle.truffle.r.runtime.data.RSharingAttributeStorage;
import com.oracle.truffle.r.runtime.data.RSymbol;

/**
 * This builtin is just a fallback - this normally uses {@link QuoteNode} directly.
 */
@RBuiltin(name = "quote", nonEvalArgs = 0, kind = PRIMITIVE, parameterNames = {"expr"}, behavior = PURE)
@ImportStatic(DSLConfig.class)
public abstract class Quote extends RBuiltinNode.Arg1 {

    static {
        Casts.noCasts(Quote.class);
    }

    private final ConditionProfile shareableProfile = ConditionProfile.createBinaryProfile();

    public abstract Object execute(RPromise expr);

    /**
     * Creates a shared permanent language so that it can be cached and repeatedly returned as the
     * result.
     */
    protected final Object cachedCreateLanguage(Closure closure) {
        Object result = createLanguage(closure);
        if (shareableProfile.profile(RSharingAttributeStorage.isShareable(result))) {
            ((RSharingAttributeStorage) result).makeSharedPermanent();
        }
        return result;
    }

    @TruffleBoundary
    protected static Object createLanguage(Closure closure) {
        return RASTUtils.createLanguageElement(closure.getExpr().asRSyntaxNode());
    }

    @SuppressWarnings("unused")
    @Specialization(limit = "getCacheSize(3)", guards = "cachedClosure == expr.getClosure()")
    protected Object quoteCached(RPromise expr,
                    @Cached("expr.getClosure()") Closure cachedClosure,
                    @Cached("cachedCreateLanguage(cachedClosure)") Object language) {
        return language;
    }

    @Specialization(replaces = "quoteCached")
    protected Object quote(RPromise expr) {
        return createLanguage(expr.getClosure());
    }

    @Specialization
    protected Object quote(@SuppressWarnings("unused") RMissing expr) {
        return RSymbol.MISSING;
    }
}
