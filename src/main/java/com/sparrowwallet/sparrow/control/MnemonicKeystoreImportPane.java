package com.sparrowwallet.sparrow.control;

import com.sparrowwallet.drongo.KeyDerivation;
import com.sparrowwallet.drongo.Utils;
import com.sparrowwallet.drongo.crypto.ChildNumber;
import com.sparrowwallet.drongo.crypto.DeterministicKey;
import com.sparrowwallet.drongo.crypto.HDKeyDerivation;
import com.sparrowwallet.drongo.crypto.HDDerivationException;
import com.sparrowwallet.drongo.protocol.ScriptType;
import com.sparrowwallet.drongo.wallet.Bip85;
import com.sparrowwallet.drongo.wallet.DeterministicSeed;
import com.sparrowwallet.drongo.wallet.Keystore;
import com.sparrowwallet.drongo.wallet.MnemonicException;
import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.sparrow.AppServices;
import com.sparrowwallet.sparrow.EventManager;
import com.sparrowwallet.sparrow.event.KeystoreImportEvent;
import com.sparrowwallet.sparrow.glyphfont.FontAwesome5;
import com.sparrowwallet.sparrow.io.ImportException;
import com.sparrowwallet.sparrow.io.KeystoreMnemonicImport;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.controlsfx.tools.Borders;
import org.controlsfx.glyphfont.Glyph;
import org.controlsfx.validation.ValidationResult;
import org.controlsfx.validation.ValidationSupport;
import org.controlsfx.validation.Validator;
import org.controlsfx.validation.decoration.StyleClassValidationDecoration;

import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.text.Normalizer;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

public class MnemonicKeystoreImportPane extends MnemonicKeystorePane {
    protected final Wallet wallet;
    private final KeystoreMnemonicImport importer;
    private final KeyDerivation defaultDerivation;

    private SplitMenuButton importButton;

    private Button generateButton;
    private Button deriveBip85Button;
    private Button calculateButton;
    private Button backButton;
    private Button nextButton;
    private Button confirmButton;
    private List<String> generatedMnemonicCode;

    public MnemonicKeystoreImportPane(Wallet wallet, KeystoreMnemonicImport importer, KeyDerivation defaultDerivation) {
        super(importer.getName(), "Create or enter seed", importer.getKeystoreImportDescription(), importer.getWalletModel());
        this.wallet = wallet;
        this.importer = importer;
        this.defaultDerivation = defaultDerivation;

        createImportButton();
        buttonBox.getChildren().add(importButton);
    }

    private void createImportButton() {
        importButton = new SplitMenuButton();
        importButton.setAlignment(Pos.CENTER_RIGHT);
        importButton.setText("Import Keystore");
        setDefaultButton(importButton);
        importButton.setOnAction(event -> {
            importButton.setDisable(true);
            importKeystore(getDefaultDerivation(), false);
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

    private List<ChildNumber> getDefaultDerivation() {
        return defaultDerivation == null || defaultDerivation.getDerivation().isEmpty() ? wallet.getScriptType().getDefaultDerivation() : defaultDerivation.getDerivation();
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

        deriveBip85Button = new Button("BIP85 child mnemonic");
        deriveBip85Button.setOnAction(event -> {
            deriveBip85Child();
        });
        deriveBip85Button.managedProperty().bind(deriveBip85Button.visibleProperty());
        deriveBip85Button.setVisible(false);
        deriveBip85Button.setTooltip(new Tooltip("Derive child seed words from the entered BIP39 parent seed"));

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

        return List.of(backButton, nextButton, confirmButton, deriveBip85Button, calculateButton);
    }

    protected void onWordChange(boolean empty, boolean validWords, boolean validChecksum) {
        if(!empty && validWords) {
            try {
                importer.getKeystore(wallet.getPolicyType(), wallet.getScriptType().getDefaultDerivation(), wordEntriesProperty.get(), passphraseProperty.get());
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
        deriveBip85Button.setVisible(!empty && validChecksum && generatedMnemonicCode == null);
        calculateButton.setDisable(!validChecksum);
        validLabel.setVisible(validChecksum);
        invalidLabel.setVisible(!validChecksum && !empty);
    }

    private void deriveBip85Child() {
        String parentPassphrase = passphraseProperty.get() == null ? "" : passphraseProperty.get();
        long creationTimeMillis = System.currentTimeMillis();
        DeterministicSeed parentSeed = new DeterministicSeed(wordEntriesProperty.get(), parentPassphrase, creationTimeMillis, DeterministicSeed.Type.BIP39);
        byte[] parentSeedBytes = null;
        try {
            parentSeed.check();
            parentSeedBytes = parentSeed.getSeedBytes();
            byte[] parentMasterFingerprint = HDKeyDerivation.createMasterPrivateKey(parentSeedBytes).getFingerprint();
            Bip85ChildDialog bip85ChildDialog = new Bip85ChildDialog(wordEntriesProperty.get().size(), parentPassphrase, parentMasterFingerprint);
            bip85ChildDialog.initOwner(this.getScene().getWindow());
            Optional<Bip85Child> optChild = bip85ChildDialog.showAndWait();
            if(optChild.isEmpty()) {
                return;
            }

            DeterministicKey parentMasterKey = HDKeyDerivation.createMasterPrivateKey(parentSeedBytes);
            DeterministicSeed childSeed = Bip85.deriveBip39Child(parentMasterKey, optChild.get().words(), optChild.get().index(), creationTimeMillis);

            passphraseProperty.unbind();
            passphraseProperty.set("");
            generatedMnemonicCode = childSeed.getMnemonicCode();
            setContent(getMnemonicWordsEntry(generatedMnemonicCode.size(), true, true));
            displayMnemonicCode();
        } catch(MnemonicException | HDDerivationException e) {
            String errorMessage = e.getMessage() == null || e.getMessage().isEmpty() ? "Could not derive BIP85 child mnemonic" : e.getMessage();
            setError("BIP85 Error", errorMessage + ".");
        } finally {
            parentSeed.clear();
            if(parentSeedBytes != null) {
                Arrays.fill(parentSeedBytes, (byte)0);
            }
        }
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
        deriveBip85Button.setVisible(false);

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
        deriveBip85Button.setVisible(false);
    }

    private void confirmBackup() {
        setDescription("Confirm backup by re-entering words");
        showHideLink.setVisible(false);
        setContent(getMnemonicWordsEntry(wordEntriesProperty.get().size(), true, false));
        setExpanded(true);
        backButton.setVisible(true);
        generateButton.setVisible(false);
        deriveBip85Button.setVisible(false);
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
            setContent(getDerivationEntry(getDefaultDerivation()));
        }
    }

    private boolean importKeystore(List<ChildNumber> derivation, boolean dryrun) {
        importButton.setDisable(true);
        try {
            Keystore keystore = importer.getKeystore(wallet.getPolicyType(), derivation, wordEntriesProperty.get(), passphraseProperty.get());
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

    private record Bip85Child(int words, int index) {
    }

    private static class Bip85ChildDialog extends Dialog<Bip85Child> {
        private static final int MAX_INDEX = Integer.MAX_VALUE;
        private static final List<Integer> WORD_COUNTS = List.of(24, 21, 18, 15, 12);

        private final String parentPassphrase;
        private final byte[] parentMasterFingerprint;
        private final ComboBox<Integer> wordCount;
        private final IntegerSpinner childIndex;
        private final ViewPasswordField passphrase;
        private final boolean requiresParentPassphrase;

        public Bip85ChildDialog(int defaultWords, String parentPassphrase, byte[] parentMasterFingerprint) {
            this.parentPassphrase = parentPassphrase == null ? "" : parentPassphrase;
            this.parentMasterFingerprint = parentMasterFingerprint;
            this.passphrase = new ViewPasswordField();
            this.requiresParentPassphrase = !this.parentPassphrase.isEmpty();

            final DialogPane dialogPane = getDialogPane();
            setTitle("Derive BIP85 child mnemonic");
            dialogPane.setHeaderText(requiresParentPassphrase ? "Choose BIP85 child mnemonic details\nand confirm the parent passphrase:" : "Choose BIP85 child mnemonic details:");
            dialogPane.getStylesheets().add(AppServices.class.getResource("general.css").toExternalForm());
            AppServices.setStageIcon(dialogPane.getScene().getWindow());
            dialogPane.getButtonTypes().addAll(ButtonType.CANCEL, ButtonType.OK);
            dialogPane.setPrefWidth(380);
            dialogPane.setPrefHeight(requiresParentPassphrase ? 340 : 290);
            AppServices.moveToActiveWindowScreen(this);

            Glyph key = new Glyph(FontAwesome5.FONT_NAME, FontAwesome5.Glyph.KEY);
            key.setFontSize(50);
            dialogPane.setGraphic(key);

            GridPane gridPane = new GridPane();
            gridPane.setHgap(10);
            gridPane.setVgap(10);

            wordCount = new ComboBox<>();
            wordCount.getItems().addAll(WORD_COUNTS);
            wordCount.getSelectionModel().select(WORD_COUNTS.contains(defaultWords) ? Integer.valueOf(defaultWords) : Integer.valueOf(24));
            wordCount.setMaxWidth(Double.MAX_VALUE);
            GridPane.setHgrow(wordCount, Priority.ALWAYS);

            childIndex = new IntegerSpinner();
            childIndex.setValueFactory(new IntegerSpinner.ValueFactory(0, MAX_INDEX, 0));
            childIndex.setEditable(true);
            childIndex.getEditor().setTextFormatter(new TextFormatter<>((TextFormatter.Change change) -> {
                String newText = change.getControlNewText();
                if(!newText.matches("\\d*")) {
                    return null;
                }
                if(newText.isEmpty()) {
                    return change;
                }

                try {
                    return Long.parseLong(newText) <= MAX_INDEX ? change : null;
                } catch(NumberFormatException e) {
                    return null;
                }
            }));
            childIndex.getEditor().textProperty().addListener((observable, oldValue, newValue) -> {
                if(newValue != null && !newValue.isEmpty()) {
                    childIndex.getValueFactory().setValue(Integer.parseInt(newValue));
                }
            });
            childIndex.setMaxWidth(Double.MAX_VALUE);
            GridPane.setHgrow(childIndex, Priority.ALWAYS);

            TextField derivationPath = new TextField();
            derivationPath.setEditable(false);
            derivationPath.setFocusTraversable(false);
            derivationPath.setMaxWidth(Double.MAX_VALUE);
            derivationPath.textProperty().bind(Bindings.createStringBinding(() ->
                    "m/83696968'/39'/0'/" + wordCount.getValue() + "'/" +
                            (childIndex.getEditor().getText().isEmpty() ? "" : childIndex.getValue()) + "'",
                    wordCount.valueProperty(), childIndex.valueProperty(), childIndex.getEditor().textProperty()));
            GridPane.setHgrow(derivationPath, Priority.ALWAYS);

            gridPane.add(new Label("Number of words"), 0, 0);
            gridPane.add(wordCount, 1, 0);
            gridPane.add(new Label("Child index"), 0, 1);
            gridPane.add(childIndex, 1, 1);
            gridPane.add(new Label("Derivation path"), 0, 2);
            gridPane.add(derivationPath, 1, 2);

            int nextRow = 3;
            if(requiresParentPassphrase) {
                passphrase.setMaxWidth(Double.MAX_VALUE);
                GridPane.setHgrow(passphrase, Priority.ALWAYS);
                gridPane.add(new Label("Parent passphrase"), 0, nextRow);
                gridPane.add(passphrase, 1, nextRow++);

                ValidationSupport validationSupport = new ValidationSupport();
                validationSupport.setValidationDecorator(new StyleClassValidationDecoration());
                validationSupport.registerValidator(passphrase, (Control c, String newValue) ->
                        ValidationResult.fromErrorIf(c, "Passphrase does not match", !isParentPassphraseConfirmed()));
            }

            HBox fingerprintBox = new HBox(10);
            fingerprintBox.setAlignment(Pos.CENTER_LEFT);
            TextField fingerprintHex = new TextField();
            fingerprintHex.setDisable(true);
            fingerprintHex.setMaxWidth(80);
            fingerprintHex.getStyleClass().addAll("fixed-width");
            fingerprintHex.setStyle("-fx-opacity: 0.6");
            fingerprintHex.setText(Utils.bytesToHex(parentMasterFingerprint));
            LifeHashIcon lifeHashIcon = new LifeHashIcon();
            lifeHashIcon.setData(parentMasterFingerprint);
            HelpLabel helpLabel = new HelpLabel();
            helpLabel.setHelpText("The parent master fingerprint identifies the parent seed and passphrase used for child seed derivation." +
                    "\nTake a moment to identify it before deriving the BIP85 child mnemonic.");
            fingerprintBox.getChildren().addAll(fingerprintHex, lifeHashIcon, helpLabel);
            gridPane.add(new Label("Parent fingerprint"), 0, nextRow);
            gridPane.add(fingerprintBox, 1, nextRow++);

            if(requiresParentPassphrase) {
                Glyph warnGlyph = new Glyph(FontAwesome5.FONT_NAME, FontAwesome5.Glyph.EXCLAMATION_TRIANGLE);
                warnGlyph.getStyleClass().add("warn-icon");
                warnGlyph.setFontSize(12);
                Label warnLabel = new Label("Note the parent fingerprint before proceeding!", warnGlyph);
                warnLabel.setGraphicTextGap(5);
                GridPane.setColumnSpan(warnLabel, 2);
                gridPane.add(warnLabel, 0, nextRow);
            }

            VBox content = new VBox(10);
            content.setPrefHeight(requiresParentPassphrase ? 230 : 180);
            content.getChildren().add(gridPane);
            dialogPane.setContent(content);

            Button okButton = (Button)dialogPane.lookupButton(ButtonType.OK);
            okButton.disableProperty().bind(Bindings.createBooleanBinding(
                    () -> childIndex.getEditor().getText().isEmpty() || !isParentPassphraseConfirmed(),
                    childIndex.getEditor().textProperty(), passphrase.textProperty()));

            Platform.runLater(childIndex::requestFocus);

            setResultConverter(dialogButton -> {
                if(dialogButton != ButtonType.OK) {
                    return null;
                }

                if(childIndex.getEditor().getText().isEmpty() || !isParentPassphraseConfirmed()) {
                    return null;
                }

                childIndex.commitValue();
                return new Bip85Child(wordCount.getValue(), childIndex.getValue());
            });
        }

        private boolean isParentPassphraseConfirmed() {
            if (!requiresParentPassphrase) {
                return true;
            }
            return Normalizer.normalize(parentPassphrase, Normalizer.Form.NFKD)
                    .equals(Normalizer.normalize(passphrase.getText(), Normalizer.Form.NFKD));
        }

    }
}
