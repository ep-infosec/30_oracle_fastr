/*
 * Copyright (c) 2014, 2022, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.truffle.r.nodes.builtin.CastBuilder.Predef.eq;
import static com.oracle.truffle.r.nodes.builtin.CastBuilder.Predef.singleElement;
import static com.oracle.truffle.r.nodes.builtin.CastBuilder.Predef.stringValue;
import static com.oracle.truffle.r.runtime.RError.Message.NOT_CHARACTER_VECTOR;
import static com.oracle.truffle.r.runtime.RError.Message.WRONG_WINSLASH;
import static com.oracle.truffle.r.runtime.builtins.RBehavior.IO;
import static com.oracle.truffle.r.runtime.builtins.RBuiltinKind.INTERNAL;

import java.io.IOException;
import java.nio.file.NoSuchFileException;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.profiles.ConditionProfile;
import com.oracle.truffle.r.nodes.builtin.RBuiltinNode;
import com.oracle.truffle.r.runtime.RError.Message;
import com.oracle.truffle.r.runtime.RRuntime;
import com.oracle.truffle.r.runtime.builtins.RBuiltin;
import com.oracle.truffle.r.runtime.data.RDataFactory;
import com.oracle.truffle.r.runtime.data.RStringVector;

@RBuiltin(name = "normalizePath", kind = INTERNAL, parameterNames = {"path", "winslash", "mustwork"}, behavior = IO)
public abstract class NormalizePath extends RBuiltinNode.Arg3 {

    private final ConditionProfile doesNotNeedToWork = ConditionProfile.createBinaryProfile();

    static {
        Casts casts = new Casts(NormalizePath.class);
        casts.arg("path").mustBe(stringValue(), NOT_CHARACTER_VECTOR, "path");
        casts.arg("winslash").defaultError(NOT_CHARACTER_VECTOR, "winslash").mustBe(stringValue()).asStringVector().mustBe(singleElement()).findFirst().mustBe(eq("/").or(eq("\\\\")).or(eq("\\")),
                        WRONG_WINSLASH);
        // Note: NA is acceptable value for mustwork with special meaning
        casts.arg("mustwork").asLogicalVector().findFirst();
    }

    @Specialization
    @TruffleBoundary
    protected RStringVector doNormalizePath(RStringVector pathVec, @SuppressWarnings("unused") String winslash, byte mustWork) {
        String[] results = new String[pathVec.getLength()];
        for (int i = 0; i < results.length; i++) {
            String path = pathVec.getDataAt(i);
            String normPath = path;
            try {
                normPath = getRContext().getSafeTruffleFile(path).getCanonicalFile().toString();
            } catch (IOException e) {
                if (doesNotNeedToWork.profile(mustWork == RRuntime.LOGICAL_FALSE)) {
                    // no error or warning
                } else {
                    if (mustWork == RRuntime.LOGICAL_TRUE) {
                        if (e instanceof NoSuchFileException) {
                            throw error(Message.NORMALIZE_PATH_NOSUCH, i + 1, path);
                        } else {
                            throw error(Message.GENERIC, e.toString());
                        }
                    } else {
                        // NA means warning
                        if (e instanceof NoSuchFileException) {
                            warning(Message.NORMALIZE_PATH_NOSUCH, i + 1, path);
                        } else {
                            warning(Message.GENERIC, e.toString());
                        }
                    }
                }
            }
            results[i] = normPath;
        }
        return RDataFactory.createStringVector(results, RDataFactory.COMPLETE_VECTOR);
    }
}
