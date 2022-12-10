/*
 * Copyright (c) 1995, 1996  Robert Gentleman and Ross Ihaka
 * Copyright (c) 1997-2013,  The R Core Team
 * Copyright (c) 2015, 2022, Oracle and/or its affiliates
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
package com.oracle.truffle.r.library.stats.deriv;

import static com.oracle.truffle.r.nodes.builtin.CastBuilder.Predef.asLogicalVector;
import static com.oracle.truffle.r.nodes.builtin.CastBuilder.Predef.boxPrimitive;
import static com.oracle.truffle.r.nodes.builtin.CastBuilder.Predef.chain;
import static com.oracle.truffle.r.nodes.builtin.CastBuilder.Predef.findFirst;
import static com.oracle.truffle.r.nodes.builtin.CastBuilder.Predef.lengthGte;
import static com.oracle.truffle.r.nodes.builtin.CastBuilder.Predef.lengthLte;
import static com.oracle.truffle.r.nodes.builtin.CastBuilder.Predef.logicalValue;
import static com.oracle.truffle.r.nodes.builtin.CastBuilder.Predef.map;
import static com.oracle.truffle.r.nodes.builtin.CastBuilder.Predef.notEmpty;
import static com.oracle.truffle.r.nodes.builtin.CastBuilder.Predef.stringValue;
import static com.oracle.truffle.r.nodes.builtin.CastBuilder.Predef.toBoolean;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.r.nodes.RRootNode;
import com.oracle.truffle.r.nodes.access.ConstantNode;
import com.oracle.truffle.r.nodes.builtin.RExternalBuiltinNode;
import com.oracle.truffle.r.nodes.function.FormalArguments;
import com.oracle.truffle.r.nodes.function.RCallSpecialNode;
import com.oracle.truffle.r.runtime.ArgumentsSignature;
import com.oracle.truffle.r.runtime.RError;
import com.oracle.truffle.r.runtime.RInternalError;
import com.oracle.truffle.r.runtime.RRuntime;
import com.oracle.truffle.r.runtime.Utils;
import com.oracle.truffle.r.runtime.context.RContext;
import com.oracle.truffle.r.runtime.context.TruffleRLanguage;
import com.oracle.truffle.r.runtime.data.Closure;
import com.oracle.truffle.r.runtime.data.RArgsValuesAndNames;
import com.oracle.truffle.r.runtime.data.RComplex;
import com.oracle.truffle.r.runtime.data.RDataFactory;
import com.oracle.truffle.r.runtime.data.REmpty;
import com.oracle.truffle.r.runtime.data.RExpression;
import com.oracle.truffle.r.runtime.data.RFunction;
import com.oracle.truffle.r.runtime.data.RMissing;
import com.oracle.truffle.r.runtime.data.RNull;
import com.oracle.truffle.r.runtime.data.RPairList;
import com.oracle.truffle.r.runtime.data.RStringVector;
import com.oracle.truffle.r.runtime.data.RSymbol;
import com.oracle.truffle.r.runtime.env.frame.FrameSlotChangeMonitor;
import com.oracle.truffle.r.runtime.nodes.RCodeBuilder;
import com.oracle.truffle.r.runtime.nodes.RCodeBuilder.Argument;
import com.oracle.truffle.r.runtime.nodes.RNode;
import com.oracle.truffle.r.runtime.nodes.RSyntaxCall;
import com.oracle.truffle.r.runtime.nodes.RSyntaxConstant;
import com.oracle.truffle.r.runtime.nodes.RSyntaxElement;
import com.oracle.truffle.r.runtime.nodes.RSyntaxFunction;
import com.oracle.truffle.r.runtime.nodes.RSyntaxLookup;
import com.oracle.truffle.r.runtime.nodes.RSyntaxNode;
import com.oracle.truffle.r.runtime.nodes.RSyntaxVisitor;
import com.oracle.truffle.r.runtime.parsermetadata.FunctionScope;

//Transcribed from GnuR, library/stats/src/deriv.c

public abstract class Deriv extends RExternalBuiltinNode {

    static {
        Casts casts = new Casts(Deriv.class);
        casts.arg(1, "namevec").mustBe(stringValue()).asStringVector().mustBe(notEmpty(), RError.Message.INVALID_VARIABLE_NAMES);
        casts.arg(2, "function.arg").mapIf(logicalValue(), chain(asLogicalVector()).with(findFirst().logicalElement()).with(map(toBoolean())).end()).mapIf(stringValue(), boxPrimitive());
        casts.arg(3, "tag").defaultError(RError.Message.INVALID_VARIABLE_NAMES).mustBe(stringValue()).asStringVector().mustBe(notEmpty()).findFirst().mustBe(lengthGte(1).and(lengthLte(60)));
        casts.arg(4, "hessian").asLogicalVector().findFirst(RRuntime.LOGICAL_NA).map(toBoolean());
    }

    static final String LEFT_PAREN = "(";
    static final String PLUS = "+";
    static final String MINUS = "-";
    static final String TIMES = "*";
    static final String DIVIDE = "/";
    static final String POWER = "^";
    static final String LOG = "log";
    static final String EXP = "exp";
    static final String COS = "cos";
    static final String SIN = "sin";
    static final String TAN = "tan";
    static final String COSH = "cosh";
    static final String SINH = "sinh";
    static final String TANH = "tanh";
    static final String SQRT = "sqrt";
    static final String PNORM = "pnorm";
    static final String DNORM = "dnorm";
    static final String ASIN = "asin";
    static final String ACOS = "acos";
    static final String ATAN = "atan";
    static final String GAMMA = "gamma";
    static final String LGAMMA = "lgamma";
    static final String DIGAMMA = "digamma";
    static final String TRIGAMMA = "trigamma";
    static final String PSIGAMMA = "psigamma";

    public static Deriv create() {
        return DerivNodeGen.create();
    }

    public abstract Object execute(Object arg1, Object arg2, Object arg3, Object arg4, Object arg5);

    @Override
    public Object call(VirtualFrame frame, RArgsValuesAndNames args) {
        checkLength(args, 5);
        return execute(castArg(args, 0), castArg(args, 1), castArg(args, 2), castArg(args, 3), castArg(args, 4));
    }

    @Override
    protected Object call(RArgsValuesAndNames args) {
        throw RInternalError.shouldNotReachHere();
    }

    protected static boolean isConstant(Object expr) {
        return !((expr instanceof RPairList && ((RPairList) expr).isLanguage()) || expr instanceof RExpression || expr instanceof RSymbol);
    }

    @Specialization(guards = "isConstant(expr)")
    protected Object derive(Object expr, RStringVector names, Object functionArg, String tag, boolean hessian) {
        return deriveInternal(RSyntaxConstant.createDummyConstant(RSyntaxNode.INTERNAL, expr), names, functionArg, tag, hessian);
    }

    @Specialization
    protected Object derive(RSymbol expr, RStringVector names, Object functionArg, String tag, boolean hessian) {
        return deriveInternal(RSyntaxLookup.createDummyLookup(RSyntaxNode.INTERNAL, expr.getName(), false), names, functionArg, tag, hessian);
    }

    @Specialization
    protected Object derive(RExpression expr, RStringVector names, Object functionArg, String tag, boolean hessian,
                    @Cached("create()") Deriv derivNode) {
        return derivNode.execute(expr.getDataAt(0), names, functionArg, tag, hessian);
    }

    @Specialization(guards = "expr.isLanguage()")
    protected Object derive(RPairList expr, RStringVector names, Object functionArg, String tag, boolean hessian) {
        return deriveInternal(getSyntaxElement(expr), names, functionArg, tag, hessian);
    }

    @TruffleBoundary
    private static RSyntaxElement getSyntaxElement(RPairList expr) {
        return expr.getSyntaxElement();
    }

    @TruffleBoundary
    private Object deriveInternal(RSyntaxElement elem, RStringVector names, Object functionArg, String tag, boolean hessian) {
        return findDerive(elem, names, functionArg, tag, hessian).getResult(getRLanguage(), functionArg);
    }

    private static final class DerivResult {
        private final RExpression result;
        private final RSyntaxNode blockCall;
        private final List<Argument<RSyntaxNode>> targetArgs;

        private DerivResult(RExpression result) {
            this.result = result;
            blockCall = null;
            targetArgs = null;
        }

        private DerivResult(RSyntaxNode blockCall, List<Argument<RSyntaxNode>> targetArgs) {
            this.blockCall = blockCall;
            this.targetArgs = targetArgs;
            result = null;
        }

        private Object getResult(TruffleRLanguage language, Object functionArg) {
            if (result != null) {
                return result;
            }
            MaterializedFrame frame = functionArg instanceof RFunction ? ((RFunction) functionArg).getEnclosingFrame() : RContext.getInstance().stateREnvironment.getGlobalFrame();
            RootCallTarget callTarget = RContext.getASTBuilder().rootFunction(language, RSyntaxNode.LAZY_DEPARSE, targetArgs, blockCall, null, FunctionScope.EMPTY_SCOPE);
            FrameSlotChangeMonitor.initializeEnclosingFrame(callTarget.getRootNode().getFrameDescriptor(), frame);
            return RDataFactory.createFunction(RFunction.NO_NAME, RFunction.NO_NAME, callTarget, null, frame);
        }
    }

    @TruffleBoundary
    private static DerivResult findDerive(RSyntaxElement elem, RStringVector names, Object functionArg, String tag, boolean hessian) {
        LinkedList<RSyntaxNode> exprlist = new LinkedList<>();
        int fIndex = findSubexpression(elem, exprlist, tag);

        int nderiv = names.getLength();
        int[] dIndex = new int[nderiv];
        int[] d2Index = hessian ? new int[(nderiv * (1 + nderiv)) / 2] : null;
        for (int i = 0, k = 0; i < nderiv; i++) {
            RSyntaxElement dExpr = d(elem, names.getDataAt(i));
            dIndex[i] = findSubexpression(dExpr, exprlist, tag);

            if (hessian) {
                for (int j = i; j < nderiv; j++) {
                    RSyntaxElement d2Expr = d(dExpr, names.getDataAt(j));
                    d2Index[k] = findSubexpression(d2Expr, exprlist, tag);
                    k++;
                }
            }
        }

        int nexpr = exprlist.size();

        if (fIndex > 0) {
            exprlist.add(createLookup(tag + fIndex));
        } else {
            exprlist.add(cloneElement(elem));
        }

        exprlist.add(null);
        if (hessian) {
            exprlist.add(null);
        }

        for (int i = 0, k = 0; i < nderiv; i++) {
            if (dIndex[i] > 0) {
                exprlist.add(createLookup(tag + dIndex[i]));

                if (hessian) {
                    RSyntaxElement dExpr = d(elem, names.getDataAt(i));
                    for (int j = i; j < nderiv; j++) {
                        if (d2Index[k] > 0) {
                            exprlist.add(createLookup(tag + d2Index[k]));
                        } else {
                            exprlist.add((RSyntaxNode) d(dExpr, names.getDataAt(j)));
                        }
                        k++;
                    }
                }
            } else {
                // the first derivative is constant or simple variable
                // TODO: do not call the d twice
                RSyntaxElement dExpr = d(elem, names.getDataAt(i));
                exprlist.add((RSyntaxNode) dExpr);

                if (hessian) {
                    for (int j = i; j < nderiv; j++) {
                        if (d2Index[k] > 0) {
                            exprlist.add(createLookup(tag + d2Index[k]));
                        } else {
                            RSyntaxElement d2Expr = d(dExpr, names.getDataAt(j));
                            if (isZero(d2Expr)) {
                                exprlist.add(null);
                            } else {
                                exprlist.add((RSyntaxNode) d2Expr);
                            }
                        }
                        k++;
                    }
                }
            }
        }

        exprlist.add(null);
        exprlist.add(null);
        if (hessian) {
            exprlist.add(null);
        }

        for (int i = 0; i < nexpr; i++) {
            String subexprName = tag + (i + 1);
            if (countOccurences(subexprName, exprlist, i + 1) < 2) {
                replace(subexprName, exprlist.get(i), exprlist, i + 1);
                exprlist.set(i, null);
            } else {
                exprlist.set(i, createAssignNode(subexprName, exprlist.get(i)));
            }
        }

        int p = nexpr;
        exprlist.set(p++, createAssignNode(".value", exprlist.get(nexpr))); // .value <-
        exprlist.set(p++, createGrad(names)); // .grad <-
        if (hessian) {
            exprlist.set(p++, createHess(names)); // .hessian
        }
        // .grad[, "..."] <- ...
        for (int i = 0; i < nderiv; i++) {
            RSyntaxNode ans = exprlist.get(p);
            exprlist.set(p, derivAssign(names.getDataAt(i), ans));
            p++;

            if (hessian) {
                for (int j = i; j < nderiv; j++, p++) {
                    ans = exprlist.get(p);
                    if (ans != null) {
                        if (i == j) {
                            exprlist.set(p, hessAssign1(names.getDataAt(i), addParens(ans)));
                        } else {
                            exprlist.set(p, hessAssign2(names.getDataAt(i), names.getDataAt(j), addParens(ans)));
                        }
                    }
                }
            }
        }
        // attr(.value, "gradient") <- .grad
        exprlist.set(p++, addGrad());
        if (hessian) {
            exprlist.set(p++, addHess());
        }

        // .value
        exprlist.set(p++, createLookup(".value"));

        // prune exprlist
        exprlist.removeAll(Collections.singleton(null));

        List<Argument<RSyntaxNode>> blockStatements = new ArrayList<>(exprlist.size());
        for (RSyntaxNode e : exprlist) {
            blockStatements.add(RCodeBuilder.argument(e));
        }
        RSyntaxNode blockCall = RContext.getASTBuilder().call(RSyntaxNode.LAZY_DEPARSE, createLookup("{"), blockStatements);

        if (functionArg instanceof RStringVector) {
            RStringVector funArgNames = (RStringVector) functionArg;
            List<Argument<RSyntaxNode>> targetArgs = new ArrayList<>();
            for (int i = 0; i < funArgNames.getLength(); i++) {
                targetArgs.add(RCodeBuilder.argument(RSyntaxNode.LAZY_DEPARSE, funArgNames.getDataAt(i), ConstantNode.create(RMissing.instance)));
            }

            return new DerivResult(blockCall, targetArgs);
        } else if (functionArg == Boolean.TRUE) {
            List<Argument<RSyntaxNode>> targetArgs = new ArrayList<>();
            for (int i = 0; i < names.getLength(); i++) {
                targetArgs.add(RCodeBuilder.argument(RSyntaxNode.LAZY_DEPARSE, names.getDataAt(i), ConstantNode.create(RMissing.instance)));
            }

            return new DerivResult(blockCall, targetArgs);
        } else if (functionArg instanceof RFunction) {
            RFunction funTemplate = (RFunction) functionArg;
            FormalArguments formals = ((RRootNode) funTemplate.getRootNode()).getFormalArguments();
            RNode[] defArgs = formals.getArguments();
            List<Argument<RSyntaxNode>> targetArgs = new ArrayList<>();
            for (int i = 0; i < defArgs.length; i++) {
                RSyntaxNode defArgClone;
                if (defArgs[i] == null) {
                    defArgClone = ConstantNode.create(RMissing.instance);
                } else {
                    defArgClone = cloneElement((RSyntaxNode) defArgs[i]);
                }
                targetArgs.add(RCodeBuilder.argument(RSyntaxNode.LAZY_DEPARSE, formals.getSignature().getName(i), defArgClone));
            }

            return new DerivResult(blockCall, targetArgs);
        } else {
            RPairList lan = RDataFactory.createLanguage(Closure.createLanguageClosure(blockCall.asRNode()));
            RExpression res = RDataFactory.createExpression(new Object[]{lan});
            return new DerivResult(res);
        }
    }

    private static int findSubexpression(RSyntaxElement expr, List<RSyntaxNode> exprlist, String tag) {
        RSyntaxVisitor<Integer> vis = new RSyntaxVisitor<>() {
            @Override
            protected Integer visit(RSyntaxCall call) {
                if (call.getSyntaxLHS() instanceof RSyntaxLookup && Utils.identityEquals(((RSyntaxLookup) call.getSyntaxLHS()).getIdentifier(), LEFT_PAREN)) {
                    return accept(call.getSyntaxArguments()[0]);
                }

                RSyntaxElement[] args = call.getSyntaxArguments();
                List<Argument<RSyntaxNode>> newArgs = new ArrayList<>();
                for (int i = 0; i < args.length; i++) {
                    int k = accept(args[i]);
                    if (k > 0) {
                        newArgs.add(RCodeBuilder.argument(createLookup(tag + k)));
                    } else {
                        newArgs.add(RCodeBuilder.argument(cloneElement(args[i])));
                    }
                }
                RSyntaxNode newCall = RContext.getASTBuilder().call(call.getSourceSection(), cloneElement(call.getSyntaxLHS()), newArgs);
                return accumulate(newCall, exprlist);
            }

            @Override
            protected Integer visit(RSyntaxConstant element) {
                return checkConstant(element.getValue());
            }

            @Override
            protected Integer visit(RSyntaxLookup element) {
                return 0;
            }

            @Override
            protected Integer visit(RSyntaxFunction element) {
                throw RError.error(RError.SHOW_CALLER, RError.Message.INVALID_EXPRESSION, "FindSubexprs");
            }
        };
        return vis.accept(expr);
    }

    private static int checkConstant(Object val) {
        if (val instanceof Double || val instanceof Integer || val instanceof RComplex || val instanceof Byte || val instanceof RSymbol) {
            return 0;
        } else {
            throw RError.error(RError.SHOW_CALLER, RError.Message.INVALID_EXPRESSION, "FindSubexprs");
        }
    }

    private static boolean isDoubleValue(RSyntaxElement elem, double value) {
        if (elem instanceof RSyntaxConstant) {
            Object val = ((RSyntaxConstant) elem).getValue();
            if (val instanceof Number) {
                return ((Number) val).doubleValue() == value;
            } else {
                return false;
            }
        } else {
            return false;
        }
    }

    static boolean isZero(RSyntaxElement elem) {
        return isDoubleValue(elem, 0.);
    }

    static boolean isOne(RSyntaxElement elem) {
        return isDoubleValue(elem, 1.);
    }

    private static int accumulate(RSyntaxElement expr, List<RSyntaxNode> exprlist) {
        for (int k = 0; k < exprlist.size(); k++) {
            if (equal(expr, exprlist.get(k))) {
                return k + 1;
            }
        }
        exprlist.add((RSyntaxNode) expr);
        return exprlist.size();
    }

    // TODO: move to a utility class
    private static boolean equal(RSyntaxElement expr1, RSyntaxElement expr2) {
        if (expr1.getClass() != expr2.getClass()) {
            return false;
        }
        if (expr1 instanceof RSyntaxLookup) {
            return Utils.identityEquals(((RSyntaxLookup) expr1).getIdentifier(), ((RSyntaxLookup) expr2).getIdentifier());
        }
        if (expr1 instanceof RSyntaxConstant) {
            return ((RSyntaxConstant) expr1).getValue().equals(((RSyntaxConstant) expr2).getValue());
        }
        if (expr1 instanceof RSyntaxCall) {
            RSyntaxElement[] args1 = ((RSyntaxCall) expr1).getSyntaxArguments();
            RSyntaxElement[] args2 = ((RSyntaxCall) expr2).getSyntaxArguments();
            if (args1.length != args2.length) {
                return false;
            }
            if (!equal(((RSyntaxCall) expr1).getSyntaxLHS(), ((RSyntaxCall) expr2).getSyntaxLHS())) {
                return false;
            }
            for (int i = 0; i < args1.length; i++) {
                if (!equal(args1[i], args2[i])) {
                    return false;
                }
            }
            return true;
        }

        throw RError.error(RError.SHOW_CALLER, RError.Message.INVALID_EXPRESSION, "equal");
    }

    static String getFunctionName(RSyntaxElement expr) {
        if (expr instanceof RSyntaxCall) {
            RSyntaxCall call = (RSyntaxCall) expr;
            return call.getSyntaxLHS() instanceof RSyntaxLookup ? ((RSyntaxLookup) call.getSyntaxLHS()).getIdentifier() : null;
        } else {
            return null;
        }
    }

    static RSyntaxNode cloneElement(RSyntaxElement element) {
        return RContext.getASTBuilder().process(element);
    }

    private static RSyntaxElement d(RSyntaxElement expr, String var) {
        return new DerivVisitor(var).accept(expr);
    }

    private static int argsLength(RSyntaxElement elem) {
        if (elem instanceof RSyntaxCall) {
            return ((RSyntaxCall) elem).getSyntaxArguments().length;
        } else {
            return 0;
        }
    }

    static RSyntaxElement arg(RSyntaxElement elem, int argIndex) {
        assert elem instanceof RSyntaxCall && (argIndex < ((RSyntaxCall) elem).getSyntaxArguments().length);
        return ((RSyntaxCall) elem).getSyntaxArguments()[argIndex];
    }

    private static RSyntaxElement setArg(RSyntaxElement elem, int argIndex, RSyntaxElement arg) {
        assert elem instanceof RSyntaxCall && (argIndex < ((RSyntaxCall) elem).getSyntaxArguments().length);
        RSyntaxCall call = (RSyntaxCall) elem;
        RSyntaxElement[] args = call.getSyntaxArguments();
        RSyntaxNode[] newArgs = new RSyntaxNode[args.length];
        for (int i = 0; i < args.length; i++) {
            if (i == argIndex) {
                newArgs[i] = (RSyntaxNode) arg;
            } else {
                newArgs[i] = cloneElement(args[i]);
            }
        }
        return RCallSpecialNode.createCall(call.getSourceSection(), (RNode) cloneElement(call.getSyntaxLHS()), ArgumentsSignature.empty(args.length), newArgs);
    }

    static RSyntaxNode newCall(String functionName, RSyntaxElement arg1, RSyntaxElement arg2) {
        if (arg2 == null) {
            return RContext.getASTBuilder().call(RSyntaxNode.SOURCE_UNAVAILABLE, createLookup(functionName), (RSyntaxNode) arg1);
        } else {
            return RContext.getASTBuilder().call(RSyntaxNode.SOURCE_UNAVAILABLE, createLookup(functionName), (RSyntaxNode) arg1, (RSyntaxNode) arg2);
        }
    }

    private static int countOccurences(String subexprName, List<RSyntaxNode> exprlist, int fromIndex) {
        if (fromIndex >= exprlist.size()) {
            return 0;
        }

        RSyntaxNode exprListNode = exprlist.get(fromIndex);
        if (exprListNode == null) {
            return countOccurences(subexprName, exprlist, fromIndex + 1);
        }

        RSyntaxVisitor<Integer> vis = new RSyntaxVisitor<>() {
            @Override
            protected Integer visit(RSyntaxCall element) {
                RSyntaxElement[] args = element.getSyntaxArguments();
                int cnt = 0;
                for (int i = 0; i < args.length; i++) {
                    cnt += accept(args[i]);
                }
                return cnt;
            }

            @Override
            protected Integer visit(RSyntaxConstant element) {
                return 0;
            }

            @Override
            protected Integer visit(RSyntaxLookup element) {
                return subexprName.equals(element.getIdentifier()) ? 1 : 0;
            }

            @Override
            protected Integer visit(RSyntaxFunction element) {
                throw RInternalError.shouldNotReachHere();
            }
        };

        return vis.accept(exprListNode) + countOccurences(subexprName, exprlist, fromIndex + 1);
    }

    private static void replace(String subexprName, RSyntaxNode replacement, List<RSyntaxNode> exprlist, int fromIndex) {
        if (fromIndex >= exprlist.size()) {
            return;
        }

        RSyntaxElement exprListNode = exprlist.get(fromIndex);
        if (exprListNode == null) {
            replace(subexprName, replacement, exprlist, fromIndex + 1);
            return;
        }

        RSyntaxVisitor<RSyntaxElement> vis = new RSyntaxVisitor<>() {

            // TODO: do not create a new call node after the first replacement

            @Override
            protected RSyntaxElement visit(RSyntaxCall call) {
                RSyntaxElement[] args = call.getSyntaxArguments();
                RSyntaxNode[] newArgs = new RSyntaxNode[args.length];
                for (int i = 0; i < args.length; i++) {
                    newArgs[i] = (RSyntaxNode) accept(args[i]);
                }
                return RCallSpecialNode.createCall(call.getSourceSection(), (RNode) call.getSyntaxLHS(), ArgumentsSignature.empty(args.length), newArgs);
            }

            @Override
            protected RSyntaxElement visit(RSyntaxConstant element) {
                return element;
            }

            @Override
            protected RSyntaxElement visit(RSyntaxLookup element) {
                return subexprName.equals(element.getIdentifier()) ? replacement : element;
            }

            @Override
            protected RSyntaxElement visit(RSyntaxFunction element) {
                throw RInternalError.shouldNotReachHere();
            }
        };

        exprlist.set(fromIndex, (RSyntaxNode) vis.accept(exprListNode));

        replace(subexprName, replacement, exprlist, fromIndex + 1);
    }

    private static RSyntaxNode createLookup(String name) {
        return RContext.getASTBuilder().lookup(RSyntaxNode.SOURCE_UNAVAILABLE, name, false);
    }

    private static RSyntaxNode createAssignNode(String varName, RSyntaxNode rhs) {
        return RContext.getASTBuilder().call(RSyntaxNode.SOURCE_UNAVAILABLE, createLookup("<-"), createLookup(Utils.intern(varName)), addParens(rhs));
    }

    private static RSyntaxNode hessAssign1(String varName, RSyntaxNode rhs) {
        RSyntaxNode tmp = RContext.getASTBuilder().call(RSyntaxNode.SOURCE_UNAVAILABLE, createLookup("["), createLookup(".hessian"), ConstantNode.create(REmpty.instance),
                        ConstantNode.create(Utils.intern(varName)), ConstantNode.create(Utils.intern(varName)));
        return RContext.getASTBuilder().call(RSyntaxNode.SOURCE_UNAVAILABLE, createLookup("<-"), tmp, rhs);
    }

    private static RSyntaxNode hessAssign2(String varName1, String varName2, RSyntaxNode rhs) {
        RSyntaxNode tmp1 = RContext.getASTBuilder().call(RSyntaxNode.SOURCE_UNAVAILABLE, createLookup("["), createLookup(".hessian"), ConstantNode.create(REmpty.instance),
                        ConstantNode.create(Utils.intern(varName1)), ConstantNode.create(Utils.intern(varName2)));
        RSyntaxNode tmp2 = RContext.getASTBuilder().call(RSyntaxNode.SOURCE_UNAVAILABLE, createLookup("["), createLookup(".hessian"), ConstantNode.create(REmpty.instance),
                        ConstantNode.create(Utils.intern(varName2)), ConstantNode.create(Utils.intern(varName1)));

        RSyntaxNode tmp3 = RContext.getASTBuilder().call(RSyntaxNode.SOURCE_UNAVAILABLE, createLookup("<-"), tmp2, rhs);
        return RContext.getASTBuilder().call(RSyntaxNode.SOURCE_UNAVAILABLE, createLookup("<-"), tmp1, tmp3);
    }

    private static RSyntaxNode createGrad(RStringVector names) {
        int n = names.getLength();
        List<Argument<RSyntaxNode>> cArgs = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            cArgs.add(RCodeBuilder.argument(ConstantNode.create(Utils.intern(names.getDataAt(i)))));
        }
        RSyntaxNode tmp1 = RContext.getASTBuilder().call(RSyntaxNode.SOURCE_UNAVAILABLE, createLookup("c"), cArgs);
        RSyntaxNode dimnames = RContext.getASTBuilder().call(RSyntaxNode.SOURCE_UNAVAILABLE, createLookup("list"), ConstantNode.create(RNull.instance), tmp1);

        RSyntaxNode tmp2 = RContext.getASTBuilder().call(RSyntaxNode.SOURCE_UNAVAILABLE, createLookup("length"), createLookup(".value"));
        RSyntaxNode dim = RContext.getASTBuilder().call(RSyntaxNode.SOURCE_UNAVAILABLE, createLookup("c"), tmp2, ConstantNode.create(n));
        ConstantNode data = ConstantNode.create(0.);

        RSyntaxNode p = RContext.getASTBuilder().call(RSyntaxNode.SOURCE_UNAVAILABLE, createLookup("array"), data, dim, dimnames);
        return RContext.getASTBuilder().call(RSyntaxNode.SOURCE_UNAVAILABLE, createLookup("<-"), createLookup(".grad"), p);
    }

    private static RSyntaxNode createHess(RStringVector names) {
        int n = names.getLength();
        List<Argument<RSyntaxNode>> cArgs = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            cArgs.add(RCodeBuilder.argument(ConstantNode.create(Utils.intern(names.getDataAt(i)))));
        }
        RSyntaxNode tmp1 = RContext.getASTBuilder().call(RSyntaxNode.SOURCE_UNAVAILABLE, createLookup("c"), cArgs);
        RSyntaxNode tmp1Clone = cloneElement(tmp1);
        RSyntaxNode dimnames = RContext.getASTBuilder().call(RSyntaxNode.SOURCE_UNAVAILABLE, createLookup("list"), ConstantNode.create(RNull.instance), tmp1, tmp1Clone);

        RSyntaxNode tmp2 = RContext.getASTBuilder().call(RSyntaxNode.SOURCE_UNAVAILABLE, createLookup("length"), createLookup(".value"));
        RSyntaxNode dim = RContext.getASTBuilder().call(RSyntaxNode.SOURCE_UNAVAILABLE, createLookup("c"), tmp2, ConstantNode.create(n), ConstantNode.create(n));
        ConstantNode data = ConstantNode.create(0.);

        RSyntaxNode p = RContext.getASTBuilder().call(RSyntaxNode.SOURCE_UNAVAILABLE, createLookup("array"), data, dim, dimnames);
        return RContext.getASTBuilder().call(RSyntaxNode.SOURCE_UNAVAILABLE, createLookup("<-"), createLookup(".hessian"), p);
    }

    private static RSyntaxNode derivAssign(String name, RSyntaxNode expr) {
        RSyntaxNode tmp = RContext.getASTBuilder().call(RSyntaxNode.SOURCE_UNAVAILABLE, createLookup("["), createLookup(".grad"), ConstantNode.create(REmpty.instance),
                        ConstantNode.create(Utils.intern(name)));
        return RContext.getASTBuilder().call(RSyntaxNode.SOURCE_UNAVAILABLE, createLookup("<-"), tmp, expr);
    }

    private static RSyntaxNode addGrad() {
        RSyntaxNode tmp = RContext.getASTBuilder().call(RSyntaxNode.SOURCE_UNAVAILABLE, createLookup("attr"), createLookup(".value"), ConstantNode.create("gradient"));
        return RContext.getASTBuilder().call(RSyntaxNode.SOURCE_UNAVAILABLE, createLookup("<-"), tmp, createLookup(".grad"));
    }

    private static RSyntaxNode addHess() {
        RSyntaxNode tmp = RContext.getASTBuilder().call(RSyntaxNode.SOURCE_UNAVAILABLE, createLookup("attr"), createLookup(".value"), ConstantNode.create("hessian"));
        return RContext.getASTBuilder().call(RSyntaxNode.SOURCE_UNAVAILABLE, createLookup("<-"), tmp, createLookup(".hessian"));
    }

    private static boolean isForm(RSyntaxElement expr, String functionName) {
        return argsLength(expr) == 2 && getFunctionName(expr) == functionName;
    }

    static RSyntaxNode addParens(RSyntaxElement node) {
        RSyntaxElement expr = node;
        if (node instanceof RSyntaxCall) {
            RSyntaxCall call = (RSyntaxCall) node;
            RSyntaxElement[] args = call.getSyntaxArguments();
            RSyntaxNode[] newArgs = new RSyntaxNode[args.length];
            for (int i = 0; i < args.length; i++) {
                newArgs[i] = addParens(args[i]);
            }
            expr = RCallSpecialNode.createCall(call.getSourceSection(), (RNode) cloneElement(call.getSyntaxLHS()), ArgumentsSignature.empty(args.length), newArgs);
        }

        if (isForm(expr, PLUS)) {
            if (isForm(arg(expr, 1), PLUS)) {
                expr = setArg(expr, 1, newCall(LEFT_PAREN, arg(expr, 1), null));
            }
        } else if (isForm(expr, MINUS)) {
            if (isForm(arg(expr, 1), PLUS) || isForm(arg(expr, 1), MINUS)) {
                expr = setArg(expr, 1, newCall(LEFT_PAREN, arg(expr, 1), null));
            }
        } else if (isForm(expr, TIMES)) {
            if (isForm(arg(expr, 1), PLUS) || isForm(arg(expr, 1), MINUS) || isForm(arg(expr, 1), TIMES) ||
                            isForm(arg(expr, 1), DIVIDE)) {
                expr = setArg(expr, 1, newCall(LEFT_PAREN, arg(expr, 1), null));
            }
            if (isForm(arg(expr, 0), MINUS) || isForm(arg(expr, 0), MINUS)) {
                expr = setArg(expr, 0, newCall(LEFT_PAREN, arg(expr, 0), null));
            }
        } else if (isForm(expr, DIVIDE)) {
            if (isForm(arg(expr, 1), PLUS) || isForm(arg(expr, 1), MINUS) || isForm(arg(expr, 1), TIMES) ||
                            isForm(arg(expr, 1), DIVIDE)) {
                expr = setArg(expr, 1, newCall(LEFT_PAREN, arg(expr, 1), null));
            }
            if (isForm(arg(expr, 0), PLUS) || isForm(arg(expr, 0), MINUS)) {
                expr = setArg(expr, 0, newCall(LEFT_PAREN, arg(expr, 0), null));
            }
        } else if (isForm(expr, POWER)) {
            if (isForm(arg(expr, 0), POWER)) {
                expr = setArg(expr, 0, newCall(LEFT_PAREN, arg(expr, 0), null));
            }
            if (isForm(arg(expr, 1), PLUS) || isForm(arg(expr, 1), MINUS) || isForm(arg(expr, 1), TIMES) ||
                            isForm(arg(expr, 1), DIVIDE)) {
                expr = setArg(expr, 1, newCall(LEFT_PAREN, arg(expr, 1), null));
            }
        }
        return (RSyntaxNode) expr;
    }
}
