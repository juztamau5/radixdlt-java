/*
 *  Copyright (C) 2018 Radix DLT
 *  This file is part of the Radix DLT Java client - https://www.radixdlt.com
 *
 *  SPDX-License-Identifier: MIT
 *  See the file LICENSE for more information.
 */

package com.radixdlt.client.core.network;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Map;
import javax.annotation.Nullable;

public final class HttpRequest {
    private final URL url;
    private final String method;
    private Map<String, String> headers;

    HttpRequest(Builder builder) {
        this.url = builder.url;
        this.method = builder.method;
        this.headers = builder.headers;
    }

    public String url() {
        return url.toString();
    }

    public String method() {
        return method;
    }

    public Map<String, String> headers() {
        return headers;
    }

    public String header(String key) {
        return headers.get(key);
    }

    @Nullable HttpURLConnection openConnection() {
        HttpURLConnection connection;

        try {
            connection = (HttpURLConnection) url.openConnection();

            connection.setRequestMethod(method);

            for (Map.Entry<String, String> entry : headers.entrySet()) {
                String key = entry.getKey();
                String value = entry.getValue();
                connection.setRequestProperty(key, value);
            }
        } catch (IOException e) {
            connection = null;
        }

        return connection;
    }

    public Builder newBuilder() {
        return new Builder(this);
    }

    public static class Builder {
        URL url;
        String method;
        Map<String, String> headers;

        public Builder() {
            this.method = "GET";
        }

        Builder(HttpRequest request) {
            this.url = request.url;
            this.method = request.method;
        }

        public Builder url(URL url) {
            this.url = url;
            return this;
        }

        public Builder url(String url) {
            if (url.regionMatches(true, 0, "ws:", 0, 3)) {
                url = "http:" + url.substring(3);
            } else if (url.regionMatches(true, 0, "wss:", 0, 4)) {
                url = "https:" + url.substring(4);
            }

            try {
                this.url = new URL(url);
            } catch (MalformedURLException e) {
                this.url = null;
            }

            return this;
        }

        public Builder method(String method) {
            if (method.length() == 0)
                throw new IllegalArgumentException("method.length() == 0");

            this.method = method;

            return this;
        }

        public Builder header(String name, String value) {
            headers.put(name, value);
            return this;
        }

        public HttpRequest build() {
            return new HttpRequest(this);
        }
    }
}
