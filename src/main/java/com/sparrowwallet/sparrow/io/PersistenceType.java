package com.sparrowwallet.sparrow.io;

public enum PersistenceType {
    JSON("json");

    private final String name;

    private PersistenceType(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public String getExtension() {
        return getName();
    }
}
