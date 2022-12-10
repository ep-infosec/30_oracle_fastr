/*
 * Copyright (c) 2020, 2022, Oracle and/or its affiliates. All rights reserved.
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
#include "fastrUpcalls.h"

#include <Rinternals.h>
#include <Defn.h>

#include "../common/rffi_upcalls.h"

extern void *ensure_string(const char *x);
extern int checkExitCall();

void gdcSetColor(int gdId, int cc) {
	((call_gdcSetColor) callbacks[gdcSetColor_x])(gdId, cc);
    checkExitCall();
}

void gdcSetFill(int gdId, int cc) {
	((call_gdcSetFill) callbacks[gdcSetFill_x])(gdId, cc);
    checkExitCall();
}

void gdcSetLine(int gdId, double lwd, int lty) {
	((call_gdcSetLine) callbacks[gdcSetLine_x])(gdId, lwd, lty);
    checkExitCall();
}

void gdcSetFont(int gdId, double cex, double ps, double lineheight, int fontface, const char* fontfamily) {
	((call_gdcSetFont) callbacks[gdcSetFont_x])(gdId, cex, ps, lineheight, fontface, ensure_string(fontfamily));
    checkExitCall();
}

void gdNewPage(int gdId, int deviceNumber, int pageNumber) {
	((call_gdNewPage) callbacks[gdNewPage_x])(gdId, deviceNumber, pageNumber);
    checkExitCall();
}

void gdActivate(int gdId) {
	((call_gdActivate) callbacks[gdActivate_x])(gdId);
    checkExitCall();
}

void gdCircle(int gdId, double x, double y, double r) {
	((call_gdCircle) callbacks[gdCircle_x])(gdId, x, y, r);
    checkExitCall();
}

void gdClip(int gdId, double x0, double x1, double y0, double y1) {
	((call_gdClip) callbacks[gdClip_x])(gdId, x0, x1, y0, y1);
    checkExitCall();
}

void gdDeactivate(int gdId) {
	((call_gdDeactivate) callbacks[gdDeactivate_x])(gdId);
    checkExitCall();
}

void gdHold(int gdId) {
	((call_gdHold) callbacks[gdHold_x])(gdId);
    checkExitCall();
}

void gdFlush(int gdId, int flush) {
	((call_gdFlush) callbacks[gdFlush_x])(gdId, flush);
    checkExitCall();
}

double* gdLocator(int gdId) {
	double *ret = ((call_gdLocator) callbacks[gdLocator_x])(gdId);
    checkExitCall();
    return ret;
}

void gdLine(int gdId, double x1, double y1, double x2, double y2) {
	((call_gdLine) callbacks[gdLine_x])(gdId, x1, y1, x2, y2);
    checkExitCall();
}

void gdMode(int gdId, int mode) {
	((call_gdMode) callbacks[gdMode_x])(gdId, mode);
    checkExitCall();
}

Rboolean gdOpen(int gdId, const char *name, double w, double h) {
	Rboolean result = ((call_gdOpen) callbacks[gdOpen_x])(gdId, ensure_string(name), w, h);
    checkExitCall();
    return result;
}

void gdClose(int gdId) {
	((call_gdClose) callbacks[gdClose_x])(gdId);
    checkExitCall();
}

void gdPath(int gdId, int npoly, int *nper, int n, double *x, double *y, Rboolean winding) {
	((call_gdPath) callbacks[gdPath_x])(gdId, npoly, nper, n, x, y, winding);
    checkExitCall();
}

void gdPolygon(int gdId, int n, double* x, double* y) {
	((call_gdPolygon) callbacks[gdPolygon_x])(gdId, n, x, y);
    checkExitCall();
}

void gdPolyline(int gdId, int n, double* x, double* y) {
	((call_gdPolyline) callbacks[gdPolyline_x])(gdId, n, x, y);
    checkExitCall();
}

void gdRect(int gdId, double x0, double y0, double x1, double y1) {
	((call_gdRect) callbacks[gdRect_x])(gdId, x0, y0, x1, y1);
    checkExitCall();
}

double* gdSize(int gdId) {
	double *ret = ((call_gdSize) callbacks[gdSize_x])(gdId);
    checkExitCall();
    return ret;
}
	
double getStrWidth(int gdId, const char* str) {
	double res = ((call_getStrWidth) callbacks[getStrWidth_x])(gdId, ensure_string(str));
    checkExitCall();
    return res;
}

void gdText(int gdId, double x, double y, const char* str, double rot, double hadj) {
	((call_gdText) callbacks[gdText_x])(gdId, x, y, ensure_string(str), rot, hadj);
    checkExitCall();
}

void gdRaster(int gdId, unsigned int *img, int img_w, int img_h, double x, double y, double w, double h, double rot, Rboolean interpolate) {
	((call_gdRaster) callbacks[gdRaster_x])(gdId, img_w, img_h, img, x, y, w, h, rot, interpolate);
    checkExitCall();
}

double* gdMetricInfo(int gdId, int ch) {
	double *res = ((call_gdMetricInfo) callbacks[gdMetricInfo_x])(gdId, ch);
    checkExitCall();
    return res;
}
