package com.sparrowwallet.sparrow;

import com.sparrowwallet.drongo.protocol.Transaction;

public interface TransactionListener {
    void updated(Transaction transaction);
}
