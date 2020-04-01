package com.sparrowwallet.sparrow.transaction;

import com.sparrowwallet.drongo.protocol.Transaction;

public interface TransactionListener {
    void updated(Transaction transaction);
}
