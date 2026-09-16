package com.sparrowwallet.sparrow.control;

import javafx.collections.ListChangeListener;
import javafx.scene.Node;
import javafx.scene.control.Label;
import tornadofx.control.Field;

/**
 * A form field that associates its generated label with its primary input.
 */
public class AccessibleField extends Field {
    public AccessibleField() {
        getInputs().addListener((ListChangeListener<Node>)change -> updateLabelFor());
    }

    private void updateLabelFor() {
        Label label = (Label)getLabelContainer().getChildren().getFirst();
        label.setLabelFor(getInputs().isEmpty() ? null : getInputs().getFirst());
    }

    public void setLabelFor(Node input) {
        Label label = (Label)getLabelContainer().getChildren().getFirst();
        label.setLabelFor(input);
    }
}
