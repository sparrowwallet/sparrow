package com.sparrowwallet.sparrow.control;

import com.sparrowwallet.drongo.KeyDerivation;
import com.sparrowwallet.drongo.crypto.ChildNumber;
import com.sparrowwallet.drongo.wallet.*;
import com.sparrowwallet.sparrow.EventManager;
import com.sparrowwallet.sparrow.event.KeystoreImportEvent;
import com.sparrowwallet.sparrow.io.*;
import javafx.beans.property.SimpleListProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.util.Callback;
import org.controlsfx.control.textfield.AutoCompletionBinding;
import org.controlsfx.control.textfield.CustomTextField;
import org.controlsfx.control.textfield.TextFields;
import org.controlsfx.validation.ValidationResult;
import org.controlsfx.validation.ValidationSupport;
import org.controlsfx.validation.Validator;
import org.controlsfx.validation.decoration.StyleClassValidationDecoration;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class MnemonicKeystoreImportPane extends TitledDescriptionPane {
    protected final Wallet wallet;
    private final KeystoreMnemonicImport importer;

    private SplitMenuButton enterMnemonicButton;
    private SplitMenuButton importButton;

    private TilePane wordsPane;
    private Button verifyButton;
    private Button backButton;
    private Button confirmButton;
    private List<String> generatedMnemonicCode;

    private SimpleListProperty<String> wordEntriesProperty;
    private final SimpleStringProperty passphraseProperty = new SimpleStringProperty();

    public MnemonicKeystoreImportPane(Wallet wallet, KeystoreMnemonicImport importer) {
        super(importer.getName(), "Mnemonic import", importer.getKeystoreImportDescription(), "image/" + importer.getWalletModel().getType() + ".png");
        this.wallet = wallet;
        this.importer = importer;

        createImportButton();
        buttonBox.getChildren().add(importButton);
    }

    public MnemonicKeystoreImportPane(Keystore keystore) {
        super(keystore.getSeed().getType().getName(), keystore.getSeed().needsPassphrase() ? "Passphrase enabled" : "Passphrase disabled", "", "image/" + WalletModel.SEED + ".png");
        this.wallet = null;
        this.importer = null;
        showHideLink.setVisible(false);
        buttonBox.getChildren().clear();

        showWordList(keystore.getSeed());
    }

    @Override
    protected Control createButton() {
        createEnterMnemonicButton();
        return enterMnemonicButton;
    }

    private void createEnterMnemonicButton() {
        enterMnemonicButton = new SplitMenuButton();
        enterMnemonicButton.setAlignment(Pos.CENTER_RIGHT);
        enterMnemonicButton.setText("Enter Mnemonic");
        enterMnemonicButton.setOnAction(event -> {
            enterMnemonic(24);
        });
        int[] numberWords = new int[] {24, 21, 18, 15, 12};
        for(int i = 0; i < numberWords.length; i++) {
            MenuItem item = new MenuItem(numberWords[i] + " words");
            final int words = numberWords[i];
            item.setOnAction(event -> {
                enterMnemonic(words);
            });
            enterMnemonicButton.getItems().add(item);
        }
        enterMnemonicButton.managedProperty().bind(enterMnemonicButton.visibleProperty());
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
        String[] accounts = new String[] {"Default Account #0", "Account #1", "Account #2", "Account #3", "Account #4", "Account #5", "Account #6", "Account #7", "Account #8", "Account #9"};
        for(int i = 0; i < accounts.length; i++) {
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

    private void enterMnemonic(int numWords) {
        generatedMnemonicCode = null;
        setDescription("Enter mnemonic word list");
        showHideLink.setVisible(false);
        setContent(getMnemonicWordsEntry(numWords, false));
        setExpanded(true);
    }

    private Node getMnemonicWordsEntry(int numWords, boolean displayWordsOnly) {
        VBox vBox = new VBox();
        vBox.setSpacing(10);

        wordsPane = new TilePane();
        wordsPane.setPrefRows(numWords/3);
        wordsPane.setHgap(10);
        wordsPane.setVgap(10);
        wordsPane.setOrientation(Orientation.VERTICAL);

        List<String> words = new ArrayList<>();
        for(int i = 0; i < numWords; i++) {
            words.add("");
        }

        ObservableList<String> wordEntryList = FXCollections.observableArrayList(words);
        wordEntriesProperty = new SimpleListProperty<>(wordEntryList);
        for(int i = 0; i < numWords; i++) {
            WordEntry wordEntry = new WordEntry(i, wordEntryList);
            wordsPane.getChildren().add(wordEntry);
        }

        vBox.getChildren().add(wordsPane);

        if(!displayWordsOnly) {
            PassphraseEntry passphraseEntry = new PassphraseEntry();
            passphraseEntry.setPadding(new Insets(0, 32, 10, 10));
            vBox.getChildren().add(passphraseEntry);

            AnchorPane buttonPane = new AnchorPane();
            buttonPane.setPadding(new Insets(0, 32, 0, 10));

            Button generateButton = new Button("Generate New");
            generateButton.setOnAction(event -> {
                generateNew();
            });
            generateButton.setTooltip(new Tooltip("Generate a unique set of words that provide the seed for your wallet"));
            buttonPane.getChildren().add(generateButton);
            AnchorPane.setLeftAnchor(generateButton, 0.0);

            confirmButton = new Button("Confirm Written Backup");
            confirmButton.setOnAction(event -> {
                confirmBackup();
            });
            confirmButton.managedProperty().bind(confirmButton.visibleProperty());
            confirmButton.setVisible(false);
            confirmButton.setDefaultButton(true);
            confirmButton.setTooltip(new Tooltip("Write down the words above as a backup - you will need to re-enter them to confirm your backup is correct"));
            buttonPane.getChildren().add(confirmButton);
            AnchorPane.setRightAnchor(confirmButton, 0.0);

            verifyButton = new Button("Verify");
            verifyButton.setDisable(true);
            verifyButton.setDefaultButton(true);
            verifyButton.setOnAction(event -> {
                prepareImport();
            });
            verifyButton.managedProperty().bind(verifyButton.visibleProperty());
            verifyButton.setTooltip(new Tooltip("Enter the words you have just written down to confirm the backup is correct"));

            backButton = new Button("Back");
            backButton.setOnAction(event -> {
                displayMnemonicCode();
            });
            backButton.managedProperty().bind(backButton.visibleProperty());
            backButton.setTooltip(new Tooltip("Go back to the generated word list"));
            backButton.setVisible(false);

            wordEntriesProperty.addListener((ListChangeListener<String>) c -> {
                for(String word : wordEntryList) {
                    if(!WordEntry.isValid(word)) {
                        verifyButton.setDisable(true);
                        return;
                    }
                }

                verifyButton.setDisable(false);
            });

            HBox rightBox = new HBox();
            rightBox.setSpacing(10);
            rightBox.getChildren().addAll(backButton, verifyButton);

            buttonPane.getChildren().add(rightBox);
            AnchorPane.setRightAnchor(rightBox, 0.0);

            vBox.getChildren().add(buttonPane);
        }

        return vBox;
    }

    private void generateNew() {
        int mnemonicSeedLength = wordEntriesProperty.get().size() * 11;
        int entropyLength = mnemonicSeedLength - (mnemonicSeedLength/33);

        DeterministicSeed deterministicSeed = new DeterministicSeed(new SecureRandom(), entropyLength, "");
        generatedMnemonicCode = deterministicSeed.getMnemonicCode();

        displayMnemonicCode();
    }

    private void displayMnemonicCode() {
        setDescription("Write down word list to confirm backup");
        showHideLink.setVisible(false);

        if(generatedMnemonicCode.size() != wordsPane.getChildren().size()) {
            throw new IllegalStateException("Generated mnemonic words list not same size as displayed words list");
        }

        for (int i = 0; i < wordsPane.getChildren().size(); i++) {
            WordEntry wordEntry = (WordEntry)wordsPane.getChildren().get(i);
            wordEntry.getEditor().setText(generatedMnemonicCode.get(i));
            wordEntry.getEditor().setEditable(false);
        }

        verifyButton.setVisible(false);
        confirmButton.setVisible(true);
        backButton.setVisible(false);
    }

    private void confirmBackup() {
        setDescription("Confirm backup by re-entering words");
        showHideLink.setVisible(false);
        setContent(getMnemonicWordsEntry(wordEntriesProperty.get().size(), false));
        setExpanded(true);
        backButton.setVisible(true);
    }

    private void prepareImport() {
        if(generatedMnemonicCode != null && !generatedMnemonicCode.equals(wordEntriesProperty.get())) {
            setError("Import Error", "Confirmation words did not match generated mnemonic");
            return;
        }

        if(importKeystore(wallet.getScriptType().getDefaultDerivation(), true)) {
            setExpanded(false);
            enterMnemonicButton.setVisible(false);
            importButton.setVisible(true);
            importButton.setDisable(false);
            setDescription("Ready to import");
            showHideLink.setText("Show Derivation...");
            showHideLink.setVisible(true);
            setContent(getDerivationEntry(wallet.getScriptType().getDefaultDerivation()));
        }
    }

    private boolean importKeystore(List<ChildNumber> derivation, boolean dryrun) {
        importButton.setDisable(true);
        try {
            Keystore keystore = importer.getKeystore(derivation, wordEntriesProperty.get(), passphraseProperty.get());
            if(!dryrun) {
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
            setError("Import Error", errorMessage);
            importButton.setDisable(false);
            return false;
        }
    }

    private static class WordEntry extends HBox {
        private static List<String> wordList;
        private final TextField wordField;

        public WordEntry(int wordNumber, ObservableList<String> wordEntryList) {
            super();
            setAlignment(Pos.CENTER_RIGHT);

            setSpacing(10);
            Label label = new Label((wordNumber+1) + ".");
            label.setPrefWidth(20);
            label.setAlignment(Pos.CENTER_RIGHT);
            wordField = new TextField();
            wordField.setMaxWidth(100);

            wordList = Bip39MnemonicCode.INSTANCE.getWordList();
            TextFields.bindAutoCompletion(wordField, new WordlistSuggestionProvider(wordList));

            ValidationSupport validationSupport = new ValidationSupport();
            validationSupport.registerValidator(wordField, Validator.combine(
                    Validator.createEmptyValidator("Word is required"),
                    (Control c, String newValue) -> ValidationResult.fromErrorIf( c, "Invalid word", !wordList.contains(newValue))
            ));
            validationSupport.setValidationDecorator(new StyleClassValidationDecoration());

            wordField.textProperty().addListener((observable, oldValue, newValue) -> {
                wordEntryList.set(wordNumber, newValue);
            });

            this.getChildren().addAll(label, wordField);
        }

        public TextField getEditor() {
            return wordField;
        }

        public static boolean isValid(String word) {
            return wordList.contains(word);
        }
    }

    private static class WordlistSuggestionProvider implements Callback<AutoCompletionBinding.ISuggestionRequest, Collection<String>> {
        private final List<String> wordList;

        public WordlistSuggestionProvider(List<String> wordList) {
            this.wordList = wordList;
        }

        @Override
        public Collection<String> call(AutoCompletionBinding.ISuggestionRequest request) {
            List<String> suggestions = new ArrayList<>();
            if(!request.getUserText().isEmpty()) {
                for(String word : wordList) {
                    if(word.startsWith(request.getUserText())) {
                        suggestions.add(word);
                    }
                }
            }

            return suggestions;
        }
    }

    private class PassphraseEntry extends HBox {
        public PassphraseEntry() {
            super();

            setAlignment(Pos.CENTER_LEFT);
            setSpacing(10);
            Label passphraseLabel = new Label("Passphrase:");
            CustomTextField passphraseField = (CustomTextField) TextFields.createClearableTextField();
            passphraseProperty.bind(passphraseField.textProperty());
            passphraseField.setPromptText("Leave blank for none");

            HelpLabel helpLabel = new HelpLabel();
            helpLabel.setStyle("-fx-padding: 0 0 0 0");
            helpLabel.setHelpText("A passphrase provides optional added security - it is not stored so it must be remembered!");

            getChildren().addAll(passphraseLabel, passphraseField, helpLabel);
        }
    }

    private Node getDerivationEntry(List<ChildNumber> derivation) {
        TextField derivationField = new TextField();
        derivationField.setPromptText("Derivation path");
        derivationField.setText(KeyDerivation.writePath(derivation));
        HBox.setHgrow(derivationField, Priority.ALWAYS);

        ValidationSupport validationSupport = new ValidationSupport();
        validationSupport.registerValidator(derivationField, Validator.combine(
                Validator.createEmptyValidator("Derivation is required"),
                (Control c, String newValue) -> ValidationResult.fromErrorIf( c, "Invalid derivation", !KeyDerivation.isValid(newValue))
        ));
        validationSupport.setValidationDecorator(new StyleClassValidationDecoration());

        Button importDerivationButton = new Button("Import");
        importDerivationButton.setOnAction(event -> {
            showHideLink.setVisible(true);
            setExpanded(false);
            List<ChildNumber> importDerivation = KeyDerivation.parsePath(derivationField.getText());
            importKeystore(importDerivation, false);
        });

        derivationField.textProperty().addListener((observable, oldValue, newValue) -> {
            importDerivationButton.setDisable(newValue.isEmpty() || !KeyDerivation.isValid(newValue));
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

    private void showWordList(DeterministicSeed seed) {
        List<String> words = seed.getMnemonicCode();
        setContent(getMnemonicWordsEntry(words.size(), true));
        setExpanded(true);

        for (int i = 0; i < wordsPane.getChildren().size(); i++) {
            WordEntry wordEntry = (WordEntry)wordsPane.getChildren().get(i);
            wordEntry.getEditor().setText(words.get(i));
            wordEntry.getEditor().setEditable(false);
        }
    }
}
