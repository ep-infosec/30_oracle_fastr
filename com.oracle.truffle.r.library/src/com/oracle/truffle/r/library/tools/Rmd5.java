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
package com.oracle.truffle.r.library.tools;

import static com.oracle.truffle.r.nodes.builtin.CastBuilder.Predef.stringValue;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleFile;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.r.nodes.builtin.RExternalBuiltinNode;
import com.oracle.truffle.r.runtime.RError;
import com.oracle.truffle.r.runtime.RInternalError;
import com.oracle.truffle.r.runtime.RRuntime;
import com.oracle.truffle.r.runtime.Utils;
import com.oracle.truffle.r.runtime.data.RDataFactory;
import com.oracle.truffle.r.runtime.data.RStringVector;

public abstract class Rmd5 extends RExternalBuiltinNode.Arg1 {

    static {
        Casts casts = new Casts(Rmd5.class);
        casts.arg(0).defaultError(RError.Message.ARG_MUST_BE_CHARACTER, "files").mustBe(stringValue());
    }

    @Specialization
    @TruffleBoundary
    protected RStringVector rmd5(RStringVector files) {
        MessageDigest digest;
        boolean complete = RDataFactory.COMPLETE_VECTOR;
        try {
            digest = MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException ex) {
            throw RInternalError.shouldNotReachHere("no MD5");
        }
        String[] data = new String[files.getLength()];
        for (int i = 0; i < data.length; i++) {
            TruffleFile file = getRContext().getSafeTruffleFile(files.getDataAt(i));
            String dataValue = RRuntime.STRING_NA;
            if (!(file.exists() && file.isReadable())) {
                complete = false;
            } else {
                try (BufferedInputStream in = new BufferedInputStream(file.newInputStream())) {
                    byte[] bytes = new byte[(int) file.size()];
                    in.read(bytes);
                    dataValue = Utils.toHexString(digest.digest(bytes));
                } catch (IOException ex) {
                    // unexpected as we checked
                    complete = false;
                }
            }
            data[i] = dataValue;
        }
        return RDataFactory.createStringVector(data, complete);
    }
}
