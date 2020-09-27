package com.sparrowwallet.sparrow.control;

import com.google.common.io.ByteStreams;
import com.google.gson.JsonParseException;
import com.sparrowwallet.drongo.policy.Policy;
import com.sparrowwallet.drongo.policy.PolicyType;
import com.sparrowwallet.drongo.protocol.Network;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

public class FileWalletKeystoreImportPane extends FileImportPane {
    private static final Logger log = LoggerFactory.getLogger(FileWalletKeystoreImportPane.class);

    private final KeystoreFileImport importer;
    private String fileName;
    private byte[] fileBytes;

    public FileWalletKeystoreImportPane(KeystoreFileImport importer) {
        super(importer, importer.getName(), "Wallet file import", importer.getKeystoreImportDescription(), "image/" + importer.getWalletModel().getType() + ".png");
        this.importer = importer;
    }

    protected void importFile(String fileName, InputStream inputStream, String password) throws ImportException {
        this.fileName = fileName;
        try {
            fileBytes = ByteStreams.toByteArray(inputStream);
        } catch(IOException e) {
            throw new ImportException("Could not read file", e);
        }

        setContent(getScriptTypeEntry());
        setExpanded(true);
        importButton.setDisable(true);
    }

    private void importWallet(ScriptType scriptType) throws ImportException {
        ByteArrayInputStream bais = new ByteArrayInputStream(fileBytes);
        Keystore keystore = importer.getKeystore(scriptType, bais, "");

        //TODO: use user input here
        Wallet wallet = new Wallet(Network.BITCOIN);
        wallet.setName(fileName);
        wallet.setPolicyType(PolicyType.SINGLE);
        wallet.setScriptType(scriptType);
        wallet.getKeystores().add(keystore);
        wallet.setDefaultPolicy(Policy.getPolicy(PolicyType.SINGLE, scriptType, wallet.getKeystores(), null));

        EventManager.get().post(new WalletImportEvent(wallet));
    }

    private Node getScriptTypeEntry() {
        Label label = new Label("Script Type:");
        ComboBox<ScriptType> scriptTypeComboBox = new ComboBox<>(FXCollections.observableArrayList(ScriptType.getAddressableScriptTypes(PolicyType.SINGLE)));
        scriptTypeComboBox.setValue(ScriptType.P2WPKH);

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
        contentBox.getChildren().addAll(label, scriptTypeComboBox, region, importFileButton);
        contentBox.setPadding(new Insets(10, 30, 10, 30));
        contentBox.setPrefHeight(60);

        return contentBox;
    }
}
