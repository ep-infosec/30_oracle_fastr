/*
 * Copyright (c) 2014, 2018, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.r.runtime.conn;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.EnumSet;

import com.oracle.truffle.r.runtime.RError;
import com.oracle.truffle.r.runtime.RInternalError;
import com.oracle.truffle.r.runtime.conn.ConnectionSupport.BaseRConnection;

abstract class DelegateWriteRConnection extends DelegateRConnection {

    protected DelegateWriteRConnection(BaseRConnection base) {
        super(base, 0, false);
    }

    protected DelegateWriteRConnection(BaseRConnection base, int cacheSize) {
        super(base, cacheSize, false);
    }

    @Override
    public String[] readLines(int n, EnumSet<ReadLineWarning> warn, boolean skipNul) throws IOException {
        throw new IOException(RError.Message.CANNOT_READ_CONNECTION.message);
    }

    @Override
    public String readChar(int nchars, boolean useBytes) throws IOException {
        throw new IOException(RError.Message.CANNOT_READ_CONNECTION.message);
    }

    @Override
    public int readBin(ByteBuffer buffer) throws IOException {
        throw new IOException(RError.Message.CANNOT_READ_CONNECTION.message);
    }

    @Override
    public byte[] readBinChars() throws IOException {
        throw new IOException(RError.Message.CANNOT_READ_CONNECTION.message);
    }

    @Override
    public int getc() throws IOException {
        throw new IOException(RError.Message.CANNOT_READ_CONNECTION.message);
    }

    @Override
    public InputStream getInputStream() {
        throw RInternalError.shouldNotReachHere();
    }

    @Override
    public boolean canRead() {
        return false;
    }

    @Override
    public boolean canWrite() {
        return true;
    }

    @Override
    public void close() throws IOException {
        flush();
        super.close();
    }
}
