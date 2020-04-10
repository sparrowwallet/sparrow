package com.sparrowwallet.sparrow.event;

import javafx.scene.control.Tab;

public class TransactionTabChangedEvent extends TabEvent {
    private boolean txHexVisible;

    public TransactionTabChangedEvent(Tab tab, boolean txHexVisible) {
        super(tab);
        this.txHexVisible = txHexVisible;
    }

    public boolean isTxHexVisible() {
        return txHexVisible;
    }
}
