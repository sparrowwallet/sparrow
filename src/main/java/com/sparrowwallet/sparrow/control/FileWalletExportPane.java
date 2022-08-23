package com.sparrowwallet.sparrow.control;

import com.sparrowwallet.drongo.SecureString;
import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.hummingbird.registry.RegistryType;
import com.sparrowwallet.sparrow.AppServices;
import com.sparrowwallet.sparrow.EventManager;
import com.sparrowwallet.sparrow.event.StorageEvent;
import com.sparrowwallet.sparrow.event.TimedEvent;
import com.sparrowwallet.sparrow.event.WalletExportEvent;
import com.sparrowwallet.sparrow.glyphfont.FontAwesome5;
import com.sparrowwallet.sparrow.io.*;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Control;
import javafx.scene.control.ToggleButton;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.controlsfx.control.SegmentedButton;
import org.controlsfx.glyphfont.Glyph;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Optional;

public class FileWalletExportPane extends TitledDescriptionPane {
    private final Wallet wallet;
    private final WalletExport exporter;
    private final boolean scannable;

    public FileWalletExportPane(Wallet wallet, WalletExport exporter) {
        super(exporter.getName(), "Wallet file export", exporter.getWalletExportDescription(), "image/" + exporter.getWalletModel().getType() + ".png");
        this.wallet = wallet;
        this.exporter = exporter;
        this.scannable = exporter.isWalletExportScannable();

        buttonBox.getChildren().clear();
        buttonBox.getChildren().add(createButton());
    }

    @Override
    protected Control createButton() {
        if(scannable) {
            ToggleButton showButton = new ToggleButton("Show...");
            Glyph cameraGlyph = new Glyph(FontAwesome5.FONT_NAME, FontAwesome5.Glyph.CAMERA);
            cameraGlyph.setFontSize(12);
            showButton.setGraphic(cameraGlyph);
            showButton.setOnAction(event -> {
                showButton.setSelected(false);
                exportQR();
            });

            ToggleButton fileButton = new ToggleButton("Export File...");
            fileButton.setAlignment(Pos.CENTER_RIGHT);
            fileButton.setOnAction(event -> {
                fileButton.setSelected(false);
                exportFile();
            });

            SegmentedButton segmentedButton = new SegmentedButton();
            segmentedButton.getButtons().addAll(showButton, fileButton);
            return segmentedButton;
        } else {
            Button exportButton = new Button("Export File...");
            exportButton.setAlignment(Pos.CENTER_RIGHT);
            exportButton.setOnAction(event -> {
                exportFile();
            });
            return exportButton;
        }
    }

    private void exportQR() {
        exportWallet(null);
    }

    private void exportFile() {
        Stage window = new Stage();

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Export " + exporter.getWalletModel().toDisplayString() + " File");
        String extension = exporter.getExportFileExtension(wallet);
        String fileName = wallet.getFullName() + "-" + exporter.getWalletModel().toDisplayString().toLowerCase(Locale.ROOT).replace(" ", "");
        if(exporter.exportsAllWallets()) {
            fileName = wallet.getMasterName();
        }
        fileChooser.setInitialFileName(fileName + (extension == null || extension.isEmpty() ? "" : "." + extension));

        AppServices.moveToActiveWindowScreen(window, 800, 450);
        File file = fileChooser.showSaveDialog(window);
        if(file != null) {
            exportWallet(file);
        }
    }

    private void exportWallet(File file) {
        if(wallet.isEncrypted() && exporter.walletExportRequiresDecryption()) {
            Wallet copy = wallet.copy();
            WalletPasswordDialog dlg = new WalletPasswordDialog(wallet.getMasterName(), WalletPasswordDialog.PasswordRequirement.LOAD);
            Optional<SecureString> password = dlg.showAndWait();
            if(password.isPresent()) {
                final String walletId = AppServices.get().getOpenWallets().get(wallet).getWalletId(wallet);
                Storage.DecryptWalletService decryptWalletService = new Storage.DecryptWalletService(copy, password.get());
                decryptWalletService.setOnSucceeded(workerStateEvent -> {
                    EventManager.get().post(new StorageEvent(walletId, TimedEvent.Action.END, "Done"));
                    Wallet decryptedWallet = decryptWalletService.getValue();

                    try {
                        exportWallet(file, decryptedWallet);
                    } finally {
                        decryptedWallet.clearPrivate();
                    }
                });
                decryptWalletService.setOnFailed(workerStateEvent -> {
                    EventManager.get().post(new StorageEvent(walletId, TimedEvent.Action.END, "Failed"));
                    setError("Export Error", decryptWalletService.getException().getMessage());
                });
                EventManager.get().post(new StorageEvent(walletId, TimedEvent.Action.START, "Decrypting wallet..."));
                decryptWalletService.start();
            }
        } else {
            exportWallet(file, wallet);
        }
    }

    private void exportWallet(File file, Wallet exportWallet) {
        try {
            if(file != null) {
                try(OutputStream outputStream = new FileOutputStream(file)) {
                    exporter.exportWallet(exportWallet, outputStream);
                    EventManager.get().post(new WalletExportEvent(exportWallet));
                }
            } else {
                ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                exporter.exportWallet(exportWallet, outputStream);
                QRDisplayDialog qrDisplayDialog;
                if(exporter instanceof CoboVaultMultisig) {
                    qrDisplayDialog = new QRDisplayDialog(RegistryType.BYTES.toString(), outputStream.toByteArray(), true);
                } else if(exporter instanceof PassportMultisig || exporter instanceof KeystoneMultisig) {
                    qrDisplayDialog = new QRDisplayDialog(RegistryType.BYTES.toString(), outputStream.toByteArray(), false);
                } else {
                    qrDisplayDialog = new QRDisplayDialog(outputStream.toString(StandardCharsets.UTF_8));
                }
                qrDisplayDialog.showAndWait();
            }
        } catch(Exception e) {
            String errorMessage = e.getMessage();
            if(e.getCause() != null && e.getCause().getMessage() != null && !e.getCause().getMessage().isEmpty()) {
                errorMessage = e.getCause().getMessage();
            }
            setError("Export Error", errorMessage);
        }
    }
}
