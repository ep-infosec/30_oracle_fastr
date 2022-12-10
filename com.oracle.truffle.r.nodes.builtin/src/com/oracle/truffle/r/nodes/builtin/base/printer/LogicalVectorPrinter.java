/*
 * Copyright (c) 1995, 1996  Robert Gentleman and Ross Ihaka
 * Copyright (c) 1997-2013,  The R Core Team
 * Copyright (c) 2016, 2021, Oracle and/or its affiliates
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, a copy is available at
 * https://www.R-project.org/Licenses/
 */
package com.oracle.truffle.r.nodes.builtin.base.printer;

import java.io.IOException;

import com.oracle.truffle.r.runtime.RRuntime;
import com.oracle.truffle.r.runtime.data.RLogicalVector;
import com.oracle.truffle.r.runtime.data.nodes.VectorAccess;
import com.oracle.truffle.r.runtime.data.nodes.VectorAccess.RandomIterator;

//Transcribed from GnuR, src/main/printutils.c, src/main/format.c

public final class LogicalVectorPrinter extends VectorPrinter<RLogicalVector> {

    static final LogicalVectorPrinter INSTANCE = new LogicalVectorPrinter();

    private LogicalVectorPrinter() {
        // singleton
    }

    @Override
    protected VectorPrinter<RLogicalVector>.VectorPrintJob createJob(RLogicalVector vector, int indx, PrintContext printCtx) {
        return new LogicalVectorPrintJob(vector, indx, printCtx);
    }

    private final class LogicalVectorPrintJob extends VectorPrintJob {

        protected LogicalVectorPrintJob(RLogicalVector vector, int indx, PrintContext printCtx) {
            super(vector, indx, printCtx);
        }

        @Override
        protected String elementTypeName() {
            return "logical";
        }

        @Override
        protected FormatMetrics formatVector(int offs, int len) {
            return new FormatMetrics(formatLogicalVectorInternal(iterator, access, offs, len, printCtx.parameters().getNaWidth()));
        }

        @Override
        protected void printElement(int i, FormatMetrics fm) throws IOException {
            out.print(encodeLogical(access.getLogical(iterator, i), fm.maxWidth, printCtx.parameters()));
        }

        @Override
        protected void printCell(int i, FormatMetrics fm) throws IOException {
            printElement(i, fm);
        }

        @Override
        protected void printEmptyVector() throws IOException {
            out.print("logical(0)");
        }
    }

    static int formatLogicalVectorInternal(RandomIterator iter, VectorAccess access, int offs, int n, int naWidth) {
        int fieldwidth = 1;
        for (int i = 0; i < n; i++) {
            byte xi = access.getLogical(iter, offs + i);
            if (xi == RRuntime.LOGICAL_NA) {
                if (fieldwidth < naWidth) {
                    fieldwidth = naWidth;
                }
            } else if (xi != 0 && fieldwidth < 4) {
                fieldwidth = 4;
            } else if (xi == 0 && fieldwidth < 5) {
                fieldwidth = 5;
                break;
                /* this is the widest it can be, so stop */
            }
        }
        return fieldwidth;
    }

    static String encodeLogical(byte x, int w, PrintParameters pp) {
        String id;
        if (x == RRuntime.LOGICAL_NA) {
            id = pp.getNaString();
        } else if (x != RRuntime.LOGICAL_FALSE) {
            id = "TRUE";
        } else {
            id = "FALSE";
        }
        if (id.length() == w) {
            return id;
        }
        int blanks = w // target width
                        - id.length(); // text
        StringBuilder str = new StringBuilder();
        for (int i = 0; i < blanks; i++) {
            str.append(' ');
        }
        str.append(id);
        return str.toString();
    }

    public static String[] format(RLogicalVector value, boolean trim, int width, PrintParameters pp) {
        VectorAccess access = value.slowPathAccess();
        RandomIterator iter = access.randomAccess(value);
        int length = access.getLength(iter);
        int w;
        if (trim) {
            w = 1;
        } else {
            w = formatLogicalVectorInternal(iter, access, 0, length, pp.getNaWidth());
        }
        w = Math.max(w, width);

        String[] result = new String[value.getLength()];
        for (int i = 0; i < length; i++) {
            result[i] = encodeLogical(access.getLogical(iter, i), w, pp);
        }
        return result;
    }
}
