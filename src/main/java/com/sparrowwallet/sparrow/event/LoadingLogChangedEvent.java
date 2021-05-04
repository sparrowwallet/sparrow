package com.sparrowwallet.sparrow.event;

public class LoadingLogChangedEvent {
    private final boolean visible;

    public LoadingLogChangedEvent(boolean visible) {
        this.visible = visible;
    }

    public boolean isVisible() {
        return visible;
    }
}
