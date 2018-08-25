/*
 *  Copyright (C) 2018 Radix DLT
 *  This file is part of the Radix DLT Java client - https://www.radixdlt.com
 *
 *  SPDX-License-Identifier: MIT
 *  See the file LICENSE for more information.
 */

package com.radixdlt.client.core.network;

import javax.annotation.Nullable;

public final class HttpResponse {
    final HttpRequest request;
    final int code;
    final String message;
    final @Nullable HttpResponseBody body;

    HttpResponse(Builder builder) {
        this.request = builder.request;
        this.code = builder.code;
        this.message = builder.message;
        this.body = builder.body;
    }

    public HttpRequest request() {
        return request;
    }

    public Map<String, String> headers() {
        return headers;
    }

    public int code() {
        return code;
    }

    public boolean isSuccessful() {
        return code >= 200 && code < 300;
    }

    public String message() {
        return message;
    }

    public @Nullable HttpResponseBody body() {
        return body;
    }

    public Builder newBuilder() {
        return new Builder(this);
    }

    public void close() {
        if (body == null) {
            throw new IllegalStateException("response is not eligible for a body and must not be closed");
        }
        body.close();
    }

    public static class Builder {
        HttpRequest request;
        int code = -1;
        String message;
        HttpResponseBody body;

        public Builder() {
        }

        Builder(HttpResponse response) {
            this.request = response.request;
            this.code = response.code;
            this.message = response.message;
            this.body = response.body;
        }

        public Builder request(HttpRequest request) {
            this.request = request;
            return this;
        }

        public Builder code(int code) {
            this.code = code;
            return this;
        }

        public Builder message(String message) {
            this.message = message;
            return this;
        }

        public Builder body(@Nullable HttpResponseBody body) {
            this.body = body;
            return this;
        }

        public HttpResponse build() {
            if (request == null)
                throw new IllegalStateException("request == null");
            if (code < 0)
                throw new IllegalStateException("code < 0: " + code);
            return new HttpResponse(this);
        }
    }
}
