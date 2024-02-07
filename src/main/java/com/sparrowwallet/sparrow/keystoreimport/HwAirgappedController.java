package com.sparrowwallet.sparrow.keystoreimport;

import com.sparrowwallet.drongo.policy.PolicyType;
import com.sparrowwallet.sparrow.control.CardImportPane;
import com.sparrowwallet.sparrow.control.FileKeystoreImportPane;
import com.sparrowwallet.sparrow.control.TitledDescriptionPane;
import com.sparrowwallet.sparrow.io.*;
import com.sparrowwallet.sparrow.io.ckcard.Satschip;
import com.sparrowwallet.sparrow.io.ckcard.Tapsigner;
import com.sparrowwallet.sparrow.io.satochip.Satochip;
import javafx.fxml.FXML;
import javafx.scene.control.Accordion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class HwAirgappedController extends KeystoreImportDetailController {
    private static final Logger log = LoggerFactory.getLogger(HwAirgappedController.class);

    @FXML
    private Accordion importAccordion;

    public void initializeView() {
        List<KeystoreFileImport> fileImporters = Collections.emptyList();
        if(getMasterController().getWallet().getPolicyType().equals(PolicyType.SINGLE)) {
            fileImporters = List.of(new ColdcardSinglesig(), new CoboVaultSinglesig(), new Jade(), new KeystoneSinglesig(), new PassportSinglesig(), new SeedSigner(), new GordianSeedTool(), new SpecterDIY(), new Krux(), new AirGapVault());
        } else if(getMasterController().getWallet().getPolicyType().equals(PolicyType.MULTI)) {
            fileImporters = List.of(new Bip129(), new ColdcardMultisig(), new CoboVaultMultisig(), new Jade(), new KeystoneMultisig(), new PassportMultisig(), new SeedSigner(), new GordianSeedTool(), new SpecterDIY(), new Krux());
        }

        for(KeystoreFileImport importer : fileImporters) {
            if(!importer.isDeprecated() || Config.get().isShowDeprecatedImportExport()) {
                FileKeystoreImportPane importPane = new FileKeystoreImportPane(getMasterController().getWallet(), importer, getMasterController().getRequiredDerivation());
                if(getMasterController().getRequiredModel() == null || getMasterController().getRequiredModel() == importer.getWalletModel()) {
                    importAccordion.getPanes().add(importPane);
                }
            }
        }

        List<KeystoreCardImport> cardImporters = List.of(new Tapsigner(), new Satochip(), new Satschip());
        for(KeystoreCardImport importer : cardImporters) {
            if(!importer.isDeprecated() || Config.get().isShowDeprecatedImportExport()) {
                CardImportPane importPane = new CardImportPane(getMasterController().getWallet(), importer, getMasterController().getRequiredDerivation());
                if(getMasterController().getRequiredModel() == null || getMasterController().getRequiredModel() == importer.getWalletModel()) {
                    importAccordion.getPanes().add(importPane);
                }
            }
        }

        importAccordion.getPanes().sort(Comparator.comparing(o -> ((TitledDescriptionPane) o).getTitle()));
    }
}
