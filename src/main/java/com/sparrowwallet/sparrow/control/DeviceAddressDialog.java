package com.sparrowwallet.sparrow.control;

import com.google.common.eventbus.Subscribe;
import com.sparrowwallet.drongo.KeyDerivation;
import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.sparrow.EventManager;
import com.sparrowwallet.sparrow.event.AddressDisplayedEvent;
import com.sparrowwallet.sparrow.io.Device;

import java.util.List;

public class DeviceAddressDialog extends DeviceDialog<String> {
    private final Wallet wallet;
    private final KeyDerivation keyDerivation;

    public DeviceAddressDialog(List<Device> devices, Wallet wallet, KeyDerivation keyDerivation) {
        super(devices);
        this.wallet = wallet;
        this.keyDerivation = keyDerivation;

        EventManager.get().register(this);
        setOnCloseRequest(event -> {
            EventManager.get().unregister(this);
        });
    }

    @Override
    protected DevicePane getDevicePane(Device device) {
        return new DevicePane(wallet, keyDerivation, device);
    }

    @Subscribe
    public void addressDisplayed(AddressDisplayedEvent event) {
        setResult(event.getAddress());
        this.close();
    }
}
