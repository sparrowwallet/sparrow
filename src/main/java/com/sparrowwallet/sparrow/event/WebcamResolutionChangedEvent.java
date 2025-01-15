package com.sparrowwallet.sparrow.event;

public class WebcamResolutionChangedEvent {
    private final boolean hdResolution;

    public WebcamResolutionChangedEvent(boolean hdResolution) {
        this.hdResolution = hdResolution;
    }

    public boolean isHdResolution() {
        return hdResolution;
    }
}
