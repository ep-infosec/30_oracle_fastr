/*
 * Copyright (c) 2014, 2022, Oracle and/or its affiliates. All rights reserved.
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
#define NO_FASTR_REDEFINE
#include <Rinterface.h>
#include <rffiutils.h>
#include <Rinternals_common.h>
#include "../common/rffi_upcalls.h"

CALLBACKS_T callbacks = NULL;

void Rinternals_addCallback(TruffleEnv* env, void** theCallbacks, int index, void *closure) {
        (*env)->newClosureRef(env, closure);
        theCallbacks[index] = closure;
}

void*** Rinternals_getCallbacksAddress() {
        return &callbacks;
}

char *ensure_truffle_chararray_n(const char *x, int n) {
	return (char *) x;
}

char *ensure_truffle_chararray(const char *x) {
	return (char *) x;
}

void *ensure_string(const char * x) {
	return (void *) x;
}

void *ensure_function(void *fptr) {
	return fptr;
}

#include "../truffle_common/Rinternals_truffle_common.h"

#define ARRAY_CACHE_SIZE 5

typedef struct array_cache_entry {
	SEXP key;
	void *data;
	unsigned int hits;
} ArrayCacheEntry;

static __thread ArrayCacheEntry int_cache[ARRAY_CACHE_SIZE];
static __thread ArrayCacheEntry real_cache[ARRAY_CACHE_SIZE];

static inline int array_cache_lookup(ArrayCacheEntry *cache, SEXP key) {
#if ARRAY_CACHE_SIZE > 0
  for(int i=0; i < ARRAY_CACHE_SIZE; i++) {
    	if(cache[i].key == key) {
    		(cache[i].hits)++;
    		return i;
    	}
    }
#endif
  return -1;
}

static inline void array_cache_insert(ArrayCacheEntry *cache, SEXP key,
		void *data) {

#if ARRAY_CACHE_SIZE > 0
	// replace least frequent
	unsigned hits = cache[0].hits;
	int idx = 0;

	for (int i = 1; i < ARRAY_CACHE_SIZE && hits != 0; i++) {
		if (cache[i].hits < hits) {
			hits = cache[i].hits;
			idx = i;
		}
	}

	cache[idx].key = key;
	cache[idx].data = data;
	cache[idx].hits = 0;
#endif
}

int *INTEGER(SEXP x) {
    TRACE(TARGp, x);

    // lookup in cache
    int idx = array_cache_lookup(int_cache, x);
    if(idx >= 0) {
    	return (int *)int_cache[idx].data;
    }

    int *result = FASTR_INTEGER(x);

    array_cache_insert(int_cache, x, result);
    return result;
}

double *REAL(SEXP x){
    TRACE(TARGp, x);

    // lookup in cache
    int idx = array_cache_lookup(real_cache, x);
    if(idx >= 0) {
    	return (double *)real_cache[idx].data;
    }

    double *result = FASTR_REAL(x);

    array_cache_insert(real_cache, x, result);
    return result;
}

/* Unwind-protect mechanism to support C++ stack unwinding. */

typedef struct {
//    int jumpmask;
    jmp_buf *jumptarget;
} unwind_cont_t;

SEXP R_MakeUnwindCont()
{
    return CONS(R_NilValue, allocVector(RAWSXP, sizeof(unwind_cont_t)));
}

#define RAWDATA(x) ((void *) RAW0(x))

void NORET R_ContinueUnwind(SEXP cont)
{
    SEXP retval = CAR(cont);
    unwind_cont_t *u = RAWDATA(CDR(cont));
	longjmp(*(u->jumptarget), 1);
}

SEXP R_UnwindProtect(SEXP (*fun)(void *data), void *data,
		     void (*cleanfun)(void *data, Rboolean jump),
		     void *cleandata, SEXP cont) {
    SEXP result;
    Rboolean jump;

    /* Allow simple usage with a NULL continuotion token. This _could_
       result in a failure in allocation or exceeding the PROTECT
       stack limit before calling fun(), so fun() and cleanfun should
       be written accordingly. */
    if (cont == NULL) {
	PROTECT(cont = R_MakeUnwindCont());
	result = R_UnwindProtect(fun, data, cleanfun, cleandata, cont);
	UNPROTECT(1);
	return result;
    }

	// peek the jump stack
    jmp_buf *jumptarget = peekJmpBuf();  
    jmp_buf cjmpbuf;
    pushJmpBuf(&cjmpbuf);      
    if (setjmp(cjmpbuf)) {
	jump = TRUE;
	//SETCAR(cont, R_ReturnedValue);
	unwind_cont_t *u = RAWDATA(CDR(cont));
	u->jumptarget = jumptarget;
    }
    else {
	result = fun(data);
	SETCAR(cont, result);
	jump = FALSE;
    }
    popJmpBuf();

    cleanfun(cleandata, jump);

    if (jump)
	R_ContinueUnwind(cont);	

    return result;
}

int call_base_dispatchHandlers() {
    DO_CALL(dispatchHandlers());
}
