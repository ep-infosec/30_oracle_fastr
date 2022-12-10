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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.oracle.truffle.r.test.packages.analyzer.LineIterator;
import com.oracle.truffle.r.test.packages.analyzer.FileLineReader;
import com.oracle.truffle.r.test.packages.analyzer.Location;
import com.oracle.truffle.r.test.packages.analyzer.Problem;
import com.oracle.truffle.r.test.packages.analyzer.model.RPackageTestRun;

public class RErrorDetector extends LineDetector {

    public static final RErrorDetector INSTANCE = new RErrorDetector();

    private static final Pattern PATTERN = Pattern.compile("(.*\\s)?Error( in (?<CALLSTR>[^:]*(\\(.*\\))?) )?: (?<MSG>.*)");

    protected RErrorDetector() {
    }

    @Override
    public String getName() {
        return "R error detector";
    }

    @Override
    public Collection<Problem> detect(RPackageTestRun pkg, Location startLocation, FileLineReader body) {

        Collection<Problem> problems = new LinkedList<>();
        assert body.isEmpty() || startLocation != null;
        int lineOffset = startLocation != null ? startLocation.lineNr : 0;

        int i = -1;
        try (LineIterator it = body.iterator()) {
            while (it.hasNext()) {
                String line = it.next();
                ++i;
                Matcher matcher = PATTERN.matcher(line);
                if (matcher.matches()) {
                    String callString = matcher.group("CALLSTR");
                    String message = matcher.group("MSG");
                    if (message.trim().isEmpty() && it.hasNext()) {
                        // message could be in the next line
                        message = it.next();
                        ++i;
                    }
                    problems.add(new RErrorProblem(pkg, this, new Location(startLocation.file, i + lineOffset), callString, message));
                }
            }
        } catch (IOException e) {
            // ignore
        }

        return problems;
    }

    public static class RErrorProblem extends Problem {

        private static final int MAX_DISTANCE = 10;
        private final String callString;
        private final String message;

        protected RErrorProblem(RPackageTestRun pkg, RErrorDetector detector, Location location, String callString, String message) {
            super(pkg, detector, location);
            this.callString = callString;
            this.message = message;
        }

        @Override
        public String toString() {
            return String.format("%s: RError in '%s' (msg = '%s'), args)", getLocation(), getPackage().getName(), message);
        }

        @Override
        public String getSummary() {
            if (callString != null) {
                return "Error in " + callString + "";
            }
            return "Error";
        }

        @Override
        public String getDetails() {
            return message;
        }

        @Override
        public int getSimilarityTo(Problem other) {
            if (other.getClass() == RErrorProblem.class) {
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
