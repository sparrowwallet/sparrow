package com.sparrowwallet.sparrow.event;

public class HideEmptyUsedAddressesStatusEvent {
    private final boolean hideEmptyUsedAddresses;

    public HideEmptyUsedAddressesStatusEvent(boolean hideEmptyUsedAddresses) {
        this.hideEmptyUsedAddresses = hideEmptyUsedAddresses;
    }

    public boolean isHideEmptyUsedAddresses() {
        return hideEmptyUsedAddresses;
    }
}
