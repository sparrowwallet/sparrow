package com.sparrowwallet.sparrow.control;

import javafx.scene.AccessibleAttribute;
import javafx.scene.Node;
import javafx.scene.control.TabPane;
import javafx.scene.control.skin.TabPaneSkin;

/**
 * Tab pane skin that tells screen readers when the selected tab changes. JavaFX only notifies the tab pane, so VoiceOver keeps reading
 * every tab it has seen selected as selected. Applied to every tab pane with -fx-skin in app.css.
 */
public class SelectedTabSkin extends TabPaneSkin {
    public SelectedTabSkin(TabPane tabPane) {
        super(tabPane);
        registerChangeListener(tabPane.getSelectionModel().selectedItemProperty(), _ -> {
            for(Node child : getChildren()) {
                if(child.getStyleClass().contains("tab-header-area")) {
                    child.lookupAll(".tab").forEach(header -> header.notifyAccessibleAttributeChanged(AccessibleAttribute.SELECTED));
                }
            }
        });
    }
}
