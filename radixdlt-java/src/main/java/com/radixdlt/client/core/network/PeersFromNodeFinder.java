package com.radixdlt.client.core.network;

import io.reactivex.Observable;
import io.reactivex.Single;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

public class PeersFromNodeFinder implements PeerDiscovery {
	private final String nodeFinderUrl;
	private final int port;

	public PeersFromNodeFinder(String url, int port) {
		this.nodeFinderUrl = url;
		this.port = port;
	}

	public Observable<RadixPeer> findPeers() {
		HttpRequest request = new HttpRequest.Builder()
			.url(this.nodeFinderUrl)
			.build();

		return Single.<String>create(emitter -> {
				HttpCall call = HttpClients.getSslAllTrustingClient().newCall(request);
				emitter.setCancellable(call::cancel);
				call.enqueue(new HttpCallback() {
					@Override
					public void onFailure(HttpCall call, IOException e) {
						emitter.tryOnError(e);
					}

					@Override
					public void onResponse(HttpCall call, HttpResponse response) throws IOException {
						HttpResponseBody body = response.body();
						if (response.isSuccessful() && body != null) {
							String bodyString = body.string();
							body.close();
							if (bodyString.isEmpty()) {
								emitter.tryOnError(new IOException("Received empty peer."));
							} else {
								emitter.onSuccess(bodyString);
							}
						} else {
							emitter.tryOnError(new IOException("Error retrieving peer: " + response.message()));
						}
					}
				});
			})
			.map(peerUrl -> new PeersFromSeed(new RadixPeer(peerUrl, true, port)))
			.flatMapObservable(PeersFromSeed::findPeers)
			.timeout(3, TimeUnit.SECONDS)
			.retryWhen(new IncreasingRetryTimer());
	}
}
