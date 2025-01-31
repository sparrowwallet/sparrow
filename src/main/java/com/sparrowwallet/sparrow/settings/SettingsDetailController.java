package com.sparrowwallet.sparrow.settings;

import com.sparrowwallet.sparrow.io.Config;

public abstract class SettingsDetailController {
    private SettingsController masterController;

    public SettingsController getMasterController() {
        return masterController;
    }

    void setMasterController(SettingsController masterController) {
        this.masterController = masterController;
    }

    public void initializeView(Config config) {

    }
}
