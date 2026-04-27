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
import javafx.application.Platform;
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
import java.util.ArrayList;
import java.util.List;

public class FileWalletKeystoreImportPane extends FileImportPane {
    private static final Logger log = LoggerFactory.getLogger(FileWalletKeystoreImportPane.class);

    private final KeystoreFileImport importer;
    private String fileName;
    private byte[] fileBytes;
    private String password;

    public FileWalletKeystoreImportPane(KeystoreFileImport importer) {
        super(importer, importer.getName(), "Wallet import", importer.getKeystoreImportDescription(), importer.getWalletModel(), importer.isKeystoreImportScannable(), importer.isFileFormatAvailable());
        this.importer = importer;
    }

    protected void importFile(String fileName, InputStream inputStream, String password) throws ImportException {
        this.fileName = fileName;
        this.password = password;

        List<PolicyAndScriptType> types = new ArrayList<>();
        for(PolicyType policyType : List.of(PolicyType.SINGLE_HD, PolicyType.SINGLE_SP)) {
            for(ScriptType scriptType : ScriptType.getAddressableScriptTypes(policyType)) {
                types.add(new PolicyAndScriptType(policyType, scriptType));
            }
        }

        if(wallets != null && !wallets.isEmpty()) {
            wallets.stream().filter(w -> w.getPolicyType() == null).forEach(w -> w.setPolicyType(PolicyType.SINGLE_HD));
            List<PolicyAndScriptType> walletTypes = wallets.stream().map(w -> new PolicyAndScriptType(w.getPolicyType(), w.getScriptType())).toList();
            types.retainAll(walletTypes);
            if(types.isEmpty()) {
                throw new ImportException("No singlesig script types present in QR code");
            }

            if(types.size() == 1) {
                Wallet wallet = wallets.stream().filter(w -> w.getPolicyType() == types.getFirst().policyType() && w.getScriptType() == types.getFirst().scriptType()).findFirst().orElseThrow(ImportException::new);
                wallet.setDefaultPolicy(Policy.getPolicy(wallet.getPolicyType(), wallet.getScriptType(), wallet.getKeystores(), null));
                wallet.setName(importer.getName());
                EventManager.get().post(new WalletImportEvent(wallet));
                return;
            }
        } else {
            try {
                fileBytes = ByteStreams.toByteArray(inputStream);
            } catch(IOException e) {
                throw new ImportException("Could not read file", e);
            }
        }

        setContent(getScriptTypeEntry(types));
        setExpanded(true);
        importButton.setDisable(true);
    }

    private void importWallet(PolicyAndScriptType type) throws ImportException {
        PolicyType policyType = type.policyType();
        ScriptType scriptType = type.scriptType();

        if(wallets != null && !wallets.isEmpty()) {
            Wallet wallet = wallets.stream().filter(w -> w.getPolicyType() == policyType && w.getScriptType() == scriptType).findFirst().orElseThrow(ImportException::new);
            wallet.setName(importer.getName());
            wallet.setDefaultPolicy(Policy.getPolicy(policyType, scriptType, wallet.getKeystores(), null));
            EventManager.get().post(new WalletImportEvent(wallet));
        } else {
            ByteArrayInputStream bais = new ByteArrayInputStream(fileBytes);
            Keystore keystore = importer.getKeystore(policyType, scriptType, bais, password);

            Wallet wallet = new Wallet();
            wallet.setName(Files.getNameWithoutExtension(fileName));
            wallet.setPolicyType(policyType);
            wallet.setScriptType(scriptType);
            wallet.getKeystores().add(keystore);
            wallet.setDefaultPolicy(Policy.getPolicy(policyType, scriptType, wallet.getKeystores(), null));

            EventManager.get().post(new WalletImportEvent(wallet));
        }
    }

    private Node getScriptTypeEntry(List<PolicyAndScriptType> types) {
        Label label = new Label("Type:");

        HBox fieldBox = new HBox(5);
        fieldBox.setAlignment(Pos.CENTER_RIGHT);
        ComboBox<PolicyAndScriptType> comboBox = new ComboBox<>(FXCollections.observableArrayList(types));
        PolicyAndScriptType defaultType = new PolicyAndScriptType(PolicyType.SINGLE_HD, ScriptType.P2WPKH);
        if(types.contains(defaultType)) {
            comboBox.setValue(defaultType);
        }
        comboBox.setConverter(new StringConverter<>() {
            @Override
            public String toString(PolicyAndScriptType type) {
                return type == null ? "" : type.getDescription();
            }

            @Override
            public PolicyAndScriptType fromString(String string) {
                return null;
            }
        });
        comboBox.setMaxWidth(220);

        HelpLabel helpLabel = new HelpLabel();
        helpLabel.setHelpText("Native Segwit is usually the best choice for new wallets.\nTaproot is newer and supports both HD and SP (silent payments) wallets.\nNested Segwit and Legacy are useful for recovering older wallets.\nFor existing wallets, be sure to choose the type that matches the wallet you are importing.");
        fieldBox.getChildren().addAll(comboBox, helpLabel);

        Region region = new Region();
        HBox.setHgrow(region, Priority.SOMETIMES);

        Button importFileButton = new Button("Import");
        importFileButton.setOnAction(event -> {
            showHideLink.setVisible(true);
            setExpanded(false);
            try {
                importWallet(comboBox.getValue());
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

        Platform.runLater(comboBox::requestFocus);

        return contentBox;
    }

    protected record PolicyAndScriptType(PolicyType policyType, ScriptType scriptType) {
        public String getDescription() {
            return scriptType.getDescription() + (policyType == PolicyType.SINGLE_SP ? " SP" : " HD");
        }
    }
}
