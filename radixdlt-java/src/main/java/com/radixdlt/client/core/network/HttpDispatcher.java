/*
 *  Copyright (C) 2018 Radix DLT
 *  This file is part of the Radix DLT Java client - https://www.radixdlt.com
 *
 *  SPDX-License-Identifier: MIT
 *  See the file LICENSE for more information.
 */

package com.radixdlt.client.core.network;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import javax.annotation.Nullable;

public final class HttpDispatcher {
    private int maxRequests = 64;
    private @Nullable ExecutorService executorService;
    private final Deque<HttpCall> readyAsyncCalls = new ArrayDeque<>();
    private final Deque<HttpCall> runningAsyncCalls = new ArrayDeque<>();

    public HttpDispatcher() {
    }

    public synchronized ExecutorService executorService() {
        if (executorService == null) {
            executorService = new ThreadPoolExecutor(0, Integer.MAX_VALUE, 60, TimeUnit.SECONDS,
                    new SynchronousQueue<Runnable>());
        }
        return executorService;
    }

    synchronized void enqueue(HttpCall call) {
        if (runningAsyncCalls.size() < maxRequests) {
            runningAsyncCalls.add(call);
            executorService().execute(call);
        } else {
            readyAsyncCalls.add(call);
        }
    }

    synchronized void executed(HttpCall call) {
        runningSyncCalls.add(call);
    }

    void finished(HttpCall call) {
        // TODO
        // finished(runningAsyncCalls, call, true);
    }
}
