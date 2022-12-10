/*
 * Copyright (c) 2015, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.r.nodes.test;

import org.junit.Assert;
import org.junit.Test;

import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.r.runtime.ArgumentsSignature;
import static com.oracle.truffle.r.runtime.context.FastROptions.UseSpecials;
import com.oracle.truffle.r.runtime.RError;
import com.oracle.truffle.r.runtime.RRuntime;
import com.oracle.truffle.r.runtime.RootWithBody;
import com.oracle.truffle.r.runtime.context.RContext;
import com.oracle.truffle.r.runtime.data.RExpression;
import com.oracle.truffle.r.runtime.data.RPairList;
import com.oracle.truffle.r.runtime.nodes.RCodeBuilder;
import com.oracle.truffle.r.runtime.nodes.RSyntaxCall;
import com.oracle.truffle.r.runtime.nodes.RSyntaxConstant;
import com.oracle.truffle.r.runtime.nodes.RSyntaxElement;
import com.oracle.truffle.r.runtime.nodes.RSyntaxFunction;
import com.oracle.truffle.r.runtime.nodes.RSyntaxLookup;
import com.oracle.truffle.r.runtime.nodes.RSyntaxNode;
import com.oracle.truffle.r.runtime.nodes.RSyntaxVisitor;

public class SpecialCallTest extends TestBase {
    private static final class CountCallsVisitor extends RSyntaxVisitor<Void> {

        public int normal;
        public int special;

        CountCallsVisitor(RootCallTarget callTarget) {
            accept(((RootWithBody) callTarget.getRootNode()).getBody());
        }

        @Override
        protected Void visit(RSyntaxCall element) {
            switch (element.getClass().getSimpleName()) {
                case "SpecialReplacementNode":
                case "SpecialVoidReplacementNode":
                case "RCallSpecialNode":
                    special++;
                    break;
                case "RCallNodeGen":
                case "GenericReplacementNode":
                    normal++;
                    break;
                case "ReplacementDispatchNode":
                case "WriteVariableSyntaxNode":
                case "BlockNode":
                    // ignored
                    break;
                default:
                    throw new AssertionError("unexpected class: " + element.getClass().getSimpleName());
            }
            accept(element.getSyntaxLHS());
            for (RSyntaxElement arg : element.getSyntaxArguments()) {
                accept(arg);
            }
            return null;
        }

        @Override
        protected Void visit(RSyntaxConstant element) {
            return null;
        }

        @Override
        protected Void visit(RSyntaxLookup element) {
            return null;
        }

        @Override
        protected Void visit(RSyntaxFunction element) {
            for (RSyntaxElement arg : element.getSyntaxArgumentDefaults()) {
                accept(arg);
            }
            accept(element.getSyntaxBody());
            return null;
        }
    }

    private static final class PrintCallsVisitor extends RSyntaxVisitor<Void> {

        private String indent = "";

        void print(RootCallTarget callTarget) {
            System.out.println();
            accept(((RootWithBody) callTarget.getRootNode()).getBody());
        }

        @Override
        protected Void visit(RSyntaxCall element) {
            System.out.println(indent + "call " + element.getClass().getSimpleName());
            indent += "    ";
            System.out.println(indent.substring(2) + "lhs:");
            accept(element.getSyntaxLHS());
            printArgs(element.getSyntaxSignature(), element.getSyntaxArguments());
            indent = indent.substring(4);
            return null;
        }

        @Override
        protected Void visit(RSyntaxConstant element) {
            System.out.println(indent + "constant " + element.getClass().getSimpleName() + " " + element.getValue().getClass().getSimpleName() + " " + element.getValue());
            return null;
        }

        @Override
        protected Void visit(RSyntaxLookup element) {
            System.out.println(indent + "lookup " + element.getClass().getSimpleName() + " " + element.getIdentifier());
            return null;
        }

        @Override
        protected Void visit(RSyntaxFunction element) {
            System.out.println(indent + "function " + element.getClass().getSimpleName());
            indent += "    ";
            printArgs(element.getSyntaxSignature(), element.getSyntaxArgumentDefaults());
            indent = indent.substring(4);
            for (RSyntaxElement arg : element.getSyntaxArgumentDefaults()) {
                accept(arg);
            }
            System.out.println(indent.substring(2) + "body:");
            accept(element.getSyntaxBody());
            return null;
        }

        private void printArgs(ArgumentsSignature signature, RSyntaxElement[] arguments) {
            for (int i = 0; i < arguments.length; i++) {
                System.out.println(indent.substring(2) + "arg " + (signature.getName(i) == null ? "<unnamed>" : signature.getName(i)));
                accept(arguments[i]);
            }
        }
    }

    @Test
    public void testBasic() {
        execInContext(() -> {
            // check a case with no calls
            assertCallCounts("library(stats)", 0, 1, 0, 1);
            return null;
        });
    }

    @Test
    public void testArithmetic() {
        execInContext(() -> {
            assertCallCounts("1 + 1", 1, 0, 1, 0);
            assertCallCounts("1 + 1 * 2 + 4", 3, 0, 3, 0);

            assertCallCounts("a <- 1; b <- 2", "a + b", 1, 0, 1, 0);
            assertCallCounts("a <- 1; b <- 2; c <- 3", "a + b * 2 * c", 3, 0, 3, 0);

            assertCallCounts("a <- data.frame(a=1); b <- 2; c <- 3", "a + b * 2 * c", 3, 0, 2, 1);
            assertCallCounts("a <- 1; b <- data.frame(a=1); c <- 3", "a + b * 2 * c", 3, 0, 0, 3);

            assertCallCounts("1 %*% 1", 0, 1, 0, 1);
            return null;
        });
    }

    @Test
    public void testSubset() {
        execInContext(() -> {
            assertCallCounts("a <- 1:10", "a[1]", 1, 0, 1, 0);
            assertCallCounts("a <- c(1,2,3,4)", "a[2]", 1, 0, 1, 0);
            assertCallCounts("a <- c(1,2,3,4)", "a[4]", 1, 0, 1, 0);
            assertCallCounts("a <- list(c(1,2,3,4),2,3)", "a[1]", 1, 0, 1, 0);
            assertCallCounts("a <- c(1,2,3,4)", "a[0.1]", 1, 0, 1, 0);
            assertCallCounts("a <- c(1,2,3,4)", "a[5]", 1, 0, 1, 0);
            assertCallCounts("a <- c(1,2,3,4)", "a[0]", 1, 0, 1, 0);
            assertCallCounts("a <- c(1,2,3,4); b <- -1", "a[b]", 1, 0, 1, 0);
            assertCallCounts("a <- c(1,2,3,4)", "a[NA_integer_]", 1, 0, 1, 0);
            assertCallCounts("a <- c(1,2,3,4)", "a[-1]", 2, 0, 2, 0);

            assertCallCounts("a <- c(1,2,3,4)", "a[drop=T, 1]", 0, 1, 0, 1);
            assertCallCounts("a <- c(1,2,3,4)", "a[drop=F, 1]", 0, 1, 0, 1);
            assertCallCounts("a <- c(1,2,3,4)", "a[1, drop=F]", 0, 1, 0, 1);
            return null;
        });
    }

    @Test
    public void testSubscript() {
        execInContext(() -> {
            assertCallCounts("a <- 1:10", "a[[1]]", 1, 0, 1, 0);
            assertCallCounts("a <- c(1,2,3,4)", "a[[2]]", 1, 0, 1, 0);
            assertCallCounts("a <- c(1,2,3,4)", "a[[4]]", 1, 0, 1, 0);
            assertCallCounts("a <- list(c(1,2,3,4),2,3)", "a[[1]]", 1, 0, 1, 0);
            assertCallCounts("a <- list(a=c(1,2,3,4),2,3)", "a[[1]]", 1, 0, 1, 0);
            assertCallCounts("a <- c(1,2,3,4)", "a[[0.1]]", 1, 0, 1, 0);
            assertCallCounts("a <- c(1,2,3,4)", "a[[5]]", 1, 0, 1, 0);
            assertCallCounts("a <- c(1,2,3,4)", "a[[0]]", 1, 0, 1, 0);
            assertCallCounts("a <- c(1,2,3,4); b <- -1", "a[[b]]", 1, 0, 1, 0);
            assertCallCounts("a <- c(1,2,3,4)", "a[[NA_integer_]]", 1, 0, 1, 0);

            assertCallCounts("a <- c(1,2,3,4)", "a[[drop=T, 1]]", 0, 1, 0, 1);
            assertCallCounts("a <- c(1,2,3,4)", "a[[drop=F, 1]]", 0, 1, 0, 1);
            assertCallCounts("a <- c(1,2,3,4)", "a[[1, drop=F]]", 0, 1, 0, 1);
            return null;
        });
    }

    @Test
    public void testUpdateSubset() {
        execInContext(() -> {
            assertCallCounts("a <- 1:10", "a[1] <- 1", 1, 0, 1, 1); // sequence
            assertCallCounts("a <- c(1,2,3,4)", "a[2] <- 1", 1, 0, 2, 0);
            assertCallCounts("a <- c(1,2,3,4)", "a[4] <- 1", 1, 0, 2, 0);
            assertCallCounts("a <- list(c(1,2,3,4),2,3)", "a[1] <- 1", 1, 0, 2, 0);
            assertCallCounts("a <- c(1,2,3,4)", "a[0.1] <- 1", 1, 0, 1, 1);
            assertCallCounts("a <- c(1,2,3,4)", "a[5] <- 1", 1, 0, 1, 1);
            assertCallCounts("a <- c(1,2,3,4)", "a[0] <- 1", 1, 0, 1, 1);
            assertCallCounts("a <- c(1,2,3,4); b <- -1", "a[b] <- 1", 1, 0, 1, 1);
            assertCallCounts("a <- c(1,2,3,4)", "a[NA_integer_] <- 1", 1, 0, 1, 1);

            assertCallCounts("a <- c(1,2,3,4)", "a[-1] <- 1", 2, 0, 2, 1);
            assertCallCounts("a <- c(1,2,3,4)", "a[drop=T, 1] <- 1", 0, 1, 0, 2);
            assertCallCounts("a <- c(1,2,3,4)", "a[drop=F, 1] <- 1", 0, 1, 0, 2);
            assertCallCounts("a <- c(1,2,3,4)", "a[1, drop=F] <- 1", 0, 1, 0, 2);
            return null;
        });
    }

    @Test
    public void testUpdateSubscript() {
        execInContext(() -> {
            assertCallCounts("a <- 1:10", "a[[1]] <- 1", 1, 0, 1, 1); // sequence
            assertCallCounts("a <- c(1,2,3,4)", "a[[2]] <- 1", 1, 0, 2, 0);
            assertCallCounts("a <- c(1,2,3,4)", "a[[4]] <- 1", 1, 0, 2, 0);
            assertCallCounts("a <- list(c(1,2,3,4),2,3)", "a[[1]] <- 1", 1, 0, 2, 0);
            assertCallCounts("a <- list(a=c(1,2,3,4),2,3)", "a[[1]] <- 1", 1, 0, 2, 0);
            assertCallCounts("a <- c(1,2,3,4)", "a[[0.1]] <- 1", 1, 0, 1, 1);
            assertCallCounts("a <- c(1,2,3,4)", "a[[5]] <- 1", 1, 0, 1, 1);
            assertCallCounts("a <- c(1,2,3,4)", "a[[0]] <- 1", 1, 0, 1, 1);
            assertCallCounts("a <- c(1,2,3,4); b <- -1", "a[[b]] <- 1", 1, 0, 1, 1);
            assertCallCounts("a <- c(1,2,3,4)", "a[[NA_integer_]] <- 1", 1, 0, 1, 1);

            assertCallCounts("a <- c(1,2,3,4)", "a[[drop=T, 1]] <- 1", 0, 1, 0, 2);
            assertCallCounts("a <- c(1,2,3,4)", "a[[drop=F, 1]] <- 1", 0, 1, 0, 2);
            assertCallCounts("a <- c(1,2,3,4)", "a[[1, drop=F]] <- 1", 0, 1, 0, 2);
            return null;
        });
    }

    @Test
    public void testParens() {
        execInContext(() -> {
            assertCallCounts("a <- 1", "(a)", 1, 0, 1, 0);
            assertCallCounts("a <- 1", "(55)", 1, 0, 1, 0);
            assertCallCounts("a <- 1", "('asdf')", 1, 0, 1, 0);
            assertCallCounts("a <- 1; b <- 2", "(a + b)", 2, 0, 2, 0);
            assertCallCounts("a <- 1; b <- 2; c <- 3", "a + (b + c)", 3, 0, 3, 0);
            assertCallCounts("a <- 1; b <- 2; c <- 1:5", "a + (b + c)", 3, 0, 3, 0);
            return null;
        });
    }

    private static void assertCallCounts(String test, int initialSpecialCount, int initialNormalCount, int finalSpecialCount, int finalNormalCount) {
        assertCallCounts("{}", test, initialSpecialCount, initialNormalCount, finalSpecialCount, finalNormalCount);
    }

    private static void assertCallCounts(String setup, String test, int initialSpecialCount, int initialNormalCount, int finalSpecialCount, int finalNormalCount) {
        if (!RContext.getInstance().getOption(UseSpecials)) {
            return;
        }
        Source setupSource = Source.newBuilder(RRuntime.R_LANGUAGE_ID, "{" + setup + "}", "test").build();
        Source testSource = Source.newBuilder(RRuntime.R_LANGUAGE_ID, test, "test").build();

        RExpression setupExpression = testVMContext.getThisEngine().parse(setupSource, false).getExpression();
        RExpression testExpression = testVMContext.getThisEngine().parse(testSource, false).getExpression();
        assert setupExpression.getLength() == 1;
        assert testExpression.getLength() == 1;
        RCodeBuilder<RSyntaxNode> builder = RContext.getASTBuilder();
        RootCallTarget setupCallTarget = testVMContext.getThisEngine().makePromiseCallTarget(builder.process(((RPairList) setupExpression.getDataAt(0)).getSyntaxElement()).asRNode(), "test");
        RootCallTarget testCallTarget = testVMContext.getThisEngine().makePromiseCallTarget(builder.process(((RPairList) testExpression.getDataAt(0)).getSyntaxElement()).asRNode(), "test");

        try {
            CountCallsVisitor count1 = new CountCallsVisitor(testCallTarget);
            Assert.assertEquals("initial special call count '" + setup + "; " + test + "': ", initialSpecialCount, count1.special);
            Assert.assertEquals("initial normal call count '" + setup + "; " + test + "': ", initialNormalCount, count1.normal);

            MaterializedFrame frame = TestBase.testVMContext.stateREnvironment.getGlobalFrame();
            try {
                setupCallTarget.call(frame);
                testCallTarget.call(frame);
            } catch (RError e) {
                // ignore
            }

            CountCallsVisitor count2 = new CountCallsVisitor(testCallTarget);
            Assert.assertEquals("special call count after first call '" + setup + "; " + test + "': ", finalSpecialCount, count2.special);
            Assert.assertEquals("normal call count after first call '" + setup + "; " + test + "': ", finalNormalCount, count2.normal);

            try {
                setupCallTarget.call(frame);
                testCallTarget.call(frame);
            } catch (RError e) {
                // ignore
            }

            CountCallsVisitor count3 = new CountCallsVisitor(testCallTarget);
            Assert.assertEquals("special call count after second call '" + setup + "; " + test + "': ", finalSpecialCount, count3.special);
            Assert.assertEquals("normal call count after second call '" + setup + "; " + test + "': ", finalNormalCount, count3.normal);
        } catch (AssertionError e) {
            new PrintCallsVisitor().print(testCallTarget);
            throw e;
        }
    }
}
