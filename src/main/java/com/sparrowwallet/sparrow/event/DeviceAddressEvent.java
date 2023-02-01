package com.sparrowwallet.sparrow.event;

import com.sparrowwallet.drongo.address.Address;

public class DeviceAddressEvent {
    private final Address address;

    public DeviceAddressEvent(Address address) {
        this.address = address;
    }

    public Address getAddress() {
        return address;
    }
}
