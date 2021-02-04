package com.sparrowwallet.sparrow.event;

import com.sparrowwallet.sparrow.AppServices;

public class ConnectionStartEvent {
    private final String status;

    public ConnectionStartEvent(String serverAddress) {
        this.status = AppServices.isTorRunning() ? "Tor running, connecting to " + serverAddress + "..." : "Connecting to " + serverAddress + "...";
    }

    public String getStatus() {
        return status;
    }
}
