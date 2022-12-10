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
import java.util.HashMap;
import java.util.Map;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.r.nodes.function.ClassHierarchyNode;
import com.oracle.truffle.r.runtime.RInternalError;
import com.oracle.truffle.r.runtime.RRuntime;
import com.oracle.truffle.r.runtime.data.CharSXPWrapper;
import com.oracle.truffle.r.runtime.data.RArgsValuesAndNames;
import com.oracle.truffle.r.runtime.data.RAttributable;
import com.oracle.truffle.r.runtime.data.RBaseObject;
import com.oracle.truffle.r.runtime.data.RDoubleVector;
import com.oracle.truffle.r.runtime.data.RExpression;
import com.oracle.truffle.r.runtime.data.RExternalPtr;
import com.oracle.truffle.r.runtime.data.RFunction;
import com.oracle.truffle.r.runtime.data.RInteropScalar;
import com.oracle.truffle.r.runtime.data.RNull;
import com.oracle.truffle.r.runtime.data.RPairList;
import com.oracle.truffle.r.runtime.data.RS4Object;
import com.oracle.truffle.r.runtime.data.RSymbol;
import com.oracle.truffle.r.runtime.data.RComplexVector;
import com.oracle.truffle.r.runtime.data.RIntVector;
import com.oracle.truffle.r.runtime.data.model.RAbstractListVector;
import com.oracle.truffle.r.runtime.data.RLogicalVector;
import com.oracle.truffle.r.runtime.data.RRawVector;
import com.oracle.truffle.r.runtime.data.RStringVector;
import com.oracle.truffle.r.runtime.env.REnvironment;
import com.oracle.truffle.r.runtime.interop.TruffleObjectConverter;

final class ValuePrinters implements ValuePrinter<Object> {

    private final Map<Class<?>, ValuePrinter<?>> printers = new HashMap<>();

    static final ValuePrinters INSTANCE = new ValuePrinters();

    private ValuePrinters() {
        printers.put(RNull.class, NullPrinter.INSTANCE);
        printers.put(RSymbol.class, SymbolPrinter.INSTANCE);
        printers.put(RFunction.class, FunctionPrinter.INSTANCE);
        printers.put(RExpression.class, ExpressionPrinter.INSTANCE);
        printers.put(RExternalPtr.class, ExternalPtrPrinter.INSTANCE);
        printers.put(RS4Object.class, S4ObjectPrinter.INSTANCE);
        printers.put(CharSXPWrapper.class, CharSXPPrinter.INSTANCE);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Override
    public void print(Object v, PrintContext printCtx) throws IOException {
        RInternalError.guarantee(v != null, "Unexpected null value");

        if (v == RNull.instance) {
            NullPrinter.INSTANCE.print(null, printCtx);
        } else {
            // handle types that are meant for or can appear via Truffle interop
            Object x = v;
            if (x instanceof RInteropScalar) {
                x = ((RInteropScalar) x).getRValue();
            }
            // try to box a scalar primitive value to the respective vector
            x = printCtx.printerNode().boxPrimitive(x);
            ValuePrinter printer = printers.get(x.getClass());
            if (printer == null) {
                if (x instanceof RIntVector && hasClass(x)) {
                    printer = FactorPrinter.INSTANCE;
                } else if (x instanceof RStringVector) {
                    printer = StringVectorPrinter.INSTANCE;
                } else if (x instanceof RDoubleVector) {
                    printer = DoubleVectorPrinter.INSTANCE;
                } else if (x instanceof RIntVector) {
                    printer = IntegerVectorPrinter.INSTANCE;
                } else if (x instanceof RLogicalVector) {
                    printer = LogicalVectorPrinter.INSTANCE;
                } else if (x instanceof RComplexVector) {
                    printer = ComplexVectorPrinter.INSTANCE;
                } else if (x instanceof RRawVector) {
                    printer = RawVectorPrinter.INSTANCE;
                } else if (x instanceof RAbstractListVector) {
                    printer = ListPrinter.INSTANCE;
                } else if (x instanceof RArgsValuesAndNames) {
                    printer = RArgsValuesAndNamesPrinter.INSTANCE;
                } else if (x instanceof REnvironment) {
                    printer = EnvironmentPrinter.INSTANCE;
                } else if (x instanceof RPairList) {
                    printer = ((RPairList) x).isLanguage() ? LanguagePrinter.INSTANCE : PairListPrinter.INSTANCE;
                } else if (x == TruffleObjectConverter.UNREADABLE) {
                    assert !(x instanceof RBaseObject) : x;
                    printer = UnreadableMemberPrinter.INSTANCE;
                } else if (x instanceof TruffleObject) {
                    assert !(x instanceof RBaseObject) : x;
                    printer = TruffleObjectPrinter.INSTANCE;
                } else {
                    RInternalError.shouldNotReachHere("unexpected type: " + x.getClass());
                }
            }
            printer.print(x, printCtx);
        }
    }

    @TruffleBoundary
    private static boolean hasClass(Object x) {
        return ClassHierarchyNode.hasClass((RAttributable) x, RRuntime.CLASS_FACTOR);
    }

    public static void printNewLine(PrintContext printCtx) {
        if (!Boolean.TRUE.equals(printCtx.getAttribute(DONT_PRINT_NL_ATTR))) {
            printCtx.output().println();
        } else {
            // Clear the instruction attribute
            printCtx.setAttribute(DONT_PRINT_NL_ATTR, false);
        }
    }
}
