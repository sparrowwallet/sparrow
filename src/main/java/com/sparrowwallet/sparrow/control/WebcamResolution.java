package com.sparrowwallet.sparrow.control;

import org.openpnp.capture.CaptureFormat;

import java.util.Arrays;

public enum WebcamResolution implements Comparable<WebcamResolution> {
    VGA("480p", 640, 480),
    HD("720p", 1280, 720),
    FHD("1080p", 1920, 1080),
    UHD4K("4K", 3840, 2160);

    private final String name;
    private final int width;
    private final int height;

    WebcamResolution(String name, int width, int height) {
        this.name = name;
        this.width = width;
        this.height = height;
    }

    public int getPixelsCount() {
        return this.width * this.height;
    }

    public boolean isStandardAspect() {
        return Arrays.equals(getAspectRatio(), new int[]{4, 3});
    }

    public boolean isWidescreenAspect() {
        return Arrays.equals(getAspectRatio(), new int[]{16, 9});
    }

    public int[] getAspectRatio() {
        int factor = this.getCommonFactor(this.width, this.height);
        int wr = this.width / factor;
        int hr = this.height / factor;
        return new int[] {wr, hr};
    }

    private int getCommonFactor(int width, int height) {
        return height == 0 ? width : this.getCommonFactor(height, width % height);
    }

    public String getName() {
        return name;
    }

    public int getWidth() {
        return this.width;
    }

    public int getHeight() {
        return this.height;
    }

    public String toString() {
        return name;
    }

    public static WebcamResolution from(CaptureFormat captureFormat) {
        for(WebcamResolution resolution : values()) {
            if(captureFormat.getFormatInfo().width == resolution.width && captureFormat.getFormatInfo().height == resolution.height) {
                return resolution;
            }
        }

        return null;
    }
}
