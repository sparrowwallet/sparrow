package com.sparrowwallet.sparrow.event;

public class UtxosChartChangedEvent {
    private final boolean visible;

    public UtxosChartChangedEvent(boolean visible) {
        this.visible = visible;
    }

    public boolean isVisible() {
        return visible;
    }
}
