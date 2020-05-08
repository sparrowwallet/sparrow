package com.sparrowwallet.sparrow.control;

import com.sparrowwallet.drongo.KeyDerivation;
import com.sparrowwallet.drongo.crypto.ChildNumber;
import com.sparrowwallet.drongo.wallet.Bip39Calculator;
import com.sparrowwallet.drongo.wallet.Keystore;
import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.sparrow.EventManager;
import com.sparrowwallet.sparrow.event.KeystoreImportEvent;
import com.sparrowwallet.sparrow.io.*;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleListProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
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
import org.controlsfx.control.textfield.CustomPasswordField;
import org.controlsfx.control.textfield.CustomTextField;
import org.controlsfx.control.textfield.TextFields;
import org.controlsfx.validation.ValidationResult;
import org.controlsfx.validation.ValidationSupport;
import org.controlsfx.validation.Validator;
import org.controlsfx.validation.decoration.StyleClassValidationDecoration;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class MnemonicKeystoreImportPane extends KeystoreImportPane {
    private final KeystoreMnemonicImport importer;

    private SplitMenuButton enterMnemonicButton;
    private SplitMenuButton importButton;

    private SimpleListProperty<String> wordEntriesProperty;
    private final SimpleStringProperty passphraseProperty = new SimpleStringProperty();

    public MnemonicKeystoreImportPane(KeystoreImportAccordion importAccordion, Wallet wallet, KeystoreMnemonicImport importer) {
        super(importAccordion, wallet, importer);
        this.importer = importer;
    }

    @Override
    protected Node getTitle(KeystoreImport importer) {
        Node title = super.getTitle(importer);
        setDescription("Keystore file import");

        createEnterMnemonicButton();
        createImportButton();
        buttonBox.getChildren().addAll(enterMnemonicButton, importButton);

        return title;
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
        setDescription("Enter mnemonic word list");
        showHideLink.setVisible(false);
        setContent(getMnemonicWordsEntry(numWords));
        setExpanded(true);
    }

    private Node getMnemonicWordsEntry(int numWords) {
        VBox vBox = new VBox();
        vBox.setSpacing(10);

        TilePane tilePane = new TilePane();
        tilePane.setPrefRows(numWords/3);
        tilePane.setHgap(10);
        tilePane.setVgap(10);
        tilePane.setOrientation(Orientation.VERTICAL);

        List<String> words = new ArrayList<>();
        for(int i = 0; i < numWords; i++) {
            words.add("");
        }

        ObservableList<String> wordEntryList = FXCollections.observableArrayList(words);
        wordEntriesProperty = new SimpleListProperty<>(wordEntryList);
        for(int i = 0; i < numWords; i++) {
            WordEntry wordEntry = new WordEntry(i, wordEntryList);
            tilePane.getChildren().add(wordEntry);
        }

        vBox.getChildren().add(tilePane);

        AnchorPane anchorPane = new AnchorPane();
        anchorPane.setPadding(new Insets(0, 32, 0, 10));

        PassphraseEntry passphraseEntry = new PassphraseEntry();
        AnchorPane.setLeftAnchor(passphraseEntry, 0.0);

        Button okButton = new Button("Ok");
        okButton.setPrefWidth(70);
        okButton.setDisable(true);
        okButton.setOnAction(event -> {
            prepareImport();
        });

        wordEntriesProperty.addListener((ListChangeListener<String>) c -> {
            for(String word : wordEntryList) {
                if(!WordEntry.isValid(word)) {
                    okButton.setDisable(true);
                    return;
                }
            }

            okButton.setDisable(false);
        });

        AnchorPane.setRightAnchor(okButton, 0.0);

        anchorPane.getChildren().addAll(passphraseEntry, okButton);
        vBox.getChildren().add(anchorPane);

        return vBox;
    }

    private void prepareImport() {
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
            if(e.getCause() != null && e.getCause().getMessage() != null && !e.getCause().getMessage().isEmpty()) {
                errorMessage = e.getCause().getMessage();
            }
            setError("Import Error", errorMessage);
            importButton.setDisable(false);
            return false;
        }
    }

    private static class WordEntry extends HBox {
        private static List<String> wordList;

        public WordEntry(int wordNumber, ObservableList<String> wordEntryList) {
            super();
            setAlignment(Pos.CENTER_RIGHT);

            setSpacing(10);
            Label label = new Label((wordNumber+1) + ".");
            label.setPrefWidth(20);
            label.setAlignment(Pos.CENTER_RIGHT);
            TextField wordField = new TextField();
            wordField.setMaxWidth(100);

            Bip39Calculator bip39Calculator = new Bip39Calculator();
            wordList = bip39Calculator.getWordList();
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

            setAlignment(Pos.CENTER_RIGHT);
            setSpacing(10);
            Label passphraseLabel = new Label("Passphrase:");
            CustomTextField passphraseField = (CustomTextField) TextFields.createClearableTextField();
            passphraseProperty.bind(passphraseField.textProperty());

            getChildren().addAll(passphraseLabel, passphraseField);
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
}
