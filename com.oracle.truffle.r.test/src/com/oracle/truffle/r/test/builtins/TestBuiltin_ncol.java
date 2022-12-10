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

public class TestBuiltin_ncol extends TestBase {

    @Test
    public void testncol1() {
        assertEval("argv <- structure(list(x = structure(list(pop15 = c(29.35, 23.32,     23.8, 41.89, 42.19, 31.72, 39.74, 44.75, 46.64, 47.64, 24.42,     46.31, 27.84, 25.06, 23.31, 25.62, 46.05, 47.32, 34.03, 41.31,     31.16, 24.52, 27.01, 41.74, 21.8, 32.54, 25.95, 24.71, 32.61,     45.04, 43.56, 41.18, 44.19, 46.26, 28.96, 31.94, 31.92, 27.74,     21.44, 23.49, 43.42, 46.12, 23.27, 29.81, 46.4, 45.25, 41.12,     28.13, 43.69, 47.2), pop75 = c(2.87, 4.41, 4.43, 1.67, 0.83,     2.85, 1.34, 0.67, 1.06, 1.14, 3.93, 1.19, 2.37, 4.7, 3.35,     3.1, 0.87, 0.58, 3.08, 0.96, 4.19, 3.48, 1.91, 0.91, 3.73,     2.47, 3.67, 3.25, 3.17, 1.21, 1.2, 1.05, 1.28, 1.12, 2.85,     2.28, 1.52, 2.87, 4.54, 3.73, 1.08, 1.21, 4.46, 3.43, 0.9,     0.56, 1.73, 2.72, 2.07, 0.66), dpi = c(2329.68, 1507.99,     2108.47, 189.13, 728.47, 2982.88, 662.86, 289.52, 276.65,     471.24, 2496.53, 287.77, 1681.25, 2213.82, 2457.12, 870.85,     289.71, 232.44, 1900.1, 88.94, 1139.95, 1390, 1257.28, 207.68,     2449.39, 601.05, 2231.03, 1740.7, 1487.52, 325.54, 568.56,     220.56, 400.06, 152.01, 579.51, 651.11, 250.96, 768.79, 3299.49,     2630.96, 389.66, 249.87, 1813.93, 4001.89, 813.39, 138.33,     380.47, 766.54, 123.58, 242.69), ddpi = c(2.87, 3.93, 3.82,     0.22, 4.56, 2.43, 2.67, 6.51, 3.08, 2.8, 3.99, 2.19, 4.32,     4.52, 3.44, 6.28, 1.48, 3.19, 1.12, 1.54, 2.99, 3.54, 8.21,     5.81, 1.57, 8.12, 3.62, 7.66, 1.76, 2.48, 3.61, 1.03, 0.67,     2, 7.48, 2.19, 2, 4.35, 3.01, 2.7, 2.96, 1.13, 2.01, 2.45,     0.53, 5.14, 10.23, 1.88, 16.71, 5.08)), .Names = c('pop15',     'pop75', 'dpi', 'ddpi'), class = 'data.frame', row.names = c('Australia',     'Austria', 'Belgium', 'Bolivia', 'Brazil', 'Canada', 'Chile',     'China', 'Colombia', 'Costa Rica', 'Denmark', 'Ecuador',     'Finland', 'France', 'Germany', 'Greece', 'Guatamala', 'Honduras',     'Iceland', 'India', 'Ireland', 'Italy', 'Japan', 'Korea',     'Luxembourg', 'Malta', 'Norway', 'Netherlands', 'New Zealand',     'Nicaragua', 'Panama', 'Paraguay', 'Peru', 'Philippines',     'Portugal', 'South Africa', 'South Rhodesia', 'Spain', 'Sweden',     'Switzerland', 'Turkey', 'Tunisia', 'United Kingdom', 'United States',     'Venezuela', 'Zambia', 'Jamaica', 'Uruguay', 'Libya', 'Malaysia'))),     .Names = 'x');" +
                        "do.call('ncol', argv)");
    }
}