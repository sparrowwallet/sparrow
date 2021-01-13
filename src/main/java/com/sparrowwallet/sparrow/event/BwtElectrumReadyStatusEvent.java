package com.sparrowwallet.sparrow.event;

public class BwtElectrumReadyStatusEvent extends BwtStatusEvent {
    private final String electrumAddr;

    public BwtElectrumReadyStatusEvent(String status, String electrumAddr) {
        super(status);
        this.electrumAddr = electrumAddr;
    }

    public String getElectrumAddr() {
        return electrumAddr;
    }
}
