/*
 *  Copyright (C) 2018 Radix DLT
 *  This file is part of the Radix DLT Java client - https://www.radixdlt.com
 *
 *  SPDX-License-Identifier: MIT
 *  See the file LICENSE for more information.
 */

package com.radixdlt.client.core.network;

import javax.annotation.Nullable;

public class WebSocketListener {
  public void onOpen(WebSocket webSocket, HttpResponse response) {
  }

  public void onMessage(WebSocket webSocket, byte[] message) {
  }

  public void onClosing(WebSocket webSocket, int code, String reason) {
  }

  public void onClosed(WebSocket webSocket, int code, String reason) {
  }

  public void onFailure(WebSocket webSocket, Throwable t, @Nullable HttpResponse response) {
  }
}
