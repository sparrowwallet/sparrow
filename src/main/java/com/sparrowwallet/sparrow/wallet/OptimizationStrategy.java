package com.sparrowwallet.sparrow.wallet;

public enum OptimizationStrategy {
    EFFICIENCY("Efficiency"), PRIVACY("Privacy");

    private final String name;

    private OptimizationStrategy(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    @Override
    public String toString() {
        return name;
    }
}
