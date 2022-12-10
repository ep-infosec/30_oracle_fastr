/*
 * Copyright (c) 2016, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.r.runtime.data;

import java.util.ArrayDeque;
import java.util.HashSet;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.object.DynamicObject;
import com.oracle.truffle.api.object.Property;
import com.oracle.truffle.api.object.Shape;
import com.oracle.truffle.r.runtime.ArgumentsSignature;
import com.oracle.truffle.r.runtime.RError;
import com.oracle.truffle.r.runtime.RError.Message;
import com.oracle.truffle.r.runtime.RRuntime;
import com.oracle.truffle.r.runtime.data.model.RAbstractListBaseVector;
import com.oracle.truffle.r.runtime.data.model.RAbstractVector;
import com.oracle.truffle.r.runtime.env.REnvironment;

import static com.oracle.truffle.r.runtime.ffi.util.NativeMemory.OBJECT_HEADER_SIZE;
import static com.oracle.truffle.r.runtime.ffi.util.NativeMemory.OBJECT_SIZE;

/**
 * Support for the sizing of the objects that flow through the interpreter, i.e., mostly
 * {@link RBaseObject}, but also including scalar types like {@code String}.
 */
public class RObjectSize {
    public static final int INT_SIZE = 4;
    public static final int DOUBLE_SIZE = 8;
    public static final int BYTE_SIZE = 1;

    private static final int CHAR_SIZE = 2;

    /**
     * Returns an estimate of the size of the this object in bytes. This is a snapshot and the size
     * can change as, e.g., attributes are added/removed.
     *
     * If called immediately after creation by {@link RDataFactory} provides an approximation of the
     * incremental memory usage of the system.
     */
    @TruffleBoundary
    public static long getObjectSize(Object obj) {
        return getObjectSizeImpl(obj);
    }

    /**
     * Returns an estimate of the size of the this object in bytes, including the recursive size of
     * any attributes and elements, recursively. Evidently this is a snapshot and the size can
     * change as, e.g., attributes are added/removed.
     */
    @TruffleBoundary
    public static long getRecursiveObjectSize(Object target) {
        ArrayDeque<Object> stack = new ArrayDeque<>();
        HashSet<Object> visited = new HashSet<>();
        stack.push(target);
        visited.add(target);

        long result = 0;
        while (!stack.isEmpty()) {
            Object obj = stack.pop();
            result += getObjectSizeImpl(obj);
            if (obj != null) {
                pushReferences(stack, visited, obj);
            }
        }
        return result;
    }

    private static void pushReferences(ArrayDeque<Object> stack, HashSet<Object> visited, Object obj) {
        if (obj instanceof RAttributable) {
            DynamicObject attrs = ((RAttributable) obj).getAttributes();
            if (attrs != null) {
                Shape shape = attrs.getShape();
                for (Property prop : shape.getProperties()) {
                    Object propVal = prop.get(attrs, shape);
                    pushIfNotPresent(stack, visited, propVal);
                }
            }
        }
        if (obj instanceof RAbstractListBaseVector) {
            RAbstractListBaseVector list = (RAbstractListBaseVector) obj;
            for (int i = 0; i < list.getLength(); i++) {
                pushIfNotPresent(stack, visited, list.getDataAt(i));
            }
        } else if (obj instanceof RArgsValuesAndNames) {
            RArgsValuesAndNames args = (RArgsValuesAndNames) obj;
            for (int i = 0; i < args.getLength(); i++) {
                pushIfNotPresent(stack, visited, args.getArgument(i));
            }
        }
        // Note: environments are ignored
    }

    private static void pushIfNotPresent(ArrayDeque<Object> stack, HashSet<Object> visited, Object obj) {
        if (!visited.contains(obj)) {
            stack.push(obj);
            visited.add(obj);
        }
    }

    private static long getObjectSizeImpl(Object obj) {
        // Note: if this gets too complex, it may be replaced by a system of providers or getSize
        // abstract method on RBaseObject. For now, we do not want to add yet another abstract
        // method to already complicated hierarchy and providers would only mean OO version of the
        // same code below.
        if (obj == null) {
            return 0;
        }
        // Primitive types:
        if (obj instanceof Integer) {
            return INT_SIZE;
        } else if (obj instanceof Double) {
            return DOUBLE_SIZE;
        } else if (obj instanceof Byte) {
            return BYTE_SIZE;
        } else if (obj instanceof String) {
            return CHAR_SIZE * ((String) obj).length();
        }
        // Check that we have RBaseObject:
        if (!(obj instanceof RBaseObject)) {
            // We ignore objects from other languages for now
            if (!(obj instanceof TruffleObject)) {
                reportWarning(obj);
            }
            return 0;
        }
        long attributesSize = 0;
        if (obj instanceof RAttributable) {
            DynamicObject attrs = ((RAttributable) obj).getAttributes();
            if (attrs != null) {
                attributesSize = OBJECT_HEADER_SIZE + attrs.getShape().getPropertyCount() * OBJECT_SIZE;
            }
        }
        // Individual RBaseObjects:
        if (obj instanceof RPromise || obj instanceof REnvironment || obj instanceof RExternalPtr || obj instanceof RFunction || obj instanceof RSymbol || obj instanceof RPairList ||
                        obj instanceof RS4Object) {
            // promise: there is no value allocated yet, we may use the size of the closure
            return OBJECT_HEADER_SIZE + attributesSize;
        } else if (obj instanceof RStringVector && ((RStringVector) obj).isSequence()) {
            RStringSeqVectorData seq = ((RStringVector) obj).getSequence();
            if (seq.getLength() == 0) {
                return OBJECT_HEADER_SIZE + INT_SIZE * 2;  // we cannot get prefix/suffix...
            } else {
                return OBJECT_HEADER_SIZE + seq.getStringAt(0).length() * CHAR_SIZE;
            }
        } else if (RRuntime.isSequence(obj)) {
            // count: start, stride, length
            return OBJECT_HEADER_SIZE + 2 * getElementSize((RAbstractVector) obj) + INT_SIZE + attributesSize;
        } else if (obj instanceof RStringVector) {
            RStringVector strVec = (RStringVector) obj;
            long result = OBJECT_HEADER_SIZE;
            for (int i = 0; i < strVec.getLength(); i++) {
                String data = strVec.getDataAt(i);
                result += data == null ? 0 : data.length() * CHAR_SIZE;
            }
            return result + attributesSize;
        } else if (obj instanceof RAbstractVector) {
            RAbstractVector vec = (RAbstractVector) obj;
            return OBJECT_HEADER_SIZE + getElementSize(vec) * vec.getLength() + attributesSize;
        } else if (obj instanceof RScalar) {
            // E.g. singletons RNull or REmpty. RInteger, RLogical etc. already caught by
            // RAbstractVector branch
            return 0;
        } else if (obj instanceof RArgsValuesAndNames) {
            return getArgsAndValuesSize((RArgsValuesAndNames) obj);
        } else {
            reportWarning(obj);
            return OBJECT_HEADER_SIZE;
        }
    }

    private static int getElementSize(RAbstractVector vector) {
        if (vector instanceof RDoubleVector) {
            return DOUBLE_SIZE;
        } else if (vector instanceof RIntVector) {
            return INT_SIZE;
        } else if (vector instanceof RLogicalVector || vector instanceof RRawVector) {
            return BYTE_SIZE;
        } else if (vector instanceof RComplexVector) {
            return DOUBLE_SIZE * 2;
        } else if (vector instanceof RAbstractListBaseVector) {
            return OBJECT_SIZE;
        }
        reportWarning(vector);
        return INT_SIZE;
    }

    private static long getArgsAndValuesSize(RArgsValuesAndNames args) {
        long result = OBJECT_HEADER_SIZE + args.getLength() * OBJECT_SIZE;
        ArgumentsSignature signature = args.getSignature();
        for (int i = 0; i < signature.getLength(); i++) {
            String name = signature.getName(i);
            if (name != null) {
                result += name.length() * CHAR_SIZE;
            }
        }
        return result;
    }

    private static void reportWarning(Object obj) {
        RError.warning(RError.NO_CALLER, Message.UNEXPECTED_OBJ_IN_SIZE, obj.getClass().getSimpleName());
    }
}
