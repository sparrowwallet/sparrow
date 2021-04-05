package com.sparrowwallet.sparrow.event;

public class TorExternalStatusEvent extends TorStatusEvent {
    public TorExternalStatusEvent() {
        super("Tor is already running, using external instance...");
    }
}
