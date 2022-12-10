/*
 * Copyright (c) 1995-2012, The R Core Team
 * Copyright (c) 2003, The R Foundation
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
package com.oracle.truffle.r.library.methods;

import static com.oracle.truffle.r.nodes.builtin.CastBuilder.Predef.instanceOf;
import static com.oracle.truffle.r.nodes.builtin.CastBuilder.Predef.lengthGt;
import static com.oracle.truffle.r.nodes.builtin.CastBuilder.Predef.lengthGte;
import static com.oracle.truffle.r.nodes.builtin.CastBuilder.Predef.singleElement;
import static com.oracle.truffle.r.nodes.builtin.CastBuilder.Predef.stringValue;
import static com.oracle.truffle.r.nodes.builtin.CastBuilder.Predef.toBoolean;

import java.util.function.Function;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.r.library.methods.MethodsListDispatchFactory.GetGenericInternalNodeGen;
import com.oracle.truffle.r.nodes.access.AccessSlotNode;
import com.oracle.truffle.r.nodes.access.AccessSlotNodeGen;
import com.oracle.truffle.r.nodes.access.variables.LocalReadVariableNode;
import com.oracle.truffle.r.nodes.builtin.NodeWithArgumentCasts.Casts;
import com.oracle.truffle.r.nodes.builtin.RExternalBuiltinNode;
import com.oracle.truffle.r.nodes.builtin.casts.fluent.PreinitialPhaseBuilder;
import com.oracle.truffle.r.nodes.function.ClassHierarchyScalarNode;
import com.oracle.truffle.r.nodes.function.ClassHierarchyScalarNodeGen;
import com.oracle.truffle.r.nodes.function.PromiseHelperNode;
import com.oracle.truffle.r.nodes.function.RCallNode;
import com.oracle.truffle.r.nodes.helpers.GetFromEnvironment;
import com.oracle.truffle.r.runtime.ArgumentsSignature;
import com.oracle.truffle.r.runtime.DSLConfig;
import com.oracle.truffle.r.runtime.PrimitiveMethodsInfo;
import com.oracle.truffle.r.runtime.PrimitiveMethodsInfo.MethodCode;
import com.oracle.truffle.r.runtime.RError;
import com.oracle.truffle.r.runtime.RError.Message;
import com.oracle.truffle.r.runtime.RInternalError;
import com.oracle.truffle.r.runtime.RRuntime;
import com.oracle.truffle.r.runtime.context.RContext;
import com.oracle.truffle.r.runtime.context.RContext.ContextKind;
import com.oracle.truffle.r.runtime.data.Closure;
import com.oracle.truffle.r.runtime.data.RAttributable;
import com.oracle.truffle.r.runtime.data.RBaseObject;
import com.oracle.truffle.r.runtime.data.RDataFactory;
import com.oracle.truffle.r.runtime.data.RExternalPtr;
import com.oracle.truffle.r.runtime.data.RFunction;
import com.oracle.truffle.r.runtime.data.RNull;
import com.oracle.truffle.r.runtime.data.RPairList;
import com.oracle.truffle.r.runtime.data.RPromise;
import com.oracle.truffle.r.runtime.data.RS4Object;
import com.oracle.truffle.r.runtime.data.RStringVector;
import com.oracle.truffle.r.runtime.data.RSymbol;
import com.oracle.truffle.r.runtime.data.VectorDataLibrary;
import com.oracle.truffle.r.runtime.data.nodes.attributes.GetFixedAttributeNode;
import com.oracle.truffle.r.runtime.env.REnvironment;
import com.oracle.truffle.r.runtime.env.frame.FrameIndex;
import com.oracle.truffle.r.runtime.env.frame.FrameSlotChangeMonitor;
import com.oracle.truffle.r.runtime.ffi.DLL;
import com.oracle.truffle.r.runtime.nodes.RBaseNode;
import com.oracle.truffle.r.runtime.nodes.RNode;
import com.oracle.truffle.r.runtime.nodes.RSyntaxCall;
import com.oracle.truffle.r.runtime.nodes.RSyntaxNode;
import com.oracle.truffle.r.runtime.nodes.unary.CastToVectorNode;
import com.oracle.truffle.r.runtime.nodes.unary.CastToVectorNodeGen;

// Transcribed (unless otherwise noted) from src/library/methods/methods_list_dispatch.c

public class MethodsListDispatch {

    private static void checkSingleString(Casts casts, int argNum, String argName, String msg, boolean nonEmpty, Function<Object, String> clsHierFn,
                    Function<Object, Integer> vecLenFn, boolean allowSymbol) {

        PreinitialPhaseBuilder builder = casts.arg(argNum, argName).defaultError(Message.SINGLE_STRING_WRONG_TYPE, msg, clsHierFn);
        if (allowSymbol) {
            builder.returnIf(instanceOf(RSymbol.class));
        }
        checkSingleString(builder, msg, nonEmpty, vecLenFn);
    }

    private static void checkSingleString(PreinitialPhaseBuilder builder, String msg, boolean nonEmpty, Function<Object, Integer> vecLenFn) {
        //@formatter:off
        builder.mustBe(stringValue()).
                asStringVector().
                mustBe(singleElement(), RError.Message.SINGLE_STRING_TOO_LONG, msg, vecLenFn).
                findFirst().
                mustBe(nonEmpty ? lengthGt(0) : lengthGte(0), RError.Message.NON_EMPTY_STRING, msg);
        //@formatter:on
    }

    private static void checkSingleString(Casts casts, int argNum, String argName, String msg, boolean nonEmpty, Function<Object, String> clsHierFn,
                    Function<Object, Integer> vecLenFn) {
        checkSingleString(casts, argNum, argName, msg, nonEmpty, clsHierFn, vecLenFn, false);
    }

    public abstract static class R_initMethodDispatch extends RExternalBuiltinNode.Arg1 {

        static {
            Casts.noCasts(R_initMethodDispatch.class);
        }

        @Specialization
        @TruffleBoundary
        protected REnvironment initMethodDispatch(REnvironment env) {
            getRContext().setMethodTableDispatchOn(true);
            // TBD what should we actually do here
            return env;
        }

        @Fallback
        protected Object initMethodFallback(@SuppressWarnings("unused") Object x) {
            return RNull.instance;
        }
    }

    public abstract static class R_methodsPackageMetaName extends RExternalBuiltinNode.Arg3 {

        static {
            Casts casts = new Casts(R_methodsPackageMetaName.class);
            Function<Object, String> clsHierFn = ClassHierarchyScalarNode::get;
            Function<Object, Integer> vecLenFn = arg -> ((RStringVector) arg).getLength();

            checkSingleString(casts, 0, "prefix", "The internal prefix (e.g., \"C\") for a meta-data object", true, clsHierFn, vecLenFn);
            checkSingleString(casts, 1, "name", "The name of the object (e.g,. a class or generic function) to find in the meta-data", false, clsHierFn, vecLenFn);
            checkSingleString(casts, 2, "pkg", "The name of the package for a meta-data object", false, clsHierFn, vecLenFn);
        }

        @Specialization
        @TruffleBoundary
        protected String callMethodsPackageMetaName(String prefix, String name, String pkg) {
            if (pkg.length() == 0) {
                return ".__" + prefix + "__" + name;
            } else {
                return ".__" + prefix + "__" + name + ":" + pkg;
            }
        }
    }

    public abstract static class R_getClassFromCache extends RExternalBuiltinNode.Arg2 {

        static {
            Casts casts = new Casts(R_getClassFromCache.class);
            casts.arg(0, "klass").defaultError(RError.Message.GENERIC, "class should be either a character-string name or a class definition").mustBe(stringValue().or(instanceOf(RS4Object.class)));
            casts.arg(1, "table").mustNotBeNull(RError.Message.USE_NULL_ENV_DEFUNCT).mustBe(instanceOf(REnvironment.class));
        }

        protected GetFixedAttributeNode createPckgAttrAccess() {
            return GetFixedAttributeNode.createFor(RRuntime.PCKG_ATTR_KEY);
        }

        @Specialization
        protected Object callGetClassFromCache(RStringVector klass, REnvironment table,
                        @Cached("create()") GetFromEnvironment get,
                        @Cached("createPckgAttrAccess()") GetFixedAttributeNode klassPckgAttrAccess,
                        @Cached("createPckgAttrAccess()") GetFixedAttributeNode valPckgAttrAccess) {
            if (klass.getLength() == 0) {
                return RNull.instance;
            }
            String klassString = klass.getDataAt(0);

            if (klassString.length() == 0) {
                throw error(RError.Message.ZERO_LENGTH_VARIABLE);
            }

            Object value = get.execute(null, table, klassString);
            if (value == null) {
                return RNull.instance;
            } else {
                Object pckgAttrObj = klassPckgAttrAccess.execute(klass);
                String pckgAttr = RRuntime.asStringLengthOne(pckgAttrObj);
                if (pckgAttr != null && value instanceof RAttributable) {
                    RAttributable attributableValue = (RAttributable) value;
                    Object valAttrObj = valPckgAttrAccess.execute(attributableValue);
                    String valAttr = RRuntime.asStringLengthOne(valAttrObj);
                    // GNUR uses == to compare strings here
                    if (valAttr != null && !valAttr.equals(pckgAttr)) {
                        return RNull.instance;
                    }
                }
                return value;
            }
        }

        @Specialization
        protected RS4Object callGetClassFromCache(RS4Object klass, @SuppressWarnings("unused") REnvironment table) {
            return klass;
        }

        @Fallback
        protected RS4Object callGetClassFromCache(@SuppressWarnings("unused") Object klass, @SuppressWarnings("unused") Object table) {
            throw error(Message.GENERIC, "class should be either a character-string name or a class definition");
        }
    }

    public abstract static class R_set_method_dispatch extends RExternalBuiltinNode.Arg1 {

        static {
            Casts casts = new Casts(R_set_method_dispatch.class);
            casts.arg(0).asLogicalVector().findFirst(RRuntime.LOGICAL_NA);
        }

        @Specialization
        @TruffleBoundary
        protected Object callSetMethodDispatch(byte onOff) {
            boolean prev = getRContext().isMethodTableDispatchOn();

            if (onOff == RRuntime.LOGICAL_NA) {
                return RRuntime.asLogical(prev);
            }
            boolean value = RRuntime.fromLogical(onOff);
            if (!value) {
                warning(Message.GENERIC, "FastR does not support R_set_method_dispatch(FALSE) yet. S4 dispatch may not work correctly.");
            }
            // StandardGeneric, the default one (true case) is currently implemented in FastR,
            // the other one is in GnuR implemented by R_standardGeneric and is not implemented
            // in FastR yet.
            getRContext().setMethodTableDispatchOn(value);
            return RRuntime.asLogical(prev);
        }
    }

    public abstract static class R_M_setPrimitiveMethods extends RExternalBuiltinNode.Arg5 {
        @Child private AccessSlotNode accessSlotNode;

        static {
            Casts casts = new Casts(R_M_setPrimitiveMethods.class);
            casts.arg(0, "fname").asStringVector().findFirst();
            casts.arg(1, "op");
            casts.arg(2, "code").mustBe(stringValue()).asStringVector().findFirst();
            casts.arg(3, "fundef");
            casts.arg(4, "mlist");
        }

        private AccessSlotNode initAccessSlotNode() {
            if (accessSlotNode == null) {
                accessSlotNode = insert(AccessSlotNodeGen.create(true));
            }
            return accessSlotNode;
        }

        @Specialization
        @TruffleBoundary
        protected Object setPrimitiveMethods(String fnameString, Object op, String codeVecString, Object fundefObj, Object mlist) {
            RBaseObject fundef = (RBaseObject) fundefObj;

            if (op == RNull.instance) {
                byte value = RRuntime.asLogical(getRContext().allowPrimitiveMethods());
                if (codeVecString.length() > 0) {
                    if (codeVecString.charAt(0) == 'c' || codeVecString.charAt(0) == 'C') {
                        getRContext().setAllowPrimitiveMethods(false);
                    } else if (codeVecString.charAt(0) == 's' || codeVecString.charAt(0) == 'S') {
                        getRContext().setAllowPrimitiveMethods(true);
                    }
                }
                return value;
            }

            Object opx = op;
            if ((op instanceof RFunction) && !((RFunction) op).isBuiltin()) {
                String internalName = RRuntime.asString(initAccessSlotNode().executeAccess(op, "internal"));
                opx = getRContext().lookupBuiltin(internalName);
                if (opx == null) {
                    throw error(RError.Message.GENERIC, "'internal' slot does not name an internal function: " + internalName);
                }
            }

            setPrimitiveMethodsInternal(opx, codeVecString, fundef, mlist);
            return fnameString;
        }

        private void setPrimitiveMethodsInternal(Object op, String codeVec, RBaseObject fundef, Object mlist) {
            MethodCode code;
            if (codeVec.charAt(0) == 'c') {
                code = MethodCode.NO_METHODS;
            } else if (codeVec.charAt(0) == 'r') {
                code = MethodCode.NEEDS_RESET;
            } else if (codeVec.startsWith("se")) {
                code = MethodCode.HAS_METHODS;
            } else if (codeVec.startsWith("su")) {
                code = MethodCode.SUPPRESSED;
            } else {
                throw error(RError.Message.INVALID_PRIM_METHOD_CODE, codeVec);
            }
            if (!(op instanceof RFunction) || !((RFunction) op).isBuiltin()) {
                throw error(RError.Message.GENERIC, "invalid object: must be a primitive function");
            }
            int primMethodIndex = ((RFunction) op).getRBuiltin().getPrimMethodIndex();
            assert primMethodIndex != PrimitiveMethodsInfo.INVALID_INDEX;

            PrimitiveMethodsInfo primMethodsInfo = getRContext().getPrimitiveMethodsInfo();
            if (primMethodIndex >= primMethodsInfo.getSize()) {
                primMethodsInfo = primMethodsInfo.resize(primMethodIndex + 1);
            }
            primMethodsInfo.setPrimMethodCode(primMethodIndex, code);
            RFunction value = primMethodsInfo.getPrimGeneric(primMethodIndex);
            if (code != MethodCode.SUPPRESSED) {
                assert fundef != null; // explicitly checked in GNU R
                if (code == MethodCode.NO_METHODS && value != null) {
                    primMethodsInfo.setPrimGeneric(primMethodIndex, null);
                    primMethodsInfo.setPrimMethodList(primMethodIndex, null);
                } else if (fundef != RNull.instance && (value == null || getRContext().getKind() == ContextKind.SHARE_NOTHING)) {
                    // If the context kind is SHARE_NOTHING, primMethodsInfo must also be updated.
                    // Otherwise, the standard generic dispatcher (see StandardGeneric) running in a
                    // child context would get an invalid method table for a given primitive
                    // generic function obtained from the enclosing environment of that generic
                    // primitive function from the initial context. NB: The setMethod function, if
                    // called from a child context, uses the enclosing environment of the generic
                    // function from the current context to update the method table.
                    if (!(fundef instanceof RFunction)) {
                        throw error(RError.Message.PRIM_GENERIC_NOT_FUNCTION, fundef.getRType().getName());
                    }
                    primMethodsInfo.setPrimGeneric(primMethodIndex, (RFunction) fundef);
                }
            }
            if (code == MethodCode.HAS_METHODS) {
                assert mlist != null; // explicitly checked in GNU R
                if (mlist != RNull.instance) {
                    primMethodsInfo.setPrimMethodList(primMethodIndex, (REnvironment) mlist);
                }
            }
        }
    }

    @ImportStatic(DSLConfig.class)
    public abstract static class R_identC extends RExternalBuiltinNode.Arg2 {

        static {
            Casts.noCasts(R_identC.class);
        }

        @Specialization
        protected byte identC(String e1, String e2) {
            return RRuntime.asLogical(e1.equals(e2));
        }

        @Specialization(limit = "getTypedVectorDataLibraryCacheSize()")
        protected byte identC(RStringVector e1, RStringVector e2,
                        @CachedLibrary("e1.getData()") VectorDataLibrary e1Lib,
                        @CachedLibrary("e2.getData()") VectorDataLibrary e2Lib) {
            Object e1Data = e1.getData();
            Object e2Data = e2.getData();
            return RRuntime.asLogical(e1Lib.getLength(e1Data) == 1 && e2Lib.getLength(e2Data) == 1 && e1Lib.getStringAt(e1Data, 0).equals(e2Lib.getStringAt(e2Data, 0)));
        }

        @SuppressWarnings("unused")
        @Fallback
        protected byte identC(Object e1, Object e2) {
            return RRuntime.LOGICAL_FALSE;
        }
    }

    public abstract static class R_getGeneric extends RExternalBuiltinNode.Arg4 {

        @Child private GetGenericInternal getGenericInternal = GetGenericInternalNodeGen.create();

        static {
            Casts casts = new Casts(R_getGeneric.class);
            Function<Object, String> clsHierFn = ClassHierarchyScalarNode::get;
            Function<Object, Integer> vecLenFn = arg -> ((RStringVector) arg).getLength();

            checkSingleString(casts, 0, "f", "The argument \"f\" to getGeneric", true, clsHierFn, vecLenFn, true);

            casts.arg(1, "mustFind").asLogicalVector().findFirst(RRuntime.LOGICAL_FALSE).map(toBoolean());

            casts.arg(2, "env").mustBe(instanceOf(REnvironment.class));

            checkSingleString(casts, 3, "package", "The argument \"package\" to getGeneric", false, clsHierFn, vecLenFn);
        }

        @Specialization
        protected Object getGeneric(RSymbol name, boolean mustFind, REnvironment env, String pckg) {
            return getGeneric(name.getName(), mustFind, env, pckg);
        }

        @Specialization
        protected Object getGeneric(String name, boolean mustFind, REnvironment env, String pckg) {
            Object value = getGenericInternal.executeObject(name, env, pckg);
            if (value == RNull.instance) {
                if (mustFind) {
                    if (env == getRContext().stateREnvironment.getGlobalEnv()) {
                        throw error(RError.Message.NO_GENERIC_FUN, name);
                    } else {
                        throw error(RError.Message.NO_GENERIC_FUN_IN_ENV, name);
                    }
                }
            }
            return value;
        }
    }

    abstract static class GetGenericInternal extends RBaseNode {

        public abstract Object executeObject(String name, REnvironment rho, String pckg);

        @Child private CastToVectorNode castToVector = CastToVectorNodeGen.create(false);
        @Child private ClassHierarchyScalarNode classHierarchyNode = ClassHierarchyScalarNodeGen.create();
        @Child private GetFixedAttributeNode getGenericAttrNode = GetFixedAttributeNode.createFor(RRuntime.GENERIC_ATTR_KEY);
        @Child private GetFixedAttributeNode getPckgAttrNode = GetFixedAttributeNode.createFor(RRuntime.PCKG_ATTR_KEY);

        @Specialization
        protected Object getGeneric(String name, REnvironment env, String pckg) {
            REnvironment rho = env;
            RAttributable generic = null;
            while (rho != null) {
                // TODO: make it faster
                MaterializedFrame currentFrame = rho.getFrame();
                if (currentFrame == null) {
                    break;
                }
                FrameDescriptor currentFrameDesc = currentFrame.getFrameDescriptor();
                Object o = slotRead(currentFrame, currentFrameDesc, name);
                if (o instanceof RAttributable) {
                    RAttributable vl = (RAttributable) o;
                    boolean ok = false;
                    if (vl instanceof RFunction && getGenericAttrNode.execute(vl) != null) {
                        if (pckg.length() > 0) {
                            Object gpckgObj = getPckgAttrNode.execute(vl);
                            if (gpckgObj != null) {
                                String gpckg = checkSingleString(castToVector.doCast(gpckgObj), false, "The \"package\" slot in generic function object", this, classHierarchyNode);
                                ok = pckg.equals(gpckg);
                            }
                        } else {
                            ok = true;
                        }
                    }
                    if (ok) {
                        generic = vl;
                        break;
                    }
                }
                rho = rho.getParent();
            }
            return generic == null ? RNull.instance : generic;
        }

        private static String checkSingleString(Object o, boolean nonEmpty, String what, RBaseNode node, ClassHierarchyScalarNode classHierarchyNode) {
            if (o instanceof RStringVector) {
                RStringVector vec = (RStringVector) o;
                if (vec.getLength() != 1) {
                    throw RError.error(node, RError.Message.SINGLE_STRING_TOO_LONG, what, vec.getLength());
                }
                String s = vec.getDataAt(0);
                if (nonEmpty && s.length() == 0) {
                    throw node.error(RError.Message.NON_EMPTY_STRING, what);
                }
                return s;
            } else {
                throw node.error(RError.Message.SINGLE_STRING_WRONG_TYPE, what, classHierarchyNode.executeString(o));
            }
        }

        @TruffleBoundary
        private static Object slotRead(MaterializedFrame currentFrame, FrameDescriptor desc, String name) {
            int frameIndex = FrameSlotChangeMonitor.getIndexOfIdentifier(desc, name);
            if (FrameIndex.isInitializedIndex(frameIndex)) {
                Object res = FrameSlotChangeMonitor.getValue(currentFrame, frameIndex);
                if (res != null) {
                    if (res instanceof RPromise) {
                        res = PromiseHelperNode.evaluateSlowPath((RPromise) res);
                    }
                }
                return res;
            } else {
                return null;
            }
        }
    }

    public abstract static class R_nextMethodCall extends RExternalBuiltinNode.Arg2 {

        @Child private LocalReadVariableNode readDotNextMethod;

        static {
            Casts.noCasts(R_nextMethodCall.class);
        }

        @Specialization(guards = "matchedCall.isLanguage()")
        @TruffleBoundary
        protected Object nextMethodCall(RPairList matchedCall, REnvironment ev) {
            // TODO: we can't create LocalReadVariableNode-s once and for all because ev may change
            // leading to a problem if contains a different frame; should we finesse implementation
            // of LocalReadVariableNode to handle this?
            readDotNextMethod = insert(LocalReadVariableNode.create(RRuntime.R_DOT_NEXT_METHOD, false));
            // TODO: do we need to handle "..." here? Read it and forward it?

            RFunction op = (RFunction) readDotNextMethod.execute(null, ev.getFrame());
            if (op == null) {
                throw error(RError.Message.GENERIC, "internal error in 'callNextMethod': '.nextMethod' was not assigned in the frame of the method call");
            }
            boolean primCase = op.isBuiltin();
            if (primCase) {
                throw RInternalError.unimplemented();
            }
            if (!(matchedCall.getSyntaxElement() instanceof RSyntaxCall)) {
                throw RInternalError.unimplemented();

            }
            RSyntaxCall callNode = (RSyntaxCall) matchedCall.getSyntaxElement();
            RNode f = RContext.getASTBuilder().lookup(RSyntaxNode.SOURCE_UNAVAILABLE, RRuntime.R_DOT_NEXT_METHOD, false).asRNode();
            ArgumentsSignature sig = callNode.getSyntaxSignature();
            RSyntaxNode[] args = new RSyntaxNode[sig.getLength()];
            for (int i = 0; i < args.length; i++) {
                args[i] = RContext.getASTBuilder().lookup(RSyntaxNode.SOURCE_UNAVAILABLE, sig.getName(i), false);
            }
            RPairList newCall = RDataFactory.createLanguage(Closure.createLanguageClosure(RCallNode.createCall(RSyntaxNode.SOURCE_UNAVAILABLE, f, sig, args)));
            Object res = RContext.getEngine().eval(newCall, ev.getFrame());
            return res;
        }
    }

    // Transcribed from src/library/methods/class_support.c
    public abstract static class R_externalPtrPrototypeObject extends RExternalBuiltinNode.Arg0 {

        @Specialization
        protected RExternalPtr extPrototypeObj() {
            // in GNU R, first argument is a pointer to a dummy C function
            // whose only purpose is to throw an error indicating that it shouldn't be called
            // TODO: finesse error handling in case a function stored in this pointer is actually
            // called
            return RDataFactory.createExternalPtr(new DLL.SymbolHandle(0L), RNull.instance, RNull.instance);
        }
    }
}
