/*
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.r.runtime.data.nodes;

import com.oracle.truffle.r.runtime.RInternalError;
import com.oracle.truffle.r.runtime.RType;
import com.oracle.truffle.r.runtime.data.RNull;
import com.oracle.truffle.r.runtime.data.nodes.FastPathVectorAccess.FastPathFromDoubleAccess;
import com.oracle.truffle.r.runtime.data.nodes.FastPathVectorAccess.FastPathFromIntAccess;
import com.oracle.truffle.r.runtime.data.nodes.FastPathVectorAccess.FastPathFromListAccess;
import com.oracle.truffle.r.runtime.data.nodes.FastPathVectorAccess.FastPathFromLogicalAccess;
import com.oracle.truffle.r.runtime.data.nodes.FastPathVectorAccess.FastPathFromStringAccess;
import com.oracle.truffle.r.runtime.data.nodes.SlowPathVectorAccess.SlowPathFromDoubleAccess;
import com.oracle.truffle.r.runtime.data.nodes.SlowPathVectorAccess.SlowPathFromIntAccess;
import com.oracle.truffle.r.runtime.data.nodes.SlowPathVectorAccess.SlowPathFromListAccess;
import com.oracle.truffle.r.runtime.data.nodes.SlowPathVectorAccess.SlowPathFromLogicalAccess;
import com.oracle.truffle.r.runtime.data.nodes.SlowPathVectorAccess.SlowPathFromStringAccess;

public abstract class PrimitiveVectorAccess {

    public static VectorAccess create(Object value) {
        if (value instanceof Integer) {
            return new FastPathFromIntAccess(value) {
                @Override
                public int getIntImpl(AccessIterator accessIter, int index) {
                    return (Integer) accessIter.getStore();
                }
            };
        } else if (value instanceof Double) {
            return new FastPathFromDoubleAccess(value) {
                @Override
                public double getDoubleImpl(AccessIterator accessIter, int index) {
                    return (Double) accessIter.getStore();
                }
            };
        } else if (value instanceof Byte) {
            return new FastPathFromLogicalAccess(value) {
                @Override
                public byte getLogicalImpl(AccessIterator accessIter, int index) {
                    return (Byte) accessIter.getStore();
                }
            };
        } else if (value instanceof String) {
            return new FastPathFromStringAccess(value) {
                @Override
                public String getStringImpl(AccessIterator accessIter, int index) {
                    return (String) accessIter.getStore();
                }
            };
        } else if (value instanceof RNull) {
            return new FastPathFromListAccess(value) {
                @Override
                public RType getType() {
                    return RType.Null;
                }

                @Override
                protected int getLength(Object vector) {
                    return 0;
                }

                @Override
                public Object getListElementImpl(AccessIterator accessIter, int index) {
                    throw RInternalError.shouldNotReachHere();
                }
            };
        } else {
            return null;
        }
    }

    private static final SlowPathFromIntAccess SLOW_PATH_INT = new SlowPathFromIntAccess() {
        @Override
        public int getIntImpl(AccessIterator accessIter, int index) {
            return (Integer) accessIter.getStore();
        }
    };
    private static final SlowPathFromDoubleAccess SLOW_PATH_DOUBLE = new SlowPathFromDoubleAccess() {
        @Override
        public double getDoubleImpl(AccessIterator accessIter, int index) {
            return (Double) accessIter.getStore();
        }
    };
    private static final SlowPathFromLogicalAccess SLOW_PATH_LOGICAL = new SlowPathFromLogicalAccess() {
        @Override
        public byte getLogicalImpl(AccessIterator accessIter, int index) {
            return (Byte) accessIter.getStore();
        }
    };
    private static final SlowPathFromStringAccess SLOW_PATH_STRING = new SlowPathFromStringAccess() {
        @Override
        public String getStringImpl(AccessIterator accessIter, int index) {
            return (String) accessIter.getStore();
        }
    };
    private static final SlowPathFromListAccess SLOW_PATH_NULL = new SlowPathFromListAccess() {
        @Override
        public RType getType() {
            return RType.Null;
        }

        @Override
        protected int getLength(Object vector) {
            return 0;
        }

        @Override
        public Object getListElementImpl(AccessIterator accessIter, int index) {
            throw RInternalError.shouldNotReachHere();
        }
    };

    public static VectorAccess createSlowPath(Object value) {
        if (value instanceof Integer) {
            return SLOW_PATH_INT;
        } else if (value instanceof Double) {
            return SLOW_PATH_DOUBLE;
        } else if (value instanceof Byte) {
            return SLOW_PATH_LOGICAL;
        } else if (value instanceof String) {
            return SLOW_PATH_STRING;
        } else if (value instanceof RNull) {
            return SLOW_PATH_NULL;
        } else {
            return null;
        }
    }
}
