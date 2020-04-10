package com.sparrowwallet.sparrow.event;

import javafx.scene.control.Tab;

public class TransactionTabSelectedEvent extends TabEvent {
    public TransactionTabSelectedEvent(Tab tab) {
        super(tab);
    }
}
