package com.sparrowwallet.sparrow.control;

import com.google.common.eventbus.Subscribe;
import com.sparrowwallet.drongo.KeyDerivation;
import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.sparrow.EventManager;
import com.sparrowwallet.sparrow.event.MessageSignedEvent;
import com.sparrowwallet.sparrow.io.Device;

import java.util.List;

public class DeviceSignMessageDialog extends DeviceDialog<String> {
    private final Wallet wallet;
    private final String message;
    private final KeyDerivation keyDerivation;

    public DeviceSignMessageDialog(List<String> operationFingerprints, Wallet wallet, String message, KeyDerivation keyDerivation) {
        super(operationFingerprints);
        this.wallet = wallet;
        this.message = message;
        this.keyDerivation = keyDerivation;
        EventManager.get().register(this);
        setOnCloseRequest(event -> {
            EventManager.get().unregister(this);
        });
    }

    @Override
    protected DevicePane getDevicePane(Device device, boolean defaultDevice) {
        return new DevicePane(wallet, message, keyDerivation, device, defaultDevice);
    }

    @Subscribe
    public void messageSigned(MessageSignedEvent event) {
        if(wallet == event.getWallet()) {
            setResult(event.getSignature());
        }
    }
}
