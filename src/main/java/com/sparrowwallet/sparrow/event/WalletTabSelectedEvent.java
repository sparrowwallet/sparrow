package com.sparrowwallet.sparrow.event;

import com.sparrowwallet.sparrow.WalletTabData;
import javafx.scene.control.Tab;

public class WalletTabSelectedEvent extends TabSelectedEvent {
    public WalletTabSelectedEvent(Tab tab) {
        super(tab);
    }

    public WalletTabData getWalletTabData() {
        return (WalletTabData)getTabData();
    }
}
