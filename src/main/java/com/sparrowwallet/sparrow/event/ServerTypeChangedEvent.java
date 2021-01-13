package com.sparrowwallet.sparrow.event;

import com.sparrowwallet.sparrow.net.ServerType;

public class ServerTypeChangedEvent {
    private final ServerType serverType;

    public ServerTypeChangedEvent(ServerType serverType) {
        this.serverType = serverType;
    }

    public ServerType getServerType() {
        return serverType;
    }
}
