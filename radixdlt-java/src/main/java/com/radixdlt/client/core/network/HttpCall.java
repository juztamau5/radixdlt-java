/*
 *  Copyright (C) 2018 Radix DLT
 *  This file is part of the Radix DLT Java client - https://www.radixdlt.com
 *
 *  SPDX-License-Identifier: MIT
 *  See the file LICENSE for more information.
 */

package com.radixdlt.client.core.network;

import io.reactivex.functions.Cancellable;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.lang.Runnable;
import java.net.HttpURLConnection;
import java.net.URL;

class HttpCall implements Cancellable, Runnable {
    private final RadixHttpClient client;
    private final HttpRequest request;
    private final HttpCallback responseCallback;

    private boolean executed;

    public HttpCall(RadixHttpClient client, HttpRequest request) {
        this.client = client;
        this.request = request;
        this.responseCallback = null;
    }

    public HttpCall(RadixHttpClient client, HttpRequest request, HttpCallback responseCallback) {
        this.client = client;
        this.request = request;
        this.responseCallback = responseCallback;
    }

    public HttpRequest request() {
        return request;
    }

    public String url() {
        return request.url();
    }

    void enqueue(HttpCallback responseCallback) {
        synchronized (this) {
            if (executed)
                throw new IllegalStateException("Already Executed");
            executed = true;
        }
        client.dispatcher().enqueue(new HttpCall(client, request, responseCallback));
    }

    @Override
    public void run() {
        boolean signalledCallback = false;

        try {
            client.dispatcher().executed(this);

            // TODO
            HttpURLConnection connection = request.openConnection();
            if (connection != null) {
                int responseCode = connection.getResponseCode();

                BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));

                String inputLine;
                StringBuffer response = new StringBuffer();

                while ((inputLine = in.readLine()) != null) {
                    response.append(inputLine);
                }

                in.close();

                response.toString();

                // TODO
            }

            // TODO
            HttpResponse result = null;

            if (result == null)
                throw new IOException("Canceled");

            return result;
        } catch (IOException e) {
            eventListener.callFailed(this, e);
            throw e;
        } finally {
            client.dispatcher().finished(this);
        }

        // TODO
        try {
            HttpResponse response = getResponseWithInterceptorChain();
            if (retryAndFollowUpInterceptor.isCanceled()) {
                signalledCallback = true;
                responseCallback.onFailure(this, new IOException("Canceled"));
            } else {
                signalledCallback = true;
                responseCallback.onResponse(this, response);
            }
        } catch (IOException e) {
            if (signalledCallback) {
                // Do not signal the callback twice!
                Platform.get().log(INFO, "Callback failure for " + toLoggableString(), e);
            } else {
                eventListener.callFailed(this, e);
                responseCallback.onFailure(this, e);
            }
        } finally {
            client.dispatcher().finished(this);
        }
    }

    @Override
    public void cancel() throws Exception {
        retryAndFollowUpInterceptor.cancel();
    }

    public static HttpCall newCall(RadixHttpClient client, HttpRequest request, HttpCallback responseCallback) {
        HttpCall call = new HttpCall(client, request, responseCallback);
        return call;
    }
}
