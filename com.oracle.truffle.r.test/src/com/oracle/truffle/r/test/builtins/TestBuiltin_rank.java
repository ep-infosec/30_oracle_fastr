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
 * Copyright (c) 2012-2014, Purdue University
 * Copyright (c) 2013, 2018, Oracle and/or its affiliates
 *
 * All rights reserved.
 */
package com.oracle.truffle.r.test.builtins;

import org.junit.Test;

import com.oracle.truffle.r.test.TestBase;

// Checkstyle: stop line length check
public class TestBuiltin_rank extends TestBase {

    @Test
    public void testrank1() {
        assertEval("argv <- list(c(1, 2, 3), 3L, 'average'); .Internal(rank(argv[[1]], argv[[2]], argv[[3]]))");
    }

    @Test
    public void testrank2() {
        assertEval("argv <- list(list(), 0L, 'average'); .Internal(rank(argv[[1]], argv[[2]], argv[[3]]))");
    }

    @Test
    public void testrank3() {
        assertEval("argv <- list(c(FALSE, FALSE), 2L, 'average'); .Internal(rank(argv[[1]], argv[[2]], argv[[3]]))");
    }

    @Test
    public void testrank4() {
        assertEval("argv <- list(c(2, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35, 36, 37, 38, 39, 40, 41, 42, 43, 44, 45, 46, 47, 48, 49, 50, 51, 52, 53, 54, 55, 56, 57, 58, 59, 60), 60L, 'average'); .Internal(rank(argv[[1]], argv[[2]], argv[[3]]))");
    }

    @Test
    public void testrank5() {
        assertEval("argv <- list(structure(c(9.96, 84.84, 93.4, 33.77, 5.16, 90.57, 92.85, 97.16, 97.67, 91.38, 98.61, 8.52, 2.27, 4.43, 2.82, 24.2, 3.3, 12.11, 2.15, 2.84, 5.23, 4.52, 15.14, 4.2, 5.23, 2.56, 7.72, 18.46, 6.1, 99.71, 99.68, 100, 98.96, 98.22, 99.06, 99.46, 96.83, 5.62, 13.79, 11.22, 16.92, 4.97, 8.65, 42.34, 50.43, 58.33), .Names = c('Courtelary', 'Delemont', 'Franches-Mnt', 'Moutier', 'Neuveville', 'Porrentruy', 'Broye', 'Glane', 'Gruyere', 'Sarine', 'Veveyse', 'Aigle', 'Aubonne', 'Avenches', 'Cossonay', 'Echallens', 'Grandson', 'Lausanne', 'La Vallee', 'Lavaux', 'Morges', 'Moudon', 'Nyone', 'Orbe', 'Payerne', 'Paysd\\'enhaut', 'Rolle', 'Vevey', 'Yverdon', 'Conthey', 'Entremont', 'Herens', 'Martigwy', 'Monthey', 'St Maurice', 'Sierre', 'Sion', 'Boudry', 'La Chauxdfnd', 'Le Locle', 'Neuchatel', 'Val de Ruz', 'ValdeTravers', 'V. De Geneve', 'Rive Droite', 'Rive Gauche')), 46L, 'average'); .Internal(rank(argv[[1]], argv[[2]], argv[[3]]))");
    }

    @Test
    public void testrank6() {
        assertEval("argv <- list(structure(c(3, 1, 4, 1, 5, 9, 2, 6, 5, 3, 5), .Names = c('a', 'b', 'c', 'd', 'e', 'f', 'g', 'h', 'i', 'j', 'k')), 11L, 'max'); .Internal(rank(argv[[1]], argv[[2]], argv[[3]]))");
    }

    @Test
    public void testrank7() {
        assertEval("argv <- list(c('9', '9', '8', '7', '6', '5', '4', '3', '2', '1'), 10L, 'min'); .Internal(rank(argv[[1]], argv[[2]], argv[[3]]))");
    }

    @Test
    public void testrank8() {
        assertEval("argv <- list(c(2, 1, 3, 4, 5), 5L, 'average'); .Internal(rank(argv[[1]], argv[[2]], argv[[3]]))");
    }

    @Test
    public void testrank9() {
        assertEval("argv <- list(structure(c('Tukey', 'Venables', 'Tierney', 'Ripley', 'Ripley', 'McNeil', 'R Core'), class = 'AsIs'), 7L, 'min'); .Internal(rank(argv[[1]], argv[[2]], argv[[3]]))");
    }

    @Test
    public void testrank10() {
        assertEval("argv <- list(c(0.0244473121385049, 0.0208069652959635, 0.00198363254553387, -0.0529221973952693, 0.0164890605562422, -0.00149317802331189, -0.00414458668937225, -0.0391260369607497, -0.0127200995448093, 0.0111183888673723, 0.03614459302116, -0.00273443474452932, 0.0103131254237995, -0.00143136127438401, -0.0366335514444555, -0.0110399906877088, -0.0104891914308669, -0.00157789861665007, 0.0292636842429564, 0.0203025627349537, -0.0043767777488601, -0.00674011381520054, 0.0185411324740319, 0.0148087639526725, -0.0183227857094651, -0.018821306675337, 0.00969887758262181, 0.0204450782737623, -0.00298871658962484, 0.0234398759771181, 0.0105907055191967, -0.0162815763859567, 0.00907471699575067, -0.0300441479633801, 0.0381223507996197, 0.0526840550960561, -0.00976909588473167, -0.0277768375074461, 0.0151561006764977, -0.00359282193318711, 0.0638896025542924, -0.0010438914218908, 0.0183489539666666, 0.00074493402929487, -0.0197731007347187, 0.00502239164768132, -0.048016837368221, 0.0389877686476984, 0.00407695805281634, 0.057797414062711, 0.0126498543239424, -0.0188865172686347, 0.0162469917717659, -0.0248495524200794, -0.0333500780212535, 0.00775326717655591, -0.0117927765447241, 2.9405377320478e-05, 0.00197768259858777, -0.0156828699257579, -0.0151281440045609, -0.00359612097150966, 0.0313403370108415, -0.0405310449252812, 0.0158005934542395, 0.00885739072926609, 0.0282813640022565, -0.00809212452705879, 0.00984351260718323, 0.00710555853883393, -0.0144325170007544, 0.0321325880127445, 0.0308698841001781, 0.0186275986571656, 0.0422141110037264, 0.0148572667758066, -0.033960845128472, -0.0152504283054679, -0.0325780457387957, -0.0125937520151832, -0.0165034507562293, 0.00112039744236678, -0.0242330078671155, 0.00420399766652167, -0.0174137422806726, 0.047014676147193, 0.0190663795644171, 0.0242131244754732, 0.0102203815371289, 0.0447504856843389, -0.0169123288643312, -0.0122810127527625, 0.0381026258511537, -0.0173103031132602, -0.00551689511296685, -0.0104497655309428, -0.00851268571043338, -0.00742517743166594, 0.0131467615666842, -0.00471747595278646, -1.01191492422851, 2.68607765034082, -0.429158817093737, -0.359113060086774, -0.200381482427124, 1.42533261410281, -0.147128808252653, -0.0752683429340958, -1.36332095751131, -0.648540544492638, 0.12032088086903, -1.17778897251933, 1.06299238526514, -3.03678816357357, 0.613115721579531, -3.07289964707517, -0.601952253673221, -1.05655980889001, -1.304189561362, -0.113793555694785, -3.82871885136002, 2.35316662403712, -3.32994487242401, -0.927060802944771, -2.23184021008569, -1.5016380023869, 4.17433309125669, 0.0347912393865033, -2.57260106086865, -3.28121106883716, 0.900374202545311, -0.037119665429276, -0.636136749087689, -1.8587242949074, -2.97492062028297, -2.15038459323136, 2.00005760742783, -1.24253338959365, -2.76885369476898, 3.73858124484716, 0.850200754744896, -0.477294201826066, 2.11696609741804, 1.77284530274987, -1.23848609646229, 4.41220492908093, -0.51005406028203, -2.84898930042562, -0.288799203908439, 0.41507667846469, 4.61595679811872, 0.211604735787423, 0.913997610846827, -0.154305870713062, -0.668001684733089, -0.0694520566225524, 1.57527921126032, 4.15049001730457, 2.05478487752754, 2.41581679677341, -2.46264684311609, 1.96779114010676, 0.439849607321303, -2.93450005818449, 1.04204548529628, -0.317509209432032, 2.92917462393959, -1.53216399920933, -0.860423507857093, -1.85221899475487, -0.354207922873081, 0.804023558972676, -1.46349634623921, 1.66074633596335, -2.41616557260893, -2.09596882561548, 2.88231541368856, -2.0316949306093, 0.82394942463503, -0.762152102217839, 0.818803679301868, 3.37774657240809, 3.17317686688394, -0.661815601365533, -4.57047107058493, 4.99532317966833, 1.33413233353099, 1.0826546719274, -0.0267990462918174, 1.02021684590585, -0.328751663539334, 0.841389286509026, -0.800493487955288, -2.74041575509492, 1.97567653490976, 3.03949005099909, -0.617481138451227, -2.50657951121538, 1.28448261135565, -0.0894182737879582), 200L, 'average'); .Internal(rank(argv[[1]], argv[[2]], argv[[3]]))");
    }

    @Test
    public void testrank11() {
        assertEval("argv <- list(structure(c(4, 7, 6, 0, 0, 2, 4, 9, 3, 6, 0, 1, 5.5, 0.5, 4.5, 5.5, 0.5, 2.5, 0.5, 0.5, 2.5, 4.5, 9.5, 3.5, 1.5, 0.5, 5.5, 0.5, 1.5, 0.5, 0.5, 0.5, 1.5, 1.5, 0.5, 2.5, 2, 0, 7, 1, 1, 2, 0, 0, 0, 0, 3, 1, 0, 2, 0, 2, 0, 3, 2, 2, 0, 1, 3, 1, 4, 6, 0, 7, 0, 1, 2, 5, 11, 11, 9, 2), .Dim = 72L, .Dimnames = list(c('A', 'A', 'A', 'A', 'A', 'A', 'A', 'A', 'A', 'A', 'A', 'A', 'B', 'B', 'B', 'B', 'B', 'B', 'B', 'B', 'B', 'B', 'B', 'B', 'C', 'C', 'C', 'C', 'C', 'C', 'C', 'C', 'C', 'C', 'C', 'C', 'D', 'D', 'D', 'D', 'D', 'D', 'D', 'D', 'D', 'D', 'D', 'D', 'E', 'E', 'E', 'E', 'E', 'E', 'E', 'E', 'E', 'E', 'E', 'E', 'F', 'F', 'F', 'F', 'F', 'F', 'F', 'F', 'F', 'F', 'F', 'F'))), 72L, 'average'); .Internal(rank(argv[[1]], argv[[2]], argv[[3]]))");
    }

    @Test
    public void testRank() {
        assertEval("{ rank(c(10,100,100,1000)) }");
        assertEval("{ rank(c(1000,100,100,100, 10)) }");
        assertEval("{ rank(c(a=2,b=1,c=3,40)) }");
        assertEval("{ rank(c(a=2,b=1,c=3,d=NA,e=40), na.last=NA) }");
        assertEval("{ rank(c(a=2,b=1,c=3,d=NA,e=40), na.last=\"keep\") }");
        assertEval("{ rank(c(a=2,b=1,c=3,d=NA,e=40), na.last=TRUE) }");
        assertEval("{ rank(c(a=2,b=1,c=3,d=NA,e=40), na.last=FALSE) }");
        assertEval("{ rank(c(a=1,b=1,c=3,d=NA,e=3), na.last=FALSE, ties.method=\"max\") }");
        assertEval("{ rank(c(a=1,b=1,c=3,d=NA,e=3), na.last=NA, ties.method=\"min\") }");
        assertEval("{ rank(c(1000, 100, 100, NA, 1, 20), ties.method=\"first\") }");
    }

    @Test
    public void testArgsCasts() {
        assertEval(".Internal(rank(c(1,2), -3L, 'max'))");
        assertEval(".Internal(rank(c(1,2), 2L, 'something'))");
        assertEval(".Internal(rank(as.raw(42), 42L, 'max'))");
    }
}
