package com.sparrowwallet.sparrow.control;

import javafx.scene.AccessibleAttribute;
import javafx.scene.AccessibleRole;
import javafx.scene.Node;
import javafx.scene.layout.VBox;

import java.util.List;

/**
 * A visually custom list of tabs that exposes the same accessibility contract
 * as a JavaFX {@link javafx.scene.control.TabPane}.
 */
public class TabList extends VBox {
    public TabList() {
        setAccessibleRole(AccessibleRole.TAB_PANE);
    }

    @Override
    public Object queryAccessibleAttribute(AccessibleAttribute attribute, Object... parameters) {
        List<Node> tabs = getTabs();
        return switch(attribute) {
            case FOCUS_ITEM -> tabs.stream()
                    .filter(tab -> Boolean.TRUE.equals(tab.queryAccessibleAttribute(AccessibleAttribute.SELECTED)))
                    .findFirst()
                    .orElse(null);
            case ITEM_COUNT -> tabs.size();
            case ITEM_AT_INDEX -> getTabAtIndex(tabs, parameters);
            default -> super.queryAccessibleAttribute(attribute, parameters);
        };
    }

    private List<Node> getTabs() {
        return getChildren().stream()
                .filter(node -> node.getAccessibleRole() == AccessibleRole.TAB_ITEM)
                .toList();
    }

    private Node getTabAtIndex(List<Node> tabs, Object... parameters) {
        if(parameters.length == 0 || !(parameters[0] instanceof Integer index) || index < 0 || index >= tabs.size()) {
            return null;
        }

        return tabs.get(index);
    }
}
