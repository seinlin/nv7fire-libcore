/*
 * Copyright (C) 2010 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package libcore.net.http;

import java.io.IOException;
import java.io.InputStream;
import java.net.CacheRequest;
import java.util.Arrays;
import java.net.Socket;
import java.net.SocketTimeoutException;

/**
 * An HTTP payload terminated by the end of the socket stream.
 */
final class UnknownLengthHttpInputStream extends AbstractHttpInputStream {
    private boolean inputExhausted;

    UnknownLengthHttpInputStream(InputStream is, CacheRequest cacheRequest,
            HttpEngine httpEngine) throws IOException {
        super(is, httpEngine, cacheRequest);
    }

    @Override public int read(byte[] buffer, int offset, int count) throws IOException {
        Arrays.checkOffsetAndCount(buffer.length, offset, count);
        checkNotClosed();
        if (in == null || inputExhausted) {
            return -1;
        }
        int read = in.read(buffer, offset, count);
        if (read == -1) {
            inputExhausted = true;
            endOfInput(false);
            return -1;
        }
        cacheWrite(buffer, offset, read);
        return read;
    }

    private boolean isStale(HttpConnection c) throws IOException {
        Socket socket = c.getSocket();
        int soTimeout = socket.getSoTimeout();
        try {
            socket.setSoTimeout(1);
            in.mark(1);
            int byteRead = in.read();
            if (byteRead != -1) {
                in.reset();
                return false;
            }
            return true; // the socket is reporting all data read; it's stale
        } catch (SocketTimeoutException e) {
            return false; // the connection is not stale; hooray
        } catch (IOException e) {
            return true; // the connection is stale, the read or soTimeout failed.
        } finally {
            socket.setSoTimeout(soTimeout);
        }
    }

    @Override public int available() throws IOException {
        checkNotClosed();
        if (in == null)
            return 0;
        int a = in.available();
        if (a == 1 && httpEngine != null) {
            HttpConnection c = httpEngine.getConnection();
            if (c != null) {
                boolean stale = isStale(c);
                if (stale) {
                    //System.out.println("Unknown available was 1 returning 0");
                    return 0;
                }
            }
        }
        return a;
    }

    @Override public void close() throws IOException {
        if (closed) {
            return;
        }
        closed = true;
        if (!inputExhausted) {
            unexpectedEndOfInput();
        }
    }
}
