package com.radixdlt.client.application;

import com.radixdlt.client.application.actions.DataStore;
import com.radixdlt.client.application.actions.TokenTransfer;
import com.radixdlt.client.application.actions.UniqueProperty;
import com.radixdlt.client.application.objects.Data;
import com.radixdlt.client.application.objects.UnencryptedData;
import com.radixdlt.client.application.translate.DataStoreTranslator;
import com.radixdlt.client.application.translate.TokenTransferTranslator;
import com.radixdlt.client.application.translate.UniquePropertyTranslator;
import com.radixdlt.client.assets.Amount;
import com.radixdlt.client.assets.Asset;
import com.radixdlt.client.core.RadixUniverse;
import com.radixdlt.client.core.address.RadixAddress;
import com.radixdlt.client.core.atoms.ApplicationPayloadAtom;
import com.radixdlt.client.core.atoms.AtomBuilder;
import com.radixdlt.client.core.atoms.Consumable;
import com.radixdlt.client.core.atoms.TransactionAtom;
import com.radixdlt.client.application.identity.RadixIdentity;
import com.radixdlt.client.core.crypto.ECPublicKey;
import com.radixdlt.client.core.ledger.RadixLedger;
import com.radixdlt.client.core.network.AtomSubmissionUpdate;
import com.radixdlt.client.core.network.AtomSubmissionUpdate.AtomSubmissionState;
import com.radixdlt.client.application.translate.ConsumableDataSource;
import com.radixdlt.client.application.translate.TransactionAtoms;
import io.reactivex.Completable;
import io.reactivex.Observable;
import io.reactivex.Single;
import io.reactivex.annotations.Nullable;
import io.reactivex.observables.ConnectableObservable;
import java.util.Collection;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * The Radix Dapp API, a high level api which dapps can utilize. The class hides
 * the complexity of Atoms and cryptography and exposes a simple high level interface.
 */
public class RadixApplicationAPI {
	public static class Result {
		private final Observable<AtomSubmissionUpdate> updates;
		private final Completable completable;

		private Result(Observable<AtomSubmissionUpdate> updates) {
			this.updates = updates;

			this.completable = updates.filter(AtomSubmissionUpdate::isComplete)
				.firstOrError()
				.flatMapCompletable(update -> {
					if (update.getState() == AtomSubmissionState.STORED) {
						return Completable.complete();
					} else {
						return Completable.error(new RuntimeException(update.getMessage()));
					}
				});
		}

		public Observable<AtomSubmissionUpdate> toObservable() {
			return updates;
		}

		public Completable toCompletable() {
			return completable;
		}
	}


	private final RadixIdentity identity;
	private final RadixLedger ledger;
	private final DataStoreTranslator dataStoreTranslator;
	private final TokenTransferTranslator tokenTransferTranslator;
	private final UniquePropertyTranslator uniquePropertyTranslator;
	private final Supplier<AtomBuilder> atomBuilderSupplier;
	private final ConsumableDataSource consumableDataSource;

	private RadixApplicationAPI(RadixIdentity identity, RadixUniverse universe, Supplier<AtomBuilder> atomBuilderSupplier) {
		this.identity = identity;
		this.ledger = universe.getLedger();
		this.consumableDataSource = new ConsumableDataSource(ledger);
		this.dataStoreTranslator = DataStoreTranslator.getInstance();
		this.tokenTransferTranslator = new TokenTransferTranslator(universe, consumableDataSource);
		this.atomBuilderSupplier = atomBuilderSupplier;
		this.uniquePropertyTranslator = new UniquePropertyTranslator();
	}

	public static RadixApplicationAPI create(RadixIdentity identity) {
		Objects.requireNonNull(identity);
		return create(identity, RadixUniverse.getInstance(), AtomBuilder::new);
	}

	public static RadixApplicationAPI create(RadixIdentity identity, RadixUniverse universe, Supplier<AtomBuilder> atomBuilderSupplier) {
		Objects.requireNonNull(identity);
		Objects.requireNonNull(universe);
		Objects.requireNonNull(atomBuilderSupplier);
		return new RadixApplicationAPI(identity, universe, atomBuilderSupplier);
	}

	public ECPublicKey getMyPublicKey() {
		return identity.getPublicKey();
	}

	public RadixIdentity getMyIdentity() {
		return identity;
	}

	public RadixAddress getMyAddress() {
		return ledger.getAddressFromPublicKey(identity.getPublicKey());
	}

	public Observable<UnencryptedData> getReadableData(RadixAddress address) {
		Objects.requireNonNull(address);

		return ledger.getAllAtoms(address.getUID(), ApplicationPayloadAtom.class)
			.map(dataStoreTranslator::fromAtom)
			.flatMapMaybe(data -> identity.decrypt(data).toMaybe().onErrorComplete());
	}

	public Result storeData(Data data) {
		return this.storeData(data, getMyAddress());
	}

	public Result storeData(Data data, RadixAddress address) {
		DataStore dataStore = new DataStore(data, address);

		AtomBuilder atomBuilder = atomBuilderSupplier.get();
		ConnectableObservable<AtomSubmissionUpdate> updates = dataStoreTranslator.translate(dataStore, atomBuilder)
			.andThen(Single.fromCallable(() -> atomBuilder.buildWithPOWFee(ledger.getMagic(), address.getPublicKey())))
			.flatMap(identity::sign)
			.flatMapObservable(ledger::submitAtom)
			.replay();

		updates.connect();

		return new Result(updates);
	}

	public Result storeData(Data data, RadixAddress address0, RadixAddress address1) {
		DataStore dataStore = new DataStore(data, address0, address1);

		AtomBuilder atomBuilder = atomBuilderSupplier.get();
		ConnectableObservable<AtomSubmissionUpdate> updates = dataStoreTranslator.translate(dataStore, atomBuilder)
			.andThen(Single.fromCallable(() -> atomBuilder.buildWithPOWFee(ledger.getMagic(), address0.getPublicKey())))
			.flatMap(identity::sign)
			.flatMapObservable(ledger::submitAtom)
			.replay();

		updates.connect();

		return new Result(updates);
	}

	public Observable<TokenTransfer> getMyTokenTransfers(Asset tokenClass) {
		return getTokenTransfers(getMyAddress(), tokenClass);
	}

	public Observable<TokenTransfer> getTokenTransfers(RadixAddress address, Asset tokenClass) {
		Objects.requireNonNull(address);
		Objects.requireNonNull(tokenClass);

		return Observable.combineLatest(
			Observable.fromCallable(() -> new TransactionAtoms(address, tokenClass.getId())),
			ledger.getAllAtoms(address.getUID(), TransactionAtom.class),
			(transactionAtoms, atom) ->
				transactionAtoms.accept(atom)
					.getNewValidTransactions()
		)
		.flatMap(atoms -> atoms.map(tokenTransferTranslator::fromAtom));
	}

	public Observable<Amount> getMyBalance(Asset tokenClass) {
		return getBalance(getMyAddress(), tokenClass);
	}

	public Observable<Amount> getBalance(RadixAddress address, Asset tokenClass) {
		Objects.requireNonNull(address);
		Objects.requireNonNull(tokenClass);

		return this.consumableDataSource.getConsumables(address)
			.map(Collection::stream)
			.map(stream -> stream
				.filter(consumable -> consumable.getAssetId().equals(tokenClass.getId()))
				.mapToLong(Consumable::getQuantity)
				.sum()
			)
			.map(balanceInSubUnits -> Amount.subUnitsOf(balanceInSubUnits, tokenClass))
			.share();
	}

	/**
	 * Sends an amount of a token to an address
	 *
	 * @param to the address to send tokens to
	 * @param amount the amount and token type
	 * @return result of the transaction
	 */
	public Result sendTokens(RadixAddress to, Amount amount) {
		return transferTokens(getMyAddress(), to, amount);
	}

	/**
	 * Sends an amount of a token with a data attachment to an address
	 *
	 * @param to the address to send tokens to
	 * @param amount the amount and token type
	 * @param attachment the data attached to the transaction
	 * @return result of the transaction
	 */
	public Result sendTokens(RadixAddress to, Amount amount, @Nullable Data attachment) {
		return transferTokens(getMyAddress(), to, amount, attachment);
	}

	/**
	 * Sends an amount of a token with a data attachment to an address with a unique property
	 * meaning that no other transaction can be executed with the same unique bytes
	 *
	 * @param to the address to send tokens to
	 * @param amount the amount and token type
	 * @param attachment the data attached to the transaction
	 * @param unique the bytes representing the unique id of this transaction
	 * @return result of the transaction
	 */
	public Result sendTokens(RadixAddress to, Amount amount, @Nullable Data attachment, @Nullable byte[] unique) {
		return transferTokens(getMyAddress(), to, amount, attachment, unique);
	}

	public Result transferTokens(RadixAddress from, RadixAddress to, Amount amount) {
		return transferTokens(from, to, amount, null, null);
	}

	public Result transferTokens(
		RadixAddress from,
		RadixAddress to,
		Amount amount,
		@Nullable Data attachment
	) {
		return transferTokens(from, to, amount, attachment, null);
	}

	public Result transferTokens(
		RadixAddress from,
		RadixAddress to,
		Amount amount,
		@Nullable Data attachment,
		@Nullable byte[] unique // TODO: make unique immutable
	) {
		Objects.requireNonNull(from);
		Objects.requireNonNull(to);
		Objects.requireNonNull(amount);

		final TokenTransfer tokenTransfer = TokenTransfer.create(from, to, amount.getTokenClass(), amount.getAmountInSubunits(), attachment);
		final UniqueProperty uniqueProperty;
		if (unique != null) {
			// Unique Property must be the from address so that all validation occurs in a single shard.
			// Once multi-shard validation is implemented this constraint can be removed.
			uniqueProperty = new UniqueProperty(unique, from);
		} else {
			uniqueProperty = null;
		}

		return executeTransaction(tokenTransfer, uniqueProperty);
	}

	// TODO: make this more generic
	private Result executeTransaction(TokenTransfer tokenTransfer, @Nullable UniqueProperty uniqueProperty) {
		Objects.requireNonNull(tokenTransfer);

		AtomBuilder atomBuilder = atomBuilderSupplier.get();

		ConnectableObservable<AtomSubmissionUpdate> updates =
			uniquePropertyTranslator.translate(uniqueProperty, atomBuilder)
			.andThen(tokenTransferTranslator.translate(tokenTransfer, atomBuilder))
			.andThen(Single.fromCallable(() -> atomBuilder.buildWithPOWFee(ledger.getMagic(), tokenTransfer.getFrom().getPublicKey())))
			.flatMap(identity::sign)
			.flatMapObservable(ledger::submitAtom)
			.replay();

		updates.connect();

		return new Result(updates);
	}
}
