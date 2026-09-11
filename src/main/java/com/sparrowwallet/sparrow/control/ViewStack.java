package com.sparrowwallet.sparrow.control;

import javafx.scene.Node;
import javafx.scene.layout.StackPane;

import java.util.Objects;

/**
 * A stack of cached views where only the active view is exposed or rendered.
 */
public class ViewStack extends StackPane {
    public void show(Node view) {
        Objects.requireNonNull(view);

        if(!getChildren().contains(view)) {
            getChildren().add(view);
        }

        for(Node child : getChildren()) {
            child.setVisible(child == view);
        }
    }
}
