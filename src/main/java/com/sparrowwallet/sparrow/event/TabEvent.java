package com.sparrowwallet.sparrow.event;

import com.sparrowwallet.sparrow.TabData;
import javafx.scene.control.Tab;

public class TabEvent {
    private final Tab tab;

    public TabEvent(Tab tab) {
        this.tab = tab;
    }

    public Tab getTab() {
        return tab;
    }

    public String getTabName() {
        return tab.getText();
    }

    public TabData getTabData() {
        return (TabData)tab.getUserData();
    }
}
