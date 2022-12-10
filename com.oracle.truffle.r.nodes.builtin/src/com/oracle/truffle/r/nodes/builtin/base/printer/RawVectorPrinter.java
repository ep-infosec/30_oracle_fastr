/*
 * Copyright (c) 2013, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.r.nodes.builtin.base.printer;

import java.io.IOException;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.r.runtime.RRuntime;
import com.oracle.truffle.r.runtime.data.RRawVector;

final class RawVectorPrinter extends VectorPrinter<RRawVector> {

    static final RawVectorPrinter INSTANCE = new RawVectorPrinter();

    private RawVectorPrinter() {
        // singleton
    }

    @Override
    protected RawVectorPrintJob createJob(RRawVector vector, int indx, PrintContext printCtx) {
        return new RawVectorPrintJob(vector, indx, printCtx);
    }

    private final class RawVectorPrintJob extends VectorPrintJob {

        protected RawVectorPrintJob(RRawVector vector, int indx, PrintContext printCtx) {
            super(vector, indx, printCtx);
        }

        @Override
        protected String elementTypeName() {
            return "raw";
        }

        @Override
        protected FormatMetrics formatVector(int offs, int len) {
            return new FormatMetrics(2);
        }

        @Override
        @TruffleBoundary
        protected void printElement(int i, FormatMetrics fm) throws IOException {
            String rs = RRuntime.rawToHexString(access.getRaw(iterator, i));
            if (fm.maxWidth > 2) {
                StringBuilder str = new StringBuilder(fm.maxWidth);
                for (int j = 2; j < fm.maxWidth; j++) {
                    str.append(' ');
                }
                rs = str.append(rs).toString();
            }
            printCtx.output().print(rs);
        }

        @Override
        protected void printCell(int i, FormatMetrics fm) throws IOException {
            printElement(i, fm);
        }

        @Override
        @TruffleBoundary
        protected void printEmptyVector() throws IOException {
            printCtx.output().print("raw(0)");
        }
    }
}
