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
        super(importer.getName(), "Seed import", importer.getKeystoreImportDescription(), importer.getWalletModel());
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
        discoverButton.setDefaultButton(AppServices.onlineProperty().get());
        discoverButton.managedProperty().bind(discoverButton.visibleProperty());
        discoverButton.setOnAction(event -> {
            discoverWallet();
        });
        discoverButton.managedProperty().bind(discoverButton.visibleProperty());
        discoverButton.setTooltip(new Tooltip("Look for existing transactions from the provided word list"));
        discoverButton.visibleProperty().bind(AppServices.onlineProperty());

        importButton = new Button("Import Wallet");
        importButton.setDisable(true);
        importButton.setDefaultButton(!AppServices.onlineProperty().get());
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
                importer.getKeystore(PolicyType.SINGLE_HD, ScriptType.P2WPKH.getDefaultDerivation(), wordEntriesProperty.get(), passphraseProperty.get());
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

        List<List<ChildNumber>> derivations = ScriptType.getScriptTypesForPolicyType(PolicyType.SINGLE_HD).stream().map(ScriptType::getDefaultDerivation).collect(Collectors.toList());
        derivations.add(List.of(new ChildNumber(0, true)));
        derivations.add(ScriptType.P2PKH.getDefaultDerivation(1)); //Bisq segwit misderivation

        for(ScriptType scriptType : ScriptType.getScriptTypesForPolicyType(PolicyType.SINGLE_HD)) {
            for(List<ChildNumber> derivation : derivations) {
                try {
                    Wallet wallet = getWallet(PolicyType.SINGLE_HD, scriptType, derivation);
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
            Optional<List<Wallet>> optWallets = walletDiscoveryService.getValue();
            if(optWallets.isPresent()) {
                List<Wallet> discoveredWallets = optWallets.get();
                if(discoveredWallets.size() > 1) {
                    for(Wallet wallet : discoveredWallets) {
                        wallet.setName(wallet.getKeystores().getFirst().getLabel() + " " + wallet.getScriptType().getDescription());
                    }
                }
                EventManager.get().post(new WalletImportEvent(discoveredWallets));
            } else {
                discoverButton.setDisable(false);
                Optional<ButtonType> optButtonType = AppServices.showErrorDialog("No existing wallet found",
                        Config.get().getServerType() == ServerType.BITCOIN_CORE ? "The configured server type is Bitcoin Core, which does not support wallet discovery.\n\n" +
                                "You can however import this wallet and scan the blockchain by supplying a start date. Do you want to import this wallet?" :
                                "Could not find an HD wallet with existing transactions using this mnemonic. Import this wallet anyway?", ButtonType.NO, ButtonType.YES);
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

    private Wallet getWallet(PolicyType policyType, ScriptType scriptType, List<ChildNumber> derivation) throws ImportException {
        Wallet wallet = new Wallet("");
        wallet.setPolicyType(policyType);
        wallet.setScriptType(scriptType);
        Keystore keystore = importer.getKeystore(policyType, derivation, wordEntriesProperty.get(), passphraseProperty.get());
        wallet.getKeystores().add(keystore);
        wallet.setDefaultPolicy(Policy.getPolicy(policyType, scriptType, wallet.getKeystores(), 1));
        return wallet;
    }

    private Node getScriptTypeEntry() {
        Label label = new Label("Type:");

        List<PolicyAndScriptType> types = new ArrayList<>();
        for(PolicyType policyType : List.of(PolicyType.SINGLE_HD, PolicyType.SINGLE_SP)) {
            for(ScriptType scriptType : ScriptType.getAddressableScriptTypes(policyType)) {
                types.add(new PolicyAndScriptType(policyType, scriptType));
            }
        }

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
        helpLabel.setHelpText("Native Segwit is usually the best choice for new wallets.\nTaproot is a new type useful for specific needs.\nTaproot Silent Payments creates a silent payment wallet.\nNested Segwit and Legacy are useful for recovering older wallets.\nFor existing wallets, be sure to choose the type that matches the wallet you are importing.");
        fieldBox.getChildren().addAll(comboBox, helpLabel);

        Region region = new Region();
        HBox.setHgrow(region, Priority.SOMETIMES);

        Button importMnemonicButton = new Button("Import");
        importMnemonicButton.setDefaultButton(true);
        importMnemonicButton.setOnAction(event -> {
            showHideLink.setVisible(true);
            setExpanded(false);
            try {
                PolicyAndScriptType type = comboBox.getValue();
                Wallet wallet = getWallet(type.policyType(), type.scriptType(), type.scriptType().getDefaultDerivation());
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

    protected record PolicyAndScriptType(PolicyType policyType, ScriptType scriptType) {
        public String getDescription() {
            return scriptType.getDescription() + (policyType == PolicyType.SINGLE_SP ? " SP" : " HD");
        }
    }
}
