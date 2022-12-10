/*
 * Copyright (c) 2017, 2019, Oracle and/or its affiliates. All rights reserved.
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
#include <Rinternals.h>
#include "rffi_upcalls.h"

void GetRNGstate() {
    ((call_GetRNGstate) callbacks[GetRNGstate_x])();
}

void PutRNGstate() {
	((call_PutRNGstate) callbacks[PutRNGstate_x])();
}

double unif_rand() {
	return ((call_unif_rand) callbacks[unif_rand_x])();
}

double norm_rand() {
	return ((call_norm_rand) callbacks[norm_rand_x])();
}

double exp_rand() {
	return ((call_exp_rand) callbacks[exp_rand_x])();
}
