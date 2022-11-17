package com.sparrowwallet.sparrow.control;

public enum QRDensity {
    NORMAL("Normal", 250),
    LOW("Low", 80);

    private final String name;
    private final int maxFragmentLength;

    QRDensity(String name, int maxFragmentLength) {
        this.name = name;
        this.maxFragmentLength = maxFragmentLength;
    }

    public String getName() {
        return name;
    }

    public int getMaxFragmentLength() {
        return maxFragmentLength;
    }
}
