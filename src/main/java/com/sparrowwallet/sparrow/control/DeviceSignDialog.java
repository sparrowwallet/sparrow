package com.sparrowwallet.sparrow.control;

import com.google.common.eventbus.Subscribe;
import com.sparrowwallet.drongo.psbt.PSBT;
import com.sparrowwallet.sparrow.EventManager;
import com.sparrowwallet.sparrow.event.PSBTSignedEvent;
import com.sparrowwallet.sparrow.io.Device;

import java.util.List;

public class DeviceSignDialog extends DeviceDialog<PSBT> {
    private final PSBT psbt;

    public DeviceSignDialog(List<Device> devices, PSBT psbt) {
        super(devices);
        this.psbt = psbt;
        EventManager.get().register(this);
        setOnCloseRequest(event -> {
            EventManager.get().unregister(this);
        });
        setResultConverter(dialogButton -> dialogButton.getButtonData().isCancelButton() ? null : psbt);
    }

    @Override
    protected DevicePane getDevicePane(Device device) {
        return new DevicePane(psbt, device);
    }

    @Subscribe
    public void psbtSigned(PSBTSignedEvent event) {
        if(psbt == event.getPsbt()) {
            setResult(event.getSignedPsbt());
        }
    }
}
