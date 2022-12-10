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

import static com.oracle.truffle.r.nodes.builtin.CastBuilder.Predef.asIntegerVector;
import static com.oracle.truffle.r.nodes.builtin.CastBuilder.Predef.constant;
import static com.oracle.truffle.r.nodes.builtin.CastBuilder.Predef.equalTo;
import static com.oracle.truffle.r.nodes.builtin.CastBuilder.Predef.findFirst;
import static com.oracle.truffle.r.nodes.builtin.CastBuilder.Predef.gte;
import static com.oracle.truffle.r.nodes.builtin.CastBuilder.Predef.instanceOf;
import static com.oracle.truffle.r.nodes.builtin.CastBuilder.Predef.integerValue;
import static com.oracle.truffle.r.nodes.builtin.CastBuilder.Predef.logicalTrue;
import static com.oracle.truffle.r.nodes.builtin.CastBuilder.Predef.logicalValue;
import static com.oracle.truffle.r.nodes.builtin.CastBuilder.Predef.lte;
import static com.oracle.truffle.r.nodes.builtin.CastBuilder.Predef.notEmpty;
import static com.oracle.truffle.r.nodes.builtin.CastBuilder.Predef.nullValue;
import static com.oracle.truffle.r.nodes.builtin.CastBuilder.Predef.rawValue;
import static com.oracle.truffle.r.nodes.builtin.CastBuilder.Predef.singleElement;
import static com.oracle.truffle.r.nodes.builtin.CastBuilder.Predef.stringValue;
import static com.oracle.truffle.r.nodes.builtin.CastBuilder.Predef.toBoolean;
import static com.oracle.truffle.r.runtime.RCompression.Type.GZIP;
import static com.oracle.truffle.r.runtime.RVisibility.OFF;
import static com.oracle.truffle.r.runtime.builtins.RBehavior.IO;
import static com.oracle.truffle.r.runtime.builtins.RBehavior.PURE;
import static com.oracle.truffle.r.runtime.builtins.RBehavior.READS_STATE;
import static com.oracle.truffle.r.runtime.builtins.RBuiltinKind.INTERNAL;
import static com.oracle.truffle.r.runtime.conn.ConnectionSupport.getBaseConnection;
import static com.oracle.truffle.r.runtime.conn.ConnectionSupport.removeFileURLPrefix;
import static com.oracle.truffle.r.runtime.conn.ConnectionSupport.ConnectionClass.GZCon;
import static com.oracle.truffle.r.runtime.conn.StdConnections.getStderr;
import static com.oracle.truffle.r.runtime.conn.StdConnections.getStdin;
import static com.oracle.truffle.r.runtime.conn.StdConnections.getStdout;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.DoubleBuffer;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;
import java.nio.channels.ByteChannel;
import java.nio.charset.Charset;
import java.nio.charset.IllegalCharsetNameException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.TypeSystemReference;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.profiles.ConditionProfile;
import com.oracle.truffle.r.nodes.builtin.NodeWithArgumentCasts.Casts;
import com.oracle.truffle.r.nodes.builtin.RBuiltinNode;
import com.oracle.truffle.r.nodes.builtin.base.ConnectionFunctionsFactory.WriteDataNodeGen;
import com.oracle.truffle.r.nodes.builtin.casts.fluent.HeadPhaseBuilder;
import com.oracle.truffle.r.runtime.DSLConfig;
import com.oracle.truffle.r.runtime.RCompression;
import com.oracle.truffle.r.runtime.RError;
import com.oracle.truffle.r.runtime.RError.Message;
import com.oracle.truffle.r.runtime.RInternalError;
import com.oracle.truffle.r.runtime.RRuntime;
import com.oracle.truffle.r.runtime.TempPathName;
import com.oracle.truffle.r.runtime.Utils;
import com.oracle.truffle.r.runtime.builtins.RBuiltin;
import com.oracle.truffle.r.runtime.builtins.RBuiltinKind;
import com.oracle.truffle.r.runtime.conn.ChannelConnections.ChannelRConnection;
import com.oracle.truffle.r.runtime.conn.ConnectionSupport;
import com.oracle.truffle.r.runtime.conn.ConnectionSupport.BaseRConnection;
import com.oracle.truffle.r.runtime.conn.FifoConnections.FifoRConnection;
import com.oracle.truffle.r.runtime.conn.FileConnections.CompressedRConnection;
import com.oracle.truffle.r.runtime.conn.FileConnections.FileRConnection;
import com.oracle.truffle.r.runtime.conn.PipeConnections.PipeRConnection;
import com.oracle.truffle.r.runtime.conn.RConnection;
import com.oracle.truffle.r.runtime.conn.RConnection.ReadLineWarning;
import com.oracle.truffle.r.runtime.conn.RawConnections.RawRConnection;
import com.oracle.truffle.r.runtime.conn.SocketConnections.RSocketConnection;
import com.oracle.truffle.r.runtime.conn.TextConnections.TextRConnection;
import com.oracle.truffle.r.runtime.conn.URLConnections.URLRConnection;
import com.oracle.truffle.r.runtime.context.RContext;
import com.oracle.truffle.r.runtime.data.RComplexVector;
import com.oracle.truffle.r.runtime.data.RDataFactory;
import com.oracle.truffle.r.runtime.data.RDoubleVector;
import com.oracle.truffle.r.runtime.data.RExpression;
import com.oracle.truffle.r.runtime.data.RIntVector;
import com.oracle.truffle.r.runtime.data.RList;
import com.oracle.truffle.r.runtime.data.RLogicalVector;
import com.oracle.truffle.r.runtime.data.RMissing;
import com.oracle.truffle.r.runtime.data.RNull;
import com.oracle.truffle.r.runtime.data.RRawVector;
import com.oracle.truffle.r.runtime.data.RStringVector;
import com.oracle.truffle.r.runtime.data.RTypes;
import com.oracle.truffle.r.runtime.data.model.RAbstractAtomicVector;
import com.oracle.truffle.r.runtime.data.model.RAbstractVector;
import com.oracle.truffle.r.runtime.data.nodes.VectorAccess;
import com.oracle.truffle.r.runtime.data.nodes.VectorAccess.SequentialIterator;
import com.oracle.truffle.r.runtime.env.REnvironment;
import com.oracle.truffle.r.runtime.nodes.RBaseNode;

/**
 * The builtins for connections.
 *
 * The implementations of {@link RConnection} corresponding to the builtins are in
 * {@code com.oracle.truffle.r.runime.conn}.
 *
 */
public abstract class ConnectionFunctions {
    @RBuiltin(name = "stdin", kind = INTERNAL, parameterNames = {}, behavior = READS_STATE)
    public abstract static class Stdin extends RBuiltinNode.Arg0 {
        @Specialization
        @TruffleBoundary
        protected RIntVector stdin() {
            return getStdin().asVector();
        }
    }

    @RBuiltin(name = "stdout", kind = INTERNAL, parameterNames = {}, behavior = READS_STATE)
    public abstract static class Stdout extends RBuiltinNode.Arg0 {
        @Specialization
        @TruffleBoundary
        protected RIntVector stdout() {
            return getStdout().asVector();
        }
    }

    @RBuiltin(name = "stderr", kind = INTERNAL, parameterNames = {}, behavior = READS_STATE)
    public abstract static class Stderr extends RBuiltinNode.Arg0 {
        @Specialization
        @TruffleBoundary
        protected RIntVector stderr() {
            return getStderr().asVector();
        }
    }

    public static final class CastsHelper {
        private static HeadPhaseBuilder<String> description(Casts casts) {
            return casts.arg("description").mustBe(stringValue()).asStringVector().shouldBe(singleElement(), RError.Message.ARGUMENT_ONLY_FIRST_1, "description").findFirst().mustNotBeNA();
        }

        private static HeadPhaseBuilder<String> open(Casts casts) {
            return casts.arg("open").mustBe(stringValue()).asStringVector().mustBe(singleElement()).findFirst().mustNotBeNA();
        }

        private static HeadPhaseBuilder<String> openMode(Casts casts) {
            return open(casts).mustBe(equalTo("").or(equalTo("r")).or(equalTo("rt")).or(equalTo("rb")).or(equalTo("r+")).or(equalTo("r+b")).or(equalTo("w")).or(equalTo("wt")).or(equalTo("wb")).or(
                            equalTo("wr")).or(equalTo("w+")).or(equalTo("w+b")).or(equalTo("a")).or(equalTo("at")).or(equalTo("a+")), RError.Message.UNSUPPORTED_MODE);
        }

        private static void encoding(Casts casts) {
            casts.arg("encoding").asStringVector().mustBe(singleElement()).findFirst();
        }

        private static void raw(Casts casts) {
            casts.arg("raw").asLogicalVector().findFirst().mustNotBeNA().map(toBoolean());
        }

        private static void blocking(Casts casts) {
            casts.arg("blocking").asLogicalVector().findFirst().mustNotBeNA().map(toBoolean());
        }

        public static void connection(Casts casts) {
            casts.arg("con").defaultError(Message.INVALID_CONNECTION).mustNotBeNull().asIntegerVector().findFirst();
        }

        private static void nchars(Casts casts) {
            casts.arg("nchars").mustNotBeMissing().mapIf(nullValue(), constant(0)).asIntegerVector().mustBe(notEmpty());
        }

        private static void useBytes(Casts casts) {
            casts.arg("useBytes").asLogicalVector().findFirst().mustNotBeNA().map(toBoolean());
        }

        private static void n(Casts casts) {
            casts.arg("n").asIntegerVector().findFirst().mustBe(gte(0));
        }

        private static void size(Casts casts) {
            casts.arg("size").asIntegerVector().findFirst();
        }

        private static void swap(Casts casts) {
            casts.arg("swap").asLogicalVector().findFirst().mustNotBeNA().map(toBoolean());
        }

        private static void method(Casts casts) {
            casts.arg("method").asStringVector().findFirst().mustBe(equalTo("default").or(equalTo("internal")), RError.Message.UNSUPPORTED_URL_METHOD);
        }

        static void blockingNotSupported(Casts casts) {
            casts.arg("blocking").asLogicalVector().findFirst().mustBe(logicalTrue(), RError.Message.NYI, "non-blocking mode not supported").map(toBoolean());
        }
    }

    @RBuiltin(name = "file", kind = INTERNAL, parameterNames = {"description", "open", "blocking", "encoding", "method", "raw"}, behavior = IO)
    public abstract static class File extends RBuiltinNode.Arg6 {

        static {
            Casts casts = new Casts(File.class);
            CastsHelper.description(casts);
            CastsHelper.open(casts);
            CastsHelper.blocking(casts);
            CastsHelper.encoding(casts);
            CastsHelper.method(casts);
            CastsHelper.raw(casts);
        }

        @Specialization
        @TruffleBoundary
        protected RIntVector file(String description, String openArg, boolean blocking, String encoding, @SuppressWarnings("unused") String method, boolean raw) {
            String open = openArg;

            // check if the description is an URL and dispatch if necessary
            String path = description;
            try {
                URL url = new URL(description);
                if (!"file".equals(url.getProtocol())) {
                    return new URLRConnection(description, open, encoding).asVector();
                } else {
                    path = removeFileURLPrefix(description);
                }
            } catch (MalformedURLException e) {
                // ignore and try to open file
            } catch (IOException e) {
                RError.warning(RError.SHOW_CALLER, RError.Message.UNABLE_TO_RESOLVE, e.getMessage());
                throw error(RError.Message.CANNOT_OPEN_CONNECTION);
            }

            if (path.length() == 0) {
                // special case, temp file opened in "w+" or "w+b" only
                if (open.length() == 0) {
                    open = "w+";
                } else {
                    if (!(open.equals("w+") || open.equals("w+b"))) {
                        open = "w+";
                        warning(RError.Message.FILE_OPEN_TMP);
                    }
                }
            }
            path = checkTemp(path);
            try {
                return new FileRConnection(description, getRContext().getSafeTruffleFile(path), open, blocking, encoding, raw, true).asVector();
            } catch (IOException ex) {
                warning(RError.Message.NO_SUCH_FILE, description);
                throw error(RError.Message.CANNOT_OPEN_CONNECTION);
            } catch (IllegalCharsetNameException ex) {
                throw error(RError.Message.UNSUPPORTED_ENCODING_CONVERSION, encoding, "");
            }
        }

        private String checkTemp(String path) {
            if (path.length() == 0) {
                return TempPathName.createNonExistingFilePath(getRContext(), "Rf", TempPathName.tempDirPath(RContext.getInstance()), "");
            } else {
                return path;
            }
        }
    }

    /*
     * In GNUR R {@code gzfile, bzfile, xzfile} are very versatile on input; they can open
     * uncompressed files, and files compressed by {@code bzip2, xz, lzma}.
     */

    public abstract static class ZZFileAdapter extends RBuiltinNode.Arg4 {
        private final RCompression.Type cType;

        protected ZZFileAdapter(RCompression.Type cType) {
            this.cType = cType;
        }

        protected static Casts createCasts(Class<? extends ZZFileAdapter> extCls, RCompression.Type cType) {
            Casts casts = new Casts(extCls);
            CastsHelper.description(casts);
            CastsHelper.open(casts);
            CastsHelper.encoding(casts);
            casts.arg("compression").asIntegerVector().findFirst().mustNotBeNA().mustBe(gte(cType == RCompression.Type.XZ ? -9 : 0).and(lte(9)));
            return casts;
        }

        @Specialization
        @TruffleBoundary
        protected RIntVector zzFile(String description, String open, String encoding, int compression) {
            try {
                return new CompressedRConnection(getRContext().getSafeTruffleFile(description), open, cType, encoding, compression).asVector();
            } catch (IOException ex) {
                throw reportError(description, ex);
            } catch (IllegalCharsetNameException ex) {
                throw error(RError.Message.UNSUPPORTED_ENCODING_CONVERSION, encoding, "");
            }
        }

        private RError reportError(String path, IOException ex) throws RError {
            warning(RError.Message.CANNOT_OPEN_FILE, path, ex.getMessage());
            throw error(RError.Message.CANNOT_OPEN_CONNECTION);
        }
    }

    @RBuiltin(name = "gzfile", kind = INTERNAL, parameterNames = {"description", "open", "encoding", "compression"}, behavior = IO)
    public abstract static class GZFile extends ZZFileAdapter {
        protected GZFile() {
            super(RCompression.Type.GZIP);
        }

        static {
            createCasts(GZFile.class, RCompression.Type.GZIP);
        }
    }

    @RBuiltin(name = "bzfile", kind = INTERNAL, parameterNames = {"description", "open", "encoding", "compression"}, behavior = IO)
    public abstract static class BZFile extends ZZFileAdapter {
        protected BZFile() {
            super(RCompression.Type.BZIP2);
        }

        static {
            createCasts(BZFile.class, RCompression.Type.BZIP2);
        }
    }

    @RBuiltin(name = "xzfile", kind = INTERNAL, parameterNames = {"description", "open", "encoding", "compression"}, behavior = IO)
    public abstract static class XZFile extends ZZFileAdapter {
        protected XZFile() {
            super(RCompression.Type.XZ);
        }

        static {
            createCasts(XZFile.class, RCompression.Type.XZ);
        }
    }

    @RBuiltin(name = "gzcon", kind = INTERNAL, parameterNames = {"con", "level", "allowNonCompressed", "text"}, behavior = PURE)
    public abstract static class GZCon extends RBuiltinNode.Arg4 {

        static {
            Casts casts = new Casts(GZCon.class);
            casts.arg("con").defaultError(RError.Message.NOT_CONNECTION, "con").mustNotBeNull().asIntegerVector().findFirst();
            casts.arg("level").asIntegerVector().findFirst();
            casts.arg("allowNonCompressed").asLogicalVector().findFirst().map(toBoolean());
            casts.arg("text").asLogicalVector().findFirst().map(toBoolean());
        }

        @Specialization
        public RIntVector gzcon(int conIndex, @SuppressWarnings("unused") int level, @SuppressWarnings("unused") boolean allowNonCompressed, @SuppressWarnings("unused") boolean text,
                        @Cached("createBinaryProfile()") ConditionProfile gzConProfile) {
            BaseRConnection base = RConnection.fromIndex(conIndex, getRContext());
            if (gzConProfile.profile(base.getConnectionClass() == ConnectionSupport.ConnectionClass.GZCon)) {
                RError.warning(this, RError.Message.IS_GZCON);
                return base.asVector();
            }
            try {
                base.setCompressionType(GZIP);
                base.updateConnectionClass(GZCon);
            } catch (IOException ex) {
                CompilerDirectives.transferToInterpreter();
                throw error(RError.Message.GENERIC, ex.getMessage());
            }
            return base.asVector();
        }

    }

    @RBuiltin(name = "textConnection", kind = INTERNAL, parameterNames = {"description", "text", "open", "env", "encoding"}, behavior = IO)
    public abstract static class TextConnection extends RBuiltinNode.Arg5 {

        static {
            Casts casts = new Casts(TextConnection.class);
            CastsHelper.description(casts);
            casts.arg("text").allowNull().mustBe(stringValue());
            CastsHelper.open(casts).mustBe(equalTo("").or(equalTo("r").or(equalTo("rt").or(equalTo("w").or(equalTo("wt").or(equalTo("a").or(equalTo("wr"))))))), RError.Message.UNSUPPORTED_MODE);
            casts.arg("env").mustNotBeNull(RError.Message.USE_NULL_ENV_DEFUNCT).mustBe(instanceOf(REnvironment.class));
            casts.arg("encoding").asIntegerVector().findFirst().mustNotBeNA();
        }

        @Specialization
        @TruffleBoundary
        protected RIntVector textConnection(String description, RStringVector text, String open, REnvironment env, @SuppressWarnings("unused") int encoding) {
            try {
                return new TextRConnection(description, text, env, open).asVector();
            } catch (IOException ex) {
                throw RInternalError.shouldNotReachHere();
            }
        }

        @Specialization
        protected RIntVector textConnection(String description, @SuppressWarnings("unused") RNull text, String open, REnvironment env, int encoding) {
            return textConnection(description, (RStringVector) null, open, env, encoding);
        }
    }

    @RBuiltin(name = "textConnectionValue", kind = INTERNAL, parameterNames = {"con"}, behavior = IO)
    public abstract static class TextConnectionValue extends RBuiltinNode.Arg1 {

        static {
            Casts casts = new Casts(TextConnectionValue.class);
            casts.arg("con").defaultError(Message.NOT_A_TEXT_CONNECTION).mustBe(integerValue()).asIntegerVector().findFirst();
        }

        @Specialization
        @TruffleBoundary
        protected RStringVector textConnection(int con) {
            RConnection connection = RConnection.fromIndex(con);
            if (connection instanceof TextRConnection) {
                return ((TextRConnection) connection).getValue();
            } else {
                throw error(Message.NOT_A_TEXT_CONNECTION);
            }
        }
    }

    @RBuiltin(name = "socketConnection", kind = INTERNAL, parameterNames = {"host", "port", "server", "blocking", "open", "encoding", "timeout"}, behavior = IO)
    public abstract static class SocketConnection extends RBuiltinNode.Arg7 {

        static {
            Casts casts = new Casts(SocketConnection.class);
            casts.arg("host").mustBe(stringValue()).asStringVector().findFirst();
            casts.arg("port").asIntegerVector().findFirst().mustNotBeNA().mustBe(gte(0));
            casts.arg("server").asLogicalVector().findFirst().mustNotBeNA().map(toBoolean());
            CastsHelper.open(casts);
            CastsHelper.blocking(casts);
            casts.arg("timeout").asIntegerVector().findFirst();
        }

        @Specialization
        @TruffleBoundary
        protected RIntVector socketConnection(String host, int port, boolean server, boolean blocking, String open,
                        String encoding, int timeout) {
            try {
                return new RSocketConnection(open, server, host, port, blocking, timeout, encoding).asVector();
            } catch (IOException ex) {
                throw error(RError.Message.CANNOT_OPEN_CONNECTION);
            } catch (IllegalCharsetNameException ex) {
                throw error(RError.Message.UNSUPPORTED_ENCODING_CONVERSION, encoding, "");
            }
        }
    }

    @RBuiltin(name = "url", kind = INTERNAL, parameterNames = {"description", "open", "blocking", "encoding", "method", "headers"}, behavior = IO)
    public abstract static class URLConnection extends RBuiltinNode.Arg6 {

        static {
            Casts casts = new Casts(URLConnection.class);
            CastsHelper.description(casts);
            CastsHelper.open(casts);
            CastsHelper.blocking(casts);
            CastsHelper.encoding(casts);
            CastsHelper.method(casts);
        }

        @Specialization
        @TruffleBoundary
        protected RIntVector urlConnection(String url, String open, @SuppressWarnings("unused") boolean blocking, String encoding,
                        @SuppressWarnings("unused") String method, @SuppressWarnings("unused") Object headers) {
            try {
                return new URLRConnection(url, open, encoding).asVector();
            } catch (MalformedURLException ex) {
                throw error(RError.Message.UNSUPPORTED_URL_SCHEME);
            } catch (IOException ex) {
                throw error(RError.Message.CANNOT_OPEN_CONNECTION);
            } catch (IllegalCharsetNameException ex) {
                throw error(RError.Message.UNSUPPORTED_ENCODING_CONVERSION, encoding, "");
            }
        }
    }

    @RBuiltin(name = "rawConnection", kind = INTERNAL, parameterNames = {"description", "object", "open"}, behavior = IO)
    public abstract static class RawConnection extends RBuiltinNode.Arg3 {

        static {
            Casts casts = new Casts(RawConnection.class);
            CastsHelper.description(casts);
            casts.arg("object").mustBe(stringValue().or(rawValue()));
            CastsHelper.openMode(casts);
        }

        @Specialization
        @TruffleBoundary
        protected RIntVector rawConnection(String description, RRawVector text, String open) {
            try {
                return new RawRConnection(description, text.getDataTemp(), open).asVector();
            } catch (IOException ex) {
                throw RInternalError.shouldNotReachHere();
            }
        }

        @Fallback
        protected RIntVector rawConnection(@SuppressWarnings("unused") Object description, @SuppressWarnings("unused") Object text, @SuppressWarnings("unused") Object open) {
            throw error(Message.INVALID_ARGUMENT, "raw");
        }
    }

    @RBuiltin(name = "rawConnectionValue", kind = INTERNAL, parameterNames = {"con"}, behavior = IO)
    public abstract static class RawConnectionValue extends RBuiltinNode.Arg1 {

        static {
            Casts casts = new Casts(RawConnectionValue.class);
            casts.arg("con").defaultError(Message.NOT_A_RAW_CONNECTION).mustBe(integerValue()).asIntegerVector().findFirst();
        }

        @Specialization
        @TruffleBoundary
        protected Object textConnection(int con) {
            BaseRConnection connection = RConnection.fromIndex(con);
            if (connection instanceof RawRConnection) {
                return RDataFactory.createRawVector(((RawRConnection) connection).getValue());
            } else {
                throw error(Message.NOT_A_RAW_CONNECTION);
            }
        }
    }

    @RBuiltin(name = "summary.connection", kind = INTERNAL, parameterNames = {"object"}, behavior = IO)
    public abstract static class Summary extends RBuiltinNode.Arg1 {
        private static final RStringVector NAMES = RDataFactory.createStringVector(new String[]{"description", "class", "mode", "text", "opened", "can read", "can write"},
                        RDataFactory.COMPLETE_VECTOR);

        static {
            Casts casts = new Casts(Summary.class);
            casts.arg("object").defaultError(Message.INVALID_CONNECTION).mustNotBeNull().asIntegerVector().findFirst();
        }

        @Specialization
        @TruffleBoundary
        protected RList summary(int object) {
            BaseRConnection baseCon = RConnection.fromIndex(object);
            Object[] data = new Object[NAMES.getLength()];
            data[0] = baseCon.getSummaryDescription();
            data[1] = baseCon.getConnectionClassName();
            data[2] = baseCon.getOpenMode().summaryString();
            data[3] = baseCon.isTextMode() ? "text" : "binary";
            data[4] = baseCon.isOpen() ? "opened" : "closed";
            data[5] = baseCon.canRead() ? "yes" : "no";
            data[6] = baseCon.canWrite() ? "yes" : "no";
            return RDataFactory.createList(data, NAMES);
        }
    }

    @RBuiltin(name = "open", visibility = OFF, kind = INTERNAL, parameterNames = {"con", "open", "blocking"}, behavior = IO)
    public abstract static class Open extends RBuiltinNode.Arg3 {

        static {
            Casts casts = new Casts(Open.class);
            CastsHelper.connection(casts);
            CastsHelper.open(casts);
            CastsHelper.blocking(casts);
        }

        @Specialization
        @TruffleBoundary
        protected Object open(int con, String open, @SuppressWarnings("unused") boolean blocking) {
            try {
                BaseRConnection baseConn = getBaseConnection(RConnection.fromIndex(con));
                if (baseConn.isClosed()) {
                    throw error(RError.Message.INVALID_CONNECTION);
                }
                if (baseConn.isOpen()) {
                    warning(RError.Message.ALREADY_OPEN_CONNECTION);
                }
                baseConn.open(open);
            } catch (IOException ex) {
                throw error(RError.Message.GENERIC, ex.getMessage());
            }
            return RNull.instance;
        }
    }

    @RBuiltin(name = "isOpen", kind = INTERNAL, parameterNames = {"con", "rw"}, behavior = IO)
    public abstract static class IsOpen extends RBuiltinNode.Arg2 {

        static {
            Casts casts = new Casts(IsOpen.class);
            CastsHelper.connection(casts);
            casts.arg("rw").asIntegerVector().findFirst();
        }

        @Specialization
        @TruffleBoundary
        protected RLogicalVector isOpen(int con, int rw) {
            BaseRConnection baseCon = getBaseConnection(RConnection.fromIndex(con));
            boolean result = baseCon.isOpen();
            switch (rw) {
                case 0:
                    break;
                case 1:
                    result &= baseCon.canRead();
                    break;
                case 2:
                    result &= baseCon.canWrite();
                    break;
                default:
                    throw error(RError.Message.UNKNOWN_VALUE, "rw");
            }
            return RDataFactory.createLogicalVectorFromScalar(result);
        }
    }

    @RBuiltin(name = "close", visibility = OFF, kind = INTERNAL, parameterNames = {"con", "type"}, behavior = IO)
    public abstract static class Close extends RBuiltinNode.Arg2 {

        static {
            Casts casts = new Casts(Close.class);
            CastsHelper.connection(casts);
            casts.arg("type").asStringVector().findFirst();
        }

        @Specialization
        @TruffleBoundary
        protected Object close(int con, @SuppressWarnings("unused") String type) {
            BaseRConnection connection = RConnection.fromIndex(con);
            try {
                connection.closeAndDestroy();
            } catch (IOException ex) {
                throw error(RError.Message.GENERIC, ex.getMessage());
            }
            return RNull.instance;
        }
    }

    @RBuiltin(name = "readLines", kind = INTERNAL, parameterNames = {"con", "n", "ok", "warn", "encoding", "skipNul"}, behavior = IO)
    public abstract static class ReadLines extends RBuiltinNode.Arg6 {

        static {
            Casts casts = new Casts(ReadLines.class);
            CastsHelper.connection(casts);
            casts.arg("n").asIntegerVector().findFirst().mustNotBeNA();
            casts.arg("ok").asLogicalVector().findFirst().mustNotBeNA().map(toBoolean());
            casts.arg("warn").asLogicalVector().findFirst().mustNotBeNA().map(toBoolean());
            CastsHelper.encoding(casts);
            casts.arg("skipNul").asLogicalVector().findFirst().mustNotBeNA().map(toBoolean());
        }

        @Specialization
        @TruffleBoundary
        protected Object readLines(int con, int n, boolean ok, boolean warn, @SuppressWarnings("unused") String encoding, boolean skipNul) {
            // TODO Implement argument 'encoding'.
            try (RConnection openConn = RConnection.fromIndex(con).forceOpen("rt")) {
                String[] lines = openConn.readLines(n, ReadLineWarning.allIf(warn), skipNul);
                if (n > 0 && lines.length < n && !ok) {
                    throw error(RError.Message.TOO_FEW_LINES_READ_LINES);
                }
                return RDataFactory.createStringVector(lines, RDataFactory.COMPLETE_VECTOR);
            } catch (IOException x) {
                throw error(RError.Message.ERROR_READING_CONNECTION, x.getMessage());
            }
        }
    }

    @RBuiltin(name = "writeLines", visibility = OFF, kind = INTERNAL, parameterNames = {"text", "con", "sep", "useBytes"}, behavior = IO)
    public abstract static class WriteLines extends RBuiltinNode.Arg4 {

        static {
            Casts casts = new Casts(WriteLines.class);
            casts.arg("text").asStringVector().mustBe(instanceOf(RStringVector.class));
            CastsHelper.connection(casts);
            casts.arg("sep").asStringVector().findFirst();
            CastsHelper.useBytes(casts);
        }

        @Specialization
        @TruffleBoundary
        protected RNull writeLines(RStringVector text, int con, String sep, boolean useBytes) {
            try (RConnection openConn = RConnection.fromIndex(con).forceOpen("wt")) {
                openConn.writeLines(text, sep, useBytes);
            } catch (IOException x) {
                throw error(RError.Message.ERROR_WRITING_CONNECTION, x.getMessage());
            }
            return RNull.instance;
        }
    }

    @RBuiltin(name = "flush", visibility = OFF, kind = INTERNAL, parameterNames = {"con"}, behavior = IO)
    public abstract static class Flush extends RBuiltinNode.Arg1 {

        static {
            Casts casts = new Casts(Flush.class);
            CastsHelper.connection(casts);
        }

        @Specialization
        @TruffleBoundary
        protected RNull flush(int con) {
            try {
                RConnection.fromIndex(con).flush();
            } catch (IOException x) {
                throw error(RError.Message.ERROR_FLUSHING_CONNECTION, x.getMessage());
            }
            return RNull.instance;
        }
    }

    @RBuiltin(name = "pushBack", visibility = OFF, kind = INTERNAL, parameterNames = {"data", "con", "newLine", "type"}, behavior = IO)
    public abstract static class PushBack extends RBuiltinNode.Arg4 {

        static {
            Casts casts = new Casts(PushBack.class);
            casts.arg("data").asStringVector().mustBe(instanceOf(RStringVector.class));
            CastsHelper.connection(casts);
            casts.arg("newLine").asLogicalVector().findFirst().mustNotBeNA().map(toBoolean());
            casts.arg("type").asIntegerVector().findFirst();
        }

        @Specialization
        @TruffleBoundary
        protected Object pushBack(RStringVector data, int connection, boolean newLine, @SuppressWarnings("unused") int type) {
            RConnection.fromIndex(connection).pushBack(data, newLine);
            return RNull.instance;
        }
    }

    @RBuiltin(name = "pushBackLength", kind = INTERNAL, parameterNames = {"con"}, behavior = IO)
    public abstract static class PushBackLength extends RBuiltinNode.Arg1 {

        static {
            Casts casts = new Casts(PushBackLength.class);
            CastsHelper.connection(casts);
        }

        @Specialization
        protected int pushBackLength(int connection) {
            return RConnection.fromIndex(connection, getRContext()).pushBackLength();
        }
    }

    @RBuiltin(name = "clearPushBack", visibility = OFF, kind = INTERNAL, parameterNames = {"con"}, behavior = IO)
    public abstract static class PushBackClear extends RBuiltinNode.Arg1 {

        static {
            Casts casts = new Casts(PushBackClear.class);
            CastsHelper.connection(casts);
        }

        @Specialization
        protected RNull pushBackClear(int connection) {
            RConnection.fromIndex(connection, getRContext()).pushBackClear();
            return RNull.instance;
        }
    }

    @RBuiltin(name = "readChar", kind = INTERNAL, parameterNames = {"con", "nchars", "useBytes"}, behavior = IO)
    public abstract static class ReadChar extends RBuiltinNode.Arg3 {

        static {
            Casts casts = new Casts(ReadChar.class);
            CastsHelper.connection(casts);
            CastsHelper.nchars(casts);
            CastsHelper.useBytes(casts);
        }

        @SuppressWarnings("unused")
        @Specialization(guards = "ncharsEmpty(nchars)")
        protected RStringVector readCharNcharsEmpty(int con, RIntVector nchars, boolean useBytes) {
            return RDataFactory.createEmptyStringVector();
        }

        @Specialization(guards = "!ncharsEmpty(nchars)")
        @TruffleBoundary
        protected RStringVector readChar(int con, RIntVector nchars, boolean useBytes) {
            try (BaseRConnection openConn = RConnection.fromIndex(con).forceOpen("rb")) {
                String[] data = new String[nchars.getLength()];
                for (int i = 0; i < data.length; i++) {
                    data[i] = openConn.readChar(nchars.getDataAt(i), useBytes);
                }
                return RDataFactory.createStringVector(data, RDataFactory.COMPLETE_VECTOR);
            } catch (IOException x) {
                throw error(RError.Message.ERROR_READING_CONNECTION, x.getMessage());
            }
        }

        boolean ncharsEmpty(RIntVector nchars) {
            return nchars.getLength() == 0;
        }
    }

    @RBuiltin(name = "writeChar", visibility = OFF, kind = INTERNAL, parameterNames = {"object", "con", "nchars", "sep", "useBytes"}, behavior = IO)
    public abstract static class WriteChar extends RBuiltinNode.Arg5 {

        static {
            // @formatter:off
            Casts casts = new Casts(WriteChar.class);
            casts.arg("object").mustBe(stringValue(), Message.ONLY_WRITE_CHAR_OBJECTS).asStringVector();
            casts.arg("con").mustBe(rawValue().not(), Message.GENERIC, "raw connection in writeChar not implemented in FastR yet.").
                    mustBe(stringValue().not(), Message.GENERIC, "filename as a connection in writeChar not implemented in FastR yet.").
                    mustBe(integerValue(), Message.INVALID_CONNECTION).asIntegerVector().findFirst();
            CastsHelper.nchars(casts);
            casts.arg("sep").mustNotBeMissing().returnIf(nullValue()).mustBe(stringValue()).asStringVector();
            CastsHelper.useBytes(casts);
            // @formatter:on
        }

        @Specialization
        protected RNull writeChar(RStringVector object, int con, RIntVector nchars, RStringVector sep, boolean useBytes) {
            return writeCharGeneric(object, con, nchars, sep, useBytes);
        }

        @TruffleBoundary
        private RNull writeCharGeneric(RStringVector object, int con, RIntVector nchars, RStringVector sep, boolean useBytes) {
            try (RConnection openConn = RConnection.fromIndex(con).forceOpen("wb")) {
                final int length = object.getLength();
                final int ncharsLen = nchars.getLength();
                for (int i = 0; i < length; i++) {
                    int nc = nchars.getDataAt(i % ncharsLen);
                    String s = object.getDataAt(i);
                    final int writeLen = Math.min(s.length(), nc);
                    int pad = nc - s.length();
                    if (pad > 0) {
                        warning(RError.Message.MORE_CHARACTERS);
                    }
                    openConn.writeChar(s.substring(0, writeLen), pad, getSepFor(sep, i), useBytes);
                }
            } catch (IOException x) {
                throw error(RError.Message.ERROR_WRITING_CONNECTION, x.getMessage());
            }
            return RNull.instance;
        }

        private static String getSepFor(RStringVector sep, int i) {
            if (sep != null) {
                return sep.getDataAt(i % sep.getLength());
            }
            return null;
        }

        @Specialization
        protected RNull writeChar(RStringVector object, int con, RIntVector nchars, @SuppressWarnings("unused") RNull sep, boolean useBytes) {
            return writeCharGeneric(object, con, nchars, null, useBytes);
        }
    }

    /**
     * Sets the order of the given {@code ByteBuffer} from value of {@code swap}. The value of
     * {@code swap} will be {@code true} iff the original requested order was the opposite of the
     * native byte order. Perhaps surprisingly, the default order of a {@code ByteBuffer} is
     * {@code ByteOrder.BIG_ENDIAN}, NOT the native byte order.
     */
    private static ByteBuffer checkOrder(ByteBuffer buffer, boolean swap) {
        ByteOrder nb = ByteOrder.nativeOrder();
        if (swap) {
            nb = nb == ByteOrder.BIG_ENDIAN ? ByteOrder.LITTLE_ENDIAN : ByteOrder.BIG_ENDIAN;
        }
        return buffer.order(nb);
    }

    @RBuiltin(name = "readBin", kind = INTERNAL, parameterNames = {"con", "what", "n", "size", "signed", "swap"}, behavior = IO)
    public abstract static class ReadBin extends RBuiltinNode.Arg6 {

        static {
            Casts casts = new Casts(ReadBin.class);
            casts.arg("con").defaultError(Message.INVALID_CONNECTION).mustNotBeNull().returnIf(rawValue()).asIntegerVector().findFirst();
            casts.arg("what").asStringVector().findFirst();
            CastsHelper.n(casts);
            CastsHelper.size(casts);
            casts.arg("signed").asLogicalVector().findFirst().mustNotBeNA().map(toBoolean());
            CastsHelper.swap(casts);
        }

        @SuppressWarnings("unused")
        @Specialization
        @TruffleBoundary
        protected Object readBin(RRawVector vec, String what, int n, int sizeInput, boolean signed, boolean swap) {
            Object result;
            switch (what) {
                case "character":
                    result = readString(vec, n);
                    break;
                case "raw":
                    result = readRaw(vec, n);
                    break;
                case "logical":
                    result = RDataFactory.createEmptyLogicalVector();
                    break;
                case "int":
                case "integer":
                    result = RDataFactory.createEmptyIntVector();
                    break;
                case "double":
                case "numeric":
                    result = RDataFactory.createEmptyDoubleVector();
                    break;
                case "complex":
                    result = RDataFactory.createEmptyComplexVector();
                    break;
                default:
                    throw RInternalError.unimplemented();
            }
            return result;
        }

        @Specialization
        @TruffleBoundary
        protected Object readBin(int con, String what, int n, int sizeInput, boolean signed, boolean swap) {
            RAbstractVector result;
            BaseRConnection connection = RConnection.fromIndex(con);
            try (RConnection openConn = connection.forceOpen("rb")) {
                if (getBaseConnection(openConn).isTextMode()) {
                    throw error(RError.Message.ONLY_READ_BINARY_CONNECTION);
                }
                switch (what) {
                    case "int":
                    case "integer":
                        int size = sizeInput;
                        if (size == RRuntime.INT_NA) {
                            size = 4;
                        }
                        if (size == 1 || size == 4 || size == 2) {
                            result = readInteger(connection, n, size, swap, signed);
                        } else {
                            throw RError.nyi(RError.SHOW_CALLER, "readBin \"int\" size not implemented");
                        }
                        break;
                    case "double":
                    case "numeric":
                        result = readDouble(connection, n, swap);
                        break;
                    case "complex":
                        result = readComplex(connection, n, swap);
                        break;
                    case "character":
                        result = readString(connection, n);
                        break;
                    case "logical":
                        result = readLogical(connection, n, swap);
                        break;
                    case "raw":
                        result = readRaw(connection, n);
                        break;
                    default:
                        throw RInternalError.shouldNotReachHere();
                }
            } catch (IOException x) {
                throw error(RError.Message.ERROR_READING_CONNECTION, x.getMessage());
            }
            return result;
        }

        private static ByteBuffer fillBuffer(RConnection con, boolean swap, int bytes) throws IOException {
            ByteBuffer buffer = ByteBuffer.allocate(bytes);
            // read at least one element
            do {
                int bytesRead = con.readBin(buffer);
                if (bytesRead == 0) {
                    break;
                }
            } while (buffer.hasRemaining());
            buffer.flip();
            checkOrder(buffer, swap);
            return buffer;
        }

        private static RIntVector readInteger(RConnection con, int n, int size, boolean swap, boolean signed) throws IOException {
            ByteBuffer buffer = fillBuffer(con, swap, n * size);
            int nInts = buffer.limit() / size;
            int[] data = new int[nInts];
            boolean complete = RDataFactory.COMPLETE_VECTOR;
            if (size == 4) {
                IntBuffer intBuffer = buffer.asIntBuffer();
                for (int i = 0; i < nInts; i++) {
                    int d = intBuffer.get();
                    if (RRuntime.isNA(d)) {
                        complete = RDataFactory.INCOMPLETE_VECTOR;
                    }
                    data[i] = d;
                }
            } else if (size == 1) {
                for (int i = 0; i < nInts; i++) {
                    byte b = buffer.get();
                    int d = signed ? b : b & 0xFF;
                    data[i] = d;
                }
            } else if (size == 2) {
                ShortBuffer shortBuffer = buffer.asShortBuffer();
                for (int i = 0; i < nInts; i++) {
                    short s = shortBuffer.get();
                    data[i] = signed ? s : s & 0xFFFF;
                }
            }
            return RDataFactory.createIntVector(data, complete);
        }

        private static RDoubleVector readDouble(RConnection con, int n, boolean swap) throws IOException {
            ByteBuffer buffer = fillBuffer(con, swap, n * 8);
            DoubleBuffer doubleBuffer = buffer.asDoubleBuffer();
            int nDoubles = buffer.limit() / 8;
            boolean complete = RDataFactory.COMPLETE_VECTOR;
            double[] data = new double[nDoubles];
            for (int i = 0; i < nDoubles; i++) {
                double d = doubleBuffer.get();
                if (RRuntime.isNA(d)) {
                    complete = RDataFactory.INCOMPLETE_VECTOR;
                }
                data[i] = d;
            }
            return RDataFactory.createDoubleVector(data, complete);
        }

        private static RComplexVector readComplex(RConnection con, int n, boolean swap) throws IOException {
            ByteBuffer buffer = fillBuffer(con, swap, n * 16);
            DoubleBuffer doubleBuffer = buffer.asDoubleBuffer();
            int nComplex = buffer.limit() / 16;
            boolean complete = RDataFactory.COMPLETE_VECTOR;
            double[] data = new double[nComplex * 2];
            for (int i = 0; i < nComplex; i++) {
                double re = doubleBuffer.get();
                double im = doubleBuffer.get();
                if (RRuntime.isNA(re) || RRuntime.isNA(im)) {
                    complete = RDataFactory.INCOMPLETE_VECTOR;
                }
                data[2 * i] = re;
                data[2 * i + 1] = im;
            }
            return RDataFactory.createComplexVector(data, complete);
        }

        private static RStringVector readString(RConnection con, int n) throws IOException {
            ArrayList<String> strings = new ArrayList<>(n);
            int s = 0;
            while (s < n) {
                byte[] chars = con.readBinChars();
                if (chars == null || chars.length == 0) {
                    break;
                }
                int npos = 0;
                while (chars[npos] != 0) {
                    npos++;
                }
                strings.add(new String(chars, 0, npos));
                s++;
            }
            // There is no special encoding for NA_character_
            String[] stringData = new String[s];
            strings.toArray(stringData);
            return RDataFactory.createStringVector(stringData, RDataFactory.COMPLETE_VECTOR);
        }

        private static RStringVector readString(RRawVector vec, int n) {
            String[] stringData = new String[n];
            byte[] chars;
            chars = vec.getReadonlyData();
            stringData[0] = new String(chars, 0, vec.getLength());
            for (int i = 1; i < stringData.length; i++) {
                stringData[i] = "";
            }
            // There is no special encoding for NA_character_
            return RDataFactory.createStringVector(stringData, RDataFactory.COMPLETE_VECTOR);
        }

        private static RRawVector readRaw(RRawVector vec, int n) {
            byte[] b;
            int length = Math.min(vec.getLength(), n);
            b = new byte[length];
            System.arraycopy(vec.getReadonlyData(), 0, b, 0, length);
            return RDataFactory.createRawVector(b);
        }

        private static RRawVector readRaw(RConnection con, int n) throws IOException {
            ByteBuffer buffer = fillBuffer(con, false, n);
            byte[] data = new byte[buffer.limit()];
            buffer.get(data);
            return RDataFactory.createRawVector(data);
        }

        /*
         * private static RRawVector readRaw(RRawVector raw, int n) { ByteBuffer buffer =
         * ByteBuffer.allocate(n); int bytesRead = con.readBin(buffer); if (bytesRead == 0) { return
         * RDataFactory.createEmptyRawVector(); } buffer.flip(); byte[] data = new byte[bytesRead];
         * buffer.get(data); return RDataFactory.createRawVector(data); }
         */

        private static RLogicalVector readLogical(RConnection con, int n, boolean swap) throws IOException {
            ByteBuffer buffer = fillBuffer(con, swap, n * 4);
            IntBuffer intBuffer = buffer.asIntBuffer();
            int nInts = buffer.limit() / 4;
            byte[] data = new byte[nInts];
            boolean complete = RDataFactory.COMPLETE_VECTOR;
            for (int i = 0; i < nInts; i++) {
                int value = intBuffer.get();
                if (RRuntime.isNA(value)) {
                    data[i] = RRuntime.LOGICAL_NA;
                    complete = RDataFactory.INCOMPLETE_VECTOR;
                } else {
                    data[i] = value == 1 ? RRuntime.LOGICAL_TRUE : RRuntime.LOGICAL_FALSE;
                }
            }
            return RDataFactory.createLogicalVector(data, complete);
        }
    }

    @ImportStatic(DSLConfig.class)
    @TypeSystemReference(RTypes.class)
    public abstract static class WriteDataNode extends RBaseNode {

        public abstract ByteBuffer execute(Object value, int size, boolean swap, boolean useBytes);

        public static WriteDataNode create() {
            return WriteDataNodeGen.create();
        }

        @TruffleBoundary
        private static ByteBuffer allocate(int capacity, boolean swap) {
            ByteBuffer buffer = ByteBuffer.allocate(capacity);
            checkOrder(buffer, swap);
            return buffer;
        }

        @TruffleBoundary
        private static byte[] encodeString(String s) {
            return s.getBytes(StandardCharsets.UTF_8);
        }

        @Specialization(guards = "objectAccess.supports(object)", limit = "getVectorAccessCacheSize()")
        protected ByteBuffer write(RAbstractVector object, @SuppressWarnings("unused") int size, boolean swap, @SuppressWarnings("unused") boolean useBytes,
                        @Cached("object.access()") VectorAccess objectAccess) {
            SequentialIterator iter = objectAccess.access(object);
            int length = objectAccess.getLength(iter);

            ByteBuffer buffer;
            switch (objectAccess.getType()) {
                case Integer:
                case Logical:
                    buffer = allocate(4 * length, swap);
                    while (objectAccess.next(iter)) {
                        Utils.putInt(buffer, objectAccess.getInt(iter));
                    }
                    return buffer;
                case Double:
                    buffer = allocate(8 * length, swap);
                    while (objectAccess.next(iter)) {
                        Utils.putDouble(buffer, objectAccess.getDouble(iter));
                    }
                    return buffer;
                case Complex:
                    buffer = allocate(16 * length, swap);
                    while (objectAccess.next(iter)) {
                        Utils.putDouble(buffer, objectAccess.getComplexR(iter));
                        Utils.putDouble(buffer, objectAccess.getComplexI(iter));
                    }
                    return buffer;
                case Character:
                    byte[][] data = new byte[length][];
                    int totalLength = 0;
                    while (objectAccess.next(iter)) {
                        // There is no special encoding for NA_character_
                        data[iter.getIndex()] = encodeString(objectAccess.getString(iter));
                        // zero pad
                        totalLength = totalLength + data[iter.getIndex()].length + 1;
                    }

                    buffer = allocate(totalLength, swap);
                    for (int i = 0; i < length; i++) {
                        Utils.putBytes(buffer, data[i]);
                        Utils.putByte(buffer, (byte) 0);
                    }
                    return buffer;
                // converted to int
                case Raw:
                    buffer = allocate(length, swap);
                    while (objectAccess.next(iter)) {
                        Utils.putByte(buffer, objectAccess.getRaw(iter));
                    }
                    return buffer;
                default:
                    throw RInternalError.shouldNotReachHere();
            }
        }

        @Specialization(replaces = "write")
        @TruffleBoundary
        protected ByteBuffer writeGeneric(RAbstractVector object, int size, boolean swap, boolean useBytes) {
            return write(object, size, swap, useBytes, object.slowPathAccess());
        }
    }

    @RBuiltin(name = "writeBin", visibility = OFF, kind = INTERNAL, parameterNames = {"object", "con", "size", "swap", "useBytes"}, behavior = IO)
    public abstract static class WriteBin extends RBuiltinNode.Arg5 {

        static {
            Casts casts = new Casts(WriteBin.class);
            casts.arg("object").asVector().mustBe(RAbstractAtomicVector.class);
            casts.arg("con").defaultError(Message.INVALID_CONNECTION).mustBe(integerValue().or(rawValue())).mapIf(integerValue(), asIntegerVector().setNext(findFirst().integerElement()));
            CastsHelper.size(casts);
            CastsHelper.swap(casts);
            CastsHelper.useBytes(casts);
        }

        @TruffleBoundary
        @Specialization
        protected Object writeBin(RAbstractVector object, int con, int size, boolean swap, boolean useBytes,
                        @Cached("create()") WriteDataNode writeData) {
            if (object instanceof RList || object instanceof RExpression) {
                throw error(RError.Message.INVALID_ARGUMENT, "object");
            }
            if (object.getLength() > 0) {
                RConnection connection = RConnection.fromIndex(con);
                try (RConnection openConn = connection.forceOpen("wb")) {
                    if (getBaseConnection(openConn).isTextMode()) {
                        throw error(RError.Message.ONLY_WRITE_BINARY_CONNECTION);
                    }
                    ByteBuffer buffer = writeData.execute(object, size, swap, useBytes);
                    buffer.flip();
                    connection.writeBin(buffer);
                } catch (IOException x) {
                    throw error(RError.Message.ERROR_WRITING_CONNECTION, x.getMessage());
                }
            }
            return RNull.instance;
        }

        @Specialization
        protected RRawVector writeBin(RAbstractVector object, @SuppressWarnings("unused") RRawVector con, int size, boolean swap, boolean useBytes,
                        @Cached("create()") WriteDataNode writeData) {
            ByteBuffer buffer = writeData.execute(object, size, swap, useBytes);
            return RDataFactory.createRawVector(getBytes(buffer));
        }

        @TruffleBoundary
        private static byte[] getBytes(ByteBuffer buffer) {
            buffer.flip();
            return buffer.array();
        }
    }

    @RBuiltin(name = "getConnection", kind = INTERNAL, parameterNames = {"what"}, behavior = IO)
    public abstract static class GetConnection extends RBuiltinNode.Arg1 {

        static {
            Casts casts = new Casts(GetConnection.class);
            casts.arg("what").asIntegerVector().findFirst();
        }

        @Specialization
        @TruffleBoundary
        protected RIntVector getConnection(int what) {
            BaseRConnection con = getRContext().stateRConnection.getConnection(what, false);
            if (con == null) {
                throw error(RError.Message.NO_SUCH_CONNECTION, what);
            } else {
                return con.asVector();
            }
        }
    }

    @RBuiltin(name = "getAllConnections", kind = INTERNAL, parameterNames = {}, behavior = IO)
    public abstract static class GetAllConnections extends RBuiltinNode.Arg0 {
        @Specialization
        @TruffleBoundary
        protected RIntVector getAllConnections() {
            return getRContext().stateRConnection.getAllConnections();
        }
    }

    @RBuiltin(name = "isSeekable", kind = INTERNAL, parameterNames = "con", behavior = IO)
    public abstract static class IsSeekable extends RBuiltinNode.Arg1 {

        static {
            Casts casts = new Casts(IsSeekable.class);
            CastsHelper.connection(casts);
        }

        @Specialization
        @TruffleBoundary
        protected byte isSeekable(int con) {
            return RRuntime.asLogical(RConnection.fromIndex(con).isSeekable());
        }
    }

    @RBuiltin(name = "seek", kind = INTERNAL, parameterNames = {"con", "where", "origin", "rw"}, behavior = IO)
    public abstract static class Seek extends RBuiltinNode.Arg4 {

        static {
            Casts casts = new Casts(Seek.class);
            CastsHelper.connection(casts);
            casts.arg("where").asDoubleVector().findFirst();
            casts.arg("origin").asIntegerVector().findFirst();
            casts.arg("rw").asIntegerVector().findFirst();
        }

        @Specialization
        @TruffleBoundary
        protected int seek(int con, double where, int origin, int rw) {
            /*
             * N.B. 0,1,2 are valid values for "rw"; 1,2,3 are valid values for "origin". We use 0
             * for the NA (enquiry) case.
             */
            long offset = 0;
            final int actualOrigin;
            if (RRuntime.isNAorNaN(where)) {
                actualOrigin = 0;
            } else {
                offset = (long) where;
                actualOrigin = origin;
            }
            try {
                long newOffset = RConnection.fromIndex(con).seek(offset, RConnection.SeekMode.values()[actualOrigin], RConnection.SeekRWMode.values()[rw]);
                if (newOffset > Integer.MAX_VALUE) {
                    throw RError.nyi(RError.SHOW_CALLER, "seek > Integer.MAX_VALUE");
                }
                return (int) newOffset;
            } catch (IOException x) {
                throw error(RError.Message.GENERIC, x.getMessage());
            }
        }
    }

    @RBuiltin(name = "fifo", kind = INTERNAL, parameterNames = {"description", "open", "blocking", "encoding"}, behavior = IO)
    public abstract static class Fifo extends RBuiltinNode.Arg4 {

        static {
            Casts casts = new Casts(Fifo.class);
            CastsHelper.description(casts);
            CastsHelper.open(casts);
            // We cannot support non-blocking because Java does simply not allow to open a file or
            // named pipe in non-blocking mode.
            CastsHelper.blockingNotSupported(casts);
            CastsHelper.encoding(casts);
        }

        @Specialization
        @TruffleBoundary
        protected RIntVector fifo(String path, String open, boolean blocking, String encoding) {
            try {
                return new FifoRConnection(path, open, blocking, encoding).asVector();
            } catch (IOException ex) {
                warning(RError.Message.CANNOT_OPEN_FIFO, path);
                throw error(RError.Message.CANNOT_OPEN_CONNECTION);
            } catch (IllegalCharsetNameException ex) {
                throw error(RError.Message.UNSUPPORTED_ENCODING_CONVERSION, encoding, "");
            }
        }
    }

    @RBuiltin(name = "pipe", kind = INTERNAL, parameterNames = {"description", "open", "encoding"}, behavior = IO)
    public abstract static class Pipe extends RBuiltinNode.Arg3 {

        static {
            Casts casts = new Casts(Pipe.class);
            CastsHelper.description(casts);
            CastsHelper.open(casts);
            CastsHelper.encoding(casts);
        }

        @Specialization
        @TruffleBoundary
        protected RIntVector pipe(String path, String openArg, String encoding) {

            try {
                return new PipeRConnection(path, openArg, encoding).asVector();
            } catch (IOException ex) {
                warning(RError.Message.CANNOT_OPEN_FIFO, path);
                throw error(RError.Message.CANNOT_OPEN_CONNECTION);
            } catch (IllegalCharsetNameException ex) {
                throw error(RError.Message.UNSUPPORTED_ENCODING_CONVERSION, encoding, "");
            }
        }
    }

    @RBuiltin(name = "isIncomplete", kind = INTERNAL, parameterNames = {"con"}, behavior = IO)
    public abstract static class IsIncomplete extends RBuiltinNode.Arg1 {

        static {
            Casts casts = new Casts(IsIncomplete.class);
            CastsHelper.connection(casts);
        }

        @Specialization
        @TruffleBoundary
        protected RLogicalVector isIncomplete(int con) {

            final boolean res = RConnection.fromIndex(con).isIncomplete();
            return RDataFactory.createLogicalVectorFromScalar(res);
        }
    }

    @RBuiltin(name = "truncate", kind = INTERNAL, parameterNames = {"con"}, behavior = IO)
    public abstract static class Truncate extends RBuiltinNode.Arg1 {

        static {
            Casts casts = new Casts(Truncate.class);
            CastsHelper.connection(casts);
        }

        @Specialization
        @TruffleBoundary
        protected RNull truncate(int con) {

            try {
                RConnection.fromIndex(con).truncate();
            } catch (IOException e) {
                throw error(RError.Message.TRUNCATE_UNSUPPORTED_FOR_CONN, e.getMessage());
            }
            return RNull.instance;
        }
    }

    @RBuiltin(name = ".fastr.channelConnection", kind = RBuiltinKind.PRIMITIVE, parameterNames = {"channel", "open", "encoding"}, behavior = IO)
    public abstract static class ChannelConnection extends RBuiltinNode.Arg3 {

        static {
            Casts casts = new Casts(ChannelConnection.class);
            casts.arg("channel").mustNotBeMissing().mustBe(nullValue().not().and(instanceOf(TruffleObject.class)));
            CastsHelper.openMode(casts);
            CastsHelper.encoding(casts);
        }

        @Override
        public Object[] getDefaultParameterValues() {
            return new Object[]{RMissing.instance, RMissing.instance, Charset.defaultCharset().name()};
        }

        @Specialization
        @TruffleBoundary
        protected RIntVector channelConnection(TruffleObject channel, String open, String encoding) {
            try {
                TruffleLanguage.Env env = getRContext().getEnv();
                if (env.isHostObject(channel)) {
                    Object obj = env.asHostObject(channel);
                    if (obj instanceof ByteChannel) {
                        ByteChannel ch = (ByteChannel) obj;
                        return new ChannelRConnection("", ch, open, encoding).asVector();
                    }
                }
                throw error(RError.Message.INVALID_CHANNEL_OBJECT);
            } catch (IOException ex) {
                throw RInternalError.shouldNotReachHere();
            }
        }
    }

    @RBuiltin(name = "sockSelect", kind = RBuiltinKind.INTERNAL, parameterNames = {"socklist", "write", "timeout"}, behavior = IO)
    public abstract static class SockSelect extends RBuiltinNode.Arg3 {

        static {
            Casts casts = new Casts(SockSelect.class);
            casts.arg("socklist").defaultError(Message.NOT_A_LIST_OF_SOCKETS).mustNotBeMissing().mustNotBeNull().asIntegerVector();
            casts.arg("write").mustNotBeMissing().mustBe(logicalValue()).asLogicalVector().findFirst().map(toBoolean());
            casts.arg("timeout").mustNotBeMissing().asIntegerVector().findFirst();
        }

        @Specialization
        protected RLogicalVector selectMultiple(RIntVector socklist, boolean write, int timeout) {
            RSocketConnection[] socketConnections = getSocketConnections(socklist);
            try {
                byte[] selected = RSocketConnection.select(socketConnections, write, timeout * 1000L);
                return RDataFactory.createLogicalVector(selected, true);
            } catch (IOException e) {
                CompilerDirectives.transferToInterpreter();
                throw error(RError.Message.GENERIC, e.getMessage());
            }
        }

        @TruffleBoundary
        private RSocketConnection[] getSocketConnections(RIntVector socklist) {
            RSocketConnection[] socketConnections = new RSocketConnection[socklist.getLength()];
            for (int i = 0; i < socklist.getLength(); i++) {
                BaseRConnection baseConnection = getBaseConnection(RConnection.fromIndex(socklist.getDataAt(i)));
                if (baseConnection instanceof RSocketConnection) {
                    socketConnections[i] = (RSocketConnection) baseConnection;
                } else {
                    throw error(Message.NOT_A_SOCKET_CONNECTION);
                }
            }
            return socketConnections;
        }
    }
}
