package com.sparrowwallet.sparrow.event;

import com.sparrowwallet.sparrow.wallet.NodeEntry;

public class ReceiveToEvent {
    private final NodeEntry receiveEntry;

    public ReceiveToEvent(NodeEntry receiveEntry) {
        this.receiveEntry = receiveEntry;
    }

    public NodeEntry getReceiveEntry() {
        return receiveEntry;
    }
}
