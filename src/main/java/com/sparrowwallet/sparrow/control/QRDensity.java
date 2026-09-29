package com.sparrowwallet.sparrow.control;

/**
 * QR code density levels applying to all encodings. For each level, the UR and BBQr fragment lengths are chosen to produce the same maximum QR code version
 * (7, 17 and 27 respectively), since scanner capability does not depend on the encoding.
 */
public enum QRDensity {
    LOW("Low", 80, 216),
    NORMAL("Medium", 400, 928),
    HIGH("High", 1000, 2000);

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

    @Override
    public String toString() {
        return name + " Density";
    }
}
