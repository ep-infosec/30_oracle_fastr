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

import static com.oracle.truffle.r.nodes.builtin.CastBuilder.Predef.emptyStringVector;
import static com.oracle.truffle.r.nodes.builtin.CastBuilder.Predef.logicalValue;
import static com.oracle.truffle.r.nodes.builtin.CastBuilder.Predef.nullValue;
import static com.oracle.truffle.r.nodes.builtin.CastBuilder.Predef.stringValue;
import static com.oracle.truffle.r.nodes.builtin.CastBuilder.Predef.toBoolean;
import static com.oracle.truffle.r.nodes.builtin.casts.fluent.CastNodeBuilder.newCastBuilder;
import static com.oracle.truffle.r.runtime.RError.Message.NON_STRING_ARG_TO_INTERNAL_PASTE;
import static com.oracle.truffle.r.runtime.builtins.RBehavior.PURE;
import static com.oracle.truffle.r.runtime.builtins.RBuiltinKind.INTERNAL;

import java.util.Arrays;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.api.profiles.ConditionProfile;
import com.oracle.truffle.api.profiles.PrimitiveValueProfile;
import com.oracle.truffle.api.profiles.ValueProfile;
import com.oracle.truffle.r.nodes.binary.BoxPrimitiveNode;
import com.oracle.truffle.r.nodes.builtin.RBuiltinNode;
import com.oracle.truffle.r.nodes.control.RLengthNode;
import com.oracle.truffle.r.nodes.function.ClassHierarchyNode;
import com.oracle.truffle.r.nodes.function.call.RExplicitBaseEnvCallDispatcher;
import com.oracle.truffle.r.runtime.RError;
import com.oracle.truffle.r.runtime.RError.Message;
import com.oracle.truffle.r.runtime.RRuntime;
import com.oracle.truffle.r.runtime.Utils;
import com.oracle.truffle.r.runtime.builtins.RBuiltin;
import com.oracle.truffle.r.runtime.data.RDataFactory;
import com.oracle.truffle.r.runtime.data.RIntSeqVectorData;
import com.oracle.truffle.r.runtime.data.RIntVector;
import com.oracle.truffle.r.runtime.data.RList;
import com.oracle.truffle.r.runtime.data.RMissing;
import com.oracle.truffle.r.runtime.data.RNull;
import com.oracle.truffle.r.runtime.data.RScalar;
import com.oracle.truffle.r.runtime.data.RStringVector;
import com.oracle.truffle.r.runtime.data.VectorDataLibrary;
import com.oracle.truffle.r.runtime.data.VectorDataLibrary.SeqIterator;
import com.oracle.truffle.r.runtime.data.model.RAbstractListVector;
import com.oracle.truffle.r.runtime.nodes.unary.CastNode;

@RBuiltin(name = "paste", kind = INTERNAL, parameterNames = {"", "sep", "collapse", "recycle0"}, behavior = PURE)
public abstract class Paste extends RBuiltinNode.Arg4 {

    private static final String[] ONE_EMPTY_STRING = new String[]{""};

    public abstract Object executeList(VirtualFrame frame, RList value, String sep, Object collapse, Object recycle0);

    @Child private ClassHierarchyNode classHierarchyNode;
    @Child private CastNode asCharacterNode;
    @Child private CastNode castAsCharacterResultNode;
    @Child private RExplicitBaseEnvCallDispatcher asCharacterDispatcher;
    @Child private BoxPrimitiveNode boxPrimitiveNode = BoxPrimitiveNode.create();
    @Child private RLengthNode elementLengthNode = RLengthNode.create();

    private final ValueProfile lengthProfile = PrimitiveValueProfile.createEqualityProfile();
    private final ConditionProfile reusedResultProfile = ConditionProfile.createBinaryProfile();
    private final BranchProfile nonNullElementsProfile = BranchProfile.create();
    private final BranchProfile onlyNullElementsProfile = BranchProfile.create();
    private final BranchProfile hasZeroLengthElementProfile = BranchProfile.create();
    private final ConditionProfile isNotStringProfile = ConditionProfile.createBinaryProfile();
    private final ConditionProfile hasNoClassProfile = ConditionProfile.createBinaryProfile();
    private final ConditionProfile convertedEmptyProfile = ConditionProfile.createBinaryProfile();
    private final ConditionProfile lengthOneAndCompleteProfile = ConditionProfile.createBinaryProfile();

    static {
        Casts casts = new Casts(Paste.class);
        casts.arg(0).mustBe(RAbstractListVector.class);
        casts.arg("sep").asStringVector().findFirst(Message.INVALID_SEPARATOR);
        casts.arg("collapse").allowNull().mustBe(stringValue()).asStringVector().findFirst();
        // recycle0 parameter might be missing in .Internal call from baseloader.R
        casts.arg("recycle0").allowNullAndMissing().mustBe(logicalValue()).asLogicalVector().findFirst().map(toBoolean());
    }

    private RStringVector castCharacterVector(VirtualFrame frame, Object o) {
        // Note: GnuR does not actually invoke as.character for character values, even if they have
        // class and uses their value directly
        Object result = o;
        if (isNotStringProfile.profile(!(o instanceof String || o instanceof RStringVector))) {
            result = castNonStringToCharacterVector(frame, result);
        }
        // box String to RStringVector
        return (RStringVector) boxPrimitiveNode.execute(result);
    }

    private Object castNonStringToCharacterVector(VirtualFrame frame, Object result) {
        RStringVector classVec = getClassHierarchyNode().execute(result);
        if (hasNoClassProfile.profile(classVec == null || classVec.getLength() == 0)) {
            // coerce non-string result to string, i.e. do what 'as.character' would do
            return getAsCharacterNode().doCast(result);
        }
        // invoke the actual 'as.character' function (with its dispatch)
        ensureAsCharacterFuncNodes();
        return castAsCharacterResultNode.doCast(asCharacterDispatcher.call(frame, result));
    }

    @Specialization(limit = "getGenericDataLibraryCacheSize()")
    protected RStringVector pasteListNullCollapse(VirtualFrame frame, RAbstractListVector values, String sep, @SuppressWarnings("unused") RNull collapse, boolean recycle0,
                    @CachedLibrary("values.getData()") VectorDataLibrary valuesDataLib) {
        int length = lengthProfile.profile(valuesDataLib.getLength(values.getData()));
        if (recycle0 && hasZeroLengthElement(values, valuesDataLib)) {
            return RDataFactory.createEmptyStringVector();
        }

        if (hasNonNullElements(values, length)) {
            int seqPos = isStringSequence(values, valuesDataLib, length);
            if (seqPos != -1) {
                return createStringSequence(frame, values, valuesDataLib, length, seqPos, sep);
            } else {
                String[] result = pasteListElements(frame, values, valuesDataLib, sep, length);
                if (result == ONE_EMPTY_STRING) {
                    return RDataFactory.createEmptyStringVector();
                } else {
                    return RDataFactory.createStringVector(result, RDataFactory.COMPLETE_VECTOR);
                }
            }
        } else {
            return RDataFactory.createEmptyStringVector();
        }
    }

    @Specialization(limit = "getGenericDataLibraryCacheSize()")
    protected String pasteList(VirtualFrame frame, RAbstractListVector values, String sep, String collapse, boolean recycle0,
                    @CachedLibrary("values.getData()") VectorDataLibrary valuesDataLib) {
        int length = lengthProfile.profile(valuesDataLib.getLength(values.getData()));
        if (recycle0 && hasZeroLengthElement(values, valuesDataLib)) {
            return "";
        }

        if (hasNonNullElements(values, length)) {
            String[] result = pasteListElements(frame, values, valuesDataLib, sep, length);
            return collapseString(result, collapse);
        } else {
            return "";
        }
    }

    /**
     * Missing recycle0 argument is, e.g., in baseloader.R (as of GNU-R version 4.0.3).
     */
    @Specialization(limit = "getGenericDataLibraryCacheSize()")
    protected Object pasteListMissingRecycle(VirtualFrame frame, RAbstractListVector values, String sep, Object collapse, @SuppressWarnings("unused") RMissing recycle0,
                    @CachedLibrary("values.getData()") VectorDataLibrary valuesDataLib) {
        if (collapse instanceof String) {
            return pasteList(frame, values, sep, (String) collapse, false, valuesDataLib);
        } else if (RRuntime.isNull(collapse)) {
            return pasteListNullCollapse(frame, values, sep, RNull.instance, false, valuesDataLib);
        } else {
            throw RError.nyi(RError.NO_CALLER, "collapse not String or NULL");
        }
    }

    private boolean hasNonNullElements(RAbstractListVector values, int length) {
        for (int i = 0; i < length; i++) {
            if (values.getDataAt(i) != RNull.instance) {
                nonNullElementsProfile.enter();
                return true;
            }
        }
        onlyNullElementsProfile.enter();
        return false;
    }

    private boolean hasZeroLengthElement(RAbstractListVector values, VectorDataLibrary valuesDataLib) {
        Object valuesData = values.getData();
        SeqIterator iterator = valuesDataLib.iterator(valuesData);
        while (valuesDataLib.nextLoopCondition(valuesData, iterator)) {
            Object element = valuesDataLib.getNextElement(valuesData, iterator);
            int elementLength = elementLengthNode.executeInteger(element);
            if (elementLength == 0) {
                hasZeroLengthElementProfile.enter();
                return true;
            }
        }
        return false;
    }

    private String[] pasteListElements(VirtualFrame frame, RAbstractListVector values, VectorDataLibrary valuesDataLib, String sep, int length) {
        String[][] converted = new String[length][];
        int maxLength = 1;
        int emptyCnt = 0;
        for (int i = 0; i < length; i++) {
            Object element = valuesDataLib.getDataAtAsObject(values.getData(), i);
            String[] array = castCharacterVector(frame, element).materialize().getReadonlyStringData();
            maxLength = Math.max(maxLength, array.length);
            if (array.length == 0) {
                converted[i] = ONE_EMPTY_STRING;
                emptyCnt++;
            } else {
                converted[i] = array;
            }
        }
        if (convertedEmptyProfile.profile(emptyCnt == length)) {
            return ONE_EMPTY_STRING;
        } else if (lengthOneAndCompleteProfile.profile(length == 1 && valuesDataLib.isComplete(values.getData()))) {
            return converted[0];
        } else if (length == 1) { // Incomplete values vector
            // Clone array since it might be physical data array of a string vector
            String[] result = Arrays.copyOf(converted[0], converted[0].length);
            for (int j = result.length - 1; j >= 0; j--) {
                if (RRuntime.isNA(result[j])) {
                    result[j] = "NA";
                }
            }
            return result;
        } else {
            return prepareResult(sep, length, converted, maxLength);
        }
    }

    private String[] prepareResult(String sep, int length, String[][] converted, int maxLength) {
        String[] result = new String[maxLength];
        String lastResult = null;
        for (int i = 0; i < maxLength; i++) {
            if (i > 0) {
                // check if the next string is composed of the same elements
                int j;
                for (j = 0; j < length; j++) {
                    String element = converted[j][i % converted[j].length];
                    String lastElement = converted[j][(i - 1) % converted[j].length];
                    if (!Utils.fastPathIdentityEquals(element, lastElement)) {
                        break;
                    }
                }
                if (reusedResultProfile.profile(j == length)) {
                    result[i] = lastResult;
                    continue;
                }
            }
            result[i] = lastResult = concatStrings(converted, i, length, sep);
        }
        return result;
    }

    private static String concatStrings(String[][] converted, int index, int length, String sep) {
        // pre compute the string length for the StringBuilder
        int stringLength = -sep.length();
        for (int j = 0; j < length; j++) {
            String element = converted[j][index % converted[j].length];
            stringLength += element.length() + sep.length();
        }
        char[] chars = new char[stringLength];
        int pos = 0;
        for (int j = 0; j < length; j++) {
            if (j != 0) {
                getChars(sep, 0, sep.length(), chars, pos);

                pos += sep.length();
            }
            String element = converted[j][index % converted[j].length];
            getChars(element, 0, element.length(), chars, pos);
            pos += element.length();
        }
        assert pos == stringLength;
        return Utils.newString(chars);
    }

    private static String collapseString(String[] value, String collapseString) {
        int stringLength = -collapseString.length();
        for (int i = 0; i < value.length; i++) {
            stringLength += collapseString.length() + value[i].length();
        }
        char[] chars = new char[stringLength];
        int pos = 0;
        for (int i = 0; i < value.length; i++) {
            if (i > 0) {
                getChars(collapseString, 0, collapseString.length(), chars, pos);
                pos += collapseString.length();
            }
            String element = value[i];
            getChars(element, 0, element.length(), chars, pos);
            pos += element.length();
        }
        assert pos == stringLength;
        return Utils.newString(chars);
    }

    @TruffleBoundary
    private static void getChars(String source, int srcBegin, int srcEnd, char[] dest, int destBegin) {
        source.getChars(srcBegin, srcEnd, dest, destBegin);
    }

    private void ensureAsCharacterFuncNodes() {
        if (asCharacterDispatcher == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            asCharacterDispatcher = insert(RExplicitBaseEnvCallDispatcher.create("as.character"));
        }
        if (castAsCharacterResultNode == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            castAsCharacterResultNode = insert(newCastBuilder().mustBe(stringValue(), NON_STRING_ARG_TO_INTERNAL_PASTE).buildCastNode());
        }
    }

    private ClassHierarchyNode getClassHierarchyNode() {
        if (classHierarchyNode == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            classHierarchyNode = insert(ClassHierarchyNode.create());
        }
        return classHierarchyNode;
    }

    private CastNode getAsCharacterNode() {
        if (asCharacterNode == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            asCharacterNode = insert(newCastBuilder().castForeignObjects(true).returnIf(nullValue(), emptyStringVector()).asStringVector().buildCastNode());
        }
        return asCharacterNode;
    }

    /**
     * Tests for pattern = { scalar } intSequence { scalar }.
     */
    private static int isStringSequence(RAbstractListVector values, VectorDataLibrary valuesDataLib, int length) {
        Object valuesData = values.getData();
        int i = 0;
        // consume prefix
        while (i < length && isScalar(valuesDataLib.getDataAtAsObject(valuesData, i))) {
            i++;
        }
        if (i < length) {
            Object currVal = valuesDataLib.getDataAtAsObject(valuesData, i);
            if (currVal instanceof RIntVector && ((RIntVector) currVal).isSequence()) {
                // consume suffix
                int j = i + 1;
                while (j < length && isScalar(valuesDataLib.getDataAtAsObject(valuesData, j))) {
                    j++;
                }
                if (j == length) {
                    return i;
                }
            }
        }
        return -1;
    }

    private static boolean isScalar(Object dataAt) {
        return dataAt instanceof RScalar || dataAt instanceof String || dataAt instanceof Double || dataAt instanceof Integer || dataAt instanceof Byte;
    }

    private RStringVector createStringSequence(VirtualFrame frame, RAbstractListVector values, VectorDataLibrary valuesDataLib, int length, int seqPos, String sep) {
        assert isStringSequence(values, valuesDataLib, length) != -1;

        String[] prefix = new String[seqPos];
        for (int i = 0; i < seqPos; i++) {
            // castCharacterVector should yield a single-element string vector
            prefix[i] = castCharacterVector(frame, values.getDataAt(i)).getDataAt(0);
        }
        RIntSeqVectorData seq = (RIntSeqVectorData) ((RIntVector) values.getDataAt(seqPos)).getData();
        String[] suffix;
        if (seqPos + 1 < length) {
            suffix = new String[length - seqPos - 1];
            for (int i = seqPos + 1; i < length; i++) {
                suffix[i - seqPos - 1] = castCharacterVector(frame, values.getDataAt(i)).getDataAt(0);
            }
        } else {
            suffix = new String[0];
        }

        return buildStringSequence(prefix, seq, suffix, sep);

    }

    @TruffleBoundary
    private static RStringVector buildStringSequence(String[] prefixArr, RIntSeqVectorData seq, String[] suffixArr, String sep) {
        StringBuilder prefix = new StringBuilder();
        for (int i = 0; i < prefixArr.length; i++) {
            prefix.append(prefixArr[i]).append(sep);
        }

        StringBuilder suffix = new StringBuilder();
        if (suffixArr.length > 0) {
            suffix.append(sep);
            for (int i = 0; i < suffixArr.length; i++) {
                suffix.append(suffixArr[i]);
                if (i < suffixArr.length - 1) {
                    suffix.append(sep);
                }
            }
        }

        return RDataFactory.createStringSequence(prefix.toString(), suffix.toString(), seq.getStart(), seq.getStride(), seq.getLength());
    }

}
