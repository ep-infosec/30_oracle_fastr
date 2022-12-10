/*
 * Copyright (c) 1995, 1996, 1997  Robert Gentleman and Ross Ihaka
 * Copyright (c) 1995-2014, The R Core Team
 * Copyright (c) 2002-2008, The R Foundation
 * Copyright (c) 2016, 2022, Oracle and/or its affiliates
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

import static com.oracle.truffle.r.nodes.builtin.CastBuilder.Predef.abstractVectorValue;
import static com.oracle.truffle.r.nodes.builtin.CastBuilder.Predef.gte0;
import static com.oracle.truffle.r.nodes.builtin.CastBuilder.Predef.intNA;
import static com.oracle.truffle.r.nodes.builtin.CastBuilder.Predef.not;
import static com.oracle.truffle.r.nodes.builtin.CastBuilder.Predef.notEmpty;
import static com.oracle.truffle.r.nodes.builtin.CastBuilder.Predef.numericValue;
import static com.oracle.truffle.r.nodes.builtin.CastBuilder.Predef.rawValue;
import static com.oracle.truffle.r.nodes.builtin.CastBuilder.Predef.stringValue;
import static com.oracle.truffle.r.runtime.RError.Message.INVALID_TIES_FOR_RANK;
import static com.oracle.truffle.r.runtime.RError.Message.INVALID_VALUE;
import static com.oracle.truffle.r.runtime.RError.Message.RANK_LARGE_N;
import static com.oracle.truffle.r.runtime.RError.Message.UNIMPLEMENTED_TYPE_IN_GREATER;
import static com.oracle.truffle.r.runtime.builtins.RBehavior.PURE;
import static com.oracle.truffle.r.runtime.builtins.RBuiltinKind.INTERNAL;

import java.util.function.Function;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.r.nodes.builtin.RBuiltinNode;
import com.oracle.truffle.r.nodes.builtin.base.OrderNodeGen.CmpNodeGen;
import com.oracle.truffle.r.nodes.builtin.base.OrderNodeGen.OrderVector1NodeGen;
import com.oracle.truffle.r.runtime.RError;
import com.oracle.truffle.r.runtime.RRuntime;
import com.oracle.truffle.r.runtime.RType;
import com.oracle.truffle.r.runtime.builtins.RBuiltin;
import com.oracle.truffle.r.runtime.data.RDataFactory;
import com.oracle.truffle.r.runtime.data.RLogicalVector;
import com.oracle.truffle.r.runtime.data.VectorDataLibrary;
import com.oracle.truffle.r.runtime.data.model.RAbstractVector;

@RBuiltin(name = "rank", kind = INTERNAL, parameterNames = {"x", "len", "ties.method"}, behavior = PURE)
public abstract class Rank extends RBuiltinNode.Arg3 {

    @Child private Order.OrderVector1Node orderVector1Node;
    @Child private Order.CmpNode orderCmpNode;
    private final BranchProfile errorProfile = BranchProfile.create();

    private enum TiesKind {
        AVERAGE,
        MAX,
        MIN
    }

    static {
        Casts casts = new Casts(Rank.class);
        Function<Object, Object> typeFunc = x -> x.getClass().getSimpleName();
        casts.arg("x").mustBe(abstractVectorValue(), UNIMPLEMENTED_TYPE_IN_GREATER, typeFunc).mustBe(not(rawValue()), RError.Message.RAW_SORT);
        // Note: in the case of no long vector support, when given anything but integer as n, GnuR
        // behaves as if n=1,
        // we allow ourselves to be bit inconsistent with GnuR in that.
        casts.arg("len").defaultError(INVALID_VALUE, "length(xx)").mustBe(numericValue()).asIntegerVector().mustBe(notEmpty()).findFirst().mustBe(intNA().not().and(gte0()));
        // Note: we parse ties.methods in the Specialization anyway, so the validation of the value
        // is there
        casts.arg("ties.method").defaultError(INVALID_TIES_FOR_RANK).mustBe(stringValue()).asStringVector().findFirst();
    }

    private Order.OrderVector1Node initOrderVector1() {
        if (orderVector1Node == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            orderVector1Node = insert(OrderVector1NodeGen.create());
        }
        return orderVector1Node;
    }

    private Order.CmpNode initOrderCmp() {
        if (orderCmpNode == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            orderCmpNode = insert(CmpNodeGen.create());
        }
        return orderCmpNode;
    }

    @Specialization
    protected Object rank(RAbstractVector xa, int inN, String tiesMethod,
                    @CachedLibrary(limit = "getVectorAccessCacheSize()") VectorDataLibrary vecDataLib) {
        int n = inN;
        Object xaData = xa.getData();
        if (n > vecDataLib.getLength(xaData)) {
            errorProfile.enter();
            n = vecDataLib.getLength(xaData);
            warning(RANK_LARGE_N);
        }

        TiesKind tiesKind = getTiesKind(tiesMethod);
        int[] ik = null;
        double[] rk = null;
        if (tiesKind == TiesKind.AVERAGE) {
            rk = new double[n];
        } else {
            ik = new int[n];
        }
        int[] indx = new int[n];
        for (int i = 0; i < n; i++) {
            indx[i] = i;
        }
        xaData = xa instanceof RLogicalVector ? vecDataLib.cast(xaData, RType.Integer) : xaData;
        initOrderVector1().execute(indx, xaData, vecDataLib, RRuntime.LOGICAL_TRUE, false, false);
        initOrderCmp();
        int j;
        for (int i = 0; i < n; i = j + 1) {
            j = i;
            while ((j < n - 1) && orderCmpNode.executeInt(xaData, indx[j], indx[j + 1], false, vecDataLib) == 0) {
                j++;
            }
            switch (tiesKind) {
                case AVERAGE:
                    for (int k = i; k <= j; k++) {
                        rk[indx[k]] = (i + j + 2) / 2.;
                    }
                    break;
                case MAX:
                    for (int k = i; k <= j; k++) {
                        ik[indx[k]] = j + 1;
                    }
                    break;
                case MIN:
                    for (int k = i; k <= j; k++) {
                        ik[indx[k]] = i + 1;
                    }
                    break;
            }
        }
        if (tiesKind == TiesKind.AVERAGE) {
            return RDataFactory.createDoubleVector(rk, RDataFactory.COMPLETE_VECTOR);
        } else {
            return RDataFactory.createIntVector(ik, RDataFactory.COMPLETE_VECTOR);
        }
    }

    private TiesKind getTiesKind(String tiesMethod) {
        switch (tiesMethod) {
            case "average":
                return TiesKind.AVERAGE;
            case "max":
                return TiesKind.MAX;
            case "min":
                return TiesKind.MIN;
            default:
                errorProfile.enter();
                throw error(RError.Message.GENERIC, "invalid ties.method for rank() [should never happen]");
        }
    }
}
