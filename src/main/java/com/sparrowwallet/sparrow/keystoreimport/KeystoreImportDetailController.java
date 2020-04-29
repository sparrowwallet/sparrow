package com.sparrowwallet.sparrow.keystoreimport;

public abstract class KeystoreImportDetailController {
    private KeystoreImportController masterController;

    public KeystoreImportController getMasterController() {
        return masterController;
    }

    void setMasterController(KeystoreImportController masterController) {
        this.masterController = masterController;
    }

    public void initializeView() {

    }
}
