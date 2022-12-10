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
package com.oracle.truffle.r.launcher;

import java.io.PrintStream;
import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentLinkedDeque;

public final class StartupTiming {
    public static final boolean ENABLED = "true".equals(System.getProperty("StartupTiming"));

    private static volatile StartupTiming INSTANCE = null;

    private final long startTime;
    private final ConcurrentLinkedDeque<Timestamp> timestamps = new ConcurrentLinkedDeque<>();

    private StartupTiming() {
        RuntimeMXBean runtimeMXBean;
        long st = 0L;
        try {
            runtimeMXBean = ManagementFactory.getRuntimeMXBean();
            if (runtimeMXBean == null) {
                st = System.currentTimeMillis();
            } else {
                st = runtimeMXBean.getStartTime();
            }
        } catch (Throwable t) {
            st = System.currentTimeMillis();
        }
        startTime = st;
    }

    private static synchronized void init() {
        if (INSTANCE == null) {
            INSTANCE = new StartupTiming();
        }
    }

    public static void timestamp(String name) {
        if (ENABLED) {
            init();
            INSTANCE.putTimestamp(name);
        }
    }

    public static void printSummary() {
        if (ENABLED) {
            init();
            INSTANCE.summary(System.out);
        }
    }

    private void putTimestamp(String tsName) {
        timestamps.add(new Timestamp(System.currentTimeMillis(), Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory(), tsName));
    }

    private void summary(PrintStream out) {
        out.println("Startup performance table:");
        out.printf("%1$-50s %2$20s %3$20s %4$20s\n", "<Timestamp>", "<FromStart>", "<FromPrev>", "<UsedMem>");

        TreeSet<Timestamp> sorted = new TreeSet<>(timestamps);
        long prevTs = startTime;
        for (Timestamp ts : sorted) {
            long relTs = ts.timestamp - startTime;
            long delta = ts.timestamp - prevTs;
            String msg = ts.name;

            out.printf("%1$-50s %2$18dms %3$18dms %4$17dKiB\n", msg, relTs, delta, ts.usedMem / 1024);

            prevTs = ts.timestamp;
        }
    }

    private static final class Timestamp implements Comparable<Timestamp> {
        private final long timestamp;
        private final long usedMem;
        private final String name;

        Timestamp(long ts, long usedMem, String name) {
            this.timestamp = ts;
            this.name = name;
            this.usedMem = usedMem;
        }

        @Override
        public int compareTo(Timestamp other) {
            return Long.compare(this.timestamp, other.timestamp);
        }
    }
}
