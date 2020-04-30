package com.sparrowwallet.sparrow.keystoreimport;

import com.sparrowwallet.sparrow.control.KeystoreImportAccordion;
import com.sparrowwallet.sparrow.io.Electrum;
import com.sparrowwallet.sparrow.io.KeystoreImport;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;

import java.util.List;

public class SwController extends KeystoreImportDetailController {
    @FXML
    private KeystoreImportAccordion importAccordion;

    public void initializeView() {
        List<KeystoreImport> importers = List.of(new Electrum());
        importAccordion.setKeystoreImporters(getMasterController().getWallet(), FXCollections.observableList(importers));
    }
}
