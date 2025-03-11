package com.sparrowwallet.sparrow.control;

import org.openpnp.capture.CaptureFormat;

public enum WebcamResolution {
    VGA(640, 480),
    HD(1280, 720);

    private final int width;
    private final int height;

    WebcamResolution(int width, int height) {
        this.width = width;
        this.height = height;
    }

    public int getPixelsCount() {
        return this.width * this.height;
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

    public int getWidth() {
        return this.width;
    }

    public int getHeight() {
        return this.height;
    }

    public String toString() {
        int[] ratio = this.getAspectRatio();
        return super.toString() + ' ' + this.width + 'x' + this.height + " (" + ratio[0] + ':' + ratio[1] + ')';
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
