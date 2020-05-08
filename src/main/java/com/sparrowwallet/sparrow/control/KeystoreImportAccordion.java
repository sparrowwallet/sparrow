package com.sparrowwallet.sparrow.control;

import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.sparrow.io.KeystoreFileImport;
import com.sparrowwallet.sparrow.io.KeystoreImport;
import com.sparrowwallet.sparrow.io.KeystoreMnemonicImport;
import javafx.collections.ObservableList;
import javafx.scene.control.Accordion;

import java.util.List;

public class KeystoreImportAccordion extends Accordion {
    private List<KeystoreImport> importers;

    public void setKeystoreImporters(Wallet wallet, ObservableList<KeystoreImport> importers) {
        this.importers = importers;

        for(KeystoreImport importer : importers) {
            KeystoreImportPane importPane = null;

            if(importer instanceof KeystoreFileImport) {
                importPane = new FileKeystoreImportPane(this, wallet, (KeystoreFileImport)importer);
            } else if(importer instanceof KeystoreMnemonicImport) {
                importPane = new MnemonicKeystoreImportPane(this, wallet, (KeystoreMnemonicImport)importer);
            } else {
                throw new IllegalArgumentException("Could not create ImportPane for importer of type " + importer.getClass());
            }

            this.getPanes().add(importPane);
        }
    }
}
