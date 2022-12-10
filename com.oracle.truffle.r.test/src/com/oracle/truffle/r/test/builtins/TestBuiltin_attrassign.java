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
 * Copyright (c) 2014, 2019, Oracle and/or its affiliates
 *
 * All rights reserved.
 */
package com.oracle.truffle.r.test.builtins;

import org.junit.Test;

import com.oracle.truffle.r.test.TestBase;

// Checkstyle: stop line length check
public class TestBuiltin_attrassign extends TestBase {

    @Test
    public void testattrassign1() {
        assertEval("argv <- list(structure(1, foo = structure(list(a = 'a'), .Names = 'a')), 'foo', value = structure(list(a = 'a'), .Names = 'a'));`attr<-`(argv[[1]],argv[[2]],argv[[3]]);");
    }

    @Test
    public void testattrassign2() {
        assertEval("argv <- list(structure(c(-99, 123, 0, -27, 0, 136, 3.5527136788005e-14, 0, -89, -59, 54.9999999999999, -260, 30, 47, 0), .Dim = c(5L, 3L)), 'dimnames', value = NULL);`attr<-`(argv[[1]],argv[[2]],argv[[3]]);");
    }

    @Test
    public void testattrassign3() {
        assertEval("argv <- list(structure(c(1, 0, 0, 0, NA, 1, 0, 0, 0, 7, 1, 0, 3, 0, 0, 1), .Dim = c(4L, 4L)), 'dimnames', value = NULL);`attr<-`(argv[[1]],argv[[2]],argv[[3]]);");
    }

    @Test
    public void testattrassign4() {
        assertEval("argv <- list(structure(c(51.4483279898675, 51.4483279898675, 103.874299440142, 103.874299440142, 135.181084465022, 135.181084465022, 165.022949241512, 165.022949241512, 190.564205234787, 190.564205234787, 200.417426252912, 200.417426252912), gradient = structure(c(0.242941154845256, 0.242941154845256, 0.490498782967253, 0.490498782967253, 0.638330730196604, 0.638330730196604, 0.779245262792577, 0.779245262792577, 0.899852140987463, 0.899852140987463, 0.946379462411014, 0.946379462411014, -624.945810835795, -624.945810835795, -849.17029094943, -849.17029094943, -784.456730502965, -784.456730502965, -584.515233856856, -584.515233856856, -306.213585850174, -306.213585850174, -172.428123740936, -172.428123740936), .Dim = c(12L, 2L), .Dimnames = list(NULL, c('Vm', 'K'))), hessian = structure(c(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, -2.95102023587733, -2.95102023587733, -4.00981760153928, -4.00981760153928, -3.70423746466663, -3.70423746466663, -2.76010536174851, -2.76010536174851, -1.44595334935665, -1.44595334935665, -0.814212806248508, -0.814212806248508, -2.95102023587733, -2.95102023587733, -4.00981760153928, -4.00981760153928, -3.70423746466663, -3.70423746466663, -2.76010536174851, -2.76010536174851, -1.44595334935665, -1.44595334935665, -0.814212806248508, -0.814212806248508, 15182.5057000153, 15182.5057000153, 13883.8998080881, 13883.8998080881, 9104.41522890179, 9104.41522890179, 4140.73388193682, 4140.73388193682, 984.096252952598, 984.096252952598, 296.69533645543, 296.69533645543), .Dim = c(12L, 2L, 2L), .Dimnames = list(NULL, c('Vm', 'K'), c('Vm', 'K')))), 'hessian', value = structure(c(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, -2.95102023587733, -2.95102023587733, -4.00981760153928, -4.00981760153928, -3.70423746466663, -3.70423746466663, -2.76010536174851, -2.76010536174851, -1.44595334935665, -1.44595334935665, -0.814212806248508, -0.814212806248508, -2.95102023587733, -2.95102023587733, -4.00981760153928, -4.00981760153928, -3.70423746466663, -3.70423746466663, -2.76010536174851, -2.76010536174851, -1.44595334935665, -1.44595334935665, -0.814212806248508, -0.814212806248508, 15182.5057000153, 15182.5057000153, 13883.8998080881, 13883.8998080881, 9104.41522890179, 9104.41522890179, 4140.73388193682, 4140.73388193682, 984.096252952598, 984.096252952598, 296.69533645543, 296.69533645543), .Dim = c(12L, 2L, 2L), .Dimnames = list(NULL, c('Vm', 'K'), c('Vm', 'K'))));`attr<-`(argv[[1]],argv[[2]],argv[[3]]);");
    }

    @Test
    public void testattrassign5() {
        assertEval("argv <- list(structure(c(NA, NA, NA, NA, NA, NA, NA, NA, NA, NA, NA, NA), .Dim = 3:4), 'dimnames', value = NULL);`attr<-`(argv[[1]],argv[[2]],argv[[3]]);");
    }

    @Test
    public void testattrassign6() {
        assertEval(Output.IgnoreWhitespace,
                        "argv <- list(structure(c('o', 'p', 'v', 'i', 'r', 'w', 'b', 'm', 'f', 's'), date = structure(1224086400, class = c('POSIXct', 'POSIXt'), tzone = '')), 'date', value = structure(1224086400, class = c('POSIXct', 'POSIXt'), tzone = ''));`attr<-`(argv[[1]],argv[[2]],argv[[3]]);");
    }

    @Test
    public void testattrassign7() {
        assertEval("argv <- list(structure(list(structure(list(structure(13L, label = 'Illinois', members = 1L, height = 0, leaf = TRUE), structure(32L, label = 'New York', members = 1L, height = 0, leaf = TRUE)), members = 2L, midpoint = 0.5, height = 6.23698645180507), structure(list(structure(22L, label = 'Michigan', members = 1L, height = 0, leaf = TRUE), structure(28L, label = 'Nevada', members = 1L, height = 0, leaf = TRUE)), members = 2L, midpoint = 0.5, height = 13.2973681606549)), members = 4L, midpoint = 1.5, height = 18.4173313943456, class = 'dendrogram', edgePar = structure(list(    p.col = 'plum'), .Names = 'p.col'), edgetext = '4 members'), 'edgetext', value = '4 members');`attr<-`(argv[[1]],argv[[2]],argv[[3]]);");
    }

    @Test
    public void testattrassign8() {
        assertEval("argv <- list(structure(4, '`Object created`' = 'Sat Dec  7 00:26:20 2013'), 'Object created', value = 'Sat Dec  7 00:26:20 2013');`attr<-`(argv[[1]],argv[[2]],argv[[3]]);");
    }

    @Test
    public void testattrassign9() {
        assertEval("argv <- list(structure(1:3, .Names = c('a', 'b', 'c')), 'names', value = list('a', 'b', 'c'));`attr<-`(argv[[1]],argv[[2]],argv[[3]]);");
    }

    @Test
    public void testattrassign10() {
        assertEval("argv <- list(structure(list(a = 1:3, b = structure(1:3, .Label = c('a', 'b', 'c'), class = 'factor')), .Names = c('a', 'b'), row.names = c(NA, -3L), class = 'data.frame', foo = 10), 'foo', value = 10);`attr<-`(argv[[1]],argv[[2]],argv[[3]]);");
    }

    @Test
    public void testattrassign11() {
        assertEval("argv <- list(structure(c(50.566057038188, 50.566057038188, 102.811023011144, 102.811023011144, 134.361651733496, 134.361651733496, 164.684698598908, 164.684698598908, 190.832887571642, 190.832887571642, 200.968775266774, 200.968775266774), gradient = structure(c(0.237752464043283, 0.237752464043283, 0.483398854556726, 0.483398854556726, 0.631744210319564, 0.631744210319564, 0.774317697987532, 0.774317697987532, 0.897261758147131, 0.897261758147131, 0.944918870762493, 0.944918870762493, -601.11023288912, -601.11023288912, -828.312179323201, -828.312179323201, -771.656323378267, -771.656323378267, -579.628530513078, -579.628530513078, -305.762593240759, -305.762593240759, -172.635625621456, -172.635625621456), .Dim = c(12L, 2L), .Dimnames = list(NULL, c('Vm', 'K')))), 'gradient', value = structure(c(0.237752464043283, 0.237752464043283, 0.483398854556726, 0.483398854556726, 0.631744210319564, 0.631744210319564, 0.774317697987532, 0.774317697987532, 0.897261758147131, 0.897261758147131, 0.944918870762493, 0.944918870762493, -601.11023288912, -601.11023288912, -828.312179323201, -828.312179323201, -771.656323378267, -771.656323378267, -579.628530513078, -579.628530513078, -305.762593240759, -305.762593240759, -172.635625621456, -172.635625621456), .Dim = c(12L, 2L), .Dimnames = list(NULL, c('Vm', 'K'))));`attr<-`(argv[[1]],argv[[2]],argv[[3]]);");
    }

    @Test
    public void testattrassign12() {
        assertEval("argv <- list(structure(numeric(0), .Dim = c(0L, 20L)), 'dimnames', value = NULL);`attr<-`(argv[[1]],argv[[2]],argv[[3]]);");
    }

    @Test
    public void testattrassign13() {
        assertEval("argv <- list(structure(c(FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, TRUE, FALSE, TRUE, TRUE, FALSE, TRUE, TRUE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, TRUE, TRUE, TRUE, FALSE, TRUE, TRUE, FALSE, TRUE, FALSE, TRUE, TRUE, TRUE, FALSE, TRUE, FALSE, TRUE, FALSE, TRUE, TRUE, FALSE, FALSE, TRUE, TRUE, FALSE, FALSE, TRUE, FALSE, TRUE, TRUE, FALSE, TRUE, TRUE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, TRUE, TRUE, TRUE, FALSE, TRUE, TRUE, FALSE, TRUE, FALSE, TRUE, TRUE, TRUE, FALSE, TRUE, FALSE, TRUE, FALSE, TRUE, TRUE, FALSE, FALSE, TRUE, TRUE, FALSE, FALSE, FALSE, TRUE, FALSE, TRUE, TRUE, TRUE, FALSE, TRUE, FALSE, TRUE, FALSE, TRUE, TRUE, FALSE, FALSE, TRUE, TRUE, FALSE, FALSE, FALSE, FALSE, FALSE, TRUE, TRUE, TRUE, FALSE, FALSE, FALSE, FALSE, FALSE, TRUE, TRUE, FALSE, FALSE, TRUE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, TRUE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, TRUE, TRUE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, TRUE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, TRUE, TRUE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, TRUE, TRUE, TRUE, FALSE, FALSE, FALSE, FALSE, FALSE, TRUE, TRUE, FALSE, FALSE, TRUE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, TRUE, TRUE, TRUE, FALSE, FALSE, FALSE, FALSE, FALSE, TRUE, TRUE, FALSE, FALSE, TRUE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, TRUE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, TRUE, TRUE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, TRUE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, TRUE, TRUE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE), .Dim = c(19L, 22L)), 'dimnames', value = NULL);`attr<-`(argv[[1]],argv[[2]],argv[[3]]);");
    }

    @Test
    public void testattrassign14() {
        assertEval("argv <- list(structure(logical(0), .Dim = c(0L, 20L)), 'dimnames', value = NULL);`attr<-`(argv[[1]],argv[[2]],argv[[3]]);");
    }

    @Test
    public void testattrassign15() {
        assertEval("argv <- list(structure(c('8189464 kB', '52252 kB', '237240 kB', '6704452 kB', '5868 kB', '3947300 kB', '3641700 kB', '521488 kB', '126264 kB', '3425812 kB', '3515436 kB', '0 kB', '0 kB', '20603324 kB', '20546156 kB', '1964 kB', '0 kB', '645292 kB', '12420 kB', '76 kB', '343696 kB', '303404 kB', '40292 kB', '2344 kB', '8464 kB', '0 kB', '0 kB', '0 kB', '24698056 kB', '1053308 kB', '34359738367 kB', '301080 kB', '34359386948 kB', '0 kB', '0', '0', '0', '0', '2048 kB', '7488 kB', '8376320 kB'), .Names = c('MemTotal', 'MemFree', 'Buffers', 'Cached', 'SwapCached', 'Active', 'Inactive', 'Active(anon)', 'Inactive(anon)', 'Active(file)', 'Inactive(file)', 'Unevictable', 'Mlocked', 'SwapTotal', 'SwapFree', 'Dirty', 'Writeback', 'AnonPages', 'Mapped', 'Shmem', 'Slab', 'SReclaimable', 'SUnreclaim', 'KernelStack', 'PageTables', 'NFS_Unstable', 'Bounce', 'WritebackTmp', 'CommitLimit', 'Committed_AS', 'VmallocTotal', 'VmallocUsed', 'VmallocChunk', 'HardwareCorrupted', 'HugePages_Total', 'HugePages_Free', 'HugePages_Rsvd', 'HugePages_Surp', 'Hugepagesize', 'DirectMap4k', 'DirectMap2M'), Name = '/proc/meminfo'), 'Name', value = '/proc/meminfo');`attr<-`(argv[[1]],argv[[2]],argv[[3]]);");
    }

    @Test
    public void testattrassign16() {
        assertEval("argv <- list(structure(c(0, -187, -34, 0, 165, 0, -95, 121, 107, 0, 41, 0, 0, 93, 0), .Dim = c(5L, 3L)), 'dimnames', value = NULL);`attr<-`(argv[[1]],argv[[2]],argv[[3]]);");
    }

    @Test
    public void testRefCount() {
        assertEval("x <- c(1,2); attr(x, \"foo\") <- c(\"a\",\"b\"); y <- x; attr(x,\"foo\")[[1]] <- \"c\"; y");
        assertEval("x <- c(1,2,3); y <- 42; attr(y, 'at') <- x; x[[1]] <- 2; attr(y, 'at')");
    }

    @Test
    public void testArgsCasts() {
        assertEval("x<-42; attr(x, NULL) <- NULL");
        assertEval("x<-42; attr(x, 42) <- NULL");
    }

    @Test
    public void testNamesAssign() {
        String[] values = new String[]{"c(3,9)", "1:2", "as.pairlist(list(1,2))", "quote(foo(3))"};
        // cast of names preserves attributes
        assertEval(template("{ x <- %0; attr(x, 'names') <- structure(c(1,2), names=c('q','r'), abc=3); names(x) }", values));
        // but making them longer will drop custom attributes, but keep and enlarge the names...
        assertEval(Ignored.Unimplemented, template("{ x <- %0; attr(x, 'names') <- structure(1, names=c('q'), abc=3); names(x) }", values));
        // names will be made longer to match the owner length
        assertEval(template("{ x <- %0; attr(x, 'names') <- 1; names(x) }", values));
        // No conversion via as.character like with names<-
        assertEval("{ as.character.namesAssignCls <- function(x) x+1; x <- 1; attr(x, 'names') <- structure(2, class='namesAssignCls'); names(x) }");
    }

    @Test
    public void testSetAttrOnNull() {
        assertEval("x<-NULL; attr(x, 'a') <- NULL");
        assertEval("x<-NULL; attr(x, 'a') <- 42");
    }

    @Test
    public void testattrassignTsp() {
        assertEval("x<-42; attr(x, 'tsp') <- 1");
        assertEval("x<-42; attr(x, 'tsp') <- c(1, 2)");
        assertEval("x<-42; attr(x, 'tsp') <- c(1, 1, 1)");
        assertEval("x<-42; attr(x, 'tsp') <- NULL");
        assertEval("x<-42; attr(x, 'tsp') <- NA");
        assertEval("x<-c(); attr(x, 'tsp') <- c(1, 1, 1)");
        assertEval("x<-NULL; attr(x, 'tsp') <- c(1, 1, 1)");
        assertEval("x<-NA; attr(x, 'tsp') <- c(1, 1, 1)");
    }

    @Test
    public void testattrassignComment() {
        assertEval("x<-42; attr(x, 'comment') <- 1");
        assertEval("x<-42; attr(x, 'comment') <- c(1, 2)");
        assertEval("x<-42; attr(x, 'comment') <- 'a'");
        assertEval("x<-42; attr(x, 'comment') <- c('a', 'b')");
        assertEval("x<-42; attr(x, 'comment') <- c('a', NA)");
        assertEval("x<-42; attr(x, 'comment') <- c(NA, 'a', NA)");
        assertEval("x<-42; attr(x, 'comment') <- c(NA)");
        assertEval("x<-42; attr(x, 'comment') <- c(NA, NA)");
        assertEval("x<-42; attr(x, 'comment') <- NULL");
        assertEval("x<-42; attr(x, 'comment') <- NA");
        assertEval("x<-c(); attr(x, 'comment') <- 'a'");
        assertEval("x<-NULL; attr(x, 'comment') <- 'a'");
        assertEval("x<-NA; attr(x, 'comment') <- 'a'");
    }

}
