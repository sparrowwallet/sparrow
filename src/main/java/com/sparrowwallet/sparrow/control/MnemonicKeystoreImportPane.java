package com.sparrowwallet.sparrow.control;

import com.sparrowwallet.drongo.KeyDerivation;
import com.sparrowwallet.drongo.crypto.ChildNumber;
import com.sparrowwallet.drongo.protocol.ScriptType;
import com.sparrowwallet.drongo.wallet.*;
import com.sparrowwallet.sparrow.EventManager;
import com.sparrowwallet.sparrow.event.KeystoreImportEvent;
import com.sparrowwallet.sparrow.glyphfont.FontAwesome5;
import com.sparrowwallet.sparrow.io.*;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;
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
import org.controlsfx.glyphfont.Glyph;
import org.controlsfx.tools.Borders;
import org.controlsfx.validation.ValidationResult;
import org.controlsfx.validation.ValidationSupport;
import org.controlsfx.validation.Validator;
import org.controlsfx.validation.decoration.StyleClassValidationDecoration;

import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class MnemonicKeystoreImportPane extends TitledDescriptionPane {
    protected final Wallet wallet;
    private final KeystoreMnemonicImport importer;

    private SplitMenuButton enterMnemonicButton;
    private SplitMenuButton importButton;

    private TilePane wordsPane;
    private Button generateButton;
    private Label validLabel;
    private Label invalidLabel;
    private Button calculateButton;
    private Button backButton;
    private Button nextButton;
    private Button confirmButton;
    private List<String> generatedMnemonicCode;

    private SimpleListProperty<String> wordEntriesProperty;
    private final SimpleStringProperty passphraseProperty = new SimpleStringProperty();
    private IntegerProperty defaultWordSizeProperty;

    public MnemonicKeystoreImportPane(Wallet wallet, KeystoreMnemonicImport importer) {
        super(importer.getName(), "Seed import", importer.getKeystoreImportDescription(), "image/" + importer.getWalletModel().getType() + ".png");
        this.wallet = wallet;
        this.importer = importer;

        createImportButton();
        buttonBox.getChildren().add(importButton);
    }

    public MnemonicKeystoreImportPane(Keystore keystore) {
        super(keystore.getSeed().getType().getName(), keystore.getSeed().needsPassphrase() ? "Passphrase entered" : "No passphrase", "", "image/" + WalletModel.SEED.getType() + ".png");
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
        enterMnemonicButton.setText("Enter 24 Words");
        defaultWordSizeProperty = new SimpleIntegerProperty(24);
        defaultWordSizeProperty.addListener((observable, oldValue, newValue) -> {
            enterMnemonicButton.setText("Enter " + newValue + " Words");
        });
        enterMnemonicButton.setOnAction(event -> {
            enterMnemonic(defaultWordSizeProperty.get());
        });
        int[] numberWords = new int[] {24, 21, 18, 15, 12};
        for(int i = 0; i < numberWords.length; i++) {
            MenuItem item = new MenuItem("Enter " + numberWords[i] + " Words");
            final int words = numberWords[i];
            item.setOnAction(event -> {
                defaultWordSizeProperty.set(words);
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
            passphraseEntry.setPadding(new Insets(0, 26, 10, 10));
            vBox.getChildren().add(passphraseEntry);

            AnchorPane buttonPane = new AnchorPane();
            buttonPane.setPadding(new Insets(0, 26, 0, 10));

            generateButton = new Button("Generate New");
            generateButton.setOnAction(event -> {
                generateNew();
            });
            generateButton.managedProperty().bind(generateButton.visibleProperty());
            generateButton.setTooltip(new Tooltip("Generate a unique set of words that provide the seed for your wallet"));
            buttonPane.getChildren().add(generateButton);
            AnchorPane.setLeftAnchor(generateButton, 0.0);

            validLabel = new Label("Valid checksum", getValidGlyph());
            validLabel.setContentDisplay(ContentDisplay.LEFT);
            validLabel.setGraphicTextGap(5.0);
            validLabel.managedProperty().bind(validLabel.visibleProperty());
            validLabel.setVisible(false);
            buttonPane.getChildren().add(validLabel);
            AnchorPane.setTopAnchor(validLabel, 5.0);
            AnchorPane.setLeftAnchor(validLabel, 0.0);

            invalidLabel = new Label("Invalid checksum", getInvalidGlyph());
            invalidLabel.setContentDisplay(ContentDisplay.LEFT);
            invalidLabel.setGraphicTextGap(5.0);
            invalidLabel.managedProperty().bind(invalidLabel.visibleProperty());
            invalidLabel.setVisible(false);
            buttonPane.getChildren().add(invalidLabel);
            AnchorPane.setTopAnchor(invalidLabel, 5.0);
            AnchorPane.setLeftAnchor(invalidLabel, 0.0);

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

            wordEntriesProperty.addListener((ListChangeListener<String>) c -> {
                boolean empty = true;
                boolean validWords = true;
                boolean validChecksum = false;
                for(String word : wordEntryList) {
                    if(!word.isEmpty()) {
                        empty = false;
                    }

                    if(!WordEntry.isValid(word)) {
                        validWords = false;
                    }
                }

                if(!empty && validWords) {
                    try {
                        importer.getKeystore(wallet.getScriptType().getDefaultDerivation(), wordEntriesProperty.get(), passphraseProperty.get());
                        validChecksum = true;
                    } catch(ImportException e) {
                        //ignore
                    }
                }

                generateButton.setVisible(empty && generatedMnemonicCode == null);
                calculateButton.setDisable(!validChecksum);
                validLabel.setVisible(validChecksum);
                invalidLabel.setVisible(!validChecksum && !empty);
            });

            HBox rightBox = new HBox();
            rightBox.setSpacing(10);
            rightBox.getChildren().addAll(backButton, nextButton, confirmButton, calculateButton);

            buttonPane.getChildren().add(rightBox);
            AnchorPane.setRightAnchor(rightBox, 0.0);

            vBox.getChildren().add(buttonPane);
        }

        StackPane stackPane = new StackPane();
        stackPane.getChildren().add(vBox);
        return stackPane;
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
        setContent(getMnemonicWordsEntry(wordEntriesProperty.get().size(), false));
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
            label.setPrefWidth(22);
            label.setAlignment(Pos.CENTER_RIGHT);
            wordField = new TextField();
            wordField.setMaxWidth(100);
            TextFormatter<?> formatter = new TextFormatter<>((TextFormatter.Change change) -> {
                String text = change.getText();
                // if text was added, fix the text to fit the requirements
                if(!text.isEmpty()) {
                    String newText = text.replace(" ", "").toLowerCase();
                    int carretPos = change.getCaretPosition() - text.length() + newText.length();
                    change.setText(newText);
                    // fix caret position based on difference in originally added text and fixed text
                    change.selectRange(carretPos, carretPos);
                }
                return change;
            });
            wordField.setTextFormatter(formatter);

            wordList = Bip39MnemonicCode.INSTANCE.getWordList();
            AutoCompletionBinding<String> autoCompletionBinding = TextFields.bindAutoCompletion(wordField, new WordlistSuggestionProvider(wordList));
            autoCompletionBinding.setDelay(50);

            ValidationSupport validationSupport = new ValidationSupport();
            validationSupport.setValidationDecorator(new StyleClassValidationDecoration());
            validationSupport.registerValidator(wordField, Validator.combine(
                    Validator.createEmptyValidator("Word is required"),
                    (Control c, String newValue) -> ValidationResult.fromErrorIf( c, "Invalid word", !wordList.contains(newValue))
            ));

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
                    if(word.equals(request.getUserText())) {
                        return Collections.emptyList();
                    }

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

    public static Glyph getValidGlyph() {
        Glyph validGlyph = new Glyph(FontAwesome5.FONT_NAME, FontAwesome5.Glyph.CHECK_CIRCLE);
        validGlyph.getStyleClass().add("valid-checksum");
        validGlyph.setFontSize(12);
        return validGlyph;
    }

    public static Glyph getInvalidGlyph() {
        Glyph invalidGlyph = new Glyph(FontAwesome5.FONT_NAME, FontAwesome5.Glyph.EXCLAMATION_CIRCLE);
        invalidGlyph.getStyleClass().add("invalid-checksum");
        invalidGlyph.setFontSize(12);
        return invalidGlyph;
    }
}
