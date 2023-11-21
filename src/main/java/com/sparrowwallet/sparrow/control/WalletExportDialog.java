package com.sparrowwallet.sparrow.control;

import com.google.common.eventbus.Subscribe;
import com.sparrowwallet.drongo.policy.PolicyType;
import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.sparrow.AppServices;
import com.sparrowwallet.sparrow.EventManager;
import com.sparrowwallet.sparrow.event.WalletExportEvent;
import com.sparrowwallet.sparrow.wallet.WalletForm;
import com.sparrowwallet.sparrow.io.*;
import javafx.scene.control.*;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.StackPane;

import java.util.Comparator;
import java.util.List;

public class WalletExportDialog extends Dialog<Wallet> {
    private Wallet wallet;

    public WalletExportDialog(WalletForm walletForm) {
        this.wallet = walletForm.getWallet();

        EventManager.get().register(this);
        setOnCloseRequest(event -> {
            EventManager.get().unregister(this);
        });

        final DialogPane dialogPane = getDialogPane();
        AppServices.setStageIcon(dialogPane.getScene().getWindow());

        StackPane stackPane = new StackPane();
        dialogPane.setContent(stackPane);

        AnchorPane anchorPane = new AnchorPane();
        stackPane.getChildren().add(anchorPane);

        ScrollPane scrollPane = new ScrollPane();
        scrollPane.setPrefHeight(400);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        anchorPane.getChildren().add(scrollPane);
        scrollPane.setFitToWidth(true);
        AnchorPane.setLeftAnchor(scrollPane, 0.0);
        AnchorPane.setRightAnchor(scrollPane, 0.0);

        List<WalletExport> exporters;
        if(wallet.getPolicyType() == PolicyType.SINGLE) {
            exporters = List.of(new Electrum(), new ElectrumPersonalServer(), new Descriptor(), new SpecterDesktop(), new Sparrow(), new WalletLabels(), new WalletTransactions(walletForm));
        } else if(wallet.getPolicyType() == PolicyType.MULTI) {
            exporters = List.of(new Bip129(), new CaravanMultisig(), new ColdcardMultisig(), new CoboVaultMultisig(), new Electrum(), new ElectrumPersonalServer(), new KeystoneMultisig(),
                    new Descriptor(), new JadeMultisig(), new PassportMultisig(), new SpecterDesktop(), new BlueWalletMultisig(), new SpecterDIY(), new Sparrow(), new WalletLabels(), new WalletTransactions(walletForm));
        } else {
            throw new UnsupportedOperationException("Cannot export wallet with policy type " + wallet.getPolicyType());
        }

        Accordion exportAccordion = new Accordion();
        for(WalletExport exporter : exporters) {
            if(!exporter.isDeprecated() || Config.get().isShowDeprecatedImportExport()) {
                FileWalletExportPane exportPane = new FileWalletExportPane(wallet, exporter);
                exportAccordion.getPanes().add(exportPane);
            }
        }

        exportAccordion.getPanes().sort(Comparator.comparing(o -> ((TitledDescriptionPane) o).getTitle()));
        scrollPane.setContent(exportAccordion);

        final ButtonType cancelButtonType = new javafx.scene.control.ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
        dialogPane.getButtonTypes().addAll(cancelButtonType);
        dialogPane.setPrefWidth(500);
        dialogPane.setPrefHeight(480);
        dialogPane.setMinHeight(dialogPane.getPrefHeight());
        AppServices.moveToActiveWindowScreen(this);

        setResultConverter(dialogButton -> dialogButton != cancelButtonType ? wallet : null);
    }

    @Subscribe
    public void walletExported(WalletExportEvent event) {
        wallet = event.getWallet();
        setResult(wallet);
    }
}
