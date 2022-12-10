/*
 * Copyright (c) 1995, 1996, Robert Gentleman and Ross Ihaka
 * Copyright (c) 1998-2013, The R Core Team
 * Copyright (c) 2014, 2022, Oracle and/or its affiliates
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

package com.oracle.truffle.r.nodes.builtin.base;

import static com.oracle.truffle.r.nodes.builtin.CastBuilder.Predef.charAt0;
import static com.oracle.truffle.r.nodes.builtin.CastBuilder.Predef.constant;
import static com.oracle.truffle.r.nodes.builtin.CastBuilder.Predef.emptyStringVector;
import static com.oracle.truffle.r.nodes.builtin.CastBuilder.Predef.instanceOf;
import static com.oracle.truffle.r.nodes.builtin.CastBuilder.Predef.length;
import static com.oracle.truffle.r.nodes.builtin.CastBuilder.Predef.lengthLte;
import static com.oracle.truffle.r.nodes.builtin.CastBuilder.Predef.logicalValue;
import static com.oracle.truffle.r.nodes.builtin.CastBuilder.Predef.lt;
import static com.oracle.truffle.r.nodes.builtin.CastBuilder.Predef.nullValue;
import static com.oracle.truffle.r.nodes.builtin.CastBuilder.Predef.singleElement;
import static com.oracle.truffle.r.nodes.builtin.CastBuilder.Predef.stringValue;
import static com.oracle.truffle.r.nodes.builtin.CastBuilder.Predef.toBoolean;
import static com.oracle.truffle.r.runtime.builtins.RBehavior.IO;
import static com.oracle.truffle.r.runtime.builtins.RBuiltinKind.INTERNAL;

import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.r.runtime.data.nodes.attributes.SpecialAttributesFunctions.ExtractNamesAttributeNode;
import com.oracle.truffle.r.nodes.builtin.RBuiltinNode;
import com.oracle.truffle.r.runtime.nodes.unary.CastToVectorNode;
import com.oracle.truffle.r.runtime.nodes.unary.CastToVectorNodeGen;
import com.oracle.truffle.r.runtime.RError;
import com.oracle.truffle.r.runtime.RError.Message;
import com.oracle.truffle.r.runtime.RInternalError;
import com.oracle.truffle.r.runtime.RRuntime;
import com.oracle.truffle.r.runtime.builtins.RBuiltin;
import com.oracle.truffle.r.runtime.conn.RConnection;
import com.oracle.truffle.r.runtime.conn.RConnection.ReadLineWarning;
import com.oracle.truffle.r.runtime.conn.StdConnections;
import com.oracle.truffle.r.runtime.data.RDataFactory;
import com.oracle.truffle.r.runtime.data.RFunction;
import com.oracle.truffle.r.runtime.data.RList;
import com.oracle.truffle.r.runtime.data.RNull;
import com.oracle.truffle.r.runtime.data.RRaw;
import com.oracle.truffle.r.runtime.data.RLogicalVector;
import com.oracle.truffle.r.runtime.data.RStringVector;
import com.oracle.truffle.r.runtime.data.model.RAbstractVector;
import com.oracle.truffle.r.runtime.ops.na.NACheck;

@RBuiltin(name = "scan", kind = INTERNAL, parameterNames = {"file", "what", "nmax", "sep", "dec", "quote", "skip", "nlines", "na.strings", "flush", "fill", "strip.white", "quiet", "blank.lines.skip",
                "multi.line", "comment.char", "allowEscapes", "encoding", "skipNull"}, behavior = IO)
public abstract class Scan extends RBuiltinNode.Arg19 {

    private static final int SCAN_BLOCKSIZE = 1000;
    private static final int NO_COMCHAR = 100000; /* won't occur even in Unicode */

    private final NACheck naCheck = NACheck.create();
    @Child private ExtractNamesAttributeNode extractNames = ExtractNamesAttributeNode.create();

    @Child private CastToVectorNode castVector;

    private RAbstractVector castVector(Object value) {
        if (castVector == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            castVector = insert(CastToVectorNodeGen.create(false));
        }
        return ((RAbstractVector) castVector.doCast(value)).materialize();
    }

    @SuppressWarnings("unused")
    private static class LocalData {
        final HashMap<String, String> stringTable = new HashMap<>();
        RStringVector naStrings = null;
        boolean quiet = false;
        char sepchar = 0; // 0 means any whitespace
        char decchar = '.';
        char[] quoteset = new char[0];
        int comchar = NO_COMCHAR;
        // connection-related (currently not supported)
        // int ttyflag = 0;
        RConnection con = null;
        // connection-related (currently not supported)
        // boolean wasopen = false;
        boolean escapes = false;
        int save = 0;
        boolean isLatin1 = false;
        boolean isUTF8 = false;
        boolean atStart = false;
        boolean embedWarn = false;
        boolean skipNull = false;
    }

    private static class GetQuotedItemsResult {
        final String[] items;
        final int pos;

        GetQuotedItemsResult(String[] items, int pos) {
            this.items = items;
            this.pos = pos;
        }
    }

    static {
        Casts casts = new Casts(Scan.class);
        casts.arg("file").defaultError(Message.INVALID_CONNECTION).mustNotBeNull().asIntegerVector().findFirst();

        casts.arg("what").mustNotBeMissing().mustBe(nullValue().not()).mustBe(instanceOf(RFunction.class).not()).asVector();

        casts.arg("nmax").asIntegerVector().findFirst(0).replaceNA(0).mapIf(lt(0), constant(0));

        casts.arg("sep").mapNull(emptyStringVector()).mustBe(stringValue()).asStringVector().findFirst("").mustBe(lengthLte(1), RError.Message.MUST_BE_ONE_BYTE, "'sep' value");

        casts.arg("dec").defaultError(RError.Message.INVALID_DECIMAL_SEP).mapNull(constant(".")).mustBe(stringValue()).asStringVector().findFirst(".").mustBe(length(1),
                        RError.Message.MUST_BE_ONE_BYTE, "'sep' value");

        casts.arg("quote").defaultError(RError.Message.INVALID_QUOTE_SYMBOL).mapNull(constant("")).mustBe(stringValue()).asStringVector().findFirst("");

        casts.arg("skip").asIntegerVector().findFirst(0).replaceNA(0).mapIf(lt(0), constant(0));

        casts.arg("nlines").asIntegerVector().findFirst(0).replaceNA(0).mapIf(lt(0), constant(0));

        casts.arg("na.strings").mustBe(stringValue());

        casts.arg("flush").asLogicalVector().findFirst(RRuntime.LOGICAL_FALSE).map(toBoolean());

        casts.arg("fill").asLogicalVector().findFirst(RRuntime.LOGICAL_FALSE).map(toBoolean());

        casts.arg("strip.white").mustBe(logicalValue());

        casts.arg("quiet").asLogicalVector().findFirst(RRuntime.LOGICAL_FALSE).replaceNA(RRuntime.LOGICAL_FALSE).map(toBoolean());

        casts.arg("blank.lines.skip").asLogicalVector().findFirst(RRuntime.LOGICAL_TRUE).replaceNA(RRuntime.LOGICAL_TRUE).map(toBoolean());

        casts.arg("multi.line").asLogicalVector().findFirst(RRuntime.LOGICAL_TRUE).replaceNA(RRuntime.LOGICAL_TRUE).map(toBoolean());

        casts.arg("comment.char").mustBe(stringValue()).asStringVector().mustBe(singleElement()).findFirst().mustBe(lengthLte(1)).map(charAt0(RRuntime.INT_NA)).replaceNA(NO_COMCHAR);

        casts.arg("allowEscapes").asLogicalVector().findFirst().mustNotBeNA().map(toBoolean());

        casts.arg("encoding").mustBe(stringValue()).asStringVector().mustBe(singleElement()).findFirst();

        casts.arg("skipNull").asLogicalVector().findFirst().mustNotBeNA().map(toBoolean());

    }

    @Specialization
    @TruffleBoundary
    protected Object doScan(int file, RAbstractVector what, int nmax, String sep, String dec, String quotes, int nskip,
                    int nlines, RStringVector naStringsVec, boolean flush, boolean fill, RLogicalVector stripVec,
                    boolean quiet, boolean blSkip, boolean multiLine, int commentChar, boolean escapes,
                    String encoding, boolean skipNull) {

        LocalData data = new LocalData();

        // TODO: some sort of character translation happens here?
        data.sepchar = sep.isEmpty() ? 0 : sep.charAt(0);

        // TODO: some sort of character translation happens here?
        data.decchar = dec.charAt(0);

        // TODO: some sort of character translation happens here?
        data.quoteset = quotes.toCharArray();

        data.naStrings = naStringsVec;

        if (stripVec.getLength() != 1 && stripVec.getLength() != what.getLength()) {
            throw error(RError.Message.INVALID_LENGTH, "strip.white");
        }
        byte strip = stripVec.getDataAt(0);

        data.quiet = quiet;

        data.comchar = commentChar;

        data.escapes = escapes;

        if (encoding.equals("latin1")) {
            data.isLatin1 = true;
        }
        if (encoding.equals("UTF-8")) {
            data.isUTF8 = true;
        }

        data.skipNull = skipNull;

        // TODO: quite a few more things happen in GNU R around connections
        data.con = RConnection.fromIndex(file);

        data.save = 0;

        try (RConnection openConn = data.con.forceOpen("r")) {
            if (nskip > 0) {
                openConn.readLines(nskip, EnumSet.of(ReadLineWarning.EMBEDDED_NUL), skipNull);
            }
            if (what instanceof RList) {
                return scanFrame((RList) what, nmax, nlines, flush, fill, strip == RRuntime.LOGICAL_TRUE, blSkip, multiLine, data);
            } else {
                return scanVector(what, nmax, nlines, flush, strip == RRuntime.LOGICAL_TRUE, blSkip, data);
            }
        } catch (IOException x) {
            throw error(RError.Message.CANNOT_READ_CONNECTION);
        }
    }

    private static int skipWhitespace(String s, int start) {
        int pos = start;
        while (pos < s.length() && (s.charAt(pos) == ' ' || s.charAt(pos) == '\t')) {
            pos++;
        }
        return pos;
    }

    private static boolean isInSet(char ch, char[] quoteset) {
        for (int i = 0; i < quoteset.length; i++) {
            if (ch == quoteset[i]) {
                return true;
            }
        }
        return false;
    }

    private static GetQuotedItemsResult getQuotedItems(LocalData data, int maxItems, String s) {
        ArrayList<String> items = new ArrayList<>();

        char sepchar = data.sepchar;
        char[] quoteset = data.quoteset;
        int length = s.length();
        int pos = 0;
        if (sepchar == 0) {
            pos = skipWhitespace(s, pos);
        }
        if (pos == length) {
            return new GetQuotedItemsResult(new String[0], pos);
        }
        StringBuilder str = new StringBuilder();
        do {
            char ch = s.charAt(pos);
            if (sepchar == 0 && (ch == ' ' || ch == '\t')) {
                pos = skipWhitespace(s, pos);
                if (pos == length) {
                    break;
                }
                items.add(str.toString());
                str.setLength(0);
            } else if (sepchar != 0 && ch == sepchar) {
                pos++;
                items.add(str.toString());
                str.setLength(0);
            } else if (str.length() == 0 && isInSet(ch, quoteset)) {
                char quoteStart = ch;
                pos++;
                while (true) {
                    if (pos == length) {
                        throw RError.error(RError.SHOW_CALLER, Message.INCOMPLETE_FINAL_LINE, s);
                    }
                    ch = s.charAt(pos++);
                    if (ch == quoteStart) {
                        if (pos < length && s.charAt(pos) == quoteStart) {
                            str.append(quoteStart);
                            pos++;
                        } else {
                            break;
                        }
                    } else {
                        str.append(ch);
                    }
                }
            } else {
                str.append(ch);
                pos++;
            }
        } while (pos < s.length() && (maxItems <= 0 || items.size() < maxItems));
        if (str.length() > 0) {
            items.add(str.toString());
        }
        return new GetQuotedItemsResult(items.toArray(new String[items.size()]), pos);
    }

    private static String[] getItems(LocalData data, int maxItems, boolean blSkip) throws IOException {
        while (true) {
            String[] str = data.con.readLines(1, EnumSet.of(ReadLineWarning.EMBEDDED_NUL), false);
            if (str == null || str.length == 0) {
                return null;
            } else {
                GetQuotedItemsResult res = getQuotedItems(data, maxItems, str[0]);
                String[] items = res.items;
                if (!blSkip || items.length != 0) {
                    if (res.pos < str[0].length()) {
                        RStringVector remainder = RDataFactory.createStringVectorFromScalar(str[0].substring(res.pos));
                        data.con.pushBack(remainder, true);
                    }
                    return items.length == 0 ? new String[]{""} : items;
                }
            }
        }
    }

    private void fillEmpty(int from, int to, int records, RList list, LocalData data) {
        for (int i = from; i < to; i++) {
            RAbstractVector vec = (RAbstractVector) list.getDataAt(i);
            vec.updateDataAtAsObject(records, extractItem(vec, "", data), naCheck);
        }
    }

    private RAbstractVector scanFrame(RList what, int maxRecords, int maxLines, boolean flush, boolean fill, @SuppressWarnings("unused") boolean stripWhite, boolean blSkip, boolean multiLine,
                    LocalData data) throws IOException {

        int nc = what.getLength();
        if (nc == 0) {
            throw error(RError.Message.EMPTY_WHAT);
        }
        int blockSize = maxRecords > 0 ? maxRecords : (maxLines > 0 ? maxLines : SCAN_BLOCKSIZE);

        RList list = RDataFactory.createList(nc);
        for (int i = 0; i < nc; i++) {
            if (what.getDataAt(i) == RNull.instance) {
                throw error(RError.Message.INVALID_ARGUMENT, "what");
            } else {
                RAbstractVector vec = castVector(what.getDataAt(i));
                list.updateDataAt(i, vec.createEmptySameType(blockSize, RDataFactory.COMPLETE_VECTOR), null);
            }
        }
        list.setNames(extractNames.execute(what));

        naCheck.enable(true);

        return scanFrameInternal(maxRecords, maxLines, flush, fill, blSkip, multiLine, data, nc, blockSize, list);
    }

    @TruffleBoundary
    private RAbstractVector scanFrameInternal(int maxRecords, int maxLines, boolean flush, boolean fill, boolean blSkip, boolean multiLine, LocalData data, int nc, int initialBlockSize, RList list)
                    throws IOException {
        int blockSize = initialBlockSize;
        int n = 0;
        int lines = 0;
        int records = 0;
        while (true) {
            // TODO: does not do any fancy stuff, like handling comments
            String[] strItems = getItems(data, maxRecords, blSkip);
            if (strItems == null) {
                break;
            }

            boolean done = false;
            for (int i = 0; i < Math.max(nc, strItems.length); i++) {

                if (n == strItems.length) {
                    if (fill) {
                        fillEmpty(n, nc, records, list, data);
                        records++;
                        n = 0;
                        break;
                    } else if (!multiLine) {
                        throw error(RError.Message.LINE_ELEMENTS, lines + 1, nc);
                    } else {
                        strItems = getItems(data, maxRecords, blSkip);
                        // Checkstyle: stop modified control variable check
                        i = 0;
                        // Checkstyle: resume modified control variable check
                        if (strItems == null) {
                            done = true;
                            break;
                        }
                    }
                }
                Object item = extractItem((RAbstractVector) list.getDataAt(n), strItems[i], data);

                if (records == blockSize) {
                    // enlarge the vector
                    blockSize = blockSize * 2;
                    for (int j = 0; j < nc; j++) {
                        RAbstractVector vec = (RAbstractVector) list.getDataAt(j);
                        vec = vec.copyResized(blockSize, false);
                        list.updateDataAt(j, vec, null);
                    }
                }

                RAbstractVector vec = (RAbstractVector) list.getDataAt(n);
                vec.updateDataAtAsObject(records, item, naCheck);
                n++;
                if (n == nc) {
                    records++;
                    n = 0;
                    if (records == maxRecords) {
                        done = true;
                        break;
                    }
                    if (flush) {
                        break;
                    }
                }
            }
            if (done) {
                break;
            }
            lines++;
            if (lines == maxLines) {
                break;
            }
        }

        if (n > 0 && n < nc) {
            if (!fill) {
                warning(RError.Message.ITEMS_NOT_MULTIPLE);
            }
            fillEmpty(n, nc, records, list, data);
            records++;
        }

        if (!data.quiet) {
            String s = String.format("Read %d record%s", records, (records == 1) ? "" : "s");
            StdConnections.getStdout().writeString(s, true);
        }
        // trim vectors if necessary
        for (int i = 0; i < nc; i++) {
            RAbstractVector vec = (RAbstractVector) list.getDataAt(i);
            if (vec.getLength() > records) {
                list.updateDataAt(i, vec.copyResized(records, false), null);
            }
        }

        return list;
    }

    @TruffleBoundary
    private RAbstractVector scanVector(RAbstractVector what, int maxItems, int maxLines, @SuppressWarnings("unused") boolean flush, @SuppressWarnings("unused") boolean stripWhite, boolean blSkip,
                    LocalData data) throws IOException {
        int blockSize = maxItems > 0 ? maxItems : SCAN_BLOCKSIZE;
        RAbstractVector vec = what.createEmptySameType(blockSize, RDataFactory.COMPLETE_VECTOR);
        naCheck.enable(true);

        int n = 0;
        int lines = 0;
        while (true) {
            // TODO: does not do any fancy stuff, like handling comments
            String[] strItems = getItems(data, maxItems, blSkip);
            if (strItems == null) {
                break;
            }

            boolean done = false;
            for (int i = 0; i < strItems.length; i++) {

                Object item = extractItem(what, strItems[i], data);

                if (n == blockSize) {
                    // enlarge the vector
                    blockSize = blockSize * 2;
                    vec = vec.copyResized(blockSize, false);
                }

                vec.updateDataAtAsObject(n, item, naCheck);
                n++;
                if (n == maxItems) {
                    done = true;
                    break;
                }
            }
            if (done) {
                break;
            }
            lines++;
            if (lines == maxLines) {
                break;
            }
        }
        if (!data.quiet) {
            String s = String.format("Read %d item%s", n, (n == 1) ? "" : "s");
            StdConnections.getStdout().writeString(s, true);
        }
        // trim vector if necessary
        return vec.getLength() > n ? vec.copyResized(n, false) : vec;
    }

    // If mode = 0 use for numeric fields where "" is NA
    // If mode = 1 use for character fields where "" is verbatim unless
    // na.strings includes ""
    private static boolean isNaString(String buffer, int mode, LocalData data) {
        int i;

        if (mode == 0 && buffer.length() == 0) {
            return true;
        }
        for (i = 0; i < data.naStrings.getLength(); i++) {
            if (data.naStrings.getDataAt(i).equals(buffer)) {
                return true;
            }
        }
        return false;
    }

    private static Object extractItem(RAbstractVector what, String buffer, LocalData data) {
        try {
            switch (what.getRType()) {
                case Logical:
                    if (isNaString(buffer, 0, data)) {
                        return RRuntime.LOGICAL_NA;
                    } else {
                        return RRuntime.string2logicalNoCheck(buffer);
                    }
                case Integer:
                    if (isNaString(buffer, 0, data)) {
                        return RRuntime.INT_NA;
                    } else {
                        return RRuntime.parseInt(buffer);
                    }
                case Double:
                    if (isNaString(buffer, 0, data)) {
                        return RRuntime.DOUBLE_NA;
                    } else {
                        return RRuntime.string2doubleNoCheck(buffer);
                    }
                case Complex:
                    if (isNaString(buffer, 0, data)) {
                        return RRuntime.COMPLEX_NA;
                    } else {
                        return RRuntime.string2complexNoCheck(buffer);
                    }
                case Character:
                    if (isNaString(buffer, 1, data)) {
                        return RRuntime.STRING_NA;
                    } else {
                        String oldEntry = data.stringTable.putIfAbsent(buffer, buffer);
                        return oldEntry == null ? buffer : oldEntry;
                    }
                case Raw:
                    if (isNaString(buffer, 0, data)) {
                        return RRaw.valueOf((byte) 0);
                    } else {
                        return RRuntime.string2raw(buffer);
                    }
                default:
                    throw RInternalError.shouldNotReachHere();
            }
        } catch (NumberFormatException e) {
            throw RError.error(RError.SHOW_CALLER, Message.SCAN_UNEXPECTED, what.getRType().getName(), buffer);
        }
    }
}
