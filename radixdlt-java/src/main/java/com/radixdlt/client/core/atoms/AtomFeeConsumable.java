package com.radixdlt.client.core.atoms;

import com.radixdlt.client.core.address.EUID;
import com.radixdlt.client.core.crypto.ECKeyPair;
import java.util.Set;

public class AtomFeeConsumable extends Consumable {
	public AtomFeeConsumable(long quantity, Set<ECKeyPair> owners, long nonce, EUID assetId) {
		super(quantity, owners, nonce, assetId);
	}
}
