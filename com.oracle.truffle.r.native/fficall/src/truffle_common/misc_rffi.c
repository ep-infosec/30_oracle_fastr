/*
 * Copyright (c) 2015, 2018, Oracle and/or its affiliates. All rights reserved.
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

#include <rffiutils.h>

double call_misc_exactSumFunc(double* contents, int length, int hasNa, int naRm) {

	long double sum = 0;
	int i = 0;
	if (!hasNa) {
		for (; i < length - 3; i+= 4) {
			sum += contents[i];
			sum += contents[i + 1];
			sum += contents[i + 2];
			sum += contents[i + 3];
		}
	}
	for (; i < length; i++) {
		double value = contents[i];
		if (R_IsNA(value)) {
			if (!naRm) {
				return R_NaReal;
			}
		} else {
			sum += value;
		}
	}

	return sum;
}

extern void dqrls_(double *x, int *n, int *p, double *y, int *ny, double *tol, double *b, double *rsd, double *qty, int *k, int *jpvt, double *qraux, double *work);

void call_misc_dqrls(double *x, int n, int p, double *y, int ny, double tol, double *b, double *rsd, double *qty, int *k, int *jpvt, double *qraux, double *work) {
	dqrls_(x, &n, &p, y, &ny, &tol, b, rsd, qty, k, jpvt, qraux, work);
}
