package com.sparrowwallet.sparrow.control;

import com.sparrowwallet.drongo.crypto.ChildNumber;
import com.sparrowwallet.drongo.policy.Policy;
import com.sparrowwallet.drongo.policy.PolicyType;
import com.sparrowwallet.drongo.protocol.ScriptType;
import com.sparrowwallet.drongo.wallet.Keystore;
import com.sparrowwallet.drongo.wallet.MnemonicException;
import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.sparrow.AppServices;
import com.sparrowwallet.sparrow.EventManager;
import com.sparrowwallet.sparrow.event.WalletImportEvent;
import com.sparrowwallet.sparrow.io.Config;
import com.sparrowwallet.sparrow.io.ImportException;
import com.sparrowwallet.sparrow.io.KeystoreMnemonicImport;
import com.sparrowwallet.sparrow.net.ElectrumServer;
import com.sparrowwallet.sparrow.net.ServerType;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.util.StringConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class MnemonicWalletKeystoreImportPane extends MnemonicKeystorePane {
    private static final Logger log = LoggerFactory.getLogger(MnemonicWalletKeystoreImportPane.class);

    private final KeystoreMnemonicImport importer;

    private Button discoverButton;
    private Button importButton;

    public MnemonicWalletKeystoreImportPane(KeystoreMnemonicImport importer) {
        super(importer.getName(), "Seed import", importer.getKeystoreImportDescription(), "image/" + importer.getWalletModel().getType() + ".png");
        this.importer = importer;
    }

    @Override
    protected void enterMnemonic(int numWords) {
        super.enterMnemonic(numWords);
        setDescription("Enter word list");
    }

    @Override
    protected List<Node> createRightButtons() {
        discoverButton = new Button("Discover Wallet");
        discoverButton.setDisable(true);
        discoverButton.setDefaultButton(true);
        discoverButton.managedProperty().bind(discoverButton.visibleProperty());
        discoverButton.setOnAction(event -> {
            discoverWallet();
        });
        discoverButton.managedProperty().bind(discoverButton.visibleProperty());
        discoverButton.setTooltip(new Tooltip("Look for existing transactions from the provided word list"));
        discoverButton.visibleProperty().bind(AppServices.onlineProperty());

        importButton = new Button("Import Wallet");
        importButton.setDisable(true);
        importButton.managedProperty().bind(importButton.visibleProperty());
        importButton.visibleProperty().bind(discoverButton.visibleProperty().not());
        importButton.setOnAction(event -> {
            setContent(getScriptTypeEntry());
            setExpanded(true);
        });

        return List.of(discoverButton, importButton);
    }

    @Override
    protected void onWordChange(boolean empty, boolean validWords, boolean validChecksum) {
        if(!empty && validWords) {
            try {
                importer.getKeystore(ScriptType.P2WPKH.getDefaultDerivation(), wordEntriesProperty.get(), passphraseProperty.get());
                validChecksum = true;
            } catch(ImportException e) {
                if(e.getCause() instanceof MnemonicException.MnemonicTypeException) {
                    invalidLabel.setText("Unsupported Electrum seed");
                    invalidLabel.setTooltip(new Tooltip("Seeds created in Electrum do not follow the BIP39 standard. Import the Electrum wallet file directly."));
                } else {
                    invalidLabel.setText("Invalid checksum");
                    invalidLabel.setTooltip(null);
                }
            }
        }

        discoverButton.setDisable(!validChecksum || !AppServices.isConnected());
        importButton.setDisable(!validChecksum);
        validLabel.setVisible(validChecksum);
        invalidLabel.setVisible(!validChecksum && !empty);
    }

    private void discoverWallet() {
        discoverButton.setDisable(true);
        discoverButton.setMaxHeight(discoverButton.getHeight());
        ProgressIndicator progressIndicator = new ProgressIndicator(0);
        progressIndicator.getStyleClass().add("button-progress");
        discoverButton.setGraphic(progressIndicator);
        List<Wallet> wallets = new ArrayList<>();

        List<List<ChildNumber>> derivations = ScriptType.getScriptTypesForPolicyType(PolicyType.SINGLE).stream().map(ScriptType::getDefaultDerivation).collect(Collectors.toList());
        derivations.add(List.of(new ChildNumber(0, true)));
        derivations.add(ScriptType.P2PKH.getDefaultDerivation(1)); //Bisq segwit misderivation

        for(ScriptType scriptType : ScriptType.getScriptTypesForPolicyType(PolicyType.SINGLE)) {
            for(List<ChildNumber> derivation : derivations) {
                try {
                    Wallet wallet = getWallet(scriptType, derivation);
                    wallets.add(wallet);
                } catch(ImportException e) {
                    String errorMessage = e.getMessage();
                    if(e.getCause() instanceof MnemonicException.MnemonicChecksumException) {
                        errorMessage = "Invalid word list - checksum incorrect";
                    } else if(e.getCause() != null && e.getCause().getMessage() != null && !e.getCause().getMessage().isEmpty()) {
                        errorMessage = e.getCause().getMessage();
                    }
                    setError("Import Error", errorMessage + ".");
                    discoverButton.setDisable(!AppServices.isConnected());
                }
            }
        }

        ElectrumServer.WalletDiscoveryService walletDiscoveryService = new ElectrumServer.WalletDiscoveryService(wallets);
        progressIndicator.progressProperty().bind(walletDiscoveryService.progressProperty());
        walletDiscoveryService.setOnSucceeded(successEvent -> {
            discoverButton.setGraphic(null);
            Optional<Wallet> optWallet = walletDiscoveryService.getValue();
            if(optWallet.isPresent()) {
                EventManager.get().post(new WalletImportEvent(optWallet.get()));
            } else {
                discoverButton.setDisable(false);
                Optional<ButtonType> optButtonType = AppServices.showErrorDialog("No existing wallet found",
                        Config.get().getServerType() == ServerType.BITCOIN_CORE ? "The configured server type is Bitcoin Core, which does not support wallet discovery.\n\n" +
                                "You can however import this wallet and scan the blockchain by supplying a start date. Do you want to import this wallet?" :
                                "Could not find a wallet with existing transactions using this mnemonic. Import this wallet anyway?", ButtonType.NO, ButtonType.YES);
                if(optButtonType.isPresent() && optButtonType.get() == ButtonType.YES) {
                    setContent(getScriptTypeEntry());
                    setExpanded(true);
                }
            }
        });
        walletDiscoveryService.setOnFailed(failedEvent -> {
            discoverButton.setGraphic(null);
            log.error("Failed to discover wallets", failedEvent.getSource().getException());
            setError("Failed to discover wallets", failedEvent.getSource().getException().getMessage());
        });
        walletDiscoveryService.start();
    }

    private Wallet getWallet(ScriptType scriptType, List<ChildNumber> derivation) throws ImportException {
        Wallet wallet = new Wallet("");
        wallet.setPolicyType(PolicyType.SINGLE);
        wallet.setScriptType(scriptType);
        Keystore keystore = importer.getKeystore(derivation, wordEntriesProperty.get(), passphraseProperty.get());
        wallet.getKeystores().add(keystore);
        wallet.setDefaultPolicy(Policy.getPolicy(PolicyType.SINGLE, scriptType, wallet.getKeystores(), 1));
        return wallet;
    }

    private Node getScriptTypeEntry() {
        Label label = new Label("Script Type:");

        HBox fieldBox = new HBox(5);
        fieldBox.setAlignment(Pos.CENTER_RIGHT);
        ComboBox<ScriptType> scriptTypeComboBox = new ComboBox<>(FXCollections.observableArrayList(ScriptType.getAddressableScriptTypes(PolicyType.SINGLE)));
        if(scriptTypeComboBox.getItems().contains(ScriptType.P2WPKH)) {
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
        helpLabel.setHelpText("Native Segwit is usually the best choice for new wallets.\nTaproot is a new type useful for specific needs.\nNested Segwit and Legacy are useful for recovering older wallets.\nFor existing wallets, be sure to choose the type that matches the wallet you are importing.");
        fieldBox.getChildren().addAll(scriptTypeComboBox, helpLabel);

        Region region = new Region();
        HBox.setHgrow(region, Priority.SOMETIMES);

        Button importMnemonicButton = new Button("Import");
        importMnemonicButton.setOnAction(event -> {
            showHideLink.setVisible(true);
            setExpanded(false);
            try {
                ScriptType scriptType = scriptTypeComboBox.getValue();
                Wallet wallet = getWallet(scriptType, scriptType.getDefaultDerivation());
                EventManager.get().post(new WalletImportEvent(wallet));
            } catch(ImportException e) {
                log.error("Error importing mnemonic", e);
                String errorMessage = e.getMessage();
                if(e.getCause() != null && e.getCause().getMessage() != null && !e.getCause().getMessage().isEmpty()) {
                    errorMessage = e.getCause().getMessage();
                }
                setError("Import Error", errorMessage);
                importButton.setDisable(false);
            }
        });

        HBox contentBox = new HBox();
        contentBox.setAlignment(Pos.CENTER_RIGHT);
        contentBox.setSpacing(20);
        contentBox.getChildren().addAll(label, fieldBox, region, importMnemonicButton);
        contentBox.setPadding(new Insets(10, 30, 10, 30));
        contentBox.setPrefHeight(60);

        return contentBox;
    }
}
