package com.sparrowwallet.sparrow.preferences;

import com.sparrowwallet.sparrow.io.Config;
import javafx.fxml.Initializable;

public abstract class PreferencesDetailController {
    private PreferencesController masterController;

    public PreferencesController getMasterController() {
        return masterController;
    }

    void setMasterController(PreferencesController masterController) {
        this.masterController = masterController;
    }

    public void initializeView(Config config) {

    }
}
