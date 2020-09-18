package com.sparrowwallet.sparrow.event;

public class VersionCheckStatusEvent {
    private final boolean enabled;

    public VersionCheckStatusEvent(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isEnabled() {
        return enabled;
    }
}
