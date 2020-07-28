package com.sparrowwallet.sparrow.event;

import com.sparrowwallet.drongo.protocol.Transaction;
import com.sparrowwallet.drongo.psbt.PSBT;

/**
 * This event is fired when a final transaction has been extracted from a PSBT
 */
public class TransactionExtractedEvent extends PSBTEvent {
    private final Transaction finalTransaction;

    public TransactionExtractedEvent(PSBT psbt, Transaction finalTransaction) {
        super(psbt);
        this.finalTransaction = finalTransaction;
    }

    public Transaction getFinalTransaction() {
        return finalTransaction;
    }
}
