package com.sparrowwallet.sparrow.event;

public class OpenWalletsNewWindowsStatusEvent {
    private final boolean openWalletsInNewWindows;

    public OpenWalletsNewWindowsStatusEvent(boolean openWalletsInNewWindows) {
        this.openWalletsInNewWindows = openWalletsInNewWindows;
    }

    public boolean isOpenWalletsInNewWindows() {
        return openWalletsInNewWindows;
    }
}
