package com.sparrowwallet.sparrow.event;

import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.sparrow.wallet.NodeEntry;

public class ReceiveActionEvent {
    private final Wallet wallet;

    public ReceiveActionEvent(NodeEntry receiveEntry) {
        this.wallet = receiveEntry.getWallet();
    }

    public ReceiveActionEvent(Wallet wallet) {
        this.wallet = wallet;
    }

    public Wallet getWallet() {
        return wallet;
    }
}
