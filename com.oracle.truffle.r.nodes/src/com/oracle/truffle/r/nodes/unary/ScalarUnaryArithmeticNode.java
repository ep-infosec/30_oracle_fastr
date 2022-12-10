/*
 * Copyright (c) 2015, 2020, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.r.nodes.primitive.UnaryMapNAFunctionNode;
import com.oracle.truffle.r.runtime.RRuntime;
import com.oracle.truffle.r.runtime.data.RComplex;
import com.oracle.truffle.r.runtime.data.RDataFactory;
import com.oracle.truffle.r.runtime.data.RDoubleSeqVectorData;
import com.oracle.truffle.r.runtime.data.RIntSeqVectorData;
import com.oracle.truffle.r.runtime.data.RSeq;
import com.oracle.truffle.r.runtime.data.model.RAbstractVector;
import com.oracle.truffle.r.runtime.ops.Operation;
import com.oracle.truffle.r.runtime.ops.UnaryArithmetic;
import com.oracle.truffle.r.runtime.ops.UnaryArithmetic.Negate;
import com.oracle.truffle.r.runtime.ops.UnaryArithmetic.Plus;

public class ScalarUnaryArithmeticNode extends UnaryMapNAFunctionNode {

    @Child private UnaryArithmetic arithmetic;

    public ScalarUnaryArithmeticNode(UnaryArithmetic arithmetic) {
        this.arithmetic = arithmetic;
    }

    @Override
    public RAbstractVector tryFoldConstantTime(RAbstractVector operand, int operandLength) {
        if (arithmetic instanceof Plus) {
            return operand;
        } else if (arithmetic instanceof Negate && operand.isSequence()) {
            RSeq seq = operand.getSequence();
            if (seq instanceof RIntSeqVectorData) {
                int start = ((RIntSeqVectorData) seq).getStart();
                int stride = ((RIntSeqVectorData) seq).getStride();
                return RDataFactory.createIntSequence(applyInteger(start), applyInteger(stride), operandLength);
            } else if (seq instanceof RDoubleSeqVectorData) {
                double start = ((RDoubleSeqVectorData) seq).getStart();
                double stride = ((RDoubleSeqVectorData) seq).getStride();
                return RDataFactory.createDoubleSequence(applyDouble(start), applyDouble(stride), operandLength);
            }
        }
        return null;
    }

    @Override
    public boolean mayFoldConstantTime(Class<?> operandClass) {
        if (arithmetic instanceof Plus) {
            return true;
        } else if (arithmetic instanceof Negate && RSeq.class.isAssignableFrom(operandClass)) {
            return true;
        }
        return false;
    }

    @Override
    public final double applyDouble(double operand) {
        if (operandNACheck.check(operand)) {
            return RRuntime.DOUBLE_NA;
        }
        try {
            return arithmetic.op(operand);
        } catch (Throwable e) {
            CompilerDirectives.transferToInterpreter();
            throw Operation.handleException(e);
        }
    }

    @Override
    public final double applyDouble(RComplex operand) {
        try {
            return arithmetic.opdChecked(operandNACheck, operand.getRealPart(), operand.getImaginaryPart());
        } catch (Throwable e) {
            CompilerDirectives.transferToInterpreter();
            throw Operation.handleException(e);
        }
    }

    @Override
    public final RComplex applyComplex(RComplex operand) {
        if (operandNACheck.check(operand)) {
            return RRuntime.COMPLEX_NA;
        }
        try {
            return arithmetic.op(operand.getRealPart(), operand.getImaginaryPart());
        } catch (Throwable e) {
            CompilerDirectives.transferToInterpreter();
            throw Operation.handleException(e);
        }
    }

    @Override
    public final int applyInteger(int operand) {
        if (operandNACheck.check(operand)) {
            return RRuntime.INT_NA;
        }
        try {
            return arithmetic.op(operand);
        } catch (Throwable e) {
            CompilerDirectives.transferToInterpreter();
            throw Operation.handleException(e);
        }
    }
}
