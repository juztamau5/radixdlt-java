package com.radixdlt.client.core.network;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import com.radixdlt.client.core.address.EUID;
import com.radixdlt.client.core.network.AtomSubmissionUpdate.AtomSubmissionState;
import com.radixdlt.client.core.network.WebSocketClient.RadixClientStatus;
import com.radixdlt.client.core.serialization.RadixJson;
import io.reactivex.Maybe;
import io.reactivex.Observable;
import io.reactivex.Single;
import io.reactivex.disposables.Disposable;
import java.util.List;
import com.radixdlt.client.core.atoms.Atom;

import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Responsible for managing the state across one web socket connection to a Radix Node.
 * This consists of mainly keeping track of JSON-RPC method calls and JSON-RPC subscription
 * calls.
 */
public class RadixJsonRpcClient {
	private static final Logger LOGGER = LoggerFactory.getLogger(RadixJsonRpcClient.class);

	/**
	 * The websocket this is wrapping
	 */
	private final WebSocketClient wsClient;

	/**
	 * Hot observable of messages received through the websocket
	 */
	private final Observable<JsonObject> messages;

	public RadixJsonRpcClient(WebSocketClient wsClient) {
		this.wsClient = wsClient;

		final JsonParser parser = new JsonParser();
		this.messages = this.wsClient.getMessages()
			.map(msg -> parser.parse(new String(msg)).getAsJsonObject())
			.publish()
			.refCount();
	}

	/**
	 * @return URL which websocket is connected to
	 */
	public String getLocation() {
		return wsClient.getEndpoint().url().toString();
	}

	public Observable<RadixClientStatus> getStatus() {
		return wsClient.getStatus();
	}

	/**
	 * Attempts to close the websocket this client is connected to.
	 * If there are still observers connected to the websocket closing
	 * will not occur.
	 *
	 * @return true if websocket was successfully closed, false otherwise
	 */
	public boolean tryClose() {
		return this.wsClient.close();
	}

	/**
	 * Generic helper method for calling a JSON-RPC method. Deserializes the received json.
	 *
	 * @param method name of JSON-RPC method
	 * @return response from rpc method
	 */
	private Single<JsonElement> jsonRpcCall(String method, JsonObject params) {
		return this.wsClient.connect().andThen(
			Single.<JsonElement>create(emitter -> {
				final String uuid = UUID.randomUUID().toString();

				JsonObject requestObject = new JsonObject();
				requestObject.addProperty("id", uuid);
				requestObject.addProperty("method", method);
				requestObject.add("params", params);

				messages
					.filter(msg -> msg.has("id"))
					.filter(msg -> msg.get("id").getAsString().equals(uuid))
					.firstOrError()
					.doOnSubscribe(disposable -> {
						boolean sendSuccess = wsClient.send(RadixJson.getGson().toJson(requestObject));
						if (!sendSuccess) {
							disposable.dispose();
							emitter.onError(new RuntimeException("Could not connect."));
						}
					})
					.subscribe(msg -> {
						final JsonObject received = msg.getAsJsonObject();
						if (received.has("result")) {
							emitter.onSuccess(received.get("result"));
						} else if (received.has("error")) {
							emitter.onError(new RuntimeException(received.toString()));
						} else {
							emitter.onError(
								new RuntimeException("Received bad json rpc message: " + received.toString())
							);
						}
					});
			})
		);
	}

	/**
	 * Generic helper method for calling a JSON-RPC method with no parameters. Deserializes the received json.
	 *
	 * @param method name of JSON-RPC method
	 * @return response from rpc method
	 */
	private Single<JsonElement> jsonRpcCall(String method) {
		return this.jsonRpcCall(method, new JsonObject());
	}

	/**
	 * Retrieve the node data for node we are connected to
	 *
	 * @return node data for node we are connected to
	 */
	public Single<NodeRunnerData> getSelf() {
		return this.jsonRpcCall("Network.getSelf")
			.map(result -> RadixJson.getGson().fromJson(result, NodeRunnerData.class));
	}

	/**
	 * Retrieve list of nodes this node knows about
	 *
	 * @return list of nodes this node knows about
	 */
	public Single<List<NodeRunnerData>> getLivePeers() {
		return this.jsonRpcCall("Network.getLivePeers")
			.map(result -> RadixJson.getGson().fromJson(result, new TypeToken<List<NodeRunnerData>>() { }.getType()));
	}


	/**
	 * Connects to this Radix Node if not already connected and queries for an atom by HID.
	 * If the node does not carry the atom (e.g. if it does not reside on the same shard) then
	 * this method will return an empty Maybe.
	 *
	 * @param hid the hash id of the atom being queried
	 * @return the atom if found, if not, return an empty Maybe
	 */
	public Maybe<Atom> getAtom(EUID hid) {
		JsonObject params = new JsonObject();
		params.addProperty("hid", hid.toString());

		return this.jsonRpcCall("Ledger.getAtoms", params)
			.<List<Atom>>map(result -> RadixJson.getGson().fromJson(result, new TypeToken<List<Atom>>() { }.getType()))
			.flatMapMaybe(list -> list.isEmpty() ? Maybe.empty() : Maybe.just(list.get(0)));
	}

	/**
	 * Generic helper method for creating a subscription via JSON-RPC.
	 *
	 * @param method name of subscription method
	 * @param notificationMethod name of the JSON-RPC notification method
	 * @return Observable of emitted subscription json elements
	 */
	public Observable<JsonElement> jsonRpcSubscribe(String method, String notificationMethod) {
		return this.jsonRpcSubscribe(method, new JsonObject(), notificationMethod);
	}

	/**
	 * Generic helper method for creating a subscription via JSON-RPC.
	 *
	 * @param method name of subscription method
	 * @param rawParams parameters to subscription method
	 * @param notificationMethod name of the JSON-RPC notification method
	 * @return Observable of emitted subscription json elements
	 */
	public Observable<JsonElement> jsonRpcSubscribe(String method, JsonObject rawParams, String notificationMethod) {
		return this.wsClient.connect().andThen(
			Observable.create(emitter -> {
				final String subscriberId = UUID.randomUUID().toString();
				final JsonObject params = rawParams.deepCopy();
				params.addProperty("subscriberId", subscriberId);

				Disposable subscriptionDisposable = messages
					.filter(msg -> msg.has("method"))
					.filter(msg -> msg.get("method").getAsString().equals(notificationMethod))
					.map(msg -> msg.get("params").getAsJsonObject())
					.filter(p -> p.get("subscriberId").getAsString().equals(subscriberId))
					.subscribe(
						emitter::onNext,
						emitter::onError
					);

				Disposable methodDisposable = this.jsonRpcCall(method, params)
					.subscribe(
						msg -> { },
						emitter::onError
					);

				emitter.setCancellable(() -> {
					methodDisposable.dispose();
					subscriptionDisposable.dispose();

					final String cancelUuid = UUID.randomUUID().toString();
					JsonObject cancelObject = new JsonObject();
					cancelObject.addProperty("id", cancelUuid);
					cancelObject.addProperty("method", "Subscription.cancel");
					JsonObject cancelParams = new JsonObject();
					cancelParams.addProperty("subscriberId", subscriberId);
					cancelObject.add("params", cancelParams);
					wsClient.send(RadixJson.getGson().toJson(cancelObject));
				});
			})
		);
	}

	/**
	 *  Retrieves all atoms from a node specified by a query. This includes all past
	 *  and future atoms. The Observable returned will never complete.
	 *
	 * @param atomQuery query specifying which atoms to retrieve
	 * @param <T> atom type
	 * @return observable of atoms
	 */
	public <T extends Atom> Observable<T> getAtoms(AtomQuery<T> atomQuery) {
		final JsonObject params = new JsonObject();
		params.add("query", atomQuery.toJson());

		return this.jsonRpcSubscribe("Atoms.subscribe", params, "Atoms.subscribeUpdate")
			.map(p -> p.getAsJsonObject().get("atoms").getAsJsonArray())
			.flatMapIterable(array -> array)
			.map(JsonElement::getAsJsonObject)
			.map(jsonAtom -> RadixJson.getGson().fromJson(jsonAtom, atomQuery.getAtomClass()))
			.map(atom -> {
				atom.putDebug("RECEIVED", System.currentTimeMillis());
				return atom;
			});
	}

	/**
	 * Attempt to submit an atom to a node. Returns the status of the atom as it
	 * gets stored on the node.
	 *
	 * @param atom the atom to submit
	 * @param <T> the type of atom
	 * @return observable of the atom as it gets stored
	 */
	public <T extends Atom> Observable<AtomSubmissionUpdate> submitAtom(T atom) {
		return this.wsClient.connect().andThen(
			Observable.<AtomSubmissionUpdate>create(emitter -> {
				JsonElement jsonAtom = RadixJson.getGson().toJsonTree(atom, Atom.class);

				final String subscriberId = UUID.randomUUID().toString();
				JsonObject params = new JsonObject();
				params.addProperty("subscriberId", subscriberId);
				params.add("atom", jsonAtom);

				Disposable subscriptionDisposable = messages
					.filter(msg -> msg.has("method"))
					.filter(msg -> msg.get("method").getAsString().equals("AtomSubmissionState.onNext"))
					.map(msg -> msg.get("params").getAsJsonObject())
					.filter(p -> p.get("subscriberId").getAsString().equals(subscriberId))
					.map(p -> {
						final AtomSubmissionState state = AtomSubmissionState.valueOf(p.get("value").getAsString());
						final String message;
						if (p.has("message")) {
							message = p.get("message").getAsString();
						} else {
							message = null;
						}
						return AtomSubmissionUpdate.now(atom.getHid(), state, message);
					})
					.takeUntil(AtomSubmissionUpdate::isComplete)
					.subscribe(
						emitter::onNext,
						emitter::onError,
						emitter::onComplete
					);


				Disposable methodDisposable = this.jsonRpcCall("Universe.submitAtomAndSubscribe", params)
					.doOnSubscribe(
						disposable -> emitter.onNext(
							AtomSubmissionUpdate.now(atom.getHid(), AtomSubmissionState.SUBMITTING)
						)
					)
					.subscribe(
						msg -> emitter.onNext(AtomSubmissionUpdate.now(atom.getHid(), AtomSubmissionState.SUBMITTED)),
						throwable -> {
							emitter.onNext(
								AtomSubmissionUpdate.now(
									atom.getHid(),
									AtomSubmissionState.FAILED,
									throwable.getMessage()
								)
							);
							emitter.onComplete();
						}
					);

				emitter.setCancellable(() -> {
					methodDisposable.dispose();
					subscriptionDisposable.dispose();
				});
			})
		);
	}
}
