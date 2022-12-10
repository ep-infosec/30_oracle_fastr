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
import java.util.Collections;

import com.oracle.truffle.r.test.packages.analyzer.LineIterator;
import com.oracle.truffle.r.test.packages.analyzer.FileLineReader;
import com.oracle.truffle.r.test.packages.analyzer.Location;
import com.oracle.truffle.r.test.packages.analyzer.Problem;
import com.oracle.truffle.r.test.packages.analyzer.model.RPackageTestRun;

public class SegfaultDetector extends LineDetector {
    public static final SegfaultDetector INSTANCE = new SegfaultDetector();

    private static final String SIGSEGV_START = "# A fatal error has been detected by the Java Runtime Environment:";

    protected SegfaultDetector() {
    }

    @Override
    public String getName() {
        return "Segfault detector";
    }

    @Override
    public Collection<Problem> detect(RPackageTestRun pkg, Location startLocation, FileLineReader body) {
        StringBuilder segfaultMessage = new StringBuilder();
        boolean collect = false;
        assert body.isEmpty() || startLocation != null;
        int lineNr = startLocation != null ? startLocation.lineNr : 0;
        boolean takeNextLine = false;
        try (LineIterator it = body.iterator()) {
            while (it.hasNext()) {
                String line = it.next();
                if (line.contains(SIGSEGV_START)) {
                    collect = true;
                }
                if (collect) {
                    if (takeNextLine) {
                        segfaultMessage.append(line);
                        takeNextLine = false;
                    }
                    if (line.contains("Problematic frame")) {
                        takeNextLine = true;
                    } else if (!line.contains("#")) {
                        break;
                    }
                }
                ++lineNr;
            }
        } catch (IOException e) {
            // ignore
        }
        if (collect) {
            return Collections.singleton(new SegfaultProblem(pkg, this, new Location(startLocation.file, lineNr), segfaultMessage.toString()));
        }
        return Collections.emptyList();
    }

    public static class SegfaultProblem extends Problem {

        private static final int MAX_DISTANCE = 100;
        private final String message;

        protected SegfaultProblem(RPackageTestRun pkg, SegfaultDetector detector, Location location, String message) {
            super(pkg, detector, location);
            this.message = message;
        }

        @Override
        public String toString() {
            return getLocation() + ": SIGSEGV in " + getPackage();
        }

        @Override
        public String getSummary() {
            return ": SIGSEGV in " + getPackage();
        }

        @Override
        public String getDetails() {
            return message;
        }

        @Override
        public int getSimilarityTo(Problem other) {
            if (other.getClass() == SegfaultProblem.class) {
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
