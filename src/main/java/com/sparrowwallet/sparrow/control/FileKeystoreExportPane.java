package com.sparrowwallet.sparrow.control;

import com.sparrowwallet.drongo.protocol.ScriptType;
import com.sparrowwallet.drongo.wallet.Keystore;
import com.sparrowwallet.drongo.wallet.KeystoreSource;
import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.hummingbird.UR;
import com.sparrowwallet.sparrow.AppServices;
import com.sparrowwallet.sparrow.EventManager;
import com.sparrowwallet.sparrow.event.KeystoreExportEvent;
import com.sparrowwallet.sparrow.glyphfont.FontAwesome5;
import com.sparrowwallet.sparrow.io.*;
import com.sparrowwallet.sparrow.io.bbqr.BBQR;
import com.sparrowwallet.sparrow.io.bbqr.BBQRType;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Control;
import javafx.scene.control.ToggleButton;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.controlsfx.control.SegmentedButton;
import org.controlsfx.glyphfont.Glyph;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

public class FileKeystoreExportPane extends TitledDescriptionPane {
    private final Keystore keystore;
    private final KeystoreFileExport exporter;
    private final boolean scannable;
    private final boolean file;

    public FileKeystoreExportPane(Keystore keystore, KeystoreFileExport exporter) {
        super(exporter.getName(), "Keystore export", exporter.getKeystoreExportDescription(), "image/" + exporter.getWalletModel().getType() + ".png");
        this.keystore = keystore;
        this.exporter = exporter;
        this.scannable = exporter.isKeystoreExportScannable();
        this.file = exporter.isKeystoreExportFile();

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
        exportKeystore(null, keystore);
    }

    private void exportFile() {
        Stage window = new Stage();

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Export " + exporter.getWalletModel().toDisplayString() + " File");
        String extension = exporter.getExportFileExtension(keystore);
        String fileName = keystore.getLabel();
        fileChooser.setInitialFileName(fileName + (extension == null || extension.isEmpty() ? "" : "." + extension));

        AppServices.moveToActiveWindowScreen(window, 800, 450);
        File file = fileChooser.showSaveDialog(window);
        if(file != null) {
            exportKeystore(file, keystore);
        }
    }

    private void exportKeystore(File file, Keystore exportKeystore) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            exporter.exportKeystore(exportKeystore, baos);

            if(exporter.requiresSignature()) {
                String message = baos.toString(StandardCharsets.UTF_8);

                if(keystore.getSource() == KeystoreSource.HW_USB || keystore.getWalletModel().isCard()) {
                    TextAreaDialog dialog = new TextAreaDialog(message, false);
                    dialog.initOwner(this.getScene().getWindow());
                    dialog.setTitle("Sign " + exporter.getName() + " Export");
                    dialog.getDialogPane().setHeaderText("The following text needs to be signed by the device.\nClick OK to continue.");
                    dialog.showAndWait();

                    Wallet wallet = new Wallet();
                    wallet.setScriptType(ScriptType.P2PKH);
                    wallet.getKeystores().add(keystore);
                    List<String> operationFingerprints = List.of(keystore.getKeyDerivation().getMasterFingerprint());

                    DeviceSignMessageDialog deviceSignMessageDialog = new DeviceSignMessageDialog(operationFingerprints, wallet, message, keystore.getKeyDerivation());
                    deviceSignMessageDialog.initOwner(this.getScene().getWindow());
                    Optional<String> optSignature = deviceSignMessageDialog.showAndWait();
                    if(optSignature.isPresent()) {
                        exporter.addSignature(keystore, optSignature.get(), baos);
                    }
                } else if(keystore.getSource() == KeystoreSource.SW_SEED) {
                    String signature = keystore.getExtendedPrivateKey().getKey().signMessage(message, ScriptType.P2PKH);
                    exporter.addSignature(keystore, signature, baos);
                } else {
                    Optional<ButtonType> optButtonType = AppServices.showWarningDialog("Cannot sign export",
                            "Signing the " +  exporter.getName() + " export with " + keystore.getWalletModel().toDisplayString() + " is not supported." +
                            "Proceed without signing?", ButtonType.NO, ButtonType.YES);
                    if(optButtonType.isPresent() && optButtonType.get() == ButtonType.NO) {
                        throw new RuntimeException("Export aborted due to lack of device message signing support.");
                    }
                }
            }

            if(file != null) {
                try(OutputStream outputStream = new FileOutputStream(file)) {
                    outputStream.write(baos.toByteArray());
                    EventManager.get().post(new KeystoreExportEvent(exportKeystore));
                }
            } else {
                QRDisplayDialog qrDisplayDialog;
                if(exporter instanceof Bip129) {
                    UR ur = UR.fromBytes(baos.toByteArray());
                    BBQR bbqr = new BBQR(BBQRType.UNICODE, baos.toByteArray());
                    qrDisplayDialog = new QRDisplayDialog(ur, bbqr, false, true, false);
                } else {
                    qrDisplayDialog = new QRDisplayDialog(baos.toString(StandardCharsets.UTF_8));
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
        }
    }
}
