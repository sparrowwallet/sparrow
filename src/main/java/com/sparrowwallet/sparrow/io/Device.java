package com.sparrowwallet.sparrow.io;

import com.sparrowwallet.drongo.wallet.WalletModel;

import java.util.Objects;

public class Device {
    private String type;
    private String path;
    private WalletModel model;
    private Boolean needsPinSent;
    private Boolean needsPassphraseSent;
    private String fingerprint;
    private boolean card;
    private String[][] warnings;
    private String error;

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

    public Boolean isNeedsPinSent() {
        return needsPinSent != null && needsPinSent;
    }

    public void setNeedsPinSent(Boolean needsPinSent) {
        this.needsPinSent = needsPinSent;
    }

    public Boolean getNeedsPassphraseSent() {
        return needsPassphraseSent;
    }

    public Boolean isNeedsPassphraseSent() {
        return needsPassphraseSent != null && needsPassphraseSent;
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

    public boolean isCard() {
        return card;
    }

    public void setCard(boolean card) {
        this.card = card;
    }

    public boolean containsWarning(String warning) {
        if(warnings != null) {
            for(String[] warns : warnings) {
                for(String warn : warns) {
                    if(warn.contains(warning)) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }

    public String toString() {
        return getModel() + ":" + getPath();
    }

    @Override
    public boolean equals(Object o) {
        if(this == o) {
            return true;
        }
        if(o == null || getClass() != o.getClass()) {
            return false;
        }
        Device device = (Device) o;
        return Objects.equals(type, device.type) &&
                Objects.equals(path, device.path);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, path);
    }
}
