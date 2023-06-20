package com.sparrowwallet.sparrow.net;

import java.util.Objects;

public class MempoolRateSize implements Comparable<MempoolRateSize> {
    private final double fee;
    private final long vSize;

    public MempoolRateSize(double fee, long vSize) {
        this.fee = fee;
        this.vSize = vSize;
    }

    public double getFee() {
        return fee;
    }

    public long getVSize() {
        return vSize;
    }

    @Override
    public boolean equals(Object o) {
        if(this == o) {
            return true;
        }
        if(o == null || getClass() != o.getClass()) {
            return false;
        }
        MempoolRateSize that = (MempoolRateSize) o;
        return fee == that.fee;
    }

    @Override
    public int hashCode() {
        return Objects.hash(fee);
    }

    @Override
    public int compareTo(MempoolRateSize other) {
        return Double.compare(fee, other.fee);
    }

    @Override
    public String toString() {
        return "MempoolRateSize{" +
                "fee=" + fee +
                ", vSize=" + vSize +
                '}';
    }
}
