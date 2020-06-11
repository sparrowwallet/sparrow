package com.sparrowwallet.sparrow.event;

import com.sparrowwallet.drongo.protocol.Sha256Hash;
import com.sparrowwallet.drongo.wallet.BlockTransaction;

import java.util.Map;

public class BlockTransactionFetchedEvent {
    private final Sha256Hash txId;
    private final BlockTransaction blockTransaction;
    private final Map<Sha256Hash, BlockTransaction> inputTransactions;

    public BlockTransactionFetchedEvent(Sha256Hash txId, BlockTransaction blockTransaction, Map<Sha256Hash, BlockTransaction> inputTransactions) {
        this.txId = txId;
        this.blockTransaction = blockTransaction;
        this.inputTransactions = inputTransactions;
    }

    public Sha256Hash getTxId() {
        return txId;
    }

    public BlockTransaction getBlockTransaction() {
        return blockTransaction;
    }

    public Map<Sha256Hash, BlockTransaction> getInputTransactions() {
        return inputTransactions;
    }
}
