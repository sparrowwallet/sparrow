package com.sparrowwallet.sparrow.event;

public class TorReadyStatusEvent extends TorStatusEvent {
    public TorReadyStatusEvent() {
        super("Tor started");
    }
}
