package com.sparrowwallet.sparrow.control;

import com.google.common.eventbus.Subscribe;
import com.sparrowwallet.drongo.OutputDescriptor;
import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.sparrow.EventManager;
import com.sparrowwallet.sparrow.event.AddressDisplayedEvent;
import com.sparrowwallet.sparrow.io.Device;

import java.util.stream.Collectors;

public class DeviceDisplayAddressDialog extends DeviceDialog<String> {
    private final Wallet wallet;
    private final OutputDescriptor outputDescriptor;

    public DeviceDisplayAddressDialog(Wallet wallet, OutputDescriptor outputDescriptor) {
        super(outputDescriptor.getExtendedPublicKeys().stream().map(extKey -> outputDescriptor.getKeyDerivation(extKey).getMasterFingerprint()).collect(Collectors.toList()));
        this.wallet = wallet;
        this.outputDescriptor = outputDescriptor;

        EventManager.get().register(this);
        setOnCloseRequest(event -> {
            EventManager.get().unregister(this);
        });
    }

    @Override
    protected DevicePane getDevicePane(Device device, boolean defaultDevice) {
        return new DevicePane(wallet, outputDescriptor, device, defaultDevice);
    }

    @Subscribe
    public void addressDisplayed(AddressDisplayedEvent event) {
        setResult(event.getAddress());
    }
}
