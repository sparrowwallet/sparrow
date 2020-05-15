package com.sparrowwallet.sparrow.event;

import javafx.scene.control.Tab;

public class TransactionTabSelectedEvent extends TabSelectedEvent {
    public TransactionTabSelectedEvent(Tab tab) {
        super(tab);
    }
}
