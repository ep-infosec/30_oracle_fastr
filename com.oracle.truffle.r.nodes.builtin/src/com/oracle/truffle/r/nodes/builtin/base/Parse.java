/*
 * Copyright (c) 2014, 2022, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.truffle.r.nodes.builtin.CastBuilder.Predef.stringValue;
import static com.oracle.truffle.r.runtime.RError.Message.MUST_BE_STRING_OR_CONNECTION;
import static com.oracle.truffle.r.runtime.builtins.RBehavior.IO;
import static com.oracle.truffle.r.runtime.builtins.RBuiltinKind.INTERNAL;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.EnumSet;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleFile;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.r.nodes.builtin.RBuiltinNode;
import com.oracle.truffle.r.runtime.RError;
import com.oracle.truffle.r.runtime.RInternalError;
import com.oracle.truffle.r.runtime.RRuntime;
import com.oracle.truffle.r.runtime.RSource;
import com.oracle.truffle.r.runtime.RSrcref;
import com.oracle.truffle.r.runtime.Utils;
import com.oracle.truffle.r.runtime.builtins.RBuiltin;
import com.oracle.truffle.r.runtime.conn.ConnectionSupport;
import com.oracle.truffle.r.runtime.conn.RConnection;
import com.oracle.truffle.r.runtime.conn.RConnection.ReadLineWarning;
import com.oracle.truffle.r.runtime.conn.StdConnections;
import com.oracle.truffle.r.runtime.context.Engine.ParseException;
import com.oracle.truffle.r.runtime.context.Engine.ParsedExpression;
import com.oracle.truffle.r.runtime.context.Engine.ParserMetadata;
import com.oracle.truffle.r.runtime.context.RContext;
import com.oracle.truffle.r.runtime.data.RComplex;
import com.oracle.truffle.r.runtime.data.RDataFactory;
import com.oracle.truffle.r.runtime.data.RExpression;
import com.oracle.truffle.r.runtime.data.RIntVector;
import com.oracle.truffle.r.runtime.data.RNull;
import com.oracle.truffle.r.runtime.data.RPairList;
import com.oracle.truffle.r.runtime.data.RStringVector;
import com.oracle.truffle.r.runtime.data.RSymbol;
import com.oracle.truffle.r.runtime.data.nodes.attributes.SetFixedAttributeNode;
import com.oracle.truffle.r.runtime.env.REnvironment;

/**
 * Internal component of the {@code parse} base package function.
 *
 * <pre>
 * parse(file, n, text, prompt, srcfile, encoding)
 * </pre>
 *
 * There are two main modalities in the arguments:
 * <ul>
 * <li>Input is taken from "conn" or "text" (in which case conn==stdin(), but ignored).</li>
 * <li>Parse the entire input or just "n" "expressions". The FastR parser cannot handle the latter
 * case properly. It will parse the entire stream whereas GnuR stops after "n" expressions. So,
 * e.g., if there is a syntax error after the "n'th" expression, GnuR does not see it, whereas FastR
 * does and throws an error. However, if there is no error FastR can truncate the expressions vector
 * to length "n"</li>
 * </ul>
 * Despite the modality there is no value in multiple specializations for what is an inherently
 * slow-path builtin.
 * <p>
 * The inputs do not lend themselves to the correct creation of {@link Source} attributes for the
 * FastR AST. In particular the {@code source} builtin reads the input internally and calls us the
 * "text" variant. However useful information regarding the origin of the input can be found either
 * in the connection info or in the "srcfile" argument which, if not {@code RNull#instance} is an
 * {@link REnvironment} with relevant data. So we can fix up the {@link Source} attributes on the
 * AST after the parse. It's relevant to do this for the Truffle instrumentation framework.
 * <p>
 * On the R side, GnuR adds similar R attributes to the result, which is important for R tooling.
 */
@RBuiltin(name = "parse", kind = INTERNAL, parameterNames = {"conn", "n", "text", "prompt", "srcfile", "encoding"}, behavior = IO)
public abstract class Parse extends RBuiltinNode.Arg6 {

    @Child private SetFixedAttributeNode setSrcRefAttrNode = SetFixedAttributeNode.create(RRuntime.R_SRCREF);
    @Child private SetFixedAttributeNode setWholeSrcRefAttrNode = SetFixedAttributeNode.create(RRuntime.R_WHOLE_SRCREF);
    @Child private SetFixedAttributeNode setSrcFileAttrNode = SetFixedAttributeNode.create(RRuntime.R_SRCFILE);

    static {
        Casts casts = new Casts(Parse.class);
        // Note: string is captured by the R wrapper and transformed to a file, other types not
        casts.arg("conn").defaultError(MUST_BE_STRING_OR_CONNECTION, "file").mustNotBeNull().asIntegerVector().findFirst();
        casts.arg("n").asIntegerVector().findFirst(RRuntime.INT_NA).replaceNA(-1);
        casts.arg("text").mustNotBeMissing().asStringVector();
        casts.arg("prompt").asStringVector().findFirst("?");
        casts.arg("encoding").mustBe(stringValue()).asStringVector().findFirst();
    }

    @TruffleBoundary
    @Specialization
    protected RExpression parse(int conn, int n, @SuppressWarnings("unused") RNull text, String prompt, Object srcFile, String encoding) {
        String[] lines;
        RConnection connection = RConnection.fromIndex(conn);
        if (connection == StdConnections.getStdin()) {
            throw RError.nyi(this, "parse from stdin not implemented");
        }
        try (RConnection openConn = connection.forceOpen("r")) {
            lines = openConn.readLines(0, EnumSet.noneOf(ReadLineWarning.class), false);
        } catch (IOException ex) {
            throw error(RError.Message.PARSE_ERROR);
        }
        return doParse(connection, n, coalesce(lines), prompt, srcFile, encoding);
    }

    @TruffleBoundary
    @Specialization
    protected RExpression parse(int conn, int n, RStringVector text, String prompt, Object srcFile, String encoding) {
        RConnection connection = RConnection.fromIndex(conn);
        return doParse(connection, n, coalesce(text), prompt, srcFile, encoding);
    }

    private RExpression doParse(RConnection conn, int n, String coalescedLines, @SuppressWarnings("unused") String prompt, Object srcFile, @SuppressWarnings("unused") String encoding) {
        if (coalescedLines.length() == 0 || n == 0) {
            return RDataFactory.createExpression(new Object[0]);
        }
        try {
            RContext context = getRContext();
            Source source = srcFile != RNull.instance ? createSource(context, srcFile, coalescedLines) : createSource(context, conn, coalescedLines);
            boolean keepSource = srcFile instanceof REnvironment;
            ParsedExpression parseRes = RContext.getEngine().parse(source, keepSource);
            RExpression exprs = parseRes.getExpression();
            if (n > 0 && n < exprs.getLength()) {
                Object[] subListData = new Object[n];
                for (int i = 0; i < n; i++) {
                    subListData[i] = exprs.getDataAt(i);
                }
                exprs = RDataFactory.createExpression(subListData);
            }
            // Handle the required R attributes
            if (keepSource) {
                addAttributes(parseRes, exprs, source, (REnvironment) srcFile);
            }
            return exprs;
        } catch (ParseException ex) {
            throw error(RError.Message.PARSE_ERROR);
        }
    }

    private static String coalesce(RStringVector lines) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lines.getLength(); i++) {
            sb.append(lines.getDataAt(i));
            if (i < lines.getLength() - 1) {
                sb.append('\n');
            }
        }
        return sb.toString();
    }

    private static String coalesce(String[] lines) {
        StringBuilder sb = new StringBuilder();
        for (String line : lines) {
            sb.append(line);
            sb.append('\n');
        }
        return sb.toString();
    }

    /**
     * Creates a {@link Source} object by gleaning information from {@code srcFile}.
     */
    private static Source createSource(RContext context, Object srcFile, String coalescedLines) {
        if (srcFile instanceof REnvironment) {
            REnvironment srcFileEnv = (REnvironment) srcFile;
            Object b = srcFileEnv.get("isFile");
            boolean isFile = RRuntime.fromLogical(RRuntime.asLogicalObject(b));
            if (isFile) {
                // Might be a URL
                String urlFileName = RRuntime.asString(srcFileEnv.get("filename"));
                assert urlFileName != null;
                String fileName = ConnectionSupport.removeFileURLPrefix(urlFileName);
                /*
                 * N.B. GnuR compatibility problem: Truffle Source does not handle ~ in pathnames
                 * but GnuR does not appear to do tilde expansion
                 */
                TruffleFile fnf = context.getSafeTruffleFile(fileName);
                String path = null;
                if (!fnf.isAbsolute()) {
                    String wd = RRuntime.asString(srcFileEnv.get("wd"));
                    path = String.join(context.getEnv().getFileNameSeparator(), wd, fileName);
                } else {
                    path = Utils.tildeExpand(fileName);
                }
                Source result = createFileSource(context, path, coalescedLines, false);
                assert result != null : "Source created from environment should not be null";
                return result;
            } else {
                return Source.newBuilder(RRuntime.R_LANGUAGE_ID, coalescedLines, "<parse>").build();
            }
        } else {
            String srcFileText = RRuntime.asString(srcFile);
            if (srcFileText.equals("<text>")) {
                return Source.newBuilder(RRuntime.R_LANGUAGE_ID, coalescedLines, "<parse>").build();
            } else {
                return createFileSource(context, ConnectionSupport.removeFileURLPrefix(srcFileText), coalescedLines, false);
            }
        }
    }

    private static Source createSource(RContext context, RConnection conn, String coalescedLines) {
        // TODO check if file
        ConnectionSupport.BaseRConnection bconn = ConnectionSupport.getBaseConnection(conn);
        String path = bconn.getSummaryDescription();
        return createFileSource(context, path, coalescedLines, bconn.isInternal());
    }

    private static Source createFileSource(RContext context, String path, String chars, boolean internal) {
        try {
            return RSource.fromFileName(context, chars, path, internal);
        } catch (URISyntaxException e) {
            // Note: to be compatible with GnuR we construct Source even with a malformed path
            return Source.newBuilder(RRuntime.R_LANGUAGE_ID, chars, path).internal(internal).build();
        }
    }

    private void addAttributes(ParsedExpression parseRes, RExpression exprs, Source source, REnvironment srcFile) {
        Object[] srcrefData = new Object[exprs.getLength()];
        for (int i = 0; i < srcrefData.length; i++) {
            Object data = exprs.getDataAt(i);
            if ((data instanceof RPairList && ((RPairList) data).isLanguage())) {
                SourceSection ss = ((RPairList) data).getSourceSection();
                srcrefData[i] = RSrcref.createLloc(ss, srcFile);
            } else if (data instanceof RSymbol) {
                srcrefData[i] = RNull.instance;
            } else if (data == RNull.instance) {
                srcrefData[i] = data;
            } else if (data instanceof Number || data instanceof RComplex || data instanceof String) {
                // in simple cases, result of parsing can be a scalar constant
                srcrefData[i] = data;
            } else {
                throw RInternalError.unimplemented("attribute of type " + data.getClass().getSimpleName());
            }
        }
        setSrcRefAttrNode.setAttr(exprs, RDataFactory.createList(srcrefData));
        int[] wholeSrcrefData = new int[8];
        int endOffset = source.getCharacters().length() - 1;
        wholeSrcrefData[0] = source.getLineNumber(0);
        wholeSrcrefData[3] = source.getLineNumber(endOffset);
        source.getColumnNumber(0);
        wholeSrcrefData[6] = wholeSrcrefData[0];
        wholeSrcrefData[6] = wholeSrcrefData[3];

        setWholeSrcRefAttrNode.setAttr(exprs, RDataFactory.createIntVector(wholeSrcrefData, RDataFactory.COMPLETE_VECTOR));
        setSrcFileAttrNode.setAttr(exprs, srcFile);

        ParserMetadata metadata = parseRes.getParseData();
        RIntVector parseData = RDataFactory.createIntVector(metadata.getData(), false);
        parseData.setAttr("tokens", RDataFactory.createStringVector(metadata.getTokens(), false));
        parseData.setAttr("text", RDataFactory.createStringVector(metadata.getText(), false));
        parseData.setClassAttr(RDataFactory.createStringVector("parseData"));
        parseData.setDimensions(new int[]{8, parseData.getLength() / 8});
        srcFile.safePut("parseData", parseData);
    }
}
