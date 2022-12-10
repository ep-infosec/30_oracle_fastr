/*
 * Copyright (c) 1995, 1996  Robert Gentleman and Ross Ihaka
 * Copyright (c) 1997-2012, The R Core Team
 * Copyright (c) 2003-2008, The R Foundation
 * Copyright (c) 2016, 2018, Oracle and/or its affiliates
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
package com.oracle.truffle.r.runtime.nmath;

import com.oracle.truffle.r.runtime.nmath.distr.*;
import static com.oracle.truffle.r.runtime.RError.SHOW_CALLER;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.r.runtime.RError;
import com.oracle.truffle.r.runtime.RError.Message;
import com.oracle.truffle.r.runtime.RRuntime;
import com.oracle.truffle.r.runtime.data.nodes.VectorAccess;
import com.oracle.truffle.r.runtime.data.nodes.VectorAccess.SequentialIterator;
import com.oracle.truffle.r.runtime.nmath.RandomFunctions.RandomNumberProvider;

public final class RMultinom {
    private RMultinom() {
        // only static method rmultinom
    }

    /**
     * Returns true if no element of the vector rN got assigned value NA, i.e. is stayed complete if
     * it was before. GnuR doc: `Return' vector rN[1:K] {K := length(prob)} where rN[j] ~ Bin(n,
     * prob[j]) , sum_j rN[j] == n, sum_j prob[j] == 1.
     */
    @TruffleBoundary
    public static boolean rmultinom(int nIn, SequentialIterator probsIter, VectorAccess probsAccess, double sum, int[] rN, int rnStartIdx, RandomNumberProvider rand, Rbinom rbinom) {
        /*
         * This calculation is sensitive to exact values, so we try to ensure that the calculations
         * are as accurate as possible so different platforms are more likely to give the same
         * result.
         */

        int n = nIn;
        int maxK = probsAccess.getLength(probsIter);
        if (RRuntime.isNA(maxK) || maxK < 1 || RRuntime.isNA(n) || n < 0) {
            if (rN.length > rnStartIdx) {
                rN[rnStartIdx] = RRuntime.INT_NA;
            }
            return false;
        }

        /*
         * Note: prob[K] is only used here for checking sum_k prob[k] = 1 ; Could make loop one
         * shorter and drop that check !
         */
        /* LDOUBLE */double pTot = 0.;
        probsAccess.reset(probsIter);
        for (int k = 0; probsAccess.next(probsIter); k++) {
            double pp = probsAccess.getDouble(probsIter) / sum;
            if (!Double.isFinite(pp) || pp < 0. || pp > 1.) {
                rN[rnStartIdx + k] = RRuntime.INT_NA;
                return false;
            }
            pTot += pp;
            rN[rnStartIdx + k] = 0;
        }

        /* LDOUBLE */double probSum = Math.abs(pTot - 1);
        if (probSum > 1e-7) {
            throw RError.error(SHOW_CALLER, Message.GENERIC, String.format("rbinom: probability sum should be 1, but is %g", pTot));
        }
        if (n == 0) {
            return true;
        }
        if (maxK == 1 && pTot == 0.) {
            return true; /* trivial border case: do as rbinom */
        }

        /* Generate the first K-1 obs. via binomials */
        probsAccess.reset(probsIter);
        for (int k = 0; probsAccess.next(probsIter) && k < maxK - 1; k++) {
            /* (p_tot, n) are for "remaining binomial" */
            /* LDOUBLE */double probK = probsAccess.getDouble(probsIter) / sum;
            if (probK != 0.) {
                double pp = probK / pTot;
                // System.out.printf("[%d] %.17f\n", k + 1, pp);
                rN[rnStartIdx + k] = ((pp < 1.) ? (int) rbinom.execute(n, pp, rand) :
                /* >= 1; > 1 happens because of rounding */
                                n);
                n -= rN[rnStartIdx + k];
            } else {
                rN[rnStartIdx + k] = 0;
            }
            if (n <= 0) {
                /* we have all */
                return true;
            }
            /* i.e. = sum(prob[(k+1):K]) */
            pTot -= probK;
        }

        rN[rnStartIdx + maxK - 1] = n;
        return true;
    }
}
