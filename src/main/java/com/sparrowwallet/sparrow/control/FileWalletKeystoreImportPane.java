package com.sparrowwallet.sparrow.control;

import com.google.common.io.ByteStreams;
import com.google.common.io.Files;
import com.google.gson.JsonParseException;
import com.sparrowwallet.drongo.policy.Policy;
import com.sparrowwallet.drongo.policy.PolicyType;
import com.sparrowwallet.drongo.protocol.ScriptType;
import com.sparrowwallet.drongo.wallet.Keystore;
import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.sparrow.EventManager;
import com.sparrowwallet.sparrow.event.WalletImportEvent;
import com.sparrowwallet.sparrow.io.ImportException;
import com.sparrowwallet.sparrow.io.KeystoreFileImport;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.util.StringConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.stream.Collectors;

public class FileWalletKeystoreImportPane extends FileImportPane {
    private static final Logger log = LoggerFactory.getLogger(FileWalletKeystoreImportPane.class);

    private final KeystoreFileImport importer;
    private String fileName;
    private byte[] fileBytes;

    public FileWalletKeystoreImportPane(KeystoreFileImport importer) {
        super(importer, importer.getName(), "Wallet import", importer.getKeystoreImportDescription(), "image/" + importer.getWalletModel().getType() + ".png", importer.isKeystoreImportScannable(), importer.isFileFormatAvailable());
        this.importer = importer;
    }

    protected void importFile(String fileName, InputStream inputStream, String password) throws ImportException {
        this.fileName = fileName;

        List<ScriptType> scriptTypes = ScriptType.getAddressableScriptTypes(PolicyType.SINGLE);
        if(wallets != null && !wallets.isEmpty()) {
            if(wallets.size() == 1 && scriptTypes.contains(wallets.get(0).getScriptType())) {
                Wallet wallet = wallets.get(0);
                wallet.setPolicyType(PolicyType.SINGLE);
                wallet.setDefaultPolicy(Policy.getPolicy(PolicyType.SINGLE, wallet.getScriptType(), wallet.getKeystores(), null));
                wallet.setName(importer.getName());
                EventManager.get().post(new WalletImportEvent(wallets.get(0)));
            } else {
                scriptTypes.retainAll(wallets.stream().map(Wallet::getScriptType).collect(Collectors.toList()));
                if(scriptTypes.isEmpty()) {
                    throw new ImportException("No singlesig script types present in QR code");
                }
            }
        } else {
            try {
                fileBytes = ByteStreams.toByteArray(inputStream);
            } catch(IOException e) {
                throw new ImportException("Could not read file", e);
            }
        }

        setContent(getScriptTypeEntry(scriptTypes));
        setExpanded(true);
        importButton.setDisable(true);
    }

    private void importWallet(ScriptType scriptType) throws ImportException {
        if(wallets != null && !wallets.isEmpty()) {
            Wallet wallet = wallets.stream().filter(wallet1 -> wallet1.getScriptType() == scriptType).findFirst().orElseThrow(ImportException::new);
            wallet.setName(importer.getName());
            wallet.setPolicyType(PolicyType.SINGLE);
            wallet.setDefaultPolicy(Policy.getPolicy(PolicyType.SINGLE, wallet.getScriptType(), wallet.getKeystores(), null));
            EventManager.get().post(new WalletImportEvent(wallet));
        } else {
            ByteArrayInputStream bais = new ByteArrayInputStream(fileBytes);
            Keystore keystore = importer.getKeystore(scriptType, bais, "");

            Wallet wallet = new Wallet();
            wallet.setName(Files.getNameWithoutExtension(fileName));
            wallet.setPolicyType(PolicyType.SINGLE);
            wallet.setScriptType(scriptType);
            wallet.getKeystores().add(keystore);
            wallet.setDefaultPolicy(Policy.getPolicy(PolicyType.SINGLE, scriptType, wallet.getKeystores(), null));

            EventManager.get().post(new WalletImportEvent(wallet));
        }
    }

    private Node getScriptTypeEntry(List<ScriptType> scriptTypes) {
        Label label = new Label("Script Type:");

        HBox fieldBox = new HBox(5);
        fieldBox.setAlignment(Pos.CENTER_RIGHT);
        ComboBox<ScriptType> scriptTypeComboBox = new ComboBox<>(FXCollections.observableArrayList(scriptTypes));
        if(scriptTypes.contains(ScriptType.P2WPKH)) {
            scriptTypeComboBox.setValue(ScriptType.P2WPKH);
        }
        scriptTypeComboBox.setConverter(new StringConverter<>() {
            @Override
            public String toString(ScriptType scriptType) {
                return scriptType == null ? "" : scriptType.getDescription();
            }

            @Override
            public ScriptType fromString(String string) {
                return null;
            }
        });
        scriptTypeComboBox.setMaxWidth(170);

        HelpLabel helpLabel = new HelpLabel();
        helpLabel.setHelpText("P2WPKH is a Native Segwit type and is usually the best choice for new wallets.\nP2SH-P2WPKH is a Wrapped Segwit type and is a reasonable choice for the widest compatibility.\nP2PKH is a Legacy type and should be avoided for new wallets.\nFor existing wallets, be sure to choose the type that matches the wallet you are importing.");
        fieldBox.getChildren().addAll(scriptTypeComboBox, helpLabel);

        Region region = new Region();
        HBox.setHgrow(region, Priority.SOMETIMES);

        Button importFileButton = new Button("Import");
        importFileButton.setOnAction(event -> {
            showHideLink.setVisible(true);
            setExpanded(false);
            try {
                importWallet(scriptTypeComboBox.getValue());
            } catch(ImportException e) {
                log.error("Error importing file", e);
                String errorMessage = e.getMessage();
                if(e.getCause() instanceof JsonParseException) {
                    errorMessage = "File was not in JSON format";
                } else if(e.getCause() != null && e.getCause().getMessage() != null && !e.getCause().getMessage().isEmpty()) {
                    errorMessage = e.getCause().getMessage();
                }
                setError("Import Error", errorMessage);
                importButton.setDisable(false);
            }
        });

        HBox contentBox = new HBox();
        contentBox.setAlignment(Pos.CENTER_RIGHT);
        contentBox.setSpacing(20);
        contentBox.getChildren().addAll(label, fieldBox, region, importFileButton);
        contentBox.setPadding(new Insets(10, 30, 10, 30));
        contentBox.setPrefHeight(60);

        return contentBox;
    }
}
