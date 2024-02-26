package com.sparrowwallet.sparrow.control;

public enum QRDensity {
    NORMAL("Normal", 400, 2000),
    LOW("Low", 80, 1000);

    private final String name;
    private final int maxUrFragmentLength;
    private final int maxBbqrFragmentLength;

    QRDensity(String name, int maxUrFragmentLength, int maxBbqrFragmentLength) {
        this.name = name;
        this.maxUrFragmentLength = maxUrFragmentLength;
        this.maxBbqrFragmentLength = maxBbqrFragmentLength;
    }

    public String getName() {
        return name;
    }

    public int getMaxUrFragmentLength() {
        return maxUrFragmentLength;
    }

    public int getMaxBbqrFragmentLength() {
        return maxBbqrFragmentLength;
    }
}
