package com.sparrowwallet.sparrow.event;

import com.sparrowwallet.drongo.protocol.Sha256Hash;
import com.sparrowwallet.drongo.wallet.Wallet;

import java.util.Set;

/**
 * Posted where the connected server reported unconfirmed transactions spending from a wallet that are not validly signed by it, which have been left
 * out of its history. Once per transaction per session.
 * <p>
 * Dispatched on the thread that fetched the history, so a handler must hop to the application thread itself.
 */
public class UnsignedTransactionsEvent {
    private final Wallet wallet;
    private final Set<Sha256Hash> txids;

    public UnsignedTransactionsEvent(Wallet wallet, Set<Sha256Hash> txids) {
        this.wallet = wallet;
        this.txids = txids;
    }

    public Wallet getWallet() {
        return wallet;
    }

    public Set<Sha256Hash> getTxids() {
        return txids;
    }
}
