package com.sparrowwallet.sparrow.keystoreimport;

import com.sparrowwallet.sparrow.control.*;
import com.sparrowwallet.sparrow.io.*;
import javafx.fxml.FXML;
import javafx.scene.control.Accordion;

import java.util.List;

public class SwController extends KeystoreImportDetailController {
    @FXML
    private Accordion importAccordion;

    public void initializeView() {
        List<KeystoreImport> importers = List.of(new Bip39(), new Bip32(), new Slip39(), new Bip93());

        for(KeystoreImport importer : importers) {
            if(importer.isDeprecated() && !Config.get().isShowDeprecatedImportExport()) {
                continue;
            }

            TitledDescriptionPane importPane = null;

            if(importer instanceof KeystoreFileImport) {
                importPane = new FileKeystoreImportPane(getMasterController().getWallet(), (KeystoreFileImport)importer, getMasterController().getRequiredDerivation());
            } else if(importer instanceof KeystoreMnemonicImport) {
                importPane = new MnemonicKeystoreImportPane(getMasterController().getWallet(), (KeystoreMnemonicImport)importer, getMasterController().getDefaultDerivation());
            } else if(importer instanceof KeystoreXprvImport) {
                importPane = new XprvKeystoreImportPane(getMasterController().getWallet(), (KeystoreXprvImport)importer, getMasterController().getDefaultDerivation());
            } else if(importer instanceof KeystoreMnemonicShareImport) {
                importPane = new MnemonicShareKeystoreImportPane(getMasterController().getWallet(), (KeystoreMnemonicShareImport)importer, getMasterController().getDefaultDerivation());
            } else if (importer instanceof KeystoreCodexImport) {
                importPane = new CodexKeystoreImportPane(getMasterController().getWallet(), (KeystoreCodexImport)importer, getMasterController().getDefaultDerivation());
            } else {
                throw new IllegalArgumentException("Could not create ImportPane for importer of type " + importer.getClass());
            }

            importAccordion.getPanes().add(importPane);
        }
    }
}
