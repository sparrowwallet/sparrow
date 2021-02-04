package com.sparrowwallet.sparrow.event;

public class TorBootStatusEvent extends TorStatusEvent {
    public TorBootStatusEvent() {
        super("Starting Tor...");
    }
}
