package com.sparrowwallet.sparrow.control;

import com.google.common.eventbus.Subscribe;
import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.sparrow.AppServices;
import com.sparrowwallet.sparrow.EventManager;
import com.sparrowwallet.sparrow.event.UsbDeviceEvent;
import com.sparrowwallet.sparrow.event.WalletImportEvent;
import com.sparrowwallet.sparrow.glyphfont.FontAwesome5;
import com.sparrowwallet.sparrow.glyphfont.FontAwesome5Brands;
import com.sparrowwallet.sparrow.io.*;
import com.sparrowwallet.sparrow.wallet.WalletForm;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.scene.control.*;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.StackPane;
import org.controlsfx.glyphfont.Glyph;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class WalletImportDialog extends Dialog<Wallet> {
    private Wallet wallet;
    private final Accordion importAccordion;
    private final Button scanButton;

    public WalletImportDialog(List<WalletForm> selectedWalletForms) {
        EventManager.get().register(this);
        setOnCloseRequest(event -> {
            EventManager.get().unregister(this);
        });

        final DialogPane dialogPane = getDialogPane();
        dialogPane.getStylesheets().add(AppServices.class.getResource("general.css").toExternalForm());
        AppServices.setStageIcon(dialogPane.getScene().getWindow());

        StackPane stackPane = new StackPane();
        dialogPane.setContent(stackPane);

        AnchorPane anchorPane = new AnchorPane();
        stackPane.getChildren().add(anchorPane);

        ScrollPane scrollPane = new ScrollPane();
        scrollPane.setPrefHeight(520);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        anchorPane.getChildren().add(scrollPane);
        scrollPane.setFitToWidth(true);
        AnchorPane.setLeftAnchor(scrollPane, 0.0);
        AnchorPane.setRightAnchor(scrollPane, 0.0);

        importAccordion = new Accordion();
        List<KeystoreFileImport> keystoreImporters = List.of(new ColdcardSinglesig(), new CoboVaultSinglesig(), new Jade(), new KeystoneSinglesig(), new PassportSinglesig(), new GordianSeedTool(), new SeedSigner(), new SpecterDIY(), new Krux(), new AirGapVault());
        for(KeystoreFileImport importer : keystoreImporters) {
            if(!importer.isDeprecated() || Config.get().isShowDeprecatedImportExport()) {
                FileWalletKeystoreImportPane importPane = new FileWalletKeystoreImportPane(importer);
                importAccordion.getPanes().add(importPane);
            }
        }

        List<WalletImport> walletImporters = new ArrayList<>(List.of(new Bip129(), new CaravanMultisig(), new ColdcardMultisig(), new CoboVaultMultisig(), new Electrum(), new KeystoneMultisig(), new Descriptor(), new SpecterDesktop(), new BlueWalletMultisig(), new Sparrow(), new JadeMultisig()));
        if(!selectedWalletForms.isEmpty()) {
            walletImporters.add(new WalletLabels(selectedWalletForms));
        }
        for(WalletImport importer : walletImporters) {
            if(!importer.isDeprecated() || Config.get().isShowDeprecatedImportExport()) {
                FileWalletImportPane importPane = new FileWalletImportPane(importer);
                importAccordion.getPanes().add(importPane);
            }
        }

        importAccordion.getPanes().sort(Comparator.comparing(o -> ((TitledDescriptionPane) o).getTitle()));

        MnemonicWalletKeystoreImportPane mnemonicImportPane = new MnemonicWalletKeystoreImportPane(new Bip39());
        importAccordion.getPanes().add(0, mnemonicImportPane);

        scrollPane.setContent(importAccordion);

        final ButtonType cancelButtonType = new javafx.scene.control.ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
        final ButtonType scanButtonType = new javafx.scene.control.ButtonType("Scan for Connected Devices", ButtonBar.ButtonData.LEFT);
        dialogPane.getButtonTypes().addAll(scanButtonType, cancelButtonType);

        scanButton = (Button) dialogPane.lookupButton(scanButtonType);
        scanButton.setGraphic(new Glyph(FontAwesome5Brands.FONT_NAME, FontAwesome5Brands.Glyph.USB));
        scanButton.addEventFilter(ActionEvent.ACTION, event -> {
            scan();
            event.consume();
        });

        dialogPane.setPrefWidth(500);
        dialogPane.setPrefHeight(600);
        dialogPane.setMinHeight(dialogPane.getPrefHeight());
        AppServices.moveToActiveWindowScreen(this);

        setResultConverter(dialogButton -> dialogButton != cancelButtonType ? wallet : null);
    }

    @Subscribe
    public void walletImported(WalletImportEvent event) {
        wallet = event.getWallet();
        setResult(wallet);
    }

    private void scan() {
        Hwi.EnumerateService enumerateService = new Hwi.EnumerateService(null);
        enumerateService.setOnSucceeded(workerStateEvent -> {
            scanButton.setGraphic(new Glyph(FontAwesome5Brands.FONT_NAME, FontAwesome5Brands.Glyph.USB));
            scanButton.setTooltip(null);
            List<Device> devices = enumerateService.getValue();
            importAccordion.getPanes().removeIf(titledPane -> titledPane instanceof DevicePane);
            for(Device device : devices) {
                DevicePane devicePane = new DevicePane(new Wallet(), device, devices.size() == 1, null);
                importAccordion.getPanes().add(0, devicePane);
            }
            Platform.runLater(() -> EventManager.get().post(new UsbDeviceEvent(devices)));
        });
        enumerateService.setOnFailed(workerStateEvent -> {
            Glyph glyph = new Glyph(FontAwesome5.FONT_NAME, FontAwesome5.Glyph.EXCLAMATION_CIRCLE);
            glyph.getStyleClass().add("failure");
            scanButton.setGraphic(glyph);
            scanButton.setTooltip(new Tooltip(workerStateEvent.getSource().getException().getMessage()));
        });
        enumerateService.start();
    }
}
