/*
 * Copyright (c) 1995, 1996  Robert Gentleman and Ross Ihaka
 * Copyright (c) 1997-2015,  The R Core Team
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

package com.oracle.truffle.r.nodes.builtin.base;

import static com.oracle.truffle.r.nodes.builtin.CastBuilder.Predef.and;
import static com.oracle.truffle.r.nodes.builtin.CastBuilder.Predef.doubleValue;
import static com.oracle.truffle.r.nodes.builtin.CastBuilder.Predef.instanceOf;
import static com.oracle.truffle.r.nodes.builtin.CastBuilder.Predef.integerValue;
import static com.oracle.truffle.r.nodes.builtin.CastBuilder.Predef.not;
import static com.oracle.truffle.r.nodes.builtin.CastBuilder.Predef.nullValue;
import static com.oracle.truffle.r.nodes.builtin.CastBuilder.Predef.stringValue;
import static com.oracle.truffle.r.nodes.builtin.CastBuilder.Predef.toBoolean;
import static com.oracle.truffle.r.runtime.builtins.RBehavior.PURE;
import static com.oracle.truffle.r.runtime.builtins.RBuiltinKind.INTERNAL;

import java.util.HashMap;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.profiles.ConditionProfile;
import com.oracle.truffle.r.nodes.builtin.RBuiltinNode;
import com.oracle.truffle.r.runtime.RError;
import com.oracle.truffle.r.runtime.RRuntime;
import com.oracle.truffle.r.runtime.builtins.RBuiltin;
import com.oracle.truffle.r.runtime.data.RDataFactory;
import com.oracle.truffle.r.runtime.data.RDoubleVector;
import com.oracle.truffle.r.runtime.data.RFunction;
import com.oracle.truffle.r.runtime.data.RIntVector;
import com.oracle.truffle.r.runtime.data.RList;
import com.oracle.truffle.r.runtime.data.RNull;
import com.oracle.truffle.r.runtime.data.RStringVector;
import com.oracle.truffle.r.runtime.data.VectorDataLibrary;
import com.oracle.truffle.r.runtime.data.model.RAbstractVector;
import com.oracle.truffle.r.runtime.ops.na.NACheck;

// Translated from main/unique.c

// TODO rowsum_df
public class RowsumFunctions {

    @RBuiltin(name = "rowsum_matrix", kind = INTERNAL, parameterNames = {"x", "g", "uniqueg", "snarm", "rn"}, behavior = PURE)
    public abstract static class Rowsum extends RBuiltinNode.Arg5 {

        private final ConditionProfile typeProfile = ConditionProfile.createBinaryProfile();
        private final NACheck na = NACheck.create();

        static {
            Casts casts = new Casts(Rowsum.class);
            casts.arg("x").mustBe(integerValue().or(doubleValue()), RError.Message.ROWSUM_NON_NUMERIC);

            casts.arg("g").mustNotBeMissing().mustBe(and(not(nullValue()), not(instanceOf(RFunction.class)))).asVector();

            casts.arg("uniqueg").mustNotBeMissing().mustBe(and(not(nullValue()), not(instanceOf(RFunction.class)))).asVector();

            casts.arg("snarm").asLogicalVector().findFirst().mustNotBeNA(RError.Message.INVALID_LOGICAL).map(toBoolean());

            casts.arg("rn").mustBe(stringValue(), RError.Message.ROWSUM_NAMES_NOT_CHAR).asStringVector();
        }

        @Specialization(limit = "getGenericDataLibraryCacheSize()")
        @TruffleBoundary
        protected Object rowsum(RAbstractVector xv, RAbstractVector g, RAbstractVector uniqueg, boolean narm, RStringVector rn,
                        @CachedLibrary("xv.getData()") VectorDataLibrary xvDataLib) {
            int p = xv.isMatrix() ? xv.getDimensions()[1] : 1;
            int n = g.getLength();
            int ng = uniqueg.getLength();
            HashMap<Object, Integer> table = new HashMap<>();
            for (int i = 0; i < ng; i++) {
                // uniqueg has no duplicates (by definition)
                table.put(uniqueg.getDataAtAsObject(i), i);
            }
            int[] matches = new int[n];
            for (int i = 0; i < n; i++) {
                Integer hi = table.get(g.getDataAtAsObject(i));
                matches[i] = hi + 1;
            }
            int offset = 0;
            int offsetg = 0;

            boolean isInt = xv instanceof RIntVector;
            RAbstractVector result;
            na.enable(xv);
            boolean complete = xvDataLib.isComplete(xv.getData());

            if (typeProfile.profile(isInt)) {
                RIntVector xi = (RIntVector) xv;
                int[] ansi = new int[ng * p];
                for (int i = 0; i < p; i++) {
                    for (int j = 0; j < n; j++) {
                        int midx = matches[j] - 1 + offsetg;
                        int itmp = ansi[midx];
                        if (na.check(xi.getDataAt(j + offset))) {
                            if (!narm) {
                                ansi[midx] = RRuntime.INT_NA;
                                complete = RDataFactory.INCOMPLETE_VECTOR;
                            }
                        } else if (!na.check(itmp)) {
                            long dtmp = itmp;
                            int jtmp = xi.getDataAt(j + offset);
                            dtmp += jtmp;
                            if (dtmp < Integer.MIN_VALUE || dtmp > Integer.MAX_VALUE) {
                                itmp = RRuntime.INT_NA;
                                complete = RDataFactory.INCOMPLETE_VECTOR;
                            } else {
                                itmp += jtmp;
                            }
                            ansi[midx] = itmp;
                        }
                    }
                    offset += n;
                    offsetg += ng;
                }
                result = RDataFactory.createIntVector(ansi, complete, new int[]{ng, p});
            } else {
                RDoubleVector xd = (RDoubleVector) xv;
                double[] ansd = new double[ng * p];
                for (int i = 0; i < p; i++) {
                    for (int j = 0; j < n; j++) {
                        int midx = matches[j] - 1 + offsetg;
                        double dtmp = xd.getDataAt(j + offset);
                        if (!narm || !Double.isNaN(dtmp)) {
                            ansd[midx] += dtmp;
                        }
                    }
                    offset += n;
                    offsetg += ng;
                }
                result = RDataFactory.createDoubleVector(ansd, complete, new int[]{ng, p});
            }
            RList dn2 = xv.materialize().getDimNames();
            Object dn2Obj = RNull.instance;
            if (dn2 != null && dn2.getLength() >= 2 && dn2.getDataAt(1) != RNull.instance) {
                dn2Obj = dn2.getDataAt(1);
            }
            RList dimNames = RDataFactory.createList(new Object[]{rn, dn2Obj});
            result.setDimNames(dimNames);
            return result;
        }
    }
}
