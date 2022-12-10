/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.r.ffi.impl.nodes;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.r.runtime.DSLConfig;
import com.oracle.truffle.r.runtime.RInternalError;
import com.oracle.truffle.r.runtime.data.RDataFactory;
import com.oracle.truffle.r.runtime.data.RDoubleVector;
import com.oracle.truffle.r.runtime.data.RNull;
import com.oracle.truffle.r.runtime.data.nodes.GetReadonlyData;
import com.oracle.truffle.r.runtime.ffi.util.ReadDoublePointerNode;
import com.oracle.truffle.r.runtime.ffi.util.ReadIntPointerNode;
import com.oracle.truffle.r.runtime.ffi.util.WritePointerNode;
import com.oracle.truffle.r.runtime.interop.ConvertForeignObjectNode;
import com.oracle.truffle.r.runtime.nmath.BesselFunctions;
import com.oracle.truffle.r.runtime.nmath.Beta;
import com.oracle.truffle.r.runtime.nmath.Choose;
import com.oracle.truffle.r.runtime.nmath.GammaFunctions;
import com.oracle.truffle.r.runtime.nmath.LBeta;
import com.oracle.truffle.r.runtime.nmath.MathConstants;
import com.oracle.truffle.r.runtime.nmath.RMath;
import com.oracle.truffle.r.runtime.nmath.distr.Logis.PLogis;
import com.oracle.truffle.r.runtime.nmath.distr.Pnorm.PnormBoth;

public final class MathFunctionsNodes {

    @ImportStatic(DSLConfig.class)
    @GenerateUncached
    public abstract static class RfPnormBothNode extends FFIUpCallNode.Arg5 {

        @Specialization
        protected Object evaluate(double x, Object cum, Object ccum, int lowerTail, int logP,
                        @Cached ReadDoublePointerNode readCumNode,
                        @Cached WritePointerNode writeCumNode,
                        @Cached WritePointerNode writeCCumNode) {
            // cum is R/W double* with size==1 and ccum is Writeonly double* with size==1
            double[] cumArr = new double[]{readCumNode.read(cum)};
            double[] ccumArr = new double[1];
            PnormBoth.evaluate(x, cumArr, ccumArr, lowerTail != 0, logP != 0);
            writeCumNode.write(cum, cumArr[0]);
            writeCCumNode.write(ccum, ccumArr[0]);
            return RNull.instance;
        }

        public static RfPnormBothNode create() {
            return MathFunctionsNodesFactory.RfPnormBothNodeGen.create();
        }

        public static RfPnormBothNode getUncached() {
            return MathFunctionsNodesFactory.RfPnormBothNodeGen.getUncached();
        }
    }

    @GenerateUncached
    public abstract static class Log1pmxNode extends FFIUpCallNode.Arg1 {

        @Specialization
        protected Object evaluate(double a) {
            return GammaFunctions.log1pmx(a);
        }

        public static Log1pmxNode create() {
            return MathFunctionsNodesFactory.Log1pmxNodeGen.create();
        }

        public static Log1pmxNode getUncached() {
            return MathFunctionsNodesFactory.Log1pmxNodeGen.getUncached();
        }
    }

    @GenerateUncached
    public abstract static class Log1pexpNode extends FFIUpCallNode.Arg1 {

        @Specialization
        protected Object evaluate(double a) {
            return PLogis.log1pexp(a);
        }

        public static Log1pexpNode create() {
            return MathFunctionsNodesFactory.Log1pexpNodeGen.create();
        }

        public static Log1pexpNode getUncached() {
            return MathFunctionsNodesFactory.Log1pexpNodeGen.getUncached();
        }
    }

    @GenerateUncached
    public abstract static class Lgamma1pNode extends FFIUpCallNode.Arg1 {

        @Specialization
        protected Object evaluate(double a) {
            return GammaFunctions.lgamma1p(a);
        }

        public static Lgamma1pNode create() {
            return MathFunctionsNodesFactory.Lgamma1pNodeGen.create();
        }

        public static Lgamma1pNode getUncached() {
            return MathFunctionsNodesFactory.Lgamma1pNodeGen.getUncached();
        }
    }

    @GenerateUncached
    public abstract static class LogspaceAddNode extends FFIUpCallNode.Arg2 {

        @Specialization
        protected Object evaluate(double a, double b) {
            return MathConstants.logspaceAdd(a, b);
        }

        public static LogspaceAddNode create() {
            return MathFunctionsNodesFactory.LogspaceAddNodeGen.create();
        }

        public static LogspaceAddNode getUncached() {
            return MathFunctionsNodesFactory.LogspaceAddNodeGen.getUncached();
        }
    }

    @GenerateUncached
    public abstract static class LogspaceSubNode extends FFIUpCallNode.Arg2 {

        @Specialization
        protected Object evaluate(double a, double b) {
            return MathConstants.logspaceSub(a, b);
        }

        public static LogspaceSubNode create() {
            return MathFunctionsNodesFactory.LogspaceSubNodeGen.create();
        }

        public static LogspaceSubNode getUncached() {
            return MathFunctionsNodesFactory.LogspaceSubNodeGen.getUncached();
        }
    }

    @GenerateUncached
    public abstract static class GammafnNode extends FFIUpCallNode.Arg1 {

        @Specialization
        protected Object evaluate(double a) {
            return GammaFunctions.gammafn(a);
        }

        public static GammafnNode create() {
            return MathFunctionsNodesFactory.GammafnNodeGen.create();
        }

        public static GammafnNode getUncached() {
            return MathFunctionsNodesFactory.GammafnNodeGen.getUncached();
        }
    }

    @GenerateUncached
    public abstract static class LGammafnNode extends FFIUpCallNode.Arg1 {

        @Specialization
        protected Object evaluate(double a) {
            return GammaFunctions.lgammafn(a);
        }

        public static LGammafnNode create() {
            return MathFunctionsNodesFactory.LGammafnNodeGen.create();
        }

        public static LGammafnNode getUncached() {
            return MathFunctionsNodesFactory.LGammafnNodeGen.getUncached();
        }
    }

    @ImportStatic(DSLConfig.class)
    @GenerateUncached
    public abstract static class LGammafnSignNode extends FFIUpCallNode.Arg2 {

        @Specialization
        protected Object evaluate(double a, Object sgn,
                        @Cached WritePointerNode writePointerNode,
                        @Cached ReadIntPointerNode readIntPointerNode) {
            int[] sgnArr = new int[]{readIntPointerNode.execute(sgn, 0)};
            double result = GammaFunctions.lgammafnSign(a, sgnArr);
            writePointerNode.execute(sgn, 0, sgnArr[0]);
            return result;
        }

        public static LGammafnSignNode create() {
            return MathFunctionsNodesFactory.LGammafnSignNodeGen.create();
        }

        public static LGammafnSignNode getUncached() {
            return MathFunctionsNodesFactory.LGammafnSignNodeGen.getUncached();
        }
    }

    @ImportStatic(DSLConfig.class)
    @GenerateUncached
    public abstract static class DpsiFnNode extends FFIUpCallNode.Arg7 {

        @Specialization
        protected Object evaluate(double x, int n, int kode, @SuppressWarnings("unused") int m, Object ans, Object nz, Object ierr,
                        @Cached ReadDoublePointerNode readAns,
                        @Cached ReadIntPointerNode readNz,
                        @Cached ReadIntPointerNode readIErr,
                        @Cached WritePointerNode writeAns,
                        @Cached WritePointerNode writeNz,
                        @Cached WritePointerNode writeIErr) {
            // ans is R/W double* size==1 and nz is R/W int* size==1
            // ierr is R/W int* size==1
            double ansIn = readAns.read(ans);
            int nzIn = readNz.read(nz);
            int ierrIn = readIErr.read(ierr);
            GammaFunctions.DpsiFnResult result = GammaFunctions.dpsifn(x, n, kode, ansIn, nzIn, ierrIn);
            writeAns.write(ans, result.ans);
            writeNz.write(nz, result.nz);
            writeIErr.write(ierr, result.ierr);
            return RNull.instance;
        }

        public static DpsiFnNode create() {
            return MathFunctionsNodesFactory.DpsiFnNodeGen.create();
        }

        public static DpsiFnNode getUncached() {
            return MathFunctionsNodesFactory.DpsiFnNodeGen.getUncached();
        }
    }

    @GenerateUncached
    public abstract static class PsiGammaNode extends FFIUpCallNode.Arg2 {

        @Specialization
        protected Object evaluate(double x, double deriv) {
            return GammaFunctions.psigamma(x, deriv);
        }

        public static PsiGammaNode create() {
            return MathFunctionsNodesFactory.PsiGammaNodeGen.create();
        }

        public static PsiGammaNode getUncached() {
            return MathFunctionsNodesFactory.PsiGammaNodeGen.getUncached();
        }
    }

    @GenerateUncached
    public abstract static class DiGammaNode extends FFIUpCallNode.Arg1 {

        @Specialization
        protected Object evaluate(double x) {
            return GammaFunctions.digamma(x);
        }

        public static DiGammaNode create() {
            return MathFunctionsNodesFactory.DiGammaNodeGen.create();
        }

        public static DiGammaNode getUncached() {
            return MathFunctionsNodesFactory.DiGammaNodeGen.getUncached();
        }
    }

    @GenerateUncached
    public abstract static class TriGammaNode extends FFIUpCallNode.Arg1 {

        @Specialization
        protected Object evaluate(double x) {
            return GammaFunctions.trigamma(x);
        }

        public static TriGammaNode create() {
            return MathFunctionsNodesFactory.TriGammaNodeGen.create();
        }

        public static TriGammaNode getUncached() {
            return MathFunctionsNodesFactory.TriGammaNodeGen.getUncached();
        }
    }

    @GenerateUncached
    public abstract static class TetraGammaNode extends FFIUpCallNode.Arg1 {

        @Specialization
        protected Object evaluate(double x) {
            return GammaFunctions.tetragamma(x);
        }

        public static TetraGammaNode create() {
            return MathFunctionsNodesFactory.TetraGammaNodeGen.create();
        }

        public static TetraGammaNode getUncached() {
            return MathFunctionsNodesFactory.TetraGammaNodeGen.getUncached();
        }
    }

    @GenerateUncached
    public abstract static class PentaGammaNode extends FFIUpCallNode.Arg1 {

        @Specialization
        protected Object evaluate(double x) {
            return GammaFunctions.pentagamma(x);
        }

        public static PentaGammaNode create() {
            return MathFunctionsNodesFactory.PentaGammaNodeGen.create();
        }

        public static PentaGammaNode getUncached() {
            return MathFunctionsNodesFactory.PentaGammaNodeGen.getUncached();
        }
    }

    @GenerateUncached
    public abstract static class BetaNode extends FFIUpCallNode.Arg2 {

        @Specialization
        protected Object evaluate(double a, double b) {
            return Beta.beta(a, b);
        }

        public static BetaNode create() {
            return MathFunctionsNodesFactory.BetaNodeGen.create();
        }

        public static BetaNode getUncached() {
            return MathFunctionsNodesFactory.BetaNodeGen.getUncached();
        }
    }

    @GenerateUncached
    public abstract static class LBetaNode extends FFIUpCallNode.Arg2 {

        @Specialization
        protected Object evaluate(double a, double b) {
            return LBeta.lbeta(a, b);
        }

        public static LBetaNode create() {
            return MathFunctionsNodesFactory.LBetaNodeGen.create();
        }

        public static LBetaNode getUncached() {
            return MathFunctionsNodesFactory.LBetaNodeGen.getUncached();
        }
    }

    @GenerateUncached
    public abstract static class ChooseNode extends FFIUpCallNode.Arg2 {

        @Specialization
        protected Object evaluate(double n, double k) {
            return Choose.choose(n, k);
        }

        public static ChooseNode create() {
            return MathFunctionsNodesFactory.ChooseNodeGen.create();
        }

        public static ChooseNode getUncached() {
            return MathFunctionsNodesFactory.ChooseNodeGen.getUncached();
        }
    }

    @GenerateUncached
    public abstract static class LChooseNode extends FFIUpCallNode.Arg2 {

        @Specialization
        protected Object evaluate(double n, double k) {
            return Choose.lchoose(n, k);
        }

        public static LChooseNode create() {
            return MathFunctionsNodesFactory.LChooseNodeGen.create();
        }

        public static LChooseNode getUncached() {
            return MathFunctionsNodesFactory.LChooseNodeGen.getUncached();
        }
    }

    @GenerateUncached
    public abstract static class BesselINode extends FFIUpCallNode.Arg3 {

        @Specialization
        protected Object evaluate(double a, double b, double c) {
            return BesselFunctions.bessel_i(a, b, c);
        }

        public static BesselINode create() {
            return MathFunctionsNodesFactory.BesselINodeGen.create();
        }

        public static BesselINode getUncached() {
            return MathFunctionsNodesFactory.BesselINodeGen.getUncached();
        }
    }

    @GenerateUncached
    public abstract static class BesselJNode extends FFIUpCallNode.Arg2 {

        @Specialization
        protected Object evaluate(double a, double b) {
            return BesselFunctions.bessel_j(a, b);
        }

        public static BesselJNode create() {
            return MathFunctionsNodesFactory.BesselJNodeGen.create();
        }

        public static BesselJNode getUncached() {
            return MathFunctionsNodesFactory.BesselJNodeGen.getUncached();
        }
    }

    @GenerateUncached
    public abstract static class BesselKNode extends FFIUpCallNode.Arg3 {

        @Specialization
        protected Object evaluate(double a, double b, double c) {
            return BesselFunctions.bessel_k(a, b, c);
        }

        public static BesselKNode create() {
            return MathFunctionsNodesFactory.BesselKNodeGen.create();
        }

        public static BesselKNode getUncached() {
            return MathFunctionsNodesFactory.BesselKNodeGen.getUncached();
        }
    }

    @GenerateUncached
    public abstract static class BesselYNode extends FFIUpCallNode.Arg2 {

        @Specialization
        protected Object evaluate(double a, double b) {
            return BesselFunctions.bessel_y(a, b);
        }

        public static BesselYNode create() {
            return MathFunctionsNodesFactory.BesselYNodeGen.create();
        }

        public static BesselYNode getUncached() {
            return MathFunctionsNodesFactory.BesselYNodeGen.getUncached();
        }
    }

    // @GenerateUncached
    public abstract static class BesselIExNode extends FFIUpCallNode.Arg4 {

        @Specialization
        protected Object evaluate(final double a, final double b, final double c, Object d,
                        @Cached("create()") BesselExNode besselEx) {
            return besselEx.execute(new BesselExCaller() {
                @Override
                public double call(double[] arr) {
                    return BesselFunctions.bessel_i_ex(a, b, c, arr);
                }

                @Override
                public int arrLen() {
                    return (int) Math.floor(b) + 1;
                }
            }, d);
        }

        public static BesselIExNode create() {
            return MathFunctionsNodesFactory.BesselIExNodeGen.create();
        }
    }

    public abstract static class BesselJExNode extends FFIUpCallNode.Arg3 {

        @Specialization
        protected Object evaluate(final double a, final double b, Object c,
                        @Cached("create()") BesselExNode besselEx) {
            return besselEx.execute(new BesselExCaller() {
                @Override
                public double call(double[] arr) {
                    return BesselFunctions.bessel_j_ex(a, b, arr);
                }

                @Override
                public int arrLen() {
                    return (int) Math.floor(b) + 1;
                }
            }, c);
        }

        public static BesselJExNode create() {
            return MathFunctionsNodesFactory.BesselJExNodeGen.create();
        }
    }

    public abstract static class BesselKExNode extends FFIUpCallNode.Arg4 {

        @Specialization
        protected Object evaluate(double a, double b, double c, Object d,
                        @Cached("create()") BesselExNode besselEx) {
            return besselEx.execute(new BesselExCaller() {
                @Override
                public double call(double[] arr) {
                    return BesselFunctions.bessel_k_ex(a, b, c, arr);
                }

                @Override
                public int arrLen() {
                    return (int) Math.floor(b) + 1;
                }
            }, d);
        }

        public static BesselKExNode create() {
            return MathFunctionsNodesFactory.BesselKExNodeGen.create();
        }
    }

    public abstract static class BesselYExNode extends FFIUpCallNode.Arg3 {

        @Specialization
        protected Object evaluate(double a, double b, Object c,
                        @Cached("create()") BesselExNode besselEx) {
            return besselEx.execute(new BesselExCaller() {
                @Override
                public double call(double[] arr) {
                    return BesselFunctions.bessel_y_ex(a, b, arr);
                }

                @Override
                public int arrLen() {
                    return (int) Math.floor(b) + 1;
                }
            }, c);
        }

        public static BesselYExNode create() {
            return MathFunctionsNodesFactory.BesselYExNodeGen.create();
        }
    }

    interface BesselExCaller {

        double call(double[] arr);

        int arrLen();

    }

    @ImportStatic(DSLConfig.class)
    abstract static class BesselExNode extends Node {

        @Child private ConvertForeignObjectNode bConvertForeign;

        public abstract double execute(BesselExCaller caller, Object b);

        @Specialization(limit = "getInteropLibraryCacheSize()", guards = "!bInterop.hasArrayElements(b)")
        protected double besselExForPointer(BesselExCaller caller, Object b,
                        @Cached("create()") GetReadonlyData.Double bReadonlyData,
                        @CachedLibrary("b") InteropLibrary bInterop) {
            RDoubleVector bVec;
            long addr;
            try {
                if (!bInterop.isPointer(b)) {
                    bInterop.toNative(b);
                }
                addr = bInterop.asPointer(b);
                bVec = RDataFactory.createDoubleVectorFromNative(addr, caller.arrLen());
            } catch (UnsupportedMessageException e) {
                throw RInternalError.shouldNotReachHere("IS_POINTER message returned true, AS_POINTER should not fail");
            }
            return caller.call(bReadonlyData.execute(bVec.materialize()));
        }

        @Specialization(limit = "getInteropLibraryCacheSize()", guards = "bInterop.hasArrayElements(b)")
        protected double besselEx(BesselExCaller caller, Object b,
                        @Cached("create()") GetReadonlyData.Double bReadonlyData,
                        @SuppressWarnings("unused") @CachedLibrary("b") InteropLibrary bInterop) {
            RDoubleVector bVec;
            if (bConvertForeign == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                bConvertForeign = insert(ConvertForeignObjectNode.create());
            }
            bVec = (RDoubleVector) bConvertForeign.convert((TruffleObject) b);
            return caller.call(bReadonlyData.execute(bVec.materialize()));
        }

        public static BesselExNode create() {
            return MathFunctionsNodesFactory.BesselExNodeGen.create();
        }

    }

    @GenerateUncached
    public abstract static class SignNode extends FFIUpCallNode.Arg1 {

        @Specialization
        protected Object evaluate(double x) {
            return RMath.sign(x);
        }

        public static SignNode create() {
            return MathFunctionsNodesFactory.SignNodeGen.create();
        }

        public static SignNode getUncached() {
            return MathFunctionsNodesFactory.SignNodeGen.getUncached();
        }
    }

    @GenerateUncached
    public abstract static class FPrecNode extends FFIUpCallNode.Arg2 {

        @Specialization
        protected Object evaluate(double x, double digits) {
            return RMath.fprec(x, digits);
        }

        public static FPrecNode create() {
            return MathFunctionsNodesFactory.FPrecNodeGen.create();
        }

        public static FPrecNode getUncached() {
            return MathFunctionsNodesFactory.FPrecNodeGen.getUncached();
        }
    }

    @GenerateUncached
    public abstract static class SinpiNode extends FFIUpCallNode.Arg1 {

        @Specialization
        protected Object evaluate(double x) {
            return RMath.sinpi(x);
        }

        public static SinpiNode create() {
            return MathFunctionsNodesFactory.SinpiNodeGen.create();
        }

        public static SinpiNode getUncached() {
            return MathFunctionsNodesFactory.SinpiNodeGen.getUncached();
        }
    }

    @GenerateUncached
    public abstract static class CospiNode extends FFIUpCallNode.Arg1 {

        @Specialization
        protected Object evaluate(double x) {
            return RMath.cospi(x);
        }

        public static CospiNode create() {
            return MathFunctionsNodesFactory.CospiNodeGen.create();
        }

        public static CospiNode getUncached() {
            return MathFunctionsNodesFactory.CospiNodeGen.getUncached();
        }
    }

    @GenerateUncached
    public abstract static class TanpiNode extends FFIUpCallNode.Arg1 {

        @Specialization
        protected Object evaluate(double x) {
            return RMath.tanpi(x);
        }

        public static TanpiNode create() {
            return MathFunctionsNodesFactory.TanpiNodeGen.create();
        }

        public static TanpiNode getUncached() {
            return MathFunctionsNodesFactory.TanpiNodeGen.getUncached();
        }
    }

}
