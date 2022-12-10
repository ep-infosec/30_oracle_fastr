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
package com.oracle.truffle.r.nodes.builtin.base.fastpaths;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.r.nodes.profile.VectorLengthProfile;
import com.oracle.truffle.r.runtime.data.RComplex;
import com.oracle.truffle.r.runtime.data.RDataFactory;
import com.oracle.truffle.r.runtime.data.RDoubleVector;
import com.oracle.truffle.r.runtime.data.RMissing;
import com.oracle.truffle.r.runtime.data.RIntVector;
import com.oracle.truffle.r.runtime.nodes.RFastPathNode;

public abstract class VectorFastPaths {

    public abstract static class IntegerFastPath extends RFastPathNode {

        @Specialization
        protected RIntVector get(@SuppressWarnings("unused") RMissing length) {
            return RDataFactory.createEmptyIntVector();
        }

        @Specialization
        protected RIntVector get(int length,
                        @Cached("create()") VectorLengthProfile profile) {
            if (length > 0) {
                return RDataFactory.createIntVector(profile.profile(length));
            }
            return null;
        }

        @Specialization
        protected RIntVector get(double length,
                        @Cached("create()") VectorLengthProfile profile) {
            if (!Double.isNaN(length)) {
                return get((int) length, profile);
            }
            return null;
        }

        @Fallback
        @SuppressWarnings("unused")
        protected Object fallback(Object length) {
            return null;
        }
    }

    public abstract static class DoubleFastPath extends RFastPathNode {

        @Specialization
        protected RDoubleVector get(@SuppressWarnings("unused") RMissing length) {
            return RDataFactory.createEmptyDoubleVector();
        }

        @Specialization
        protected RDoubleVector get(int length,
                        @Cached("create()") VectorLengthProfile profile) {
            if (length > 0) {
                return RDataFactory.createDoubleVector(profile.profile(length));
            }
            return null;
        }

        @Specialization
        protected RDoubleVector get(double length,
                        @Cached("create()") VectorLengthProfile profile) {
            if (!Double.isNaN(length)) {
                return get((int) length, profile);
            }
            return null;
        }

        @Fallback
        @SuppressWarnings("unused")
        protected Object fallback(Object length) {
            return null;
        }
    }

    public abstract static class ComplexFastPath extends RFastPathNode {

        @Specialization
        @SuppressWarnings("unused")
        protected RComplex get(RMissing lengthOut, double real, double imaginary, RMissing modulus, RMissing argument) {
            return RComplex.valueOf(real, imaginary);
        }

        @Fallback
        @SuppressWarnings("unused")
        protected Object fallback(Object lengthOut, Object real, Object imaginary, Object modulus, Object argument) {
            return null;
        }
    }
}
