/*
 * Copyright (c) 1995, 1996, 1997  Robert Gentleman and Ross Ihaka
 * Copyright (c) 1995-2014, The R Core Team
 * Copyright (c) 2002-2008, The R Foundation
 * Copyright (c) 2015, 2022, Oracle and/or its affiliates
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, a copy is available at
 * https://www.R-project.org/Licenses/
 */
package com.oracle.truffle.r.nodes.builtin.base;

import static com.oracle.truffle.r.nodes.builtin.CastBuilder.Predef.instanceOf;
import static com.oracle.truffle.r.nodes.builtin.CastBuilder.Predef.lengthGt;
import static com.oracle.truffle.r.nodes.builtin.CastBuilder.Predef.stringValue;
import static com.oracle.truffle.r.nodes.builtin.casts.fluent.CastNodeBuilder.newCastBuilder;
import static com.oracle.truffle.r.runtime.RVisibility.CUSTOM;
import static com.oracle.truffle.r.runtime.builtins.RBehavior.COMPLEX;
import static com.oracle.truffle.r.runtime.builtins.RBuiltinKind.PRIMITIVE;

import java.util.function.Function;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.object.DynamicObject;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.api.profiles.ConditionProfile;
import com.oracle.truffle.r.nodes.access.variables.LocalReadVariableNode;
import com.oracle.truffle.r.nodes.access.variables.ReadVariableNode;
import com.oracle.truffle.r.nodes.builtin.RBuiltinNode;
import com.oracle.truffle.r.nodes.function.ClassHierarchyScalarNode;
import com.oracle.truffle.r.nodes.objects.CollectGenericArgumentsNode;
import com.oracle.truffle.r.nodes.objects.CollectGenericArgumentsNodeGen;
import com.oracle.truffle.r.nodes.objects.DispatchGeneric;
import com.oracle.truffle.r.nodes.objects.DispatchGenericNodeGen;
import com.oracle.truffle.r.runtime.RArguments;
import com.oracle.truffle.r.runtime.RCaller;
import com.oracle.truffle.r.runtime.RError;
import com.oracle.truffle.r.runtime.RRuntime;
import com.oracle.truffle.r.runtime.builtins.RBuiltin;
import com.oracle.truffle.r.runtime.context.RContext;
import com.oracle.truffle.r.runtime.data.RFunction;
import com.oracle.truffle.r.runtime.data.RList;
import com.oracle.truffle.r.runtime.data.RMissing;
import com.oracle.truffle.r.runtime.data.RNull;
import com.oracle.truffle.r.runtime.data.RStringVector;
import com.oracle.truffle.r.runtime.data.nodes.attributes.GetFixedPropertyNode;
import com.oracle.truffle.r.runtime.env.REnvironment;
import com.oracle.truffle.r.runtime.nodes.unary.CastNode;

// transcribed from /src/library/methods/src/methods_list_dispatch.c (R_dispatch_generic function)

@RBuiltin(name = "standardGeneric", visibility = CUSTOM, kind = PRIMITIVE, parameterNames = {"f", "fdef"}, behavior = COMPLEX)
public abstract class StandardGeneric extends RBuiltinNode.Arg2 {

    // TODO: for now, we always go through generic dispatch

    @Child private GetFixedPropertyNode genericAttrAccess;
    @Child private FrameFunctions.SysFunction sysFunction;
    @Child private LocalReadVariableNode readMTableFirst = LocalReadVariableNode.create(RRuntime.DOT_ALL_MTABLE, true);
    @Child private LocalReadVariableNode readSigLength = LocalReadVariableNode.create(RRuntime.DOT_SIG_LENGTH, true);
    @Child private LocalReadVariableNode readSigARgs = LocalReadVariableNode.create(RRuntime.DOT_SIG_ARGS, true);
    @Child private CollectGenericArgumentsNode collectArgumentsNode;
    @Child private DispatchGeneric dispatchGeneric = DispatchGenericNodeGen.create();

    @Child private CastNode castIntScalar = newCastBuilder().asIntegerVector().findFirst(RRuntime.INT_NA).buildCastNode();
    @Child private CastNode castStringScalar = newCastBuilder().asStringVector().findFirst(RRuntime.STRING_NA).buildCastNode();

    private final BranchProfile noGenFunFound = BranchProfile.create();
    private final ConditionProfile sameNamesProfile = ConditionProfile.createBinaryProfile();
    private final ConditionProfile isBuiltinProfile = ConditionProfile.createBinaryProfile();
    private final ConditionProfile isDeferredProfile = ConditionProfile.createBinaryProfile();

    static {
        Casts casts = new Casts(StandardGeneric.class);
        casts.arg("f").defaultError(RError.Message.GENERIC, "argument to 'standardGeneric' must be a non-empty character string").mustBe(
                        stringValue()).asStringVector().findFirst().mustBe(lengthGt(0));
        Function<Object, Object> argClass = ClassHierarchyScalarNode::get;
        casts.arg("fdef").defaultError(RError.Message.EXPECTED_GENERIC, argClass).allowMissing().asAttributable(true, true, true).mustBe(instanceOf(RFunction.class));
    }

    private Object stdGenericInternal(VirtualFrame frame, String fname, RFunction fdef) {
        RFunction def = fdef;
        RContext context = getRContext();
        if (isBuiltinProfile.profile(def.isBuiltin())) {
            def = context.getPrimitiveMethodsInfo().getPrimGeneric(def.getRBuiltin().getPrimMethodIndex());
            if (isDeferredProfile.profile(def == null)) {
                return RRuntime.DEFERRED_DEFAULT_MARKER;
            }
        }
        MaterializedFrame fnFrame = def.getEnclosingFrame();
        REnvironment mtable = (REnvironment) readMTableFirst.execute(frame, fnFrame);
        if (mtable == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            // mtable can be null the first time around, but the following call will initialize it
            // and this slow path should not be executed again
            REnvironment methodsEnv = REnvironment.getRegisteredNamespace(context, "methods");
            RFunction currentFunction = ReadVariableNode.lookupFunction(".getMethodsTable", methodsEnv.getFrame(), true, true);
            mtable = (REnvironment) context.getThisEngine().evalFunction(currentFunction, frame.materialize(), RCaller.create(frame, getOriginalCall()), true, null, def);
        }
        RList sigArgs = (RList) readSigARgs.execute(null, fnFrame);
        int sigLength = (int) castIntScalar.doCast(readSigLength.execute(null, fnFrame));
        if (sigLength > sigArgs.getLength()) {
            throw error(RError.Message.GENERIC, "'.SigArgs' is shorter than '.SigLength' says it should be");
        }
        if (collectArgumentsNode == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            collectArgumentsNode = insert(CollectGenericArgumentsNodeGen.create(sigLength));
        }
        RStringVector classes = collectArgumentsNode.execute(frame, sigLength);
        Object ret = dispatchGeneric.executeObject(frame, mtable, classes, def, fname);
        return ret;
    }

    private Object getFunction(VirtualFrame frame, String fname, Object fnObj) {
        if (fnObj == RNull.instance) {
            noGenFunFound.enter();
            return null;
        }
        RFunction fn = (RFunction) fnObj;
        Object genObj = null;
        DynamicObject attributes = fn.getAttributes();
        if (attributes == null) {
            noGenFunFound.enter();
            return null;
        }
        if (genericAttrAccess == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            genericAttrAccess = insert(GetFixedPropertyNode.create(RRuntime.GENERIC_ATTR_KEY));
        }
        genObj = genericAttrAccess.execute(attributes);
        if (genObj == null) {
            noGenFunFound.enter();
            return null;
        }
        String gen = (String) castStringScalar.doCast(genObj);
        if (sameNamesProfile.profile(gen == fname)) {
            return stdGenericInternal(frame, fname, fn);
        } else {
            // in many cases == is good enough (and this will be the fastest path), but it's not
            // always sufficient
            if (!gen.equals(fname)) {
                noGenFunFound.enter();
                return null;
            }
            return stdGenericInternal(frame, fname, fn);
        }
    }

    @Specialization
    protected Object stdGeneric(VirtualFrame frame, String fname, RFunction fdef) {
        return stdGenericInternal(frame, fname, fdef);
    }

    @Specialization
    protected Object stdGeneric(VirtualFrame frame, String fname, @SuppressWarnings("unused") RMissing fdef) {
        int n = RArguments.getDepth(frame);
        Object fnObj = RArguments.getFunction(frame);
        fnObj = getFunction(frame, fname, fnObj);
        if (fnObj != null) {
            return fnObj;
        }
        if (sysFunction == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            sysFunction = insert(FrameFunctionsFactory.SysFunctionNodeGen.create());
            RError.performanceWarning("sys.frame usage in standardGeneric");
        }
        // TODO: GNU R counts to (i < n) - does their equivalent of getDepth return a different
        // value
        // TODO: shouldn't we count from n to 0?
        for (int i = 0; i <= n; i++) {
            fnObj = sysFunction.executeObject(frame, i);
            fnObj = getFunction(frame, fname, fnObj);
            if (fnObj != null) {
                return fnObj;
            }
        }
        throw error(RError.Message.STD_GENERIC_WRONG_CALL, fname);
    }
}
