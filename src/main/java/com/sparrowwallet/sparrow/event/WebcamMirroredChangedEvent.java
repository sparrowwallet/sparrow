package com.sparrowwallet.sparrow.event;

public class WebcamMirroredChangedEvent {
    private final boolean mirrored;

    public WebcamMirroredChangedEvent(boolean mirrored) {
        this.mirrored = mirrored;
    }

    public boolean isMirrored() {
        return mirrored;
    }
}
