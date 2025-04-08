package com.sparrowwallet.sparrow.control;

import com.sparrowwallet.drongo.OsType;

public enum WebcamPixelFormat {
    //Only V4L2 formats defined in linux/videodev2.h are required here, declared in order of priority for supported formats
    PIX_FMT_RGB24("RGB3", true),
    PIX_FMT_YUYV("YUYV", true),
    PIX_FMT_MJPG("MJPG", true),
    PIX_FMT_NV12("NV12", false);

    private final String name;
    private final boolean supported;

    WebcamPixelFormat(String name, boolean supported) {
        this.name = name;
        this.supported = supported;
    }

    public String getName() {
        return name;
    }

    public boolean isSupported() {
        return supported;
    }

    public String toString() {
        return name;
    }

    public static WebcamPixelFormat fromFourCC(int fourCC) {
        String strFourCC = fourCCToString(fourCC);
        for(WebcamPixelFormat pixelFormat : WebcamPixelFormat.values()) {
            if(pixelFormat.getName().equalsIgnoreCase(strFourCC)) {
                return pixelFormat;
            }
        }

        return null;
    }

    public static String fourCCToString(int fourCC) {
        int fccVal = fourCC;
        int tmp = fccVal;

        if(OsType.getCurrent() == OsType.MACOS) {
            tmp = ((tmp >> 16) & 0x0000FFFF) | ((tmp << 16) & 0xFFFF0000);
            tmp = ((tmp & 0x00FF00FF) << 8) | ((tmp & 0xFF00FF00) >>> 8);
        }

        fccVal = tmp;

        StringBuilder v = new StringBuilder(4);
        for(int i = 0; i < 4; i++) {
            char c = (char) (fccVal & 0xFF);
            v.append(c);
            fccVal >>>= 8;
        }

        return v.toString();
    }

    public static int getPriority(WebcamPixelFormat pixelFormat) {
        if(pixelFormat == null) {
            return values().length;
        } else if(pixelFormat.isSupported()) {
            return pixelFormat.ordinal();
        } else {
            return values().length + 1;
        }
    }
}
