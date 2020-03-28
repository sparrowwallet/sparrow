package com.craigraw.sparrow;

import com.craigraw.drongo.protocol.Transaction;

public interface TransactionListener {
    void updated(Transaction transaction);
}
