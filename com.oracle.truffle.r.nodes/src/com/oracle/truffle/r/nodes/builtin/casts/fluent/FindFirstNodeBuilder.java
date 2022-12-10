/*
 * Copyright (c) 2016, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.r.nodes.builtin.casts.fluent;

import com.oracle.truffle.r.runtime.MessageData;
import com.oracle.truffle.r.nodes.builtin.casts.PipelineStep;
import com.oracle.truffle.r.nodes.builtin.casts.PipelineStep.FindFirstStep;
import com.oracle.truffle.r.runtime.data.RComplex;
import com.oracle.truffle.r.runtime.data.RDoubleVector;
import com.oracle.truffle.r.runtime.data.RComplexVector;
import com.oracle.truffle.r.runtime.data.RIntVector;
import com.oracle.truffle.r.runtime.data.RLogicalVector;
import com.oracle.truffle.r.runtime.data.RStringVector;
import com.oracle.truffle.r.runtime.data.model.RAbstractVector;

/**
 * Allows to convert find first into a valid step when used in {@code chain}, for example
 * {@code chain(findFirst().stringElement())}.
 */
public final class FindFirstNodeBuilder {
    private final MessageData message;

    public FindFirstNodeBuilder(MessageData message) {
        this.message = message;
    }

    private <V, E> PipelineStep<V, E> create(Class<?> elementClass, Object defaultValue) {
        return new FindFirstStep<>(defaultValue, elementClass, message);
    }

    public PipelineStep<RLogicalVector, Byte> logicalElement() {
        return create(Byte.class, null);
    }

    public PipelineStep<RLogicalVector, Byte> logicalElement(byte defaultValue) {
        return create(Byte.class, defaultValue);
    }

    public PipelineStep<RDoubleVector, Double> doubleElement() {
        return create(Double.class, null);
    }

    public PipelineStep<RDoubleVector, Double> doubleElement(double defaultValue) {
        return create(Double.class, defaultValue);
    }

    public PipelineStep<RIntVector, Integer> integerElement() {
        return create(Integer.class, null);
    }

    public PipelineStep<RIntVector, Integer> integerElement(int defaultValue) {
        return create(Integer.class, defaultValue);
    }

    public PipelineStep<RStringVector, String> stringElement() {
        return create(String.class, null);
    }

    public PipelineStep<RStringVector, String> stringElement(String defaultValue) {
        return create(String.class, defaultValue);
    }

    public PipelineStep<RComplexVector, RComplex> complexElement() {
        return create(String.class, null);
    }

    public PipelineStep<RComplexVector, RComplex> complexElement(RComplex defaultValue) {
        return create(String.class, defaultValue);
    }

    public PipelineStep<RAbstractVector, Object> objectElement() {
        return create(Object.class, null);
    }

    public PipelineStep<RAbstractVector, Object> objectElement(Object defaultValue) {
        return create(Object.class, defaultValue);
    }
}
