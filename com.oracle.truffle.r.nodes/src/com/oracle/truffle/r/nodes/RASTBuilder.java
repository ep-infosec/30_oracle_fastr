/*
 * Copyright (c) 2016, 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.r.nodes;

import static com.oracle.truffle.r.runtime.context.FastROptions.ForceSources;
import static com.oracle.truffle.r.runtime.context.FastROptions.RefCountIncrementOnly;

import java.util.ArrayList;
import java.util.List;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.TruffleLogger;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.object.DynamicObject;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.r.nodes.access.AccessArgumentNode;
import com.oracle.truffle.r.nodes.access.ConstantNode;
import com.oracle.truffle.r.nodes.access.ReadVariadicComponentNode;
import com.oracle.truffle.r.nodes.access.WriteVariableNode;
import com.oracle.truffle.r.nodes.access.variables.ReadVariableNode;
import com.oracle.truffle.r.nodes.builtin.InternalNode;
import com.oracle.truffle.r.nodes.control.AbstractBlockNode;
import com.oracle.truffle.r.nodes.control.BreakNode;
import com.oracle.truffle.r.nodes.control.ForNodeGen;
import com.oracle.truffle.r.nodes.control.IfNode;
import com.oracle.truffle.r.nodes.control.NextNode;
import com.oracle.truffle.r.nodes.control.RepeatNode;
import com.oracle.truffle.r.nodes.control.ReplacementDispatchNode;
import com.oracle.truffle.r.nodes.control.WhileNode;
import com.oracle.truffle.r.nodes.function.FormalArguments;
import com.oracle.truffle.r.nodes.function.FunctionDefinitionNode;
import com.oracle.truffle.r.nodes.function.FunctionExpressionNode;
import com.oracle.truffle.r.nodes.function.PostProcessArgumentsNode;
import com.oracle.truffle.r.nodes.function.RCallSpecialNode;
import com.oracle.truffle.r.nodes.function.SaveArgumentsNode;
import com.oracle.truffle.r.nodes.function.WrapArgumentNode;
import com.oracle.truffle.r.nodes.function.signature.MissingNode;
import com.oracle.truffle.r.nodes.function.signature.QuoteNode;
import com.oracle.truffle.r.runtime.ArgumentsSignature;
import com.oracle.truffle.r.runtime.RError;
import com.oracle.truffle.r.runtime.RError.Message;
import com.oracle.truffle.r.runtime.RLogger;
import com.oracle.truffle.r.runtime.RRuntime;
import com.oracle.truffle.r.runtime.Utils;
import com.oracle.truffle.r.runtime.builtins.FastPathFactory;
import com.oracle.truffle.r.runtime.context.Engine.ParserMetadata;
import com.oracle.truffle.r.runtime.context.RContext;
import com.oracle.truffle.r.runtime.context.TruffleRLanguage;
import com.oracle.truffle.r.runtime.data.REmpty;
import com.oracle.truffle.r.runtime.data.RNull;
import com.oracle.truffle.r.runtime.data.RPairList;
import com.oracle.truffle.r.runtime.data.RSharingAttributeStorage;
import com.oracle.truffle.r.runtime.data.RSymbol;
import com.oracle.truffle.r.runtime.env.frame.FrameIndex;
import com.oracle.truffle.r.runtime.env.frame.FrameSlotChangeMonitor;
import com.oracle.truffle.r.runtime.nodes.EvaluatedArgumentsVisitor;
import com.oracle.truffle.r.runtime.nodes.RCodeBuilder;
import com.oracle.truffle.r.runtime.nodes.RNode;
import com.oracle.truffle.r.runtime.nodes.RSyntaxElement;
import com.oracle.truffle.r.runtime.nodes.RSyntaxLookup;
import com.oracle.truffle.r.runtime.nodes.RSyntaxNode;
import com.oracle.truffle.r.runtime.parsermetadata.FunctionScope;

/**
 * This class can be used to build fragments of Truffle AST that correspond to R language
 * constructs: calls, lookups, constants and functions.
 */
public final class RASTBuilder implements RCodeBuilder<RSyntaxNode> {

    private static final TruffleLogger logger = RLogger.getLogger(RLogger.LOGGER_AST);
    private CodeBuilderContext context = CodeBuilderContext.DEFAULT;
    private ParseDataBuilder parseDataBuilder;

    public RASTBuilder(boolean keepSource) {
        this.parseDataBuilder = keepSource ? new ParseDataBuilder() : null;
    }

    public ParserMetadata getParseData() {
        assert parseDataBuilder != null : "cannot invoke getParseData on RASTBuilder creates with keepSource == false";
        return parseDataBuilder.getParseData();
    }

    @Override
    public void modifyLastToken(RCodeToken newToken) {
        parseDataBuilder.modifyLastToken(newToken);
    }

    @Override
    public void modifyLastTokenIf(RCodeToken oldToken, RCodeToken newToken) {
        parseDataBuilder.modifyLastTokenIf(oldToken, newToken);
    }

    @Override
    public void token(SourceSection source, RCodeToken token, String tokenTextIn) {
        if (parseDataBuilder != null) {
            parseDataBuilder.token(source, token, tokenTextIn);
        }
    }

    @Override
    public RSyntaxNode call(SourceSection source, RSyntaxNode lhs, List<Argument<RSyntaxNode>> args, DynamicObject attributes) {
        RSyntaxNode sn = createCall(source, lhs, args);
        sn.setAttributes(attributes);
        return sn;
    }

    private RSyntaxNode createCall(SourceSection source, RSyntaxNode lhs, List<Argument<RSyntaxNode>> args) {
        if (lhs instanceof RSyntaxLookup) {
            RSyntaxLookup lhsLookup = (RSyntaxLookup) lhs;
            String symbol = lhsLookup.getIdentifier();

            if (parseDataBuilder != null) {
                parseDataBuilder.lookupCall(source, symbol);
            }
            if (args.size() == 0) {
                switch (symbol) {
                    case "break":
                        return new BreakNode(source, lhsLookup);
                    case "next":
                        return new NextNode(source, lhsLookup);
                }
            } else if (args.size() == 1) {
                switch (symbol) {
                    case "repeat":
                        return new RepeatNode(source, lhsLookup, args.get(0).value);
                }
            } else if (args.size() == 2) {
                switch (symbol) {
                    case "$":
                    case "@":
                        break;
                    case "while":
                        return new WhileNode(source, lhsLookup, args.get(0).value, args.get(1).value);
                    case "if":
                        return new IfNode(source, lhsLookup, args.get(0).value, args.get(1).value, null);
                    case "=":
                    case "<-":
                    case ":=":
                    case "<<-":
                    case "->":
                    case "->>":
                        boolean isSuper = "<<-".equals(symbol) || "->>".equals(symbol);
                        boolean switchArgs = "->".equals(symbol) || "->>".equals(symbol);
                        // fix the operators while keeping the correct source sections
                        if ("->>".equals(symbol)) {
                            lhsLookup = (RSyntaxLookup) ReadVariableNode.wrap(lhs.getLazySourceSection(), ReadVariableNode.createForcedFunctionLookup("<<-"));
                        } else if ("->".equals(symbol)) {
                            lhsLookup = (RSyntaxLookup) ReadVariableNode.wrap(lhs.getLazySourceSection(), ReadVariableNode.createForcedFunctionLookup("<-"));
                        }
                        // switch the args if needed
                        RSyntaxNode lhsArg = args.get(switchArgs ? 1 : 0).value;
                        RSyntaxNode rhsArg = args.get(switchArgs ? 0 : 1).value;
                        String lhsName = args.get(0).name;
                        String rhsName = args.get(1).name;
                        ArgumentsSignature names;
                        if (lhsName == null && rhsName == null) {
                            names = ArgumentsSignature.empty(2);
                        } else {
                            names = ArgumentsSignature.get(lhsName, rhsName);
                        }
                        return new ReplacementDispatchNode(source, lhsLookup, lhsArg, rhsArg, isSuper, context.getReplacementVarsStartIndex(), names);
                }
            } else if (args.size() == 3) {
                switch (symbol) {
                    case "for":
                        if (args.get(0).value instanceof RSyntaxLookup) {
                            RSyntaxLookup var = (RSyntaxLookup) args.get(0).value;
                            return ForNodeGen.create(source, lhsLookup, var, args.get(2).value.asRNode(), args.get(1).value.asRNode());
                        }
                        break;
                    case "if":
                        return new IfNode(source, lhsLookup, args.get(0).value, args.get(1).value, args.get(2).value);
                }
            }
            switch (symbol) {
                case "{":
                    return AbstractBlockNode.create(source, lhsLookup, toRNodeArray(args));
                case "missing":
                    return new MissingNode(source, lhsLookup, createSignature(args), toSyntaxElementArray(args));
                case "quote":
                    return new QuoteNode(source, lhsLookup, createSignature(args), toSyntaxElementArray(args));
                case ".Internal":
                    return InternalNode.create(source, lhsLookup, createSignature(args), toSyntaxNodeArray(args));
            }
        } else {
            recordExpr(source);
        }

        return RCallSpecialNode.createCall(source, lhs.asRNode(), createSignature(args), createArguments(args));
    }

    private static RSyntaxElement[] toSyntaxElementArray(List<Argument<RSyntaxNode>> args) {
        RSyntaxElement[] result = new RSyntaxElement[args.size()];
        for (int i = 0; i < args.size(); i++) {
            result[i] = args.get(i).value;
        }
        return result;
    }

    private static RSyntaxNode[] toSyntaxNodeArray(List<Argument<RSyntaxNode>> args) {
        RSyntaxNode[] result = new RSyntaxNode[args.size()];
        for (int i = 0; i < args.size(); i++) {
            result[i] = args.get(i).value;
        }
        return result;
    }

    private static RNode[] toRNodeArray(List<Argument<RSyntaxNode>> args) {
        RNode[] result = new RNode[args.size()];
        for (int i = 0; i < args.size(); i++) {
            result[i] = args.get(i).value.asRNode();
        }
        return result;
    }

    private static ArgumentsSignature createSignature(List<Argument<RSyntaxNode>> args) {
        String[] argumentNames = new String[args.size()];
        for (int i = 0; i < args.size(); i++) {
            argumentNames[i] = args.get(i).name;
        }
        return ArgumentsSignature.get(argumentNames);
    }

    private static RSyntaxNode[] createArguments(List<Argument<RSyntaxNode>> args) {
        RSyntaxNode[] nodes = new RSyntaxNode[args.size()];
        for (int i = 0; i < nodes.length; i++) {
            Argument<RSyntaxNode> arg = args.get(i);
            nodes[i] = (arg.value == null && arg.name == null) ? ConstantNode.create(arg.source == null ? RSyntaxNode.SOURCE_UNAVAILABLE : arg.source, REmpty.instance) : arg.value;
        }
        return nodes;
    }

    private static String getFunctionDescription(SourceSection source, Object assignedTo) {
        if (assignedTo instanceof String) {
            return (String) assignedTo;
        } else if (assignedTo instanceof RSyntaxLookup) {
            return ((RSyntaxLookup) assignedTo).getIdentifier();
        } else {
            CharSequence functionBody = source.getCharacters();
            return functionBody.subSequence(0, Math.min(functionBody.length(), 40)).toString().replace("\n", "\\n");
        }
    }

    public static FastPathFactory createFunctionFastPath(RSyntaxElement body, ArgumentsSignature signature) {
        return EvaluatedArgumentsVisitor.process(body, signature);
    }

    @Override
    public RSyntaxNode function(TruffleRLanguage language, SourceSection source, List<Argument<RSyntaxNode>> params, RSyntaxNode body, Object assignedTo, FunctionScope functionScope) {
        String functionName = getFunctionDescription(source, assignedTo);
        RootCallTarget callTarget = rootFunction(language, source, params, body, functionName, functionScope);
        return FunctionExpressionNode.create(source, callTarget);
    }

    @Override
    public ArrayList<Argument<RSyntaxNode>> getFunctionExprArgs(Object args) {
        CompilerAsserts.neverPartOfCompilation();
        if (!((args instanceof RPairList && !((RPairList) args).isLanguage()) || args == RNull.instance)) {
            throw RError.error(RError.SHOW_CALLER, Message.INVALID_FORMAL_ARG_LIST, "function");
        }
        ArrayList<Argument<RSyntaxNode>> finalArgs = new ArrayList<>();
        Object argList = args;
        while (argList != RNull.instance) {
            if (!(argList instanceof RPairList)) {
                throw RError.error(RError.SHOW_CALLER, Message.INVALID_FORMAL_ARG_LIST, "function");
            }
            RPairList pl = (RPairList) argList;
            String name = ((RSymbol) pl.getTag()).getName();
            RSyntaxNode value = RASTUtils.createNodeForValue(pl.car()).asRSyntaxNode();
            finalArgs.add(RCodeBuilder.argument(RSyntaxNode.LAZY_DEPARSE, name, value));
            argList = pl.cdr();
        }
        return finalArgs;
    }

    @Override
    public RootCallTarget rootFunction(TruffleRLanguage language, SourceSection source, List<Argument<RSyntaxNode>> params, RSyntaxNode body, String name, FunctionScope functionScope) {
        assert functionScope != null;
        // Parse argument list
        logger.finer("rootFunction '" + name + "'");
        RNode[] defaultValues = new RNode[params.size()];
        SaveArgumentsNode saveArguments;
        AccessArgumentNode[] argAccessNodes = new AccessArgumentNode[params.size()];
        SourceSection[] argSourceSections = new SourceSection[params.size()];
        PostProcessArgumentsNode argPostProcess;
        RNode[] init = new RNode[params.size()];
        for (int i = 0; i < params.size(); i++) {
            Argument<RSyntaxNode> arg = params.get(i);
            // Parse argument's default value
            RNode defaultValue;
            if (arg.value != null) {
                // default argument initialization is, in a sense, quite similar to local
                // variable write and thus should do appropriate state transition and/or
                // RShareable copy if need be
                defaultValue = WrapArgumentNode.create(arg.value.asRNode(), i);
            } else {
                defaultValue = null;
            }

            // Create an initialization statement
            AccessArgumentNode accessArg = AccessArgumentNode.create(i);
            argAccessNodes[i] = accessArg;
            init[i] = WriteVariableNode.createArgSave(arg.name, accessArg);

            // Store formal arguments
            defaultValues[i] = defaultValue;

            argSourceSections[i] = arg.source;
        }

        saveArguments = new SaveArgumentsNode(init);
        if (!params.isEmpty() && !RContext.getInstance().getOption(RefCountIncrementOnly)) {
            argPostProcess = PostProcessArgumentsNode.create(params.size());
        } else {
            argPostProcess = null;
        }

        FormalArguments formals = FormalArguments.createForFunction(defaultValues, createSignature(params));

        for (AccessArgumentNode access : argAccessNodes) {
            access.setFormals(formals);
        }

        // Local variables
        logger.fine(() -> String.format("rootFunction('%s'): functionScope = %s", name, functionScope != FunctionScope.EMPTY_SCOPE ? functionScope.toString() : "empty scope"));
        String functionFrameDescriptorName = name != null && !name.isEmpty() ? name : "<function>";
        FrameDescriptor descriptor = FrameSlotChangeMonitor.createFunctionFrameDescriptor(functionFrameDescriptorName, functionScope);
        FunctionDefinitionNode rootNode = FunctionDefinitionNode.create(language, source, descriptor, argSourceSections, saveArguments, body, formals, name, argPostProcess);

        if (RContext.getInstance().getOption(ForceSources)) {
            // forces source sections to be generated
            rootNode.getSourceSection();
        }
        return rootNode.getCallTarget();
    }

    @Override
    public void setContext(CodeBuilderContext context) {
        this.context = context;
    }

    @Override
    public CodeBuilderContext getContext() {
        return context;
    }

    @Override
    public RSyntaxNode constant(SourceSection source, Object value) {
        recordExpr(source);
        if (value instanceof String && !RRuntime.isNA((String) value)) {
            return ConstantNode.create(source, Utils.intern((String) value));
        }
        if (RSharingAttributeStorage.isShareable(value)) {
            RSharingAttributeStorage shareable = (RSharingAttributeStorage) value;
            if (!shareable.isSharedPermanent()) {
                return ConstantNode.create(source, shareable.makeSharedPermanent());
            }
        }
        return ConstantNode.create(source, value);
    }

    @Override
    public RSyntaxNode specialLookup(SourceSection source, String symbol, boolean functionLookup, FunctionScope functionScope) {
        logger.finer("specialLookup '" + symbol + "'");
        assert source != null;
        if (!functionLookup) {
            int index = RSyntaxLookup.getVariadicComponentIndex(symbol);
            if (index != -1) {
                return new ReadVariadicComponentNode(source, index > 0 ? index - 1 : index);
            }
        }
        // Pre-initialization of ReadVariableNodes for local variables is not implemented for local
        // function variables - they are mostly promises that has to be evaluated first anyway, and
        // the evaluation of the promise has to be done on slow-path.
        if (functionScope != null && !functionLookup) {
            Integer locVarFrameIdx = functionScope.getLocalVariableFrameIndex(symbol);
            if (locVarFrameIdx != null) {
                logger.fine(() -> "Creating ReadNode for local variable " + symbol);
                assert FrameIndex.isInitializedIndex(locVarFrameIdx);
                var readVariableNode = ReadVariableNode.createLocalVariableLookup(symbol, locVarFrameIdx);
                return ReadVariableNode.wrap(source, readVariableNode);
            }
        }
        return ReadVariableNode.wrap(source, functionLookup ? ReadVariableNode.createForcedFunctionLookup(symbol) : ReadVariableNode.create(symbol));
    }

    @Override
    public RSyntaxNode lookup(SourceSection source, String symbol, boolean functionLookup, FunctionScope functionScope) {
        assert source != null;
        recordExpr(source);
        return specialLookup(source, symbol, functionLookup, functionScope);
    }

    private void recordExpr(SourceSection source) {
        if (parseDataBuilder != null) {
            parseDataBuilder.expr(source);
        }
    }
}
