/*
 * Copyright (c) 2013, 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.r.nodes.builtin.casts;

import java.util.function.Supplier;

import com.oracle.truffle.api.profiles.ConditionProfile;
import com.oracle.truffle.r.nodes.binary.BoxPrimitiveNode;
import com.oracle.truffle.r.nodes.builtin.ArgumentFilter;
import com.oracle.truffle.r.nodes.builtin.ArgumentFilter.ArgumentTypeFilter;
import com.oracle.truffle.r.nodes.builtin.ArgumentMapper;
import com.oracle.truffle.r.nodes.builtin.ValuePredicateArgumentMapper;
import com.oracle.truffle.r.nodes.builtin.casts.Filter.AndFilter;
import com.oracle.truffle.r.nodes.builtin.casts.Filter.CompareFilter;
import com.oracle.truffle.r.nodes.builtin.casts.Filter.CompareFilter.Dim;
import com.oracle.truffle.r.nodes.builtin.casts.Filter.CompareFilter.ElementAt;
import com.oracle.truffle.r.nodes.builtin.casts.Filter.CompareFilter.NATest;
import com.oracle.truffle.r.nodes.builtin.casts.Filter.CompareFilter.ScalarValue;
import com.oracle.truffle.r.nodes.builtin.casts.Filter.CompareFilter.StringLength;
import com.oracle.truffle.r.nodes.builtin.casts.Filter.CompareFilter.VectorSize;
import com.oracle.truffle.r.nodes.builtin.casts.Filter.DoubleFilter;
import com.oracle.truffle.r.nodes.builtin.casts.Filter.FilterVisitor;
import com.oracle.truffle.r.nodes.builtin.casts.Filter.MatrixFilter;
import com.oracle.truffle.r.nodes.builtin.casts.Filter.MissingFilter;
import com.oracle.truffle.r.nodes.builtin.casts.Filter.NotFilter;
import com.oracle.truffle.r.nodes.builtin.casts.Filter.NullFilter;
import com.oracle.truffle.r.nodes.builtin.casts.Filter.OrFilter;
import com.oracle.truffle.r.nodes.builtin.casts.Filter.RTypeFilter;
import com.oracle.truffle.r.nodes.builtin.casts.Filter.RVarArgsFilter;
import com.oracle.truffle.r.nodes.builtin.casts.Filter.ResultForArg;
import com.oracle.truffle.r.nodes.builtin.casts.Filter.TypeFilter;
import com.oracle.truffle.r.nodes.builtin.casts.Mapper.MapByteToBoolean;
import com.oracle.truffle.r.nodes.builtin.casts.Mapper.MapDoubleToInt;
import com.oracle.truffle.r.nodes.builtin.casts.Mapper.MapToCharAt;
import com.oracle.truffle.r.nodes.builtin.casts.Mapper.MapToValue;
import com.oracle.truffle.r.nodes.builtin.casts.Mapper.MapperVisitor;
import com.oracle.truffle.r.nodes.builtin.casts.PipelineStep.AttributableCoercionStep;
import com.oracle.truffle.r.nodes.builtin.casts.PipelineStep.BoxPrimitiveStep;
import com.oracle.truffle.r.nodes.builtin.casts.PipelineStep.CoercionStep;
import com.oracle.truffle.r.nodes.builtin.casts.PipelineStep.FilterStep;
import com.oracle.truffle.r.nodes.builtin.casts.PipelineStep.FindFirstStep;
import com.oracle.truffle.r.nodes.builtin.casts.PipelineStep.MapIfStep;
import com.oracle.truffle.r.nodes.builtin.casts.PipelineStep.MapStep;
import com.oracle.truffle.r.nodes.builtin.casts.PipelineStep.NotNAStep;
import com.oracle.truffle.r.nodes.builtin.casts.PipelineStep.PipelineStepVisitor;
import com.oracle.truffle.r.nodes.builtin.casts.analysis.ForwardingAnalysisResult;
import com.oracle.truffle.r.nodes.unary.CastComplexNodeGen;
import com.oracle.truffle.r.nodes.unary.CastDoubleBaseNodeGen;
import com.oracle.truffle.r.nodes.unary.CastDoubleNodeGen;
import com.oracle.truffle.r.nodes.unary.CastIntegerBaseNodeGen;
import com.oracle.truffle.r.nodes.unary.CastIntegerNodeGen;
import com.oracle.truffle.r.nodes.unary.CastLogicalBaseNodeGen;
import com.oracle.truffle.r.nodes.unary.CastLogicalNodeGen;
import com.oracle.truffle.r.runtime.nodes.unary.CastNode;
import com.oracle.truffle.r.nodes.unary.CastRawNodeGen;
import com.oracle.truffle.r.nodes.unary.CastStringBaseNodeGen;
import com.oracle.truffle.r.nodes.unary.CastStringNodeGen;
import com.oracle.truffle.r.nodes.unary.CastToAttributableNodeGen;
import com.oracle.truffle.r.nodes.unary.ChainedCastNode;
import com.oracle.truffle.r.nodes.unary.ConditionalMapNode;
import com.oracle.truffle.r.nodes.unary.FilterNode;
import com.oracle.truffle.r.nodes.unary.FindFirstNodeGen;
import com.oracle.truffle.r.nodes.unary.MapNode;
import com.oracle.truffle.r.nodes.unary.NonNANodeGen;
import com.oracle.truffle.r.nodes.unary.RVarArgsFilterNode;
import com.oracle.truffle.r.runtime.MessageData;
import com.oracle.truffle.r.runtime.RError;
import com.oracle.truffle.r.runtime.RError.ErrorContext;
import com.oracle.truffle.r.runtime.RInternalError;
import com.oracle.truffle.r.runtime.RRuntime;
import com.oracle.truffle.r.runtime.RType;
import com.oracle.truffle.r.runtime.Utils;
import com.oracle.truffle.r.runtime.data.RComplex;
import com.oracle.truffle.r.runtime.data.RDoubleVector;
import com.oracle.truffle.r.runtime.data.RComplexVector;
import com.oracle.truffle.r.runtime.data.RIntVector;
import com.oracle.truffle.r.runtime.data.RLogicalVector;
import com.oracle.truffle.r.runtime.data.RRawVector;
import com.oracle.truffle.r.runtime.data.RStringVector;
import com.oracle.truffle.r.runtime.data.model.RAbstractVector;
import com.oracle.truffle.r.runtime.nodes.unary.CastToVectorNodeGen;
import com.oracle.truffle.r.runtime.ops.na.NACheck;

/**
 * Converts given pipeline into corresponding cast nodes chain.
 */
public final class PipelineToCastNode {

    private PipelineToCastNode() {
        // nop: static class
    }

    public static CastNode convert(PipelineConfig config, PipelineStep<?, ?> firstStepIn, ForwardingAnalysisResult fwdAnalysisResult) {
        if (firstStepIn == null) {
            return null;
        }

        // if the pipeline is only single return, we change it to map to avoid needing to catch
        // the PipelineReturnException, otherwise the exception is caught by ChainedCastNode
        boolean singleMapStep = firstStepIn.getNext() == null && firstStepIn instanceof MapIfStep;
        PipelineStep<?, ?> firstStep = singleMapStep ? ((MapIfStep<?, ?>) firstStepIn).withoutReturns() : firstStepIn;

        CastNode firstCastNode = config.getCastForeign() ? new CastForeignNode() : null;
        Supplier<CastNode> originalPipelineFactory = () -> convert(firstCastNode, firstStep,
                        new CastNodeFactory(config.getDefaultError(), config.getDefaultWarning(), config.getDefaultWarningContext(), config.getDefaultDefaultMessage()));

        if (!config.getValueForwarding()) {
            return originalPipelineFactory.get();
        }

        if (fwdAnalysisResult.isAnythingForwarded()) {
            return ValueForwardingNodeGen.create(fwdAnalysisResult, originalPipelineFactory);
        } else {
            return originalPipelineFactory.get();
        }
    }

    /**
     * Converts chain of pipeline steps to cast nodes.
     */
    private static CastNode convert(CastNode firstCastNode, PipelineStep<?, ?> firstStep, PipelineStepVisitor<CastNode> nodeFactory) {
        if (firstStep == null) {
            return null;
        }

        CastNode prevCastNode = firstCastNode;
        PipelineStep<?, ?> currCastStep = firstStep;
        while (currCastStep != null) {
            CastNode node = currCastStep.accept(nodeFactory, prevCastNode);
            if (node != null) {
                if (prevCastNode == null) {
                    prevCastNode = node;
                } else {
                    prevCastNode = new ChainedCastNode(prevCastNode, node, currCastStep.getNext() == null);
                }
            }

            currCastStep = currCastStep.getNext();
        }
        return prevCastNode;
    }

    private static final class CastNodeFactory implements PipelineStepVisitor<CastNode> {
        private boolean boxPrimitives = false;

        private final MessageData defaultError;
        private final MessageData defaultWarning;
        private final ErrorContext warningContext;

        CastNodeFactory(MessageData defaultError, MessageData defaultWarning, ErrorContext warningContext, MessageData defaultMessage) {
            assert defaultMessage != null : "defaultMessage is null";
            this.defaultError = MessageData.getFirstNonNull(defaultError, defaultMessage);
            this.defaultWarning = MessageData.getFirstNonNull(defaultWarning, defaultMessage);
            this.warningContext = warningContext;
        }

        @Override
        public CastNode visit(BoxPrimitiveStep<?> step, CastNode previous) {
            return BoxPrimitiveNode.create();
        }

        @Override
        public CastNode visit(FindFirstStep<?, ?> step, CastNode previous) {
            boxPrimitives = false;

            // See FindFirstStep documentation on how it should be interpreted
            if (step.getDefaultValue() == null) {
                MessageData msg = step.getError();
                if (msg == null) {
                    // Note: intentional direct use of defaultError
                    msg = defaultError != null ? defaultError : new MessageData(RError.Message.LENGTH_ZERO);
                }
                return FindFirstNodeGen.create(step.getElementClass(), msg, step.getDefaultValue());
            } else {
                MessageData warning = step.getError();
                if (warning == null) {
                    return FindFirstNodeGen.create(step.getElementClass(), step.getDefaultValue());
                } else {
                    return FindFirstNodeGen.create(step.getElementClass(), warning, step.getDefaultValue());
                }
            }
        }

        @Override
        @SuppressWarnings("unchecked")
        public CastNode visit(FilterStep<?, ?> step, CastNode previous) {
            if (step.getFilter() instanceof RVarArgsFilter) {
                return RVarArgsFilterNode.create();
            }
            ArgumentFilter<Object, Object> filter = (ArgumentFilter<Object, Object>) ArgumentFilterFactoryImpl.INSTANCE.createFilter(step.getFilter());
            MessageData msg = getDefaultIfNull(step.getMessage(), step.isWarning());
            return FilterNode.create(filter, step.isWarning(), msg, boxPrimitives, step.getFilter().resultForNull() == ResultForArg.TRUE,
                            step.getFilter().resultForMissing() == ResultForArg.TRUE);
        }

        @Override
        public CastNode visit(NotNAStep<?> step, CastNode previous) {
            if (step.getReplacement() == null) {
                MessageData msg = getDefaultIfNull(step.getMessage(), false);
                return NonNANodeGen.create(msg, step.getReplacement());
            } else {
                MessageData msg = step.getMessage();
                if (msg == null) {
                    return NonNANodeGen.create(null, step.getReplacement());
                } else {
                    return NonNANodeGen.create(msg, step.getReplacement());
                }
            }
        }

        @Override
        public CastNode visit(CoercionStep<?, ?> step, CastNode previous) {
            boxPrimitives = true;

            RType type = step.getType();
            switch (type) {
                case Integer:
                    return step.vectorCoercion ? CastIntegerNodeGen.create(step.preserveNames, step.preserveDimensions, step.preserveAttributes, false, step.useClosure, warningContext)
                                    : CastIntegerBaseNodeGen.create(step.preserveNames, step.preserveDimensions, step.preserveAttributes, false, step.useClosure, warningContext);
                case Double:
                    return step.vectorCoercion ? CastDoubleNodeGen.create(step.preserveNames, step.preserveDimensions, step.preserveAttributes, false, step.useClosure, warningContext)
                                    : CastDoubleBaseNodeGen.create(step.preserveNames, step.preserveDimensions, step.preserveAttributes, false, step.useClosure, warningContext);
                case Character:
                    return step.vectorCoercion ? CastStringNodeGen.create(step.preserveNames, step.preserveDimensions, step.preserveAttributes, false, step.useClosure, warningContext)
                                    : CastStringBaseNodeGen.create(step.preserveNames, step.preserveDimensions, step.preserveAttributes, false, step.useClosure, warningContext);
                case Logical:
                    return step.vectorCoercion ? CastLogicalNodeGen.create(step.preserveNames, step.preserveDimensions, step.preserveAttributes, false, step.useClosure, warningContext)
                                    : CastLogicalBaseNodeGen.create(step.preserveNames, step.preserveDimensions, step.preserveAttributes, false, step.useClosure, warningContext);
                case Complex:
                    return CastComplexNodeGen.create(step.preserveNames, step.preserveDimensions, step.preserveAttributes, step.useClosure, warningContext);
                case Raw:
                    return CastRawNodeGen.create(step.preserveNames, step.preserveDimensions, step.preserveAttributes, step.useClosure, step.useClosure, warningContext);
                case Any:
                    return CastToVectorNodeGen.create(step.preserveNonVector);
                default:
                    throw RInternalError.shouldNotReachHere(Utils.stringFormat("Unsupported type '%s' in AsVectorStep.", type));
            }
        }

        @Override
        public CastNode visit(AttributableCoercionStep<?> step, CastNode previous) {
            return CastToAttributableNodeGen.create(step.preserveNames, step.preserveDimensions, step.preserveAttributes);
        }

        @Override
        public CastNode visit(MapStep<?, ?> step, CastNode previous) {
            return MapNode.create(ArgumentMapperFactoryImpl.INSTANCE.createMapper(step.getMapper()));
        }

        @Override
        @SuppressWarnings("unchecked")
        public CastNode visit(MapIfStep<?, ?> step, CastNode previous) {
            assert !(step.getFilter() instanceof RVarArgsFilter) : "mapIf not yet implemented for RVarArgsFilter";
            ArgumentFilter<Object, Object> condition = (ArgumentFilter<Object, Object>) ArgumentFilterFactoryImpl.INSTANCE.createFilter(step.getFilter());
            CastNode trueCastNode = PipelineToCastNode.convert(null, step.getTrueBranch(), this);
            CastNode falseCastNode = PipelineToCastNode.convert(null, step.getFalseBranch(), this);
            return ConditionalMapNode.create(condition, trueCastNode, falseCastNode, ResultForArg.TRUE.equals(step.getFilter().resultForNull()),
                            ResultForArg.TRUE.equals(step.getFilter().resultForMissing()), step.isReturns());
        }

        private MessageData getDefaultIfNull(MessageData message, boolean isWarning) {
            return MessageData.getFirstNonNull(message, isWarning ? defaultWarning : defaultError);
        }
    }

    public interface ArgumentFilterFactory {
        ArgumentFilter<?, ?> createFilter(Filter<?, ?> filter);
    }

    public static final class ArgumentFilterFactoryImpl
                    implements ArgumentFilterFactory, FilterVisitor<ArgumentFilter<?, ?>>, MatrixFilter.OperationVisitor<ArgumentFilter<RAbstractVector, RAbstractVector>>,
                    DoubleFilter.OperationVisitor<ArgumentFilter<Double, Double>>, CompareFilter.SubjectVisitor<ArgumentFilter<?, ?>> {

        public static final ArgumentFilterFactoryImpl INSTANCE = new ArgumentFilterFactoryImpl();

        private ArgumentFilterFactoryImpl() {
            // singleton
        }

        @Override
        public ArgumentFilter<?, ?> createFilter(Filter<?, ?> filter) {
            return filter.accept(this, null);
        }

        @Override
        public ArgumentFilter<?, ?> visit(TypeFilter<?, ?> filter, ArgumentFilter<?, ?> previous) {
            return filter.getInstanceOfLambda();
        }

        @Override
        public ArgumentFilter<?, ?> visit(RTypeFilter<?> filter, ArgumentFilter<?, ?> previous) {
            switch (filter.getType()) {
                case Integer:
                    return new ArgumentFilter<Object, Object>() {
                        private final ConditionProfile profile = ConditionProfile.createBinaryProfile();

                        @Override
                        public boolean test(Object x) {
                            return profile.profile(x instanceof Integer) || x instanceof RIntVector;
                        }
                    };
                case Double:
                    return new ArgumentFilter<Object, Object>() {
                        private final ConditionProfile profile = ConditionProfile.createBinaryProfile();

                        @Override
                        public boolean test(Object x) {
                            return profile.profile(x instanceof Double) || x instanceof RDoubleVector;
                        }
                    };
                case Logical:
                    return new ArgumentFilter<Object, Object>() {
                        private final ConditionProfile profile = ConditionProfile.createBinaryProfile();

                        @Override
                        public boolean test(Object x) {
                            return profile.profile(x instanceof Byte) || x instanceof RLogicalVector;
                        }
                    };
                case Complex:
                    return x -> x instanceof RComplexVector;
                case Character:
                    return new ArgumentFilter<Object, Object>() {
                        private final ConditionProfile profile = ConditionProfile.createBinaryProfile();

                        @Override
                        public boolean test(Object x) {
                            return profile.profile(x instanceof String) || x instanceof RStringVector;
                        }
                    };
                case Raw:
                    return x -> x instanceof RRawVector;
                default:
                    throw RInternalError.unimplemented("type " + filter.getType());
            }
        }

        @Override
        public ArgumentFilter<?, ?> visit(CompareFilter<?> filter, ArgumentFilter<?, ?> previous) {
            return filter.getSubject().accept(this, filter.getOperation(), previous);
        }

        @SuppressWarnings({"rawtypes", "unchecked"})
        @Override
        public ArgumentFilter<?, ?> visit(AndFilter<?, ?> filter, ArgumentFilter<?, ?> previous) {
            ArgumentFilter leftFilter = filter.getLeft().accept(this, previous);
            ArgumentFilter rightFilter = filter.getRight().accept(this, previous);
            return new ArgumentTypeFilter<Object, Object>() {
                private final ConditionProfile profile = ConditionProfile.createBinaryProfile();

                @Override
                public boolean test(Object arg) {
                    if (profile.profile(leftFilter.test(arg))) {
                        return rightFilter.test(arg);
                    } else {
                        return false;
                    }
                }
            };
        }

        @SuppressWarnings({"rawtypes", "unchecked"})
        @Override
        public ArgumentFilter<?, ?> visit(OrFilter<?> filter, ArgumentFilter<?, ?> previous) {
            ArgumentFilter leftFilter = filter.getLeft().accept(this, previous);
            ArgumentFilter rightFilter = filter.getRight().accept(this, previous);
            return new ArgumentTypeFilter<Object, Object>() {
                private final ConditionProfile profile = ConditionProfile.createBinaryProfile();

                @Override
                public boolean test(Object arg) {
                    if (profile.profile(leftFilter.test(arg))) {
                        return true;
                    } else {
                        return rightFilter.test(arg);
                    }
                }
            };
        }

        @SuppressWarnings({"rawtypes", "unchecked"})
        @Override
        public ArgumentFilter<?, ?> visit(NotFilter<?> filter, ArgumentFilter<?, ?> previous) {
            ArgumentFilter toNegate = filter.getFilter().accept(this, previous);
            return (ArgumentFilter<Object, Object>) arg -> !toNegate.test(arg);
        }

        @Override
        public ArgumentFilter<?, ?> visit(NullFilter filter, ArgumentFilter<?, ?> previous) {
            return (ArgumentFilter<Object, Object>) arg -> false;
        }

        @Override
        public ArgumentFilter<?, ?> visit(MissingFilter filter, ArgumentFilter<?, ?> previous) {
            return (ArgumentFilter<Object, Object>) arg -> false;
        }

        @Override
        public ArgumentFilter<?, ?> visit(MatrixFilter<?> filter, ArgumentFilter<?, ?> previous) {
            return filter.acceptOperation(this, null);
        }

        @Override
        public ArgumentFilter<?, ?> visit(RVarArgsFilter filter, ArgumentFilter<?, ?> previous) {
            throw RInternalError.shouldNotReachHere("This filter should be handled separately as it has its own node.");
        }

        @Override
        public ArgumentFilter<?, ?> visit(DoubleFilter filter, ArgumentFilter<?, ?> previous) {
            return filter.acceptOperation(this, null);
        }

        @Override
        public ArgumentFilter<RAbstractVector, RAbstractVector> visitIsMatrix(ArgumentFilter<RAbstractVector, RAbstractVector> previous) {
            return RAbstractVector::isMatrix;
        }

        @Override
        public ArgumentFilter<RAbstractVector, RAbstractVector> visitIsSquareMatrix(ArgumentFilter<RAbstractVector, RAbstractVector> previous) {
            return x -> x.isMatrix() && x.getDimensions()[0] == x.getDimensions()[1];
        }

        @Override
        public ArgumentFilter<Double, Double> visitIsFinite(ArgumentFilter<Double, Double> previous) {
            return x -> !Double.isInfinite(x);
        }

        @Override
        public ArgumentFilter<Double, Double> visitIsFractional(ArgumentFilter<Double, Double> previous) {
            return x -> !RRuntime.isNAorNaN(x) && !Double.isInfinite(x) && x != Math.floor(x);
        }

        @Override
        public ArgumentFilter<?, ?> visit(ScalarValue scalarValue, byte operation, ArgumentFilter<?, ?> previous) {
            switch (operation) {
                case CompareFilter.EQ:
                    switch (scalarValue.type) {
                        case Character:
                            return (String arg) -> arg.equals(scalarValue.value);
                        case Integer:
                            return (Integer arg) -> arg == (int) scalarValue.value;
                        case Double:
                            return (Double arg) -> arg == (double) scalarValue.value;
                        case Logical:
                            return (Byte arg) -> arg == (byte) scalarValue.value;
                        case Any:
                            return arg -> (arg instanceof String && scalarValue.value instanceof String && ((String) arg).equals(scalarValue.value)) ||
                                            (arg instanceof Integer && scalarValue.value instanceof Integer && ((Integer) arg).intValue() == ((Integer) scalarValue.value).intValue()) ||
                                            (arg instanceof Double && scalarValue.value instanceof Double && ((Double) arg).doubleValue() == ((Double) scalarValue.value).doubleValue()) ||
                                            (arg instanceof Byte && scalarValue.value instanceof Byte && ((Byte) arg).byteValue() == ((Byte) scalarValue.value).byteValue());
                        default:
                            throw RInternalError.unimplemented("TODO: more types here ");
                    }
                case CompareFilter.GT:
                    switch (scalarValue.type) {
                        case Integer:
                            return (Integer arg) -> arg > (int) scalarValue.value;
                        case Double:
                            return (Double arg) -> arg > (double) scalarValue.value;
                        case Logical:
                            return (Byte arg) -> arg > (byte) scalarValue.value;
                        default:
                            throw RInternalError.unimplemented("TODO: more types here");
                    }
                case CompareFilter.LT:
                    switch (scalarValue.type) {
                        case Integer:
                            return (Integer arg) -> arg < (int) scalarValue.value;
                        case Double:
                            return (Double arg) -> arg < (double) scalarValue.value;
                        case Logical:
                            return (Byte arg) -> arg < (byte) scalarValue.value;
                        default:
                            throw RInternalError.unimplemented("TODO: more types here");
                    }
                case CompareFilter.GE:
                    switch (scalarValue.type) {
                        case Integer:
                            return (Integer arg) -> arg >= (int) scalarValue.value;
                        case Double:
                            return (Double arg) -> arg >= (double) scalarValue.value;
                        case Logical:
                            return (Byte arg) -> arg >= (byte) scalarValue.value;
                        default:
                            throw RInternalError.unimplemented("TODO: more types here");
                    }
                case CompareFilter.LE:
                    switch (scalarValue.type) {
                        case Integer:
                            return (Integer arg) -> arg <= (int) scalarValue.value;
                        case Double:
                            return (Double arg) -> arg <= (double) scalarValue.value;
                        case Logical:
                            return (Byte arg) -> arg <= (byte) scalarValue.value;
                        default:
                            throw RInternalError.unimplemented("TODO: more types here");
                    }
                case CompareFilter.STRING_EQ:
                    return arg -> ((String) scalarValue.value).equals(arg);

                default:
                    throw RInternalError.unimplemented("TODO: more operations here");
            }
        }

        @Override
        public ArgumentFilter<?, ?> visit(NATest naTest, byte operation, ArgumentFilter<?, ?> previous) {
            switch (operation) {
                case CompareFilter.EQ:
                    switch (naTest.type) {
                        case Integer:
                            return arg -> RRuntime.isNA((int) arg);
                        case Double:
                            return arg -> RRuntime.isNAorNaN((double) arg);
                        case Logical:
                            return arg -> RRuntime.isNA((byte) arg);
                        case Character:
                            return arg -> RRuntime.isNA((String) arg);
                        case Complex:
                            return arg -> RRuntime.isNA((RComplex) arg);
                        default:
                            throw RInternalError.unimplemented("TODO: more types here");
                    }
                default:
                    throw RInternalError.unimplemented("TODO: more operations here");
            }
        }

        @Override
        public ArgumentFilter<String, String> visit(StringLength stringLength, byte operation, ArgumentFilter<?, ?> previous) {
            switch (operation) {
                case CompareFilter.EQ:
                    return arg -> arg.length() == stringLength.length;

                case CompareFilter.GT:
                    return arg -> arg.length() > stringLength.length;

                case CompareFilter.LT:
                    return arg -> arg.length() < stringLength.length;

                case CompareFilter.GE:
                    return arg -> arg.length() >= stringLength.length;

                case CompareFilter.LE:
                    return arg -> arg.length() <= stringLength.length;

                default:
                    throw RInternalError.unimplemented("TODO: more operations here");
            }
        }

        @Override
        public ArgumentFilter<RAbstractVector, RAbstractVector> visit(VectorSize vectorSize, byte operation, ArgumentFilter<?, ?> previous) {
            switch (operation) {
                case CompareFilter.EQ:
                    return arg -> arg.getLength() == vectorSize.size;

                case CompareFilter.GT:
                    return arg -> arg.getLength() > vectorSize.size;

                case CompareFilter.LT:
                    return arg -> arg.getLength() < vectorSize.size;

                case CompareFilter.GE:
                    return arg -> arg.getLength() >= vectorSize.size;

                case CompareFilter.LE:
                    return arg -> arg.getLength() <= vectorSize.size;

                default:
                    throw RInternalError.unimplemented("TODO: more operations here");
            }
        }

        @Override
        public ArgumentFilter<RAbstractVector, RAbstractVector> visit(ElementAt elementAt, byte operation, ArgumentFilter<?, ?> previous) {
            switch (operation) {
                case CompareFilter.EQ:
                    switch (elementAt.type) {
                        case Integer:
                            return arg -> elementAt.index < arg.getLength() && (int) elementAt.value == (int) arg.getDataAtAsObject(elementAt.index);
                        case Double:
                            return arg -> elementAt.index < arg.getLength() && (double) elementAt.value == (double) arg.getDataAtAsObject(elementAt.index);
                        case Logical:
                            return arg -> elementAt.index < arg.getLength() && (byte) elementAt.value == (byte) arg.getDataAtAsObject(elementAt.index);
                        case Character:
                        case Complex:
                            return arg -> elementAt.index < arg.getLength() && elementAt.value.equals(arg.getDataAtAsObject(elementAt.index));
                        default:
                            throw RInternalError.unimplemented("TODO: more types here");
                    }
                default:
                    throw RInternalError.unimplemented("TODO: more operations here");
            }
        }

        @Override
        public ArgumentFilter<RAbstractVector, RAbstractVector> visit(Dim dim, byte operation, ArgumentFilter<?, ?> previous) {
            switch (operation) {
                case CompareFilter.EQ:
                    return v -> v.isMatrix() && v.getDimensions().length > dim.dimIndex && v.getDimensions()[dim.dimIndex] == dim.dimSize;
                case CompareFilter.GT:
                    return v -> v.isMatrix() && v.getDimensions().length > dim.dimIndex && v.getDimensions()[dim.dimIndex] > dim.dimSize;
                default:
                    throw RInternalError.unimplemented("TODO: more operations here");
            }
        }
    }

    public interface ArgumentMapperFactory {
        ArgumentMapper<?, ?> createMapper(Mapper<?, ?> mapper);
    }

    public static final class ArgumentMapperFactoryImpl implements ArgumentMapperFactory, MapperVisitor<ValuePredicateArgumentMapper<Object, Object>> {

        public static final ArgumentMapperFactoryImpl INSTANCE = new ArgumentMapperFactoryImpl();

        private ArgumentMapperFactoryImpl() {
            // singleton
        }

        @Override
        public ArgumentMapper<Object, Object> createMapper(Mapper<?, ?> mapper) {
            return mapper.accept(this, null);
        }

        @Override
        public ValuePredicateArgumentMapper<Object, Object> visit(MapToValue<?, ?> mapper, ValuePredicateArgumentMapper<Object, Object> previous) {
            final Object value = mapper.getValue();
            return ValuePredicateArgumentMapper.fromLambda(x -> value);
        }

        @Override
        public ValuePredicateArgumentMapper<Object, Object> visit(MapByteToBoolean mapper, ValuePredicateArgumentMapper<Object, Object> previous) {
            return ValuePredicateArgumentMapper.fromLambda(x -> RRuntime.fromLogical((Byte) x, mapper.naReplacement));
        }

        @Override
        public ValuePredicateArgumentMapper<Object, Object> visit(MapDoubleToInt mapper, ValuePredicateArgumentMapper<Object, Object> previous) {
            final NACheck naCheck = NACheck.create();
            return ValuePredicateArgumentMapper.fromLambda(x -> {
                double d = (Double) x;
                naCheck.enable(d);
                return naCheck.convertDoubleToInt(d);
            });
        }

        @Override
        public ValuePredicateArgumentMapper<Object, Object> visit(MapToCharAt mapper, ValuePredicateArgumentMapper<Object, Object> previous) {
            final int defaultValue = mapper.getDefaultValue();
            final int index = mapper.getIndex();
            return ValuePredicateArgumentMapper.fromLambda(x -> {
                String str = (String) x;
                if (x == null || str.isEmpty()) {
                    return defaultValue;
                } else {
                    if (RRuntime.isNA(str)) {
                        return RRuntime.INT_NA;
                    } else {
                        return (int) str.charAt(index);
                    }
                }
            });
        }
    }
}
