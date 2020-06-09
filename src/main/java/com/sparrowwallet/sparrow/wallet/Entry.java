package com.sparrowwallet.sparrow.wallet;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.util.List;

public abstract class Entry {
    private final SimpleStringProperty labelProperty;
    private final ObservableList<Entry> children;

    public Entry(String label, List<Entry> entries) {
        this.labelProperty = new SimpleStringProperty(label);
        this.children = FXCollections.observableList(entries);
    }

    public Entry(SimpleStringProperty labelProperty, ObservableList<Entry> children) {
        this.labelProperty = labelProperty;
        this.children = children;
    }

    public String getLabel() {
        return labelProperty.get();
    }

    public SimpleStringProperty labelProperty() {
        return labelProperty;
    }

    public ObservableList<Entry> getChildren() {
        return children;
    }

    public abstract Long getValue();
}
