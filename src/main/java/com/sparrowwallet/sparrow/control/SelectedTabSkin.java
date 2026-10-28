package com.sparrowwallet.sparrow.control;

import javafx.scene.AccessibleAttribute;
import javafx.scene.Node;
import javafx.scene.control.TabPane;
import javafx.scene.control.skin.TabPaneSkin;

/**
 * Tab pane skin that tells screen readers when the selected tab changes. JavaFX only notifies the tab pane, so VoiceOver keeps reading
 * every tab it has seen selected as selected. Also leaves a tab pane with hidden headers out of the Tab order.
 * Applied to every tab pane with -fx-skin in app.css.
 */
public class SelectedTabSkin extends TabPaneSkin {
    public SelectedTabSkin(TabPane tabPane) {
        super(tabPane);
        //A tab pane whose tab headers are hidden (a single account or payment) is not a Tab stop, as it would focus a tab that cannot be seen
        for(Node child : getChildren()) {
            if(child.getStyleClass().contains("tab-header-area")) {
                child.visibleProperty().addListener((_, _, visible) -> tabPane.setFocusTraversable(visible));
                tabPane.setFocusTraversable(child.isVisible());
            }
        }
        registerChangeListener(tabPane.getSelectionModel().selectedItemProperty(), _ -> {
            for(Node child : getChildren()) {
                if(child.getStyleClass().contains("tab-header-area")) {
                    child.lookupAll(".tab").forEach(header -> header.notifyAccessibleAttributeChanged(AccessibleAttribute.SELECTED));
                }
            }
        });
    }
}
