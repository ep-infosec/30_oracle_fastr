/*
 * Copyright (c) 2017, 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.r.test.library.grid;

import org.junit.Test;

import com.oracle.truffle.r.test.TestBase;

/**
 * Tests non-graphical functions in grid package.
 */
public class TestGridPackage extends TestBase {
    @Test
    public void testUnits() {
        run("unit.c(unit(1,'mm'), unit(1,'mm'))");
        run("3 * (unit(1, 'mm'));");
        run("grid:::unit.list(3 * unit(1, 'mm'));");
        run("unit.c(unit(1,'mm'), 42*unit(1,'mm'));");
    }

    private void run(String testCode) {
        assertEval(String.format("{ library(grid); %s }", testCode));
    }
}
