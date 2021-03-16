package com.sparrowwallet.sparrow.event;

import com.sparrowwallet.drongo.protocol.Sha256Hash;
import com.sparrowwallet.drongo.protocol.Transaction;
import com.sparrowwallet.drongo.wallet.BlockTransaction;

import java.util.Map;

public class BlockTransactionFetchedEvent extends TransactionReferencesFinishedEvent {
    private final Sha256Hash txId;
    private final Map<Sha256Hash, BlockTransaction> inputTransactions;

    public BlockTransactionFetchedEvent(Transaction transaction, BlockTransaction blockTransaction, Map<Sha256Hash, BlockTransaction> inputTransactions, int pageStart, int pageEnd) {
        super(transaction, blockTransaction, pageStart, pageEnd);
        this.txId = transaction.getTxId();
        this.inputTransactions = inputTransactions;
    }

    public Sha256Hash getTxId() {
        return txId;
    }

    public Map<Sha256Hash, BlockTransaction> getInputTransactions() {
        return inputTransactions;
    }
}
