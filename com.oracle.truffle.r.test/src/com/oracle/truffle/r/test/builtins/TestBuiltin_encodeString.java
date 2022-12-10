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
 * Copyright (c) 2012-2014, Purdue University
 * Copyright (c) 2013, 2018, Oracle and/or its affiliates
 *
 * All rights reserved.
 */
package com.oracle.truffle.r.test.builtins;

import org.junit.Test;

import com.oracle.truffle.r.test.TestBase;

// Checkstyle: stop line length check
public class TestBuiltin_encodeString extends TestBase {

    @Test
    public void testencodeString1() {
        assertEval("argv <- list(c('1', '2', NA), 0L, '\\\'', 0L, FALSE); .Internal(encodeString(argv[[1]], argv[[2]], argv[[3]], argv[[4]], argv[[5]]))");
    }

    @Test
    public void testencodeString3() {
        assertEval("argv <- list(c('a', 'ab', 'abcde'), NA, '', 0L, TRUE); .Internal(encodeString(argv[[1]], argv[[2]], argv[[3]], argv[[4]], argv[[5]]))");
    }

    @Test
    public void testencodeString4() {
        assertEval("argv <- list(c('a', 'ab', 'abcde'), NA, '', 1L, TRUE); .Internal(encodeString(argv[[1]], argv[[2]], argv[[3]], argv[[4]], argv[[5]]))");
    }

    @Test
    public void testencodeString6() {
        assertEval("argv <- list(c('NA', 'a', 'b', 'c', 'd', NA), 0L, '', 0L, TRUE); .Internal(encodeString(argv[[1]], argv[[2]], argv[[3]], argv[[4]], argv[[5]]))");
    }

    @Test
    public void testencodeString7() {
        assertEval("argv <- list('ab\\bc\\ndef', 0L, '', 0L, TRUE); .Internal(encodeString(argv[[1]], argv[[2]], argv[[3]], argv[[4]], argv[[5]]))");
    }

    @Test
    public void testencodeString8() {
        assertEval("argv <- list(c('FALSE', NA), 0L, '', 0L, TRUE); .Internal(encodeString(argv[[1]], argv[[2]], argv[[3]], argv[[4]], argv[[5]]))");
    }

    @Test
    public void testencodeString9() {
        assertEval("argv <- list(structure('integer(0)', .Names = 'c0', row.names = character(0)), 0L, '', 0L, TRUE); .Internal(encodeString(argv[[1]], argv[[2]], argv[[3]], argv[[4]], argv[[5]]))");
    }

    @Test
    public void testencodeString10() {
        assertEval("argv <- list('\\\'class\\\' is a reserved slot name and cannot be redefined', 0L, '\\\'', 0L, FALSE); .Internal(encodeString(argv[[1]], argv[[2]], argv[[3]], argv[[4]], argv[[5]]))");
    }

    @Test
    public void testencodeString11() {
        assertEval("argv <- list(structure(character(0), .Dim = c(0L, 0L)), 0L, '', 0L, TRUE); .Internal(encodeString(argv[[1]], argv[[2]], argv[[3]], argv[[4]], argv[[5]]))");
    }

    @Test
    public void testencodeString12() {
        // docs mention that width arg is integer so logical(0) is correctly refused by FastR
        assertEval(Ignored.ReferenceError, "argv <- list(character(0), logical(0), '', 0L, TRUE); .Internal(encodeString(argv[[1]], argv[[2]], argv[[3]], argv[[4]], argv[[5]]))");
    }

    @Test
    public void testencodeString13() {
        assertEval("argv <- list(structure('integer(0)', .Names = 'c0', row.names = character(0)), structure(list(c0 = structure(integer(0), .Label = character(0), class = 'factor')), .Names = 'c0', row.names = character(0), class = structure('integer(0)', .Names = 'c0')), '', 0L, TRUE); .Internal(encodeString(argv[[1]], argv[[2]], argv[[3]], argv[[4]], argv[[5]]))");
    }

    @Test
    public void testEncodeString() {
        String[] strings = new String[]{"c('a','ab','abcde',NA,'\\t\\n\\u1234foo\\\"\\'')", "c('a',NA)"};
        String[] widths = new String[]{"0", "1", "3", "7", "NA"};
        String[] quote = new String[]{"'a'", "'\\''", "'\"'", ""};
        String[] justify = new String[]{"'l'", "'r'", "'c'", "'n'"};
        String[] naEncode = new String[]{"T", "F"};
        assertEval(template("encodeString(%0, width = %1, quote=%2, justify=%3, na=%4)", strings, widths, quote, justify, naEncode));
    }
}
