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
public class TestBuiltin_setEncoding extends TestBase {

    @Test
    public void testsetEncoding1() {
        assertEval("argv <- list('abc', 'UTF-8'); .Internal(setEncoding(argv[[1]], argv[[2]]))");
    }

    @Test
    public void testsetEncoding2() {
        assertEval("argv <- list('', 'unknown'); .Internal(setEncoding(argv[[1]], argv[[2]]))");
    }

    @Test
    public void testsetEncoding3() {
        assertEval("argv <- list('3.0.1', 'unknown'); .Internal(setEncoding(argv[[1]], argv[[2]]))");
    }

    @Test
    public void testsetEncoding4() {
        assertEval("argv <- list(structure(c('Matrix', '1.0-12', '2013-03-26', 'recommended', 'Sparse and Dense Matrix Classes and Methods', 'Douglas Bates <bates@stat.wisc.edu> and Martin Maechler\\n        <maechler@stat.math.ethz.ch>', 'Martin Maechler <mmaechler+Matrix@gmail.com>', 'Doug and Martin <Matrix-authors@R-project.org>', 'Classes and methods for dense and sparse matrices and\\n        operations on them using Lapack and SuiteSparse.', 'R (>= 2.15.0), stats, methods, utils, lattice', 'graphics, grid', 'expm, MASS', 'MatrixModels, graph, SparseM, sfsmisc', 'UTF-8', 'no', 'no longer available, since we use data/*.R *and* our\\nclasses', 'yes', 'no', 'GPL (>= 2)', 'The Matrix package includes libraries AMD, CHOLMOD,\\nCOLAMD, CSparse and SPQR from the SuiteSparse collection of Tim\\nDavis.  All sections of that code are covered by the GPL or\\nLGPL licenses.  See the directory doc/UFsparse for details.', 'http://Matrix.R-forge.R-project.org/', '2013-03-26 15:38:54 UTC; maechler', 'yes', 'CRAN', '2013-03-26 19:25:05', 'R 3.0.1; x86_64-unknown-linux-gnu; 2013-12-07 03:52:11 UTC; unix'), .Names = c('Package', 'Version', 'Date', 'Priority', 'Title', 'Author', 'Maintainer', 'Contact', 'Description', 'Depends', 'Imports', 'Suggests', 'Enhances', 'Encoding', 'LazyData', 'LazyDataNote', 'ByteCompile', 'BuildResaveData', 'License', 'LicenseDetails', 'URL', 'Packaged', 'NeedsCompilation', 'Repository', 'Date/Publication', 'Built')), structure('UTF-8', .Names = 'Encoding')); .Internal(setEncoding(argv[[1]], argv[[2]]))");
    }
}
