package com.sparrowwallet.sparrow.event;

import com.sparrowwallet.drongo.wallet.Wallet;

public class ReceiveActionEvent {
    private Wallet.Node receiveNode;

    public ReceiveActionEvent(Wallet.Node receiveNode) {
        this.receiveNode = receiveNode;
    }

    public Wallet.Node getReceiveNode() {
        return receiveNode;
    }
}
