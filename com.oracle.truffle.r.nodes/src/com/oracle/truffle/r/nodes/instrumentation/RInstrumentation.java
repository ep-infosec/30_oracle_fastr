/*
 * Copyright (c) 2014, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.r.nodes.instrumentation;

import com.oracle.truffle.api.instrumentation.Instrumenter;
import com.oracle.truffle.api.instrumentation.SourceSectionFilter;
import com.oracle.truffle.api.instrumentation.SourceSectionFilter.IndexRange;
import com.oracle.truffle.api.instrumentation.StandardTags;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.r.nodes.function.FunctionDefinitionNode;
import com.oracle.truffle.r.runtime.RSource;
import com.oracle.truffle.r.runtime.context.RContext;
import com.oracle.truffle.r.runtime.data.RFunction;
import com.oracle.truffle.r.runtime.instrument.RSyntaxTags;

/**
 * Handles the initialization of the (NEW) instrumentation system which sets up various instruments
 * depending on command line options and provides utility methods for instrumentation-based tools.
 *
 */
public class RInstrumentation {

    public static FunctionDefinitionNode getFunctionDefinitionNode(RFunction func) {
        assert !func.isBuiltin();
        return (FunctionDefinitionNode) func.getRootNode();
    }

    public static SourceSection getSourceSection(RFunction func) {
        return func.getRootNode().getSourceSection();
    }

    public static SourceSectionFilter.Builder createFunctionStatementFilter(FunctionDefinitionNode fdn) {
        return createFunctionFilter(fdn, StandardTags.StatementTag.class);
    }

    public static SourceSectionFilter.Builder createFunctionFilter(FunctionDefinitionNode fdn, Class<?> tag) {
        /* Filter needs to check for statement tags in the range of the function in the Source */
        SourceSectionFilter.Builder builder = SourceSectionFilter.newBuilder();
        builder.tagIs(tag);
        SourceSection fdns = fdn.getSourceSection();
        builder.indexIn(fdns.getCharIndex(), fdns.getCharLength());
        Source source = fdns.getSource();
        builder.sourceIs(source::equals);
        return builder;
    }

    public static SourceSectionFilter.Builder createLineFilter(Source src, int line, Class<?> tag) {
        /* Filter needs to check for statement tags in the range of the function in the Source */
        SourceSectionFilter.Builder builder = SourceSectionFilter.newBuilder();
        builder.tagIs(tag);
        SourceSection lineSourceSection = src.createSection(line);
        builder.indexIn(lineSourceSection.getCharIndex(), lineSourceSection.getCharLength());
        builder.lineIn(lineSourceSection.getStartLine(), 1);
        builder.lineEndsIn(IndexRange.between(lineSourceSection.getEndLine(), lineSourceSection.getEndLine() + 1));
        builder.sourceIs(visitinSrc -> RSource.getPathInternal(src).equals(RSource.getPathInternal(visitinSrc)));
        return builder;
    }

    /**
     * Create a filter that matches the start function node (tag) in {@code func}.
     */
    public static SourceSectionFilter.Builder createFunctionStartFilter(RFunction func) {
        FunctionDefinitionNode fdn = (FunctionDefinitionNode) func.getRootNode();
        SourceSectionFilter.Builder builder = SourceSectionFilter.newBuilder();
        builder.tagIs(RSyntaxTags.FunctionBodyBlockTag.class);
        builder.sourceSectionEquals(fdn.getBody().getSourceSection());
        return builder;
    }

    /**
     * Activate the instrumentation system for {@code context}. Currently this simply checks for the
     * global (command-line) options for tracing and timing. They are applied to every context.
     */
    public static void activate(@SuppressWarnings("unused") RContext context) {
        // Check for function tracing
        RContext.getRRuntimeASTAccess().traceAllFunctions();
    }

    public static Instrumenter getInstrumenter() {
        return RContext.getInstance().getInstrumentationState().getInstrumenter();
    }
}
