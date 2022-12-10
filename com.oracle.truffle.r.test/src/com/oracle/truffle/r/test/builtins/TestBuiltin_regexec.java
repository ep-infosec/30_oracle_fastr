/*
 * This program is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation; either version 2 of the License, or (at your option)
 * any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License for
 * more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program; if not, write to the Free Software Foundation, Inc., 51
 * Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 *
 * Copyright (c) 2014, Purdue University
 * Copyright (c) 2014, 2018, Oracle and/or its affiliates
 *
 * All rights reserved.
 */
package com.oracle.truffle.r.test.builtins;

import org.junit.Test;

import com.oracle.truffle.r.test.TestBase;

// Checkstyle: stop line length check
public class TestBuiltin_regexec extends TestBase {

    @Test
    public void testregexec1() {
        // Extra newline after "match.length" attr output
        assertEval(Output.IgnoreWhitespace,
                        "argv <- list('^(([^:]+)://)?([^:/]+)(:([0-9]+))?(/.*)', 'http://stat.umn.edu:80/xyz', FALSE, FALSE, FALSE); .Internal(regexec(argv[[1]], argv[[2]], argv[[3]], argv[[4]], argv[[5]]))");

        assertEval(Output.IgnoreWhitespace, "regexec(\"^(?:(?:^\\\\[([^\\\\]]+)\\\\])?(?:'?([^']+)'?!)?([a-zA-Z0-9:\\\\-$\\\\[\\\\]]+)|(.*))$\", 'A1', perl=T)");
        assertEval("regexec(\"^(?:(?:^\\\\[([^\\\\]]+)\\\\])?(?:'?([^']+)'?!)?([a-zA-Z0-9:\\\\-$\\\\[\\\\]]+)|(.*))$\", 'A1A1', perl=T)");
        assertEval("regexec(\"^(?:(?:^\\\\[([^\\\\]]+)\\\\])?(?:'?([^']+)'?!)?([a-zA-Z0-9:\\\\-$\\\\[\\\\]]+)|(.*))$\", 'A1 A1', perl=T)");
        assertEval("regexec(\"^(?<n1>:(?:^\\\\[([^\\\\]]+)\\\\])?(?:'?([^']+)'?!)?([a-zA-Z0-9:\\\\-$\\\\[\\\\]]+)|(.*))$\", 'A1', perl=T)");
        assertEval("regexec(\"^(?<n1>:(?:^\\\\[([^\\\\]]+)\\\\])?(?<n2>:'?([^']+)'?!)?([a-zA-Z0-9:\\\\-$\\\\[\\\\]]+)|(.*))$\", 'A1', perl=T)");
        assertEval("regexec(\"^(?:(?:^\\\\[([^\\\\]]+)\\\\])?(?<n2>:'?([^']+)'?!)?([a-zA-Z0-9:\\\\-$\\\\[\\\\]]+)|(.*))$\", 'A1', perl=T)");
        assertEval("regexec(\"^((.*))$\", 'A1', perl=T)");
        assertEval("regexec(\"^(?<n>(.*))$\", 'A1', perl=T)");
        assertEval("regexec(\"^(.*)$\", 'A1', perl=T)");
        assertEval("regexec(\"^(?<n>.*)$\", 'A1', perl=T)");

        assertEval("regexec(\"^(([A-Z)|([a-z]))$\", 'Aa', perl=T)");
        assertEval("regexec(\"^(([A-Z)|([a-z]))$\", c('A', 'Aa'), perl=T)");
        assertEval("regexec(\"^(([A-Z)|([a-z]))$\", c('Aa', 'A'), perl=T)");
    }
}
