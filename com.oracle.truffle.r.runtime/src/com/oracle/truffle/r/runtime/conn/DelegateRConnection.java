/*
 * Copyright (c) 2014, 2020, Oracle and/or its affiliates. All rights reserved.
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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.nio.ByteBuffer;
import java.nio.channels.ByteChannel;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.SeekableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.Objects;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.r.runtime.RError;
import com.oracle.truffle.r.runtime.RInternalError;
import com.oracle.truffle.r.runtime.RType;
import com.oracle.truffle.r.runtime.conn.ConnectionSupport.BaseRConnection;
import com.oracle.truffle.r.runtime.data.RBaseObject;
import com.oracle.truffle.r.runtime.data.RDataFactory;
import com.oracle.truffle.r.runtime.data.RStringVector;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Actually performs the I/O operations for a connections.<br>
 * <p>
 * A delegate connection is called from its base connection and implements the actual I/O
 * operations.
 * </p>
 */
abstract class DelegateRConnection extends RBaseObject implements RConnection, ByteChannel {
    public static final int DEFAULT_CACHE_SIZE = 16 * 1024;
    protected final BaseRConnection base;
    private final ByteBuffer cache;
    private final boolean readCache;

    DelegateRConnection(BaseRConnection base) {
        this(base, DEFAULT_CACHE_SIZE, true);
    }

    DelegateRConnection(BaseRConnection base, int cacheSize, boolean readCache) {
        this.base = Objects.requireNonNull(base);
        this.readCache = readCache;

        if (cacheSize > 0) {
            cache = ByteBuffer.allocate(cacheSize);

            // indicate that there are no remaining bytes in the buffer to read
            if (readCache) {
                cache.flip();
            }
        } else {
            cache = null;
        }
    }

    private static int transfer(ByteBuffer from, ByteBuffer to) {
        int nbytes = Math.min(to.remaining(), from.remaining());
        if (nbytes > 0) {
            to.put(from.array(), from.arrayOffset() + from.position(), nbytes);
            from.position(from.position() + nbytes);
        }
        return nbytes;
    }

    @Override
    public int getDescriptor() {
        return base.getDescriptor();
    }

    @Override
    public boolean isTextMode() {
        return base.isTextMode();
    }

    @Override
    public boolean isOpen() {
        return base.isOpen();
    }

    @Override
    public RConnection forceOpen(String modeString) throws IOException {
        return base.forceOpen(modeString);
    }

    @SuppressWarnings("unused")
    protected long seekInternal(long offset, SeekMode seekMode, SeekRWMode seekRWMode) throws IOException {
        throw RInternalError.shouldNotReachHere("seek has not been implemented for this connection");
    }

    @Override
    public final long seek(long offset, SeekMode seekMode, SeekRWMode seekRWMode) throws IOException {
        if (!isSeekable()) {
            throw RError.error(RError.SHOW_CALLER, RError.Message.NOT_ENABLED_FOR_THIS_CONN, "seek");
        }
        long res = seekInternal(offset, seekMode, seekRWMode);
        if (seekMode != SeekMode.ENQUIRE) {
            invalidateCache();
        }
        return res;
    }

    /**
     * {@code readLines} from the connection. It would be convenient to use a {@link BufferedReader}
     * but mixing binary and text operations, which is a requirement, would then be difficult.
     * 
     * @param warn Specifies which warnings should be output.
     * @param skipNul Specifies if the null character should be ignored.
     */
    @Override
    @TruffleBoundary
    public String[] readLines(int n, EnumSet<ReadLineWarning> warn, boolean skipNul) throws IOException {
        base.setIncomplete(false);
        ArrayList<String> lines = new ArrayList<>();
        int totalRead = 0;
        int nBytesConsumed = 0;
        byte[] buffer = new byte[64];
        int pushBack = 0;
        boolean nullRead = false;
        while (true) {
            int ch;
            if (pushBack != 0) {
                ch = pushBack;
                pushBack = 0;
            } else {
                ch = readInternal();
            }
            boolean lineEnd = false;
            if (ch < 0) {
                if (totalRead > 0) {
                    /*
                     * GnuR says if non-blocking and in text mode, silently push back incomplete
                     * lines, otherwise keep data and output warning.
                     */
                    final String incompleteFinalLine = new String(buffer, 0, totalRead, base.getEncoding());
                    nBytesConsumed += totalRead;
                    if (!base.isBlocking() && base.isTextMode()) {
                        base.pushBack(RDataFactory.createStringVector(incompleteFinalLine), false);
                        base.setIncomplete(true);
                    } else {
                        lines.add(incompleteFinalLine);
                        if (warn.contains(ReadLineWarning.INCOMPLETE_LAST_LINE)) {
                            RError.warning(RError.SHOW_CALLER, RError.Message.INCOMPLETE_FINAL_LINE, base.getSummaryDescription());
                        }
                    }
                }
                break;
            }
            if (ch == '\n') {
                lineEnd = true;
            } else if (ch == '\r') {
                lineEnd = true;
                ch = readInternal();
                if (ch == '\n') {
                    // swallow the trailing lf
                } else {
                    pushBack = ch;
                }
            } else if (ch == 0) {
                nullRead = true;
                if (warn.contains(ReadLineWarning.EMBEDDED_NUL) && !skipNul) {
                    RError.warning(RError.SHOW_CALLER, RError.Message.LINE_CONTAINS_EMBEDDED_NULLS, lines.size() + 1);
                }
            }
            if (lineEnd) {
                lines.add(new String(buffer, 0, totalRead, base.getEncoding()));
                nBytesConsumed += totalRead;
                if (n > 0 && lines.size() == n) {
                    break;
                }
                totalRead = 0;
                nullRead = false;
            } else {
                if (!nullRead) {
                    buffer = DelegateRConnection.checkBuffer(buffer, totalRead);
                    buffer[totalRead++] = (byte) (ch & 0xFF);
                }
                if (skipNul) {
                    nullRead = false;
                }
            }
        }
        String[] result = new String[lines.size()];
        lines.toArray(result);
        updateReadOffset(nBytesConsumed);
        return result;
    }

    /**
     * Updates the read cursor.<br>
     * <p>
     * Called by methods using {@link #readInternal()} to tell how many bytes have been consumed to
     * be able to update a read curosor if available.
     * </p>
     *
     * @param nBytesConsumed Number of bytes consumed by a read operation.
     */
    protected void updateReadOffset(int nBytesConsumed) {
        // default: nothing to do
    }

    @Override
    public String readChar(int nchars, boolean useBytes) throws IOException {
        if (useBytes) {
            return DelegateRConnection.readCharHelper(nchars, this);
        } else {
            return DelegateRConnection.readCharHelper(nchars, getDecoder(nchars));
        }
    }

    /**
     * Writes a string to a channel.
     *
     * @param out the channel
     * @param s The actual string to write.
     * @param nl Indicates if a line separator should be appended.
     * @param encoding The encoding to use for writing.
     * @return {@code true} if an incomplete line was written; {@code false} otherwise
     * @throws IOException
     */
    @TruffleBoundary
    public static boolean writeStringHelper(WritableByteChannel out, String s, boolean nl, Charset encoding) throws IOException {
        boolean incomplete;
        final byte[] bytes = s.getBytes(encoding);
        final byte[] lineSepBytes = nl ? System.lineSeparator().getBytes(encoding) : null;

        ByteBuffer buf = ByteBuffer.allocate(bytes.length + (nl ? lineSepBytes.length : 0));
        buf.put(bytes);
        if (nl) {
            buf.put(lineSepBytes);
            incomplete = false;
        } else {
            incomplete = !s.contains("\n");
        }

        buf.rewind();
        out.write(buf);
        return incomplete;
    }

    /**
     * Writes characters in binary mode (without any re-encoding) to the provided channel.
     *
     * @param channel The writable byte channel to write to (must not be {@code null}).
     * @param s The character string to write (must not be {@code null}).
     * @param pad The number of null characters to append to the characters.
     * @param eos The end-of-string terminator (may be {@code null}).
     * @throws IOException
     */
    @TruffleBoundary
    public static void writeCharHelper(WritableByteChannel channel, String s, int pad, String eos) throws IOException {

        final byte[] bytes = s.getBytes();
        final byte[] eosBytes = eos != null ? eos.getBytes() : null;

        final int bufLen = bytes.length + (pad > 0 ? pad : 0) + (eos != null ? eosBytes.length + 1 : 0);
        assert bufLen >= s.length();
        ByteBuffer buf = ByteBuffer.allocate(bufLen);
        buf.put(bytes);
        if (pad > 0) {
            for (int i = 0; i < pad; i++) {
                buf.put((byte) 0);
            }
        }
        if (eos != null) {
            if (eos.length() > 0) {
                buf.put(eos.getBytes());
            }
            // function writeChar is defined to append the null character if eos != null
            buf.put((byte) 0);
        }
        buf.rewind();
        channel.write(buf);
    }

    /**
     * Reads a specified amount of characters.
     *
     * @param nchars Number of characters to read.
     * @param in The encoded byte stream.
     * @return The read string.
     * @throws IOException
     */
    public static String readCharHelper(int nchars, Reader in) throws IOException {
        char[] chars = new char[nchars];
        in.read(chars);
        int j = 0;
        for (; j < chars.length; j++) {
            // strings end at 0
            if (chars[j] == 0) {
                break;
            }
        }

        return new String(chars, 0, j);
    }

    /**
     * Reads a specified number of single-byte characters.<br>
     * <p>
     * This method is meant to be used if R's function {@code readChar} is called with parameter
     * {@code useBytes=TRUE}.
     * </p>
     *
     * @param nchars The number of single-byte characters to read.
     * @param channel The channel to read from (must not be {@code null}).
     * @throws IOException
     */
    public static String readCharHelper(int nchars, ReadableByteChannel channel) throws IOException {
        ByteBuffer buf = ByteBuffer.allocate(nchars);
        channel.read(buf);
        int j = 0;
        for (; j < buf.position(); j++) {
            // strings end at 0
            if (buf.get(j) == 0) {
                break;
            }
        }

        return new String(buf.array(), 0, j);
    }

    /**
     * Implements standard seeking behavior.<br>
     * <p>
     * <it>Standard</it> means that there is a shared cursor between reading and writing operations.
     * </p>
     */
    public static long seek(SeekableByteChannel channel, long offset, SeekMode seekMode, @SuppressWarnings("unused") SeekRWMode seekRWMode, int bytesInCache) throws IOException {
        long position = channel.position() - bytesInCache;
        switch (seekMode) {
            case ENQUIRE:
                break;
            case CURRENT:
                if (offset != 0) {
                    channel.position(position + offset);
                }
                break;
            case START:
                channel.position(offset);
                break;
            case END:
                channel.position(channel.size());
                break;
        }
        return position;
    }

    /**
     * Enlarges the buffer if necessary.
     */
    private static byte[] checkBuffer(byte[] buffer, int n) {
        if (n > buffer.length - 1) {
            byte[] newBuffer = new byte[buffer.length + buffer.length / 2];
            System.arraycopy(buffer, 0, newBuffer, 0, buffer.length);
            return newBuffer;
        } else {
            return buffer;
        }
    }

    @TruffleBoundary
    public static boolean writeLinesHelper(WritableByteChannel out, RStringVector lines, String sep, Charset encoding) throws IOException {
        if (sep != null && sep.contains("\n")) {
            // fast path: we know that the line is complete
            final ByteBuffer nlBuf = ByteBuffer.wrap(sep.getBytes(encoding));
            for (int i = 0; i < lines.getLength(); i++) {
                final String line = lines.getDataAt(i);
                final ByteBuffer buf = ByteBuffer.wrap(line.getBytes(encoding));
                out.write(buf);
                nlBuf.rewind();
                out.write(nlBuf);
            }
            return false;
        } else {
            // slow path: we have to scan every string if it contains a newline
            boolean incomplete = false;
            for (int i = 0; i < lines.getLength(); i++) {
                final String line = lines.getDataAt(i);
                incomplete = DelegateRConnection.writeStringHelper(out, line, false, encoding);
                incomplete = DelegateRConnection.writeStringHelper(out, sep, false, encoding) || incomplete;
            }
            return incomplete;
        }
    }

    @Override
    public void pushBack(RStringVector lines, boolean addNewLine) {
        throw RInternalError.shouldNotReachHere();
    }

    /**
     * Creates the stream decoder on demand and returns it.
     */
    protected Reader getDecoder(int bufSize) {
        CharsetDecoder charsetEncoder = base.getEncoding().newDecoder().onMalformedInput(CodingErrorAction.REPLACE).onUnmappableCharacter(CodingErrorAction.REPLACE);
        return Channels.newReader(this, charsetEncoder, bufSize);
    }

    @Override
    public void truncate() throws IOException {
        if (!isSeekable()) {
            throw RError.error(RError.SHOW_CALLER, RError.Message.TRUNCATE_NOT_ENABLED);
        }
        throw RError.nyi(RError.SHOW_CALLER, "truncate");
    }

    @Override
    public void writeBin(ByteBuffer buffer) throws IOException {
        write(buffer);
    }

    @Override
    public void writeChar(String s, int pad, String eos, boolean useBytes) throws IOException {
        DelegateRConnection.writeCharHelper(this, s, pad, eos);
    }

    @Override
    public void writeLines(RStringVector lines, String sep, boolean useBytes) throws IOException {
        boolean incomplete = DelegateRConnection.writeLinesHelper(this, lines, sep, base.getEncoding());
        base.setIncomplete(incomplete);
    }

    @Override
    public void writeString(String s, boolean nl) throws IOException {
        DelegateRConnection.writeStringHelper(this, s, nl, base.getEncoding());
    }

    @Override
    @TruffleBoundary
    public int read(ByteBuffer dst) throws IOException {
        if (readCache && cache != null) {
            final int bytesRequested = dst.remaining();
            int totalBytesRead = 0;
            int bytesToRead = 0;
            boolean eof;
            do {
                eof = ensureDataAvailable(dst.remaining());
                bytesToRead = Math.min(cache.remaining(), dst.remaining());
                cache.get(dst.array(), dst.position(), bytesToRead);
                dst.position(dst.position() + bytesToRead);
                totalBytesRead += bytesToRead;
            } while (totalBytesRead < bytesRequested && bytesToRead > 0 && !eof);
            return totalBytesRead == 0 && eof ? -1 : totalBytesRead;
        } else {
            return getChannel().read(dst);
        }
    }

    @Override
    @TruffleBoundary
    public int write(ByteBuffer src) throws IOException {
        if (!readCache && cache != null) {
            int total = 0;
            while (src.hasRemaining()) {
                total += transfer(src, cache);
                if (!cache.hasRemaining()) {
                    flush();
                }
            }
            return total;
        } else {
            invalidateCache();
            return getChannel().write(src);
        }
    }

    /**
     * Reads one byte from the channel.<br>
     * <p>
     * Should basically do the same job as {@link #getc()} but is only used internally by this class
     * or subclasses and may therefore produce an inconsistent state over several calls. For
     * example, updating the channel's cursor position can be collapsed.
     * </p>
     */
    protected int readInternal() throws IOException {
        if (readCache && cache != null) {
            ensureDataAvailable(1);
            if (!cache.hasRemaining()) {
                return -1;
            }
            // consider byte to be unsigned
            return cache.get() & 0xFF;
        } else {

            ByteBuffer buf = ByteBuffer.allocate(1);
            int n = getChannel().read(buf);
            if (n <= 0) {
                return -1;
            }
            buf.flip();
            return buf.get() & 0xFF;
        }
    }

    private boolean ensureDataAvailable(int i) throws IOException {
        assert cache != null;
        if (cache.remaining() < i) {
            byte[] rem = new byte[cache.remaining()];
            cache.get(rem);
            assert !cache.hasRemaining();
            cache.clear();
            cache.put(rem);
            int read = getChannel().read(cache);
            cache.flip();
            return read == -1;
        }
        return false;
    }

    /**
     * Invalidates the read cache by dropping cached data.<br>
     * <p>
     * This method is most useful if an operation like {@code seek} is performed that destroys the
     * order data is read.
     * </p>
     */
    protected void invalidateCache() {
        if (cache != null) {
            cache.clear();
            cache.flip();
        }
    }

    @Override
    public int getc() throws IOException {
        return readInternal();
    }

    @Override
    public int readBin(ByteBuffer buffer) throws IOException {
        int read = read(buffer);
        return read < 0 ? 0 : read;
    }

    /**
     * Reads null-terminated character strings from a {@link ReadableByteChannel}.
     */
    @Override
    public byte[] readBinChars() throws IOException {
        int numRead = readInternal();
        if (numRead <= 0) {
            return null;
        }
        int totalRead = 0;
        byte[] buffer = new byte[64];
        while (true) {
            buffer = DelegateRConnection.checkBuffer(buffer, totalRead);
            buffer[totalRead++] = (byte) numRead;
            if (numRead == 0) {
                break;
            } else if (numRead == -1) {
                RError.warning(RError.SHOW_CALLER, RError.Message.INCOMPLETE_STRING_AT_EOF_DISCARDED);
                return null;
            }
            numRead = readInternal();
        }
        return buffer;
    }

    @Override
    public boolean canRead() {
        return true;
    }

    @Override
    public boolean canWrite() {
        return true;
    }

    @Override
    public void flush() throws IOException {
        if (!readCache && cache != null) {
            cache.flip();
            getChannel().write(cache);
            cache.clear();
        }
    }

    protected int bytesInCache() {
        return cache.remaining();
    }

    @Override
    public OutputStream getOutputStream() throws IOException {
        return Channels.newOutputStream(this);
    }

    @Override
    public InputStream getInputStream() throws IOException {
        return Channels.newInputStream(this);
    }

    @Override
    public void close() throws IOException {
        getChannel().close();
    }

    @Override
    public void closeAndDestroy() throws IOException {
        base.closed = true;
        close();
    }

    @Override
    public RType getRType() {
        return RType.Connection;
    }

    private static final int GZIP_BUFFER_SIZE = (2 << 20);

    static DelegateRConnection createGZIPDelegateOutputConnection(BaseRConnection base, OutputStream os) throws IOException {
        assert base.getOpenMode().canWrite();
        return new CompressedOutputRConnection(base, new GZIPOutputStream(os, GZIP_BUFFER_SIZE), true);
    }

    static DelegateRConnection createGZIPDelegateInputConnection(BaseRConnection base, InputStream is) throws IOException {
        assert base.getOpenMode().canRead();
        return new CompressedInputRConnection(base, new GZIPInputStream(is, GZIP_BUFFER_SIZE));
    }

    static class CompressedOutputRConnection extends DelegateWriteRConnection {
        protected ByteChannel channel;
        private final boolean seekable;
        private long seekPosition = 0L;

        protected CompressedOutputRConnection(BaseRConnection base, OutputStream os, boolean seekable) {
            super(base);
            this.seekable = seekable;
            this.channel = ConnectionSupport.newChannel(os);
        }

        @Override
        public void closeAndDestroy() throws IOException {
            base.closed = true;
            close();
        }

        @Override
        protected long seekInternal(long offset, RConnection.SeekMode seekMode, RConnection.SeekRWMode seekRWMode) throws IOException {
            if (seekable) {
                // TODO GZIP is basically seekable; however, the output stream does not allow any
                // seeking
                long oldPos = seekPosition;
                seekPosition = offset;
                return oldPos;
            }
            return super.seek(offset, seekMode, seekRWMode);
        }

        @Override
        public boolean isSeekable() {
            return seekable;
        }

        @Override
        public ByteChannel getChannel() {
            return channel;
        }

        @Override
        public void truncate() throws IOException {
            throw RError.nyi(RError.SHOW_CALLER, "truncating compressed file not");
        }
    }

    static class CompressedInputRConnection extends DelegateReadRConnection {
        private final ByteChannel channel;

        protected CompressedInputRConnection(BaseRConnection base, InputStream is) {
            super(base);
            channel = ConnectionSupport.newChannel(is);
        }

        @Override
        public ByteChannel getChannel() {
            return channel;
        }

        @Override
        public boolean isSeekable() {
            return false;
        }
    }

}
