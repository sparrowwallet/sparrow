package com.sparrowwallet.sparrow.control;

import com.sparrowwallet.drongo.wallet.Bip39MnemonicCode;
import com.sparrowwallet.drongo.wallet.DeterministicSeed;
import com.sparrowwallet.sparrow.AppServices;
import com.sparrowwallet.sparrow.glyphfont.FontAwesome5;
import javafx.application.Platform;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleListProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.concurrent.ScheduledService;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.input.Clipboard;
import javafx.scene.layout.*;
import javafx.util.Callback;
import javafx.util.Duration;
import org.controlsfx.control.textfield.AutoCompletionBinding;
import org.controlsfx.control.textfield.TextFields;
import org.controlsfx.glyphfont.Glyph;
import org.controlsfx.validation.ValidationResult;
import org.controlsfx.validation.ValidationSupport;
import org.controlsfx.validation.Validator;
import org.controlsfx.validation.decoration.StyleClassValidationDecoration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

import static com.sparrowwallet.sparrow.AppServices.showErrorDialog;

public class MnemonicKeystorePane extends TitledDescriptionPane {
    private static final Logger log = LoggerFactory.getLogger(MnemonicKeystorePane.class);

    protected SplitMenuButton enterMnemonicButton;
    protected TilePane wordsPane;
    protected Label validLabel;
    protected Label invalidLabel;

    protected SimpleListProperty<String> wordEntriesProperty;
    protected final SimpleStringProperty passphraseProperty = new SimpleStringProperty("");
    protected IntegerProperty defaultWordSizeProperty;

    public MnemonicKeystorePane(String title, String description, String content, String imageUrl) {
        super(title, description, content, imageUrl);
    }

    @Override
    protected Control createButton() {
        createEnterMnemonicButton();
        return enterMnemonicButton;
    }

    private void createEnterMnemonicButton() {
        enterMnemonicButton = new SplitMenuButton();
        enterMnemonicButton.setAlignment(Pos.CENTER_RIGHT);
        enterMnemonicButton.setText("Use 24 Words");
        defaultWordSizeProperty = new SimpleIntegerProperty(24);
        defaultWordSizeProperty.addListener((observable, oldValue, newValue) -> {
            enterMnemonicButton.setText("Use " + newValue + " Words");
        });
        enterMnemonicButton.setOnAction(event -> {
            enterMnemonic(defaultWordSizeProperty.get());
        });
        int[] numberWords = new int[] {24, 21, 18, 15, 12};
        for(int i = 0; i < numberWords.length; i++) {
            MenuItem item = new MenuItem("Use " + numberWords[i] + " Words");
            final int words = numberWords[i];
            item.setOnAction(event -> {
                defaultWordSizeProperty.set(words);
                enterMnemonic(words);
            });
            enterMnemonicButton.getItems().add(item);
        }
        enterMnemonicButton.getItems().add(new SeparatorMenuItem());
        MenuItem gridItem = new MenuItem("Border Wallets Grid...");
        gridItem.setOnAction(event -> {
            showGrid();
        });
        enterMnemonicButton.getItems().add(gridItem);

        MenuItem scanItem = new MenuItem("Scan QR...");
        scanItem.setOnAction(event -> {
            scanQR();
        });
        enterMnemonicButton.getItems().add(scanItem);
        enterMnemonicButton.managedProperty().bind(enterMnemonicButton.visibleProperty());
    }

    protected void showGrid() {
        MnemonicGridDialog mnemonicGridDialog = new MnemonicGridDialog();
        mnemonicGridDialog.initOwner(this.getScene().getWindow());
        Optional<List<String>> optWords = mnemonicGridDialog.showAndWait();
        if(optWords.isPresent()) {
            List<String> words = optWords.get();
            defaultWordSizeProperty.set(words.size() + 1);
            setContent(getMnemonicWordsEntry(words.size() + 1, true, true));
            setExpanded(true);

            for(int i = 0; i < wordsPane.getChildren().size(); i++) {
                WordEntry wordEntry = (WordEntry)wordsPane.getChildren().get(i);
                if(i < words.size()) {
                    wordEntry.getEditor().setText(words.get(i));
                    wordEntry.getEditor().setEditable(false);
                } else {
                    ScheduledService<Void> service = new ScheduledService<>() {
                        @Override
                        protected Task<Void> createTask() {
                            return new Task<>() {
                                @Override
                                protected Void call() {
                                    return null;
                                }
                            };
                        }
                    };
                    service.setDelay(Duration.millis(500));
                    service.setOnSucceeded(event1 -> {
                        service.cancel();
                        Platform.runLater(() -> wordEntry.getEditor().requestFocus());
                    });
                    service.start();
                }
            }
        }
    }

    protected void scanQR() {
        QRScanDialog qrScanDialog = new QRScanDialog();
        qrScanDialog.initOwner(this.getScene().getWindow());
        Optional<QRScanDialog.Result> optionalResult = qrScanDialog.showAndWait();
        if(optionalResult.isPresent()) {
            QRScanDialog.Result result = optionalResult.get();
            if(result.seed != null) {
                showWordList(result.seed);
                Platform.runLater(() -> validLabel.requestFocus());
            } else if(result.exception != null) {
                log.error("Error scanning QR", result.exception);
                showErrorDialog("Error scanning QR", result.exception.getMessage());
            } else {
                AppServices.showErrorDialog("Invalid QR Code", "Cannot parse QR code into a seed.");
            }
        }
    }

    protected void showWordList(DeterministicSeed seed) {
        List<String> words = seed.getMnemonicCode();
        setContent(getMnemonicWordsEntry(words.size(), true, true));
        setExpanded(true);

        for(int i = 0; i < wordsPane.getChildren().size(); i++) {
            WordEntry wordEntry = (WordEntry)wordsPane.getChildren().get(i);
            wordEntry.getEditor().setText(words.get(i));
            wordEntry.getEditor().setEditable(false);
        }
    }

    protected void enterMnemonic(int numWords) {
        setDescription("Generate new or enter existing");
        showHideLink.setVisible(false);
        setContent(getMnemonicWordsEntry(numWords, true, true));
        setExpanded(true);
    }

    protected Node getMnemonicWordsEntry(int numWords, boolean showPassphrase, boolean editPassphrase) {
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
        List<WordEntry> wordEntries = new ArrayList<>(numWords);
        for(int i = 0; i < numWords; i++) {
            wordEntries.add(new WordEntry(i, wordEntryList));
        }
        for(int i = 0; i < numWords - 1; i++) {
            wordEntries.get(i).setNextEntry(wordEntries.get(i + 1));
            wordEntries.get(i).setNextField(wordEntries.get(i + 1).getEditor());
        }
        wordsPane.getChildren().addAll(wordEntries);

        vBox.getChildren().add(wordsPane);

        if(showPassphrase) {
            PassphraseEntry passphraseEntry = new PassphraseEntry(editPassphrase);
            wordEntries.get(wordEntries.size() - 1).setNextField(passphraseEntry.getEditor());
            passphraseEntry.setPadding(new Insets(0, 26, 10, 10));
            vBox.getChildren().add(passphraseEntry);
        }

        AnchorPane buttonPane = new AnchorPane();
        buttonPane.setPadding(new Insets(0, 26, 0, 10));

        HBox leftBox = new HBox(10);
        leftBox.getChildren().addAll(createLeftButtons());

        buttonPane.getChildren().add(leftBox);
        AnchorPane.setLeftAnchor(leftBox, 0.0);

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

            onWordChange(empty, validWords, validChecksum);
        });

        HBox rightBox = new HBox();
        rightBox.setSpacing(10);
        rightBox.getChildren().addAll(createRightButtons());

        buttonPane.getChildren().add(rightBox);
        AnchorPane.setRightAnchor(rightBox, 0.0);

        vBox.getChildren().add(buttonPane);

        Platform.runLater(() -> wordEntries.get(0).getEditor().requestFocus());

        StackPane stackPane = new StackPane();
        stackPane.getChildren().add(vBox);
        return stackPane;
    }

    protected List<Node> createLeftButtons() {
        return Collections.emptyList();
    }

    protected List<Node> createRightButtons() {
        return Collections.emptyList();
    }

    protected void onWordChange(boolean empty, boolean validWords, boolean validChecksum) {
        //nothing by default
    }

    protected static class WordEntry extends HBox {
        private static List<String> wordList;
        private final TextField wordField;
        private WordEntry nextEntry;
        private TextField nextField;

        public WordEntry(int wordNumber, ObservableList<String> wordEntryList) {
            super();
            setAlignment(Pos.CENTER_RIGHT);

            setSpacing(10);
            Label label = new Label((wordNumber+1) + ".");
            label.setPrefWidth(22);
            label.setAlignment(Pos.CENTER_RIGHT);
            wordField = new TextField() {
                @Override
                public void paste() {
                    Clipboard clipboard = Clipboard.getSystemClipboard();
                    if(clipboard.hasString() && clipboard.getString().matches("(?m).+[\\n\\s][\\S\\s]*")) {
                        String[] words = clipboard.getString().split("[\\n\\s]");
                        WordEntry entry = WordEntry.this;
                        for(int i = 0; i < words.length; i++) {
                            String word = words[i];
                            if(entry.nextField != null) {
                                if(i == words.length - 2 && isValid(word)) {
                                    label.requestFocus();
                                } else {
                                    entry.nextField.requestFocus();
                                }
                            }

                            entry.wordField.setText(word);
                            entry = entry.nextEntry;
                            if(entry == null) {
                                break;
                            }
                        }
                    } else {
                        super.paste();
                    }
                }
            };
            wordField.setMaxWidth(100);
            TextFormatter<?> formatter = new TextFormatter<>((TextFormatter.Change change) -> {
                String text = change.getText();
                // if text was added, fix the text to fit the requirements
                if(!text.isEmpty()) {
                    String newText = text.replace(" ", "").toLowerCase(Locale.ROOT);
                    int carretPos = change.getCaretPosition() - text.length() + newText.length();
                    change.setText(newText);
                    // fix caret position based on difference in originally added text and fixed text
                    change.selectRange(carretPos, carretPos);
                }
                return change;
            });
            wordField.setTextFormatter(formatter);

            wordList = Bip39MnemonicCode.INSTANCE.getWordList();
            AutoCompletionBinding<String> autoCompletionBinding = TextFields.bindAutoCompletion(wordField, new WordlistSuggestionProvider(wordList, wordNumber, wordEntryList));
            autoCompletionBinding.setDelay(50);
            autoCompletionBinding.setOnAutoCompleted(event -> {
                if(nextField != null) {
                    nextField.requestFocus();
                }
            });

            //Show autocomplete for the last word on focus if empty
            boolean lastWord = wordNumber == wordEntryList.size() - 1;
            if(lastWord) {
                wordField.focusedProperty().addListener((observable, oldValue, focused) -> {
                    if(focused && wordField.getText().isEmpty()) {
                        autoCompletionBinding.setUserInput("");
                    }
                });
            }

            ValidationSupport validationSupport = new ValidationSupport();
            validationSupport.setValidationDecorator(new StyleClassValidationDecoration());
            validationSupport.registerValidator(wordField, Validator.combine(
                    (Control c, String newValue) -> ValidationResult.fromErrorIf( c, "Invalid word", (newValue.length() > 0 || !lastWord) && !wordList.contains(newValue))
            ));

            wordField.textProperty().addListener((observable, oldValue, newValue) -> {
                wordEntryList.set(wordNumber, newValue);
            });

            this.getChildren().addAll(label, wordField);
        }

        public TextField getEditor() {
            return wordField;
        }

        public void setNextEntry(WordEntry nextEntry) {
            this.nextEntry = nextEntry;
        }

        public void setNextField(TextField field) {
            this.nextField = field;
        }

        public static boolean isValid(String word) {
            return wordList.contains(word);
        }
    }

    protected static class WordlistSuggestionProvider implements Callback<AutoCompletionBinding.ISuggestionRequest, Collection<String>> {
        private final List<String> wordList;
        private final int wordNumber;
        private final ObservableList<String> wordEntryList;

        public WordlistSuggestionProvider(List<String> wordList, int wordNumber, ObservableList<String> wordEntryList) {
            this.wordList = wordList;
            this.wordNumber = wordNumber;
            this.wordEntryList = wordEntryList;
        }

        @Override
        public Collection<String> call(AutoCompletionBinding.ISuggestionRequest request) {
            if(wordNumber == wordEntryList.size() - 1 && allPreviousWordsValid()) {
                try {
                    List<String> possibleLastWords = Bip39MnemonicCode.INSTANCE.getPossibleLastWords(wordEntryList.subList(0, wordEntryList.size() - 1));
                    if(!request.getUserText().isEmpty()) {
                        possibleLastWords.removeIf(s -> !s.startsWith(request.getUserText()));
                    }

                    return possibleLastWords;
                } catch(Exception e) {
                    log.warn("Cannot determine possible last words", e);
                }
            }

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

        private boolean allPreviousWordsValid() {
            for(int i = 0; i < wordEntryList.size() - 1; i++) {
                if(!WordEntry.isValid(wordEntryList.get(i))) {
                    return false;
                }
            }

            return true;
        }
    }

    protected class PassphraseEntry extends HBox {
        private final TextField passphraseField;

        public PassphraseEntry(boolean editable) {
            super();

            setAlignment(Pos.CENTER_LEFT);
            setSpacing(10);
            Label usePassphraseLabel = new Label("Use passphrase?");
            usePassphraseLabel.managedProperty().bind(usePassphraseLabel.visibleProperty());
            CheckBox usePassphraseCheckbox = new CheckBox(" ");
            usePassphraseCheckbox.setDisable(!editable);
            usePassphraseCheckbox.managedProperty().bind(usePassphraseCheckbox.visibleProperty());
            usePassphraseLabel.visibleProperty().bind(usePassphraseCheckbox.visibleProperty());
            usePassphraseCheckbox.setVisible(passphraseProperty.isEmpty().get());
            usePassphraseCheckbox.selectedProperty().addListener((observable, oldValue, newValue) -> {
                if(newValue) {
                    Optional<ButtonType> optType = AppServices.showWarningDialog("Add a passphrase?", "A passphrase is not a password! " +
                            "Any variation entered in future loads a valid wallet, but with different addresses.\n\n" +
                            "This feature provides optional added security for advanced users only. Are you sure?", ButtonType.NO, ButtonType.YES);
                    if(optType.isPresent() && optType.get() == ButtonType.YES) {
                        usePassphraseCheckbox.setVisible(false);
                    } else {
                        usePassphraseCheckbox.setSelected(false);
                    }
                }
            });

            Label passphraseLabel = new Label("Passphrase:");
            passphraseLabel.managedProperty().bind(passphraseLabel.visibleProperty());
            passphraseField = new TextField();
            passphraseField.setPromptText(passphraseProperty.isEmpty().get() ? "Leave blank for none" : "");
            passphraseField.setText(passphraseProperty.get());
            passphraseField.setDisable(!editable);
            passphraseField.managedProperty().bind(passphraseField.visibleProperty());
            passphraseLabel.visibleProperty().bind(passphraseField.visibleProperty());
            passphraseField.visibleProperty().bind(usePassphraseCheckbox.visibleProperty().not());
            passphraseProperty.bind(passphraseField.textProperty());

            HelpLabel helpLabel = new HelpLabel();
            helpLabel.setPrefHeight(28);
            helpLabel.setStyle("-fx-padding: 0 0 0 0");
            helpLabel.setHelpText("Advanced feature: a passphrase provides optional added security, but it is not stored so it must be remembered!");

            getChildren().addAll(usePassphraseLabel, usePassphraseCheckbox, passphraseLabel, passphraseField, helpLabel);
        }

        public TextField getEditor() {
            return passphraseField;
        }
    }

    public static Glyph getValidGlyph() {
        Glyph validGlyph = new Glyph(FontAwesome5.FONT_NAME, FontAwesome5.Glyph.CHECK_CIRCLE);
        validGlyph.getStyleClass().add("success");
        validGlyph.setFontSize(12);
        return validGlyph;
    }

    public static Glyph getInvalidGlyph() {
        Glyph invalidGlyph = new Glyph(FontAwesome5.FONT_NAME, FontAwesome5.Glyph.EXCLAMATION_CIRCLE);
        invalidGlyph.getStyleClass().add("failure");
        invalidGlyph.setFontSize(12);
        return invalidGlyph;
    }
}
