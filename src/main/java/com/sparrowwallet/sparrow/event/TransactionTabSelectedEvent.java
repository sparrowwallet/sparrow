package com.sparrowwallet.sparrow.event;

import com.sparrowwallet.sparrow.TransactionTabData;
import javafx.scene.control.Tab;

public class TransactionTabSelectedEvent extends TabSelectedEvent {
    public TransactionTabSelectedEvent(Tab tab) {
        super(tab);
    }

    public TransactionTabData getTransactionTabData() {
        return (TransactionTabData)getTabData();
    }
}
