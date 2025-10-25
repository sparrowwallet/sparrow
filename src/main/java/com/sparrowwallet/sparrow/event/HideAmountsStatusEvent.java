package com.sparrowwallet.sparrow.event;

public class HideAmountsStatusEvent {
    private final boolean hideAmounts;

    public HideAmountsStatusEvent(boolean hideAmounts) {
        this.hideAmounts = hideAmounts;
    }

    public boolean isHideAmounts() {
        return hideAmounts;
    }
}
