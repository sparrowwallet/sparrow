package com.sparrowwallet.sparrow.event;

import com.sparrowwallet.drongo.protocol.Sha256Hash;
import com.sparrowwallet.drongo.protocol.Transaction;
import com.sparrowwallet.drongo.wallet.BlockTransaction;

import java.util.List;

public class BlockTransactionOutputsFetchedEvent extends TransactionReferencesEvent {
    private final Sha256Hash txId;
    private final List<BlockTransaction> outputTransactions;

    public BlockTransactionOutputsFetchedEvent(Transaction transaction, List<BlockTransaction> outputTransactions, int pageStart, int pageEnd) {
        super(transaction, pageStart, pageEnd);
        this.txId = transaction.getTxId();
        this.outputTransactions = outputTransactions;
    }

    public Sha256Hash getTxId() {
        return txId;
    }

    public List<BlockTransaction> getOutputTransactions() {
        return outputTransactions;
    }
}
