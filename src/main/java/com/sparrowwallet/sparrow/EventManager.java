package com.sparrowwallet.sparrow;

import com.sparrowwallet.drongo.protocol.Transaction;
import com.sparrowwallet.sparrow.transaction.TransactionListener;

import java.util.ArrayList;
import java.util.List;

public class EventManager {
    private List<TransactionListener> listenerList = new ArrayList<>();
    private static EventManager SINGLETON = new EventManager();

    private EventManager() {}

    public void subscribe(TransactionListener listener) {
        listenerList.add(listener);
    }

    public void unsubscribe(TransactionListener listener) {
        listenerList.remove(listener);
    }

    public void notify(Transaction transaction) {
        for (TransactionListener listener : listenerList) {
            listener.updated(transaction);
        }
    }

    public static EventManager get() {
        return SINGLETON;
    }
}
