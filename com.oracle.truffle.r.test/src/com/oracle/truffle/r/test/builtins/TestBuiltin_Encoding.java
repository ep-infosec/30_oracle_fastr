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
public class TestBuiltin_Encoding extends TestBase {

    @Test
    public void testEncoding1() {
        assertEval("argv <- list('Byte Code Compiler'); .Internal(Encoding(argv[[1]]))");
    }

    @Test
    public void testEncoding2() {
        // FIXME GnuR outputs "unknown" for each vector member while FastR just one:
        // FastR output: [1] "unknown"
        assertEval(Ignored.ImplementationError,
                        "argv <- list(c('\\n', '\\n', '## These cannot be run by examples() but should be OK when pasted\\n', '## into an interactive R session with the tcltk package loaded\\n', '\\n', 'tt <- tktoplevel()\\n', 'tkpack(txt.w <- tktext(tt))\\n', 'tkinsert(txt.w, \\\'0.0\\\', \\\'plot(1:10)\\\')\\n', '\\n', '# callback function\\n', 'eval.txt <- function()\\n', '   eval(parse(text = tclvalue(tkget(txt.w, \\\'0.0\\\', \\\'end\\\'))))\\n', 'tkpack(but.w <- tkbutton(tt, text = \\\'Submit\\\', command = eval.txt))\\n', '\\n', '## Try pressing the button, edit the text and when finished:\\n', '\\n', 'tkdestroy(tt)\\n', '\\n', '\\n')); .Internal(Encoding(argv[[1]]))");
    }

    @Test
    public void testEncoding3() {
        // FIXME character '‘' causes the string to become "UTF-8" so we should
        // probably adhere to this too thus marking ImplementationError for now
        // Expected output: [1] "UTF-8"
        // FastR output: [1] "unknown"
        assertEval(Ignored.ImplementationError, "argv <- list('detaching ‘package:nlme’, ‘package:splines’'); .Internal(Encoding(argv[[1]]))");
    }

    @Test
    public void testEncoding4() {
        // FastR result seems better thus ReferenceError for now
        // Expected output: character(0)
        // FastR output: [1] "unknown"
        assertEval(Ignored.ReferenceError, "argv <- list(structure(character(0), class = 'check_code_usage_in_package')); .Internal(Encoding(argv[[1]]))");
    }

    @Test
    public void testEncoding5() {
        assertEval("argv <- list(structure('Type demo(PKG::FOO) to run demonstration PKG::FOO.', .Names = 'demo')); .Internal(Encoding(argv[[1]]))");
    }

    @Test
    public void testEncoding6() {
        assertEval("argv <- list('A shell of class documentation has been written to the file ./myTst2/man/DocLink-class.Rd.\\n'); .Internal(Encoding(argv[[1]]))");
    }

    @Test
    public void testEncoding7() {
        // FIXME GnuR outputs "unknown" for each vector member while FastR just one:
        // FastR output: [1] "unknown"
        assertEval(Ignored.ImplementationError,
                        "argv <- list(c('* Edit the help file skeletons in man, possibly combining help files for multiple functions.', '* Edit the exports in NAMESPACE, and add necessary imports.', '* Put any C/C++/Fortran code in src.', '* If you have compiled code, add a useDynLib() directive to NAMESPACE.', '* Run R CMD build to build the package tarball.', '* Run R CMD check to check the package tarball.', '', 'Read Writing R Extensions for more information.')); .Internal(Encoding(argv[[1]]))");
    }

    @Test
    public void testEncoding9() {
        assertEval("argv <- structure(list(x = 'abc'), .Names = 'x');do.call('Encoding', argv)");
    }

    @Test
    public void testEncoding() {
        assertEval("{ x<-42; Encoding(x) }");
    }
}
