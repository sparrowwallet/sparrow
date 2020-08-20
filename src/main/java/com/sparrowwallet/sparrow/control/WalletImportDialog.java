package com.sparrowwallet.sparrow.control;

import com.google.common.eventbus.Subscribe;
import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.sparrow.EventManager;
import com.sparrowwallet.sparrow.event.WalletImportEvent;
import com.sparrowwallet.sparrow.io.*;
import javafx.scene.control.*;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.StackPane;

import java.util.List;

public class WalletImportDialog extends Dialog<Wallet> {
    private Wallet wallet;

    public WalletImportDialog() {
        EventManager.get().register(this);
        setOnCloseRequest(event -> {
            EventManager.get().unregister(this);
        });

        final DialogPane dialogPane = getDialogPane();

        StackPane stackPane = new StackPane();
        dialogPane.setContent(stackPane);

        AnchorPane anchorPane = new AnchorPane();
        stackPane.getChildren().add(anchorPane);

        ScrollPane scrollPane = new ScrollPane();
        scrollPane.setPrefHeight(280);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        anchorPane.getChildren().add(scrollPane);
        scrollPane.setFitToWidth(true);
        AnchorPane.setLeftAnchor(scrollPane, 0.0);
        AnchorPane.setRightAnchor(scrollPane, 0.0);

        List<WalletImport> importers = List.of(new ColdcardMultisig(), new Electrum());
        Accordion importAccordion = new Accordion();
        for(WalletImport importer : importers) {
            FileWalletImportPane importPane = new FileWalletImportPane(importer);
            importAccordion.getPanes().add(importPane);
        }
        scrollPane.setContent(importAccordion);

        final ButtonType cancelButtonType = new javafx.scene.control.ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
        dialogPane.getButtonTypes().addAll(cancelButtonType);
        dialogPane.setPrefWidth(500);
        dialogPane.setPrefHeight(360);

        setResultConverter(dialogButton -> dialogButton != cancelButtonType ? wallet : null);
    }

    @Subscribe
    public void walletImported(WalletImportEvent event) {
        wallet = event.getWallet();
        setResult(wallet);
    }
}
