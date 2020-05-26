package com.sparrowwallet.sparrow.wallet;

import javafx.beans.property.SimpleStringProperty;

import java.util.ArrayList;
import java.util.List;

public abstract class Entry {
    private final SimpleStringProperty labelProperty;
    private final List<Entry> children = new ArrayList<>();

    public Entry(String label) {
        this.labelProperty = new SimpleStringProperty(label);
    }

    public Entry(SimpleStringProperty labelProperty) {
        this.labelProperty = labelProperty;
    }

    public String getLabel() {
        return labelProperty.get();
    }

    public SimpleStringProperty labelProperty() {
        return labelProperty;
    }

    public List<Entry> getChildren() {
        return children;
    }

    public abstract Long getAmount();
}
