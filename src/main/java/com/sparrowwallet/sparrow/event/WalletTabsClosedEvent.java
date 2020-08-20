package com.sparrowwallet.sparrow.event;

import com.sparrowwallet.sparrow.WalletTabData;

import java.util.List;

public class WalletTabsClosedEvent {
    private final List<WalletTabData> closedWalletTabData;

    public WalletTabsClosedEvent(List<WalletTabData> closedWalletTabData) {
        this.closedWalletTabData = closedWalletTabData;
    }

    public List<WalletTabData> getClosedWalletTabData() {
        return closedWalletTabData;
    }
}
