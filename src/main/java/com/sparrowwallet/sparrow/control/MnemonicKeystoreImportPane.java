package com.sparrowwallet.sparrow.control;

import com.sparrowwallet.drongo.KeyDerivation;
import com.sparrowwallet.drongo.crypto.ChildNumber;
import com.sparrowwallet.drongo.protocol.ScriptType;
import com.sparrowwallet.drongo.wallet.DeterministicSeed;
import com.sparrowwallet.drongo.wallet.Keystore;
import com.sparrowwallet.drongo.wallet.MnemonicException;
import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.sparrow.EventManager;
import com.sparrowwallet.sparrow.event.KeystoreImportEvent;
import com.sparrowwallet.sparrow.io.ImportException;
import com.sparrowwallet.sparrow.io.KeystoreMnemonicImport;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import org.controlsfx.tools.Borders;
import org.controlsfx.validation.ValidationResult;
import org.controlsfx.validation.ValidationSupport;
import org.controlsfx.validation.Validator;
import org.controlsfx.validation.decoration.StyleClassValidationDecoration;

import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.List;
import java.util.Optional;

public class MnemonicKeystoreImportPane extends MnemonicKeystorePane {
    protected final Wallet wallet;
    private final KeystoreMnemonicImport importer;

    private SplitMenuButton importButton;

    private Button generateButton;
    private Button calculateButton;
    private Button backButton;
    private Button nextButton;
    private Button confirmButton;
    private List<String> generatedMnemonicCode;

    public MnemonicKeystoreImportPane(Wallet wallet, KeystoreMnemonicImport importer) {
        super(importer.getName(), "Create or enter seed", importer.getKeystoreImportDescription(), "image/" + importer.getWalletModel().getType() + ".png");
        this.wallet = wallet;
        this.importer = importer;

        createImportButton();
        buttonBox.getChildren().add(importButton);
    }

    private void createImportButton() {
        importButton = new SplitMenuButton();
        importButton.setAlignment(Pos.CENTER_RIGHT);
        importButton.setText("Import Keystore");
        importButton.getStyleClass().add("default-button");
        importButton.setOnAction(event -> {
            importButton.setDisable(true);
            importKeystore(wallet.getScriptType().getDefaultDerivation(), false);
        });
        String[] accounts = new String[] {"Import Default Account #0", "Import Account #1", "Import Account #2", "Import Account #3", "Import Account #4", "Import Account #5", "Import Account #6", "Import Account #7", "Import Account #8", "Import Account #9"};
        int scriptAccountsLength = ScriptType.P2SH.equals(wallet.getScriptType()) ? 1 : accounts.length;
        for(int i = 0; i < scriptAccountsLength; i++) {
            MenuItem item = new MenuItem(accounts[i]);
            final List<ChildNumber> derivation = wallet.getScriptType().getDefaultDerivation(i);
            item.setOnAction(event -> {
                importButton.setDisable(true);
                importKeystore(derivation, false);
            });
            importButton.getItems().add(item);
        }

        importButton.managedProperty().bind(importButton.visibleProperty());
        importButton.setVisible(false);
    }

    protected void enterMnemonic(int numWords) {
        generatedMnemonicCode = null;
        super.enterMnemonic(numWords);
    }

    protected List<Node> createLeftButtons() {
        generateButton = new Button("Generate New");
        generateButton.setOnAction(event -> {
            generateNew();
        });
        generateButton.managedProperty().bind(generateButton.visibleProperty());
        generateButton.setTooltip(new Tooltip("Generate a unique set of words that provide the seed for your wallet"));

        return List.of(generateButton);
    }

    protected List<Node> createRightButtons() {
        confirmButton = new Button("Re-enter Words...");
        confirmButton.setOnAction(event -> {
            confirmBackup();
        });
        confirmButton.managedProperty().bind(confirmButton.visibleProperty());
        confirmButton.setVisible(false);
        confirmButton.setDefaultButton(true);
        confirmButton.setTooltip(new Tooltip("Re-enter the generated word list to confirm your backup is correct"));

        calculateButton = new Button("Create Keystore");
        calculateButton.setDisable(true);
        calculateButton.setDefaultButton(true);
        calculateButton.setOnAction(event -> {
            prepareImport();
        });
        calculateButton.managedProperty().bind(calculateButton.visibleProperty());
        calculateButton.setTooltip(new Tooltip("Create the keystore from the provided word list"));

        backButton = new Button("Back");
        backButton.setOnAction(event -> {
            displayMnemonicCode();
        });
        backButton.managedProperty().bind(backButton.visibleProperty());
        backButton.setTooltip(new Tooltip("Go back to the generated word list"));
        backButton.setVisible(false);

        nextButton = new Button("Confirm Backup...");
        nextButton.setOnAction(event -> {
            confirmRecord();
        });
        nextButton.managedProperty().bind(nextButton.visibleProperty());
        nextButton.setTooltip(new Tooltip("Confirm you have recorded the generated word list"));
        nextButton.setVisible(false);
        nextButton.setDefaultButton(true);

        return List.of(backButton, nextButton, confirmButton, calculateButton);
    }

    protected void onWordChange(boolean empty, boolean validWords, boolean validChecksum) {
        if(!empty && validWords) {
            try {
                importer.getKeystore(wallet.getScriptType().getDefaultDerivation(), wordEntriesProperty.get(), passphraseProperty.get());
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

        generateButton.setVisible(empty && generatedMnemonicCode == null);
        calculateButton.setDisable(!validChecksum);
        validLabel.setVisible(validChecksum);
        invalidLabel.setVisible(!validChecksum && !empty);
    }

    private void generateNew() {
        int mnemonicSeedLength = wordEntriesProperty.get().size() * 11;
        int entropyLength = mnemonicSeedLength - (mnemonicSeedLength/33);

        SecureRandom secureRandom;
        try {
            secureRandom = SecureRandom.getInstanceStrong();
        } catch(NoSuchAlgorithmException e) {
            secureRandom = new SecureRandom();
        }

        DeterministicSeed deterministicSeed = new DeterministicSeed(secureRandom, entropyLength, "");
        generatedMnemonicCode = deterministicSeed.getMnemonicCode();

        displayMnemonicCode();
    }

    private void displayMnemonicCode() {
        setDescription("Write down words before re-entering");
        showHideLink.setVisible(false);

        calculateButton.setVisible(false);
        nextButton.setVisible(true);
        backButton.setVisible(false);

        if(generatedMnemonicCode.size() != wordsPane.getChildren().size()) {
            throw new IllegalStateException("Generated mnemonic words list not same size as displayed words list");
        }

        for (int i = 0; i < wordsPane.getChildren().size(); i++) {
            WordEntry wordEntry = (WordEntry)wordsPane.getChildren().get(i);
            wordEntry.getEditor().setText(generatedMnemonicCode.get(i));
            wordEntry.getEditor().setEditable(false);
        }

        StackPane wordsStackPane = (StackPane)getContent();
        if(wordsStackPane.getChildren().size() > 1) {
            wordsStackPane.getChildren().remove(1);
            confirmButton.setVisible(false);
        }
    }

    private void confirmRecord() {
        setDescription("Confirm words have been recorded");
        showHideLink.setVisible(false);

        StackPane wordsPane = (StackPane)getContent();
        StackPane confirmPane = new StackPane();
        confirmPane.setMaxWidth(350);
        confirmPane.setMaxHeight(100);
        Region region = new Region();
        region.setMinWidth(confirmPane.getMaxWidth());
        region.setMinHeight(confirmPane.getMaxHeight());
        confirmPane.getStyleClass().add("box-overlay");
        Node wrappedRegion = Borders.wrap(region).lineBorder().innerPadding(0).outerPadding(0).buildAll();
        Label label = new Label("Have these " + wordEntriesProperty.get().size() + " words been written down?\nIn the next step, you will need to re-enter them.");
        confirmPane.getChildren().addAll(wrappedRegion, label);
        wordsPane.getChildren().add(confirmPane);

        setExpanded(true);
        backButton.setVisible(true);
        nextButton.setVisible(false);
        confirmButton.setVisible(true);
        generateButton.setVisible(false);
    }

    private void confirmBackup() {
        setDescription("Confirm backup by re-entering words");
        showHideLink.setVisible(false);
        setContent(getMnemonicWordsEntry(wordEntriesProperty.get().size(), true, false));
        setExpanded(true);
        backButton.setVisible(true);
        generateButton.setVisible(false);
    }

    private void prepareImport() {
        if(generatedMnemonicCode != null && !generatedMnemonicCode.equals(wordEntriesProperty.get())) {
            setError("Import Error", "Confirmation words did not match generated mnemonic");
            return;
        }

        if(importKeystore(wallet.getScriptType().getDefaultDerivation(), true)) {
            setExpanded(true);
            enterMnemonicButton.setVisible(false);
            importButton.setVisible(true);
            importButton.setDisable(false);
            setDescription("Ready to import");
            showHideLink.setText("Show Derivation...");
            showHideLink.setVisible(false);
            setContent(getDerivationEntry(wallet.getScriptType().getDefaultDerivation()));
        }
    }

    private boolean importKeystore(List<ChildNumber> derivation, boolean dryrun) {
        importButton.setDisable(true);
        try {
            Keystore keystore = importer.getKeystore(derivation, wordEntriesProperty.get(), passphraseProperty.get());
            if(!dryrun) {
                if(passphraseProperty.get() != null && !passphraseProperty.get().isEmpty()) {
                    KeystorePassphraseDialog keystorePassphraseDialog = new KeystorePassphraseDialog(null, keystore, true);
                    keystorePassphraseDialog.initOwner(this.getScene().getWindow());
                    Optional<String> optPassphrase = keystorePassphraseDialog.showAndWait();
                    if(optPassphrase.isEmpty() || !optPassphrase.get().equals(passphraseProperty.get())) {
                        throw new ImportException("Re-entered passphrase did not match");
                    }
                }

                EventManager.get().post(new KeystoreImportEvent(keystore));
            }
            return true;
        } catch (ImportException e) {
            String errorMessage = e.getMessage();
            if(e.getCause() instanceof MnemonicException.MnemonicChecksumException) {
                errorMessage = "Invalid word list - checksum incorrect";
            } else if(e.getCause() != null && e.getCause().getMessage() != null && !e.getCause().getMessage().isEmpty()) {
                errorMessage = e.getCause().getMessage();
            }
            setError("Import Error", errorMessage + ".");
            importButton.setDisable(false);
            return false;
        }
    }

    private Node getDerivationEntry(List<ChildNumber> derivation) {
        TextField derivationField = new TextField();
        derivationField.setPromptText("Derivation path");
        derivationField.setText(KeyDerivation.writePath(derivation));
        HBox.setHgrow(derivationField, Priority.ALWAYS);

        ValidationSupport validationSupport = new ValidationSupport();
        validationSupport.setValidationDecorator(new StyleClassValidationDecoration());
        validationSupport.registerValidator(derivationField, Validator.combine(
                Validator.createEmptyValidator("Derivation is required"),
                (Control c, String newValue) -> ValidationResult.fromErrorIf( c, "Invalid derivation", !KeyDerivation.isValid(newValue))
        ));

        Button importDerivationButton = new Button("Import Custom Derivation Keystore");
        importDerivationButton.setDisable(true);
        importDerivationButton.setOnAction(event -> {
            showHideLink.setVisible(true);
            setExpanded(false);
            List<ChildNumber> importDerivation = KeyDerivation.parsePath(derivationField.getText());
            importKeystore(importDerivation, false);
        });

        derivationField.textProperty().addListener((observable, oldValue, newValue) -> {
            importButton.setDisable(newValue.isEmpty() || !KeyDerivation.isValid(newValue) || !KeyDerivation.parsePath(newValue).equals(derivation));
            importDerivationButton.setDisable(newValue.isEmpty() || !KeyDerivation.isValid(newValue) || KeyDerivation.parsePath(newValue).equals(derivation));
        });

        HBox contentBox = new HBox();
        contentBox.setAlignment(Pos.TOP_RIGHT);
        contentBox.setSpacing(20);
        contentBox.getChildren().add(derivationField);
        contentBox.getChildren().add(importDerivationButton);
        contentBox.setPadding(new Insets(10, 30, 10, 30));
        contentBox.setPrefHeight(60);

        return contentBox;
    }
}
