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
 * Copyright (c) 2013, 2019, Oracle and/or its affiliates
 *
 * All rights reserved.
 */
package com.oracle.truffle.r.test.builtins;

import org.junit.Test;

import com.oracle.truffle.r.test.TestBase;
import java.io.File;
import org.junit.After;

// Checkstyle: stop line length check

public class TestBuiltin_scan extends TestBase {

    private static final String TEST_CVS_FILE = "__TestBuiltin_scan_testReadCsvTestFile.cvs";

    @After
    public void cleanup() {
        File f = new File(TEST_CVS_FILE);
        if (f.exists()) {
            f.delete();
        }
    }

    @Test
    public void testScan() {
        // from scan's documentation
        assertEval("{ con<-textConnection(c(\"TITLE extra line\", \"2 3 5 7\", \"11 13 17\")); scan(con, skip = 1, quiet = TRUE) }");
        assertEval("{ con<-textConnection(c(\"TITLE extra line\", \"2 3 5 7\", \"11 13 17\")); scan(con, skip = 1) }");
        assertEval("{ con<-textConnection(c(\"TITLE extra line\", \"2 3 5 7\", \"11 13 17\")); scan(con, skip = 1, nlines = 1) }");
        assertEval("{ con<-textConnection(c(\"TITLE extra line\", \"2 3 5 7\", \"11 13 17\")); scan(con, what = list(\"\",\"\",\"\")) }");
        assertEval("{ con<-textConnection(c(\"TITLE extra line\", \"2 3 5 7\", \"11 13 17\")); scan(con, what = list(\"\",\"\",\"\"), flush=TRUE) }");

        assertEval("{ con<-textConnection(c(\"HEADER\", \"7 2 3\", \"4 5 42\")); scan(con, skip = 1) }");
        assertEval("{ con<-textConnection(c(\"HEADER\", \"7 2 3\", \"4 5 42\")); scan(con, skip = 1, quiet=TRUE) }");
        assertEval("{ con<-textConnection(c(\"HEADER\", \"7 2 3\", \"4 5 42\")); scan(con, skip = 1, nlines = 1) }");
        assertEval("{ con<-textConnection(c(\"HEADER\", \"7 2 3\", \"4 5 42\")); scan(con, what = list(\"\",\"\",\"\")) }");

        assertEval("{ con<-textConnection(c(\"HEADER\", \"7 2 3\", \"4 5 42\")); scan(con, what = list(\"\",\"\",\"\"), fill=TRUE) }");
        assertEval("{ con<-textConnection(c(\"HEADER\", \"7 2 3\", \"4 5 42\")); scan(con, what = list(\"\",\"\",\"\"), multi.line=FALSE) }");
        assertEval("{ con<-textConnection(c(\"HEADER\", \"7 2 3\", \"4 5 42\")); scan(con, what = list(\"\",\"\",\"\"), fill=TRUE, multi.line=FALSE) }");

        assertEval("{ con<-textConnection(c(\"\\\"2\\\"\", \"\\\"11\\\"\")); scan(con, what=list(\"\")) }");
        assertEval("{ con<-textConnection(c(\"2 3 5\", \"\", \"11 13 17\")); scan(con, what=list(\"\")) }");
        assertEval("{ con<-textConnection(c(\"2 3 5\", \"\", \"11 13 17\")); scan(con, what=list(\"\"), blank.lines.skip=FALSE) }");
        assertEval("{ con<-textConnection(c(\"2 3 5\", \"\", \"11 13 17\")); scan(con, what=list(integer()), blank.lines.skip=FALSE) }");

        assertEval("{ con<-textConnection(c(\"foo faz\", \"\\\"bar\\\" \\\"baz\\\"\")); scan(con, what=list(\"\", \"\")) }");
        assertEval("{ con<-textConnection(c(\"foo faz\", \"bar \\\"baz\\\"\")); scan(con, what=list(\"\", \"\")) }");
        assertEval("{ con<-textConnection(c(\"foo, faz\", \"bar, baz\")); scan(con, what=list(\"\", \"\"), sep=\",\") }");

        assertEval("con<-textConnection(c(\"foo,\\\"bar,bar\\\"\")); scan(con, what=list(\"\"), sep=',')");
        assertEval("con<-textConnection(c(\"foo,'bar,bar'\")); scan(con, what=list(\"\"), sep=',')");

        assertEval("{ con<-textConnection(c(\"bar'foo'\")); scan(con, what=list(\"\")) }");
        assertEval("{ con<-textConnection(c(\"'foo'\")); scan(con, what=list(\"\")) }");
        assertEval("{ con<-textConnection(c(\"bar 'foo'\")); scan(con, what=list(\"\")) }");

        // sep should not be treated as a regex:
        assertEval("con <- textConnection(\"A|B|C\\n1|2|3\\n4|5|6\"); read.csv(con, sep=\"|\")");

        assertEval("con <- textConnection('%%MatrixMarket matrix coordinate real general'); list(" +
                        "scan(con, nmax = 1, what = character(), quiet = TRUE), " +
                        "scan(con, nmax = 1, what = character(), quiet = TRUE), " +
                        "scan(con, nmax = 1, what = character(), quiet = TRUE), " +
                        "scan(con, nmax = 1, what = character(), quiet = TRUE), " +
                        "scan(con, nmax = 1, what = character(), quiet = TRUE))");
    }

    @Test
    public void testReadCsv() {
        String testData = "n1,n2\nv1,\"v5, v5\"\n";
        assertEval("fileConn<-file('" + TEST_CVS_FILE + "'); writeLines(c('" + testData + "'), fileConn); m <- read.csv('" + TEST_CVS_FILE + "'); m");
    }

    @Test
    public void testArgsCasts() {
        // Empty 2nd 'what' parameter
        assertEval(Output.IgnoreErrorContext, "{ con<-textConnection(c(\"1 2 3\", \"4 5 6\")); .Internal(scan(con, , 2, ' ', '.', '\"', 0, 3, \"NA\", F, F, F, T, T, '', '#', T, 'utf8', F)) }");
        // NULL 2nd 'what' parameter
        assertEval("{ con<-textConnection(c(\"1 2 3\", \"4 5 6\")); .Internal(scan(con, NULL, 2, ' ', '.', '\"', 0, 3, \"NA\", F, F, F, T, T, '', '#', T, 'utf8', F)) }");
        // function passed as 2nd 'what' parameter
        assertEval("{ con<-textConnection(c(\"1 2 3\", \"4 5 6\")); .Internal(scan(con, print, 2, ' ', '.', '\"', 0, 3, \"NA\", F, F, F, T, T, '', '#', T, 'utf8', F)) }");
        // NULL 4th 'sep' parameter
        assertEval("{ con<-textConnection(c(\"1 2 3\", \"4 5 6\")); .Internal(scan(con, 1L, 2, NULL, '.', '\"', 0, 3, \"NA\", F, F, F, T, T, '', '#', T, 'utf8', F)) }");
        // NULL 5th 'dec' parameter
        assertEval("{ con<-textConnection(c(\"1.5 2.89 3\", \"4 5 6\")); .Internal(scan(con, 1.2, 2, ' ', NULL, '\"', 0, 3, \"NA\", F, F, F, T, T, '', '#', T, 'utf8', F)) }");
    }

    @Test
    public void testPooling() {
        assertEvalFastR("s <- scan(textConnection(paste0(rep('asdf\\n', 1000))), character(0), quiet=T); all(sapply(s, function(x) .fastr.identity(x) == .fastr.identity(s[[1]])))", "TRUE");
    }
}
