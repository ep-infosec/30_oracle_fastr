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
package com.oracle.truffle.r.nodes.objects;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.api.profiles.ConditionProfile;
import com.oracle.truffle.api.profiles.ValueProfile;
import com.oracle.truffle.r.nodes.RASTUtils;
import com.oracle.truffle.r.nodes.access.variables.LocalReadVariableNode;
import com.oracle.truffle.r.nodes.access.variables.ReadVariableNode;
import com.oracle.truffle.r.nodes.helpers.InheritsCheckNode;
import com.oracle.truffle.r.runtime.RCaller;
import com.oracle.truffle.r.runtime.RRuntime;
import com.oracle.truffle.r.runtime.Utils;
import com.oracle.truffle.r.runtime.context.RContext;
import com.oracle.truffle.r.runtime.data.RFunction;
import com.oracle.truffle.r.runtime.data.RStringVector;
import com.oracle.truffle.r.runtime.env.REnvironment;
import com.oracle.truffle.r.runtime.nodes.RBaseNode;

// transcribed from /src/library/methods/src/methods_list_dispatch.c (R_dispatch_generic function)
public abstract class DispatchGeneric extends RBaseNode {

    public abstract Object executeObject(VirtualFrame frame, REnvironment mtable, RStringVector classes, RFunction fdef, String fname);

    private final ConditionProfile singleStringProfile = ConditionProfile.createBinaryProfile();
    private final ConditionProfile isDeferredProfile = ConditionProfile.createBinaryProfile();
    private final BranchProfile equalsMethodRequired = BranchProfile.create();
    @Child private LoadMethod loadMethod = LoadMethodNodeGen.create();
    @Child private ExecuteMethod executeMethod = new ExecuteMethod();
    @Child private InheritsCheckNode inheritsInternalDispatchCheckNode;

    @TruffleBoundary
    private static String createMultiDispatchString(RStringVector classes) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < classes.getLength(); i++) {
            if (i > 0) {
                sb.append('#');
            }
            sb.append(classes.getDataAt(i));
        }
        return sb.toString();
    }

    protected String createDispatchString(RStringVector classes) {
        if (singleStringProfile.profile(classes.getLength() == 1)) {
            return classes.getDataAt(0);
        } else {
            return createMultiDispatchString(classes);
        }
    }

    protected LocalReadVariableNode createTableRead(String dispatchString) {
        return LocalReadVariableNode.create(dispatchString, true);
    }

    private Object dispatchInternal(VirtualFrame frame, REnvironment mtable, RStringVector classes, RFunction fdef, String fname, RFunction f) {
        RFunction method = f;
        if (method == null) {
            // if method has not been found, it will be retrieved by the following R function call
            // and installed in the methods table so that the slow path does not have to be executed
            // again
            CompilerDirectives.transferToInterpreterAndInvalidate();
            RContext context = getRContext();
            REnvironment methodsEnv = REnvironment.getRegisteredNamespace(context, "methods");
            RFunction currentFunction = ReadVariableNode.lookupFunction(".InheritForDispatch", methodsEnv.getFrame(), true, true);
            method = (RFunction) context.getThisEngine().evalFunction(currentFunction, frame.materialize(), RCaller.create(frame, RASTUtils.getOriginalCall(this)), true, null, classes, fdef, mtable);
        }
        if (isDeferredProfile.profile(method.isBuiltin() || getInheritsInternalDispatchCheckNode().execute(method))) {
            return RRuntime.DEFERRED_DEFAULT_MARKER;
        }
        method = loadMethod.executeRFunction(frame, method, fname);
        return executeMethod.executeObject(frame, method, fname);
    }

    @SuppressWarnings("unused")
    @Specialization(guards = "equalClasses(classes, cachedClasses)")
    protected Object dispatchCached(VirtualFrame frame, REnvironment mtable, RStringVector classes, RFunction fdef, String fname,
                    @Cached("classes") RStringVector cachedClasses,
                    @Cached("createDispatchString(cachedClasses)") String dispatchString,
                    @Cached("createTableRead(dispatchString)") LocalReadVariableNode tableRead,
                    @Cached("createClassProfile()") ValueProfile frameAccessProfile) {
        RFunction method = (RFunction) tableRead.execute(frame, mtable.getFrame(frameAccessProfile));
        return dispatchInternal(frame, mtable, classes, fdef, fname, method);
    }

    @Specialization(replaces = "dispatchCached")
    protected Object dispatch(VirtualFrame frame, REnvironment mtable, RStringVector classes, RFunction fdef, String fname) {
        String dispatchString = createDispatchString(classes);
        RFunction method = (RFunction) mtable.get(dispatchString);
        return dispatchInternal(frame, mtable, classes, fdef, fname, method);
    }

    protected boolean equalClasses(RStringVector classes, RStringVector cachedClasses) {
        if (cachedClasses.getLength() == classes.getLength()) {
            for (int i = 0; i < cachedClasses.getLength(); i++) {
                // TODO: makes sure equality is good enough here, but it's for optimization only
                // anwyay
                if (!Utils.fastPathIdentityEquals(cachedClasses.getDataAt(i), classes.getDataAt(i))) {
                    equalsMethodRequired.enter();
                    return cachedClasses.getDataAt(i).equals(classes.getDataAt(i));
                }
            }
            return true;
        }
        return false;
    }

    private InheritsCheckNode getInheritsInternalDispatchCheckNode() {
        if (inheritsInternalDispatchCheckNode == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            inheritsInternalDispatchCheckNode = insert(InheritsCheckNode.create("internalDispatchMethod"));
        }
        return inheritsInternalDispatchCheckNode;
    }
}
