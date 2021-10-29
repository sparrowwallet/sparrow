package com.sparrowwallet.sparrow.control;

import com.google.common.eventbus.Subscribe;
import com.sparrowwallet.drongo.wallet.Keystore;
import com.sparrowwallet.drongo.wallet.StandardAccount;
import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.sparrow.EventManager;
import com.sparrowwallet.sparrow.event.KeystoresDiscoveredEvent;
import com.sparrowwallet.sparrow.io.Device;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public class DeviceKeystoreDiscoverDialog extends DeviceDialog<Map<StandardAccount, Keystore>> {
    private final Wallet masterWallet;
    private final List<StandardAccount> availableAccounts;

    public DeviceKeystoreDiscoverDialog(List<String> operationFingerprints, Wallet masterWallet, List<StandardAccount> availableAccounts) {
        super(operationFingerprints);
        this.masterWallet = masterWallet;
        this.availableAccounts = availableAccounts;
        EventManager.get().register(this);
        setOnCloseRequest(event -> {
            EventManager.get().unregister(this);
        });
        setResultConverter(dialogButton -> dialogButton.getButtonData().isCancelButton() ? null : Collections.emptyMap());
    }

    @Override
    protected DevicePane getDevicePane(Device device, boolean defaultDevice) {
        return new DevicePane(masterWallet, availableAccounts, device, defaultDevice);
    }

    @Subscribe
    public void keystoresDiscovered(KeystoresDiscoveredEvent event) {
        setResult(event.getDiscoveredKeystores());
    }
}
