/*
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.r.test.packages.analyzer.detectors;

import java.io.IOException;
import java.util.Collection;
import java.util.LinkedList;

import com.oracle.truffle.r.test.packages.analyzer.LineIterator;
import com.oracle.truffle.r.test.packages.analyzer.FileLineReader;
import com.oracle.truffle.r.test.packages.analyzer.Location;
import com.oracle.truffle.r.test.packages.analyzer.Problem;
import com.oracle.truffle.r.test.packages.analyzer.model.RPackageTestRun;

public class RInternalErrorDetector extends LineDetector {

    public static final RInternalErrorDetector INSTANCE = new RInternalErrorDetector();

    private static final String P = "com.oracle.truffle.r.runtime.RInternalError: ";

    protected RInternalErrorDetector() {
    }

    @Override
    public String getName() {
        return null;
    }

    @Override
    public Collection<Problem> detect(RPackageTestRun pkg, Location startLocation, FileLineReader body) {
        Collection<Problem> problems = new LinkedList<>();
        int lineNr = startLocation != null ? startLocation.lineNr : 0;
        try (LineIterator it = body.iterator()) {
            while (it.hasNext()) {
                String line = it.next();
                int indexOf = line.indexOf(P);
                if (indexOf != -1) {
                    String message = line.substring(indexOf + P.length());
                    problems.add(new RInternalErrorProblem(pkg, this, new Location(startLocation.file, lineNr), message));
                }
                ++lineNr;
            }
        } catch (IOException e) {
            // ignore
        }
        return problems;
    }

    public static class RInternalErrorProblem extends Problem {

        private static final int MAX_DISTANCE = 10;
        private final String message;

        protected RInternalErrorProblem(RPackageTestRun pkg, RInternalErrorDetector detector, Location location, String message) {
            super(pkg, detector, location);
            this.message = message;
        }

        public String getMessage() {
            return message;
        }

        @Override
        public String toString() {
            return getLocation() + ": RInternalError: " + message;
        }

        @Override
        public String getSummary() {
            return "RInternalError";
        }

        @Override
        public String getDetails() {
            return message;
        }

        @Override
        public int getSimilarityTo(Problem other) {
            if (other.getClass() == RInternalErrorProblem.class) {
                return Problem.computeLevenshteinDistanceFast(getDetails().trim(), other.getDetails().trim(), MAX_DISTANCE);
            }
            return Integer.MAX_VALUE;
        }

        @Override
        public boolean isSimilarTo(Problem other) {
            return getSimilarityTo(other) < MAX_DISTANCE;
        }

    }

}
