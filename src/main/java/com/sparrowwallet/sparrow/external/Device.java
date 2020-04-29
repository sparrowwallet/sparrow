package com.sparrowwallet.sparrow.external;

import com.sparrowwallet.drongo.wallet.WalletModel;

public class Device {
    private String type;
    private String path;
    private WalletModel model;
    private Boolean needsPinSent;
    private Boolean needsPassphraseSent;
    private String fingerprint;

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public WalletModel getModel() {
        return model;
    }

    public void setModel(WalletModel model) {
        this.model = model;
    }

    public Boolean getNeedsPinSent() {
        return needsPinSent;
    }

    public void setNeedsPinSent(Boolean needsPinSent) {
        this.needsPinSent = needsPinSent;
    }

    public Boolean getNeedsPassphraseSent() {
        return needsPassphraseSent;
    }

    public void setNeedsPassphraseSent(Boolean needsPassphraseSent) {
        this.needsPassphraseSent = needsPassphraseSent;
    }

    public String getFingerprint() {
        return fingerprint;
    }

    public void setFingerprint(String fingerprint) {
        this.fingerprint = fingerprint;
    }

    public String toString() {
        return getModel() + ":" + getPath();
    }
}
