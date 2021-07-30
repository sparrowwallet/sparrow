package com.sparrowwallet.sparrow.event;

import com.sparrowwallet.sparrow.WalletTabData;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;

public class WalletTabSelectedEvent extends TabSelectedEvent {
    public WalletTabSelectedEvent(Tab tab) {
        super(tab);
    }

    public WalletTabData getWalletTabData() {
        TabPane subTabs = (TabPane)getTab().getContent();
        Tab subTab = subTabs.getSelectionModel().getSelectedItem();
        return (WalletTabData)subTab.getUserData();
    }
}
