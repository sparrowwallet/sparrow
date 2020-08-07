package com.sparrowwallet.sparrow.event;

/**
 * This event is used by the DeviceAddressDialog to indicate that a USB device has displayed an address
 *
 */
public class AddressDisplayedEvent {
    private final String address;

    public AddressDisplayedEvent(String address) {
        this.address = address;
    }

    public String getAddress() {
        return address;
    }
}
