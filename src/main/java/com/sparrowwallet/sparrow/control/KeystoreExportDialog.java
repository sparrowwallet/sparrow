package com.sparrowwallet.sparrow.control;

import com.google.common.eventbus.Subscribe;
import com.sparrowwallet.drongo.wallet.Keystore;
import com.sparrowwallet.sparrow.AppServices;
import com.sparrowwallet.sparrow.EventManager;
import com.sparrowwallet.sparrow.event.KeystoreExportEvent;
import com.sparrowwallet.sparrow.io.*;
import javafx.scene.control.*;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.StackPane;

import java.util.Comparator;
import java.util.List;

public class KeystoreExportDialog extends Dialog<Keystore> {
    public KeystoreExportDialog(Keystore keystore) {
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
        scrollPane.setPrefHeight(200);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        anchorPane.getChildren().add(scrollPane);
        scrollPane.setFitToWidth(true);
        AnchorPane.setLeftAnchor(scrollPane, 0.0);
        AnchorPane.setRightAnchor(scrollPane, 0.0);

        List<KeystoreFileExport> exporters = List.of(new Bip129());

        Accordion exportAccordion = new Accordion();
        for(KeystoreFileExport exporter : exporters) {
            if(!exporter.isDeprecated() || Config.get().isShowDeprecatedImportExport()) {
                FileKeystoreExportPane exportPane = new FileKeystoreExportPane(keystore, exporter);
                exportAccordion.getPanes().add(exportPane);
            }
        }

        exportAccordion.getPanes().sort(Comparator.comparing(o -> ((TitledDescriptionPane) o).getTitle()));
        scrollPane.setContent(exportAccordion);

        final ButtonType cancelButtonType = new javafx.scene.control.ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
        dialogPane.getButtonTypes().addAll(cancelButtonType);
        dialogPane.setPrefWidth(500);
        dialogPane.setPrefHeight(280);
        AppServices.moveToActiveWindowScreen(this);

        setResultConverter(dialogButton -> dialogButton != cancelButtonType ? keystore : null);
    }

    @Subscribe
    public void keystoreExported(KeystoreExportEvent event) {
        setResult(event.getKeystore());
    }
}
