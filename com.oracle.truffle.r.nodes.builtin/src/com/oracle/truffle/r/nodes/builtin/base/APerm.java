/*
 * This program is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation; either version 2 of the License, or (at your option)
 * any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License for
 * more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program; if not, write to the Free Software Foundation, Inc., 51
 * Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 *
 * Copyright (c) 2014, Purdue University
 * Copyright (c) 2014, 2021, Oracle and/or its affiliates
 *
 * All rights reserved.
 */
package com.oracle.truffle.r.nodes.builtin.base;

import static com.oracle.truffle.r.nodes.builtin.CastBuilder.Predef.asIntegerVectorClosure;
import static com.oracle.truffle.r.nodes.builtin.CastBuilder.Predef.complexValue;
import static com.oracle.truffle.r.nodes.builtin.CastBuilder.Predef.logicalValue;
import static com.oracle.truffle.r.nodes.builtin.CastBuilder.Predef.numericValue;
import static com.oracle.truffle.r.nodes.builtin.CastBuilder.Predef.stringValue;
import static com.oracle.truffle.r.runtime.builtins.RBehavior.PURE;
import static com.oracle.truffle.r.runtime.builtins.RBuiltinKind.INTERNAL;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.api.profiles.ConditionProfile;
import com.oracle.truffle.r.nodes.builtin.RBuiltinNode;
import com.oracle.truffle.r.runtime.RError;
import com.oracle.truffle.r.runtime.RError.Message;
import com.oracle.truffle.r.runtime.RRuntime;
import com.oracle.truffle.r.runtime.Utils;
import com.oracle.truffle.r.runtime.builtins.RBuiltin;
import com.oracle.truffle.r.runtime.data.AbstractContainerLibrary;
import com.oracle.truffle.r.runtime.data.RDataFactory;
import com.oracle.truffle.r.runtime.data.RIntVector;
import com.oracle.truffle.r.runtime.data.RList;
import com.oracle.truffle.r.runtime.data.RNull;
import com.oracle.truffle.r.runtime.data.RStringVector;
import com.oracle.truffle.r.runtime.data.VectorDataLibrary;
import com.oracle.truffle.r.runtime.data.VectorDataLibrary.RandomAccessIterator;
import com.oracle.truffle.r.runtime.data.VectorDataLibrary.RandomAccessWriteIterator;
import com.oracle.truffle.r.runtime.data.model.RAbstractVector;
import com.oracle.truffle.r.runtime.data.nodes.ExtractListElement;
import com.oracle.truffle.r.runtime.data.nodes.VectorReuse;
import com.oracle.truffle.r.runtime.data.nodes.attributes.RemoveRegAttributesNode;
import com.oracle.truffle.r.runtime.data.nodes.attributes.SpecialAttributesFunctions.GetDimAttributeNode;
import com.oracle.truffle.r.runtime.data.nodes.attributes.SpecialAttributesFunctions.GetDimNamesAttributeNode;
import com.oracle.truffle.r.runtime.data.nodes.attributes.SpecialAttributesFunctions.GetNamesAttributeNode;
import com.oracle.truffle.r.runtime.data.nodes.attributes.SpecialAttributesFunctions.SetDimAttributeNode;
import com.oracle.truffle.r.runtime.data.nodes.attributes.SpecialAttributesFunctions.SetDimNamesAttributeNode;

// TODO: add (permuted) dimnames to the result
@RBuiltin(name = "aperm", kind = INTERNAL, parameterNames = {"a", "perm", "resize"}, behavior = PURE)
public abstract class APerm extends RBuiltinNode.Arg3 {

    private final BranchProfile emptyPermVector = BranchProfile.create();
    private final ConditionProfile mustResize = ConditionProfile.createBinaryProfile();
    private final ConditionProfile setDimNamesProfile = ConditionProfile.createBinaryProfile();

    @Child private SetDimNamesAttributeNode setDimNames;
    @Child private ExtractListElement extractListElement;

    static {
        Casts casts = new Casts(APerm.class);
        casts.arg("a").mustNotBeNull(RError.Message.FIRST_ARG_MUST_BE_ARRAY);
        casts.arg("perm").allowNull().mustBe(numericValue().or(stringValue()).or(complexValue())).mapIf(numericValue().or(complexValue()), asIntegerVectorClosure(true, true, false));
        casts.arg("resize").mustBe(numericValue().or(logicalValue()), Message.INVALID_LOGICAL, "resize").asLogicalVector().findFirst();
    }

    private void checkErrorConditions(int[] dim) {
        if (!GetDimAttributeNode.isArray(dim)) {
            throw error(RError.Message.FIRST_ARG_MUST_BE_ARRAY);
        }
    }

    @Specialization(limit = "getGenericVectorAccessCacheSize()")
    protected RAbstractVector aPermNull(RAbstractVector vector, @SuppressWarnings("unused") RNull permVector, byte resize,
                    @CachedLibrary("vector") AbstractContainerLibrary vectorLib,
                    @CachedLibrary("vector.getData()") VectorDataLibrary vectorDataLib,
                    @CachedLibrary(limit = "getCacheSize(1)") VectorDataLibrary resultDataLib,
                    @Cached("create()") GetDimAttributeNode getDimsNode,
                    @Cached("create()") SetDimAttributeNode setDimNode) {

        int[] dim = getDimsNode.getDimensions(vector);
        checkErrorConditions(dim);
        final int diml = dim.length;

        Object vectorData = vector.getData();
        int resultLen = vectorDataLib.getLength(vectorData);
        RAbstractVector result = vectorLib.createEmptySameType(vector, resultLen, false);

        if (mustResize.profile(resize == RRuntime.LOGICAL_TRUE)) {
            int[] pDim = new int[diml];
            for (int i = 0; i < diml; i++) {
                pDim[i] = dim[diml - 1 - i];
            }
            setDimNode.setDimensions(result, pDim);
        } else {
            setDimNode.setDimensions(result, dim);
        }

        // Move along the old array using stride
        int[] posV = new int[diml];
        int[] ap = new int[diml];

        Object resultData = result.getData();
        RandomAccessWriteIterator resultIt = resultDataLib.randomAccessWriteIterator(resultData);
        boolean neverSeenNA = false;
        try {
            RandomAccessIterator vectorIt = vectorDataLib.randomAccessIterator(vectorData);
            for (int i = 0; i < resultLen; i++) {
                for (int j = 0; j < ap.length; j++) {
                    ap[diml - 1 - j] = posV[j];
                }
                int pos = toPos(ap, dim);
                resultDataLib.transfer(resultData, resultIt, i, vectorDataLib, vectorIt, vectorData, pos);
                for (int j = 0; j < diml; j++) {
                    posV[j]++;
                    if (posV[j] < dim[diml - 1 - j]) {
                        break;
                    }
                    posV[j] = 0;
                }
            }
            neverSeenNA = vectorDataLib.isComplete(vectorData) || vectorDataLib.getNACheck(vectorData).neverSeenNA();
        } finally {
            resultDataLib.commitRandomAccessWriteIterator(resultData, resultIt, neverSeenNA);
        }

        return result;
    }

    @TruffleBoundary
    @Specialization(replaces = "aPermNull")
    protected RAbstractVector aPermNullGeneric(RAbstractVector vector, @SuppressWarnings("unused") RNull permVector, byte resize,
                    @Cached("create()") GetDimAttributeNode getDimsNode,
                    @Cached("create()") SetDimAttributeNode setDimNode) {
        return aPermNull(vector, permVector, resize, AbstractContainerLibrary.getFactory().getUncached(), VectorDataLibrary.getFactory().getUncached(), VectorDataLibrary.getFactory().getUncached(),
                        getDimsNode, setDimNode);
    }

    @Specialization(guards = {"isIdentityPermutation(vector, permVector, getDimsNode)", "reuseNonSharedNode.supports(vector)"}, limit = "getVectorAccessCacheSize()")
    protected RAbstractVector doIdentity(RAbstractVector vector, @SuppressWarnings("unused") RIntVector permVector, @SuppressWarnings("unused") byte resize,
                    @Cached("create()") RemoveRegAttributesNode removeClassAttrNode,
                    @Cached("create()") GetDimAttributeNode getDimsNode,
                    @Cached("createNonShared(vector)") VectorReuse reuseNonSharedNode) {
        int[] dim = getDimsNode.getDimensions(vector);
        checkErrorConditions(dim);

        RAbstractVector reused = reuseNonSharedNode.getMaterializedResult(vector);

        // we have to remove some attributes
        // remove all regular attributes (including the class attribute)
        removeClassAttrNode.execute(reused);

        // also ensures that we do not give a closure away
        return reused;
    }

    @Specialization(replaces = "doIdentity", guards = "isIdentityPermutation(vector, permVector, getDimsNode)")
    protected RAbstractVector doIdentityGeneric(RAbstractVector vector, RIntVector permVector, byte resize,
                    @Cached("create()") RemoveRegAttributesNode removeClassAttrNode,
                    @Cached("create()") GetDimAttributeNode getDimsNode,
                    @Cached("createNonSharedGeneric()") VectorReuse reuseNonSharedNode) {
        return doIdentity(vector, permVector, resize, removeClassAttrNode, getDimsNode, reuseNonSharedNode);
    }

    @Specialization(guards = "!isIdentityPermutation(vector, permVector, getDimsNode)", limit = "getGenericVectorAccessCacheSize()")
    protected RAbstractVector doNonIdentity(RAbstractVector vector, RIntVector permVector, byte resize,
                    @CachedLibrary("vector") AbstractContainerLibrary vectorLib,
                    @CachedLibrary("vector.getData()") VectorDataLibrary vectorDataLib,
                    @CachedLibrary(limit = "getCacheSize(1)") VectorDataLibrary resultDataLib,
                    @Cached("create()") GetNamesAttributeNode getNames,
                    @Cached("create()") GetDimAttributeNode getDimsNode,
                    @Cached("create()") SetDimAttributeNode setDimsNode,
                    @Cached("create()") GetDimNamesAttributeNode getDimNamesNode,
                    @CachedLibrary(limit = "getGenericDataLibraryCacheSize()") VectorDataLibrary namesDataLib) {

        int[] dim = getDimsNode.getDimensions(vector);
        checkErrorConditions(dim);
        int[] perm = getPermute(dim, permVector);

        int[] posV = new int[dim.length];
        int[] pDim = applyPermute(dim, perm, false);

        Object vectorData = vector.getData();
        int resultLen = vectorDataLib.getLength(vectorData);
        RAbstractVector result = vectorLib.createEmptySameType(vector, resultLen, false);

        setDimsNode.setDimensions(result, resize == RRuntime.LOGICAL_TRUE ? pDim : dim);

        Object resultData = result.getData();
        RandomAccessWriteIterator resultIt = resultDataLib.randomAccessWriteIterator(resultData);
        boolean neverSeenNA = false;
        try {
            RandomAccessIterator vectorIt = vectorDataLib.randomAccessIterator(vectorData);
            // Move along the old array using stride
            for (int i = 0; i < resultLen; i++) {
                int pos = toPos(applyPermute(posV, perm, true), dim);
                resultDataLib.transfer(resultData, resultIt, i, vectorDataLib, vectorIt, vectorData, pos);
                incArray(posV, pDim);
            }
            neverSeenNA = vectorDataLib.isComplete(vectorData) || vectorDataLib.getNACheck(vectorData).neverSeenNA();
        } finally {
            resultDataLib.commitRandomAccessWriteIterator(resultData, resultIt, neverSeenNA);
        }

        RList dimNames = getDimNamesNode.getDimNames(vector);
        if (setDimNamesProfile.profile(dimNames != null)) {
            if (setDimNames == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                setDimNames = insert(SetDimNamesAttributeNode.create());
            }
            if (extractListElement == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                extractListElement = insert(ExtractListElement.create());
            }
            Object[] permData = new Object[dimNames.getLength()];
            RStringVector names = getNames.getNames(dimNames); // May be null for "list(NULL,NULL)"
            Object namesData = names != null ? names.getData() : null;
            String[] permNames = (names != null) ? new String[permData.length] : null;
            for (int i = 0; i < permData.length; i++) {
                permData[i] = extractListElement.execute(dimNames, perm[i]);
                if (permNames != null) {
                    permNames[i] = namesDataLib.getStringAt(namesData, perm[i]);
                }
            }
            RList permDimNames = RDataFactory.createList(permData, (names != null) ? RDataFactory.createStringVector(permNames, namesDataLib.isComplete(namesData)) : null);
            setDimNames.setDimNames(result, permDimNames);
        }

        return result;
    }

    @TruffleBoundary
    @Specialization(replaces = "doNonIdentity", guards = "!isIdentityPermutation(vector, permVector, getDimsNode)")
    protected RAbstractVector doNonIdentityGeneric(RAbstractVector vector, RIntVector permVector, byte resize,
                    @Cached("create()") GetNamesAttributeNode getNames,
                    @Cached("create()") GetDimAttributeNode getDimsNode,
                    @Cached("create()") SetDimAttributeNode setDimsNode,
                    @Cached("create()") GetDimNamesAttributeNode getDimNamesNode,
                    @CachedLibrary(limit = "getGenericDataLibraryCacheSize()") VectorDataLibrary namesDataLib) {
        AbstractContainerLibrary containerLib = AbstractContainerLibrary.getFactory().getUncached();
        VectorDataLibrary dataLib = VectorDataLibrary.getFactory().getUncached();
        return doNonIdentity(vector, permVector, resize, containerLib, dataLib, dataLib, getNames, getDimsNode, setDimsNode, getDimNamesNode, namesDataLib);
    }

    protected boolean isIdentityPermutation(RAbstractVector v, RIntVector permVector, GetDimAttributeNode getDimAttributeNode) {
        int[] dimensions = getDimAttributeNode.getDimensions(v);
        if (dimensions != null) {
            int[] perm = getPermute(dimensions, permVector);
            for (int i = 0; i < dimensions.length; i++) {
                if (i != perm[i]) {
                    return false;
                }
            }
            return true;
        }
        return false;
    }

    @Specialization(guards = "reuseNonSharedNode.supports(vector)", limit = "getGenericVectorAccessCacheSize()")
    protected RAbstractVector aPerm(RAbstractVector vector, RStringVector permVector, byte resize,
                    @CachedLibrary("vector") AbstractContainerLibrary vectorLib,
                    @CachedLibrary("vector.getData()") VectorDataLibrary vectorDataLib,
                    @CachedLibrary(limit = "getCacheSize(1)") VectorDataLibrary resultDataLib,
                    @Cached("createBinaryProfile()") ConditionProfile isIdentityProfile,
                    @Cached("create()") GetNamesAttributeNode getNames,
                    @Cached("create()") GetDimAttributeNode getDimsNode,
                    @Cached("create()") SetDimAttributeNode setDimsNode,
                    @Cached("create()") RemoveRegAttributesNode removeClassAttrNode,
                    @Cached("create()") GetDimNamesAttributeNode getDimNamesNode,
                    @Cached("createNonShared(vector)") VectorReuse reuseNonSharedNode,
                    @CachedLibrary(limit = "getGenericDataLibraryCacheSize()") VectorDataLibrary namesDataLib) {
        RList dimNames = getDimNamesNode.getDimNames(vector);
        if (dimNames == null) {
            // TODO: this error is reported after IS_OF_WRONG_LENGTH in GnuR
            throw error(RError.Message.DOES_NOT_HAVE_DIMNAMES, "a");
        }

        int[] perm = new int[permVector.getLength()];
        for (int i = 0; i < perm.length; i++) {
            for (int dimNamesIdx = 0; dimNamesIdx < dimNames.getLength(); dimNamesIdx++) {
                if (Utils.equals(dimNames.getDataAt(dimNamesIdx), permVector.getDataAt(i))) {
                    perm[i] = dimNamesIdx;
                    break;
                }
            }
            // TODO: not found dimname error
        }

        com.oracle.truffle.r.runtime.data.RIntVector permIntVector = RDataFactory.createIntVector(perm, true);
        if (isIdentityProfile.profile(isIdentityPermutation(vector, permIntVector, getDimsNode))) {
            return doIdentity(vector, permIntVector, resize, removeClassAttrNode, getDimsNode, reuseNonSharedNode);
        }

        // Note: if this turns out to be slow, we can cache the permutation
        return doNonIdentity(vector, permIntVector, resize, vectorLib, vectorDataLib, resultDataLib, getNames, getDimsNode, setDimsNode, getDimNamesNode, namesDataLib);
    }

    @Specialization(replaces = "aPerm", limit = "getGenericVectorAccessCacheSize()")
    protected RAbstractVector aPermGeneric(RAbstractVector vector, RStringVector permVector, byte resize,
                    @CachedLibrary("vector") AbstractContainerLibrary vectorLib,
                    @CachedLibrary("vector.getData()") VectorDataLibrary vectorDataLib,
                    @CachedLibrary(limit = "getCacheSize(1)") VectorDataLibrary resultDataLib,
                    @Cached("createBinaryProfile()") ConditionProfile isIdentityProfile,
                    @Cached("create()") GetNamesAttributeNode getNames,
                    @Cached("create()") GetDimAttributeNode getDimsNode,
                    @Cached("create()") SetDimAttributeNode setDimsNode,
                    @Cached("create()") RemoveRegAttributesNode removeClassAttrNode,
                    @Cached("create()") GetDimNamesAttributeNode getDimNamesNode,
                    @Cached("createNonSharedGeneric()") VectorReuse reuseNonSharedNode,
                    @CachedLibrary(limit = "getGenericDataLibraryCacheSize()") VectorDataLibrary namesDataLib) {
        return aPerm(vector, permVector, resize, vectorLib, vectorDataLib, resultDataLib, isIdentityProfile, getNames, getDimsNode, setDimsNode, removeClassAttrNode, getDimNamesNode,
                        reuseNonSharedNode, namesDataLib);
    }

    private static int[] getReverse(int[] dim) {
        int[] arrayPerm = new int[dim.length];
        for (int i = 0; i < dim.length; i++) {
            arrayPerm[i] = dim.length - 1 - i;
        }
        return arrayPerm;
    }

    private int[] getPermute(int[] dim, RIntVector perm) {
        if (perm.getLength() == 0) {
            // If perm missing, the default is a reverse of the dim.
            emptyPermVector.enter();
            return getReverse(dim);
        } else if (perm.getLength() == dim.length) {
            // Check for valid permute
            int[] arrayPerm = new int[dim.length];
            boolean[] visited = new boolean[arrayPerm.length];
            for (int i = 0; i < perm.getLength(); i++) {
                int pos = perm.getDataAt(i) - 1; // Adjust to zero based permute.
                if (pos >= perm.getLength() || pos < 0) {
                    throw error(RError.Message.VALUE_OUT_OF_RANGE, "perm");
                }
                arrayPerm[i] = pos;
                if (visited[pos]) {
                    // Duplicate dimension mapping in permute
                    throw error(RError.Message.INVALID_ARGUMENT, "perm");
                }
                visited[pos] = true;
            }
            return arrayPerm;
        } else {
            // perm size error
            throw error(RError.Message.IS_OF_WRONG_LENGTH, "perm", perm.getLength(), dim.length);
        }
    }

    /**
     * Apply permute to an equal sized array.
     */
    private static int[] applyPermute(int[] a, int[] perm, boolean reverse) {
        int[] newA = a.clone();
        if (reverse) {
            for (int i = 0; i < newA.length; i++) {
                newA[perm[i]] = a[i];
            }
        } else {
            for (int i = 0; i < newA.length; i++) {
                newA[i] = a[perm[i]];
            }
        }
        return newA;
    }

    /**
     * Increment a stride array. Note: First input array may be modified.
     */
    private static void incArray(int[] a, int[] dim) {
        for (int i = 0; i < a.length; i++) {
            a[i]++;
            if (a[i] < dim[i]) {
                break;
            }
            a[i] = 0;
        }
    }

    /**
     * Stride array to a linear position.
     */
    private static int toPos(int[] a, int[] dim) {
        int pos = a[0];
        for (int i = 1; i < a.length; i++) {
            int dimSizeBefore = 1; // Total size of dimensions before the ith dimension.
            for (int j = i - 1; j >= 0; j--) {
                dimSizeBefore *= dim[j];
            }
            pos += a[i] * dimSizeBefore;
        }
        return pos;
    }
}
