package com.sparrowwallet.sparrow.event;

import com.sparrowwallet.sparrow.control.WebcamResolution;

public class WebcamResolutionChangedEvent {
    private final WebcamResolution resolution;

    public WebcamResolutionChangedEvent(WebcamResolution resolution) {
        this.resolution = resolution;
    }

    public WebcamResolution getResolution() {
        return resolution;
    }
}
