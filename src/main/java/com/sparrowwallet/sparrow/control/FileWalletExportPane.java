package com.sparrowwallet.sparrow.control;

import com.sparrowwallet.drongo.wallet.Keystore;
import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.sparrow.EventManager;
import com.sparrowwallet.sparrow.event.WalletExportEvent;
import com.sparrowwallet.sparrow.io.WalletExport;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Control;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.Optional;

public class FileWalletExportPane extends TitledDescriptionPane {
    private final Wallet wallet;
    private final WalletExport exporter;

    public FileWalletExportPane(Wallet wallet, WalletExport exporter) {
        super(exporter.getName(), "Wallet file export", exporter.getWalletExportDescription(), "image/" + exporter.getWalletModel().getType() + ".png");
        this.wallet = wallet;
        this.exporter = exporter;
    }

    @Override
    protected Control createButton() {
        Button exportButton = new Button("Export Wallet...");
        exportButton.setAlignment(Pos.CENTER_RIGHT);
        exportButton.setOnAction(event -> {
            exportWallet();
        });
        return exportButton;
    }

    private void exportWallet() {
        Stage window = new Stage();

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Export " + exporter.getWalletModel().toDisplayString() + " File");

        File file = fileChooser.showSaveDialog(window);
        if(file != null) {
            exportWallet(file);
        }
    }

    private void exportWallet(File file) {
        Wallet copy = wallet.copy();

        if(copy.isEncrypted()) {
            WalletPasswordDialog dlg = new WalletPasswordDialog(WalletPasswordDialog.PasswordRequirement.LOAD);
            Optional<String> password = dlg.showAndWait();
            if(password.isPresent()) {
               copy.decrypt(password.get(), "");

               for(Keystore keystore : copy.getKeystores()) {
                   if(keystore.hasSeed() && keystore.getSeed().usesPassphrase()) {
                       KeystorePassphraseDialog passphraseDialog = new KeystorePassphraseDialog(keystore);
                       Optional<String> passphrase = passphraseDialog.showAndWait();
                       if(passphrase.isPresent()) {
                           keystore.getSeed().setPassphrase(passphrase.get());
                       } else {
                           return;
                       }
                   }
               }
            } else {
                return;
            }
        }

        try {
            OutputStream outputStream = new FileOutputStream(file);
            exporter.exportWallet(copy, outputStream);
            EventManager.get().post(new WalletExportEvent(copy));
        } catch(Exception e) {
            String errorMessage = e.getMessage();
            if(e.getCause() != null && e.getCause().getMessage() != null && !e.getCause().getMessage().isEmpty()) {
                errorMessage = e.getCause().getMessage();
            }
            setError("Export Error", errorMessage);
        }
    }
}
