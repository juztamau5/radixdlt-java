/*
 *  Copyright (C) 2018 Radix DLT
 *  This file is part of the Radix DLT Java client - https://www.radixdlt.com
 *
 *  SPDX-License-Identifier: MIT
 *  See the file LICENSE for more information.
 */

package com.radixdlt.client.core.network;

import java.io.IOException;

public interface HttpCallback {
    void onFailure(HttpCall call, IOException e);
    void onResponse(HttpCall call, HttpResponse response) throws IOException;
}
