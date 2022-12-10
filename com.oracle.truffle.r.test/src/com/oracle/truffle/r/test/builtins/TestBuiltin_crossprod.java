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

// Checkstyle: stop line length check
public class TestBuiltin_crossprod extends TestBase {

    @Test
    public void testcrossprod1() {
        assertEval("argv <- list(structure(c(1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1), .Dim = c(60L, 5L)), structure(c(1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1), .Dim = c(60L, 6L))); .Internal(crossprod(argv[[1]], argv[[2]]))");
    }

    @Test
    public void testcrossprod2() {
        // FIXME NullPointerException at
        // com.oracle.truffle.r.nodes.builtin.base.MatMult.left0Dim(MatMult.java:119)
        assertEval(Ignored.ImplementationError, "argv <- list(numeric(0), numeric(0)); .Internal(crossprod(argv[[1]], argv[[2]]))");
    }

    @Test
    public void testcrossprod3() {
        assertEval("argv <- list(c(1.078125, 0.603125, -0.90625, 0.984375, 1.359375, -2.21875, -0.5, 1.2, 0.5), c(3.1859635002998, 2.5309880107589, 0.0716489644728567, 1.23651898905887, 1.28393932315826, -0.671528370670039, 0.873486219199556, 1.05088299688189, 0.0536562654335257)); .Internal(crossprod(argv[[1]], argv[[2]]))");
    }

    @Test
    public void testcrossprod4() {
        assertEval("argv <- list(structure(c(1, 2, 3, 4, 5, 6), .Dim = 2:3), c(2, 1)); .Internal(crossprod(argv[[1]], argv[[2]]))");
    }

    @Test
    public void testcrossprod5() {
        assertEval("argv <- list(c(1, 2, 3), structure(c(1, 3, 5, 2, 4, 6), .Dim = c(3L, 2L))); .Internal(crossprod(argv[[1]], argv[[2]]))");
    }

    @Test
    public void testcrossprod6() {
        assertEval("argv <- list(structure(c(0, 0, 1, 0), .Dim = c(2L, 2L), .Dimnames = list(NULL, NULL)), c(2, 3)); .Internal(crossprod(argv[[1]], argv[[2]]))");
    }

    @Test
    public void testcrossprod7() {
        assertEval("argv <- list(structure(c(-0.409148064492827, 0, 0.486127240746069, 0.000757379686646223), .Dim = c(2L, 2L), .Dimnames = list(c('Vm', 'K'), NULL)), structure(c(0, 6.20800822278518, 6.20800822278518, -25013.7571686415), .Dim = c(2L, 2L))); .Internal(crossprod(argv[[1]], argv[[2]]))");
    }

    @Test
    public void testcrossprod8() {
        assertEval("argv <- list(structure(c(-0.0972759604917099, -0.0972759604917099, -0.197781705719934, -0.197781705719934, -0.258476920906799, -0.258476920906799, -0.31681058743414, -0.31681058743414, -0.36711291168933, -0.36711291168933, -0.386611727075222, -0.386611727075222, -0.339690730499459, -0.33969073049946, -0.392353467475584, -0.392353467475584, -0.277328754578855, -0.277328754578855, -0.062581948827679, -0.062581948827679, 0.204605005658209, 0.204605005658209, 0.32860008733551, 0.32860008733551, 0.504748197638673, 0.504748197638673, 0.0398546163039329, 0.039854616303933, -0.269613788250837, -0.269613788250837, -0.312096598983548, -0.312096598983548, 0.0190548270250438, 0.0190548270250438, 0.270521530002251, 0.270521530002251), .Dim = c(12L, 3L)), structure(c(-2.82631170793264, -2.82631170793264, -3.89457420977924, -3.89457420977924, -3.62818861156626, -3.62818861156626, -2.72530862462141, -2.72530862462141, -1.437640468988, -1.437640468988, -0.811701520293695, -0.811701520293695, 14291.543903102, 14291.543903102, 13346.8386233407, 13346.8386233407, 8863.44390274002, 8863.44390274002, 4080.15117667984, 4080.15117667984, 979.818149952962, 979.818149952962, 296.593928028368, 296.593928028368), .Dim = c(12L, 2L), .Dimnames = list(NULL, c('Vm', 'K')))); .Internal(crossprod(argv[[1]], argv[[2]]))");
    }

    @Test
    public void testCrossprod() {
        assertEval("{ x <- matrix(c(0.368962955428, 0.977400955511, 0.5002433417831, 0.0664379808586, 0.6384031679481, 0.4481831840239), nrow=2); crossprod(x) }");
        assertEval("{ x <- 1:6 ; crossprod(x) }");
        assertEval("{ x <- 1:2 ; crossprod(t(x)) }");
        assertEval("{ crossprod(1:3, matrix(1:6, ncol=2)) }");
        assertEval("{ crossprod(t(1:2), 5) }");
        assertEval("{ crossprod(c(1,NA,2), matrix(1:6, ncol=2)) }");
        // The following test works if options(matprod = 'blas')
        assertEval(Ignored.ImplementationError, "{ x <- matrix(c(NaN,2,3,4,5,NA), nrow=3); crossprod(x) }");

        // FIXME Number at [2][2] position of resulting matrix differs
        assertEval(Ignored.ImplementationError, "{ x <- matrix(c(NaN,2+3i,3,4+1i,5,NA), nrow=3); crossprod(x) }");

        assertEval(Output.ImprovedErrorContext, "{ crossprod('asdf', matrix(1:6, ncol=2)) }");

        assertEval(template("crossprod(complex(real=%0, imaginary=%1), complex(real=%2, imaginary=%3))", new String[][]{
                        new String[]{"0", "1", "NaN"},
                        new String[]{"0", "1", "NaN"},
                        new String[]{"0", "1", "NaN"},
                        new String[]{"0", "1", "NaN"}
        }));

        assertEval(Ignored.ImplementationError, template("crossprod(complex(real=Inf, imaginary=%0), complex(real=%1, imaginary=%2))", new String[][]{
                        new String[]{"0", "1", "NaN"},
                        new String[]{"0", "1", "NaN"},
                        new String[]{"0", "1", "NaN"},
        }));

        assertEval(Ignored.ImplementationError, template("crossprod(complex(real=%0, imaginary=Inf), complex(real=%1, imaginary=%2))", new String[][]{
                        new String[]{"0", "1", "NaN"},
                        new String[]{"0", "1", "NaN"},
                        new String[]{"0", "1", "NaN"},
        }));

        assertEval(Ignored.ImplementationError, template("crossprod(complex(real=%0, imaginary=%1), complex(real=Inf, imaginary=%2))", new String[][]{
                        new String[]{"0", "1", "NaN"},
                        new String[]{"0", "1", "NaN"},
                        new String[]{"0", "1", "NaN"},
        }));

        assertEval(Ignored.ImplementationError, template("crossprod(complex(real=%0, imaginary=%1), complex(real=%2, imaginary=Inf))", new String[][]{
                        new String[]{"0", "1", "NaN"},
                        new String[]{"0", "1", "NaN"},
                        new String[]{"0", "1", "NaN"},
        }));
    }

    @Test
    public void testCrossprodDimnames() {
        assertEval("{ crossprod(structure(1:9, .Dim=c(3L,3L), .Dimnames=list(c('a', 'b', 'c'), c('A', 'B', 'C'))), structure(1:9, .Dim=c(3L,3L), .Dimnames=list(c('d', 'e', 'f'), c('D', 'E', 'F')))) }");
    }
}
