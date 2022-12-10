/*
 * Copyright (c) 2015, 2020, Oracle and/or its affiliates. All rights reserved.
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

#include <Defn.h>
#include <rffiutils.h>
#include "rffi_upcalls.h"

void R_CheckStack(void) {
    // TODO: check for stack overflow
    // ignored
}

void R_CheckStack2(size_t extra) {
    // TODO: check for stack overflow
    // ignored
}

void R_CheckUserInterrupt(void) {
    // ignored
}

Rboolean isOrdered(SEXP s)
{
    return (TYPEOF(s) == INTSXP
	    && inherits(s, "factor")
	    && inherits(s, "ordered"));
}


SEXP octsize(SEXP s)
{
	return ((call_octsize) callbacks[octsize_x])(s);
}

#define NAMEDMAX 7

void (ENSURE_NAMEDMAX)(SEXP v) { 
	SEXP __enm_v__ = v;
	if (NAMED(__enm_v__) < NAMEDMAX) {
	    SET_NAMED( __enm_v__, NAMEDMAX);
	}
}

SEXP DispatchPRIMFUN(SEXP call, SEXP op, SEXP args, SEXP env) {
    SEXP result = ((call_DispatchPRIMFUN) callbacks[DispatchPRIMFUN_x])(call, op, args, env);
	checkExitCall();
    return result;
}

CCODE PRIMFUN(SEXP x) {
	return &DispatchPRIMFUN;
}
