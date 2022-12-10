/*
 * Copyright (c) 2014, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.r.runtime.ffi;

import com.oracle.truffle.r.runtime.REnvVars;
import com.oracle.truffle.r.runtime.RRuntime;
import com.oracle.truffle.r.runtime.TempPathName;
import com.oracle.truffle.r.runtime.context.RContext;
import com.oracle.truffle.r.runtime.data.CharSXPWrapper;
import com.oracle.truffle.r.runtime.data.RDataFactory;
import com.oracle.truffle.r.runtime.data.RNull;
import com.oracle.truffle.r.runtime.data.RSymbol;
import com.oracle.truffle.r.runtime.data.RUnboundValue;
import com.oracle.truffle.r.runtime.env.REnvironment;

/**
 * Note: regenerate the C glue code upon any change in this enum.
 */
public enum RFFIVariables {
    R_Home("dummy string"),
    R_TempDir("dummy string"), // Set later
    Sys_TempDir("dummy string"), // Set later
    R_GlobalEnv(null), // Set later
    R_BaseEnv(null), // Set later
    R_BaseNamespace(null), // Set later
    R_NamespaceRegistry(null), // Set later
    R_Interactive(0), // Set later
    R_NilValue(RNull.instance),
    R_UnboundValue(RUnboundValue.instance),
    R_MissingArg(RSymbol.MISSING),
    R_EmptyEnv(REnvironment.emptyEnv()),
    R_Srcref(null),
    R_Bracket2Symbol(RDataFactory.createSymbol("[[")),
    R_BracketSymbol(RDataFactory.createSymbol("[")),
    R_BraceSymbol(RDataFactory.createSymbol("{")),
    R_DoubleColonSymbol(RDataFactory.createSymbol("::")),
    R_ClassSymbol(RDataFactory.createSymbol("class")),
    R_DeviceSymbol(RDataFactory.createSymbol(".Device")),
    R_DevicesSymbol(RDataFactory.createSymbol(".Devices")),
    R_DimNamesSymbol(RDataFactory.createSymbol("dimnames")),
    R_DimSymbol(RDataFactory.createSymbol("dim")),
    R_DollarSymbol(RDataFactory.createSymbol("$")),
    R_DotsSymbol(RDataFactory.createSymbol("...")),
    R_DropSymbol(RDataFactory.createSymbol("drop")),
    R_LastvalueSymbol(RDataFactory.createSymbol(".Last.value")),
    R_LevelsSymbol(RDataFactory.createSymbol("levels")),
    R_ModeSymbol(RDataFactory.createSymbol("mode")),
    R_NameSymbol(RDataFactory.createSymbol("name")),
    R_NamesSymbol(RDataFactory.createSymbol("names")),
    R_NaRmSymbol(RDataFactory.createSymbol("na.rm")),
    R_PackageSymbol(RDataFactory.createSymbol("package")),
    R_QuoteSymbol(RDataFactory.createSymbol("quote")),
    R_RowNamesSymbol(RDataFactory.createSymbol("row.names")),
    R_SeedsSymbol(RDataFactory.createSymbol(".Random.seed")),
    R_SourceSymbol(RDataFactory.createSymbol("source")),
    R_TspSymbol(RDataFactory.createSymbol("tsp")),
    R_dot_defined(RDataFactory.createSymbol(".defined")),
    R_dot_Method(RDataFactory.createSymbol(".Method")),
    R_dot_target(RDataFactory.createSymbol(".target")),
    R_dot_packageName(RDataFactory.createSymbol(".packageName")),
    R_dot_Generic(RDataFactory.createSymbol(".Generic")),
    R_SrcrefSymbol(RDataFactory.createSymbol("srcref")),
    R_SrcfileSymbol(RDataFactory.createSymbol("srcfile")),
    R_NaString(CharSXPWrapper.create(RRuntime.STRING_NA)),
    R_NaN(Double.NaN),
    R_PosInf(Double.POSITIVE_INFINITY),
    R_NegInf(Double.NEGATIVE_INFINITY),
    R_NaReal(RRuntime.DOUBLE_NA),
    R_NaInt(RRuntime.INT_NA),
    R_TrueValue(RDataFactory.getPermanent().createLogicalVectorFromScalar(RRuntime.LOGICAL_TRUE)),
    R_FalseValue(RDataFactory.getPermanent().createLogicalVectorFromScalar(RRuntime.LOGICAL_FALSE)),
    R_LogicalNAValue(RDataFactory.getPermanent().createLogicalVectorFromScalar(RRuntime.LOGICAL_NA)),
    R_BlankString(CharSXPWrapper.create("")),
    R_BlankScalarString(RDataFactory.createStringVectorFromScalar("")),
    R_BaseSymbol(RDataFactory.createSymbol("base")),
    R_NamespaceEnvSymbol(RDataFactory.createSymbol(".__NAMESPACE__.")),
    R_RestartToken(RDataFactory.createSymbol("")),
    R_SortListSymbol(RDataFactory.createSymbol("sort.list")),
    R_SpecSymbol(RDataFactory.createSymbol("spec")),
    R_TripleColonSymbol(RDataFactory.createSymbol(":::")),
    R_PreviousSymbol(RDataFactory.createSymbol("previous"));

    private Object value;
    public final boolean alwaysUpCall;

    RFFIVariables(Object value, boolean alwaysUpCall) {
        this.value = value;
        this.alwaysUpCall = alwaysUpCall;
    }

    RFFIVariables(Object value) {
        this(value, false);
    }

    public Object getValue() {
        return value;
    }

    /**
     * Sets {@link #R_TempDir} for the initial context.
     */
    public static RFFIVariables[] initialize(RContext context) {
        R_Home.value = REnvVars.rHome(context);
        R_TempDir.value = TempPathName.tempDirPath(context);
        Sys_TempDir.value = TempPathName.tempDirPath(context);
        R_GlobalEnv.value = RContext.getInstance().stateREnvironment.getGlobalEnv();
        R_BaseEnv.value = RContext.getInstance().stateREnvironment.getBaseEnv();
        R_BaseNamespace.value = RContext.getInstance().stateREnvironment.getBaseNamespace();
        R_NamespaceRegistry.value = RContext.getInstance().stateREnvironment.getNamespaceRegistry();
        R_Interactive.value = context.isInteractive() ? 1 : 0;
        return values();
    }
}
