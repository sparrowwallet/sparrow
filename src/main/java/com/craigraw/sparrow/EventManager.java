package com.craigraw.sparrow;

import com.craigraw.drongo.protocol.Transaction;

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
