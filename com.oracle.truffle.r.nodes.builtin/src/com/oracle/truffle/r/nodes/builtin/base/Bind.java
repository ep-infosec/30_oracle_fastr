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

import static com.oracle.truffle.r.runtime.builtins.RBehavior.COMPLEX;
import static com.oracle.truffle.r.runtime.builtins.RBuiltinKind.INTERNAL;

import java.util.Arrays;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.api.profiles.ConditionProfile;
import com.oracle.truffle.api.profiles.ValueProfile;
import com.oracle.truffle.r.nodes.RASTUtils;
import com.oracle.truffle.r.nodes.access.variables.ReadVariableNode;
import com.oracle.truffle.r.nodes.builtin.RBuiltinNode;
import com.oracle.truffle.r.nodes.function.ClassHierarchyNode;
import com.oracle.truffle.r.nodes.function.ClassHierarchyNodeGen;
import com.oracle.truffle.r.nodes.function.GetBaseEnvFrameNode;
import com.oracle.truffle.r.nodes.function.S3FunctionLookupNode;
import com.oracle.truffle.r.nodes.function.S3FunctionLookupNode.Result;
import com.oracle.truffle.r.nodes.function.call.RExplicitCallNode;
import com.oracle.truffle.r.nodes.unary.CastComplexNode;
import com.oracle.truffle.r.nodes.unary.CastDoubleNode;
import com.oracle.truffle.r.nodes.unary.CastIntegerNode;
import com.oracle.truffle.r.nodes.unary.CastListNode;
import com.oracle.truffle.r.nodes.unary.CastLogicalNode;
import com.oracle.truffle.r.nodes.unary.CastLogicalNodeGen;
import com.oracle.truffle.r.nodes.unary.CastStringNode;
import com.oracle.truffle.r.nodes.unary.PrecedenceNode;
import com.oracle.truffle.r.nodes.unary.PrecedenceNodeGen;
import com.oracle.truffle.r.runtime.ArgumentsSignature;
import com.oracle.truffle.r.runtime.RArguments;
import com.oracle.truffle.r.runtime.RError;
import com.oracle.truffle.r.runtime.RError.Message;
import com.oracle.truffle.r.runtime.RRuntime;
import com.oracle.truffle.r.runtime.Utils;
import com.oracle.truffle.r.runtime.builtins.RBuiltin;
import com.oracle.truffle.r.runtime.data.RArgsValuesAndNames;
import com.oracle.truffle.r.runtime.data.RDataFactory;
import com.oracle.truffle.r.runtime.data.RFunction;
import com.oracle.truffle.r.runtime.data.RList;
import com.oracle.truffle.r.runtime.data.RNull;
import com.oracle.truffle.r.runtime.data.RPromise;
import com.oracle.truffle.r.runtime.data.RStringVector;
import com.oracle.truffle.r.runtime.data.RSymbol;
import com.oracle.truffle.r.runtime.data.RTypes;
import com.oracle.truffle.r.runtime.data.VectorDataLibrary;
import com.oracle.truffle.r.runtime.data.model.RAbstractVector;
import com.oracle.truffle.r.runtime.data.nodes.attributes.SpecialAttributesFunctions.ExtractNamesAttributeNode;
import com.oracle.truffle.r.runtime.data.nodes.attributes.SpecialAttributesFunctions.GetDimAttributeNode;
import com.oracle.truffle.r.runtime.data.nodes.attributes.SpecialAttributesFunctions.GetDimNamesAttributeNode;
import com.oracle.truffle.r.runtime.data.nodes.attributes.SpecialAttributesFunctions.SetDimAttributeNode;
import com.oracle.truffle.r.runtime.data.nodes.attributes.SpecialAttributesFunctions.SetDimNamesAttributeNode;
import com.oracle.truffle.r.runtime.env.REnvironment;
import com.oracle.truffle.r.runtime.nodes.RBaseNodeWithWarnings;
import com.oracle.truffle.r.runtime.nodes.unary.CastNode;
import com.oracle.truffle.r.runtime.nodes.unary.CastToVectorNode;
import com.oracle.truffle.r.runtime.nodes.unary.CastToVectorNodeGen;
import com.oracle.truffle.r.runtime.ops.na.NACheck;

public abstract class Bind extends RBaseNodeWithWarnings {

    protected enum BindType {
        rbind,
        cbind;
    }

    protected static final int NO_PRECEDENCE = PrecedenceNode.NO_PRECEDENCE;
    protected static final int RAW_PRECEDENCE = PrecedenceNode.RAW_PRECEDENCE;
    protected static final int LOGICAL_PRECEDENCE = PrecedenceNode.LOGICAL_PRECEDENCE;
    protected static final int INT_PRECEDENCE = PrecedenceNode.INT_PRECEDENCE;
    protected static final int DOUBLE_PRECEDENCE = PrecedenceNode.DOUBLE_PRECEDENCE;
    protected static final int COMPLEX_PRECEDENCE = PrecedenceNode.COMPLEX_PRECEDENCE;
    protected static final int STRING_PRECEDENCE = PrecedenceNode.STRING_PRECEDENCE;
    protected static final int LIST_PRECEDENCE = PrecedenceNode.LIST_PRECEDENCE;
    protected static final int EXPRESSION_PRECEDENCE = PrecedenceNode.EXPRESSION_PRECEDENCE;

    public abstract Object execute(VirtualFrame frame, int deparseLevel, Object[] args, RArgsValuesAndNames promiseArgs, int precedence);

    @Child private CastToVectorNode castVector;
    @Child private CastLogicalNode castLogical;
    @Child private GetDimAttributeNode getDimsNode;
    @Child private SetDimNamesAttributeNode setDimNamesNode;

    private final BindType type;

    private final ConditionProfile nullNamesProfile = ConditionProfile.createBinaryProfile();
    private final ConditionProfile emptyVectorProfile = ConditionProfile.createBinaryProfile();
    private final ConditionProfile allEmptyVectorProfile = ConditionProfile.createBinaryProfile();
    private final BranchProfile nonNullNames = BranchProfile.create();
    private final ConditionProfile dimNamesInComplete = ConditionProfile.createBinaryProfile();
    private final NACheck naCheck = NACheck.create();
    protected final ValueProfile resultProfile = ValueProfile.createClassProfile();
    protected final ValueProfile vectorProfile = ValueProfile.createClassProfile();

    protected Bind(BindType type) {
        this.type = type;
    }

    protected int[] getVectorDimensions(RAbstractVector v) {
        if (getDimsNode == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            getDimsNode = insert(GetDimAttributeNode.create());
        }
        return getDimsNode.getDimensions(v);
    }

    protected RAbstractVector castVector(Object value) {
        if (castVector == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            castVector = insert(CastToVectorNodeGen.create(false));
        }
        return (RAbstractVector) castVector.doCast(value);
    }

    protected Object castLogical(Object operand, boolean preserveAllAttr) {
        if (castLogical == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            castLogical = insert(CastLogicalNodeGen.create(true, preserveAllAttr, preserveAllAttr));
        }
        return castLogical.doCast(operand);
    }

    @SuppressWarnings("unused")
    @Specialization(guards = {"precedence == NO_PRECEDENCE"})
    protected RNull allNull(VirtualFrame frame, int deparseLevel, Object[] args, RArgsValuesAndNames promiseArgs, int precedence) {
        return RNull.instance;
    }

    private Object bindInternal(int deparseLevel, Object[] args, RArgsValuesAndNames promiseArgs, CastNode castNode, boolean needsVectorCast, SetDimAttributeNode setDimNode,
                    GetDimNamesAttributeNode getDimNamesNode, ExtractNamesAttributeNode extractNamesNode, VectorDataLibrary dispatchedDataLib) {
        ArgumentsSignature signature = promiseArgs.getSignature();
        String[] vecNames = nullNamesProfile.profile(signature.getNonNullCount() == 0) ? null : new String[signature.getLength()];
        RAbstractVector[] vectors = new RAbstractVector[args.length];
        boolean complete = true;
        int ind = 0;
        naCheck.enable(true);
        RAbstractVector fromNotNullArgVector = null;
        for (int i = 0; i < args.length; i++) {
            setVectorNames(vecNames, ind, signature.getName(i));
            RAbstractVector vector = getVector(args[i], castNode, needsVectorCast);
            if (fromNotNullArgVector == null && !RTypes.isRNull(args[i])) {
                fromNotNullArgVector = vector;
            }
            if (emptyVectorProfile.profile(vector.getLength() == 0)) {
                int[] dims = getVectorDimensions(vector);
                int srcDim1Ind = type == BindType.cbind ? 0 : 1;
                if (dims != null && dims[srcDim1Ind] > 0) {
                    // an empty matrix with a positive column, resp. row, dimension cannot be
                    // ignored
                    vectors[ind] = vector;
                    complete &= dispatchedDataLib.isComplete(vector.getData());
                    ind++;
                }
                // nothing to do
            } else {
                vectors[ind] = vector;
                complete &= dispatchedDataLib.isComplete(vector.getData());
                ind++;
            }
        }
        boolean allEmpty = ind == 0;
        if (emptyVectorProfile.profile(ind < args.length)) {
            if (allEmptyVectorProfile.profile(allEmpty)) {
                for (int i = 0; i < args.length; i++) {
                    setVectorNames(vecNames, i, signature.getName(i));
                    vectors[i] = getVector(args[i], castNode, needsVectorCast);
                    complete &= dispatchedDataLib.isComplete(vectors[i].getData());
                }
            } else {
                if (vecNames != null) {
                    nonNullNames.enter();
                    vecNames = Arrays.copyOf(vecNames, ind);
                }
                vectors = Arrays.copyOf(vectors, ind);
            }
        }

        int[] bindDims = new int[vectors.length];
        int[] resultDimensions = new int[2];
        boolean rowsAndColumnsNotEqual = getResultDimensions(vectors, resultDimensions, bindDims);
        RAbstractVector resultVec;
        if (fromNotNullArgVector != null) {
            resultVec = resultProfile.profile(vectorProfile.profile(fromNotNullArgVector).createEmptySameType(resultDimensions[0] * resultDimensions[1], complete));
        } else {
            resultVec = resultProfile.profile(vectorProfile.profile(vectors[0]).createEmptySameType(resultDimensions[0] * resultDimensions[1], complete));
        }

        if (type == BindType.cbind) {
            return genericCBind(promiseArgs, vectors, resultVec, resultDimensions, bindDims, rowsAndColumnsNotEqual, allEmpty, vecNames, deparseLevel, setDimNode, getDimNamesNode,
                            extractNamesNode);
        } else {
            return genericRBind(promiseArgs, vectors, resultVec, resultDimensions, bindDims, rowsAndColumnsNotEqual, allEmpty, vecNames, deparseLevel, setDimNode, getDimNamesNode,
                            extractNamesNode);
        }
    }

    private RAbstractVector getVector(Object arg, CastNode castNode, boolean needsVectorCast) {
        Object result = castNode.doCast(arg);
        RAbstractVector vector;
        if (needsVectorCast) {
            vector = castVector(result);
        } else {
            vector = (RAbstractVector) result;
        }
        return vector;
    }

    private void setVectorNames(String[] vecNames, int vectorInd, String signatureName) {
        if (vecNames != null) {
            nonNullNames.enter();
            vecNames[vectorInd] = signatureName;
            naCheck.check(vecNames[vectorInd]);
        }
    }

    @Specialization(guards = {"precedence == LOGICAL_PRECEDENCE", "args.length > 1"})
    protected Object allLogical(int deparseLevel, Object[] args, RArgsValuesAndNames promiseArgs, @SuppressWarnings("unused") int precedence,  //
                    @Cached("create()") CastLogicalNode cast,
                    @Cached("create()") SetDimAttributeNode setDimNode,
                    @Cached("create()") GetDimNamesAttributeNode getDimNamesNode,
                    @Cached("create()") ExtractNamesAttributeNode extractNamesNode,
                    @CachedLibrary(limit = "getGenericDataLibraryCacheSize()") VectorDataLibrary namesDataLib) {
        return bindInternal(deparseLevel, args, promiseArgs, cast, true, setDimNode, getDimNamesNode, extractNamesNode, namesDataLib);
    }

    @Specialization(guards = {"precedence == INT_PRECEDENCE", "args.length > 1"})
    protected Object allInt(int deparseLevel, Object[] args, RArgsValuesAndNames promiseArgs, @SuppressWarnings("unused") int precedence, //
                    @Cached("create()") CastIntegerNode cast,
                    @Cached("create()") SetDimAttributeNode setDimNode,
                    @Cached("create()") GetDimNamesAttributeNode getDimNamesNode,
                    @Cached("create()") ExtractNamesAttributeNode extractNamesNode,
                    @CachedLibrary(limit = "getGenericDataLibraryCacheSize()") VectorDataLibrary namesDataLib) {
        return bindInternal(deparseLevel, args, promiseArgs, cast, true, setDimNode, getDimNamesNode, extractNamesNode, namesDataLib);
    }

    @Specialization(guards = {"precedence == DOUBLE_PRECEDENCE", "args.length > 1"})
    protected Object allDouble(int deparseLevel, Object[] args, RArgsValuesAndNames promiseArgs, @SuppressWarnings("unused") int precedence, //
                    @Cached("create()") CastDoubleNode cast,
                    @Cached("create()") SetDimAttributeNode setDimNode,
                    @Cached("create()") GetDimNamesAttributeNode getDimNamesNode,
                    @Cached("create()") ExtractNamesAttributeNode extractNamesNode,
                    @CachedLibrary(limit = "getGenericDataLibraryCacheSize()") VectorDataLibrary namesDataLib) {
        return bindInternal(deparseLevel, args, promiseArgs, cast, true, setDimNode, getDimNamesNode, extractNamesNode, namesDataLib);
    }

    @Specialization(guards = {"precedence == STRING_PRECEDENCE", "args.length> 1"})
    protected Object allString(int deparseLevel, Object[] args, RArgsValuesAndNames promiseArgs, @SuppressWarnings("unused") int precedence, //
                    @Cached("create()") CastStringNode cast,
                    @Cached("create()") SetDimAttributeNode setDimNode,
                    @Cached("create()") GetDimNamesAttributeNode getDimNamesNode,
                    @Cached("create()") ExtractNamesAttributeNode extractNamesNode,
                    @CachedLibrary(limit = "getGenericDataLibraryCacheSize()") VectorDataLibrary namesDataLib) {
        return bindInternal(deparseLevel, args, promiseArgs, cast, true, setDimNode, getDimNamesNode, extractNamesNode, namesDataLib);
    }

    @Specialization(guards = {"precedence == COMPLEX_PRECEDENCE", "args.length > 1"})
    protected Object allComplex(int deparseLevel, Object[] args, RArgsValuesAndNames promiseArgs, @SuppressWarnings("unused") int precedence, //
                    @Cached("create()") CastComplexNode cast,
                    @Cached("create()") SetDimAttributeNode setDimNode,
                    @Cached("create()") GetDimNamesAttributeNode getDimNamesNode,
                    @Cached("create()") ExtractNamesAttributeNode extractNamesNode,
                    @CachedLibrary(limit = "getGenericDataLibraryCacheSize()") VectorDataLibrary namesDataLib) {
        return bindInternal(deparseLevel, args, promiseArgs, cast, true, setDimNode, getDimNamesNode, extractNamesNode, namesDataLib);
    }

    @Specialization(guards = {"precedence == LIST_PRECEDENCE", "args.length > 1"})
    protected Object allList(int deparseLevel, Object[] args, RArgsValuesAndNames promiseArgs, @SuppressWarnings("unused") int precedence, //
                    @Cached("create()") CastListNode cast,
                    @Cached("create()") SetDimAttributeNode setDimNode,
                    @Cached("create()") GetDimNamesAttributeNode getDimNamesNode,
                    @Cached("create()") ExtractNamesAttributeNode extractNamesNode,
                    @CachedLibrary(limit = "getGenericDataLibraryCacheSize()") VectorDataLibrary namesDataLib) {
        return bindInternal(deparseLevel, args, promiseArgs, cast, false, setDimNode, getDimNamesNode, extractNamesNode, namesDataLib);
    }

    /**
     * Compute dimnames for rows (cbind) or columns (rbind) from names of elements of combined
     * vectors.
     */
    protected Object getDimResultNamesFromElements(RAbstractVector vec, int dimLength, int dimInd, GetDimNamesAttributeNode getDimNamesNode, ExtractNamesAttributeNode extractNamesNode) {
        Object firstDimResultNames = RNull.instance;
        Object firstDimNames = RNull.instance;
        int[] dim = getVectorDimensions(vec);
        if (GetDimAttributeNode.isMatrix(dim)) {
            RList vecDimNames = getDimNamesNode.getDimNames(vec);
            if (vecDimNames != null) {
                firstDimNames = vecDimNames.getDataAt(dimInd);
            }
        } else {
            if (!GetDimAttributeNode.isArray(dim) || dim.length == 1) {
                RStringVector names = extractNamesNode.execute(vec);
                firstDimNames = names == null ? RNull.instance : names;
            }
            // dimnames are simply ignored for arrays with 3 and more dimensions
        }
        if (firstDimNames != RNull.instance) {
            RStringVector names = (RStringVector) firstDimNames;
            if (names != null && names.getLength() == dimLength) {
                firstDimResultNames = names;
            }
        }
        return firstDimResultNames;
    }

    /**
     * Compute dimnames for columns (cbind) or rows (rbind) from names of vectors being combined or
     * by deparsing.
     */
    protected int getDimResultNamesFromVectors(RArgsValuesAndNames promiseArgs, RAbstractVector vec, String[] argNames, int resDim, int oldInd, int vecInd, int deparseLevel,
                    String[] dimNamesArray, int dimNamesInd, GetDimNamesAttributeNode getDimNamesNode) {
        int ind = oldInd;
        int[] rawDimensions = getVectorDimensions(vec);
        if (GetDimAttributeNode.isMatrix(rawDimensions)) {
            RList vecDimNames = getDimNamesNode.getDimNames(vec);
            if (vecDimNames != null) {
                Object resDimNames = vecDimNames.getDataAt(dimNamesInd);
                if (resDimNames != RNull.instance) {
                    if (resDimNames instanceof String) {
                        dimNamesArray[ind++] = (String) resDimNames;
                    } else {
                        RStringVector names = (RStringVector) resDimNames;
                        assert names.getLength() == resDim;
                        for (int i = 0; i < names.getLength(); i++) {
                            dimNamesArray[ind++] = names.getDataAt(i);
                        }
                    }
                    return ind;
                }
            }
            for (int i = 0; i < resDim; i++) {
                dimNamesArray[ind++] = RRuntime.NAMES_ATTR_EMPTY_VALUE;
            }
            return -ind;
        } else {
            if (!GetDimAttributeNode.isArray(rawDimensions) || rawDimensions.length == 1) {
                if (argNames == null) {
                    if (deparseLevel == 0) {
                        dimNamesArray[ind++] = RRuntime.NAMES_ATTR_EMPTY_VALUE;
                        return -ind;
                    } else {
                        String deparsedName = deparseArgName(promiseArgs, deparseLevel, vecInd);
                        dimNamesArray[ind++] = deparsedName;
                        return Utils.identityEquals(deparsedName, RRuntime.NAMES_ATTR_EMPTY_VALUE) ? -ind : ind;
                    }
                } else {
                    if (argNames[vecInd] == null) {
                        dimNamesArray[ind++] = RRuntime.NAMES_ATTR_EMPTY_VALUE;
                        return -ind;
                    } else {
                        dimNamesArray[ind++] = argNames[vecInd];
                        return ind;
                    }
                }
            } else {
                for (int i = 0; i < resDim; i++) {
                    dimNamesArray[ind++] = RRuntime.NAMES_ATTR_EMPTY_VALUE;
                }
                return -ind;
            }
        }
    }

    /**
     * @param vectors vectors to be combined
     * @param res (output) dimensions of the resulting matrix.
     * @param bindDims (output, size == vectors.length) for each vector gives how many columns
     *            (cbind) it will fill in the resulting matrix, i.e. 1 for vectors and columns count
     *            for matrices.
     * @return whether some vectors will have to be recycled, i.e. iterated more than once when
     *         filling the result
     */
    protected boolean getResultDimensions(RAbstractVector[] vectors, int[] res, int[] bindDims) {
        /*
         * From the documentation: if there are several matrix arguments, they must all have the
         * same number of columns (or rows) and this will be the number of columns (or rows) of the
         * result. If all the arguments are vectors, the number of columns (rows) in the result is
         * equal to the length of the longest vector. Values in shorter arguments are recycled to
         * achieve this length (with a warning if they are recycled only fractionally)
         *
         * When the arguments consist of a mix of matrices and vectors the number of columns (rows)
         * of the result is determined by the number of columns (rows) of the matrix arguments. Any
         * vectors have their values recycled or subsetted to achieve this length.
         */
        assert vectors.length > 0;
        // NOTE: naming is chosen for cbind version, but it applies to rbind too
        int rowsCountMatrix = -1; // the number of rows of the first matrix argument if any
        int rowsCountVector = 0;  // the max length of simple vectors
        int minRowsCountVector = 0;  // the min length of simple vectors, to determine return value
        int[] rowsCountsVectors = new int[vectors.length];  // rows count of each vector/matrix
        int columnsCount = 0; // the total number of columns (cbind) in the resulting matrix
        int srcDim1Ind = type == BindType.cbind ? 0 : 1;
        int srcDim2Ind = type == BindType.cbind ? 1 : 0;
        for (int i = 0; i < vectors.length; i++) {
            RAbstractVector v = vectorProfile.profile(vectors[i]);
            int[] dims = getVectorDimensions(v);
            if (dims == null || dims.length != 2) {
                int vectorLen = v.getLength();
                rowsCountsVectors[i] = vectorLen;
                rowsCountVector = Math.max(rowsCountVector, vectorLen);
                minRowsCountVector = Math.min(minRowsCountVector, vectorLen);
                columnsCount++;
                bindDims[i] = 1;
                continue;
            }
            columnsCount += dims[srcDim2Ind];
            bindDims[i] = dims[srcDim2Ind];
            if (rowsCountMatrix == -1) {
                rowsCountMatrix = dims[srcDim1Ind];

            } else if (rowsCountMatrix != dims[srcDim1Ind]) {
                error(type == BindType.cbind ? Message.ROWS_MUST_MATCH : Message.COLS_MUST_MATCH, i + 1);
            }
        }
        res[srcDim2Ind] = columnsCount;
        int resultRowsCount = res[srcDim1Ind] = rowsCountMatrix != -1 ? rowsCountMatrix : rowsCountVector;
        for (int i = 0; i < vectors.length; i++) {
            if (rowsCountsVectors[i] != 0 && rowsCountsVectors[i] > resultRowsCount) {
                warning(type == BindType.cbind ? Message.ROWS_NOT_MULTIPLE : Message.COLUMNS_NOT_MULTIPLE, i + 1);
                break; // reported only for first ocurrence
            }
        }
        return minRowsCountVector < resultRowsCount;
    }

    protected int[] getDimensions(RAbstractVector vector, int[] dimensions) {
        if (dimensions == null || dimensions.length != 2) {
            return type == BindType.cbind ? new int[]{vector.getLength(), 1} : new int[]{1, vector.getLength()};
        } else {
            assert dimensions.length == 2;
            return dimensions;
        }
    }

    @TruffleBoundary
    protected static String deparseArgName(RArgsValuesAndNames promiseArgs, int deparseLevel, int argInd) {
        assert promiseArgs.getLength() >= argInd;
        Object argValue = promiseArgs.getArgument(argInd);
        if (argValue instanceof RPromise) {
            RPromise p = (RPromise) argValue;
            Object node = RASTUtils.createLanguageElement(p.getRep().asRSyntaxNode());
            if (deparseLevel == 1 && node instanceof RSymbol) {
                return ((RSymbol) node).toString();
            } // else - TODO handle deparseLevel > 1
        }
        // else - TODO handle non-promise arg (particularly a problem with the bind function
        // execuded via do.call

        return RRuntime.NAMES_ATTR_EMPTY_VALUE;
    }

    private final BranchProfile everSeenNotEqualRows = BranchProfile.create();
    private final BranchProfile everSeenNotEqualColumns = BranchProfile.create();
    private final ConditionProfile needsDimNames = ConditionProfile.createBinaryProfile();

    @Specialization(guards = {"precedence != NO_PRECEDENCE", "args.length == 1"})
    protected Object allOneElem(int deparseLevel, Object[] args, RArgsValuesAndNames promiseArgs, @SuppressWarnings("unused") int precedence,
                    @Cached() ExtractNamesAttributeNode extractNamesNode,
                    @Cached() SetDimAttributeNode setDims) {

        RAbstractVector vec = vectorProfile.profile(castVector(args[0]));
        int[] rawDimensions = null;
        rawDimensions = getVectorDimensions(vec);
        if (GetDimAttributeNode.isMatrix(rawDimensions)) {
            return vec;
        }

        // for cbind dimNamesA is names for the 1st dim and dimNamesB is names for 2nd dim; for
        // rbind the other way around
        Object dimNamesA = extractNamesNode.execute(vec);
        if (dimNamesA == null) {
            dimNamesA = RNull.instance;
        }
        Object dimNamesB;

        ArgumentsSignature signature = promiseArgs.getSignature();
        if (signature.getNonNullCount() == 0) {
            if (deparseLevel == 0) {
                dimNamesB = RNull.instance;
            } else {
                // var arg is at the first position - as in the R bind call
                String deparsedName = deparseArgName(promiseArgs, deparseLevel, 0);
                dimNamesB = Utils.identityEquals(deparsedName, RRuntime.NAMES_ATTR_EMPTY_VALUE) ? RNull.instance : RDataFactory.createStringVector(deparsedName);
            }
        } else {
            String[] names = new String[signature.getLength()];
            for (int i = 0; i < names.length; i++) {
                names[i] = signature.getName(i);
            }
            dimNamesB = RDataFactory.createStringVector(names, RDataFactory.COMPLETE_VECTOR);
        }

        int[] dims = getDimensions(vec, rawDimensions);
        RAbstractVector res = vec.copyDropAttributes();
        setDims.setDimensions(res, dims);
        if (needsDimNames.profile(vec.getLength() == 0 || dimNamesA != RNull.instance || dimNamesB != RNull.instance)) {
            setDimNames(res, RDataFactory.createList(type == BindType.cbind ? new Object[]{dimNamesA, dimNamesB} : new Object[]{dimNamesB, dimNamesA}));
        }
        return res;
    }

    public RAbstractVector genericCBind(RArgsValuesAndNames promiseArgs, RAbstractVector[] vectors, RAbstractVector result, int[] resultDimensions, int[] secondDims, boolean rowsAndColumnsNotEqual,
                    boolean allEmpty, String[] vecNames, int deparseLevel, SetDimAttributeNode setDimNode,
                    GetDimNamesAttributeNode getDimNamesNode, ExtractNamesAttributeNode extractNamesNode) {

        int ind = 0;
        Object rowDimResultNames = RNull.instance;
        String[] colDimNamesArray = new String[resultDimensions[1]];
        int colInd = 0;
        boolean allColDimNamesNull = true;
        for (int i = 0; i < vectors.length; i++) {
            RAbstractVector vec = vectorProfile.profile(vectors[i]);
            if (rowDimResultNames == RNull.instance) {
                // get the first valid names value
                rowDimResultNames = getDimResultNamesFromElements(vec, resultDimensions[0], 0, getDimNamesNode, extractNamesNode);
            }

            // compute dimnames for the second dimension
            int newColInd = getDimResultNamesFromVectors(promiseArgs, vec, vecNames, secondDims[i], colInd, i, deparseLevel, colDimNamesArray, 1, getDimNamesNode);
            if (newColInd < 0) {
                colInd = -newColInd;
            } else {
                allColDimNamesNull = false;
                colInd = newColInd;
            }

            // compute result vector values
            int[] dims = getDimensions(vec, getVectorDimensions(vec));
            assert dims.length == 2;
            for (int col = 0; col < dims[1]; col++) {
                int rowsCount = Math.min(dims[0], resultDimensions[0]);
                for (int row = 0; row < rowsCount; row++) {
                    result.transferElementSameType(ind++, vec, dims[0] * col + row);
                }
                if (rowsAndColumnsNotEqual) {
                    everSeenNotEqualRows.enter();
                    if (rowsCount < resultDimensions[0]) {
                        // re-use vector elements
                        int k = 0;
                        for (int j = 0; j < resultDimensions[0] - dims[0]; j++, k = Utils.incMod(k, dims[0])) {
                            result.transferElementSameType(ind++, vectors[i], dims[0] * col + k);
                        }
                        if (k != 0) {
                            RError.warning(this, RError.Message.ROWS_NOT_MULTIPLE, i + 1);
                        }
                    }
                }
            }
        }
        Object colDimResultNames = allColDimNamesNull ? RNull.instance : RDataFactory.createStringVector(colDimNamesArray, dimResultNamesComplete(colDimNamesArray));
        setDimNode.setDimensions(result, resultDimensions);
        if (needsDimNames.profile(allEmpty || rowDimResultNames != RNull.instance || colDimResultNames != RNull.instance)) {
            setDimNames(result, RDataFactory.createList(new Object[]{rowDimResultNames, colDimResultNames}));
        }
        return result;
    }

    @RBuiltin(name = "cbind", kind = INTERNAL, parameterNames = {"deparse.level", "..."}, behavior = COMPLEX)
    public abstract static class CbindInternal extends AbstractBind {
        public CbindInternal() {
            super(BindType.cbind);
        }

        static {
            createCasts(CbindInternal.class);
        }
    }

    @RBuiltin(name = "rbind", kind = INTERNAL, parameterNames = {"deparse.level", "..."}, behavior = COMPLEX)
    public abstract static class RbindInternal extends AbstractBind {
        public RbindInternal() {
            super(BindType.rbind);
        }

        static {
            createCasts(RbindInternal.class);
        }
    }

    protected abstract static class AbstractBind extends RBuiltinNode.Arg2 {
        @Child private ClassHierarchyNode classHierarchy = ClassHierarchyNodeGen.create(false, false);
        private final ConditionProfile hasClassProfile = ConditionProfile.createBinaryProfile();
        private final ConditionProfile hasDispatchFunction = ConditionProfile.createBinaryProfile();

        @Child private Bind bind;
        @Child private RExplicitCallNode dispatchCallNode;
        @Child private PrecedenceNode precedenceNode;

        @Child private S3FunctionLookupNode lookup;
        @Child private GetBaseEnvFrameNode getBaseEnv;

        private final BindType type;

        public AbstractBind(BindType type) {
            this.type = type;
        }

        protected static Casts createCasts(Class<? extends AbstractBind> extCls) {
            Casts casts = new Casts(extCls);
            casts.arg("deparse.level").asIntegerVector().findFirst(0);
            return casts;
        }

        @Specialization
        protected Object bind(VirtualFrame frame, int deparseLevel, RArgsValuesAndNames args) {
            RFunction dispatchFunction = createDispatchFunction(frame, args.getArguments(), deparseLevel);
            if (hasDispatchFunction.profile(dispatchFunction != null)) {
                if (dispatchCallNode == null) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    dispatchCallNode = insert(RExplicitCallNode.create());
                }
                return dispatchCallNode.call(frame, dispatchFunction, (RArgsValuesAndNames) RArguments.getArgument(frame, 0));
            } else {
                if (bind == null) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    bind = insert(BindNodeGen.create(type));
                }
                Object firstArg = RArguments.getArgument(frame, 0);
                RArgsValuesAndNames promisedArgs = args;
                if (firstArg instanceof RArgsValuesAndNames) {
                    promisedArgs = (RArgsValuesAndNames) firstArg;
                }
                return bind.execute(frame, deparseLevel, args.getArguments(), promisedArgs, precedence(args.getArguments()));
            }
        }

        protected int precedence(Object[] args) {
            int precedence = -1;
            if (precedenceNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                precedenceNode = insert(PrecedenceNodeGen.create());
            }
            for (int i = 0; i < args.length; i++) {
                precedence = Math.max(precedence, precedenceNode.executeInteger(args[i], false));
            }
            return precedence;
        }

        private RFunction createDispatchFunction(VirtualFrame frame, Object[] args, int deparseLevel) {

            // S4 dispatch is only considered if deparseLevel is non-negative
            boolean tryS4 = deparseLevel >= 0;

            // indicating if any of the arguments is an S4 object
            boolean anyS4 = false;

            Result result = null;
            for (Object arg : args) {
                if (tryS4 && !anyS4 && RRuntime.isS4Object(arg)) {
                    anyS4 = true;
                }

                RStringVector clazz = classHierarchy.execute(arg);
                if (hasClassProfile.profile(clazz != null)) {
                    if (lookup == null) {
                        CompilerDirectives.transferToInterpreterAndInvalidate();
                        lookup = insert(S3FunctionLookupNode.create(false, false));
                    }
                    if (getBaseEnv == null) {
                        CompilerDirectives.transferToInterpreterAndInvalidate();
                        getBaseEnv = insert(GetBaseEnvFrameNode.create());
                    }
                    Result r = lookup.execute(frame, type.toString(), clazz, null, frame.materialize(), getBaseEnv.execute());
                    if (r != null) {
                        if (result == null) {
                            result = r;
                        }
                    }
                }
            }

            RFunction dispatchFunction = result != null ? result.function : null;

            // TODO class compatibility: record if S3 classes were compatible, if not, do S4
            // dispatch
            // In this case, call function 'methods::cbind' or 'methods::rbind'.
            if (dispatchFunction == null && anyS4) {
                REnvironment methodsEnv = REnvironment.getRegisteredNamespace(getRContext(), "methods");
                dispatchFunction = ReadVariableNode.lookupFunction(type.name(), methodsEnv.getFrame(), true, true);
            }
            return dispatchFunction;
        }
    }

    public RAbstractVector genericRBind(RArgsValuesAndNames promiseArgs, RAbstractVector[] vectors, RAbstractVector result, int[] resultDimensions, int[] firstDims, boolean rowsAndColumnsNotEqual,
                    boolean allEmpty, String[] vecNames, int deparseLevel, SetDimAttributeNode setDimNode,
                    GetDimNamesAttributeNode getDimNamesNode, ExtractNamesAttributeNode extractNamesNode) {

        Object colDimResultNames = RNull.instance;
        String[] rowDimNamesArray = new String[resultDimensions[0]];
        int rowInd = 0;
        boolean allRowDimNamesNull = true;
        int dstRowInd = 0;
        for (int i = 0; i < vectors.length; i++) {
            if (firstDims[i] == 0) {
                continue;
            }
            RAbstractVector vec = vectorProfile.profile(vectors[i]);
            if (colDimResultNames == RNull.instance) {
                // get the first valid names value
                colDimResultNames = getDimResultNamesFromElements(vec, resultDimensions[1], 1, getDimNamesNode, extractNamesNode);
            }

            // compute dimnames for the second dimension
            int newRowInd = getDimResultNamesFromVectors(promiseArgs, vec, vecNames, firstDims[i], rowInd, i, deparseLevel, rowDimNamesArray, 0, getDimNamesNode);
            if (newRowInd < 0) {
                rowInd = -newRowInd;
            } else {
                allRowDimNamesNull = false;
                rowInd = newRowInd;
            }

            // compute result vector values
            int vecLength = vec.getLength();
            int srcInd = 0;
            int j = 0;
            for (; j < vecLength / firstDims[i]; j++) {
                for (int k = dstRowInd; k < dstRowInd + firstDims[i]; k++) {
                    result.transferElementSameType(j * resultDimensions[0] + k, vec, srcInd++);
                }
            }
            if (rowsAndColumnsNotEqual) {
                everSeenNotEqualColumns.enter();
                if (j < resultDimensions[1]) {
                    // re-use vector elements
                    int k = 0;
                    for (; j < resultDimensions[1]; j++, k = Utils.incMod(k, vecLength % resultDimensions[1])) {
                        result.transferElementSameType(j * resultDimensions[0] + (dstRowInd + firstDims[i] - 1), vectors[i], k);
                    }

                    if (k != 0) {
                        RError.warning(this, RError.Message.COLUMNS_NOT_MULTIPLE, i + 1);
                    }
                }
            }
            dstRowInd += firstDims[i];

        }
        Object rowDimResultNames = allRowDimNamesNull ? RNull.instance : RDataFactory.createStringVector(rowDimNamesArray, dimResultNamesComplete(rowDimNamesArray));
        setDimNode.setDimensions(result, resultDimensions);
        if (needsDimNames.profile(allEmpty || rowDimResultNames != RNull.instance || colDimResultNames != RNull.instance)) {
            setDimNames(result, RDataFactory.createList(new Object[]{rowDimResultNames, colDimResultNames}));
        }
        return result;
    }

    private boolean dimResultNamesComplete(String[] rowDimNamesArray) {
        for (String s : rowDimNamesArray) {
            if (dimNamesInComplete.profile(RRuntime.isNA(s))) {
                return RDataFactory.INCOMPLETE_VECTOR;
            }
        }
        return RDataFactory.COMPLETE_VECTOR;
    }

    private void setDimNames(RAbstractVector result, RList dimNames) {
        if (setDimNamesNode == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            setDimNamesNode = insert(SetDimNamesAttributeNode.create());
        }
        setDimNamesNode.setDimNames(result, dimNames);
    }
}
