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
package com.oracle.truffle.r.runtime;

import java.util.ArrayList;
import java.util.Arrays;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.frame.FrameSlotTypeException;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.r.runtime.RError.Message;
import com.oracle.truffle.r.runtime.context.RContext;
import com.oracle.truffle.r.runtime.context.TruffleRLanguage;
import com.oracle.truffle.r.runtime.data.RArgsValuesAndNames;
import com.oracle.truffle.r.runtime.data.RMissing;
import com.oracle.truffle.r.runtime.data.RPairList;
import com.oracle.truffle.r.runtime.data.RPromise;
import com.oracle.truffle.r.runtime.data.RPromise.EagerPromise;
import com.oracle.truffle.r.runtime.data.RSymbol;
import com.oracle.truffle.r.runtime.env.REnvironment;
import com.oracle.truffle.r.runtime.env.frame.FrameSlotChangeMonitor;
import com.oracle.truffle.r.runtime.nodes.RCodeBuilder;
import com.oracle.truffle.r.runtime.nodes.RCodeBuilder.Argument;
import com.oracle.truffle.r.runtime.nodes.RSyntaxCall;
import com.oracle.truffle.r.runtime.nodes.RSyntaxConstant;
import com.oracle.truffle.r.runtime.nodes.RSyntaxElement;
import com.oracle.truffle.r.runtime.nodes.RSyntaxFunction;
import com.oracle.truffle.r.runtime.nodes.RSyntaxLookup;
import com.oracle.truffle.r.runtime.nodes.RSyntaxNode;
import com.oracle.truffle.r.runtime.nodes.RSyntaxVisitor;
import com.oracle.truffle.r.runtime.parsermetadata.FunctionScope;

public class RSubstitute {

    /**
     * The heart of the {@code substitute} function, where we look up the value of {@code name} in
     * {@code env} and, if bound, return whatever value it had (as an {@link RSyntaxElement},or
     * {@code null} if not bound.
     */
    private static <T> RSyntaxElement substituteElement(Object val, RCodeBuilder<T> builder) {
        if (val == null) {
            // not bound in env,
            return null;
        } else if (val instanceof RMissing) {
            // strange special case, mimics GnuR behavior
            return RSyntaxLookup.createDummyLookup(RSyntaxNode.LAZY_DEPARSE, "", false);
        } else if (val instanceof RPromise) {
            RPromise promise = (RPromise) val;
            return substitutePromise(promise, builder);
        } else if ((val instanceof RPairList && ((RPairList) val).isLanguage())) {
            return ((RPairList) val).getSyntaxElement();
        } else if (val instanceof RSymbol) {
            return RSyntaxLookup.createDummyLookup(RSyntaxNode.LAZY_DEPARSE, ((RSymbol) val).getName(), false);
        } else if (val instanceof RArgsValuesAndNames) {
            return RSyntaxConstant.createDummyConstant(RSyntaxNode.LAZY_DEPARSE, val);
        } else {
            // An actual value
            return RSyntaxConstant.createDummyConstant(RSyntaxNode.LAZY_DEPARSE, val);
        }
    }

    /**
     * Promises may contain two types of "..X" (e.g. "..1") lookups. One is actual read of this
     * variable, the other may be transitive varargs component reference, which we should follow and
     * not interpret as syntax.
     */
    private static <T> RSyntaxElement substitutePromise(RPromise promise, RCodeBuilder<T> builder) {
        RSyntaxNode result = promise.getRep().asRSyntaxNode();
        if (result instanceof RSyntaxLookup && ((RSyntaxLookup) result).isPromiseLookup()) {
            int dotIdx = RSyntaxLookup.getVariadicComponentIndex(((RSyntaxLookup) result).getIdentifier());
            if (dotIdx != -1) {
                if (promise instanceof EagerPromise) {
                    ((EagerPromise) promise).materialize();
                }
                MaterializedFrame promiseFrame = promise.getFrame();
                int dotsFrameIndex = FrameSlotChangeMonitor.getIndexOfIdentifier(promiseFrame.getFrameDescriptor(), "...");
                RArgsValuesAndNames dots = null;
                try {
                    dots = (RArgsValuesAndNames) FrameSlotChangeMonitor.getObject(promiseFrame, dotsFrameIndex);
                } catch (FrameSlotTypeException e) {
                    e.printStackTrace();
                }
                assert dots != null && dotIdx <= dots.getLength() : "promise to a vararg .." + dotIdx + " outside of the varargs bounds";
                return substituteElement(dots.getArgument(dotIdx - 1), builder);
            }
        }
        return result;
    }

    private static boolean isLookup(RSyntaxElement element, String identifier) {
        return element instanceof RSyntaxLookup && identifier.equals(((RSyntaxLookup) element).getIdentifier());
    }

    /**
     * This method returns a newly created AST fragment for the given original element, with the
     * given substitutions.<br/>
     * We have to examine all the names in the expression:
     * <ul>
     * <li>Ordinary variable, replace by value (if bound), else unchanged</li>
     * <li>promise (aka function argument): replace by expression associated with the promise</li>
     * <li>..., replace by contents of ... (if bound)</li>
     * </ul>
     */
    private static <T> T substitute(RCodeBuilder<T> builder, RSyntaxElement original, REnvironment callEnv, TruffleRLanguage language) {
        return new RSyntaxVisitor<T>() {

            REnvironment env = callEnv;

            @Override
            protected T visit(RSyntaxCall element) {
                RSyntaxElement lhs = element.getSyntaxLHS();
                RSyntaxElement[] arguments = element.getSyntaxArguments();
                /*
                 * Handle the special case of replacements in a$b, a@b, a$b<- and a@b<-, where FastR
                 * currently uses a string constant instead of a lookup.
                 */
                if ((arguments.length == 2 && isLookup(lhs, "$") || isLookup(lhs, "@")) || (arguments.length == 3 && isLookup(lhs, "$<-") || isLookup(lhs, "@<-"))) {
                    if (arguments[1] instanceof RSyntaxConstant) {
                        String field = RRuntime.asStringLengthOne(((RSyntaxConstant) arguments[1]).getValue());
                        if (field != null) {
                            RSyntaxElement substitute = substituteElement(env.get(field), builder);
                            if (substitute instanceof RSyntaxLookup) {
                                substitute = RSyntaxConstant.createDummyConstant(RSyntaxNode.LAZY_DEPARSE, ((RSyntaxLookup) substitute).getIdentifier());
                            }
                            if (substitute instanceof RSyntaxConstant) {
                                arguments = Arrays.copyOf(arguments, arguments.length, RSyntaxElement[].class);
                                arguments[1] = substitute;
                            }
                        }
                    }
                }
                ArrayList<Argument<T>> args = createArguments(element.getSyntaxSignature(), arguments);
                return builder.call(RSyntaxNode.LAZY_DEPARSE, accept(lhs), args);
            }

            private ArrayList<Argument<T>> createArguments(ArgumentsSignature signature, RSyntaxElement[] arguments) {
                ArrayList<Argument<T>> args = new ArrayList<>(arguments.length);
                for (int i = 0; i < arguments.length; i++) {
                    RSyntaxElement arg = arguments[i];
                    // handle replacement of "..."
                    if (arg != null && arg instanceof RSyntaxLookup) {
                        RSyntaxLookup lookup = (RSyntaxLookup) arg;
                        if (ArgumentsSignature.VARARG_NAME.equals(lookup.getIdentifier())) {
                            Object substitute = env.get(ArgumentsSignature.VARARG_NAME);
                            if (substitute != null) {
                                if (!(substitute instanceof RArgsValuesAndNames)) {
                                    throw RError.error(RError.SHOW_CALLER, Message.NO_DOT_DOT_DOT);
                                }
                                RArgsValuesAndNames dots = (RArgsValuesAndNames) substitute;
                                for (int j = 0; j < dots.getLength(); j++) {
                                    Object dotArg = dots.getArgument(j);
                                    RSyntaxElement contents = substituteElement(dotArg, builder);
                                    if (dotArg instanceof RPromise) {
                                        // Replace the environment used to substitute symbols in a
                                        // promise by the empty one to prevent an infinite recursion
                                        // if the promise contains a vararg
                                        REnvironment origEnv = env;
                                        env = REnvironment.emptyEnv();
                                        args.add(RCodeBuilder.argument(RSyntaxNode.LAZY_DEPARSE, dots.getSignature().getName(j), accept(contents)));
                                        env = origEnv;
                                    } else {
                                        args.add(RCodeBuilder.argument(RSyntaxNode.LAZY_DEPARSE, dots.getSignature().getName(j), accept(contents)));
                                    }
                                }
                                continue;
                            }
                        }
                    }
                    args.add(RCodeBuilder.argument(arg == null ? null : RSyntaxNode.LAZY_DEPARSE, signature.getName(i), arg == null ? null : accept(arg)));
                }
                return args;
            }

            @Override
            protected T visit(RSyntaxConstant element) {
                return builder.constant(RSyntaxNode.LAZY_DEPARSE, element.getValue());
            }

            @Override
            protected T visit(RSyntaxLookup element) {
                // this takes care of replacing variable lookups
                RSyntaxElement substitute = substituteElement(env.get(element.getIdentifier()), builder);
                if (substitute != null) {
                    return builder.process(substitute);
                } else {
                    return builder.lookup(RSyntaxNode.LAZY_DEPARSE, element.getIdentifier(), element.isFunctionLookup());
                }
            }

            @Override
            protected T visit(RSyntaxFunction element) {
                ArrayList<Argument<T>> params = createArguments(element.getSyntaxSignature(), element.getSyntaxArgumentDefaults());
                // TODO: Search for localVariables
                return builder.function(language, RSyntaxNode.LAZY_DEPARSE, params, accept(element.getSyntaxBody()), element.getSyntaxDebugName(), FunctionScope.EMPTY_SCOPE);
            }
        }.accept(original);
    }

    @TruffleBoundary
    public static RSyntaxNode substitute(REnvironment env, RSyntaxElement node, TruffleRLanguage language) {
        return substitute(RContext.getASTBuilder(), node, env, language);
    }
}
