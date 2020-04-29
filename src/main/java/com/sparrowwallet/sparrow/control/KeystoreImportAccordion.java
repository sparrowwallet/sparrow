package com.sparrowwallet.sparrow.control;

import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.sparrow.external.KeystoreFileImport;
import com.sparrowwallet.sparrow.external.KeystoreImport;
import com.sparrowwallet.sparrow.external.KeystoreMnemonicImport;
import javafx.collections.ObservableList;
import javafx.scene.control.Accordion;

import java.util.List;

public class KeystoreImportAccordion extends Accordion {
    private List<KeystoreImport> importers;

    public void setKeystoreImporters(Wallet wallet, ObservableList<KeystoreImport> importers) {
        this.importers = importers;

        for(KeystoreImport importer : importers) {
            KeystoreFileImportPane importPane = null;

            if(importer instanceof KeystoreFileImport) {
                importPane = new KeystoreFileImportPane(this, wallet, (KeystoreFileImport)importer);
            } else if(importer instanceof KeystoreMnemonicImport) {
                //TODO:
            } else {
                throw new IllegalArgumentException("Could not create ImportPane for importer of type " + importer.getClass());
            }

            this.getPanes().add(importPane);
        }
    }
}
