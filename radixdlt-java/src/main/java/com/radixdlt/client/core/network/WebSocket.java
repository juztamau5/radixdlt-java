/*
 *  Copyright (C) 2018 Radix DLT
 *  This file is part of the Radix DLT Java client - https://www.radixdlt.com
 *
 *  SPDX-License-Identifier: MIT
 *  See the file LICENSE for more information.
 */

package com.radixdlt.client.core.network;

import com.radixdlt.client.core.util.ByteString;

import java.io.IOException;
import java.net.ProtocolException;
import javax.annotation.Nullable;

public class WebSocket {
    private final HttpRequest request;
    private final WebSocketListener listener;
    // private final String key;
    private HttpCall call;

    public WebSocket(HttpRequest request, WebSocketListener listener) {
        this.request = request;
        this.listener = listener;
        // this.key = ByteString.toHex(nonce)
    }
    
    HttpRequest request() {
        return request;
    }

    void cancel() {
        try {
            call.cancel();
        } catch (Exception e) {
        }
    }

    public void connect(RadixHttpClient client) {
        final HttpRequest request = this.request.newBuilder()
                .header("Upgrade", "websocket")
                .header("Connection", "Upgrade")
                .header("Sec-WebSocket-Key", key)
                .header("Sec-WebSocket-Version", "13")
                .build();

        call = HttpCall.newCall(client, request);

        call.enqueue(new HttpCallback() {
            @Override
            public void onResponse(HttpCall call, HttpResponse response) {
                try {
                    checkResponse(response);
                } catch (ProtocolException e) {
                    // failWebsocket(e, response);
                    // closeQuietly(response);
                    return;
                }
                // TODO
            }

            @Override
            public void onFailure(HttpCall call, IOException e) {
                // failWebSocket(e, null);
            }
        });
    }

    public final class WebSocketProtocol {
        /** Magic value which must be appended to the key in a response header. */
        static final String ACCEPT_MAGIC = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";

    }

    // Protocol
    static void validateCloseCode(int code) {
        String message = closeCodeExceptionMessage(code);
        if (message != null)
            throw new IllegalArgumentException(message);
    }

    // Protocol
    static String closeCodeExceptionMessage(int code) {
        if (code < 1000 || code >= 5000) {
            return "Code must be in range [1000,5000): " + code;
        } else if ((code >= 1004 && code <= 1006) || (code >= 1012 && code <= 2999)) {
            return "Code " + code + " is reserved and may not be used.";
        } else {
            return null;
        }
    }

    void checkResponse(HttpResponse response) throws ProtocolException {
        if (response.code() != 101) {
            throw new ProtocolException(
                    "Expected HTTP 101 response but was '" + response.code() + " " + response.message() + "'");
        }

        String headerConnection = response.header("Connection");
        if (!"Upgrade".equalsIgnoreCase(headerConnection)) {
            throw new ProtocolException(
                    "Expected 'Connection' header value 'Upgrade' but was '" + headerConnection + "'");
        }

        String headerUpgrade = response.header("Upgrade");
        if (!"websocket".equalsIgnoreCase(headerUpgrade)) {
            throw new ProtocolException("Expected 'Upgrade' header value 'websocket' but was '" + headerUpgrade + "'");
        }

        String headerAccept = response.header("Sec-WebSocket-Accept");
        String acceptExpected = ByteString.encodeUtf8(key + WebSocketProtocol.ACCEPT_MAGIC).sha1().base64();
        if (!acceptExpected.equals(headerAccept)) {
            throw new ProtocolException("Expected 'Sec-WebSocket-Accept' header value '" + acceptExpected
                    + "' but was '" + headerAccept + "'");
        }
    }

    boolean send(String text) {
        // TODO
        //if (text == null) throw new NullPointerException("text == null");
        //return send(ByteString.encodeUtf8(text), OPCODE_TEXT);
        return false;
    }

    boolean send(byte[] bytes) {
        // TODO
        //if (bytes == null) throw new NullPointerException("bytes == null");
        //return send(bytes, OPCODE_BINARY);
        return false;
    }

    void close(int code, @Nullable String reason) {
        validateCloseCode(code);
    }

    public void onReadMessage(byte[] bytes) throws IOException {
        listener.onMessage(this, bytes);
    }

    public void failWebSocket(Exception e, @Nullable HttpResponse response) {
        /*
        Streams streamsToClose;
        synchronized (this) {
            if (failed)
                return; // Already failed.
            failed = true;
            streamsToClose = this.streams;
            this.streams = null;
            if (cancelFuture != null)
                cancelFuture.cancel(false);
            if (executor != null)
                executor.shutdown();
        }
        */

        try {
            listener.onFailure(this, e, response);
        } finally {
            // streamsToClose.close();
        }
    }
}
