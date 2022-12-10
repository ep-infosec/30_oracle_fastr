/*
 * Copyright (c) 2013, 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.r.nodes.builtin;

import static com.oracle.truffle.r.runtime.context.FastROptions.LoadPackagesNativeCode;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.TruffleFile;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.r.nodes.builtin.base.BasePackage;
import com.oracle.truffle.r.nodes.builtin.base.BaseVariables;
import com.oracle.truffle.r.runtime.RDeparse;
import com.oracle.truffle.r.runtime.REnvVars;
import com.oracle.truffle.r.runtime.RInternalError;
import com.oracle.truffle.r.runtime.RRuntime;
import com.oracle.truffle.r.runtime.RSource;
import com.oracle.truffle.r.runtime.RSuicide;
import com.oracle.truffle.r.runtime.builtins.RBuiltin;
import com.oracle.truffle.r.runtime.builtins.RBuiltinDescriptor;
import com.oracle.truffle.r.runtime.builtins.RBuiltinKind;
import com.oracle.truffle.r.runtime.builtins.RBuiltinLookup;
import com.oracle.truffle.r.runtime.context.Engine.ParseException;
import com.oracle.truffle.r.runtime.context.RContext;
import com.oracle.truffle.r.runtime.context.TruffleRLanguage;
import com.oracle.truffle.r.runtime.data.RDataFactory;
import com.oracle.truffle.r.runtime.data.RFunction;
import com.oracle.truffle.r.runtime.env.REnvironment;
import com.oracle.truffle.r.runtime.env.REnvironment.PutException;
import com.oracle.truffle.r.runtime.env.frame.FrameIndex;
import com.oracle.truffle.r.runtime.env.frame.FrameSlotChangeMonitor;

/**
 * Support for loading the base package and also optional overrides for the packages provided with
 * the system.
 */
public final class RBuiltinPackages implements RBuiltinLookup {

    private static final RBuiltinPackages instance = new RBuiltinPackages();
    private static RBuiltinPackage basePackage;

    public static RBuiltinPackages getInstance() {
        return instance;
    }

    public static void loadBase(RContext context, MaterializedFrame baseFrame) {
        basePackage = new BasePackage(context);
        RBuiltinPackage pkg = basePackage;
        REnvironment baseEnv = REnvironment.baseEnv(context);
        BaseVariables.initialize(baseEnv, context);
        /*
         * All the RBuiltin PRIMITIVE methods that were created earlier need to be added to the
         * environment so that lookups through the environment work as expected.
         */
        Map<String, RBuiltinFactory> builtins = pkg.getBuiltins();
        for (Map.Entry<String, RBuiltinFactory> entrySet : builtins.entrySet()) {
            String methodName = entrySet.getKey();
            RBuiltinFactory builtinFactory = entrySet.getValue();
            if (builtinFactory.getKind() != RBuiltinKind.INTERNAL) {
                RFunction function = createFunction(context.getLanguage(), builtinFactory, methodName);
                try {
                    baseEnv.put(methodName, function);
                    baseEnv.lockBinding(methodName);
                } catch (PutException ex) {
                    RSuicide.rSuicide("failed to install builtin function: " + methodName);
                }
            }
        }
        // Now "load" the package
        TruffleFile baseDirPath = REnvVars.getRHomeTruffleFile(context).resolve("library").resolve("base");
        TruffleFile basePathbase = baseDirPath.resolve("R").resolve("base");
        Source baseSource = null;
        try {
            baseSource = RSource.fromFileName(context, basePathbase.toString(), true);
        } catch (IOException ex) {
            throw RSuicide.rSuicide(String.format("unable to open the base package %s", basePathbase));
        }
        // Load the (stub) DLL for base
        if (RContext.getInstance().getOption(LoadPackagesNativeCode)) {
            String path = baseDirPath.resolve("libs").resolve("base.so").toString();
            Source loadSource = RSource.fromTextInternal(".Internal(dyn.load(" + RRuntime.escapeString(path, false, true) + ", TRUE, TRUE, \"\"))", RSource.Internal.R_IMPL);
            RContext.getEngine().parseAndEval(loadSource, baseFrame, false);
        }

        // Any RBuiltinKind.SUBSTITUTE functions installed above should not be overridden
        try {
            RContext.getInstance().setLoadingBase(true);
            try {
                RContext.getEngine().parseAndEval(baseSource, baseFrame, false);
            } catch (ParseException e) {
                throw new RInternalError(e, "error while parsing base source from %s", baseSource.getName());
            }
            // forcibly clear last.warnings during startup:
            int frameIndex = FrameSlotChangeMonitor.getIndexOfIdentifier(baseFrame.getFrameDescriptor(), "last.warning");
            if (FrameIndex.isInitializedIndex(frameIndex)) {
                FrameSlotChangeMonitor.setObject(baseFrame, frameIndex, null);
            }
        } finally {
            RContext.getInstance().setLoadingBase(false);
        }
        pkg.loadOverrides(baseFrame);
    }

    public static void loadDefaultPackageOverrides(RContext context) {
        ArrayList<Source> componentList = RBuiltinPackage.getRFiles(context, context.getNamespaceName());
        if (componentList.size() > 0) {
            /*
             * Only the overriding code can know which environment to update, package or namespace.
             */
            REnvironment env = REnvironment.baseEnv(context);
            for (Source source : componentList) {
                try {
                    RContext.getEngine().parseAndEval(source, env.getFrame(), false);
                } catch (ParseException e) {
                    throw new RInternalError(e, "error while parsing default package override from %s", source.getName());
                }
            }
        }
    }

    @Override
    public RFunction lookupBuiltin(TruffleRLanguage language, String methodName) {
        CompilerAsserts.neverPartOfCompilation();
        RFunction function = language.getBuiltinFunctionCache().get(methodName);
        if (function != null) {
            return function;
        }

        RBuiltinFactory builtin = lookupBuiltinDescriptor(methodName);
        if (builtin == null) {
            return null;
        }
        return createFunction(language, builtin, methodName);
    }

    private static RootCallTarget createArgumentsCallTarget(TruffleRLanguage language, RBuiltinFactory builtin) {
        CompilerAsserts.neverPartOfCompilation();

        FrameDescriptor frameDescriptor = FrameSlotChangeMonitor.createFunctionFrameDescriptor(builtin.getName());
        RBuiltinRootNode root = new RBuiltinRootNode(language, builtin, frameDescriptor, null);
        return root.getCallTarget();
    }

    private static RFunction createFunction(TruffleRLanguage language, RBuiltinFactory builtinFactory, String methodName) {
        try {
            HashMap<String, RFunction> cache = language.getBuiltinFunctionCache();
            RFunction function = cache.get(methodName);
            if (function != null) {
                return function;
            }
            RootCallTarget callTarget = createArgumentsCallTarget(language, builtinFactory);
            function = RDataFactory.createFunction(builtinFactory.getName(), "base", callTarget, builtinFactory, null);
            cache.put(methodName, function);
            return function;
        } catch (Throwable t) {
            throw new RuntimeException("error while creating builtin " + methodName + " / " + builtinFactory, t);
        }
    }

    @Override
    public RBuiltinFactory lookupBuiltinDescriptor(String name) {
        CompilerAsserts.neverPartOfCompilation();
        assert basePackage != null;
        return basePackage.lookupByName(name);
    }

    /**
     * Used by {@link RDeparse} to detect whether a symbol is a builtin (or special), i.e. not an
     * {@link RBuiltinKind#INTERNAL}. N.B. special functions are not explicitly denoted currently,
     * only by virtue of the {@link RBuiltin#nonEvalArgs} attribute.
     */
    @Override
    public boolean isPrimitiveBuiltin(String name) {
        assert basePackage != null;
        RBuiltinPackage pkg = basePackage;
        RBuiltinDescriptor rbf = pkg.lookupByName(name);
        return rbf != null && rbf.getKind() != RBuiltinKind.INTERNAL;
    }
}
