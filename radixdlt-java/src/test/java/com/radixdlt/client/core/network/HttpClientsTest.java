package com.radixdlt.client.core.network;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class HttpClientsTest {
	@Test
	public void testClientCreation() {
		RadixHttpClient client = HttpClients.getSslAllTrustingClient();
		for (int i = 0; i < 10; i++) {
			assertTrue(client == HttpClients.getSslAllTrustingClient());
		}
	}
}