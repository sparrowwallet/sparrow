package com.sparrowwallet.sparrow;

public enum Appearance {
    CLASSIC("Classic"),
    MODERN("Modern");

    private final String label;

    Appearance(String label) {
        this.label = label;
    }

    @Override
    public String toString() {
        return label;
    }
}
