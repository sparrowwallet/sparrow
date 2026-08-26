package com.sparrowwallet.sparrow.event;

import com.sparrowwallet.drongo.wallet.BlockTransactionHash;
import com.sparrowwallet.drongo.wallet.Wallet;

import java.util.Set;

/**
 * The transactions of one wallet whose confirmed heights the connected server did not prove in a single history pass, aggregated so that a pass
 * surfacing many of them raises one dialog rather than one per transaction.
 */
public abstract class TransactionProofsEvent {
    private final Wallet wallet;
    private final Set<BlockTransactionHash> references;

    public TransactionProofsEvent(Wallet wallet, Set<BlockTransactionHash> references) {
        this.wallet = wallet;
        this.references = references;
    }

    public Wallet getWallet() {
        return wallet;
    }

    /**
     * The transactions with the heights the server reported them at, which are no longer the heights they are held at.
     */
    public Set<BlockTransactionHash> getReferences() {
        return references;
    }
}
