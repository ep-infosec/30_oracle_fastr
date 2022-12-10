/*
 * Copyright (c) 2013, 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.r.nodes.builtin.base;

import java.util.function.Supplier;

import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.r.library.utils.Download.CurlDownload;
import com.oracle.truffle.r.library.utils.DownloadNodeGen.CurlDownloadNodeGen;
import com.oracle.truffle.r.nodes.RRootNode;
import com.oracle.truffle.r.nodes.access.variables.ReadVariableNode;
import com.oracle.truffle.r.nodes.binary.BinaryArithmeticNodeGen;
import com.oracle.truffle.r.nodes.binary.BinaryArithmeticSpecial;
import com.oracle.truffle.r.nodes.binary.BinaryBooleanNodeGen;
import com.oracle.truffle.r.nodes.binary.BinaryBooleanScalarNodeGen;
import com.oracle.truffle.r.nodes.binary.BinaryBooleanSpecial;
import com.oracle.truffle.r.nodes.builtin.RBuiltinNode;
import com.oracle.truffle.r.nodes.builtin.RBuiltinPackage;
import com.oracle.truffle.r.nodes.builtin.base.ConnectionFunctions.SockSelect;
import com.oracle.truffle.r.nodes.builtin.base.ConnectionFunctionsFactory.SockSelectNodeGen;
import com.oracle.truffle.r.nodes.builtin.base.DebugFunctions.FastRSetBreakpoint;
import com.oracle.truffle.r.nodes.builtin.base.DebugFunctionsFactory.FastRSetBreakpointNodeGen;
import com.oracle.truffle.r.nodes.builtin.base.VersionFunctions.RVersion;
import com.oracle.truffle.r.nodes.builtin.base.fastpaths.AssignFastPathNodeGen;
import com.oracle.truffle.r.nodes.builtin.base.fastpaths.ExistsFastPathNodeGen;
import com.oracle.truffle.r.nodes.builtin.base.fastpaths.GetFastPathNodeGen;
import com.oracle.truffle.r.nodes.builtin.base.fastpaths.IntersectFastPathNodeGen;
import com.oracle.truffle.r.nodes.builtin.base.fastpaths.IsElementFastPathNodeGen;
import com.oracle.truffle.r.nodes.builtin.base.fastpaths.MatchArgFastPath;
import com.oracle.truffle.r.nodes.builtin.base.fastpaths.MatchArgFastPathNodeGen;
import com.oracle.truffle.r.nodes.builtin.base.fastpaths.MatrixFastPathNodeGen;
import com.oracle.truffle.r.nodes.builtin.base.fastpaths.SetDiffFastPathNodeGen;
import com.oracle.truffle.r.nodes.builtin.base.fastpaths.StopifnotFastPath;
import com.oracle.truffle.r.nodes.builtin.base.fastpaths.SubscriptDataFrameFastPathNodeGen;
import com.oracle.truffle.r.nodes.builtin.base.fastpaths.SubsetDataFrameFastPath;
import com.oracle.truffle.r.nodes.builtin.base.fastpaths.SubsetDataFrameFastPathNodeGen;
import com.oracle.truffle.r.nodes.builtin.base.fastpaths.VectorFastPathsFactory.ComplexFastPathNodeGen;
import com.oracle.truffle.r.nodes.builtin.base.fastpaths.VectorFastPathsFactory.DoubleFastPathNodeGen;
import com.oracle.truffle.r.nodes.builtin.base.fastpaths.VectorFastPathsFactory.IntegerFastPathNodeGen;
import com.oracle.truffle.r.nodes.builtin.base.foreign.CallAndExternalFunctions;
import com.oracle.truffle.r.nodes.builtin.base.foreign.CallAndExternalFunctionsFactory;
import com.oracle.truffle.r.nodes.builtin.base.foreign.FortranAndCFunctions;
import com.oracle.truffle.r.nodes.builtin.base.foreign.FortranAndCFunctions.DotFortran;
import com.oracle.truffle.r.nodes.builtin.base.foreign.FortranAndCFunctionsFactory;
import com.oracle.truffle.r.nodes.builtin.base.infix.AccessField;
import com.oracle.truffle.r.nodes.builtin.base.infix.AccessFieldNodeGen;
import com.oracle.truffle.r.nodes.builtin.base.infix.AssignBuiltin;
import com.oracle.truffle.r.nodes.builtin.base.infix.AssignBuiltinEq;
import com.oracle.truffle.r.nodes.builtin.base.infix.AssignBuiltinEqNodeGen;
import com.oracle.truffle.r.nodes.builtin.base.infix.AssignBuiltinNodeGen;
import com.oracle.truffle.r.nodes.builtin.base.infix.AssignOuterBuiltin;
import com.oracle.truffle.r.nodes.builtin.base.infix.AssignOuterBuiltinNodeGen;
import com.oracle.truffle.r.nodes.builtin.base.infix.BraceBuiltin;
import com.oracle.truffle.r.nodes.builtin.base.infix.BraceBuiltinNodeGen;
import com.oracle.truffle.r.nodes.builtin.base.infix.BreakBuiltin;
import com.oracle.truffle.r.nodes.builtin.base.infix.BreakBuiltinNodeGen;
import com.oracle.truffle.r.nodes.builtin.base.infix.ForBuiltin;
import com.oracle.truffle.r.nodes.builtin.base.infix.ForBuiltinNodeGen;
import com.oracle.truffle.r.nodes.builtin.base.infix.FunctionBuiltin;
import com.oracle.truffle.r.nodes.builtin.base.infix.IfBuiltin;
import com.oracle.truffle.r.nodes.builtin.base.infix.IfBuiltinNodeGen;
import com.oracle.truffle.r.nodes.builtin.base.infix.NextBuiltin;
import com.oracle.truffle.r.nodes.builtin.base.infix.NextBuiltinNodeGen;
import com.oracle.truffle.r.nodes.builtin.base.infix.ParenBuiltin;
import com.oracle.truffle.r.nodes.builtin.base.infix.RepeatBuiltin;
import com.oracle.truffle.r.nodes.builtin.base.infix.RepeatBuiltinNodeGen;
import com.oracle.truffle.r.nodes.builtin.base.infix.Subscript;
import com.oracle.truffle.r.nodes.builtin.base.infix.SubscriptNodeGen;
import com.oracle.truffle.r.nodes.builtin.base.infix.Subset;
import com.oracle.truffle.r.nodes.builtin.base.infix.SubsetNodeGen;
import com.oracle.truffle.r.nodes.builtin.base.infix.Tilde;
import com.oracle.truffle.r.nodes.builtin.base.infix.TildeNodeGen;
import com.oracle.truffle.r.nodes.builtin.base.infix.UpdateField;
import com.oracle.truffle.r.nodes.builtin.base.infix.UpdateFieldNodeGen;
import com.oracle.truffle.r.nodes.builtin.base.infix.UpdateSubscript;
import com.oracle.truffle.r.nodes.builtin.base.infix.UpdateSubscriptNodeGen;
import com.oracle.truffle.r.nodes.builtin.base.infix.UpdateSubset;
import com.oracle.truffle.r.nodes.builtin.base.infix.UpdateSubsetNodeGen;
import com.oracle.truffle.r.nodes.builtin.base.infix.WhileBuiltin;
import com.oracle.truffle.r.nodes.builtin.base.infix.WhileBuiltinNodeGen;
import com.oracle.truffle.r.nodes.builtin.base.system.SystemFunction;
import com.oracle.truffle.r.nodes.builtin.base.system.SystemFunctionNodeGen;
import com.oracle.truffle.r.nodes.builtin.fastr.FastRContext;
import com.oracle.truffle.r.nodes.builtin.fastr.FastRContextFactory;
import com.oracle.truffle.r.nodes.builtin.fastr.FastRDebug;
import com.oracle.truffle.r.nodes.builtin.fastr.FastRDebugNodeGen;
import com.oracle.truffle.r.nodes.builtin.fastr.FastRDispatchNativeHandlers;
import com.oracle.truffle.r.nodes.builtin.fastr.FastRGDSetGraphics;
import com.oracle.truffle.r.nodes.builtin.fastr.FastRGDSetGraphicsNodeGen;
import com.oracle.truffle.r.nodes.builtin.fastr.FastRGetExecutor;
import com.oracle.truffle.r.nodes.builtin.fastr.FastRHelp.FastRAddHelpPath;
import com.oracle.truffle.r.nodes.builtin.fastr.FastRHelp.FastRHelpPath;
import com.oracle.truffle.r.nodes.builtin.fastr.FastRHelp.FastRHelpRd;
import com.oracle.truffle.r.nodes.builtin.fastr.FastRHelpFactory.FastRHelpPathNodeGen;
import com.oracle.truffle.r.nodes.builtin.fastr.FastRHelpFactory.FastRHelpRdNodeGen;
import com.oracle.truffle.r.nodes.builtin.fastr.FastRIdentity;
import com.oracle.truffle.r.nodes.builtin.fastr.FastRIdentityNodeGen;
import com.oracle.truffle.r.nodes.builtin.fastr.FastRInitEventLoop;
import com.oracle.truffle.r.nodes.builtin.fastr.FastRInitEventLoopNodeGen;
import com.oracle.truffle.r.nodes.builtin.fastr.FastRInspect;
import com.oracle.truffle.r.nodes.builtin.fastr.FastRInspectFrame;
import com.oracle.truffle.r.nodes.builtin.fastr.FastRInspectFrameNodeGen;
import com.oracle.truffle.r.nodes.builtin.fastr.FastRInspectNodeGen;
import com.oracle.truffle.r.nodes.builtin.fastr.FastRInterop;
import com.oracle.truffle.r.nodes.builtin.fastr.FastRInterop.FastRInteropCheckException;
import com.oracle.truffle.r.nodes.builtin.fastr.FastRInterop.FastRInteropClearException;
import com.oracle.truffle.r.nodes.builtin.fastr.FastRInterop.FastRInteropGetException;
import com.oracle.truffle.r.nodes.builtin.fastr.FastRInterop.FastRInteropTry;
import com.oracle.truffle.r.nodes.builtin.fastr.FastRInteropFactory;
import com.oracle.truffle.r.nodes.builtin.fastr.FastRInteropFactory.FastRInteropCheckExceptionNodeGen;
import com.oracle.truffle.r.nodes.builtin.fastr.FastRInteropFactory.FastRInteropClearExceptionNodeGen;
import com.oracle.truffle.r.nodes.builtin.fastr.FastRInteropFactory.FastRInteropGetExceptionNodeGen;
import com.oracle.truffle.r.nodes.builtin.fastr.FastRInteropFactory.FastRInteropTryNodeGen;
import com.oracle.truffle.r.nodes.builtin.fastr.FastRJavaGDResize;
import com.oracle.truffle.r.nodes.builtin.fastr.FastRLibPaths;
import com.oracle.truffle.r.nodes.builtin.fastr.FastRLibPathsNodeGen;
import com.oracle.truffle.r.nodes.builtin.fastr.FastROptionBuiltin;
import com.oracle.truffle.r.nodes.builtin.fastr.FastRPatchPackage;
import com.oracle.truffle.r.nodes.builtin.fastr.FastRPatchPackageNodeGen;
import com.oracle.truffle.r.nodes.builtin.fastr.FastRPkgSource;
import com.oracle.truffle.r.nodes.builtin.fastr.FastRPkgSourceNodeGen;
import com.oracle.truffle.r.nodes.builtin.fastr.FastRPrintError;
import com.oracle.truffle.r.nodes.builtin.fastr.FastRPrintErrorNodeGen;
import com.oracle.truffle.r.nodes.builtin.fastr.FastRRCallerTrace;
import com.oracle.truffle.r.nodes.builtin.fastr.FastRRefCountInfo;
import com.oracle.truffle.r.nodes.builtin.fastr.FastRRefCountInfoNodeGen;
import com.oracle.truffle.r.nodes.builtin.fastr.FastRRegisterFunctions;
import com.oracle.truffle.r.nodes.builtin.fastr.FastRRegisterFunctionsNodeGen;
import com.oracle.truffle.r.nodes.builtin.fastr.FastRSVGFileName;
import com.oracle.truffle.r.nodes.builtin.fastr.FastRSVGFileNameNodeGen;
import com.oracle.truffle.r.nodes.builtin.fastr.FastRSVGGetContent;
import com.oracle.truffle.r.nodes.builtin.fastr.FastRSVGGetContentNodeGen;
import com.oracle.truffle.r.nodes.builtin.fastr.FastRSVGSetFileName;
import com.oracle.truffle.r.nodes.builtin.fastr.FastRSVGSetFileNameNodeGen;
import com.oracle.truffle.r.nodes.builtin.fastr.FastRSavePlot;
import com.oracle.truffle.r.nodes.builtin.fastr.FastRSavePlotNodeGen;
import com.oracle.truffle.r.nodes.builtin.fastr.FastRSetConsoleHandler;
import com.oracle.truffle.r.nodes.builtin.fastr.FastRSetConsoleHandlerNodeGen;
import com.oracle.truffle.r.nodes.builtin.fastr.FastRSetToolchain;
import com.oracle.truffle.r.nodes.builtin.fastr.FastRSetToolchainNodeGen;
import com.oracle.truffle.r.nodes.builtin.fastr.FastRSlotAssign;
import com.oracle.truffle.r.nodes.builtin.fastr.FastRSlotAssignNodeGen;
import com.oracle.truffle.r.nodes.builtin.fastr.FastRSourceInfo;
import com.oracle.truffle.r.nodes.builtin.fastr.FastRSourceInfoNodeGen;
import com.oracle.truffle.r.nodes.builtin.fastr.FastRStackTrace;
import com.oracle.truffle.r.nodes.builtin.fastr.FastRStackTraceNodeGen;
import com.oracle.truffle.r.nodes.builtin.fastr.FastRSyntaxTree;
import com.oracle.truffle.r.nodes.builtin.fastr.FastRSyntaxTreeNodeGen;
import com.oracle.truffle.r.nodes.builtin.fastr.FastRTestsTry;
import com.oracle.truffle.r.nodes.builtin.fastr.FastRTestsTryNodeGen;
import com.oracle.truffle.r.nodes.builtin.fastr.FastRThrowCompilerError;
import com.oracle.truffle.r.nodes.builtin.fastr.FastRThrowIt;
import com.oracle.truffle.r.nodes.builtin.fastr.FastRThrowItNodeGen;
import com.oracle.truffle.r.nodes.builtin.fastr.FastRTrace;
import com.oracle.truffle.r.nodes.builtin.fastr.FastRTraceFactory;
import com.oracle.truffle.r.nodes.builtin.fastr.FastRTree;
import com.oracle.truffle.r.nodes.builtin.fastr.FastRTreeNodeGen;
import com.oracle.truffle.r.nodes.builtin.fastr.FastRTreeStats;
import com.oracle.truffle.r.nodes.builtin.fastr.FastRTreeStatsNodeGen;
import com.oracle.truffle.r.nodes.builtin.fastr.FastRUseDebugMakevars;
import com.oracle.truffle.r.nodes.builtin.fastr.FastRUseDebugMakevarsNodeGen;
import com.oracle.truffle.r.nodes.builtin.fastr.FastrDqrls;
import com.oracle.truffle.r.nodes.builtin.fastr.FastrDqrlsNodeGen;
import com.oracle.truffle.r.nodes.builtin.fastr.memprof.FastRprofmem;
import com.oracle.truffle.r.nodes.builtin.fastr.memprof.FastRprofmemNodeGen;
import com.oracle.truffle.r.nodes.builtin.fastr.memprof.FastRprofmemShow;
import com.oracle.truffle.r.nodes.builtin.fastr.memprof.FastRprofmemShowNodeGen;
import com.oracle.truffle.r.nodes.builtin.fastr.memprof.FastRprofmemSnapshot;
import com.oracle.truffle.r.nodes.builtin.fastr.memprof.FastRprofmemSnapshotNodeGen;
import com.oracle.truffle.r.nodes.builtin.fastr.memprof.FastRprofmemSource;
import com.oracle.truffle.r.nodes.builtin.fastr.memprof.FastRprofmemSourceNodeGen;
import com.oracle.truffle.r.nodes.unary.UnaryArithmeticBuiltinNode;
import com.oracle.truffle.r.nodes.unary.UnaryArithmeticSpecial;
import com.oracle.truffle.r.nodes.unary.UnaryNotNode;
import com.oracle.truffle.r.nodes.unary.UnaryNotNodeGen;
import com.oracle.truffle.r.runtime.RInternalError;
import com.oracle.truffle.r.runtime.RVisibility;
import com.oracle.truffle.r.runtime.builtins.FastPathFactory;
import com.oracle.truffle.r.runtime.builtins.RBuiltin;
import com.oracle.truffle.r.runtime.context.RContext;
import com.oracle.truffle.r.runtime.data.RFunction;
import com.oracle.truffle.r.runtime.nodes.RFastPathNode;
import com.oracle.truffle.r.runtime.ops.BinaryArithmetic;
import com.oracle.truffle.r.runtime.ops.BinaryArithmeticFactory;
import com.oracle.truffle.r.runtime.ops.BinaryCompare;
import com.oracle.truffle.r.runtime.ops.BinaryLogic;
import com.oracle.truffle.r.runtime.ops.BooleanOperationFactory;
import com.oracle.truffle.r.runtime.ops.UnaryArithmetic;
import com.oracle.truffle.r.runtime.ops.UnaryArithmeticFactory;

public class BasePackage extends RBuiltinPackage {

    public BasePackage(RContext context) {
        super(context, "base");

        /*
         * Primitive operations (these are really builtins, but not currently defined that way, so
         * we fake it). N.B. UnaryNotNode is annotated, but not loaded automatically because it is
         * not in the {nodes.builtin.base} package, (along with all the other nodes). A corollary of
         * this is that all the node classes referenced here must be annotated with
         */
        add(UnaryNotNode.class, UnaryNotNodeGen::create);

        addUnaryArithmetic(Ceiling.class, Ceiling::new);
        addUnaryArithmetic(Floor.class, Floor::new);
        addUnaryArithmetic(Trunc.class, Trunc::new);
        addUnaryArithmetic(LogFunctions.Log10.class, LogFunctions.Log10::new);
        addUnaryArithmetic(LogFunctions.Log1p.class, LogFunctions.Log1p::new);
        addUnaryArithmetic(LogFunctions.Log2.class, LogFunctions.Log2::new);
        addUnaryArithmetic(NumericalFunctions.Abs.class, NumericalFunctions.Abs::new);
        addUnaryArithmetic(NumericalFunctions.Arg.class, NumericalFunctions.Arg::new);
        addUnaryArithmetic(NumericalFunctions.Conj.class, NumericalFunctions.Conj::new);
        addUnaryArithmetic(NumericalFunctions.Im.class, NumericalFunctions.Im::new);
        addUnaryArithmetic(NumericalFunctions.Mod.class, NumericalFunctions.Mod::new);
        addUnaryArithmetic(NumericalFunctions.Re.class, NumericalFunctions.Re::new);
        addUnaryArithmetic(NumericalFunctions.Sign.class, NumericalFunctions.Sign::new);
        addUnaryArithmetic(NumericalFunctions.Sqrt.class, NumericalFunctions.Sqrt::new);
        addUnaryArithmetic(TrigExpFunctions.Acos.class, TrigExpFunctions.Acos::new);
        addUnaryArithmetic(TrigExpFunctions.Acosh.class, TrigExpFunctions.Acosh::new);
        addUnaryArithmetic(TrigExpFunctions.Asin.class, TrigExpFunctions.Asin::new);
        addUnaryArithmetic(TrigExpFunctions.Asinh.class, TrigExpFunctions.Asinh::new);
        addUnaryArithmetic(TrigExpFunctions.Atan.class, TrigExpFunctions.Atan::new);
        addUnaryArithmetic(TrigExpFunctions.Atanh.class, TrigExpFunctions.Atanh::new);
        addUnaryArithmetic(TrigExpFunctions.Cos.class, TrigExpFunctions.Cos::new);
        addUnaryArithmetic(TrigExpFunctions.Cosh.class, TrigExpFunctions.Cosh::new);
        addUnaryArithmetic(TrigExpFunctions.Cospi.class, TrigExpFunctions.Cospi::new);
        addUnaryArithmetic(TrigExpFunctions.Exp.class, TrigExpFunctions.Exp::new);
        addUnaryArithmetic(TrigExpFunctions.ExpM1.class, TrigExpFunctions.ExpM1::new);
        addUnaryArithmetic(TrigExpFunctions.Sin.class, TrigExpFunctions.Sin::new);
        addUnaryArithmetic(TrigExpFunctions.Sinh.class, TrigExpFunctions.Sinh::new);
        addUnaryArithmetic(TrigExpFunctions.Sinpi.class, TrigExpFunctions.Sinpi::new);
        addUnaryArithmetic(TrigExpFunctions.Tan.class, TrigExpFunctions.Tan::new);
        addUnaryArithmetic(TrigExpFunctions.Tanh.class, TrigExpFunctions.Tanh::new);
        addUnaryArithmetic(TrigExpFunctions.Tanpi.class, TrigExpFunctions.Tanpi::new);

        addBinaryArithmetic(BinaryArithmetic.AddBuiltin.class, BinaryArithmetic.ADD, UnaryArithmetic.PLUS);
        addBinaryArithmetic(BinaryArithmetic.SubtractBuiltin.class, BinaryArithmetic.SUBTRACT, UnaryArithmetic.NEGATE);
        addBinaryArithmetic(BinaryArithmetic.DivBuiltin.class, BinaryArithmetic.DIV, null);
        addBinaryArithmetic(BinaryArithmetic.IntegerDivBuiltin.class, BinaryArithmetic.INTEGER_DIV, null);
        addBinaryArithmetic(BinaryArithmetic.ModBuiltin.class, BinaryArithmetic.MOD, null);
        addBinaryArithmetic(BinaryArithmetic.MultiplyBuiltin.class, BinaryArithmetic.MULTIPLY, null);
        addBinaryArithmetic(BinaryArithmetic.PowBuiltin.class, BinaryArithmetic.POW, null);

        addBinaryCompare(BinaryCompare.EqualBuiltin.class, BinaryCompare.EQUAL);
        addBinaryCompare(BinaryCompare.NotEqualBuiltin.class, BinaryCompare.NOT_EQUAL);
        addBinaryCompare(BinaryCompare.GreaterEqualBuiltin.class, BinaryCompare.GREATER_EQUAL);
        addBinaryCompare(BinaryCompare.GreaterBuiltin.class, BinaryCompare.GREATER_THAN);
        addBinaryCompare(BinaryCompare.LessBuiltin.class, BinaryCompare.LESS_THAN);
        addBinaryCompare(BinaryCompare.LessEqualBuiltin.class, BinaryCompare.LESS_EQUAL);

        add(BinaryLogic.AndBuiltin.class, () -> BinaryBooleanNodeGen.create(BinaryLogic.AND));
        add(BinaryLogic.OrBuiltin.class, () -> BinaryBooleanNodeGen.create(BinaryLogic.OR));

        add(BinaryLogic.NonVectorAndBuiltin.class, () -> BinaryBooleanScalarNodeGen.create(BinaryLogic.NON_VECTOR_AND));
        add(BinaryLogic.NonVectorOrBuiltin.class, () -> BinaryBooleanScalarNodeGen.create(BinaryLogic.NON_VECTOR_OR));

        // Now load the rest of the builtins in "base"
        add(Abbrev.class, AbbrevNodeGen::create);
        add(APerm.class, APermNodeGen::create);
        add(All.class, AllNodeGen::create);
        add(AllNames.class, AllNamesNodeGen::create);
        add(Any.class, AnyNodeGen::create);
        add(AnyNA.class, AnyNANodeGen::create);
        add(Args.class, ArgsNodeGen::create);
        add(Array.class, ArrayNodeGen::create);
        add(AsCall.class, AsCallNodeGen::create);
        add(AsCharacter.class, AsCharacterNodeGen::create);
        add(AsCharacterFactor.class, AsCharacterFactorNodeGen::create);
        add(AsComplex.class, AsComplexNodeGen::create);
        add(AsDouble.class, AsDoubleNodeGen::create);
        add(AsFunction.class, AsFunctionNodeGen::create);
        add(AsInteger.class, AsIntegerNodeGen::create);
        add(AsLogical.class, AsLogicalNodeGen::create);
        add(SetS4Object.class, SetS4ObjectNodeGen::create);
        add(SetTimeLimit.class, SetTimeLimitNodeGen::create);
        add(AsRaw.class, AsRawNodeGen::create);
        add(AsVector.class, AsVectorNodeGen::create);
        add(Assign.class, AssignNodeGen::create);
        add(AttachFunctions.Attach.class, AttachFunctionsFactory.AttachNodeGen::create);
        add(AttachFunctions.Detach.class, AttachFunctionsFactory.DetachNodeGen::create);
        add(Attr.class, AttrNodeGen::create);
        add(Attributes.class, AttributesNodeGen::create);
        add(BaseGammaFunctions.DiGamma.class, BaseGammaFunctionsFactory.DiGammaNodeGen::create);
        add(BaseGammaFunctions.Gamma.class, BaseGammaFunctionsFactory.GammaNodeGen::create);
        add(BaseGammaFunctions.Lgamma.class, BaseGammaFunctionsFactory.LgammaNodeGen::create);
        add(BaseGammaFunctions.TriGamma.class, BaseGammaFunctionsFactory.TriGammaNodeGen::create);
        add(BaseGammaFunctions.TetraGamma.class, BaseGammaFunctionsFactory.TetraGammaNodeGen::create);
        add(BaseGammaFunctions.PentaGamma.class, BaseGammaFunctionsFactory.PentaGammaNodeGen::create);
        add(BaseGammaFunctions.PsiGamma.class, BaseGammaFunctionsFactory.PsiGammaNodeGen::create);
        add(BaseBesselFunctions.BesselI.class, BaseBesselFunctionsFactory.BesselINodeGen::create);
        add(BaseBesselFunctions.BesselJ.class, BaseBesselFunctionsFactory.BesselJNodeGen::create);
        add(BaseBesselFunctions.BesselK.class, BaseBesselFunctionsFactory.BesselKNodeGen::create);
        add(BaseBesselFunctions.BesselY.class, BaseBesselFunctionsFactory.BesselYNodeGen::create);
        add(ChooseFunctions.Choose.class, ChooseFunctionsFactory.ChooseNodeGen::create);
        add(ChooseFunctions.LChoose.class, ChooseFunctionsFactory.LChooseNodeGen::create);
        add(BetaFunctions.LBeta.class, BetaFunctionsFactory.LBetaNodeGen::create);
        add(BetaFunctions.BetaBuiltin.class, BetaFunctionsFactory.BetaBuiltinNodeGen::create);
        add(Bincode.class, BincodeNodeGen::create);
        add(Bind.CbindInternal.class, BindNodeGen.CbindInternalNodeGen::create);
        add(Bind.RbindInternal.class, BindNodeGen.RbindInternalNodeGen::create);
        add(BitwiseFunctions.BitwiseAnd.class, BitwiseFunctionsFactory.BitwiseAndNodeGen::create);
        add(BitwiseFunctions.BitwiseNot.class, BitwiseFunctionsFactory.BitwiseNotNodeGen::create);
        add(BitwiseFunctions.BitwiseOr.class, BitwiseFunctionsFactory.BitwiseOrNodeGen::create);
        add(BitwiseFunctions.BitwiseShiftL.class, BitwiseFunctionsFactory.BitwiseShiftLNodeGen::create);
        add(BitwiseFunctions.BitwiseShiftR.class, BitwiseFunctionsFactory.BitwiseShiftRNodeGen::create);
        add(BitwiseFunctions.BitwiseXor.class, BitwiseFunctionsFactory.BitwiseXorNodeGen::create);
        add(Body.class, BodyNodeGen::create);
        add(BrowserFunctions.BrowserCondition.class, BrowserFunctionsFactory.BrowserConditionNodeGen::create);
        add(BrowserFunctions.BrowserNode.class, BrowserFunctionsFactory.BrowserNodeGen::create);
        add(BrowserFunctions.BrowserSetDebug.class, BrowserFunctionsFactory.BrowserSetDebugNodeGen::create);
        add(BrowserFunctions.BrowserText.class, BrowserFunctionsFactory.BrowserTextNodeGen::create);
        add(Call.class, CallNodeGen::create);
        add(CallAndExternalFunctions.DotCall.class, CallAndExternalFunctionsFactory.DotCallNodeGen::create);
        add(CallAndExternalFunctions.DotCallGraphics.class, CallAndExternalFunctionsFactory.DotCallGraphicsNodeGen::create);
        add(CallAndExternalFunctions.DotExternal.class, CallAndExternalFunctionsFactory.DotExternalNodeGen::create);
        add(CallAndExternalFunctions.DotExternal2.class, CallAndExternalFunctionsFactory.DotExternal2NodeGen::create);
        add(CallAndExternalFunctions.DotExternalGraphics.class, CallAndExternalFunctionsFactory.DotExternalGraphicsNodeGen::create);
        add(Capabilities.class, CapabilitiesNodeGen::create);
        add(Cat.class, CatNodeGen::create);
        add(CharMatch.class, CharMatchNodeGen::create);
        add(Col.class, ColNodeGen::create);
        add(Colon.class, ColonNodeGen::create, Colon::special);
        add(ColMeans.class, ColMeansNodeGen::create);
        add(ColSums.class, ColSumsNodeGen::create);
        add(Combine.class, CombineNodeGen::create);
        add(CommandArgs.class, CommandArgsNodeGen::create);
        add(Comment.class, CommentNodeGen::create);
        add(Complex.class, ComplexNodeGen::create);
        add(CompileFunctions.CompilePKGS.class, CompileFunctionsFactory.CompilePKGSNodeGen::create);
        add(CompileFunctions.EnableJIT.class, CompileFunctionsFactory.EnableJITNodeGen::create);
        add(CompileFunctions.MkCode.class, CompileFunctionsFactory.MkCodeNodeGen::create);
        add(CompileFunctions.BcClose.class, CompileFunctionsFactory.BcCloseNodeGen::create);
        add(CompileFunctions.IsBuiltinInternal.class, CompileFunctionsFactory.IsBuiltinInternalNodeGen::create);
        add(CompileFunctions.Disassemble.class, CompileFunctionsFactory.DisassembleNodeGen::create);
        add(CompileFunctions.BcVersion.class, CompileFunctionsFactory.BcVersionNodeGen::create);
        add(CompileFunctions.LoadFromFile.class, CompileFunctionsFactory.LoadFromFileNodeGen::create);
        add(CompileFunctions.SaveToFile.class, CompileFunctionsFactory.SaveToFileNodeGen::create);
        add(CompileFunctions.Growconst.class, CompileFunctionsFactory.GrowconstNodeGen::create);
        add(CompileFunctions.Putconst.class, CompileFunctionsFactory.PutconstNodeGen::create);
        add(CompileFunctions.Getconst.class, CompileFunctionsFactory.GetconstNodeGen::create);
        add(ConditionFunctions.AddCondHands.class, ConditionFunctionsFactory.AddCondHandsNodeGen::create);
        add(ConditionFunctions.AddRestart.class, ConditionFunctionsFactory.AddRestartNodeGen::create);
        add(ConditionFunctions.DfltStop.class, ConditionFunctionsFactory.DfltStopNodeGen::create);
        add(ConditionFunctions.DfltWarn.class, ConditionFunctionsFactory.DfltWarnNodeGen::create);
        add(ConditionFunctions.GetRestart.class, ConditionFunctionsFactory.GetRestartNodeGen::create);
        add(ConditionFunctions.Geterrmessage.class, ConditionFunctionsFactory.GeterrmessageNodeGen::create);
        add(ConditionFunctions.InvokeRestart.class, ConditionFunctionsFactory.InvokeRestartNodeGen::create);
        add(ConditionFunctions.PrintDeferredWarnings.class, ConditionFunctionsFactory.PrintDeferredWarningsNodeGen::create);
        add(ConditionFunctions.ResetCondHands.class, ConditionFunctionsFactory.ResetCondHandsNodeGen::create);
        add(ConditionFunctions.Seterrmessage.class, ConditionFunctionsFactory.SeterrmessageNodeGen::create);
        add(ConditionFunctions.SignalCondition.class, ConditionFunctionsFactory.SignalConditionNodeGen::create);
        add(ConnectionFunctions.Close.class, ConnectionFunctionsFactory.CloseNodeGen::create);
        add(ConnectionFunctions.File.class, ConnectionFunctionsFactory.FileNodeGen::create);
        add(ConnectionFunctions.Flush.class, ConnectionFunctionsFactory.FlushNodeGen::create);
        add(ConnectionFunctions.GZFile.class, ConnectionFunctionsFactory.GZFileNodeGen::create);
        add(ConnectionFunctions.BZFile.class, ConnectionFunctionsFactory.BZFileNodeGen::create);
        add(ConnectionFunctions.XZFile.class, ConnectionFunctionsFactory.XZFileNodeGen::create);
        add(ConnectionFunctions.GZCon.class, ConnectionFunctionsFactory.GZConNodeGen::create);
        add(ConnectionFunctions.GetAllConnections.class, ConnectionFunctionsFactory.GetAllConnectionsNodeGen::create);
        add(ConnectionFunctions.GetConnection.class, ConnectionFunctionsFactory.GetConnectionNodeGen::create);
        add(ConnectionFunctions.IsOpen.class, ConnectionFunctionsFactory.IsOpenNodeGen::create);
        add(ConnectionFunctions.IsSeekable.class, ConnectionFunctionsFactory.IsSeekableNodeGen::create);
        add(ConnectionFunctions.Open.class, ConnectionFunctionsFactory.OpenNodeGen::create);
        add(ConnectionFunctions.PushBack.class, ConnectionFunctionsFactory.PushBackNodeGen::create);
        add(ConnectionFunctions.PushBackClear.class, ConnectionFunctionsFactory.PushBackClearNodeGen::create);
        add(ConnectionFunctions.PushBackLength.class, ConnectionFunctionsFactory.PushBackLengthNodeGen::create);
        add(ConnectionFunctions.ReadBin.class, ConnectionFunctionsFactory.ReadBinNodeGen::create);
        add(ConnectionFunctions.ReadChar.class, ConnectionFunctionsFactory.ReadCharNodeGen::create);
        add(ConnectionFunctions.ReadLines.class, ConnectionFunctionsFactory.ReadLinesNodeGen::create);
        add(ConnectionFunctions.Seek.class, ConnectionFunctionsFactory.SeekNodeGen::create);
        add(ConnectionFunctions.Truncate.class, ConnectionFunctionsFactory.TruncateNodeGen::create);
        add(ConnectionFunctions.SocketConnection.class, ConnectionFunctionsFactory.SocketConnectionNodeGen::create);
        add(ConnectionFunctions.RawConnection.class, ConnectionFunctionsFactory.RawConnectionNodeGen::create);
        add(ConnectionFunctions.RawConnectionValue.class, ConnectionFunctionsFactory.RawConnectionValueNodeGen::create);
        add(ConnectionFunctions.ChannelConnection.class, ConnectionFunctionsFactory.ChannelConnectionNodeGen::create);
        add(ConnectionFunctions.Fifo.class, ConnectionFunctionsFactory.FifoNodeGen::create);
        add(ConnectionFunctions.Pipe.class, ConnectionFunctionsFactory.PipeNodeGen::create);
        add(ConnectionFunctions.Stderr.class, ConnectionFunctionsFactory.StderrNodeGen::create);
        add(ConnectionFunctions.Stdin.class, ConnectionFunctionsFactory.StdinNodeGen::create);
        add(ConnectionFunctions.Stdout.class, ConnectionFunctionsFactory.StdoutNodeGen::create);
        add(ConnectionFunctions.Summary.class, ConnectionFunctionsFactory.SummaryNodeGen::create);
        add(ConnectionFunctions.TextConnection.class, ConnectionFunctionsFactory.TextConnectionNodeGen::create);
        add(ConnectionFunctions.TextConnectionValue.class, ConnectionFunctionsFactory.TextConnectionValueNodeGen::create);
        add(ConnectionFunctions.URLConnection.class, ConnectionFunctionsFactory.URLConnectionNodeGen::create);
        add(ConnectionFunctions.WriteBin.class, ConnectionFunctionsFactory.WriteBinNodeGen::create);
        add(ConnectionFunctions.WriteChar.class, ConnectionFunctionsFactory.WriteCharNodeGen::create);
        add(ConnectionFunctions.WriteLines.class, ConnectionFunctionsFactory.WriteLinesNodeGen::create);
        add(ConnectionFunctions.IsIncomplete.class, ConnectionFunctionsFactory.IsIncompleteNodeGen::create);
        add(Contributors.class, ContributorsNodeGen::create);
        add(CopyDFAttr.class, CopyDFAttrNodeGen::create);
        add(CrossprodCommon.TCrossprod.class, CrossprodCommon.class, CrossprodCommon::createTCrossprod);
        add(CrossprodCommon.Crossprod.class, CrossprodCommon.class, CrossprodCommon::createCrossprod);
        add(CRC64.class, CRC64NodeGen::create);
        add(CumMax.class, CumMaxNodeGen::create);
        add(CumMin.class, CumMinNodeGen::create);
        add(CumProd.class, CumProdNodeGen::create);
        add(CumSum.class, CumSumNodeGen::create);
        add(MaxCol.class, MaxCol::create);
        add(CacheClass.class, CacheClassNodeGen::create);
        add(CurlDownload.class, CurlDownloadNodeGen::create);
        add(Date.class, DateNodeGen::create);
        add(DatePOSIXFunctions.Date2POSIXlt.class, DatePOSIXFunctionsFactory.Date2POSIXltNodeGen::create);
        add(DatePOSIXFunctions.AsPOSIXct.class, DatePOSIXFunctionsFactory.AsPOSIXctNodeGen::create);
        add(DatePOSIXFunctions.AsPOSIXlt.class, DatePOSIXFunctionsFactory.AsPOSIXltNodeGen::create);
        add(DatePOSIXFunctions.FormatPOSIXlt.class, DatePOSIXFunctionsFactory.FormatPOSIXltNodeGen::create);
        add(DatePOSIXFunctions.POSIXlt2Date.class, DatePOSIXFunctionsFactory.POSIXlt2DateNodeGen::create);
        add(DatePOSIXFunctions.StrPTime.class, DatePOSIXFunctionsFactory.StrPTimeNodeGen::create);
        add(DebugFunctions.Debug.class, DebugFunctionsFactory.DebugNodeGen::create);
        add(DebugFunctions.DebugOnce.class, DebugFunctionsFactory.DebugOnceNodeGen::create);
        add(DebugFunctions.IsDebugged.class, DebugFunctionsFactory.IsDebuggedNodeGen::create);
        add(DebugFunctions.UnDebug.class, DebugFunctionsFactory.UnDebugNodeGen::create);
        add(DelayedAssign.class, DelayedAssignNodeGen::create);
        add(Deparse.class, DeparseNodeGen::create);
        add(Diag.class, DiagNodeGen::create);
        add(Dim.class, DimNodeGen::create);
        add(DimNames.class, DimNamesNodeGen::create);
        add(DoCall.class, DoCallNodeGen::create);
        add(DPut.class, DPutNodeGen::create);
        add(Dump.class, DumpNodeGen::create);
        add(Drop.class, DropNodeGen::create);
        add(DuplicatedFunctions.AnyDuplicated.class, DuplicatedFunctionsFactory.AnyDuplicatedNodeGen::create);
        add(DuplicatedFunctions.Duplicated.class, DuplicatedFunctionsFactory.DuplicatedNodeGen::create);
        add(DynLoadFunctions.DynLoad.class, DynLoadFunctionsFactory.DynLoadNodeGen::create);
        add(DynLoadFunctions.DynUnload.class, DynLoadFunctionsFactory.DynUnloadNodeGen::create);
        add(DynLoadFunctions.GetLoadedDLLs.class, DynLoadFunctionsFactory.GetLoadedDLLsNodeGen::create);
        add(DynLoadFunctions.GetSymbolInfo.class, DynLoadFunctionsFactory.GetSymbolInfoNodeGen::create);
        add(DynLoadFunctions.IsLoaded.class, DynLoadFunctionsFactory.IsLoadedNodeGen::create);
        add(VersionFunctions.ExtSoftVersion.class, VersionFunctionsFactory.ExtSoftVersionNodeGen::create);
        add(EApply.class, EApplyNodeGen::create);
        add(EncodeString.class, EncodeStringNodeGen::create);
        add(EncodingFunctions.Encoding.class, EncodingFunctionsFactory.EncodingNodeGen::create);
        add(EncodingFunctions.SetEncoding.class, EncodingFunctionsFactory.SetEncodingNodeGen::create);
        add(EnvFunctions.AsEnvironment.class, EnvFunctionsFactory.AsEnvironmentNodeGen::create);
        add(EnvFunctions.BaseEnv.class, EnvFunctionsFactory.BaseEnvNodeGen::create);
        add(EnvFunctions.BindingIsActive.class, EnvFunctionsFactory.BindingIsActiveNodeGen::create);
        add(EnvFunctions.BindingIsLocked.class, EnvFunctionsFactory.BindingIsLockedNodeGen::create);
        add(EnvFunctions.EmptyEnv.class, EnvFunctionsFactory.EmptyEnvNodeGen::create);
        add(EnvFunctions.EnvToList.class, EnvFunctionsFactory.EnvToListNodeGen::create);
        add(EnvFunctions.Environment.class, EnvFunctionsFactory.EnvironmentNodeGen::create);
        add(EnvFunctions.EnvironmentIsLocked.class, EnvFunctionsFactory.EnvironmentIsLockedNodeGen::create);
        add(EnvFunctions.EnvironmentName.class, EnvFunctionsFactory.EnvironmentNameNodeGen::create);
        add(EnvFunctions.GlobalEnv.class, EnvFunctionsFactory.GlobalEnvNodeGen::create);
        add(EnvFunctions.IsEnvironment.class, EnvFunctionsFactory.IsEnvironmentNodeGen::create);
        add(EnvFunctions.LockBinding.class, EnvFunctionsFactory.LockBindingNodeGen::create);
        add(EnvFunctions.LockEnvironment.class, EnvFunctionsFactory.LockEnvironmentNodeGen::create);
        add(EnvFunctions.MakeActiveBinding.class, EnvFunctionsFactory.MakeActiveBindingNodeGen::create);
        add(EnvFunctions.NewEnv.class, EnvFunctionsFactory.NewEnvNodeGen::create);
        add(EnvFunctions.ParentEnv.class, EnvFunctionsFactory.ParentEnvNodeGen::create);
        add(EnvFunctions.TopEnv.class, EnvFunctionsFactory.TopEnvNodeGen::create);
        add(EnvFunctions.Search.class, EnvFunctionsFactory.SearchNodeGen::create);
        add(EnvFunctions.SetParentEnv.class, EnvFunctionsFactory.SetParentEnvNodeGen::create);
        add(EnvFunctions.UnlockBinding.class, EnvFunctionsFactory.UnlockBindingNodeGen::create);
        add(Eval.class, EvalNodeGen::create);
        add(RecordGraphics.class, RecordGraphics::create);
        add(WithVisible.class, WithVisibleNodeGen::create, WithVisible::createSpecial);
        add(Exists.class, ExistsNodeGen::create);
        add(Expression.class, ExpressionNodeGen::create);
        add(FastRGetExecutor.class, FastRGetExecutor::new);
        add(FastRContext.R.class, FastRContextFactory.RNodeGen::create);
        add(FastRContext.Rscript.class, FastRContextFactory.RscriptNodeGen::create);
        add(FastRContext.CloseChannel.class, FastRContextFactory.CloseChannelNodeGen::create);
        add(FastRContext.CreateChannel.class, FastRContextFactory.CreateChannelNodeGen::create);
        add(FastRContext.CreateForkChannel.class, FastRContextFactory.CreateForkChannelNodeGen::create);
        add(FastRContext.Eval.class, FastRContextFactory.EvalNodeGen::create);
        add(FastRContext.Get.class, FastRContext.Get::new);
        add(FastRContext.FastRContextNew.class, FastRContext.FastRContextNew::new);
        add(FastRContext.FastRContextClose.class, FastRContext.FastRContextClose::new);
        add(FastRContext.GetChannel.class, FastRContextFactory.GetChannelNodeGen::create);
        add(FastRContext.ChannelPoll.class, FastRContextFactory.ChannelPollNodeGen::create);
        add(FastRContext.ChannelReceive.class, FastRContextFactory.ChannelReceiveNodeGen::create);
        add(FastRContext.ChannelSelect.class, FastRContextFactory.ChannelSelectNodeGen::create);
        add(FastRContext.ChannelSend.class, FastRContextFactory.ChannelSendNodeGen::create);
        add(FastRContext.Spawn.class, FastRContextFactory.SpawnNodeGen::create);
        add(FastRContext.Interrupt.class, FastRContextFactory.InterruptNodeGen::create);
        add(FastRContext.Join.class, FastRContextFactory.JoinNodeGen::create);
        add(FastRRegisterFunctions.class, FastRRegisterFunctionsNodeGen::create);
        add(FastrDqrls.class, FastrDqrlsNodeGen::create);
        add(FastRDebug.class, FastRDebugNodeGen::create);
        add(FastRPatchPackage.class, FastRPatchPackageNodeGen::create);
        add(FastRDispatchNativeHandlers.class, FastRDispatchNativeHandlers::new);
        add(FastRInitEventLoop.class, FastRInitEventLoopNodeGen::create);
        add(FastRJavaGDResize.class, FastRJavaGDResize::new);
        add(FastRSetBreakpoint.class, FastRSetBreakpointNodeGen::create);
        add(FastRAddHelpPath.class, FastRAddHelpPath::create);
        add(FastRHelpPath.class, FastRHelpPathNodeGen::create);
        add(FastRHelpRd.class, FastRHelpRdNodeGen::create);
        add(FastRIdentity.class, FastRIdentityNodeGen::create);
        add(FastROptionBuiltin.class, FastROptionBuiltin::create);
        add(FastRTestsTry.class, FastRTestsTryNodeGen::create);
        add(FastRInteropTry.class, FastRInteropTryNodeGen::create);
        add(FastRInteropCheckException.class, FastRInteropCheckExceptionNodeGen::create);
        add(FastRInteropGetException.class, FastRInteropGetExceptionNodeGen::create);
        add(FastRInteropClearException.class, FastRInteropClearExceptionNodeGen::create);
        add(FastRInspect.class, FastRInspectNodeGen::create);
        add(FastRInspectFrame.class, FastRInspectFrameNodeGen::create);
        add(FastRInterop.Eval.class, FastRInteropFactory.EvalNodeGen::create);
        add(FastRInterop.Export.class, FastRInteropFactory.ExportNodeGen::create);
        add(FastRInterop.Import.class, FastRInteropFactory.ImportNodeGen::create);
        add(FastRInterop.InteropNew.class, FastRInteropFactory.InteropNewNodeGen::create);
        add(FastRInterop.IsExternal.class, FastRInteropFactory.IsExternalNodeGen::create);
        add(FastRInterop.JavaType.class, FastRInteropFactory.JavaTypeNodeGen::create);
        add(FastRInterop.JavaAddToClasspath.class, FastRInteropFactory.JavaAddToClasspathNodeGen::create);
        add(FastRInterop.JavaIsIdentical.class, FastRInteropFactory.JavaIsIdenticalNodeGen::create);
        add(FastRInterop.JavaIsAssignableFrom.class, FastRInteropFactory.JavaIsAssignableFromNodeGen::create);
        add(FastRInterop.JavaIsInstance.class, FastRInteropFactory.JavaIsInstanceNodeGen::create);
        add(FastRInterop.JavaAsTruffleObject.class, FastRInteropFactory.JavaAsTruffleObjectNodeGen::create);
        add(FastRInterop.ToJavaArray.class, FastRInteropFactory.ToJavaArrayNodeGen::create);
        add(FastRInterop.AsVector.class, FastRInteropFactory.AsVectorNodeGen::create);
        add(FastRInterop.ToByte.class, FastRInteropFactory.ToByteNodeGen::create);
        add(FastRInterop.ToChar.class, FastRInteropFactory.ToCharNodeGen::create);
        add(FastRInterop.ToFloat.class, FastRInteropFactory.ToFloatNodeGen::create);
        add(FastRInterop.ToLong.class, FastRInteropFactory.ToLongNodeGen::create);
        add(FastRInterop.ToShort.class, FastRInteropFactory.ToShortNodeGen::create);
        add(FastRRefCountInfo.class, FastRRefCountInfoNodeGen::create);
        add(FastRPkgSource.class, FastRPkgSourceNodeGen::create);
        add(FastRPrintError.class, FastRPrintErrorNodeGen::create);
        add(FastRRCallerTrace.class, FastRRCallerTrace::create);
        add(FastRSourceInfo.class, FastRSourceInfoNodeGen::create);
        add(FastRSetConsoleHandler.class, FastRSetConsoleHandlerNodeGen::create);
        add(FastRSetToolchain.class, FastRSetToolchainNodeGen::create);
        add(FastRStackTrace.class, FastRStackTraceNodeGen::create);
        add(FastRSlotAssign.class, FastRSlotAssignNodeGen::create);
        add(FastRSyntaxTree.class, FastRSyntaxTreeNodeGen::create);
        add(FastRGDSetGraphics.class, FastRGDSetGraphicsNodeGen::create);
        add(FastRSVGGetContent.class, FastRSVGGetContentNodeGen::create);
        add(FastRSVGFileName.class, FastRSVGFileNameNodeGen::create);
        add(FastRSVGSetFileName.class, FastRSVGSetFileNameNodeGen::create);
        add(FastRSavePlot.class, FastRSavePlotNodeGen::create);
        add(FastRThrowIt.class, FastRThrowItNodeGen::create);
        add(FastRThrowCompilerError.class, FastRThrowCompilerError::new);
        add(FastRTrace.Trace.class, FastRTraceFactory.TraceNodeGen::create);
        add(FastRTrace.Untrace.class, FastRTraceFactory.UntraceNodeGen::create);
        add(FastRTree.class, FastRTreeNodeGen::create);
        add(FastRTreeStats.class, FastRTreeStatsNodeGen::create);
        add(FastRUseDebugMakevars.class, FastRUseDebugMakevarsNodeGen::create);
        add(FastRprofmem.class, FastRprofmemNodeGen::create);
        add(FastRprofmemShow.class, FastRprofmemShowNodeGen::create);
        add(FastRprofmemSource.class, FastRprofmemSourceNodeGen::create);
        add(FastRprofmemSnapshot.class, FastRprofmemSnapshotNodeGen::create);
        add(FastRLibPaths.class, FastRLibPathsNodeGen::create);
        add(FileFunctions.BaseName.class, FileFunctionsFactory.BaseNameNodeGen::create);
        add(FileFunctions.DirCreate.class, FileFunctionsFactory.DirCreateNodeGen::create);
        add(FileFunctions.DirExists.class, FileFunctionsFactory.DirExistsNodeGen::create);
        add(FileFunctions.DirName.class, FileFunctionsFactory.DirNameNodeGen::create);
        add(FileFunctions.FileAccess.class, FileFunctionsFactory.FileAccessNodeGen::create);
        add(FileFunctions.FileAppend.class, FileFunctionsFactory.FileAppendNodeGen::create);
        add(FileFunctions.FileCopy.class, FileFunctionsFactory.FileCopyNodeGen::create);
        add(FileFunctions.FileCreate.class, FileFunctionsFactory.FileCreateNodeGen::create);
        add(FileFunctions.FileExists.class, FileFunctionsFactory.FileExistsNodeGen::create);
        add(FileFunctions.FileInfo.class, FileFunctionsFactory.FileInfoNodeGen::create);
        add(FileFunctions.FileLink.class, FileFunctionsFactory.FileLinkNodeGen::create);
        add(FileFunctions.FilePath.class, FileFunctionsFactory.FilePathNodeGen::create);
        add(FileFunctions.FileRemove.class, FileFunctionsFactory.FileRemoveNodeGen::create);
        add(FileFunctions.FileRename.class, FileFunctionsFactory.FileRenameNodeGen::create);
        add(FileFunctions.FileShow.class, FileFunctionsFactory.FileShowNodeGen::create);
        add(FileFunctions.FileSymLink.class, FileFunctionsFactory.FileSymLinkNodeGen::create);
        add(FileFunctions.ListFiles.class, FileFunctionsFactory.ListFilesNodeGen::create);
        add(FileFunctions.ListDirs.class, FileFunctionsFactory.ListDirsNodeGen::create);
        add(FileFunctions.Unlink.class, FileFunctionsFactory.UnlinkNodeGen::create);
        add(FindInterval.class, FindIntervalNodeGen::create);
        add(ForceAndCall.class, ForceAndCallNodeGen::create);
        add(Formals.class, FormalsNodeGen::create);
        add(Format.class, FormatNodeGen::create);
        add(FormatC.class, FormatCNodeGen::create);
        add(FormatInfo.class, FormatInfoNodeGen::create);
        add(FortranAndCFunctions.DotC.class, FortranAndCFunctionsFactory.DotCNodeGen::create);
        add(DotFortran.class, FortranAndCFunctionsFactory.DotFortranNodeGen::create);
        add(FrameFunctions.MatchCall.class, FrameFunctionsFactory.MatchCallNodeGen::create);
        add(FrameFunctions.ParentFrame.class, FrameFunctionsFactory.ParentFrameNodeGen::create);
        add(PosToEnv.class, PosToEnv::create);
        add(FrameFunctions.SysCall.class, FrameFunctionsFactory.SysCallNodeGen::create);
        add(FrameFunctions.SysCalls.class, FrameFunctionsFactory.SysCallsNodeGen::create);
        add(FrameFunctions.SysFrame.class, FrameFunctionsFactory.SysFrameNodeGen::create);
        add(FrameFunctions.SysFrames.class, FrameFunctionsFactory.SysFramesNodeGen::create);
        add(FrameFunctions.SysFunction.class, FrameFunctionsFactory.SysFunctionNodeGen::create);
        add(FrameFunctions.SysNFrame.class, FrameFunctionsFactory.SysNFrameNodeGen::create);
        add(FrameFunctions.SysParent.class, FrameFunctionsFactory.SysParentNodeGen::create);
        add(FrameFunctions.SysParents.class, FrameFunctionsFactory.SysParentsNodeGen::create);
        add(GcFunctions.Gc.class, GcFunctionsFactory.GcNodeGen::create);
        add(GcFunctions.Gctorture.class, GcFunctionsFactory.GctortureNodeGen::create);
        add(GcFunctions.Gctorture2.class, GcFunctionsFactory.Gctorture2NodeGen::create);
        add(GetClass.class, GetClassNodeGen::create);
        add(GetFunctions.Get.class, GetFunctionsFactory.GetNodeGen::create);
        add(GetFunctions.Get0.class, GetFunctionsFactory.Get0NodeGen::create);
        add(GetFunctions.MGet.class, GetFunctionsFactory.MGetNodeGen::create);
        add(OptionsFunctions.GetOption.class, OptionsFunctionsFactory.GetOptionNodeGen::create);
        add(GetText.class, GetTextNodeGen::create);
        add(Getwd.class, GetwdNodeGen::create);
        add(GrepFunctions.AGrep.class, GrepFunctionsFactory.AGrepNodeGen::create);
        add(GrepFunctions.AGrepL.class, GrepFunctionsFactory.AGrepLNodeGen::create);
        add(GrepFunctions.GSub.class, GrepFunctionsFactory.GSubNodeGen::create);
        add(GrepFunctions.Gregexpr.class, GrepFunctionsFactory.GregexprNodeGen::create);
        add(GrepFunctions.Grep.class, GrepFunctionsFactory.GrepNodeGen::create);
        add(GrepFunctions.GrepL.class, GrepFunctionsFactory.GrepLNodeGen::create);
        add(GrepFunctions.Regexpr.class, GrepFunctionsFactory.RegexprNodeGen::create);
        add(GrepFunctions.Regexec.class, GrepFunctionsFactory.RegexecNodeGen::create);
        add(GrepFunctions.Strsplit.class, GrepFunctionsFactory.StrsplitNodeGen::create);
        add(GrepFunctions.Sub.class, GrepFunctionsFactory.SubNodeGen::create);
        add(GrepFunctions.GrepRaw.class, GrepFunctionsFactory.GrepRawNodeGen::create);
        add(HiddenInternalFunctions.GetRegisteredRoutines.class, HiddenInternalFunctionsFactory.GetRegisteredRoutinesNodeGen::create);
        add(HiddenInternalFunctions.ImportIntoEnv.class, HiddenInternalFunctionsFactory.ImportIntoEnvNodeGen::create);
        add(HiddenInternalFunctions.LazyLoadDBFetch.class, HiddenInternalFunctionsFactory.LazyLoadDBFetchNodeGen::create);
        add(HiddenInternalFunctions.LazyLoadDBFlush.class, HiddenInternalFunctionsFactory.LazyLoadDBFlushNodeGen::create);
        add(HiddenInternalFunctions.MakeLazy.class, HiddenInternalFunctionsFactory.MakeLazyNodeGen::create);
        add(HiddenInternalFunctions.GetVarsFromFrame.class, HiddenInternalFunctionsFactory.GetVarsFromFrameNodeGen::create);
        add(HiddenInternalFunctions.LazyLoadDBinsertValue.class, HiddenInternalFunctionsFactory.LazyLoadDBinsertValueNodeGen::create);
        add(IConv.class, IConvNodeGen::create);
        add(Identical.class, Identical::create);
        add(InheritsBuiltin.class, InheritsBuiltinNodeGen::create);
        add(Inspect.class, InspectNodeGen::create);
        add(Interactive.class, InteractiveNodeGen::create);
        add(Internal.class, InternalNodeGen::create);
        add(InternalsID.class, InternalsIDNodeGen::create);
        add(IntToBits.class, IntToBitsNodeGen::create);
        add(IntToUtf8.class, IntToUtf8NodeGen::create);
        add(Invisible.class, InvisibleNodeGen::create);
        add(IsATTY.class, IsATTYNodeGen::create);
        add(IsFiniteFunctions.IsFinite.class, IsFiniteFunctionsFactory.IsFiniteNodeGen::create);
        add(IsFiniteFunctions.IsInfinite.class, IsFiniteFunctionsFactory.IsInfiniteNodeGen::create);
        add(IsFiniteFunctions.IsNaN.class, IsFiniteFunctionsFactory.IsNaNNodeGen::create);
        add(IsListFactor.class, IsListFactorNodeGen::create);
        add(IsMethodsDispatchOn.class, IsMethodsDispatchOnNodeGen::create);
        add(IsNA.class, IsNANodeGen::create);
        add(IsS4.class, IsS4NodeGen::create);
        add(IsTypeFunctions.IsArray.class, IsTypeFunctionsFactory.IsArrayNodeGen::create);
        add(IsTypeFunctions.IsAtomic.class, IsTypeFunctionsFactory.IsAtomicNodeGen::create);
        add(IsTypeFunctions.IsCall.class, IsTypeFunctionsFactory.IsCallNodeGen::create);
        add(IsTypeFunctions.IsCharacter.class, IsTypeFunctionsFactory.IsCharacterNodeGen::create);
        add(IsTypeFunctions.IsComplex.class, IsTypeFunctionsFactory.IsComplexNodeGen::create);
        add(IsTypeFunctions.IsDouble.class, IsTypeFunctionsFactory.IsDoubleNodeGen::create);
        add(IsTypeFunctions.IsExpression.class, IsTypeFunctionsFactory.IsExpressionNodeGen::create);
        add(IsTypeFunctions.IsFunction.class, IsTypeFunctionsFactory.IsFunctionNodeGen::create);
        add(IsTypeFunctions.IsInteger.class, IsTypeFunctionsFactory.IsIntegerNodeGen::create);
        add(IsTypeFunctions.IsLanguage.class, IsTypeFunctionsFactory.IsLanguageNodeGen::create);
        add(IsTypeFunctions.IsList.class, IsTypeFunctionsFactory.IsListNodeGen::create);
        add(IsTypeFunctions.IsLogical.class, IsTypeFunctionsFactory.IsLogicalNodeGen::create);
        add(IsTypeFunctions.IsMatrix.class, IsTypeFunctionsFactory.IsMatrixNodeGen::create);
        add(IsTypeFunctions.IsName.class, IsTypeFunctionsFactory.IsNameNodeGen::create);
        add(IsTypeFunctions.IsNull.class, IsTypeFunctionsFactory.IsNullNodeGen::create);
        add(IsTypeFunctions.IsNumeric.class, IsTypeFunctionsFactory.IsNumericNodeGen::create);
        add(IsTypeFunctions.IsObject.class, IsTypeFunctionsFactory.IsObjectNodeGen::create);
        add(IsTypeFunctions.IsPairList.class, IsTypeFunctionsFactory.IsPairListNodeGen::create);
        add(IsTypeFunctions.IsRaw.class, IsTypeFunctionsFactory.IsRawNodeGen::create);
        add(IsTypeFunctions.IsRecursive.class, IsTypeFunctionsFactory.IsRecursiveNodeGen::create);
        add(IsTypeFunctions.IsVector.class, IsTypeFunctionsFactory.IsVectorNodeGen::create);
        add(IsUnsorted.class, IsUnsortedNodeGen::create);
        add(SortedFastPass.class, SortedFastPass::create);
        add(WrapMeta.class, WrapMeta::create);
        add(DotDotDotLength.class, DotDotDotLength::create);
        add(DotDotDotElt.class, DotDotDotElt::create);
        add(ValidUTF8.class, ValidUTF8NodeGen::create);
        add(LaFunctions.DetGeReal.class, LaFunctionsFactory.DetGeRealNodeGen::create);
        add(LaFunctions.LaChol.class, LaFunctionsFactory.LaCholNodeGen::create);
        add(LaFunctions.LaChol2Inv.class, LaFunctionsFactory.LaChol2InvNodeGen::create);
        add(LaFunctions.Qr.class, LaFunctionsFactory.QrNodeGen::create);
        add(LaFunctions.QrCoefReal.class, LaFunctionsFactory.QrCoefRealNodeGen::create);
        add(LaFunctions.QrCoefCmplx.class, LaFunctionsFactory.QrCoefCmplxNodeGen::create);
        add(LaFunctions.Rg.class, LaFunctionsFactory.RgNodeGen::create);
        add(LaFunctions.Rs.class, LaFunctionsFactory.RsNodeGen::create);
        add(LaFunctions.Version.class, LaFunctionsFactory.VersionNodeGen::create);
        add(LaFunctions.LaSolve.class, LaFunctionsFactory.LaSolveNodeGen::create);
        add(LaFunctions.Svd.class, LaFunctionsFactory.SvdNodeGen::create);
        add(LaFunctions.LaLibrary.class, LaFunctionsFactory.LaLibraryNodeGen::create);
        add(LaFunctions.Backsolve.class, LaFunctionsFactory.BacksolveNodeGen::create);
        add(Lapply.class, LapplyNodeGen::create);
        add(Rapply.class, RapplyNodeGen::create);
        add(Length.class, LengthNodeGen::create);
        add(Lengths.class, LengthsNodeGen::create);
        add(License.class, LicenseNodeGen::create);
        add(ListBuiltin.class, ListBuiltinNodeGen::create);
        add(List2Env.class, List2EnvNodeGen::create);
        add(LoadSaveFunctions.Load.class, LoadSaveFunctionsFactory.LoadNodeGen::create);
        add(LoadSaveFunctions.LoadFromConn2.class, LoadSaveFunctionsFactory.LoadFromConn2NodeGen::create);
        add(LoadSaveFunctions.LoadInfoFromConn2.class, LoadSaveFunctionsFactory.LoadInfoFromConn2NodeGen::create);
        add(LoadSaveFunctions.SaveToConn.class, LoadSaveFunctionsFactory.SaveToConnNodeGen::create);
        add(LocaleFunctions.BindTextDomain.class, LocaleFunctionsFactory.BindTextDomainNodeGen::create);
        add(LocaleFunctions.Enc2Native.class, LocaleFunctionsFactory.Enc2NativeNodeGen::create);
        add(LocaleFunctions.Enc2Utf8.class, LocaleFunctionsFactory.Enc2Utf8NodeGen::create);
        add(LocaleFunctions.GetLocale.class, LocaleFunctionsFactory.GetLocaleNodeGen::create);
        add(LocaleFunctions.L10nInfo.class, LocaleFunctionsFactory.L10nInfoNodeGen::create);
        add(LocaleFunctions.LocaleConv.class, LocaleFunctionsFactory.LocaleConvNodeGen::create);
        add(LocaleFunctions.SetLocale.class, LocaleFunctionsFactory.SetLocaleNodeGen::create);
        add(LogFunctions.Log.class, LogFunctionsFactory.LogNodeGen::create);
        add(Ls.class, LsNodeGen::create);
        add(MakeNames.class, MakeNamesNodeGen::create);
        add(MakeUnique.class, MakeUniqueNodeGen::create);
        add(Mapply.class, MapplyNodeGen::create);
        add(MatMult.class, MatMult::create);
        add(Match.class, MatchNodeGen::create);
        add(MatchFun.class, MatchFunNodeGen::create);
        add(Matrix.class, MatrixNodeGen::create);
        add(Max.class, MaxNodeGen::create);
        add(Mean.class, MeanNodeGen::create);
        add(Merge.class, MergeNodeGen::create);
        add(Min.class, MinNodeGen::create);
        add(Missing.class, MissingNodeGen::create);
        add(NArgs.class, NArgsNodeGen::create);
        add(NChar.class, NCharNodeGen::create);
        add(NGetText.class, NGetTextNodeGen::create);
        add(NZChar.class, NZCharNodeGen::create);
        add(Names.class, NamesNodeGen::create);
        add(NamespaceFunctions.GetNamespaceRegistry.class, NamespaceFunctionsFactory.GetNamespaceRegistryNodeGen::create);
        add(NamespaceFunctions.GetRegisteredNamespace.class, NamespaceFunctionsFactory.GetRegisteredNamespaceNodeGen::create);
        add(NamespaceFunctions.IsNamespaceEnv.class, NamespaceFunctionsFactory.IsNamespaceEnvNodeGen::create);
        add(NamespaceFunctions.IsRegisteredNamespace.class, NamespaceFunctionsFactory.IsRegisteredNamespaceNodeGen::create);
        add(NamespaceFunctions.RegisterNamespace.class, NamespaceFunctionsFactory.RegisterNamespaceNodeGen::create);
        add(NamespaceFunctions.UnregisterNamespace.class, NamespaceFunctionsFactory.UnregisterNamespaceNodeGen::create);
        add(NormalizePath.class, NormalizePathNodeGen::create);
        add(OldClass.class, OldClassNodeGen::create);
        add(OnExit.class, OnExitNodeGen::create);
        add(OptionsFunctions.Options.class, OptionsFunctionsFactory.OptionsNodeGen::create);
        add(Order.class, OrderNodeGen::create);
        add(PCREConfig.class, PCREConfigNodeGen::create);
        add(PMatch.class, PMatchNodeGen::create);
        add(PMinMax.PMax.class, PMinMaxNodeGen.PMaxNodeGen::create);
        add(PMinMax.PMin.class, PMinMaxNodeGen.PMinNodeGen::create);
        add(PackBits.class, PackBitsNodeGen::create);
        add(Parse.class, ParseNodeGen::create);
        add(Paste.class, PasteNodeGen::create);
        add(Paste0.class, Paste0NodeGen::create);
        add(PathExpand.class, PathExpandNodeGen::create);
        add(Polyroot.class, PolyrootNodeGen::create);
        add(Pretty.class, PrettyNodeGen::create);
        add(Primitive.class, PrimitiveNodeGen::create);
        add(PrintFunctions.PrintDefault.class, PrintFunctionsFactory.PrintDefaultNodeGen::create);
        add(ProcTime.class, ProcTimeNodeGen::create);
        add(Prod.class, ProdNodeGen::create);
        add(Quit.class, QuitNodeGen::create);
        add(Quote.class, QuoteNodeGen::create);
        add(Range.class, RangeNodeGen::create);
        add(Rank.class, RankNodeGen::create);
        add(RNGFunctions.RNGkind.class, RNGFunctionsFactory.RNGkindNodeGen::create);
        add(RNGFunctions.SetSeed.class, RNGFunctionsFactory.SetSeedNodeGen::create);
        add(RNGFunctions.FastRSetSeed.class, RNGFunctionsFactory.FastRSetSeedNodeGen::create);
        add(RVersion.class, VersionFunctionsFactory.RVersionNodeGen::create);
        add(RawFunctions.CharToRaw.class, RawFunctionsFactory.CharToRawNodeGen::create);
        add(RawFunctions.RawToChar.class, RawFunctionsFactory.RawToCharNodeGen::create);
        add(RawFunctions.RawShift.class, RawFunctionsFactory.RawShiftNodeGen::create);
        add(RawToBits.class, RawToBitsNodeGen::create);
        add(ReadDCF.class, ReadDCFNodeGen::create);
        add(ReadREnviron.class, ReadREnvironNodeGen::create);
        add(Readline.class, ReadlineNodeGen::create);
        add(Recall.class, RecallNodeGen::create);
        add(RegFinalizer.class, RegFinalizerNodeGen::create);
        add(Repeat.class, RepeatNodeGen::create);
        add(RepeatInternal.class, RepeatInternalNodeGen::create);
        add(RepeatLength.class, RepeatLengthNodeGen::create);
        add(Return.class, ReturnNodeGen::create, Return::createSpecial);
        add(Rhome.class, RhomeNodeGen::create);
        add(Rm.class, RmNodeGen::create);
        add(Round.class, RoundNodeGen::create);
        add(Row.class, RowNodeGen::create);
        add(RowMeans.class, RowMeansNodeGen::create);
        add(RowSums.class, RowSumsNodeGen::create);
        add(RowsumFunctions.Rowsum.class, RowsumFunctionsFactory.RowsumNodeGen::create);
        add(S3DispatchFunctions.NextMethod.class, S3DispatchFunctionsFactory.NextMethodNodeGen::create);
        add(S3DispatchFunctions.UseMethod.class, S3DispatchFunctionsFactory.UseMethodNodeGen::create);
        add(Sample.class, SampleNodeGen::create);
        add(Sample2.class, Sample2NodeGen::create);
        add(Scan.class, ScanNodeGen::create);
        add(SeqFunctions.SeqInt.class, SeqFunctionsFactory.SeqIntNodeGen::create);
        add(SeqFunctions.SeqAlong.class, SeqFunctionsFactory.SeqAlongNodeGen::create);
        add(SeqFunctions.SeqLen.class, SeqFunctionsFactory.SeqLenNodeGen::create);
        add(SerializeFunctions.Serialize.class, SerializeFunctionsFactory.SerializeNodeGen::create);
        add(SerializeFunctions.SerializeB.class, SerializeFunctionsFactory.SerializeBNodeGen::create);
        add(SerializeFunctions.SerializeToConn.class, SerializeFunctionsFactory.SerializeToConnNodeGen::create);
        add(SerializeFunctions.Unserialize.class, SerializeFunctionsFactory.UnserializeNodeGen::create);
        add(SerializeFunctions.UnserializeFromConn.class, SerializeFunctionsFactory.UnserializeFromConnNodeGen::create);
        add(SerializeFunctions.SerializeInfoFromConn.class, SerializeFunctionsFactory.SerializeInfoFromConnNodeGen::create);
        add(Setwd.class, SetwdNodeGen::create);
        add(ShortRowNames.class, ShortRowNamesNodeGen::create);
        add(Signif.class, SignifNodeGen::create);
        add(SinkFunctions.Sink.class, SinkFunctionsFactory.SinkNodeGen::create);
        add(SinkFunctions.SinkNumber.class, SinkFunctionsFactory.SinkNumberNodeGen::create);
        add(Slot.class, SlotNodeGen::create);
        add(SockSelect.class, SockSelectNodeGen::create);
        add(SortFunctions.PartialSort.class, SortFunctionsFactory.PartialSortNodeGen::create);
        add(SortFunctions.QSort.class, SortFunctionsFactory.QSortNodeGen::create);
        add(SortFunctions.RadixSort.class, SortFunctionsFactory.RadixSortNodeGen::create);
        add(SortFunctions.Sort.class, SortFunctionsFactory.SortNodeGen::create);
        add(Split.class, SplitNodeGen::create);
        add(Sprintf.class, SprintfNodeGen::create);
        add(StandardGeneric.class, StandardGenericNodeGen::create);
        add(StartsEndsWithFunctions.StartsWith.class, StartsEndsWithFunctionsFactory.StartsWithNodeGen::create);
        add(StartsEndsWithFunctions.EndsWith.class, StartsEndsWithFunctionsFactory.EndsWithNodeGen::create);
        add(Stop.class, StopNodeGen::create);
        add(Str2Expression.class, Str2ExpressionNodeGen::create);
        add(Str2Lang.class, Str2LangNodeGen::create);
        add(Strtoi.class, StrtoiNodeGen::create);
        add(Strrep.class, StrrepNodeGen::create);
        add(Strtrim.class, StrtrimNodeGen::create);
        add(Substitute.class, SubstituteNodeGen::create);
        add(Substr.class, SubstrNodeGen::create);
        add(Sum.class, SumNodeGen::create);
        add(Switch.class, SwitchNodeGen::create);
        add(SysFunctions.SysChmod.class, SysFunctionsFactory.SysChmodNodeGen::create);
        add(SysFunctions.SysGetenv.class, SysFunctionsFactory.SysGetenvNodeGen::create);
        add(SysFunctions.SysGetpid.class, SysFunctionsFactory.SysGetpidNodeGen::create);
        add(SysFunctions.SysGlob.class, SysFunctionsFactory.SysGlobNodeGen::create);
        add(SysFunctions.SysInfo.class, SysFunctionsFactory.SysInfoNodeGen::create);
        add(SysFunctions.SysReadlink.class, SysFunctionsFactory.SysReadlinkNodeGen::create);
        add(SysFunctions.SysSetEnv.class, SysFunctionsFactory.SysSetEnvNodeGen::create);
        add(SysFunctions.SysSetFileTime.class, SysFunctionsFactory.SysSetFileTimeNodeGen::create);
        add(SysFunctions.SysSleep.class, SysFunctionsFactory.SysSleepNodeGen::create);
        add(SysFunctions.SysTime.class, SysFunctionsFactory.SysTimeNodeGen::create);
        add(SysFunctions.SysUmask.class, SysFunctionsFactory.SysUmaskNodeGen::create);
        add(SysFunctions.SysUnSetEnv.class, SysFunctionsFactory.SysUnSetEnvNodeGen::create);
        add(SystemFunction.class, SystemFunctionNodeGen::create);
        add(Tabulate.class, TabulateNodeGen::create);
        add(TempDir.class, TempDirNodeGen::create);
        add(TempFile.class, TempFileNodeGen::create);
        add(CharTr.class, CharTr::create);
        add(ToLowerOrUpper.ToLower.class, ToLowerOrUpperFactory.ToLowerNodeGen::create);
        add(ToLowerOrUpper.ToUpper.class, ToLowerOrUpperFactory.ToUpperNodeGen::create);
        add(Traceback.class, TracebackNodeGen::create);
        add(TraceFunctions.PrimTrace.class, TraceFunctionsFactory.PrimTraceNodeGen::create);
        add(TraceFunctions.PrimUnTrace.class, TraceFunctionsFactory.PrimUnTraceNodeGen::create);
        add(TraceFunctions.TraceOnOff.class, TraceFunctionsFactory.TraceOnOffNodeGen::create);
        add(TraceFunctions.Tracemem.class, TraceFunctionsFactory.TracememNodeGen::create);
        add(TraceFunctions.Retracemem.class, TraceFunctionsFactory.RetracememNodeGen::create);
        add(TraceFunctions.Untracemem.class, TraceFunctionsFactory.UntracememNodeGen::create);
        add(Transpose.class, TransposeNodeGen::create);
        add(TrigExpFunctions.Atan2.class, TrigExpFunctionsFactory.Atan2NodeGen::create);
        add(Typeof.class, TypeofNodeGen::create);
        add(UnClass.class, UnClassNodeGen::create);
        add(Unique.class, UniqueNodeGen::create);
        add(Unlist.class, UnlistNodeGen::create);
        add(UpdateAttr.class, UpdateAttrNodeGen::create);
        add(UpdateAttributes.class, UpdateAttributesNodeGen::create);
        add(UpdateClass.class, UpdateClassNodeGen::create);
        add(UpdateComment.class, UpdateCommentNodeGen::create);
        add(UpdateDim.class, UpdateDimNodeGen::create);
        add(UpdateDimNames.class, UpdateDimNamesNodeGen::create);
        add(Utf8ToInt.class, Utf8ToIntNodeGen::create);
        add(EnvFunctions.UpdateEnvironment.class, EnvFunctionsFactory.UpdateEnvironmentNodeGen::create);
        add(UpdateLength.class, UpdateLengthNodeGen::create);
        add(UpdateLevels.class, UpdateLevelsNodeGen::create);
        add(UpdateNames.class, UpdateNamesNodeGen::create);
        add(UpdateOldClass.class, UpdateOldClassNodeGen::create);
        add(UpdateSlot.class, UpdateSlotNodeGen::create);
        add(UpdateStorageMode.class, UpdateStorageModeNodeGen::create);
        add(UpdateSubstr.class, UpdateSubstrNodeGen::create);
        add(ValidEnc.class, ValidEncNodeGen::create);
        add(VApply.class, VApplyNodeGen::create);
        add(Vector.class, VectorNodeGen::create);
        add(Warning.class, WarningNodeGen::create);
        add(WhichFunctions.Which.class, WhichFunctionsFactory.WhichNodeGen::create);
        add(WhichFunctions.WhichMax.class, WhichFunctions.WhichMax::create);
        add(WhichFunctions.WhichMin.class, WhichFunctions.WhichMin::create);
        add(Xtfrm.class, XtfrmNodeGen::create);
        add(IsSingle.class, IsSingleNodeGen::create);

        // infix functions
        add(Subscript.class, SubscriptNodeGen::create, Subscript::special);
        add(Subscript.DefaultBuiltin.class, SubscriptNodeGen::create, Subscript::special);
        add(Subset.class, SubsetNodeGen::create, Subset::special);
        add(Subset.DefaultBuiltin.class, SubsetNodeGen::create, Subset::special);
        add(AccessField.class, AccessFieldNodeGen::create, AccessField::createSpecial);
        add(AssignBuiltin.class, AssignBuiltinNodeGen::create);
        add(AssignBuiltinEq.class, AssignBuiltinEqNodeGen::create);
        add(AssignOuterBuiltin.class, AssignOuterBuiltinNodeGen::create);
        add(BraceBuiltin.class, BraceBuiltinNodeGen::create);
        add(BreakBuiltin.class, BreakBuiltinNodeGen::create);
        add(ForBuiltin.class, ForBuiltinNodeGen::create);
        add(FunctionBuiltin.class, FunctionBuiltin::create);
        add(IfBuiltin.class, IfBuiltinNodeGen::create);
        add(NextBuiltin.class, NextBuiltinNodeGen::create);
        add(ParenBuiltin.class, ParenBuiltin::new, ParenBuiltin::special);
        add(RepeatBuiltin.class, RepeatBuiltinNodeGen::create);
        add(Tilde.class, TildeNodeGen::create);
        add(UpdateSubscript.class, UpdateSubscriptNodeGen::create, UpdateSubscript::special);
        add(UpdateSubset.class, UpdateSubsetNodeGen::create, UpdateSubset::special);
        add(UpdateField.class, UpdateFieldNodeGen::create, UpdateField::createSpecial);
        add(WhileBuiltin.class, WhileBuiltinNodeGen::create);
    }

    private void addBinaryArithmetic(Class<?> builtinClass, BinaryArithmeticFactory binaryFactory, UnaryArithmeticFactory unaryFactory) {
        add(builtinClass, new BinaryArithmeticBuiltinFactory(binaryFactory, unaryFactory), BinaryArithmeticSpecial.createSpecialFactory(binaryFactory, unaryFactory));
    }

    private void addUnaryArithmetic(Class<?> builtinClass, UnaryArithmeticFactory unaryFactory) {
        add(builtinClass, new UnaryArithmeticBuiltinFactory(unaryFactory), UnaryArithmeticSpecial.createSpecialFactory(unaryFactory));
    }

    private void addBinaryCompare(Class<?> builtinClass, BooleanOperationFactory factory) {
        add(builtinClass, new BinaryCompareBuiltinFactory(factory), BinaryBooleanSpecial.createSpecialFactory(factory));
    }

    private static final class BinaryArithmeticBuiltinFactory implements Supplier<RBuiltinNode> {
        private final BinaryArithmeticFactory binaryFactory;
        private final UnaryArithmeticFactory unaryFactory;

        BinaryArithmeticBuiltinFactory(BinaryArithmeticFactory binaryFactory, UnaryArithmeticFactory unaryFactory) {
            this.binaryFactory = binaryFactory;
            this.unaryFactory = unaryFactory;
        }

        @Override
        public RBuiltinNode get() {
            return BinaryArithmeticNodeGen.create(binaryFactory, unaryFactory);
        }
    }

    private static final class UnaryArithmeticBuiltinFactory implements Supplier<RBuiltinNode> {
        private final UnaryArithmeticFactory unaryFactory;

        UnaryArithmeticBuiltinFactory(UnaryArithmeticFactory unaryFactory) {
            this.unaryFactory = unaryFactory;
        }

        @Override
        public RBuiltinNode get() {
            return new UnaryArithmeticBuiltinNode(unaryFactory);
        }
    }

    private static final class BinaryCompareBuiltinFactory implements Supplier<RBuiltinNode> {
        private final BooleanOperationFactory factory;

        BinaryCompareBuiltinFactory(BooleanOperationFactory factory) {
            this.factory = factory;
        }

        @Override
        public RBuiltinNode get() {
            return BinaryBooleanNodeGen.create(factory);
        }
    }

    private static void addFastPath(MaterializedFrame baseFrame, String name, FastPathFactory factory) {
        RFunction function = ReadVariableNode.lookupFunction(name, baseFrame);
        if (function == null) {
            throw new RInternalError("failed adding the fast path for the R function " + name +
                            ". The function was not found. This could be due to previous errors that prevented it from being loaded.");
        }
        ((RRootNode) function.getRootNode()).setFastPath(factory);
    }

    private static void addFastPath(MaterializedFrame baseFrame, String name, Supplier<RFastPathNode> factory, RVisibility visibility) {
        addFastPath(baseFrame, name, FastPathFactory.fromVisibility(visibility, factory));
    }

    private static void addFastPath(MaterializedFrame baseFrame, String name, Supplier<RFastPathNode> factory, Class<?> builtinNodeClass) {
        RBuiltin builtin = builtinNodeClass.getAnnotation(RBuiltin.class);
        addFastPath(baseFrame, name, FastPathFactory.fromRBuiltin(builtin, factory));
    }

    @Override
    public void loadOverrides(MaterializedFrame baseFrame) {
        super.loadOverrides(baseFrame);
        addFastPath(baseFrame, "[[.data.frame", SubscriptDataFrameFastPathNodeGen::create, RVisibility.ON);
        addFastPath(baseFrame, "[.data.frame", SubsetDataFrameFastPath.createFastPathFactory(SubsetDataFrameFastPathNodeGen::create));
        addFastPath(baseFrame, "matrix", MatrixFastPathNodeGen::create, Matrix.class);
        addFastPath(baseFrame, "setdiff", SetDiffFastPathNodeGen::create, RVisibility.ON);
        addFastPath(baseFrame, "get", GetFastPathNodeGen::create, RVisibility.ON);
        addFastPath(baseFrame, "exists", ExistsFastPathNodeGen::create, Exists.class);
        addFastPath(baseFrame, "assign", AssignFastPathNodeGen::create, Assign.class);
        addFastPath(baseFrame, "is.element", IsElementFastPathNodeGen::create, RVisibility.ON);
        addFastPath(baseFrame, "integer", IntegerFastPathNodeGen::create, RVisibility.ON);
        addFastPath(baseFrame, "numeric", DoubleFastPathNodeGen::create, RVisibility.ON);
        addFastPath(baseFrame, "double", DoubleFastPathNodeGen::create, RVisibility.ON);
        addFastPath(baseFrame, "complex", ComplexFastPathNodeGen::create, RVisibility.ON);
        addFastPath(baseFrame, "intersect", IntersectFastPathNodeGen::create, RVisibility.ON);
        addFastPath(baseFrame, "pmax", FastPathFactory.EVALUATE_ARGS);
        addFastPath(baseFrame, "pmin", FastPathFactory.EVALUATE_ARGS);
        addFastPath(baseFrame, "cbind", FastPathFactory.FORCED_EAGER_ARGS);
        addFastPath(baseFrame, "rbind", FastPathFactory.FORCED_EAGER_ARGS);
        addFastPath(baseFrame, "seq.default", SeqFunctionsFactory.SeqDefaultFastPathNodeGen::create, RVisibility.ON);
        addFastPath(baseFrame, "seq", SeqFunctionsFactory.SeqFastPathNodeGen::create, RVisibility.ON);
        addFastPath(baseFrame, "match.arg", MatchArgFastPathNodeGen::create, MatchArgFastPath.class);
        addFastPath(baseFrame, "stopifnot", StopifnotFastPath::new, StopifnotFastPath.class);

        setContainsDispatch(baseFrame, "eval", "[.data.frame", "[[.data.frame", "[<-.data.frame", "[[<-.data.frame");
    }

    private static void setContainsDispatch(MaterializedFrame baseFrame, String... functions) {
        for (String name : functions) {
            RFunction function = ReadVariableNode.lookupFunction(name, baseFrame);
            ((RRootNode) function.getRootNode()).setContainsDispatch(true);
        }
    }
}
