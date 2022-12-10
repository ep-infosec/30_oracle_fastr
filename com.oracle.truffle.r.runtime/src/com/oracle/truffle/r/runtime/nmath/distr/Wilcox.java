/*
 * Copyright (c) 1999--2014, The R Core Team
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

package com.oracle.truffle.r.runtime.nmath.distr;

import static com.oracle.truffle.r.runtime.RError.Message.CALLOC_COULD_NOT_ALLOCATE;
import static com.oracle.truffle.r.runtime.nmath.Choose.choose;
import static com.oracle.truffle.r.runtime.nmath.Choose.lchoose;
import static com.oracle.truffle.r.runtime.nmath.MathConstants.DBL_EPSILON;

import java.util.Arrays;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.r.runtime.RError;
import com.oracle.truffle.r.runtime.RError.Message;
import com.oracle.truffle.r.runtime.RRuntime;
import com.oracle.truffle.r.runtime.nmath.DPQ;
import com.oracle.truffle.r.runtime.nmath.DPQ.EarlyReturn;
import com.oracle.truffle.r.runtime.nmath.MathFunctions.Function3_1;
import com.oracle.truffle.r.runtime.nmath.MathFunctions.Function3_2;
import com.oracle.truffle.r.runtime.nmath.RMath;
import com.oracle.truffle.r.runtime.nmath.RMathError;
import com.oracle.truffle.r.runtime.nmath.RandomFunctions.RandFunction2_Double;
import com.oracle.truffle.r.runtime.nmath.RandomFunctions.RandomNumberProvider;
import com.oracle.truffle.r.runtime.nmath.TOMS708;
import com.oracle.truffle.r.runtime.nmath.distr.WilcoxFactory.RWilcoxNodeGen;
import com.oracle.truffle.r.runtime.rng.RRNG;

public final class Wilcox {
    /**
     * Wilcox will always allocate at least this size of data (for the dynamic programming
     * algorithm) in order to avoid re-allocations.
     */
    private static final int WILCOX_MIN_ALLOCATE = 50;

    /**
     * Anything above this we do not even try to allocate. This means that some of the values can be
     * safely stored in integers (will not be greater than Integer.MAX_VALUE). Note: somewhere
     * around this value, GnuR running dwilcox gets killed on Linux with 8GB RAM.
     */
    private static final int WILCOX_MAX_ALLOCATE = 10000;

    private Wilcox() {
        // only static members
    }

    /**
     * Holds cache for the dynamic algorithm that wilcox uses.
     */
    public static final class WilcoxData {
        private static final ThreadLocal<double[][][]> data = new ThreadLocal<>();

        static boolean checkSize(double m, double n) {
            if (m > WILCOX_MAX_ALLOCATE || n > WILCOX_MAX_ALLOCATE) {
                RMathError.warning(Message.WILCOX_TOO_MUCH_MEMORY, m, n);
                return false;
            }
            return true;
        }

        @TruffleBoundary
        static double[][][] getData(int mIn, int nIn) {
            double[][][] result = data.get();
            int m = mIn;
            int n = nIn;
            if (m > n) {
                int tmp = n;
                n = m;
                m = tmp;
            }

            if (result != null && (result.length <= m || (result.length > 0 && result[0].length <= n))) {
                // if the array is to small allocate it again
                result = null;
            }

            if (result == null) {
                m = Math.max(m, WILCOX_MIN_ALLOCATE);
                n = Math.max(n, WILCOX_MIN_ALLOCATE);
                try {
                    result = new double[m + 1][n + 1][];
                } catch (OutOfMemoryError e) {
                    throw couldNotAllocateError();
                }
                data.set(result);
            }
            return result;
        }

        @TruffleBoundary
        public static void freeData() {
            data.set(null);
        }
    }

    private static RError couldNotAllocateError() {
        // GnuR seems to be reporting the same number regardless of the actual size?
        throw RError.error(RError.SHOW_CALLER, CALLOC_COULD_NOT_ALLOCATE, "18446744071562067968", 4);
    }

    private static double cwilcox(double[][][] w, int kIn, int m, int n) {
        int u = m * n;
        if (kIn < 0 || kIn > u) {
            return 0;
        }
        int c = u / 2;
        int k = kIn <= c ? kIn : u - kIn; /* hence k <= floor(u / 2) */
        assert k <= Math.floor(u);
        int i;
        int j;
        if (m < n) {
            i = m;
            j = n;
        } else {
            i = n;
            j = m;
        } /* hence i <= j */
        assert i <= j;

        if (j == 0) {
            /* and hence i == 0 */
            assert i == 0;
            return k == 0 ? 1 : 0;
        }

        /*
         * We can simplify things if k is small. Consider the Mann-Whitney definition, and sort y.
         * Then if the statistic is k, no more than k of the y's can be <= any x[i], and since they
         * are sorted these can only be in the first k. So the count is the same as if there were
         * just k y's.
         */
        if (j > 0 && k < j) {
            return cwilcox(w, k, i, k);
        }

        if (w[i][j] == null) {
            w[i][j] = new double[c + 1];
            Arrays.fill(w[i][j], -1);
        }
        if (w[i][j][k] < 0) {
            assert j != 0;
            w[i][j][k] = cwilcox(w, k - j, i - 1, j) + cwilcox(w, k, i, j - 1);
        }
        return w[i][j][k];
    }

    public static final class QWilcox implements Function3_2 {

        public static QWilcox create() {
            return new QWilcox();
        }

        public static QWilcox getUncached() {
            return new QWilcox();
        }

        @Override
        public double evaluate(double xIn, double mIn, double nIn, boolean lowerTail, boolean logP) {
            if (Double.isNaN(xIn) || Double.isNaN(mIn) || Double.isNaN(nIn)) {
                return (xIn + mIn + nIn);
            }

            if (!Double.isFinite(xIn) || !Double.isFinite(mIn) || !Double.isFinite(nIn)) {
                return RMathError.defaultError();
            }

            try {
                DPQ.rqp01check(xIn, logP);
            } catch (EarlyReturn e) {
                return e.result;
            }

            if (!WilcoxData.checkSize(mIn, nIn)) {
                return Double.NaN;
            }

            double m = RMath.forceint(mIn);
            double n = RMath.forceint(nIn);
            if (m <= 0 || n <= 0) {
                return RMathError.defaultError();
            }

            if (xIn == DPQ.rdt0(lowerTail, logP)) {
                return 0;
            }
            if (xIn == DPQ.rdt1(lowerTail, logP)) {
                return m * n;
            }

            double x = !logP && lowerTail ? xIn : DPQ.rdtqiv(xIn, lowerTail, logP);
            int mm = (int) m;
            int nn = (int) n;
            double[][][] w = WilcoxData.getData(mm, nn);
            double c = choose(m + n, n);
            double p = 0;
            int q = 0;
            if (x <= 0.5) {
                x = x - 10 * DBL_EPSILON;
                while (true) {
                    p += cwilcox(w, q, mm, nn) / c;
                    if (p >= x) {
                        break;
                    }
                    q++;
                }
            } else {
                x = 1 - x + 10 * DBL_EPSILON;
                while (true) {
                    p += cwilcox(w, q, mm, nn) / c;
                    if (p > x) {
                        q = (int) (m * n - q);
                        break;
                    }
                    q++;
                }
            }

            return q;
        }
    }

    public static final class PWilcox implements Function3_2 {

        public static PWilcox create() {
            return new PWilcox();
        }

        public static PWilcox getUncached() {
            return new PWilcox();
        }

        @Override
        public double evaluate(double qIn, double mIn, double nIn, boolean lowerTail, boolean logP) {
            if (Double.isNaN(qIn) || Double.isNaN(mIn) || Double.isNaN(nIn)) {
                return (qIn + mIn + nIn);
            }

            if (!Double.isFinite(mIn) || !Double.isFinite(nIn)) {
                return RMathError.defaultError();
            }
            double m = RMath.forceint(mIn);
            double n = RMath.forceint(nIn);
            if (m <= 0 || n <= 0) {
                return RMathError.defaultError();
            }

            double q = Math.floor(qIn + 1e-7);
            if (q < 0.0) {
                return DPQ.rdt0(lowerTail, logP);
            }
            if (q >= m * n) {
                return DPQ.rdt1(lowerTail, logP);
            }

            if (!WilcoxData.checkSize(mIn, nIn)) {
                return Double.NaN;
            }

            // Note: since we limit m and n, and q < m*n, is should follow that m,n, and q < MAX_INT
            int mm = (int) m;
            int nn = (int) n;
            double[][][] w = WilcoxData.getData(mm, nn);
            double c = choose(m + n, n);
            double p = 0;
            /* Use summation of probs over the shorter range */
            if (q <= (m * n / 2)) {
                int qUpperInt = (int) Math.ceil(q);
                for (int i = 0; i <= qUpperInt; i++) {
                    p += cwilcox(w, i, mm, nn) / c;
                }
                return DPQ.rdtval(p, lowerTail, logP);
            } else {
                q = m * n - q;
                int qUpperInt = (int) Math.ceil(q);
                for (int i = 0; i < qUpperInt; i++) {
                    p += cwilcox(w, i, mm, nn) / c;
                }
                /* swap lower tail: p = 1 - p; */
                return DPQ.rdtval(p, !lowerTail, logP);
            }
        }
    }

    public static final class DWilcox implements Function3_1 {

        public static DWilcox create() {
            return new DWilcox();
        }

        public static DWilcox getUncached() {
            return new DWilcox();
        }

        @Override
        public double evaluate(double x, double mIn, double nIn, boolean giveLog) {
            /* NaNs propagated correctly */
            if (Double.isNaN(x) || Double.isNaN(mIn) || Double.isNaN(nIn)) {
                return (x + mIn + nIn);
            }

            if (!Double.isFinite(mIn) || !Double.isFinite(nIn)) {
                if (mIn == Double.POSITIVE_INFINITY) {
                    // To match GnuR's behaviour...
                    return 0;
                }
                return RMathError.defaultError();
            }

            double m = RMath.forceint(mIn);
            double n = RMath.forceint(nIn);
            if (m <= 0 || n <= 0) {
                return RMathError.defaultError();
            }

            if (TOMS708.fabs(x - RMath.forceint(x)) > 1e-7) {
                return DPQ.rd0(giveLog);
            }
            double xInt = RMath.forceint(x);
            if ((xInt < 0) || (xInt > m * n)) {
                return DPQ.rd0(giveLog);
            }

            if (!WilcoxData.checkSize(mIn, nIn)) {
                return Double.NaN;
            }

            // Note: since we limit m and n, and q < m*n, is should follow that m,n, and q < MAX_INT
            int mm = (int) m;
            int nn = (int) n;
            int xx = (int) xInt;
            double[][][] w = WilcoxData.getData(mm, nn);
            return giveLog ? Math.log(cwilcox(w, xx, mm, nn)) - lchoose(m + n, n) : cwilcox(w, xx, mm, nn) / choose(m + n, n);
        }
    }

    @GenerateUncached
    public abstract static class RWilcox extends RandFunction2_Double {
        @Specialization
        public double exec(double mIn, double nIn, RandomNumberProvider rand) {
            /* NaNs propagated correctly */
            if (Double.isNaN(mIn) || Double.isNaN(nIn)) {
                return mIn + nIn;
            }
            if (!Double.isFinite(mIn) || !Double.isFinite(nIn)) {
                // GnuR does not check this and tries to allocate the memory, we do check this, but
                // fail with the same error message for compatibility reasons.
                throw couldNotAllocateError();
            }

            double m = RMath.round(mIn);
            double n = RMath.round(nIn);
            if ((m < 0) || (n < 0)) {
                // TODO: for some reason the macro in GNUR here returns NA instead of NaN...
                // return StatsUtil.mlError();
                return RRuntime.DOUBLE_NA;
            }

            if ((m == 0) || (n == 0)) {
                return (0);
            }
            return rwilcoxRaw(rand, n, (int) (m + n));
        }

        @TruffleBoundary
        private static double rwilcoxRaw(RandomNumberProvider rand, double n, int kIn) {
            double r = 0.0;
            int k = kIn;
            int[] x;
            try {
                x = new int[k];
            } catch (OutOfMemoryError ex) {
                throw couldNotAllocateError();
            }
            for (int i = 0; i < k; i++) {
                x[i] = i;
            }
            for (int i = 0; i < n; i++) {
                int j = (int) RRNG.unifIndex(rand, k);
                r += x[j];
                x[j] = x[--k];
            }
            return (r - n * (n - 1) / 2);
        }

        public static RWilcox create() {
            return RWilcoxNodeGen.create();
        }

        public static RWilcox getUncached() {
            return RWilcoxNodeGen.getUncached();
        }
    }
}
