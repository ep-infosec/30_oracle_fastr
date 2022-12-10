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
public class TestBuiltin_tanh extends TestBase {

    @Test
    public void testtanh1() {
        assertEval("argv <- list(c(0.57459950307683, 1.3311607364495));tanh(argv[[1]]);");
    }

    @Test
    public void testtanh2() {
        assertEval("argv <- list(FALSE);tanh(argv[[1]]);");
    }

    @Test
    public void testtanh3() {
        assertEval("argv <- list(c(0.018063120710024, 0.202531388051386, 0.417573408622862, 1.63052300091743, 2.60085453772445, 2.75283670267494, 2.30083138197613, 1.47188976409943, 0.829803307993584, 0.295089115172324, 0.237719196109985, 0.617898787321681, 0.850777050382226, 0.516973890969527, 0.522699166681335, 0.850446724158497, 0.645479182912265, 0.193978409371909, 0.414456893353747, 0.492772947140595, 0.420563171733189, 0.369166401583374, 0.592867562934369, 1.21638206559229, 0.54564621330955, 0.672292186547141, 0.557193544541334, 0.112218530051911, -0.0391766542932368, 0.246991917518619, -0.0310729286667355, 0.100305401934259, 0.385595467685569, 0.347899688300561, 0.0900835492886662, -0.128526864819991));tanh(argv[[1]]);");
    }

    @Test
    public void testtanh4() {
        assertEval("argv <- list(c(-0.560475646552213-0.710406563699301i, -0.23017748948328+0.25688370915653i, 1.55870831414912-0.24669187846237i, 0.070508391424576-0.347542599397733i, 0.129287735160946-0.951618567265016i, 1.71506498688328-0.04502772480892i, 0.460916205989202-0.784904469457076i, -1.26506123460653-1.66794193658814i, -0.686852851893526-0.380226520287762i, -0.445661970099958+0.918996609060766i, 1.22408179743946-0.57534696260839i, 0.359813827057364+0.607964322225033i, 0.40077145059405-1.61788270828916i, 0.11068271594512-0.055561965524539i, -0.555841134754075+0.519407203943462i, 1.78691313680308+0.30115336216671i, 0.497850478229239+0.105676194148943i, -1.96661715662964-0.64070600830538i, 0.701355901563686-0.849704346033582i, -0.47279140772793-1.02412879060491i, -1.06782370598685+0.11764659710013i, -0.217974914658295-0.947474614184802i, -1.02600444830724-0.49055744370067i, -0.72889122929114-0.256092192198247i, -0.62503926784926+1.84386200523221i, -1.68669331074241-0.65194990169546i, 0.837787044494525+0.235386572284857i, 0.153373117836515+0.077960849563711i, -1.13813693701195-0.96185663413013i, 1.25381492106993-0.0713080861236i, 0.42646422147681+1.44455085842335i, -0.295071482992271+0.451504053079215i, 0.895125661045022+0.04123292199294i, 0.878133487533042-0.422496832339625i, 0.82158108163749-2.05324722154052i, 0.68864025410009+1.13133721341418i, 0.55391765353759-1.46064007092482i, -0.061911710576722+0.739947510877334i, -0.30596266373992+1.90910356921748i, -0.38047100101238-1.4438931609718i, -0.694706978920513+0.701784335374711i, -0.207917278019599-0.262197489402468i, -1.26539635156826-1.57214415914549i, 2.16895596533851-1.51466765378175i, 1.20796199830499-1.60153617357459i, -1.12310858320335-0.5309065221703i, -0.40288483529908-1.4617555849959i, -0.466655353623219+0.687916772975828i, 0.77996511833632+2.10010894052567i, -0.08336906647183-1.28703047603518i, 0.253318513994755+0.787738847475178i, -0.028546755348703+0.76904224100091i, -0.042870457291316+0.332202578950118i, 1.36860228401446-1.00837660827701i, -0.225770985659268-0.119452606630659i, 1.51647060442954-0.28039533517025i, -1.54875280423022+0.56298953322048i, 0.584613749636069-0.372438756103829i, 0.123854243844614+0.976973386685621i, 0.215941568743973-0.374580857767014i, 0.37963948275988+1.05271146557933i, -0.5023234531093-1.04917700666607i, -0.33320738366942-1.26015524475811i, -1.01857538310709+3.2410399349424i, -1.07179122647558-0.41685758816043i, 0.303528641404258+0.298227591540715i, 0.448209778629426+0.636569674033849i, 0.053004226730504-0.483780625708744i, 0.922267467879738+0.516862044313609i, 2.05008468562714+0.36896452738509i, -0.491031166056535-0.215380507641693i, -2.30916887564081+0.06529303352532i, 1.00573852446226-0.03406725373846i, -0.70920076258239+2.12845189901618i, -0.688008616467358-0.741336096272828i, 1.0255713696967-1.09599626707466i, -0.284773007051009+0.037788399171079i, -1.22071771225454+0.31048074944314i, 0.18130347974915+0.436523478910183i, -0.138891362439045-0.458365332711106i, 0.00576418589989-1.06332613397119i, 0.38528040112633+1.26318517608949i, -0.370660031792409-0.349650387953555i, 0.644376548518833-0.865512862653374i, -0.220486561818751-0.236279568941097i, 0.331781963915697-0.197175894348552i, 1.09683901314935+1.10992028971364i, 0.435181490833803+0.084737292197196i, -0.325931585531227+0.754053785184521i, 1.14880761845109-0.49929201717226i, 0.993503855962119+0.214445309581601i, 0.54839695950807-0.324685911490835i, 0.238731735111441+0.094583528173571i, -0.627906076039371-0.895363357977542i, 1.36065244853001-1.31080153332797i, -0.60025958714713+1.99721338474797i, 2.18733299301658+0.60070882367242i, 1.53261062618519-1.25127136162494i, -0.235700359100477-0.611165916680421i, -1.02642090030678-1.18548008459731i));tanh(argv[[1]]);");
    }
}
