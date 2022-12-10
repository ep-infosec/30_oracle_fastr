/*
 * Copyright (c) 2013, 2018, Oracle and/or its affiliates. All rights reserved.
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

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public final class Samples<T> {

    private static final Samples<?> ANYTHING = new Samples<>("anything", Collections.emptySet(), Collections.emptySet(), x -> true);
    private static final Samples<?> NOTHING = new Samples<>("nothing", Collections.emptySet(), Collections.emptySet(), x -> false);

    @SuppressWarnings("unchecked")
    public static <T> Samples<T> anything() {
        return (Samples<T>) ANYTHING;
    }

    public static <T> Samples<T> anything(T x) {
        return new Samples<>("anything(" + x + ")", Collections.singleton(x), Collections.emptySet(), xx -> true);
    }

    @SuppressWarnings("unchecked")
    public static <T> Samples<T> nothing() {
        return (Samples<T>) NOTHING;
    }

    private final Set<Object> posSamples;
    private final Set<Object> negSamples;
    private final Predicate<Object> posMembership;
    private final Predicate<Object> negMembership;
    private final String name;

    public Samples(String name, Set<Object> positiveSamples, Set<Object> negativeSamples, Predicate<Object> posMembership) {
        this.name = name;
        this.posSamples = positiveSamples;
        this.negSamples = negativeSamples;
        this.posMembership = CastUtils.instrument(posMembership, name);
        this.negMembership = CastUtils.instrument(this.posMembership.negate(), "neg(" + name + ")");
    }

    private Samples(String name, Set<Object> positiveSamples, Set<Object> negativeSamples, Predicate<Object> posMembership, Predicate<Object> negMembership) {
        this.name = name;
        this.posSamples = positiveSamples;
        this.negSamples = negativeSamples;
        this.posMembership = CastUtils.instrument(posMembership, name);
        this.negMembership = CastUtils.instrument(negMembership, "neg(" + name + ")");
    }

    public Samples<T> addPositiveSamples(Object... sampleValues) {
        Set<Object> newPosSamples = new HashSet<>(Arrays.asList(sampleValues));
        newPosSamples.addAll(posSamples);
        return new Samples<>(name, newPosSamples, negSamples, posMembership, negMembership);
    }

    public Samples<T> addNegativeSamples(Object... sampleValues) {
        Set<Object> newNegSamples = new HashSet<>(Arrays.asList(sampleValues));
        newNegSamples.addAll(negSamples);
        return new Samples<>(name, posSamples, newNegSamples, posMembership, negMembership);
    }

    public Set<Object> positiveSamples() {
        return posSamples;
    }

    public Set<Object> negativeSamples() {
        return negSamples;
    }

    public Set<Object> allSamples() {
        HashSet<Object> all = new HashSet<>(posSamples);
        all.addAll(negSamples);
        return all;
    }

    public <R> Samples<R> map(Function<Object, R> posMapper, Function<Object, Object> negMapper, Function<Object, Optional<Object>> posUnmapper, Function<Object, Optional<Object>> negUnmapper) {
        Set<Object> mappedPositive = positiveSamples().stream().map(posMapper).collect(Collectors.toSet());
        Set<Object> mappedNegative = negativeSamples().stream().map(negMapper).collect(Collectors.toSet());
        return new Samples<>(name + ".map", mappedPositive, mappedNegative, x -> {
            Optional<Object> um = posUnmapper.apply(x);
            return um.isPresent() ? posMembership.test(um.get()) : false;
        }, x -> {
            Optional<Object> um = negUnmapper.apply(x);
            return um.isPresent() ? negMembership.test(um.get()) : false;
        });
    }

    public Samples<T> filter(Predicate<Object> newPosCondition) {
        return filter(newPosCondition, negMembership);
    }

    public Samples<T> filter(Predicate<Object> newPosCondition, Predicate<Object> newNegCondition) {
        Set<Object> newPositive = positiveSamples().stream().filter(newPosCondition).collect(Collectors.toSet());
        Set<Object> newNegative = negativeSamples().stream().filter(newNegCondition).collect(Collectors.toSet());
        return new Samples<>(name + ".filter", newPositive, newNegative, x -> posMembership.test(x) && newPosCondition.test(x),
                        x -> negMembership.test(x) && newNegCondition.test(x));
    }

    public Samples<T> and(Samples<?> other) {
        String newName = "and(" + name + "," + other.name + ")";

        Set<Object> negativeUnion = new HashSet<>(other.negativeSamples());
        negativeUnion.addAll(other.positiveSamples());
        negativeUnion.addAll(negativeSamples());
        negativeUnion.addAll(positiveSamples());
        Predicate<Object> newNegCondition = CastUtils.instrument(negMembership.or(other.negMembership), "and-neg");
        negativeUnion.removeIf(CastUtils.instrument(newNegCondition.negate(), "pruningNegUnion:" + newName));

        Set<Object> positiveUnion = new HashSet<>(other.positiveSamples());
        positiveUnion.addAll(other.negativeSamples());
        positiveUnion.addAll(positiveSamples());
        positiveUnion.addAll(negativeSamples());
        Predicate<Object> newPosCondition = CastUtils.instrument(posMembership.and(other.posMembership), "and-pos");
        positiveUnion.removeIf(CastUtils.instrument(newPosCondition.negate(), "pruningPosUnion:" + newName));

        return new Samples<>(newName, positiveUnion, negativeUnion, newPosCondition, newNegCondition);
    }

    public Samples<T> or(Samples<?> other) {
        String newName = "or(" + name + "," + other.name + ")";

        Set<Object> negativeUnion = new HashSet<>(other.negativeSamples());
        negativeUnion.addAll(other.positiveSamples());
        negativeUnion.addAll(negativeSamples());
        negativeUnion.addAll(positiveSamples());
        Predicate<Object> newNegCondition = CastUtils.instrument(negMembership.and(other.negMembership), "or-neg");
        negativeUnion.removeIf(CastUtils.instrument(newNegCondition.negate(), "pruningNegUnion:" + newName));

        Set<Object> positiveUnion = new HashSet<>(other.positiveSamples());
        positiveUnion.addAll(other.negativeSamples());
        positiveUnion.addAll(positiveSamples());
        positiveUnion.addAll(negativeSamples());
        Predicate<Object> newPosCondition = CastUtils.instrument(posMembership.or(other.posMembership), "or-neg");
        positiveUnion.removeIf(CastUtils.instrument(newPosCondition.negate(), "pruningPosUnion:" + newName));

        return new Samples<>(newName, positiveUnion, negativeUnion, newPosCondition, newNegCondition);
    }

    public Samples<Object> swap() {
        return new Samples<>(name + ".swap", negSamples, posSamples, negMembership, posMembership);
    }

    public Samples<Object> makePositive() {
        Set<Object> mergedSamples = new HashSet<>(positiveSamples());
        // Add negative samples to positive samples
        mergedSamples.addAll(negativeSamples());
        return new Samples<>(name + ".makePositive", mergedSamples, Collections.emptySet(), posMembership.or(negMembership));
    }

    public Samples<T> positiveOnly() {
        return new Samples<>(name + ".positiveOnly", posSamples, Collections.emptySet(), posMembership);
    }

    @Override
    public String toString() {
        // return posSamples.toString() + ":" + negSamples.toString();
        return "Positive:" + posSamples.stream().map(s -> s != null ? s + "(" + s.getClass() + ")" : "null").collect(Collectors.toList()).toString() + "\nNegative:" +
                        negSamples.stream().map(s -> s != null ? s + "(" + s.getClass() + ")" : "null").collect(Collectors.toList());
    }
}
