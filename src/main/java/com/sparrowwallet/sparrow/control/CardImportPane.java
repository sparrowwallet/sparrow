package com.sparrowwallet.sparrow.control;

import com.google.common.base.Throwables;
import com.sparrowwallet.drongo.KeyDerivation;
import com.sparrowwallet.drongo.crypto.ChildNumber;
import com.sparrowwallet.drongo.protocol.Sha256Hash;
import com.sparrowwallet.drongo.wallet.Keystore;
import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.sparrow.AppServices;
import com.sparrowwallet.sparrow.EventManager;
import com.sparrowwallet.sparrow.event.KeystoreImportEvent;
import com.sparrowwallet.sparrow.glyphfont.FontAwesome5;
import com.sparrowwallet.sparrow.io.KeystoreCardImport;
import com.sparrowwallet.sparrow.io.ckcard.CardAuthorizationException;
import javafx.beans.property.SimpleStringProperty;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.smartcardio.CardException;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class CardImportPane extends TitledDescriptionPane {
    private static final Logger log = LoggerFactory.getLogger(CardImportPane.class);

    private final KeystoreCardImport importer;
    private final List<ChildNumber> derivation;
    protected Button importButton;
    private final SimpleStringProperty pin = new SimpleStringProperty("");
    private final SimpleStringProperty errorText = new SimpleStringProperty("");
    private boolean initialized;

    public CardImportPane(Wallet wallet, KeystoreCardImport importer, KeyDerivation requiredDerivation) {
        super(importer.getName(), "Keystore import", importer.getKeystoreImportDescription(getAccount(wallet, requiredDerivation)), "image/" + importer.getWalletModel().getType() + ".png");
        this.importer = importer;
        this.derivation = requiredDerivation == null ? wallet.getScriptType().getDefaultDerivation() : requiredDerivation.getDerivation();
    }

    @Override
    protected Control createButton() {
        importButton = new Button("Tap");
        Glyph tapGlyph = new Glyph(FontAwesome5.FONT_NAME, FontAwesome5.Glyph.WIFI);
        tapGlyph.setFontSize(12);
        importButton.setGraphic(tapGlyph);
        importButton.setAlignment(Pos.CENTER_RIGHT);
        importButton.setOnAction(event -> {
            importCard();
        });
        return importButton;
    }

    private void importCard() {
        errorText.set("");

        try {
            if(!importer.isInitialized()) {
                setDescription("Card not initialized");
                setContent(getInitializationPanel());
                setExpanded(true);
                return;
            } else {
                initialized = true;
            }
        } catch(CardException e) {
            setError("Card Error", e.getMessage());
            return;
        }

        if(pin.get().isEmpty()) {
            setDescription("Enter PIN code");
            setContent(getPinEntry());
            setExpanded(true);
            return;
        }

        CardImportService cardImportService = new CardImportService(importer, pin.get(), derivation);
        cardImportService.messageProperty().addListener((observable, oldValue, newValue) -> {
            setDescription(newValue);
        });
        cardImportService.setOnSucceeded(event -> {
            EventManager.get().post(new KeystoreImportEvent(cardImportService.getValue()));
        });
        cardImportService.setOnFailed(event -> {
            log.error("Error importing keystore from card", event.getSource().getException());
            Throwable rootCause = Throwables.getRootCause(event.getSource().getException());
            if(rootCause instanceof CardAuthorizationException) {
                setError("Import Error", "Incorrect PIN code, try again:");
            } else {
                setError("Import Error", rootCause.getMessage());
            }
        });
        cardImportService.start();
    }

    @Override
    protected void setError(String title, String detail) {
        if(!initialized) {
            super.setError(title, detail);
        } else {
            super.setError(title, null);
            errorText.set(detail);
            setContent(getPinEntry());
            setExpanded(true);
        }
    }

    private Node getInitializationPanel() {
        VBox initTypeBox = new VBox(5);
        RadioButton automatic = new RadioButton("Automatic (Recommended)");
        RadioButton advanced = new RadioButton("Advanced");
        TextField entropy = new TextField();
        entropy.setPromptText("Enter input for chain code");
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
            byte[] chainCode = toggleGroup.getSelectedToggle() == automatic ? null : Sha256Hash.hashTwice(entropy.getText().getBytes(StandardCharsets.UTF_8));
            CardInitializationService cardInitializationService = new CardInitializationService(importer, chainCode);
            cardInitializationService.setOnSucceeded(event1 -> {
                initialized = true;
                AppServices.showSuccessDialog("Card Initialized", "The card was successfully initialized.\n\nYou will now need to enter the PIN code found on the back. You can change the PIN code once it has been imported.");
                setDescription("Enter PIN code");
                setContent(getPinEntry());
                setExpanded(true);
            });
            cardInitializationService.setOnFailed(event1 -> {
                Throwable e = event1.getSource().getException();
                log.error("Error initializing card", e);
                AppServices.showErrorDialog("Card Initialization Failed", "The card was not initialized.\n\n" + e.getMessage());
            });
            cardInitializationService.start();
        });

        HBox contentBox = new HBox(20);
        contentBox.getChildren().addAll(initTypeBox, initializeButton);
        contentBox.setPadding(new Insets(10, 30, 10, 30));
        HBox.setHgrow(initTypeBox, Priority.ALWAYS);

        return contentBox;
    }

    private Node getPinEntry() {
        VBox vBox = new VBox();

        if(!errorText.get().isEmpty()) {
            Node errorBox = getContentBox(errorText.get());
            if(errorBox instanceof HBox hBox && hBox.getPrefHeight() == 60) {
                hBox.setPrefHeight(50);
            }
            vBox.getChildren().add(errorBox);
        }

        CustomPasswordField pinField = new ViewPasswordField();
        pinField.setPromptText("PIN Code");
        pinField.setText(pin.get());
        importButton.setDefaultButton(true);
        pin.bind(pinField.textProperty());
        HBox.setHgrow(pinField, Priority.ALWAYS);

        HBox contentBox = new HBox();
        contentBox.setAlignment(Pos.TOP_RIGHT);
        contentBox.setSpacing(20);
        contentBox.getChildren().add(pinField);
        contentBox.setPadding(new Insets(errorText.get().isEmpty() ? 10 : 0, 30, 10, 30));
        contentBox.setPrefHeight(50);

        vBox.getChildren().add(contentBox);

        return vBox;
    }

    public static class CardInitializationService extends Service<Void> {
        private final KeystoreCardImport cardImport;
        private final byte[] chainCode;

        public CardInitializationService(KeystoreCardImport cardImport, byte[] chainCode) {
            this.cardImport = cardImport;
            this.chainCode = chainCode;
        }

        @Override
        protected Task<Void> createTask() {
            return new Task<>() {
                @Override
                protected Void call() throws Exception {
                    cardImport.initialize(chainCode);
                    return null;
                }
            };
        }
    }

    public static class CardImportService extends Service<Keystore> {
        private final KeystoreCardImport cardImport;
        private final String pin;
        private final List<ChildNumber> derivation;

        public CardImportService(KeystoreCardImport cardImport, String pin, List<ChildNumber> derivation) {
            this.cardImport = cardImport;
            this.pin = pin;
            this.derivation = derivation;
        }

        @Override
        protected Task<Keystore> createTask() {
            return new Task<>() {
                @Override
                protected Keystore call() throws Exception {
                    cardImport.messageProperty().addListener((observable, oldValue, newValue) -> {
                        updateMessage(newValue);
                    });
                    return cardImport.getKeystore(pin, derivation);
                }
            };
        }
    }
}
