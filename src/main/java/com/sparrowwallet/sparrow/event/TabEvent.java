package com.sparrowwallet.sparrow.event;

import javafx.scene.control.Tab;

public class TabEvent {
    private Tab tab;

    public TabEvent(Tab tab) {
        this.tab = tab;
    }

    public Tab getTab() {
        return tab;
    }
}
