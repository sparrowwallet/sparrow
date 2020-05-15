package com.sparrowwallet.sparrow.event;

import javafx.scene.control.Tab;

public class WalletTabSelectedEvent extends TabSelectedEvent {
    public WalletTabSelectedEvent(Tab tab) {
        super(tab);
    }
}
