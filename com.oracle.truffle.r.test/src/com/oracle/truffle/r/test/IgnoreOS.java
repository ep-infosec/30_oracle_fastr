/*
 * Copyright (c) 2017, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.r.test;

import java.util.Arrays;

/**
 * This test trait allows ignoring tests on certain operating systems.
 */
public enum IgnoreOS implements TestTrait {
    Solaris("sunos"),
    Linux("linux"),
    MacOS("darwin", "Mac OS X");

    private String[] osNames;

    IgnoreOS(String... names) {
        osNames = names;
    }

    public static boolean containsIgnoring(TestTrait[] traits) {
        return Arrays.stream(TestTrait.collect(traits, IgnoreOS.class)).anyMatch(t -> t.isIgnoring());
    }

    private boolean isIgnoring() {
        String current = System.getProperty("os.name");
        if (current == null) {
            return false;
        }
        for (String osName : osNames) {
            if (current.toLowerCase().contains(osName.toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    @Override
    public String getName() {
        return name();
    }
}
