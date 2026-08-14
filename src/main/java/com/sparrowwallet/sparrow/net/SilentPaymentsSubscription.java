package com.sparrowwallet.sparrow.net;

import java.util.Arrays;

public class SilentPaymentsSubscription {
    public String address;
    public Integer[] labels;
    public int start_height;

    public SilentPaymentsSubscription() {}

    public SilentPaymentsSubscription(String address, Integer[] labels, int start_height) {
        this.address = address;
        this.labels = labels;
        this.start_height = start_height;
    }

    @Override
    public String toString() {
        return "SilentPaymentsSubscription{address='" + address + "', labels=" + Arrays.toString(labels) + ", start_height=" + start_height + '}';
    }
}
