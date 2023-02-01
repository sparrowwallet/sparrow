package com.sparrowwallet.sparrow.control;

import com.google.common.eventbus.Subscribe;
import com.sparrowwallet.drongo.crypto.ECKey;
import com.sparrowwallet.drongo.protocol.ScriptType;
import com.sparrowwallet.sparrow.EventManager;
import com.sparrowwallet.sparrow.event.DeviceGetPrivateKeyEvent;
import com.sparrowwallet.sparrow.io.Device;

import java.util.List;

public class DeviceUnsealDialog extends DeviceDialog<DeviceUnsealDialog.DevicePrivateKey> {
    public DeviceUnsealDialog(List<String> operationFingerprints) {
        super(operationFingerprints);
        EventManager.get().register(this);
        setOnCloseRequest(event -> {
            EventManager.get().unregister(this);
        });
    }

    @Override
    protected DevicePane getDevicePane(Device device, boolean defaultDevice) {
        return new DevicePane(DevicePane.DeviceOperation.GET_PRIVATE_KEY, device, defaultDevice);
    }

    @Subscribe
    public void deviceGetPrivateKey(DeviceGetPrivateKeyEvent event) {
        setResult(new DevicePrivateKey(event.getPrivateKey(), event.getScriptType()));
    }

    public record DevicePrivateKey(ECKey privateKey, ScriptType scriptType) {}
}
