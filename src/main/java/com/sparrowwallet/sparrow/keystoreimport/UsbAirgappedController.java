package com.sparrowwallet.sparrow.keystoreimport;

import com.sparrowwallet.drongo.policy.PolicyType;
import com.sparrowwallet.sparrow.control.KeystoreImportAccordion;
import com.sparrowwallet.sparrow.external.ColdcardMultisig;
import com.sparrowwallet.sparrow.external.ColdcardSinglesig;
import com.sparrowwallet.sparrow.external.KeystoreImport;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;

import java.util.Collections;
import java.util.List;

public class UsbAirgappedController extends KeystoreImportDetailController {
    @FXML
    private KeystoreImportAccordion importAccordion;

    public void initializeView() {
        List<KeystoreImport> importers = Collections.emptyList();
        if(getMasterController().getWallet().getPolicyType().equals(PolicyType.SINGLE)) {
            importers = List.of(new ColdcardSinglesig());
        } else if(getMasterController().getWallet().getPolicyType().equals(PolicyType.MULTI)) {
            importers = List.of(new ColdcardMultisig());
        }

        importAccordion.setKeystoreImporters(getMasterController().getWallet(), FXCollections.observableList(importers));
    }
}
