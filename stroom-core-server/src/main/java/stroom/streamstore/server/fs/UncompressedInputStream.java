/*
 * Copyright 2016 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.streamstore.server.fs;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;

import stroom.io.SeekableInputStream;
import stroom.io.StreamCloser;

/**
 * A stream that interfaces with a random access file.
 *
 * if lazy it is assumed that a missing file means a blank stream.
 */
public class UncompressedInputStream extends InputStream implements SeekableInputStream {
    private final RandomAccessFile raFile;
    private final BlockBufferedInputStream streamAdaptor;
    private long position;
    private long lastMarkPosition;

    // Use to help track non-closed streams
    private StreamCloser streamCloser = new StreamCloser();

    public UncompressedInputStream(final File file, boolean lazy) throws IOException {
        if (lazy && !file.isFile()) {
            raFile = null;
            streamAdaptor = null;
        } else {
            raFile = new RandomAccessFile(file, BlockGZIPConstants.READ_ONLY);
            streamAdaptor = new BlockBufferedInputStream(new RandomAccessStreamAdaptor(raFile));

            streamCloser.add(raFile).add(streamAdaptor);
        }
    }

    /**
     * @return byte or -1
     */
    @Override
    public int read() throws IOException {
        if (streamAdaptor == null) {
            // LAZY
            return -1;
        } else {
            int rtn = streamAdaptor.read();
            if (rtn != -1) {
                position++;
            }
            return rtn;
        }
    }

    /**
     * @param b
     *            to fill
     */
    @Override
    public int read(final byte[] b) throws IOException {
        if (streamAdaptor == null) {
            // LAZY
            return -1;
        } else {
            int read = streamAdaptor.read(b);
            if (read != -1) {
                position += read;
            }
            return read;
        }
    }

    /**
     * @param b
     *            to fill
     * @param off
     *            offset
     * @param len
     *            length
     */
    @Override
    public int read(final byte[] b, final int off, final int len) throws IOException {
        if (streamAdaptor == null) {
            // LAZY
            return -1;
        } else {
            int read = streamAdaptor.read(b, off, len);
            if (read != -1) {
                position += read;
            }
            return read;
        }
    }

    @Override
    public void close() throws IOException {
        try {
            streamCloser.close();
        } catch (final IOException e) {
            throw e;
        } finally {
            super.close();
        }
    }

    @Override
    public long getPosition() throws IOException {
        return position;
    }

    @Override
    public long getSize() throws IOException {
        if (raFile == null) {
            // LAZY empty
            return 0;
        } else {
            return raFile.getChannel().size();
        }
    }

    @Override
    public void seek(final long pos) throws IOException {
        position = pos;
        if (raFile != null) {
            raFile.seek(pos);
            streamAdaptor.recycle(new RandomAccessStreamAdaptor(raFile));
        }
    }

    @Override
    public void mark(final int readlimit) {
        lastMarkPosition = position;
    }

    @Override
    public boolean markSupported() {
        return true;
    }

    @Override
    public void reset() throws IOException {
        seek(lastMarkPosition);
    }

    /**
     * @param n
     *            bytes to skip
     * @return how many we skipped
     */
    @Override
    public long skip(final long n) throws IOException {
        seek(position + n);
        return n;
    }

}
