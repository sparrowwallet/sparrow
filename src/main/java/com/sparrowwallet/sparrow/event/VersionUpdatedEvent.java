package com.sparrowwallet.sparrow.event;

public class VersionUpdatedEvent {
    private final String version;

    public VersionUpdatedEvent(String version) {
        this.version = version;
    }

    public String getVersion() {
        return version;
    }
}
