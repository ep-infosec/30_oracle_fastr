/*
 * Copyright (c) 2013, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.r.nodes.casts;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.r.runtime.RRuntime;
import com.oracle.truffle.r.runtime.RType;
import com.oracle.truffle.r.runtime.data.RComplex;
import com.oracle.truffle.r.runtime.data.RDataFactory;
import com.oracle.truffle.r.runtime.data.RDoubleVector;
import com.oracle.truffle.r.runtime.data.RMissing;
import com.oracle.truffle.r.runtime.data.RNull;
import com.oracle.truffle.r.runtime.data.RRaw;
import com.oracle.truffle.r.runtime.data.RTypes;
import com.oracle.truffle.r.runtime.data.RComplexVector;
import com.oracle.truffle.r.runtime.data.model.RAbstractContainer;
import com.oracle.truffle.r.runtime.data.RIntVector;
import com.oracle.truffle.r.runtime.data.model.RAbstractListBaseVector;
import com.oracle.truffle.r.runtime.data.RLogicalVector;
import com.oracle.truffle.r.runtime.data.RRawVector;
import com.oracle.truffle.r.runtime.data.RStringVector;
import com.oracle.truffle.r.runtime.data.model.RAbstractVector;

public class CastUtils {

    public static final class Cast {

        public enum Coverage {
            potential {
                @Override
                public Coverage transpose(Type sourceType, Type targetType, boolean sourcePositive, boolean targetPositive) {
                    if (sourcePositive && targetPositive) {
                        return potential;
                    }
                    if (!sourcePositive && targetPositive) {
                        return potential;
                    }
                    if (sourcePositive && !targetPositive) {
                        return potential;
                    }
                    return potential;
                }

                @Override
                public Coverage or(Coverage other) {
                    switch (other) {
                        case full:
                            return full;
                        case partial:
                            return partial;
                        case potential:
                            return potential;
                        case none:
                        default:
                            return potential;
                    }
                }

                @Override
                public Coverage and(Coverage other) {
                    switch (other) {
                        case full:
                            return potential;
                        case partial:
                            return potential;
                        case potential:
                            return potential;
                        case none:
                        default:
                            return none;
                    }
                }
            },
            partial {
                @Override
                public Coverage transpose(Type sourceType, Type targetType, boolean sourcePositive, boolean targetPositive) {
                    if (sourceType == targetType) {
                        return sourcePositive == targetPositive ? full : none;
                    }

                    if (sourcePositive && targetPositive) {
                        return partial;
                    }
                    if (!sourcePositive && targetPositive) {
                        return none;
                    }
                    if (sourcePositive && !targetPositive) {
                        return partial;
                    }
                    return sourceType == Object.class ? none : full;
                }

                @Override
                public Coverage or(Coverage other) {
                    switch (other) {
                        case full:
                            return full;
                        case partial:
                            return partial;
                        case potential:
                            return partial;
                        case none:
                        default:
                            return partial;
                    }
                }

                @Override
                public Coverage and(Coverage other) {
                    switch (other) {
                        case full:
                            return partial;
                        case partial:
                            return partial;
                        case potential:
                            return potential;
                        case none:
                        default:
                            return none;
                    }
                }
            },
            none {
                @Override
                public Coverage transpose(Type sourceType, Type targetType, boolean sourcePositive, boolean targetPositive) {
                    if (sourcePositive && targetPositive) {
                        return none;
                    }
                    if (!sourcePositive && targetPositive) {
                        return partial;
                    }
                    if (sourcePositive && !targetPositive) {
                        return full;
                    }
                    return partial;
                }

                @Override
                public Coverage or(Coverage other) {
                    switch (other) {
                        case full:
                            return full;
                        case partial:
                            return partial;
                        case potential:
                            return potential;
                        case none:
                        default:
                            return none;
                    }
                }

                @Override
                public Coverage and(Coverage other) {
                    switch (other) {
                        case full:
                            return none;
                        case partial:
                            return none;
                        case potential:
                            return none;
                        case none:
                        default:
                            return none;
                    }
                }
            },
            full {
                @Override
                public Coverage transpose(Type sourceType, Type targetType, boolean sourcePositive, boolean targetPositive) {
                    if (sourceType == targetType) {
                        if (sourceType == Object.class) {
                            return sourcePositive && targetPositive ? full : none;
                        } else {
                            return sourcePositive == targetPositive ? full : none;
                        }
                    }

                    if (sourcePositive && targetPositive) {
                        return full;
                    }
                    if (!sourcePositive && targetPositive) {
                        return targetType == Object.class ? full : partial;
                    }
                    if (sourcePositive && !targetPositive) {
                        return none;
                    }
                    return targetType == Object.class ? none : partial;
                }

                @Override
                public Coverage or(Coverage other) {
                    switch (other) {
                        case full:
                            return full;
                        case partial:
                            return full;
                        case potential:
                            return full;
                        case none:
                        default:
                            return full;
                    }
                }

                @Override
                public Coverage and(Coverage other) {
                    switch (other) {
                        case full:
                            return full;
                        case partial:
                            return partial;
                        case potential:
                            return potential;
                        case none:
                        default:
                            return none;
                    }
                }
            };

            /**
             * It transforms this type coverage into another one that would be returned in the
             * situation when the source and the target type were either positive or negative, as
             * determined by the <code>sourcePositive</code> and <code>targetPositive</code>
             * arguments.
             * <p>
             * N.B. It is assumed that this coverage is obtained for the positive source anb target
             * types.
             */
            public abstract Coverage transpose(Type sourceType, Type targetType, boolean sourcePositive, boolean targetPositive);

            public abstract Coverage or(Coverage other);

            public abstract Coverage and(Coverage other);
        }

        private final Type it;
        private final Type rt;
        private final Coverage coverage;

        Cast(Type it, Type rt, Coverage coverage) {
            this.it = it;
            this.rt = rt;
            this.coverage = coverage;
        }

        public Cast(Type it, Type rt) {
            this(it, rt, Coverage.full);
        }

        public Type inputType() {
            return it;
        }

        public Type resultType() {
            return rt;
        }

        public Coverage coverage() {
            return coverage;
        }
    }

    public static class Casts {
        private final List<Cast> cs;
        private static final Casts implicitCasts = createImplicitCasts();

        Casts(List<Cast> cs) {
            this.cs = cs;
        }

        public List<Cast> casts() {
            return cs;
        }

        public Set<Type> inputTypes() {
            return cs.stream().map(c -> c.inputType()).collect(Collectors.toSet());
        }

        public Set<Type> resultTypes() {
            return cs.stream().map(c -> c.resultType()).collect(Collectors.toSet());
        }

        public TypeExpr narrow(TypeExpr actualInputTypes) {
            if (actualInputTypes.isNothing()) {
                return cs.stream().map((Cast c) -> {
                    return TypeExpr.atom(c.resultType());
                }).reduce((res, te) -> res.or(te)).orElse(TypeExpr.NOTHING);
            } else {
                Casts positiveCasts = new Casts(cs.stream().filter(c -> existsConvertibleActualType(actualInputTypes, c.inputType(), true)).collect(Collectors.toList()));
                TypeExpr positive = positiveCasts.casts().stream().map(c -> TypeExpr.atom(c.resultType())).reduce((res, te) -> res.or(te)).orElse(TypeExpr.NOTHING);
                Casts negativeCasts = new Casts(
                                cs.stream().filter(c -> !existsConvertibleActualType(actualInputTypes, c.inputType(), true) &&
                                                // Make sure that the negativeCasts set contains no
                                                // cast whose return type is
                                                // convertible to the return type of any cast from
                                                // the positiveCasts set
                                                positiveCasts.resultTypes().stream().allMatch(posResTp -> isConvertible(c.resultType(), posResTp, false) == Cast.Coverage.none)).collect(
                                                                Collectors.toList()));
                TypeExpr negative = negativeCasts.casts().stream().map(c -> TypeExpr.atom(c.resultType()).not()).reduce((res, te) -> res.and(te)).orElse(TypeExpr.ANYTHING);

                return positive.and(negative);
            }
        }

        public Casts findCasts(Type fromType, Type toType, boolean includeImplicits) {
            return new Casts(cs.stream().filter(
                            c -> isConvertible(fromType, c.inputType(), includeImplicits) == Cast.Coverage.full &&
                                            isConvertible(c.resultType(), toType, includeImplicits) == Cast.Coverage.full).collect(Collectors.toList()));
        }

        public static boolean existsConvertibleActualType(TypeExpr actualInputTypes, Type formalInputCls, boolean includeImplicits) {
            Set<Cast> convTypes = findConvertibleActualType(actualInputTypes, formalInputCls, includeImplicits);
            return !convTypes.isEmpty();
        }

        public static Set<Cast> findConvertibleActualType(TypeExpr actualInputTypes, Type formalInputCls, boolean includeImplicits) {
            Set<Type> normActTypes = actualInputTypes.toNormalizedConjunctionSet();
            return normActTypes.stream().map(actualInputCls -> new Cast(actualInputCls, formalInputCls, isConvertible(actualInputCls, formalInputCls, includeImplicits))).filter(
                            c -> c.coverage != Cast.Coverage.none).collect(Collectors.toSet());
        }

        public static Cast.Coverage isConvertible(Type actualInputType, Type formalInputType, boolean includeImplicits) {
            UpperBoundsConjunction from = UpperBoundsConjunction.fromType(actualInputType);
            UpperBoundsConjunction to = UpperBoundsConjunction.fromType(formalInputType);

            return to.coverageFrom(from, includeImplicits);
        }

        public static boolean hasImplicitCast(Type actualInputCls, Type formalInputCls) {
            return !implicitCasts.findCasts(actualInputCls, formalInputCls, false).casts().isEmpty();
        }

        public static Type boxType(Type t) {
            if (t == byte.class) {
                return Byte.class;
            }
            if (t == int.class) {
                return Integer.class;
            }
            if (t == long.class) {
                return Long.class;
            }
            if (t == float.class) {
                return Float.class;
            }
            if (t == double.class) {
                return Double.class;
            }
            if (t == boolean.class) {
                return Boolean.class;
            }
            if (t == char.class) {
                return Character.class;
            }
            return t;
        }

        public static Casts createCastNodeCasts(Class<?> castNodeClass) {
            return createCastsFromMethods(castNodeClass, Specialization.class, Fallback.class);
        }

        public static Casts createImplicitCasts() {
            List<Cast> castList = Arrays.asList(RTypes.class.getMethods()).stream().filter(dm -> {
                return dm.getParameterTypes().length == 1 && dm.getParameterTypes()[0] != Object.class;
            }).map(m -> new Cast(m.getParameterTypes()[0], m.getReturnType(), Cast.Coverage.full)).collect(Collectors.toList());

            return new Casts(castList);
        }

        public static Casts createCasts(List<Cast> cs) {
            return new Casts(cs);
        }

        @SafeVarargs
        public static Casts createCastsFromMethods(Class<?> clazz, Class<? extends Annotation>... annotClasses) {
            List<Method> specs = getAnnotatedMethods(clazz, annotClasses);
            return new Casts(specs.stream().map(s -> new Cast(s.getParameterTypes()[0], s.getReturnType(), Cast.Coverage.full)).filter(c -> c.rt != Object.class).collect(Collectors.toList()));
        }

        public static Casts createPowerCasts(Set<Class<?>> set1, Set<Class<?>> set2) {
            List<Cast> powerCasts = new ArrayList<>();
            for (Class<?> c1 : set1) {
                for (Class<?> c2 : set2) {
                    powerCasts.add(new Cast(c1, c2, isConvertible(c1, c2, true)));
                }
            }
            return new Casts(powerCasts);
        }

        public static TypeExpr inputsAsTypeExpr(Set<Cast> casts) {
            return casts.stream().map(c -> TypeExpr.atom(c.inputType())).reduce((res, t) -> t.or(res)).orElse(TypeExpr.NOTHING);
        }
    }

    public static Set<List<Type>> argumentProductSet(List<TypeExpr> argTypeSets) {
        if (argTypeSets.isEmpty()) {
            return Collections.emptySet();
        } else if (argTypeSets.size() == 1) {
            return argTypeSets.get(0).toNormalizedConjunctionSet().stream().map((Type t) -> Collections.singletonList(t)).collect(Collectors.toSet());
        } else {
            Set<List<Type>> tailPowerSet = argumentProductSet(argTypeSets.subList(1, argTypeSets.size()));
            TypeExpr headArgSet = argTypeSets.get(0);
            Set<List<Type>> resultSet = new HashSet<>();
            for (Type headType : headArgSet.toNormalizedConjunctionSet()) {
                Set<LinkedList<Type>> extSublists = tailPowerSet.stream().map(x -> {
                    LinkedList<Type> extSublist = new LinkedList<>(x);
                    extSublist.addFirst(headType);
                    return extSublist;
                }).collect(Collectors.toSet());
                resultSet.addAll(extSublists);
            }

            return resultSet;
        }
    }

    @SafeVarargs
    public static List<Method> getAnnotatedMethods(Class<?> clazz, Class<? extends Annotation>... annotClasses) {
        List<Method> annotMethList = new ArrayList<>();
        for (Class<? extends Annotation> annotClass : annotClasses) {
            annotMethList.addAll(getMethodsAnnotatedBy(clazz, annotClass));
        }
        return annotMethList;
    }

    private static List<Method> getMethodsAnnotatedBy(Class<?> clazz, Class<? extends Annotation> annotClass) {
        List<Method> annotMethList = new ArrayList<>(Arrays.asList(clazz.getDeclaredMethods()).stream().filter(dm -> {
            return dm.getAnnotation(annotClass) != null;
        }).collect(Collectors.toList()));

        if (clazz.getSuperclass() != Object.class) {
            annotMethList.addAll(getMethodsAnnotatedBy(clazz.getSuperclass(), annotClass));
        }

        return annotMethList;
    }

    public static Type elementType(Class<?> vectorType) {
        if (RIntVector.class.isAssignableFrom(vectorType) || Integer.class.isAssignableFrom(vectorType) || int.class.isAssignableFrom(vectorType)) {
            return Integer.class;
        }
        if (RDoubleVector.class.isAssignableFrom(vectorType) || Double.class.isAssignableFrom(vectorType) || double.class.isAssignableFrom(vectorType)) {
            return Double.class;
        }
        if (RLogicalVector.class.isAssignableFrom(vectorType) || Byte.class.isAssignableFrom(vectorType) || byte.class.isAssignableFrom(vectorType)) {
            return Byte.class;
        }
        if (RStringVector.class.isAssignableFrom(vectorType) || String.class.isAssignableFrom(vectorType)) {
            return String.class;
        }
        if (RComplexVector.class.isAssignableFrom(vectorType) || RComplex.class.isAssignableFrom(vectorType)) {
            return RComplex.class;
        }
        if (RRawVector.class.isAssignableFrom(vectorType)) {
            return RRaw.class;
        }
        if (RAbstractListBaseVector.class.isAssignableFrom(vectorType)) {
            return Object.class;
        }
        if (RAbstractVector.class.isAssignableFrom(vectorType)) {
            return Object.class;
        }
        return vectorType;
    }

    public static RAbstractVector emptyVector(Class<?> elementType) {
        if (Integer.class.isAssignableFrom(elementType)) {
            return RDataFactory.createEmptyIntVector();
        }
        if (Double.class.isAssignableFrom(elementType)) {
            return RDataFactory.createEmptyDoubleVector();
        }
        if (Byte.class.isAssignableFrom(elementType)) {
            return RDataFactory.createEmptyLogicalVector();
        }
        if (Boolean.class.isAssignableFrom(elementType)) {
            return RDataFactory.createEmptyLogicalVector();
        }
        if (String.class.isAssignableFrom(elementType)) {
            return RDataFactory.createEmptyStringVector();
        }
        if (RComplex.class.isAssignableFrom(elementType)) {
            return RDataFactory.createEmptyComplexVector();
        }
        return null;
    }

    public static RAbstractVector vectorOfSize(Class<?> vectorType, int size) {
        if (RIntVector.class.isAssignableFrom(vectorType) || Integer.class.isAssignableFrom(vectorType) || int.class.isAssignableFrom(vectorType)) {
            return RDataFactory.createIntVector(size);
        }
        if (RDoubleVector.class.isAssignableFrom(vectorType) || Double.class.isAssignableFrom(vectorType) || double.class.isAssignableFrom(vectorType)) {
            return RDataFactory.createDoubleVector(size);
        }
        if (RLogicalVector.class.isAssignableFrom(vectorType) || Byte.class.isAssignableFrom(vectorType) || byte.class.isAssignableFrom(vectorType)) {
            return RDataFactory.createLogicalVector(size);
        }
        if (RStringVector.class.isAssignableFrom(vectorType) || String.class.isAssignableFrom(vectorType)) {
            return RDataFactory.createStringVector(size);
        }
        if (RComplexVector.class.isAssignableFrom(vectorType) || RComplex.class.isAssignableFrom(vectorType)) {
            return RDataFactory.createComplexVector(size);
        }
        if (RAbstractVector.class.isAssignableFrom(vectorType) || vectorType == Object.class) {
            return RDataFactory.createIntVector(size);
        }
        return null;
    }

    public static RAbstractVector vectorOfSize(RType vectorType, int size) {
        switch (vectorType) {
            case Integer:
                return RDataFactory.createIntVector(size);
            case Double:
                return RDataFactory.createDoubleVector(size);
            case Logical:
                return RDataFactory.createLogicalVector(size);
            case Character:
                return RDataFactory.createStringVector(size);
            case Complex:
                return RDataFactory.createComplexVector(size);
            case Any:
                return RDataFactory.createIntVector(size);
            default:
                return null;
        }
    }

    public static Class<?>[] rTypeToClasses(RType type) {
        switch (type) {
            case Integer:
                return new Class<?>[]{Integer.class, RIntVector.class};
            case Double:
                return new Class<?>[]{Double.class, RDoubleVector.class};
            case Logical:
                return new Class<?>[]{Byte.class, RLogicalVector.class};
            case Character:
                return new Class<?>[]{String.class, RStringVector.class};
            case Complex:
                return new Class<?>[]{RComplexVector.class};
            case Raw:
                return new Class<?>[]{RRawVector.class};
            case Any:
                return new Class<?>[]{Object.class};
        }
        return null;
    }

    public static RAbstractVector fillVector(RType vectorType, int size, Object value, boolean complete) {
        switch (vectorType) {
            case Integer:
                int[] iarray = new int[size];
                Arrays.fill(iarray, (int) value);
                return RDataFactory.createIntVector(iarray, complete);
            case Double:
                double[] darray = new double[size];
                Arrays.fill(darray, (double) value);
                return RDataFactory.createDoubleVector(darray, complete);
            case Logical:
                byte[] larray = new byte[size];
                Arrays.fill(larray, (byte) value);
                return RDataFactory.createLogicalVector(larray, complete);
            case Character:
                String[] sarray = new String[size];
                Arrays.fill(sarray, value);
                return RDataFactory.createStringVector(sarray, complete);
            case Complex:
                double[] carray = new double[2 * size];
                RComplex c = (RComplex) value;
                for (int i = 0; i < size; i++) {
                    carray[2 * i] = c.getRealPart();
                    carray[2 * i + 1] = c.getImaginaryPart();
                }
                return RDataFactory.createComplexVector(carray, complete);
            case Any:
                Object[] oarray = new Object[size];
                Arrays.fill(oarray, value);
                return RDataFactory.createList(oarray);
            default:
                return null;
        }
    }

    public static Object naVector(Class<?> elementType) {
        if (Integer.class.isAssignableFrom(elementType)) {
            return RDataFactory.createIntVectorFromScalar(RRuntime.INT_NA);
        }
        if (Double.class.isAssignableFrom(elementType)) {
            return RDataFactory.createDoubleVectorFromScalar(RRuntime.DOUBLE_NA);
        }
        if (Byte.class.isAssignableFrom(elementType)) {
            return RDataFactory.createLogicalVectorFromScalar(RRuntime.LOGICAL_NA);
        }
        if (Boolean.class.isAssignableFrom(elementType)) {
            return RDataFactory.createLogicalVectorFromScalar(RRuntime.LOGICAL_NA);
        }
        if (String.class.isAssignableFrom(elementType)) {
            return RDataFactory.createStringVectorFromScalar(RRuntime.STRING_NA);
        }
        if (RComplex.class.isAssignableFrom(elementType)) {
            return RDataFactory.createComplexVectorFromScalar(RRuntime.COMPLEX_NA);
        }
        return null;
    }

    public static Object naValue(Class<?> elementType) {
        if (Integer.class.isAssignableFrom(elementType)) {
            return RRuntime.INT_NA;
        }
        if (Double.class.isAssignableFrom(elementType)) {
            return RRuntime.DOUBLE_NA;
        }
        if (Byte.class.isAssignableFrom(elementType)) {
            return RRuntime.LOGICAL_NA;
        }
        if (Boolean.class.isAssignableFrom(elementType)) {
            return null;
        }
        if (String.class.isAssignableFrom(elementType)) {
            return RRuntime.STRING_NA;
        }
        if (RComplex.class.isAssignableFrom(elementType)) {
            return RRuntime.COMPLEX_NA;
        }
        return null;
    }

    public static boolean isNaValue(Object x) {
        if (x instanceof Integer) {
            return RRuntime.isNA((Integer) x);
        }
        if (x instanceof Double) {
            return RRuntime.isNA((Double) x);
        }
        if (x instanceof Byte) {
            return RRuntime.isNA((Byte) x);
        }
        if (x instanceof String) {
            return RRuntime.isNA((String) x);
        }
        if (x instanceof RComplex) {
            return RRuntime.isNA((RComplex) x);
        }
        return false;
    }

    public static Object singletonVector(Object element) {
        if (element == RNull.instance) {
            return RNull.instance;
        }
        if (element instanceof Integer) {
            return RDataFactory.createIntVectorFromScalar((Integer) element);
        }
        if (element instanceof Double) {
            return RDataFactory.createDoubleVectorFromScalar((Double) element);
        }
        if (element instanceof Byte) {
            return RDataFactory.createLogicalVectorFromScalar((Byte) element);
        }
        if (element instanceof Boolean) {
            return RDataFactory.createLogicalVectorFromScalar((Boolean) element);
        }
        if (element instanceof String) {
            return RDataFactory.createStringVectorFromScalar((String) element);
        }
        if (element instanceof RComplex) {
            return RDataFactory.createComplexVectorFromScalar((RComplex) element);
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    public static <T> Optional<T> firstElement(Object vectorOrScalar, Object defaultValue) {
        if (vectorOrScalar == RNull.instance) {
            return defaultValue == null ? Optional.of((T) RNull.instance) : Optional.of((T) defaultValue);
        }
        if (vectorOrScalar == RMissing.instance) {
            return defaultValue == null ? Optional.of((T) RMissing.instance) : Optional.of((T) defaultValue);
        }
        if (vectorOrScalar instanceof RAbstractContainer) {
            if (((RAbstractContainer) vectorOrScalar).getLength() == 0) {
                return defaultValue == null ? Optional.empty() : Optional.of((T) defaultValue);
            } else {
                if (vectorOrScalar instanceof RIntVector) {
                    return Optional.of((T) ((RIntVector) vectorOrScalar).getDataAtAsObject(0));
                }
                if (vectorOrScalar instanceof RDoubleVector) {
                    return Optional.of((T) ((RDoubleVector) vectorOrScalar).getDataAtAsObject(0));
                }
                if (vectorOrScalar instanceof RComplexVector) {
                    return Optional.of((T) ((RComplexVector) vectorOrScalar).getDataAtAsObject(0));
                }
                if (vectorOrScalar instanceof RLogicalVector) {
                    return Optional.of((T) ((RLogicalVector) vectorOrScalar).getDataAtAsObject(0));
                }
                if (vectorOrScalar instanceof RStringVector) {
                    return Optional.of((T) ((RStringVector) vectorOrScalar).getDataAtAsObject(0));
                }
                return defaultValue == null ? Optional.empty() : Optional.of((T) defaultValue);
            }
        } else {
            return Optional.of((T) vectorOrScalar);
        }
    }

    public static Optional<Class<?>> vectorElementType(Object vector) {
        if (vector instanceof RAbstractContainer) {
            if (vector instanceof RIntVector) {
                return Optional.of(Integer.class);
            }
            if (vector instanceof RDoubleVector) {
                return Optional.of(Double.class);
            }
            if (vector instanceof RComplexVector) {
                return Optional.of(RComplex.class);
            }
            if (vector instanceof RLogicalVector) {
                return Optional.of(Byte.class);
            }
            if (vector instanceof RStringVector) {
                return Optional.of(String.class);
            }
            return Optional.of(Object.class);
        } else {
            return Optional.empty();
        }
    }

    public static Set<?> sampleValuesForTypeExpr(TypeExpr te) {
        return te.toNormalizedConjunctionSet().stream().flatMap(t -> CastUtils.sampleValuesForType(t).stream()).collect(Collectors.toSet());
    }

    public static Set<?> sampleValuesForClasses(Class<?>[] classes) {
        return Arrays.stream(classes).flatMap(t -> CastUtils.sampleValuesForType(t).stream()).collect(Collectors.toSet());
    }

    public static Set<?> sampleValuesForClass(Class<?> cls) {
        return sampleValuesForClasses(new Class<?>[]{cls});
    }

    public static Set<?> sampleValuesForType(Type t) {
        HashSet<Object> samples = new HashSet<>();

        if (!(t instanceof Class)) {
            // todo:
            return samples;
        }

        Class<?> cls = (Class<?>) t;

        samples.add(naValue(cls));

        if (cls == Object.class || Byte.class.isAssignableFrom(cls)) {
            samples.add(RRuntime.LOGICAL_TRUE);
            samples.add(RRuntime.LOGICAL_FALSE);
        }

        if (cls == Object.class || RLogicalVector.class.isAssignableFrom(cls)) {
            samples.add(RDataFactory.createLogicalVectorFromScalar(RRuntime.LOGICAL_TRUE));
            samples.add(RDataFactory.createLogicalVectorFromScalar(RRuntime.LOGICAL_FALSE));
            samples.add(RDataFactory.createLogicalVector(new byte[]{RRuntime.LOGICAL_FALSE, RRuntime.LOGICAL_TRUE}, true));
            samples.add(RDataFactory.createLogicalVector(new byte[]{RRuntime.LOGICAL_FALSE, RRuntime.LOGICAL_TRUE, RRuntime.LOGICAL_NA}, false));
        }

        if (cls == Object.class || Boolean.class.isAssignableFrom(cls)) {
            samples.add(Boolean.TRUE);
            samples.add(Boolean.FALSE);
        }

        if (cls == Object.class || Integer.class.isAssignableFrom(cls)) {
            samples.add(0);
            samples.add(1);
            samples.add(-1);
        }

        if (cls == Object.class || RIntVector.class.isAssignableFrom(cls)) {
            samples.add(RDataFactory.createIntVectorFromScalar(0));
            samples.add(RDataFactory.createIntVectorFromScalar(1));
            samples.add(RDataFactory.createIntVectorFromScalar(-1));
            samples.add(RDataFactory.createIntVector(new int[]{-1, 0, 1}, true));
            samples.add(RDataFactory.createIntVector(new int[]{-1, 0, 1, RRuntime.INT_NA}, false));
            samples.add(RDataFactory.createIntVector(new int[]{RRuntime.INT_NA}, false));
        }

        if (cls == Object.class || Double.class.isAssignableFrom(cls)) {
            samples.add(0d);
            samples.add(Math.PI);
            samples.add(-Math.PI);
        }

        if (cls == Object.class || RDoubleVector.class.isAssignableFrom(cls)) {
            samples.add(RDataFactory.createDoubleVectorFromScalar(0));
            samples.add(RDataFactory.createDoubleVectorFromScalar(1));
            samples.add(RDataFactory.createDoubleVectorFromScalar(-1));
            samples.add(RDataFactory.createDoubleVector(new double[]{-1, 0, 1}, true));
            samples.add(RDataFactory.createDoubleVector(new double[]{-Math.PI, 0, Math.PI,
                            RRuntime.DOUBLE_NA}, false));
            samples.add(RDataFactory.createDoubleVector(new double[]{RRuntime.DOUBLE_NA}, false));
        }

        if (cls == Object.class || RComplex.class.isAssignableFrom(cls)) {
            samples.add(RComplex.valueOf(0, 0));
            samples.add(RComplex.valueOf(Math.PI, Math.PI));
            samples.add(RComplex.valueOf(-Math.PI, -Math.PI));
        }

        if (cls == Object.class || RComplexVector.class.isAssignableFrom(cls)) {
            samples.add(RRuntime.COMPLEX_NA);
            samples.add(RComplex.valueOf(0, 0));
            samples.add(RDataFactory.createComplexVectorFromScalar(RComplex.valueOf(0, 0)));
            samples.add(RDataFactory.createComplexVectorFromScalar(RComplex.valueOf(1, 1)));
            samples.add(RDataFactory.createComplexVectorFromScalar(RComplex.valueOf(-1, 1)));
            samples.add(RDataFactory.createComplexVector(new double[]{-Math.PI, 0, Math.PI,
                            RRuntime.DOUBLE_NA, -Math.PI, 0, Math.PI, RRuntime.DOUBLE_NA}, false));
            samples.add(RDataFactory.createComplexVector(new double[]{-Math.PI, 0, Math.PI,
                            RRuntime.DOUBLE_NA, -Math.PI, 0, Math.PI, RRuntime.DOUBLE_NA}, false));
            samples.add(RDataFactory.createComplexVector(new double[]{RRuntime.DOUBLE_NA, RRuntime.DOUBLE_NA}, false));
        }

        if (cls == Object.class || String.class.isAssignableFrom(cls)) {
            samples.add("");
            samples.add("abc");
            samples.add(RRuntime.STRING_NA);
        }

        if (cls == Object.class || RStringVector.class.isAssignableFrom(cls)) {
            samples.add(RDataFactory.createStringVectorFromScalar(""));
            samples.add(RDataFactory.createStringVectorFromScalar("abc"));
            samples.add(RDataFactory.createStringVector(new String[]{"", "abc"}, true));
            samples.add(RDataFactory.createStringVector(new String[]{"", "abc", RRuntime.STRING_NA}, false));
            samples.add(RDataFactory.createStringVector(new String[]{RRuntime.STRING_NA}, false));
        }

        if (RRawVector.class.isAssignableFrom(cls)) {
            samples.add((byte) 0);
            samples.add(RDataFactory.createRawVector(new byte[]{0}));
        }

        samples.remove(null);

        return samples;
    }

    public static Set<Object> sampleValuesForClassesOtherThan(Class<?> cls) {
        return CastUtils.sampledClasses.stream().filter(c -> c != cls).flatMap(c -> sampleValuesForType(c).stream()).collect(Collectors.toSet());
    }

    public static final Set<Class<?>> sampledClasses = Collections.unmodifiableSet(samples(Integer.class, Double.class, Byte.class, RComplex.class, String.class));

    public static Set<Object> pseudoValuesForClass(Class<?> cls) {
        HashSet<Object> samples = new HashSet<>();
        samples.add(RNull.instance);
        samples.add(emptyVector(cls));
        samples.add(naValue(cls));
        samples.add(naVector(cls));

        samples.remove(null);

        return samples;
    }

    @SuppressWarnings("varargs")
    @SafeVarargs
    public static <T> Set<T> samples(T samplesHead, T... samplesTail) {
        HashSet<T> sampleSet = new HashSet<>(Arrays.asList(samplesTail));
        sampleSet.add(samplesHead);
        return sampleSet;
    }

    public static <T> Set<T> samples(T s) {
        if (s == null) {
            return Collections.emptySet();
        }
        return Collections.singleton(s);
    }

    public static <T> Set<T> samples() {
        return Collections.emptySet();
    }

    public static String getPredefStepDesc() {
        StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
        return stackTrace[3].toString();
    }

    private static int instrIndent = 0;

    static <T> Predicate<T> instrument(Predicate<T> predicate, String desc) {
        if (Boolean.getBoolean("rbdiag.instrument")) {
            return x -> {
                char[] indentChars = new char[instrIndent];
                Arrays.fill(indentChars, ' ');
                String indent = String.valueOf(indentChars);

                System.out.println(indent + "{ " + desc + ": " + x);
                instrIndent++;
                boolean res;
                try {
                    res = predicate.test(x);
                } finally {
                    instrIndent--;
                }
                System.out.println(indent + "} " + desc + ": " + x + "->" + res);
                return res;
            };
        } else {
            return predicate;
        }
    }
}
