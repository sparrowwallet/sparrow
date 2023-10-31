package com.sparrowwallet.sparrow.control;

import com.google.common.base.Throwables;
import com.sparrowwallet.drongo.KeyDerivation;
import com.sparrowwallet.drongo.crypto.ChildNumber;
import com.sparrowwallet.drongo.protocol.Sha256Hash;
import com.sparrowwallet.drongo.wallet.*;
import com.sparrowwallet.sparrow.AppServices;
import com.sparrowwallet.sparrow.EventManager;
import com.sparrowwallet.sparrow.event.KeystoreImportEvent;
import com.sparrowwallet.sparrow.glyphfont.FontAwesome5;
import com.sparrowwallet.sparrow.io.KeystoreCardImport;
import com.sparrowwallet.sparrow.io.CardAuthorizationException;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.concurrent.Service;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.controlsfx.control.textfield.CustomPasswordField;
import org.controlsfx.glyphfont.Glyph;
import org.controlsfx.validation.ValidationResult;
import org.controlsfx.validation.ValidationSupport;
import org.controlsfx.validation.Validator;
import org.controlsfx.validation.decoration.StyleClassValidationDecoration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.smartcardio.CardException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

import static com.sparrowwallet.sparrow.io.CardApi.isReaderAvailable;

public class CardImportPane extends TitledDescriptionPane {
    private static final Logger log = LoggerFactory.getLogger(CardImportPane.class);

    private final KeystoreCardImport importer;
    private List<ChildNumber> derivation;
    protected Button importButton;
    private final SimpleStringProperty pin = new SimpleStringProperty("");

    public CardImportPane(Wallet wallet, KeystoreCardImport importer, KeyDerivation requiredDerivation) {
        super(importer.getName(), "Place card on reader", importer.getKeystoreImportDescription(getAccount(wallet, requiredDerivation)), "image/" + importer.getWalletModel().getType() + ".png");
        this.importer = importer;
        this.derivation = requiredDerivation == null ? wallet.getScriptType().getDefaultDerivation() : requiredDerivation.getDerivation();
    }

    @Override
    protected Control createButton() {
        importButton = new Button("Import");
        Glyph tapGlyph = new Glyph(FontAwesome5.FONT_NAME, FontAwesome5.Glyph.WIFI);
        tapGlyph.setFontSize(12);
        importButton.setGraphic(tapGlyph);
        importButton.setAlignment(Pos.CENTER_RIGHT);
        importButton.setOnAction(event -> {
            importButton.setDisable(true);
            importCard();
        });
        return importButton;
    }

    private void importCard() {
        if(!isReaderAvailable()) {
            setError("No reader", "No card reader was detected.");
            importButton.setDisable(false);
            return;
        }

        StringProperty messageProperty = new SimpleStringProperty();
        messageProperty.addListener((observable, oldValue, newValue) -> {
            Platform.runLater(() -> setDescription(newValue));
        });

        try {
            if(pin.get().length() < importer.getWalletModel().getMinPinLength()) {
                setDescription(pin.get().isEmpty() ? (!importer.getWalletModel().hasDefaultPin() && !importer.isInitialized() ? "Choose a PIN code" : "Enter PIN code") : "PIN code too short");
                setContent(getPinAndDerivationEntry());
                showHideLink.setVisible(false);
                setExpanded(true);
                importButton.setDisable(false);
                return;
            }

            if(!importer.isInitialized()) {
                setDescription("Card not initialized");
                setContent(getInitializationPanel(messageProperty));
                showHideLink.setVisible(false);
                setExpanded(true);
                return;
            }
        } catch(CardException e) {
            setError("Card Error", e.getMessage());
            importButton.setDisable(false);
            return;
        }

        CardImportService cardImportService = new CardImportService(importer, pin.get(), derivation, messageProperty);
        cardImportService.setOnSucceeded(event -> {
            EventManager.get().post(new KeystoreImportEvent(cardImportService.getValue()));
        });
        cardImportService.setOnFailed(event -> {
            Throwable rootCause = Throwables.getRootCause(event.getSource().getException());
            if(rootCause instanceof CardAuthorizationException) {
                setError(rootCause.getMessage(), null);
                setContent(getPinAndDerivationEntry());
            } else {
                log.error("Error importing keystore from card", event.getSource().getException());
                setError("Import Error", rootCause.getMessage());
            }
            importButton.setDisable(false);
        });
        cardImportService.start();
    }

    private Node getInitializationPanel(StringProperty messageProperty) {
        if(importer.getWalletModel().requiresSeedInitialization()) {
            return getSeedInitializationPanel(messageProperty);
        }

        return getEntropyInitializationPanel(messageProperty);
    }

    private Node getSeedInitializationPanel(StringProperty messageProperty) {
        VBox confirmationBox = new VBox(5);
        CustomPasswordField confirmationPin = new ViewPasswordField();
        confirmationPin.setPromptText("Re-enter chosen PIN");
        confirmationBox.getChildren().add(confirmationPin);

        Button initializeButton = new Button("Initialize");
        initializeButton.setDefaultButton(true);
        initializeButton.setOnAction(event -> {
            initializeButton.setDisable(true);
            if(!pin.get().equals(confirmationPin.getText())) {
                setError("PIN Error", "The confirmation PIN did not match");
                return;
            }
            int pinSize = pin.get().length();
            if(pinSize < importer.getWalletModel().getMinPinLength() || pinSize > importer.getWalletModel().getMaxPinLength()) {
                setError("PIN Error", "PIN length must be between " + importer.getWalletModel().getMinPinLength() + " and " + importer.getWalletModel().getMaxPinLength() + " characters");
                return;
            }

            SeedEntryDialog seedEntryDialog = new SeedEntryDialog(importer.getWalletModel().toDisplayString() + " Seed Words", 12);
            seedEntryDialog.initOwner(this.getScene().getWindow());
            Optional<List<String>> optWords = seedEntryDialog.showAndWait();
            if(optWords.isPresent()) {
                try {
                    List<String> mnemonicWords = optWords.get();
                    Bip39MnemonicCode.INSTANCE.check(mnemonicWords);
                    DeterministicSeed seed = new DeterministicSeed(mnemonicWords, "", System.currentTimeMillis(), DeterministicSeed.Type.BIP39);
                    byte[] seedBytes = seed.getSeedBytes();

                    CardInitializationService cardInitializationService = new CardInitializationService(importer, pin.get(), seedBytes, messageProperty);
                    cardInitializationService.setOnSucceeded(successEvent -> {
                        AppServices.showSuccessDialog("Card Initialized", "The card was successfully initialized.\n\nYou can now import the keystore.");
                        setDescription("Leave card on reader");
                        setExpanded(false);
                        importButton.setDisable(false);
                    });
                    cardInitializationService.setOnFailed(failEvent -> {
                        log.error("Error initializing card", failEvent.getSource().getException());
                        AppServices.showErrorDialog("Card Initialization Failed", "The card was not initialized.\n\n" + failEvent.getSource().getException().getMessage());
                        initializeButton.setDisable(false);
                    });
                    cardInitializationService.start();
                } catch(MnemonicException e) {
                    log.error("Invalid seed entered", e);
                    AppServices.showErrorDialog("Invalid seed entered", "The seed was invalid.\n\n" + e.getMessage());
                    initializeButton.setDisable(false);
                }
            } else {
                initializeButton.setDisable(false);
            }
        });

        HBox contentBox = new HBox(20);
        contentBox.getChildren().addAll(confirmationBox, initializeButton);
        contentBox.setPadding(new Insets(10, 30, 10, 30));
        HBox.setHgrow(confirmationBox, Priority.ALWAYS);

        return contentBox;
    }

    private Node getEntropyInitializationPanel(StringProperty messageProperty) {
        VBox initTypeBox = new VBox(5);
        RadioButton automatic = new RadioButton("Automatic (Recommended)");
        RadioButton advanced = new RadioButton("Advanced");
        TextField entropy = new TextField();
        entropy.setPromptText("Enter input for user entropy");
        entropy.setDisable(true);

        ToggleGroup toggleGroup = new ToggleGroup();
        automatic.setToggleGroup(toggleGroup);
        advanced.setToggleGroup(toggleGroup);
        automatic.setSelected(true);
        toggleGroup.selectedToggleProperty().addListener((observable, oldValue, newValue) -> {
            entropy.setDisable(newValue == automatic);
        });

        initTypeBox.getChildren().addAll(automatic, advanced, entropy);

        Button initializeButton = new Button("Initialize");
        initializeButton.setDefaultButton(true);
        initializeButton.setOnAction(event -> {
            initializeButton.setDisable(true);
            byte[] chainCode = toggleGroup.getSelectedToggle() == automatic ? null : Sha256Hash.hashTwice(entropy.getText().getBytes(StandardCharsets.UTF_8));
            CardInitializationService cardInitializationService = new CardInitializationService(importer, pin.get(), chainCode, messageProperty);
            cardInitializationService.setOnSucceeded(successEvent -> {
                AppServices.showSuccessDialog("Card Initialized", "The card was successfully initialized.\n\nYou can now import the keystore.");
                setDescription("Leave card on reader");
                setExpanded(false);
                importButton.setDisable(false);
            });
            cardInitializationService.setOnFailed(failEvent -> {
                Throwable rootCause = Throwables.getRootCause(failEvent.getSource().getException());
                if(rootCause instanceof CardAuthorizationException) {
                    setError(rootCause.getMessage(), null);
                    setContent(getPinEntry());
                    importButton.setDisable(false);
                } else {
                    log.error("Error initializing card", failEvent.getSource().getException());
                    AppServices.showErrorDialog("Card Initialization Failed", "The card was not initialized.\n\n" + failEvent.getSource().getException().getMessage());
                    initializeButton.setDisable(false);
                }
            });
            cardInitializationService.start();
        });

        HBox contentBox = new HBox(20);
        contentBox.getChildren().addAll(initTypeBox, initializeButton);
        contentBox.setPadding(new Insets(10, 30, 10, 30));
        HBox.setHgrow(initTypeBox, Priority.ALWAYS);

        return contentBox;
    }

    private Node getPinAndDerivationEntry() {
        VBox vBox = new VBox();
        vBox.getChildren().add(getPinEntry());
        vBox.getChildren().add(getDerivationEntry());
        return vBox;
    }

    private Node getPinEntry() {
        VBox vBox = new VBox();

        CustomPasswordField pinField = new ViewPasswordField();
        pinField.setPromptText("PIN Code");
        importButton.setDefaultButton(true);
        pin.bind(pinField.textProperty());
        HBox.setHgrow(pinField, Priority.ALWAYS);
        Platform.runLater(pinField::requestFocus);

        HBox contentBox = new HBox();
        contentBox.setAlignment(Pos.TOP_RIGHT);
        contentBox.setSpacing(20);
        contentBox.getChildren().add(pinField);
        contentBox.setPadding(new Insets(10, 30, 0, 30));
        contentBox.setPrefHeight(50);

        vBox.getChildren().add(contentBox);

        return vBox;
    }

    private Node getDerivationEntry() {
        VBox vBox = new VBox();

        CheckBox checkBox = new CheckBox("Use Custom Derivation");
        Label customLabel = new Label("Derivation:");
        TextField customDerivation = new TextField(KeyDerivation.writePath(derivation));

        ValidationSupport validationSupport = new ValidationSupport();
        validationSupport.setValidationDecorator(new StyleClassValidationDecoration());
        validationSupport.registerValidator(customDerivation, Validator.combine(
                Validator.createEmptyValidator("Derivation is required"),
                (Control c, String newValue) -> ValidationResult.fromErrorIf( c, "Invalid derivation", !KeyDerivation.isValid(newValue))
        ));

        customDerivation.textProperty().addListener((observable, oldValue, newValue) -> {
            if(newValue.isEmpty() || !KeyDerivation.isValid(newValue)) {
                importButton.setDisable(true);
            } else {
                importButton.setDisable(false);
                derivation = KeyDerivation.parsePath(newValue);
            }
        });

        checkBox.managedProperty().bind(checkBox.visibleProperty());
        customLabel.managedProperty().bind(customLabel.visibleProperty());
        customDerivation.managedProperty().bind(customDerivation.visibleProperty());
        customLabel.visibleProperty().bind(checkBox.visibleProperty().not());
        customDerivation.visibleProperty().bind(checkBox.visibleProperty().not());

        checkBox.selectedProperty().addListener((observable, oldValue, newValue) -> {
            checkBox.setVisible(false);
        });

        HBox derivationBox = new HBox();
        derivationBox.setAlignment(Pos.CENTER_LEFT);
        derivationBox.setSpacing(20);
        derivationBox.getChildren().addAll(checkBox, customLabel, customDerivation);
        derivationBox.setPadding(new Insets(10, 30, 10, 30));
        derivationBox.setPrefHeight(50);

        vBox.getChildren().addAll(derivationBox);

        return vBox;
    }

    public static class CardInitializationService extends Service<Void> {
        private final KeystoreCardImport cardImport;
        private final String pin;
        private final byte[] chainCode;
        private final StringProperty messageProperty;

        public CardInitializationService(KeystoreCardImport cardImport, String pin, byte[] chainCode, StringProperty messageProperty) {
            this.cardImport = cardImport;
            this.pin = pin;
            this.chainCode = chainCode;
            this.messageProperty = messageProperty;
        }

        @Override
        protected Task<Void> createTask() {
            return new Task<>() {
                @Override
                protected Void call() throws Exception {
                    cardImport.initialize(pin, chainCode, messageProperty);
                    return null;
                }
            };
        }
    }

    public static class CardImportService extends Service<Keystore> {
        private final KeystoreCardImport cardImport;
        private final String pin;
        private final List<ChildNumber> derivation;
        private final StringProperty messageProperty;

        public CardImportService(KeystoreCardImport cardImport, String pin, List<ChildNumber> derivation, StringProperty messageProperty) {
            this.cardImport = cardImport;
            this.pin = pin;
            this.derivation = derivation;
            this.messageProperty = messageProperty;
        }

        @Override
        protected Task<Keystore> createTask() {
            return new Task<>() {
                @Override
                protected Keystore call() throws Exception {
                    return cardImport.getKeystore(pin, derivation, messageProperty);
                }
            };
        }
    }
}
