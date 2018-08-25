/*
 *  Copyright (C) 2018 Radix DLT
 *  This file is part of the Radix DLT Java client - https://www.radixdlt.com
 *
 *  SPDX-License-Identifier: MIT
 *  See the file LICENSE for more information.
 */

package com.radixdlt.client.core.network;

import javax.annotation.Nullable;
import javax.net.SocketFactory;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.X509TrustManager;

public final class RadixHttpClient {
    final HttpDispatcher dispatcher;
    final SSLSocketFactory sslSocketFactory;
    final CertificateChainCleaner certificateChainCleaner;

    public RadixHttpClient(Builder builder) {
        this.dispatcher = builder.dispatcher;
        this.sslSocketFactory = builder.sslSocketFactory;
        this.certificateChainCleaner = builder.certificateChainCleaner;
    }

    public HttpDispatcher dispatcher() {
        return dispatcher;
    }

    public HttpCall newCall(HttpRequest request) {
        return HttpCall.newCall(this, request);
    }

    static HttpCall newCall(RadixHttpClient client, HttpRequest originalRequest) {
        HttpCall call = new HttpCall(client, originalRequest);
        call.eventListener = client.eventListenerFactory().create(call);
        return call;
    }

    public WebSocket newWebSocket(HttpRequest request, WebSocketListener listener) {
        WebSocket webSocket = new WebSocket(request, listener);
        webSocket.connect(this);
        return webSocket;
    }

    public Builder newBuilder() {
        return new Builder(this);
    }

    public static final class Builder {
        HttpDispatcher dispatcher;
        SSLSocketFactory sslSocketFactory;
        CertificateChainCleaner certificateChainCleaner;

        public Builder() {
            dispatcher = new HttpDispatcher();
        }

        Builder(RadixHttpClient radixHttpClient) {
            dispatcher = radixHttpClient.dispatcher;
        }

        public Builder sslSocketFactory(SSLSocketFactory sslSocketFactory, X509TrustManager trustManager) {
            this.sslSocketFactory = sslSocketFactory;
            this.certificateChainCleaner = BasicCertificateChainCleaner(
                    new BasicTrustRootIndex(trustManager.getAcceptedIssuers()));
            return this;
        }

        public RadixHttpClient build() {
            return new RadixHttpClient(this);
        }
    }

    public CertificateChainCleaner buildCertificateChainCleaner(X509TrustManager trustManager) {
        return new BasicCertificateChainCleaner(buildTrustRootIndex(trustManager));
    }

    public TrustRootIndex buildTrustRootIndex(X509TrustManager trustManager) {
        return new BasicTrustRootIndex(trustManager.getAcceptedIssuers());
    }
}
