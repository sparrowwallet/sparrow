package com.sparrowwallet.sparrow.keystoreimport;

import com.sparrowwallet.drongo.policy.PolicyType;
import com.sparrowwallet.sparrow.control.FileKeystoreImportPane;
import com.sparrowwallet.sparrow.control.TitledDescriptionPane;
import com.sparrowwallet.sparrow.io.*;
import javafx.fxml.FXML;
import javafx.scene.control.Accordion;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class HwAirgappedController extends KeystoreImportDetailController {
    @FXML
    private Accordion importAccordion;

    public void initializeView() {
        List<KeystoreFileImport> importers = Collections.emptyList();
        if(getMasterController().getWallet().getPolicyType().equals(PolicyType.SINGLE)) {
            importers = List.of(new ColdcardSinglesig(), new CoboVaultSinglesig(), new KeystoneSinglesig(), new PassportSinglesig(), new SeedSigner(), new SeedTool(), new SpecterDIY());
        } else if(getMasterController().getWallet().getPolicyType().equals(PolicyType.MULTI)) {
            importers = List.of(new ColdcardMultisig(), new CoboVaultMultisig(), new KeystoneMultisig(), new PassportMultisig(), new SeedSigner(), new SeedTool(), new SpecterDIY());
        }

        for(KeystoreFileImport importer : importers) {
            FileKeystoreImportPane importPane = new FileKeystoreImportPane(getMasterController().getWallet(), importer, getMasterController().getRequiredDerivation());
            if(getMasterController().getRequiredModel() == null || getMasterController().getRequiredModel() == importer.getWalletModel()) {
                importAccordion.getPanes().add(importPane);
            }
        }

        importAccordion.getPanes().sort(Comparator.comparing(o -> ((TitledDescriptionPane) o).getTitle()));
    }
}
