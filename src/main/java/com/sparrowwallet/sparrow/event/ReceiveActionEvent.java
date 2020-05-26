package com.sparrowwallet.sparrow.event;

import com.sparrowwallet.sparrow.wallet.NodeEntry;

public class ReceiveActionEvent {
    private NodeEntry receiveEntry;

    public ReceiveActionEvent(NodeEntry receiveEntry) {
        this.receiveEntry = receiveEntry;
    }

    public NodeEntry getReceiveEntry() {
        return receiveEntry;
    }
}
