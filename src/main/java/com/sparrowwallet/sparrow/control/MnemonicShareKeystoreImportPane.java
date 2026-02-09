package com.sparrowwallet.sparrow.control;

import com.sparrowwallet.drongo.KeyDerivation;
import com.sparrowwallet.drongo.crypto.ChildNumber;
import com.sparrowwallet.drongo.protocol.ScriptType;
import com.sparrowwallet.drongo.wallet.DeterministicSeed;
import com.sparrowwallet.drongo.wallet.Keystore;
import com.sparrowwallet.drongo.wallet.MnemonicException;
import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.drongo.wallet.slip39.Share;
import com.sparrowwallet.sparrow.EventManager;
import com.sparrowwallet.sparrow.event.KeystoreImportEvent;
import com.sparrowwallet.sparrow.glyphfont.FontAwesome5;
import com.sparrowwallet.sparrow.glyphfont.GlyphUtils;
import com.sparrowwallet.sparrow.io.ImportException;
import com.sparrowwallet.sparrow.io.KeystoreMnemonicShareImport;
import com.sparrowwallet.sparrow.io.Slip39;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import org.controlsfx.glyphfont.Glyph;
import org.controlsfx.validation.ValidationResult;
import org.controlsfx.validation.ValidationSupport;
import org.controlsfx.validation.Validator;
import org.controlsfx.validation.decoration.StyleClassValidationDecoration;

import java.util.*;

public class MnemonicShareKeystoreImportPane extends MnemonicKeystorePane {
    protected final Wallet wallet;
    private final KeystoreMnemonicShareImport importer;
    private final KeyDerivation defaultDerivation;
    private final List<List<String>> mnemonicShares = new ArrayList<>();

    private SplitMenuButton importButton;

    private Button calculateButton;
    private Button backButton;
    private Button nextButton;
    private int currentShare;

    public MnemonicShareKeystoreImportPane(Wallet wallet, KeystoreMnemonicShareImport importer, KeyDerivation defaultDerivation) {
        super(importer.getName(), "Enter seed share", importer.getKeystoreImportDescription(), importer.getWalletModel());
        this.wallet = wallet;
        this.importer = importer;
        this.defaultDerivation = defaultDerivation;

        createImportButton();
        buttonBox.getChildren().add(importButton);
    }

    @Override
    protected Control createButton() {
        createEnterMnemonicButton();
        return enterMnemonicButton;
    }

    private void createEnterMnemonicButton() {
        enterMnemonicButton = new SplitMenuButton();
        enterMnemonicButton.setAlignment(Pos.CENTER_RIGHT);
        enterMnemonicButton.setText("Use 20 Words");
        defaultWordSizeProperty = new SimpleIntegerProperty(20);
        defaultWordSizeProperty.addListener((observable, oldValue, newValue) -> {
            enterMnemonicButton.setText("Use " + newValue + " Words");
        });
        enterMnemonicButton.setOnAction(event -> {
            resetShares();
            enterMnemonic(defaultWordSizeProperty.get());
        });
        int[] numberWords = new int[] {20, 33};
        for(int i = 0; i < numberWords.length; i++) {
            MenuItem item = new MenuItem("Use " + numberWords[i] + " Words");
            final int words = numberWords[i];
            item.setOnAction(event -> {
                resetShares();
                defaultWordSizeProperty.set(words);
                enterMnemonic(words);
            });
            enterMnemonicButton.getItems().add(item);
        }
        enterMnemonicButton.managedProperty().bind(enterMnemonicButton.visibleProperty());
    }

    protected List<Node> createRightButtons() {
        calculateButton = new Button("Create Keystore");
        calculateButton.setDefaultButton(true);
        calculateButton.setOnAction(event -> {
            prepareImport();
        });
        calculateButton.managedProperty().bind(calculateButton.visibleProperty());
        calculateButton.setTooltip(new Tooltip("Create the keystore from the provided shares"));
        calculateButton.setVisible(false);

        backButton = new Button("Back");
        backButton.setOnAction(event -> {
            lastShare();
        });
        backButton.managedProperty().bind(backButton.visibleProperty());
        backButton.setTooltip(new Tooltip("Display the last share added"));
        backButton.setVisible(currentShare > 0);

        nextButton = new Button("Next");
        nextButton.setOnAction(event -> {
            nextShare();
        });
        nextButton.managedProperty().bind(nextButton.visibleProperty());
        nextButton.setTooltip(new Tooltip("Add the next share"));
        nextButton.visibleProperty().bind(calculateButton.visibleProperty().not());
        nextButton.setDefaultButton(true);
        nextButton.setDisable(true);

        return List.of(backButton, nextButton, calculateButton);
    }

    @Override
    protected void enterMnemonic(int numWords) {
        super.enterMnemonic(numWords);
        setDescription("Enter existing share");
    }

    private void resetShares() {
        currentShare = 0;
        mnemonicShares.clear();
    }

    private void lastShare() {
        currentShare--;
        showWordList(mnemonicShares.get(currentShare));
    }

    private void nextShare() {
        if(currentShare == mnemonicShares.size()) {
            mnemonicShares.add(wordEntriesProperty.get());
        } else {
            mnemonicShares.set(currentShare, wordEntriesProperty.get());
        }

        currentShare++;

        if(currentShare < mnemonicShares.size()) {
            showWordList(mnemonicShares.get(currentShare));
        } else {
            setContent(getMnemonicWordsEntry(defaultWordSizeProperty.get(), true, true));
        }
        setExpanded(true);
    }

    protected void onWordChange(boolean empty, boolean validWords, boolean validChecksum) {
        boolean validSet = false;
        boolean complete = false;
        if(!empty && validWords) {
            try {
                Share.fromMnemonic(String.join(" ", wordEntriesProperty.get()));
                validChecksum = true;

                List<List<String>> existing = new ArrayList<>(mnemonicShares);
                if(currentShare >= mnemonicShares.size()) {
                    existing.add(wordEntriesProperty.get());
                }

                importer.getKeystore(wallet.getScriptType().getDefaultDerivation(), existing, passphraseProperty.get());
                validSet = true;
                complete = true;
            } catch(MnemonicException e) {
                invalidLabel.setText(e.getTitle());
                invalidLabel.setTooltip(new Tooltip(e.getMessage()));
            } catch(Slip39.Slip39ProgressException e) {
                validSet = true;
                invalidLabel.setText(e.getTitle());
                invalidLabel.setTooltip(new Tooltip(e.getMessage()));
            } catch(ImportException e) {
                if(e.getCause() instanceof MnemonicException mnemonicException) {
                    invalidLabel.setText(mnemonicException.getTitle());
                    invalidLabel.setTooltip(new Tooltip(mnemonicException.getMessage()));
                } else {
                    invalidLabel.setText("Import Error");
                    invalidLabel.setTooltip(new Tooltip(e.getMessage()));
                }
            }
        }

        calculateButton.setVisible(complete);
        backButton.setVisible(currentShare > 0 && !complete);
        nextButton.setDisable(!validChecksum || !validSet);
        validLabel.setVisible(complete);
        validLabel.setText(mnemonicShares.isEmpty() ? "Valid checksum" : "Completed share set");
        invalidLabel.setVisible(!complete && !empty);
        invalidLabel.setGraphic(validChecksum && validSet ? getIncompleteGlyph() : GlyphUtils.getFailureGlyph());
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

    private void prepareImport() {
        nextShare();
        backButton.setVisible(false);

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
            Keystore keystore = importer.getKeystore(derivation, mnemonicShares, passphraseProperty.get());
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

    public static Glyph getIncompleteGlyph() {
        Glyph warningGlyph = new Glyph(FontAwesome5.FONT_NAME, FontAwesome5.Glyph.PLUS_CIRCLE);
        warningGlyph.getStyleClass().add("warn-icon");
        warningGlyph.setFontSize(12);
        return warningGlyph;
    }

    @Override
    protected WordlistProvider getWordlistProvider() {
        return getWordListProvider(DeterministicSeed.Type.SLIP39);
    }
}
