/*
 * Copyright (c) 1995-2015, The R Core Team
 * Copyright (c) 2003, The R Foundation
 * Copyright (c) 2016, 2020, Oracle and/or its affiliates
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
package com.oracle.truffle.r.runtime;

import java.io.IOException;
import java.nio.file.NoSuchFileException;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleFile;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.r.runtime.context.RContext;
import com.oracle.truffle.r.runtime.data.RDataFactory;
import com.oracle.truffle.r.runtime.data.RList;
import com.oracle.truffle.r.runtime.data.RNull;
import com.oracle.truffle.r.runtime.data.RStringVector;
import com.oracle.truffle.r.runtime.data.RIntVector;
import com.oracle.truffle.r.runtime.env.REnvironment;
import com.oracle.truffle.r.runtime.env.REnvironment.PutException;
import com.oracle.truffle.r.runtime.ffi.BaseRFFI;
import com.oracle.truffle.r.runtime.nodes.RSourceSectionNode;
import com.oracle.truffle.r.runtime.nodes.RSyntaxCall;
import com.oracle.truffle.r.runtime.nodes.RSyntaxConstant;
import com.oracle.truffle.r.runtime.nodes.RSyntaxElement;
import com.oracle.truffle.r.runtime.nodes.RSyntaxFunction;
import com.oracle.truffle.r.runtime.nodes.RSyntaxLookup;
import com.oracle.truffle.r.runtime.nodes.RSyntaxNode;
import com.oracle.truffle.r.runtime.nodes.RSyntaxVisitor;

/**
 * Utilities for handling R srcref attributes, in particular conversion from {@link Source},
 * {@link SourceSection} instances.
 *
 * {@code srcfile} corresponds loosely to a {@link Source} and a {@code srcref} to a
 * {@link SourceSection}.
 *
 * GnuR creates a significant amount of detail in the parser that is captured in a srcref, but the
 * FastR parser does not match it in detail.
 *
 *
 * See https://stat.ethz.ch/R-manual/R-devel/library/base/html/srcfile.html.
 */
public class RSrcref {
    public enum SrcrefFields {
        Enc,
        encoding,
        timestamp,
        filename,
        isFile,
        wd,
        fixedNewlines,
        lines;
    }

    private static final RStringVector SRCREF_ATTR = RDataFactory.createStringVectorFromScalar(RRuntime.R_SRCREF);
    private static final RStringVector SRCFILE_ATTR = RDataFactory.createStringVectorFromScalar(RRuntime.R_SRCFILE);

    /**
     * Internal version of srcfile(path).
     */
    public static REnvironment createSrcfile(RContext context, String path, Set<Object> envRefHolder) {
        TruffleFile file = context.getSafeTruffleFile(path);
        return createSrcfile(context, file, envRefHolder);
    }

    @TruffleBoundary
    static REnvironment createSrcfile(RContext context, TruffleFile path, Set<Object> envRefHolder) {
        // A srcfile is an environment
        REnvironment env = context.srcfileEnvironments.get(path);
        if (env == null) {
            env = RDataFactory.createNewEnv("");
            if (envRefHolder != null) {
                envRefHolder.add(path);
            }
            env.safePut(SrcrefFields.Enc.name(), "unknown");
            env.safePut(SrcrefFields.encoding.name(), "native.enc");
            env.safePut(SrcrefFields.timestamp.name(), getTimestamp(path));
            env.safePut(SrcrefFields.filename.name(), path.toString());
            env.safePut(SrcrefFields.isFile.name(), RRuntime.LOGICAL_TRUE);
            env.safePut(SrcrefFields.wd.name(), BaseRFFI.GetwdRootNode.create().getCallTarget().call());
            env.setClassAttr(SRCFILE_ATTR);
            RContext.getInstance().srcfileEnvironments.put(path, env);
        }
        return env;
    }

    private static int getTimestamp(TruffleFile path) {
        try {
            return Utils.getTimeInSecs(path.getLastModifiedTime());
        } catch (IOException ex) {
            return RRuntime.INT_NA;
        }
    }

    /**
     * Creates an {@code lloc} structure from a {@link SourceSection} and associated {@code srcfile}
     * . assert: srcfile was created from the {@link Source} associated with {@code ss} or is
     * {@code null} in which case it will be created from {@code ss}.
     */
    public static RIntVector createLloc(RContext context, SourceSection ss, String path) {
        return createLloc(ss, createSrcfile(context, path, null));
    }

    /**
     * Creates a block source reference or {@code null} if the function's body is not a block
     * statement.<br>
     * Srcref for blocks are different in that it is an RList of srcref vectors whereas each element
     * corresponds to one syntax call in the block (including the block itself). E.g.
     * <p>
     * <code> {<br/>
     * print('Hello')<br/>
     * print(x)<br/>
     * }</code>
     * </p>
     * will result in [[1, 20, 4, 1, 20, 1, 1, 4], [2, 2, 2, 15, 2, 15, 2, 2], [3, 2, 3, 9, 2, 9, 3,
     * 3]]
     *
     * @param function
     */
    @TruffleBoundary
    public static RList createBlockSrcrefs(RSyntaxElement function) {

        BlockSrcrefsVisitor v = new BlockSrcrefsVisitor();
        v.accept(function);
        List<Object> blockSrcrefs = v.blockSrcrefs;

        if (!blockSrcrefs.isEmpty()) {
            return RDataFactory.createList(blockSrcrefs.toArray());
        }
        return null;
    }

    @TruffleBoundary
    public static Object createLloc(RContext context, SourceSection src) {
        if (src == null) {
            return RNull.instance;
        }
        if (src == RSyntaxNode.INTERNAL || src == RSyntaxNode.LAZY_DEPARSE || src == RSyntaxNode.SOURCE_UNAVAILABLE) {
            return RNull.instance;
        }
        Source source = src.getSource();
        REnvironment env = RContext.getInstance().sourceRefEnvironments.get(source);
        if (env == null) {
            env = RDataFactory.createNewEnv("src");
            env.setClassAttr(RDataFactory.createStringVector(new String[]{"srcfilecopy", RRuntime.R_SRCFILE}, true));
            try {
                String pathStr = RSource.getPathInternal(source);
                TruffleFile path = context.getSafeTruffleFile(pathStr != null ? pathStr : "");

                env.put(SrcrefFields.filename.name(), path.toString());
                env.put(SrcrefFields.fixedNewlines.name(), RRuntime.LOGICAL_TRUE);
                String[] lines = new String[source.getLineCount()];
                for (int i = 0; i < lines.length; i++) {
                    lines[i] = source.getCharacters(i + 1).toString();
                }
                env.put(SrcrefFields.lines.name(), RDataFactory.createStringVector(lines, true));
                env.safePut(SrcrefFields.Enc.name(), "unknown");
                env.safePut(SrcrefFields.isFile.name(), RRuntime.asLogical(path.isRegularFile()));
                env.safePut(SrcrefFields.timestamp.name(), getTimestamp(path));
                env.safePut(SrcrefFields.wd.name(), BaseRFFI.GetwdRootNode.create().getCallTarget().call());
            } catch (PutException e) {
                throw RInternalError.shouldNotReachHere(e);
            }
            RContext.getInstance().sourceRefEnvironments.put(source, env);
        }
        return createLloc(src, env);
    }

    @TruffleBoundary
    public static RIntVector createLloc(SourceSection ss, REnvironment srcfile) {
        /*
         * TODO: it's unclear what the exact format is, experimentally it is (first line, first
         * column, last line, last column, first column, last column, first line, last line). the
         * second pair of columns is likely bytes instead of chars, and the second pair of lines
         * parsed as opposed to "real" lines (may be modified by #line).
         */
        int[] llocData = new int[8];
        int startLine = ss.getStartLine();
        int startColumn = ss.getStartColumn();
        int lastLine = ss.getEndLine();
        int lastColumn = ss.getEndColumn();
        // no multi-byte support, so byte==char
        llocData[0] = startLine;
        llocData[1] = startColumn;
        llocData[2] = lastLine;
        llocData[3] = lastColumn;
        llocData[4] = startColumn;
        llocData[5] = lastColumn;
        llocData[6] = startLine;
        llocData[7] = lastLine;
        RIntVector lloc = RDataFactory.createIntVector(llocData, RDataFactory.COMPLETE_VECTOR);
        lloc.setClassAttr(SRCREF_ATTR);
        lloc.setAttr(RRuntime.R_SRCFILE, srcfile);
        return lloc;
    }

    public static SourceSection createSourceSection(RContext context, RIntVector srcrefVec, Source sharedSource) {
        try {
            Source source;
            if (sharedSource != null) {
                source = sharedSource;
            } else {
                Object srcfile = srcrefVec.getAttr(RRuntime.R_SRCFILE);
                assert srcfile instanceof REnvironment;
                source = RSource.fromSrcfile(context, (REnvironment) srcfile);
            }
            int startLine = srcrefVec.getDataAt(0);
            int startColumn = srcrefVec.getDataAt(1);
            int startIdx = getLineStartOffset(source, startLine) + startColumn;
            int length = getLineStartOffset(source, srcrefVec.getDataAt(2)) + srcrefVec.getDataAt(3) - startIdx + 1;
            return source.createSection(startLine, startColumn, length);
        } catch (NoSuchFileException e) {
            assert debugWarning("Missing source file: " + e.getMessage());
        } catch (IOException e) {
            assert debugWarning("Cannot access source file: " + e.getMessage());
        } catch (IllegalArgumentException e) {
            assert debugWarning("Invalid source reference: " + e.getMessage());
        }
        return RSourceSectionNode.LAZY_DEPARSE;
    }

    private static boolean debugWarning(String message) {
        RError.warning(RError.SHOW_CALLER, RError.Message.GENERIC, message);
        return true;
    }

    private static int getLineStartOffset(Source source, int lineNum) {
        try {
            return source.getLineStartOffset(lineNum);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(String.format("line %d does not exist in source %s", lineNum, RSource.getPathInternal(source)), e);
        }
    }

    private static final class BlockSrcrefsVisitor extends RSyntaxVisitor<Void> {
        private List<Object> blockSrcrefs = new LinkedList<>();
        private int depth = 0;

        @Override
        public Void visit(RSyntaxCall element) {

            if (depth == 0 && !isBlockStatement(element)) {
                return null;
            }

            addSrcref(blockSrcrefs, element);

            if (depth == 0) {
                RSyntaxElement[] syntaxArguments = element.getSyntaxArguments();
                for (int i = 0; i < syntaxArguments.length; i++) {
                    if (syntaxArguments[i] != null) {
                        depth++;
                        accept(syntaxArguments[i]);
                        depth--;
                    }
                }
            }
            return null;
        }

        private static void addSrcref(List<Object> blockSrcrefs, RSyntaxElement element) {
            SourceSection lazySourceSection = element.getLazySourceSection();
            if (lazySourceSection != null) {
                blockSrcrefs.add(createLloc(RContext.getInstance(), lazySourceSection));
            }
        }

        private static boolean isBlockStatement(RSyntaxCall element) {
            RSyntaxElement lhs = element.getSyntaxLHS();
            if (lhs instanceof RSyntaxLookup) {
                return "{".equals(((RSyntaxLookup) lhs).getIdentifier());
            }
            return false;
        }

        @Override
        public Void visit(RSyntaxConstant element) {
            addSrcref(blockSrcrefs, element);
            return null;
        }

        @Override
        public Void visit(RSyntaxLookup element) {
            addSrcref(blockSrcrefs, element);
            return null;
        }

        @Override
        public Void visit(RSyntaxFunction element) {
            accept(element.getSyntaxBody());
            return null;
        }
    }
}
