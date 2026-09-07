package com.sparrowwallet.sparrow.event;

import com.sparrowwallet.drongo.wallet.BlockTransactionHash;
import com.sparrowwallet.drongo.wallet.Wallet;

import java.util.Set;

/**
 * The transactions whose confirmed heights the connected server did not prove, aggregated per wallet so that a history pass surfacing many of them
 * raises one dialog rather than one per transaction. A transaction reached outside any wallet is raised on its own, under a null wallet.
 */
public abstract class TransactionProofsEvent {
    private final Wallet wallet;
    private final Set<BlockTransactionHash> references;

    public TransactionProofsEvent(Wallet wallet, Set<BlockTransactionHash> references) {
        this.wallet = wallet;
        this.references = references;
    }

    /**
     * The wallet whose history holds these transactions, or null for one reached outside any wallet, where there is no history to refresh.
     */
    public Wallet getWallet() {
        return wallet;
    }

    /**
     * The transactions with the heights the server reported them at. A wallet no longer holds them at those heights, having demoted them; one reached
     * outside a wallet is still shown at its, marked as unproven.
     */
    public Set<BlockTransactionHash> getReferences() {
        return references;
    }
}
