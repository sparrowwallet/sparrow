package com.sparrowwallet.sparrow.keystoreimport;

import com.sparrowwallet.sparrow.control.FileKeystoreImportPane;
import com.sparrowwallet.sparrow.control.MnemonicKeystoreImportPane;
import com.sparrowwallet.sparrow.control.TitledDescriptionPane;
import com.sparrowwallet.sparrow.control.XprvKeystoreImportPane;
import com.sparrowwallet.sparrow.io.*;
import javafx.fxml.FXML;
import javafx.scene.control.Accordion;

import java.util.List;

public class SwController extends KeystoreImportDetailController {
    @FXML
    private Accordion importAccordion;

    public void initializeView() {
        List<KeystoreImport> importers = List.of(new Bip39(), new Electrum(), new Bip32());

        for(KeystoreImport importer : importers) {
            if(importer.isDeprecated() && !Config.get().isShowDeprecatedImportExport()) {
                continue;
            }

            TitledDescriptionPane importPane = null;

            if(importer instanceof KeystoreFileImport) {
                importPane = new FileKeystoreImportPane(getMasterController().getWallet(), (KeystoreFileImport)importer, getMasterController().getRequiredDerivation());
            } else if(importer instanceof KeystoreMnemonicImport) {
                importPane = new MnemonicKeystoreImportPane(getMasterController().getWallet(), (KeystoreMnemonicImport)importer);
            } else if(importer instanceof KeystoreXprvImport) {
                importPane = new XprvKeystoreImportPane(getMasterController().getWallet(), (KeystoreXprvImport)importer);
            } else {
                throw new IllegalArgumentException("Could not create ImportPane for importer of type " + importer.getClass());
            }

            importAccordion.getPanes().add(importPane);
        }
    }
}
