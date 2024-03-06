package com.sparrowwallet.sparrow.control;

import com.sparrowwallet.drongo.KeyPurpose;
import com.sparrowwallet.drongo.OutputDescriptor;
import com.sparrowwallet.drongo.SecureString;
import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.hummingbird.UR;
import com.sparrowwallet.hummingbird.registry.CryptoOutput;
import com.sparrowwallet.hummingbird.registry.RegistryType;
import com.sparrowwallet.sparrow.AppServices;
import com.sparrowwallet.sparrow.EventManager;
import com.sparrowwallet.sparrow.event.StorageEvent;
import com.sparrowwallet.sparrow.event.TimedEvent;
import com.sparrowwallet.sparrow.event.WalletExportEvent;
import com.sparrowwallet.sparrow.glyphfont.FontAwesome5;
import com.sparrowwallet.sparrow.io.*;
import com.sparrowwallet.sparrow.io.bbqr.BBQR;
import com.sparrowwallet.sparrow.io.bbqr.BBQRType;
import javafx.concurrent.Service;
import javafx.concurrent.Task;
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

import static com.sparrowwallet.sparrow.wallet.SettingsController.getCryptoOutput;

public class FileWalletExportPane extends TitledDescriptionPane {
    private final Wallet wallet;
    private final WalletExport exporter;
    private final boolean scannable;
    private final boolean file;

    public FileWalletExportPane(Wallet wallet, WalletExport exporter) {
        super(exporter.getName(), "Wallet export", exporter.getWalletExportDescription(), "image/" + exporter.getWalletModel().getType() + ".png");
        this.wallet = wallet;
        this.exporter = exporter;
        this.scannable = exporter.isWalletExportScannable();
        this.file = exporter.isWalletExportFile();

        buttonBox.getChildren().clear();
        buttonBox.getChildren().add(createButton());
    }

    @Override
    protected Control createButton() {
        if(scannable && file) {
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
        } else if(scannable) {
            Button showButton = new Button("Show...");
            Glyph cameraGlyph = new Glyph(FontAwesome5.FONT_NAME, FontAwesome5.Glyph.CAMERA);
            cameraGlyph.setFontSize(12);
            showButton.setGraphic(cameraGlyph);
            showButton.setOnAction(event -> {
                exportQR();
            });
            return showButton;
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
        String walletModel = exporter.getWalletModel().toDisplayString().toLowerCase(Locale.ROOT).replace(" ", "");
        String postfix = walletModel.equals(extension) ? "" : "-" + walletModel;
        String fileName = wallet.getFullName() + postfix;
        if(exporter.exportsAllWallets()) {
            fileName = wallet.getMasterName() + postfix;
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
            dlg.initOwner(buttonBox.getScene().getWindow());
            Optional<SecureString> password = dlg.showAndWait();
            if(password.isPresent()) {
                final String walletId = AppServices.get().getOpenWallets().get(wallet).getWalletId(wallet);
                String walletPassword = password.get().asString();
                Storage.DecryptWalletService decryptWalletService = new Storage.DecryptWalletService(copy, password.get());
                decryptWalletService.setOnSucceeded(workerStateEvent -> {
                    EventManager.get().post(new StorageEvent(walletId, TimedEvent.Action.END, "Done"));
                    Wallet decryptedWallet = decryptWalletService.getValue();
                    exportWallet(file, decryptedWallet, walletPassword);
                });
                decryptWalletService.setOnFailed(workerStateEvent -> {
                    EventManager.get().post(new StorageEvent(walletId, TimedEvent.Action.END, "Failed"));
                    setError("Export Error", decryptWalletService.getException().getMessage());
                });
                EventManager.get().post(new StorageEvent(walletId, TimedEvent.Action.START, "Decrypting wallet..."));
                decryptWalletService.start();
            }
        } else {
            exportWallet(file, wallet, null);
        }
    }

    private void exportWallet(File file, Wallet exportWallet, String password) {
        try {
            if(file != null) {
                FileWalletExportService fileWalletExportService = new FileWalletExportService(exporter, file, exportWallet, password);
                fileWalletExportService.setOnSucceeded(event -> {
                    EventManager.get().post(new WalletExportEvent(exportWallet));
                });
                fileWalletExportService.setOnFailed(event -> {
                    Throwable e = event.getSource().getException();
                    String errorMessage = e.getMessage();
                    if(e.getCause() != null && e.getCause().getMessage() != null && !e.getCause().getMessage().isEmpty()) {
                        errorMessage = e.getCause().getMessage();
                    }
                    setError("Export Error", errorMessage);
                });
                fileWalletExportService.start();
            } else {
                ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                exporter.exportWallet(exportWallet, outputStream, password);
                QRDisplayDialog qrDisplayDialog;
                if(exporter instanceof CoboVaultMultisig) {
                    qrDisplayDialog = new QRDisplayDialog(RegistryType.BYTES.toString(), outputStream.toByteArray(), true);
                } else if(exporter instanceof PassportMultisig || exporter instanceof KeystoneMultisig || exporter instanceof JadeMultisig) {
                    qrDisplayDialog = new QRDisplayDialog(RegistryType.BYTES.toString(), outputStream.toByteArray(), false);
                } else if(exporter instanceof Bip129) {
                    UR ur = UR.fromBytes(outputStream.toByteArray());
                    BBQR bbqr = new BBQR(BBQRType.UNICODE, outputStream.toByteArray());
                    qrDisplayDialog = new QRDisplayDialog(ur, bbqr, false, true, false);
                } else if(exporter instanceof Descriptor) {
                    OutputDescriptor outputDescriptor = OutputDescriptor.getOutputDescriptor(exportWallet, KeyPurpose.DEFAULT_PURPOSES, null);
                    CryptoOutput cryptoOutput = getCryptoOutput(exportWallet);
                    qrDisplayDialog = new DescriptorQRDisplayDialog(exportWallet.getFullDisplayName(), outputDescriptor.toString(true), cryptoOutput.toUR());
                } else {
                    qrDisplayDialog = new QRDisplayDialog(outputStream.toString(StandardCharsets.UTF_8));
                }
                qrDisplayDialog.initOwner(buttonBox.getScene().getWindow());
                qrDisplayDialog.showAndWait();
            }
        } catch(Exception e) {
            String errorMessage = e.getMessage();
            if(e.getCause() != null && e.getCause().getMessage() != null && !e.getCause().getMessage().isEmpty()) {
                errorMessage = e.getCause().getMessage();
            }
            setError("Export Error", errorMessage);
        } finally {
            if(file == null && password != null) {
                exportWallet.clearPrivate();
            }
        }
    }

    public static class FileWalletExportService extends Service<Void> {
        private final WalletExport exporter;
        private final File file;
        private final Wallet wallet;
        private final String password;

        public FileWalletExportService(WalletExport exporter, File file, Wallet wallet, String password) {
            this.exporter = exporter;
            this.file = file;
            this.wallet = wallet;
            this.password = password;
        }

        @Override
        protected Task<Void> createTask() {
            return new Task<>() {
                @Override
                protected Void call() throws Exception {
                    try(OutputStream outputStream = new FileOutputStream(file)) {
                        exporter.exportWallet(wallet, outputStream, password);
                    } finally {
                        if(password != null) {
                            wallet.clearPrivate();
                        }
                    }

                    return null;
                }
            };
        }
    }
}
