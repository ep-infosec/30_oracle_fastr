/*
 * Copyright (c) 2016, 2022, Oracle and/or its affiliates. All rights reserved.
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

extern void dotCModifiedArguments(int* len, int* idata, double* rdata, int* ldata, char** cdata);

extern SEXP addInt(SEXP a, SEXP b);

extern SEXP addDouble(SEXP a, SEXP b);

extern SEXP populateIntVector(SEXP n);

extern SEXP populateRawVector(SEXP n);

extern SEXP populateCharacterVector(SEXP n);

extern SEXP populateComplexVector(SEXP n);

extern SEXP populateDoubleVector(SEXP n);

extern SEXP populateLogicalVector(SEXP n);

extern SEXP createExternalPtr(SEXP addr, SEXP tag, SEXP prot);

extern SEXP getExternalPtrAddr(SEXP eptr);

extern SEXP invoke_TYPEOF(SEXP x);

extern SEXP invoke_error(SEXP msg);

extern SEXP dot_external_access_args(SEXP args);

extern void invoke_fun(double* data, int* n, void* fun);

extern SEXP invoke_isString(SEXP s);

extern SEXP invoke12(SEXP a1, SEXP a2, SEXP a3, SEXP a4, SEXP a5, SEXP a6, SEXP a7, SEXP a8, SEXP a9, SEXP a10, SEXP a11, SEXP a12);

extern SEXP interactive(void);

extern SEXP tryEval(SEXP expr, SEXP env);

extern SEXP rHomeDir();

extern SEXP nestedCall1(SEXP upcall, SEXP env);

extern SEXP nestedCall2(SEXP v);

extern SEXP r_home(void);

extern SEXP mkStringFromChar(void);

extern SEXP mkStringFromRaw();

extern SEXP mkStringFromBytes(void);

extern SEXP null(void);

extern SEXP iterate_iarray(SEXP x);

extern SEXP iterate_iptr(SEXP x);

extern SEXP preserve_object(SEXP val);

extern SEXP release_object(SEXP x);

extern SEXP findvar(SEXP x, SEXP env);

extern SEXP test_asReal(SEXP x);

extern SEXP test_asChar(SEXP x);

extern SEXP test_asInteger(SEXP x);

extern SEXP test_asLogical(SEXP x);

extern SEXP test_CAR(SEXP x);

extern SEXP test_CDR(SEXP x);

extern SEXP test_LENGTH(SEXP x);

extern SEXP test_inlined_length(SEXP x);

extern SEXP test_coerceVector(SEXP x, SEXP mode);

extern SEXP test_ATTRIB(SEXP);

extern SEXP test_getAttrib(SEXP,SEXP);

extern SEXP test_stringNA(void);

extern SEXP test_captureDotsWithSingleElement(SEXP env);

extern SEXP test_evalAndNativeArrays(SEXP vec, SEXP expr, SEXP env);

extern SEXP test_writeConnection(SEXP conn);

extern SEXP test_readConnection(SEXP conn);

extern SEXP test_createNativeConnection(void);

extern SEXP test_ParseVector(SEXP src);

extern SEXP test_lapply(SEXP list, SEXP fn, SEXP rho);

extern SEXP test_RfFindFunAndRfEval(SEXP x, SEXP y);

extern SEXP test_RfEvalWithPromiseInPairList(void);

extern SEXP test_isNAString(SEXP vec);

extern SEXP test_setStringElt(SEXP vec, SEXP elt);

extern SEXP test_getBytes(SEXP vec);

extern SEXP test_RfRandomFunctions();

extern SEXP test_RfRMultinom();

extern SEXP test_RfFunctions();

extern SEXP test_DATAPTR(SEXP,SEXP);

extern SEXP test_duplicate(SEXP, SEXP);

extern SEXP test_R_nchar(SEXP x);

extern SEXP test_forceAndCall(SEXP call, SEXP args, SEXP rho);

extern SEXP test_constant_types();

extern SEXP shareIntElement(SEXP x, SEXP xIndex, SEXP y, SEXP yIndex);

extern SEXP shareDoubleElement(SEXP x, SEXP xIndex, SEXP y, SEXP yIndex);

extern SEXP shareStringElement(SEXP x, SEXP xIndex, SEXP y, SEXP yIndex);

extern SEXP shareListElement(SEXP x, SEXP xIndex, SEXP y, SEXP yIndex);

extern SEXP test_sort_complex(SEXP complexVec);

extern SEXP testMultiSetProtection();

extern SEXP get_dataptr(SEXP vec);

extern void benchRf_isNull(int* n);

extern SEXP benchMultipleUpcalls(SEXP x);

extern SEXP test_lapplyWithForceAndCall(SEXP list, SEXP fn, SEXP fa, SEXP rho);

extern SEXP benchProtect(SEXP x, SEXP nn);

extern SEXP testMissingArgWithATTRIB();

extern SEXP testPRIMFUN(SEXP fun, SEXP args);

extern SEXP testTrace();

extern SEXP testdiv(SEXP n);

extern SEXP testInstallTrChar(SEXP strvec, SEXP env);

extern SEXP test_RfMatch(SEXP x, SEXP y);

extern SEXP test_mkCharDoesNotCollect();

extern SEXP test_setRRawVector();

extern SEXP test_setRRawVector2();
