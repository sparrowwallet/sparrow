package com.sparrowwallet.sparrow.control;

import com.google.common.eventbus.Subscribe;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.client.j2se.MatrixToImageConfig;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.sparrowwallet.drongo.BitcoinUnit;
import com.sparrowwallet.drongo.OsType;
import com.sparrowwallet.drongo.OutputDescriptor;
import com.sparrowwallet.drongo.SecureString;
import com.sparrowwallet.drongo.address.Address;
import com.sparrowwallet.drongo.address.InvalidAddressException;
import com.sparrowwallet.drongo.protocol.ScriptType;
import com.sparrowwallet.drongo.protocol.Transaction;
import com.sparrowwallet.drongo.psbt.PSBT;
import com.sparrowwallet.drongo.psbt.PSBTParseException;
import com.sparrowwallet.drongo.psbt.PSBTSignatureException;
import com.sparrowwallet.drongo.silentpayments.SilentPaymentAddress;
import com.sparrowwallet.drongo.wallet.Keystore;
import com.sparrowwallet.drongo.wallet.KeystoreSource;
import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.drongo.wallet.WalletNode;
import com.sparrowwallet.hummingbird.registry.CryptoPSBT;
import com.sparrowwallet.sparrow.AppServices;
import com.sparrowwallet.sparrow.EventManager;
import com.sparrowwallet.sparrow.UnitFormat;
import com.sparrowwallet.sparrow.event.FeeRatesUpdatedEvent;
import com.sparrowwallet.sparrow.event.KeystoreDeviceRegistrationsChangedEvent;
import com.sparrowwallet.sparrow.event.StorageEvent;
import com.sparrowwallet.sparrow.event.TimedEvent;
import com.sparrowwallet.sparrow.event.UsbDeviceEvent;
import com.sparrowwallet.sparrow.event.WalletEntryLabelsChangedEvent;
import com.sparrowwallet.sparrow.glyphfont.FontAwesome5;
import com.sparrowwallet.sparrow.glyphfont.FontAwesome5Brands;
import com.sparrowwallet.sparrow.glyphfont.GlyphUtils;
import com.sparrowwallet.sparrow.io.Config;
import com.sparrowwallet.sparrow.io.Device;
import com.sparrowwallet.sparrow.io.Hwi;
import com.sparrowwallet.sparrow.io.Storage;
import com.sparrowwallet.sparrow.io.TimelockRecoveryPdf;
import com.sparrowwallet.sparrow.io.bbqr.BBQR;
import com.sparrowwallet.sparrow.io.bbqr.BBQRType;
import com.sparrowwallet.sparrow.timelockrecovery.TimelockCancellationPlan;
import com.sparrowwallet.sparrow.timelockrecovery.TimelockRecovery;
import com.sparrowwallet.sparrow.timelockrecovery.TimelockRecoveryException;
import com.sparrowwallet.sparrow.timelockrecovery.TimelockRecoveryPlan;
import com.sparrowwallet.sparrow.timelockrecovery.TimelockRecoveryRecipient;
import com.sparrowwallet.sparrow.wallet.NodeEntry;
import com.sparrowwallet.sparrow.wallet.WalletForm;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.event.Event;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TablePosition;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.util.Callback;
import javafx.util.converter.DefaultStringConverter;
import org.controlsfx.glyphfont.Glyph;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.util.Objects;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public class TimelockRecoveryDialog extends Dialog<Boolean> {
    private static final Logger log = LoggerFactory.getLogger(TimelockRecoveryDialog.class);

    private enum Page {
        CONFIGURE, SIGN_INITIATION, SIGN_RECOVERY, SIGN_CANCELLATION, EXPORT
    }

    private enum SignedTx {
        INITIATION("Initiation"), RECOVERY("Recovery"), CANCELLATION("Cancellation");

        private final String displayName;

        SignedTx(String displayName) {
            this.displayName = displayName;
        }

        public String displayName() {
            return displayName;
        }
    }

    private final WalletForm walletForm;
    private final Wallet wallet;
    private final BitcoinUnit bitcoinUnit;
    private final UnitFormat unitFormat;

    private Page page = Page.CONFIGURE;
    private final WalletNode initiationNode;
    private TimelockRecovery recovery;
    private Instant createdAt;
    private String planId;
    private boolean exported;
    private boolean eventBusRegistered;

    private final ObservableList<RecipientRow> recipientRows = FXCollections.observableArrayList();
    private final Spinner<Integer> daysSpinner = new Spinner<>();
    private final FeeRangeSlider feeRange = new FeeRangeSlider();
    private final CopyableLabel feeRateLabel = new CopyableLabel();
    private final CheckBox cancellationCheck = new CheckBox("Create a cancellation transaction");
    private final Label previewLabel = new Label();
    private final Label frozenWarning = new Label();
    private final Label errorLabel = new Label();
    private final VBox verifyAddressBox = new VBox(8);
    private final Label verifyAddressHeading = new Label();
    private final CopyableTextField verifyAddressField = new CopyableTextField();
    private final ImageView verifyAddressQr = new ImageView();
    private final Button displayAddressButton = new Button("Display Address");
    private QRDisplayDialog verifyAddressQrDialog;
    private WalletNode verifyNode;

    private final HBox stepBar = new HBox(12);
    private final StackPane pages = new StackPane();
    private final VBox configurePage = new VBox(12);
    private final VBox signPage = new VBox(12);
    private final VBox exportPage = new VBox(12);

    private final Label signTitle = new Label();
    private final Label signExplanation = new Label();
    private final TransactionDiagram transactionDiagram = new TransactionDiagram();
    private final Label signStatus = new Label();
    private final Button signButton = new Button();
    private final Button showPsbtButton = new Button();
    private final Button loadPsbtButton = new Button();
    private final Button savePsbtButton = new Button();

    private final CheckBox agreementCheck = new CheckBox("I understand that this plan is broken if I keep using this wallet (new deposits are uncovered, and spending invalidates it).");
    private final Button saveRecoveryPdfButton = new Button("Save Recovery Guide PDF...");
    private final Button saveRecoveryJsonButton = new Button("Save BIP-128 JSON...");
    private final Button saveCancellationPdfButton = new Button("Save Cancellation Guide PDF...");
    private final Button saveCancellationJsonButton = new Button("Save Cancellation JSON...");

    private final Button backButton = new Button("Back");
    private final Button nextButton = new Button("Continue");

    public TimelockRecoveryDialog(WalletForm walletForm) {
        this.walletForm = walletForm;
        this.wallet = walletForm.getWallet();
        this.initiationNode = TimelockRecovery.reserveInitiationNode(wallet);
        BitcoinUnit unit = Config.get().getBitcoinUnit();
        this.bitcoinUnit = unit == BitcoinUnit.AUTO ? wallet.getAutoUnit() : unit;
        this.unitFormat = Config.get().getUnitFormat() == null ? UnitFormat.DOT : Config.get().getUnitFormat();

        final DialogPane dialogPane = getDialogPane();
        dialogPane.getStylesheets().add(AppServices.class.getResource("general.css").toExternalForm());
        dialogPane.getStylesheets().add(AppServices.class.getResource("dialog.css").toExternalForm());
        dialogPane.getStylesheets().add(AppServices.class.getResource("wallet/wallet.css").toExternalForm());
        dialogPane.getStylesheets().add(AppServices.class.getResource("wallet/send.css").toExternalForm());
        dialogPane.getStylesheets().add(AppServices.class.getResource("wallet/receive.css").toExternalForm());
        AppServices.setStageIcon(dialogPane.getScene().getWindow());
        setTitle("Timelock Recovery");
        Label headerLabel = new Label("Create a pre-signed Initiation, Recovery, and optional Cancellation plan");
        headerLabel.setWrapText(true);
        headerLabel.setStyle("-fx-font-size: 24px;");
        ImageView timelockLogo = new ImageView(new Image(
                TimelockRecoveryDialog.class.getResource("/com/sparrowwallet/sparrow/timelockrecovery/timelock_recovery_820.png").toExternalForm()));
        timelockLogo.setFitWidth(DialogImage.WIDTH);
        timelockLogo.setFitHeight(DialogImage.HEIGHT);
        timelockLogo.setPreserveRatio(true);
        HBox headerLogos = new HBox(8, timelockLogo, new DialogImage(DialogImage.Type.SPARROW));
        headerLogos.setAlignment(Pos.CENTER_RIGHT);
        BorderPane header = new BorderPane();
        header.setLeft(headerLabel);
        header.setRight(headerLogos);
        BorderPane.setAlignment(headerLabel, Pos.CENTER_LEFT);
        BorderPane.setAlignment(headerLogos, Pos.CENTER_RIGHT);
        header.setPadding(new Insets(20));
        header.setStyle("-fx-background-color: -fx-control-inner-background;");
        header.setMaxWidth(Double.MAX_VALUE);
        headerLabel.maxWidthProperty().bind(Bindings.createDoubleBinding(
                () -> Math.max(200, header.getWidth() - headerLogos.getWidth() - 64),
                header.widthProperty(), headerLogos.widthProperty()));
        dialogPane.setHeader(header);

        recipientRows.add(new RecipientRow("", "", "", true));
        buildConfigurePage();
        buildSignPage();
        buildExportPage();
        pages.getChildren().addAll(configurePage, signPage, exportPage);

        VBox root = new VBox(16);
        root.setPadding(new Insets(12, 20, 16, 20));
        stepBar.setAlignment(Pos.CENTER_LEFT);
        previewLabel.setWrapText(true);
        errorLabel.setWrapText(true);
        errorLabel.getStyleClass().add("failure");
        HBox nav = new HBox(10);
        nav.setAlignment(Pos.CENTER_RIGHT);
        HBox.setHgrow(nav, Priority.ALWAYS);
        nav.getChildren().addAll(backButton, nextButton);
        root.getChildren().addAll(stepBar, pages, errorLabel, nav);
        VBox.setVgrow(pages, Priority.ALWAYS);
        dialogPane.setContent(root);

        backButton.setOnAction(_ -> goBack());
        nextButton.setOnAction(_ -> goNext());
        nextButton.setDefaultButton(true);

        dialogPane.getButtonTypes().add(ButtonType.CLOSE);
        dialogPane.setPrefWidth(920);
        dialogPane.setPrefHeight(820);
        setResizable(true);
        AppServices.moveToActiveWindowScreen(this);
        AppServices.onEscapePressed(dialogPane.getScene(), this::closeDialog);

        setResultConverter(_ -> exported);
        setOnShown(_ -> {
            javafx.scene.Node closeButton = dialogPane.lookupButton(ButtonType.CLOSE);
            if(closeButton != null) {
                closeButton.setVisible(false);
                closeButton.setManaged(false);
            }
            javafx.scene.Node buttonBar = dialogPane.lookup(".button-bar");
            if(buttonBar != null) {
                buttonBar.setVisible(false);
                buttonBar.setManaged(false);
            }
            javafx.scene.Node headerPanel = dialogPane.lookup(".header-panel");
            if(headerPanel != null) {
                headerPanel.setStyle("-fx-background-color: -fx-control-inner-background;");
            }
        });

        EventManager.get().register(this);
        eventBusRegistered = true;
        setOnCloseRequest(event -> {
            if(!confirmClose()) {
                event.consume();
                return;
            }
            unregisterEventBus();
        });

        showPage(Page.CONFIGURE);
        rebuildDraft();
    }

    private void buildConfigurePage() {
        Label intro = new Label("Timelock Recovery pre-signs transactions to move funds after a delay if you lose access. Nothing is broadcast from Sparrow.");
        intro.setWrapText(true);
        Hyperlink site = new Hyperlink(TimelockRecovery.SITE_URL);
        site.setOnAction(_ -> AppServices.get().getApplication().getHostServices().showDocument(TimelockRecovery.SITE_URL));
        Button help = new Button("Help...");
        help.setOnAction(_ -> showHelp());
        HBox introBox = new HBox(10, site, help);
        introBox.setAlignment(Pos.CENTER_LEFT);

        TableView<RecipientRow> table = new TableView<>(recipientRows);
        table.setEditable(true);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setMinHeight(140);
        table.setPrefHeight(180);

        TableColumn<RecipientRow, String> addressCol = new TableColumn<>("Address");
        addressCol.setCellValueFactory(data -> data.getValue().addressProperty());
        addressCol.setCellFactory(_ -> new RecipientTextCell());
        addressCol.setOnEditCommit(event -> {
            event.getRowValue().setAddress(event.getNewValue());
            rebuildDraft();
        });
        addressCol.setPrefWidth(360);

        TableColumn<RecipientRow, String> amountCol = new TableColumn<>("Amount (" + bitcoinUnit.getLabel() + ")");
        amountCol.setCellValueFactory(data -> data.getValue().amountProperty());
        amountCol.setCellFactory(remainingAmountCell());
        amountCol.setOnEditCommit(event -> {
            event.getRowValue().setAmount(event.getNewValue());
            rebuildDraft();
        });
        amountCol.setPrefWidth(160);

        TableColumn<RecipientRow, String> labelCol = new TableColumn<>("Label");
        labelCol.setCellValueFactory(data -> data.getValue().labelProperty());
        labelCol.setCellFactory(_ -> new RecipientTextCell());
        labelCol.setOnEditCommit(event -> event.getRowValue().setLabel(event.getNewValue()));
        table.getColumns().add(addressCol);
        table.getColumns().add(amountCol);
        table.getColumns().add(labelCol);
        table.editingCellProperty().addListener((_, _, editing) -> nextButton.setDefaultButton(editing == null));

        Button addRecipient = new Button("Add Recipient");
        addRecipient.setOnAction(_ -> {
            if(!recipientRows.isEmpty()) {
                recipientRows.getLast().setRemaining(false);
            }
            recipientRows.add(new RecipientRow("", "", "", true));
            table.refresh();
            rebuildDraft();
        });
        Button removeRecipient = new Button("Remove");
        removeRecipient.setOnAction(_ -> {
            RecipientRow selected = table.getSelectionModel().getSelectedItem();
            if(selected == null || recipientRows.size() <= 1) {
                return;
            }
            recipientRows.remove(selected);
            recipientRows.getLast().setRemaining(true);
            recipientRows.getLast().setAmount("");
            table.refresh();
            rebuildDraft();
        });
        HBox recipientButtons = new HBox(8, addRecipient, removeRecipient);

        daysSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(
                TimelockRecovery.MIN_TIMELOCK_DAYS, TimelockRecovery.MAX_TIMELOCK_DAYS, TimelockRecovery.DEFAULT_TIMELOCK_DAYS));
        daysSpinner.setEditable(true);
        daysSpinner.valueProperty().addListener((_, _, _) -> rebuildDraft());
        Label daysNote = new Label("BIP-68 time locks use median time past (MTP), so the wait is not a wall-clock guarantee.");
        daysNote.setWrapText(true);
        daysNote.setStyle("-fx-font-size: 11px;");

        feeRange.setMaxWidth(360);
        feeRange.valueProperty().addListener((_, _, _) -> {
            updateFeeRateLabel();
            rebuildDraft();
        });
        feeRange.setFeeRate(defaultTimelockFeeRate());
        updateFeeRateLabel();

        cancellationCheck.setSelected(false);
        cancellationCheck.selectedProperty().addListener((_, _, _) -> rebuildDraft());
        Label cancellationNote = wrappingNote("If the Initiation transaction is broadcast against your intention, the Cancellation transaction spends that new output back into this wallet immediately (unlike the Recovery transaction which must wait for the timelock).");
        Label cancellationWarning = wrappingNote("If the keys to this wallet are lost, do not reveal the Cancellation transaction to anyone, as it could return funds to the same wallet.");
        VBox cancellationNotes = new VBox(4, cancellationNote, cancellationWarning);
        cancellationNotes.setMaxWidth(Double.MAX_VALUE);
        cancellationNotes.setMinHeight(Region.USE_PREF_SIZE);

        int frozen = TimelockRecovery.frozenUtxoCount(wallet);
        if(frozen > 0) {
            frozenWarning.setText("Warning: " + frozen + " frozen UTXO" + (frozen == 1 ? " is" : "s are") + " omitted from the Initiation transaction.");
            frozenWarning.setGraphic(GlyphUtils.getWarningGlyph());
            frozenWarning.setWrapText(true);
        } else {
            frozenWarning.setVisible(false);
            frozenWarning.setManaged(false);
        }

        buildVerifyAddressBox();

        configurePage.getChildren().addAll(intro, introBox, verifyAddressBox,
                labeled("Destinations (last amount is the leftover after the Recovery fee):", table),
                recipientButtons,
                labeled("Cancellation period (2-388 days):", daysSpinner), daysNote,
                labeled("Fee rate (applied to Initiation, Recovery, and Cancellation transactions) - use a high rate so they still confirm far in the future:", feeRange), feeRateLabel,
                cancellationCheck, cancellationNotes, frozenWarning, previewLabel);
    }

    private void buildVerifyAddressBox() {
        verifyAddressHeading.setWrapText(true);

        verifyAddressField.setEditable(false);
        verifyAddressField.setSkin(new AddressTextFieldSkin(verifyAddressField));
        verifyAddressField.getStyleClass().add("address-text-field");
        HBox.setHgrow(verifyAddressField, Priority.ALWAYS);

        Glyph usb = new Glyph(FontAwesome5Brands.FONT_NAME, FontAwesome5Brands.Glyph.USB);
        usb.setFontSize(12);
        displayAddressButton.setGraphic(usb);
        displayAddressButton.setGraphicTextGap(5);
        displayAddressButton.managedProperty().bind(displayAddressButton.visibleProperty());
        displayAddressButton.setOnAction(_ -> displayVerifyAddress());

        verifyAddressQr.getStyleClass().add("qr-code");
        verifyAddressQr.setFitWidth(130);
        verifyAddressQr.setFitHeight(130);
        verifyAddressQr.setPreserveRatio(true);
        verifyAddressQr.setOnMouseClicked(_ -> showVerifyAddressQr());

        VBox addressControls = new VBox(8, verifyAddressField, displayAddressButton);
        addressControls.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(addressControls, Priority.ALWAYS);

        HBox addressRow = new HBox(16, addressControls, verifyAddressQr);
        addressRow.setAlignment(Pos.CENTER_LEFT);

        verifyAddressBox.managedProperty().bind(verifyAddressBox.visibleProperty());
        verifyAddressBox.setVisible(false);
        verifyAddressBox.getChildren().addAll(verifyAddressHeading, addressRow);
    }

    private void placeVerifyAddressBox(boolean onSignPage) {
        if(verifyAddressBox.getParent() instanceof javafx.scene.layout.Pane parent) {
            parent.getChildren().remove(verifyAddressBox);
        }
        if(onSignPage) {
            int index = signPage.getChildren().indexOf(transactionDiagram);
            signPage.getChildren().add(index < 0 ? signPage.getChildren().size() : index, verifyAddressBox);
        } else {
            configurePage.getChildren().add(Math.min(2, configurePage.getChildren().size()), verifyAddressBox);
        }
        updateVerifyAddressDisplay();
    }

    private void buildSignPage() {
        signTitle.setStyle("-fx-font-size: 20px; -fx-font-weight: bold;");
        signExplanation.setWrapText(true);
        transactionDiagram.setId("transactionDiagram");
        HBox actions = new HBox(8, signButton, showPsbtButton, loadPsbtButton, savePsbtButton);
        signButton.setOnAction(_ -> signCurrent());
        showPsbtButton.setOnAction(_ -> showCurrentPsbt());
        loadPsbtButton.setOnAction(_ -> loadCurrentPsbt());
        savePsbtButton.setOnAction(_ -> saveCurrentPsbt());
        signPage.getChildren().addAll(signTitle, signExplanation, transactionDiagram, signStatus, actions);
    }

    private void buildExportPage() {
        Label title = new Label("Export");
        title.setStyle("-fx-font-size: 20px; -fx-font-weight: bold;");
        Label explanation = new Label("Save the heir-facing Recovery Guide and BIP-128 JSON. If you created a Cancellation transaction, also save the owner-only Cancellation Guide. These transactions are never broadcast from Sparrow.");
        explanation.setWrapText(true);
        agreementCheck.setWrapText(true);
        agreementCheck.selectedProperty().addListener((_, _, _) -> updateExportButtons());
        saveRecoveryPdfButton.setOnAction(_ -> saveRecoveryPdf());
        saveRecoveryJsonButton.setOnAction(_ -> saveRecoveryJson());
        saveCancellationPdfButton.setOnAction(_ -> saveCancellationPdf());
        saveCancellationJsonButton.setOnAction(_ -> saveCancellationJson());
        HBox recoveryButtons = new HBox(8, saveRecoveryPdfButton, saveRecoveryJsonButton);
        HBox cancellationButtons = new HBox(8, saveCancellationPdfButton, saveCancellationJsonButton);
        exportPage.getChildren().addAll(title, explanation, agreementCheck, recoveryButtons, cancellationButtons);
    }

    private void showPage(Page next) {
        this.page = next;
        configurePage.setVisible(next == Page.CONFIGURE);
        configurePage.setManaged(next == Page.CONFIGURE);
        boolean signing = next == Page.SIGN_INITIATION || next == Page.SIGN_RECOVERY || next == Page.SIGN_CANCELLATION;
        signPage.setVisible(signing);
        signPage.setManaged(signing);
        exportPage.setVisible(next == Page.EXPORT);
        exportPage.setManaged(next == Page.EXPORT);
        updateStepBar();
        backButton.setVisible(next != Page.CONFIGURE);
        backButton.setManaged(next != Page.CONFIGURE);
        if(next == Page.EXPORT) {
            nextButton.setText("Done");
            nextButton.setDisable(!exported);
            updateExportButtons();
            verifyAddressBox.setVisible(false);
        } else if(signing) {
            nextButton.setText("Continue");
            placeVerifyAddressBox(next == Page.SIGN_INITIATION || next == Page.SIGN_CANCELLATION);
            updateSignPage();
        } else {
            nextButton.setText("Continue");
            nextButton.setDisable(recovery == null);
            placeVerifyAddressBox(false);
        }
        errorLabel.setText("");
    }

    private void updateStepBar() {
        stepBar.getChildren().clear();
        addStep("1. Initiation", Page.SIGN_INITIATION, recovery != null && recovery.getSignedInitiationTx() != null);
        stepBar.getChildren().add(stepSeparator());
        addStep("2. Recovery", Page.SIGN_RECOVERY, recovery != null && recovery.getSignedRecoveryTx() != null);
        if(cancellationCheck.isSelected() || (recovery != null && recovery.hasCancellation())) {
            stepBar.getChildren().add(stepSeparator());
            addStep("3. Cancellation", Page.SIGN_CANCELLATION, recovery != null && recovery.getSignedCancellationTx() != null);
        }
        stepBar.getChildren().add(stepSeparator());
        addStep("Export", Page.EXPORT, exported);
    }

    private void addStep(String text, Page stepPage, boolean complete) {
        Label label = new Label(text);
        boolean current = page == stepPage || (page == Page.CONFIGURE && stepPage == Page.SIGN_INITIATION);
        if(complete) {
            label.setGraphic(GlyphUtils.getSuccessGlyph());
        }
        if(current) {
            label.setStyle("-fx-font-weight: bold;");
        }
        stepBar.getChildren().add(label);
    }

    private static Label stepSeparator() {
        return new Label("→");
    }

    private void goBack() {
        switch(page) {
            case SIGN_INITIATION -> showPage(Page.CONFIGURE);
            case SIGN_RECOVERY -> showPage(Page.SIGN_INITIATION);
            case SIGN_CANCELLATION -> showPage(Page.SIGN_RECOVERY);
            case EXPORT -> showPage(recovery != null && recovery.hasCancellation() ? Page.SIGN_CANCELLATION : Page.SIGN_RECOVERY);
            default -> {
            }
        }
    }

    private void goNext() {
        try {
            switch(page) {
                case CONFIGURE -> {
                    if(recovery == null) {
                        rebuildDraft();
                    }
                    if(recovery == null) {
                        return;
                    }
                    persistLabels();
                    showPage(Page.SIGN_INITIATION);
                }
                case SIGN_INITIATION -> {
                    if(recovery.getSignedInitiationTx() == null) {
                        recovery.extractSignedInitiation();
                    }
                    showPage(Page.SIGN_RECOVERY);
                }
                case SIGN_RECOVERY -> {
                    if(recovery.getSignedRecoveryTx() == null) {
                        recovery.extractSignedRecovery();
                    }
                    showPage(recovery.hasCancellation() ? Page.SIGN_CANCELLATION : Page.EXPORT);
                }
                case SIGN_CANCELLATION -> {
                    if(recovery.getSignedCancellationTx() == null) {
                        recovery.extractSignedCancellation();
                    }
                    ensurePlanMetadata();
                    showPage(Page.EXPORT);
                }
                case EXPORT -> closeDialog();
            }
        } catch(TimelockRecoveryException e) {
            errorLabel.setText(e.getMessage());
        }
    }

    private void persistLabels() {
        List<com.sparrowwallet.sparrow.wallet.Entry> entries = new ArrayList<>();
        entries.add(new NodeEntry(wallet, recovery.getInitiationNode()));
        if(recovery.getCancellationNode() != null) {
            entries.add(new NodeEntry(wallet, recovery.getCancellationNode()));
        }
        EventManager.get().post(new WalletEntryLabelsChangedEvent(wallet, entries));
    }

    private void rebuildDraft() {
        if(page != Page.CONFIGURE) {
            return;
        }
        try {
            List<TimelockRecoveryRecipient> recipients = parseRecipients();
            if(recipients.isEmpty()) {
                recovery = null;
                updateVerifyAddressDisplay();
                previewLabel.setText("Enter at least one destination address.");
                nextButton.setDisable(true);
                return;
            }
            Integer days = daysSpinner.getValue();
            if(days == null) {
                nextButton.setDisable(true);
                return;
            }
            Integer height = AppServices.getCurrentBlockHeight();
            if(height == null) {
                height = wallet.getStoredBlockHeight();
            }
            recovery = TimelockRecovery.create(wallet, recipients, days, feeRange.getFeeRate(),
                    cancellationCheck.isSelected(), height);
            createdAt = null;
            planId = null;
            exported = false;
            updateVerifyAddressDisplay();
            previewLabel.setText(previewText());
            errorLabel.setText("");
            nextButton.setDisable(false);
        } catch(Exception e) {
            recovery = null;
            updateVerifyAddressDisplay();
            previewLabel.setText("");
            errorLabel.setText(e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
            nextButton.setDisable(true);
        }
    }

    private String previewText() {
        StringBuilder builder = new StringBuilder();
        builder.append("Initiation fee: ").append(formatAmount(recovery.getInitiationWalletTx().getFee())).append("\n");
        builder.append("Recovery fee: ").append(formatAmount(recovery.getRecoveryWalletTx().getFee()));
        if(recovery.hasCancellation()) {
            builder.append("\nCancellation address: ").append(recovery.getCancellationAddress());
            builder.append("\nCancellation fee: ").append(formatAmount(recovery.getCancellationWalletTx().getFee()));
        }
        builder.append("\nRecovery outputs:");
        for(var payment : recovery.getRecoveryWalletTx().getPayments()) {
            builder.append("\n• ").append(payment.getAddress()).append("  ").append(formatAmount(payment.getAmount()));
            if(payment.getLabel() != null && !payment.getLabel().isBlank()) {
                builder.append("  (").append(payment.getLabel()).append(")");
            }
        }
        return builder.toString();
    }

    private List<TimelockRecoveryRecipient> parseRecipients() throws TimelockRecoveryException {
        List<TimelockRecoveryRecipient> recipients = new ArrayList<>();
        int lastIndex = lastNonEmptyIndex();
        for(int i = 0; i < recipientRows.size(); i++) {
            RecipientRow row = recipientRows.get(i);
            String addressText = row.getAddress() == null ? "" : row.getAddress().trim();
            if(addressText.isEmpty()) {
                continue;
            }
            Address address = parseDestination(addressText);
            String label = row.getLabel() == null || row.getLabel().isBlank() ? null : row.getLabel().trim();
            boolean remaining = row.isRemaining() || i == lastIndex;
            if(remaining) {
                recipients.add(TimelockRecoveryRecipient.remaining(address, label));
            } else {
                Long amount = parseAmount(row.getAmount());
                if(amount == null) {
                    throw new TimelockRecoveryException("Enter an amount for every destination except the last");
                }
                recipients.add(TimelockRecoveryRecipient.of(address, label, amount));
            }
        }
        return recipients;
    }

    private int lastNonEmptyIndex() {
        for(int i = recipientRows.size() - 1; i >= 0; i--) {
            if(recipientRows.get(i).getAddress() != null && !recipientRows.get(i).getAddress().isBlank()) {
                return i;
            }
        }
        return -1;
    }

    private Address parseDestination(String value) throws TimelockRecoveryException {
        try {
            SilentPaymentAddress.from(value);
            throw new TimelockRecoveryException("Silent payment addresses cannot be used as Timelock Recovery destinations");
        } catch(TimelockRecoveryException e) {
            throw e;
        } catch(Exception ignored) {
            // Not a silent payment address.
        }
        try {
            Address address = Address.fromString(value);
            if(wallet.isWalletAddress(address)) {
                throw new TimelockRecoveryException("Recovery destinations must not belong to this wallet: " + address);
            }
            return address;
        } catch(InvalidAddressException e) {
            throw new TimelockRecoveryException("Invalid destination address: " + value);
        }
    }

    private Long parseAmount(String raw) {
        if(raw == null || raw.isBlank() || "remaining".equalsIgnoreCase(raw.trim())) {
            return null;
        }
        String groupingStripped = raw.trim().replace(unitFormat.getGroupingSeparator(), "");
        try {
            if(bitcoinUnit == BitcoinUnit.BTC) {
                String normalised = groupingStripped.replace(unitFormat.getDecimalSeparator(), ".");
                return bitcoinUnit.getSatsValue(Double.parseDouble(normalised));
            }
            return Long.parseLong(groupingStripped);
        } catch(NumberFormatException e) {
            return null;
        }
    }

    private void updateSignPage() {
        SignedTx tx = currentSignedTx();
        signTitle.setText("Sign " + tx.displayName() + " Transaction");
        signExplanation.setText(explanation(tx));
        signButton.setText("Sign " + tx.displayName() + " Transaction");
        showPsbtButton.setText("Show " + tx.displayName() + " PSBT");
        loadPsbtButton.setText("Load signed " + tx.displayName() + " PSBT");
        savePsbtButton.setText("Save " + tx.displayName() + " PSBT");
        signButton.setGraphic(signGlyph());
        PSBT psbt = currentPsbt();
        transactionDiagram.setFinal(isCurrentSigned());
        transactionDiagram.update(currentWalletTx());
        boolean signed = isCurrentSigned();
        signStatus.setText(signed ? tx.displayName() + " transaction is fully signed." : awaitingSignaturesText(tx, psbt));
        signButton.setDisable(signed);
        nextButton.setDisable(!signed && (psbt == null || !psbt.isSigned()));
    }

    private String awaitingSignaturesText(SignedTx tx, PSBT psbt) {
        String text = "Awaiting signatures for the " + tx.displayName() + " transaction";
        int required = wallet.getDefaultPolicy().getNumSignaturesRequired();
        if(wallet.getKeystores().size() > 1) {
            text += " (" + collectedSignatures(psbt) + " of " + required + ")";
        }
        return text + ".";
    }

    private int collectedSignatures(PSBT psbt) {
        if(psbt == null) {
            return 0;
        }
        return wallet.getSignedKeystores(psbt).values().stream().mapToInt(Map::size).min().orElse(0);
    }

    private String explanation(SignedTx tx) {
        return switch(tx) {
            case INITIATION -> "Sends most funds back to this wallet, except for 600 sats to each destination (CPFP anchors).";
            case RECOVERY -> "After the timelock, sends the Initiation transaction’s large UTXO to the destination(s). Relative locktime is "
                    + daysSpinner.getValue() + " days (nSequence " + String.format(Locale.ROOT, "0x%08X", recovery.getRelativeSequence()) + ").";
            case CANCELLATION -> "Spends the Initiation transaction's large UTXO back into this wallet with no timelock. Owner-only.";
        };
    }

    private SignedTx currentSignedTx() {
        return switch(page) {
            case SIGN_RECOVERY -> SignedTx.RECOVERY;
            case SIGN_CANCELLATION -> SignedTx.CANCELLATION;
            default -> SignedTx.INITIATION;
        };
    }

    private PSBT currentPsbt() {
        if(recovery == null) {
            return null;
        }
        return switch(currentSignedTx()) {
            case INITIATION -> recovery.getInitiationPsbt();
            case RECOVERY -> recovery.getRecoveryPsbt();
            case CANCELLATION -> recovery.getCancellationPsbt();
        };
    }

    private com.sparrowwallet.drongo.wallet.WalletTransaction currentWalletTx() {
        return switch(currentSignedTx()) {
            case INITIATION -> recovery.getInitiationWalletTx();
            case RECOVERY -> recovery.getRecoveryWalletTx();
            case CANCELLATION -> recovery.getCancellationWalletTx();
        };
    }

    private boolean isCurrentSigned() {
        return switch(currentSignedTx()) {
            case INITIATION -> recovery.getSignedInitiationTx() != null;
            case RECOVERY -> recovery.getSignedRecoveryTx() != null;
            case CANCELLATION -> recovery.getSignedCancellationTx() != null;
        };
    }

    private void signCurrent() {
        signSoftwareKeystores();
        signDeviceKeystores();
    }

    private void signSoftwareKeystores() {
        PSBT psbt = currentPsbt();
        if(psbt == null || psbt.isSigned() || wallet.getKeystores().stream().noneMatch(Keystore::hasPrivateKey)) {
            return;
        }

        Wallet copy = wallet.copy();
        String walletId = walletForm.getWalletId();
        if(copy.isEncrypted()) {
            WalletPasswordDialog dlg = new WalletPasswordDialog(copy.getMasterName(), WalletPasswordDialog.PasswordRequirement.LOAD);
            dlg.setTitle("Sign " + currentSignedTx().displayName() + " Transaction");
            dlg.getDialogPane().setHeaderText("Enter the wallet password to sign the " + currentSignedTx().displayName() + " transaction:");
            dlg.initOwner(getDialogPane().getScene().getWindow());
            Optional<SecureString> password = dlg.showAndWait();
            if(password.isPresent()) {
                Storage.DecryptWalletService decryptWalletService = new Storage.DecryptWalletService(copy, password.get());
                decryptWalletService.setOnSucceeded(_ -> {
                    EventManager.get().post(new StorageEvent(walletId, TimedEvent.Action.END, "Done"));
                    signUnencrypted(decryptWalletService.getValue());
                });
                decryptWalletService.setOnFailed(_ -> {
                    EventManager.get().post(new StorageEvent(walletId, TimedEvent.Action.END, "Failed"));
                    AppServices.showErrorDialog("Incorrect Password", decryptWalletService.getException().getMessage());
                });
                EventManager.get().post(new StorageEvent(walletId, TimedEvent.Action.START, "Decrypting wallet..."));
                decryptWalletService.start();
            }
        } else {
            signUnencrypted(copy);
        }
    }

    private void signUnencrypted(Wallet unencryptedWallet) {
        try {
            unencryptedWallet.sign(currentPsbt());
            maybeExtract();
            updateSignPage();
        } catch(Exception e) {
            log.warn("Failed to sign " + currentSignedTx().displayName(), e);
            AppServices.showErrorDialog("Failed to Sign", e.getMessage());
        }
    }

    private void signDeviceKeystores() {
        PSBT psbt = currentPsbt();
        if(psbt == null || psbt.isSigned()) {
            return;
        }
        List<String> fingerprints = wallet.getKeystores().stream().map(keystore -> keystore.getKeyDerivation().getMasterFingerprint()).collect(Collectors.toList());
        List<com.sparrowwallet.sparrow.io.Device> signingDevices = AppServices.getDevices().stream()
                .filter(device -> fingerprints.contains(device.getFingerprint())).collect(Collectors.toList());
        if(signingDevices.isEmpty() &&
                (wallet.getKeystores().stream().noneMatch(keystore -> keystore.getSource().equals(KeystoreSource.HW_USB) || keystore.getSource().equals(KeystoreSource.SW_WATCH) || keystore.getWalletModel().isCard()) ||
                        (wallet.getKeystores().stream().anyMatch(keystore -> keystore.getSource().equals(KeystoreSource.SW_SEED)) && wallet.getKeystores().stream().anyMatch(keystore -> keystore.getSource().equals(KeystoreSource.SW_WATCH))))) {
            return;
        }

        DeviceSignDialog dlg = new DeviceSignDialog(wallet, fingerprints, psbt);
        dlg.setTitle("Sign " + currentSignedTx().displayName() + " Transaction");
        dlg.getDialogPane().setHeaderText("Sign the " + currentSignedTx().displayName() + " transaction on your hardware wallet");
        dlg.initOwner(getDialogPane().getScene().getWindow());
        Optional<PSBT> optionalSignedPsbt = dlg.showAndWait();
        if(optionalSignedPsbt.isPresent()) {
            combineSigned(optionalSignedPsbt.get());
        }
    }

    private void combineSigned(PSBT signedPsbt) {
        PSBT current = currentPsbt();
        try {
            if(signedPsbt.isFinalized() && !current.isFinalized()) {
                current.copyFinalizedFields(signedPsbt);
            } else {
                current.verifyCombinedSignatures(signedPsbt);
                current.combine(signedPsbt);
            }
            maybeExtract();
            updateSignPage();
        } catch(PSBTSignatureException | IllegalArgumentException e) {
            AppServices.showErrorDialog("Invalid PSBT", e.getMessage());
        }
    }

    private void maybeExtract() {
        PSBT psbt = currentPsbt();
        if(psbt == null || !psbt.isSigned()) {
            return;
        }
        try {
            switch(currentSignedTx()) {
                case INITIATION -> recovery.extractSignedInitiation();
                case RECOVERY -> recovery.extractSignedRecovery();
                case CANCELLATION -> recovery.extractSignedCancellation();
            }
        } catch(TimelockRecoveryException e) {
            errorLabel.setText(e.getMessage());
        }
    }

    private void showCurrentPsbt() {
        PSBT psbt = currentPsbt();
        if(psbt == null) {
            return;
        }
        boolean addLegacyEncodingOption = wallet.getKeystores().stream().anyMatch(keystore -> keystore.getWalletModel().showLegacyQR());
        boolean addBbqrOption = wallet.getKeystores().stream().anyMatch(keystore -> keystore.getWalletModel().showBbqr());
        QREncoding encoding = wallet.getKeystores().stream().allMatch(keystore -> keystore.getWalletModel().selectBbqr()) ? QREncoding.BBQR : QREncoding.UR;
        boolean includeNonWitnessUtxos = currentSignedTx() != SignedTx.INITIATION || !Arrays.asList(ScriptType.WITNESS_TYPES).contains(wallet.getScriptType());
        byte[] psbtBytes = psbt.getForExport().serialize(true, includeNonWitnessUtxos);
        CryptoPSBT cryptoPSBT = new CryptoPSBT(psbtBytes);
        BBQR bbqr = addBbqrOption ? new BBQR(BBQRType.PSBT, psbtBytes) : null;
        QRDisplayDialog qrDisplayDialog = new QRDisplayDialog(cryptoPSBT.toUR(), bbqr, addLegacyEncodingOption, true, encoding);
        qrDisplayDialog.setTitle("Show " + currentSignedTx().displayName() + " PSBT");
        qrDisplayDialog.initOwner(getDialogPane().getScene().getWindow());
        Optional<ButtonType> optButtonType = qrDisplayDialog.showAndWait();
        if(optButtonType.isPresent() && optButtonType.get().getButtonData() == ButtonBar.ButtonData.OK_DONE) {
            scanCurrentPsbt();
        }
    }

    private void scanCurrentPsbt() {
        QRScanDialog qrScanDialog = new QRScanDialog();
        qrScanDialog.initOwner(getDialogPane().getScene().getWindow());
        Optional<QRScanDialog.Result> optionalResult = qrScanDialog.showAndWait();
        if(optionalResult.isPresent()) {
            QRScanDialog.Result result = optionalResult.get();
            if(result.psbt != null) {
                combineSigned(result.psbt);
            } else if(result.transaction != null) {
                applyExtractedTransaction(result.transaction);
            } else if(result.exception != null) {
                log.error("Error scanning QR", result.exception);
                AppServices.showErrorDialog("Error scanning QR", result.exception.getMessage());
            } else {
                AppServices.showErrorDialog("Invalid QR Code", "Cannot parse QR code into a PSBT or transaction.");
            }
        }
    }

    private void loadCurrentPsbt() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Load signed " + currentSignedTx().displayName() + " PSBT");
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("All Files", OsType.getCurrent().equals(OsType.UNIX) ? "*" : "*.*"),
                new FileChooser.ExtensionFilter("PSBT Files", "*.psbt")
        );
        Stage window = new Stage();
        AppServices.moveToActiveWindowScreen(window, 800, 450);
        File file = fileChooser.showOpenDialog(window);
        if(file == null) {
            return;
        }
        try {
            byte[] bytes = Files.readAllBytes(file.toPath());
            if(PSBT.isPSBT(bytes)) {
                combineSigned(new PSBT(bytes, false));
            } else {
                applyExtractedTransaction(new Transaction(bytes));
            }
        } catch(PSBTParseException | IOException e) {
            AppServices.showErrorDialog("Error loading PSBT", e.getMessage());
        }
    }

    private void saveCurrentPsbt() {
        PSBT psbt = currentPsbt();
        if(psbt == null) {
            return;
        }
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Save " + currentSignedTx().displayName() + " PSBT");
        fileChooser.setInitialFileName(currentSignedTx().displayName().toLowerCase(Locale.ROOT) + ".psbt");
        Stage window = new Stage();
        AppServices.moveToActiveWindowScreen(window, 800, 450);
        File file = fileChooser.showSaveDialog(window);
        if(file != null) {
            if(!file.getName().toLowerCase(Locale.ROOT).endsWith(".psbt")) {
                file = new File(file.getAbsolutePath() + ".psbt");
            }
            boolean includeNonWitnessUtxos = currentSignedTx() != SignedTx.INITIATION;
            try(FileOutputStream outputStream = new FileOutputStream(file)) {
                outputStream.write(psbt.getForExport().serialize(true, includeNonWitnessUtxos));
            } catch(IOException e) {
                log.error("Error saving PSBT", e);
                AppServices.showErrorDialog("Error saving PSBT", "Cannot write to " + file.getAbsolutePath());
            }
        }
    }

    private void applyExtractedTransaction(Transaction transaction) {
        try {
            if(currentSignedTx() == SignedTx.INITIATION) {
                recovery.applySignedInitiation(transaction);
                updateSignPage();
                return;
            }
            throw new TimelockRecoveryException("Load a signed " + currentSignedTx().displayName() + " PSBT rather than a raw transaction");
        } catch(TimelockRecoveryException e) {
            AppServices.showErrorDialog("Invalid transaction", e.getMessage());
        }
    }

    private void updateExportButtons() {
        boolean enabled = agreementCheck.isSelected() && recovery != null && recovery.getSignedInitiationTx() != null && recovery.getSignedRecoveryTx() != null
                && (!recovery.hasCancellation() || recovery.getSignedCancellationTx() != null);
        saveRecoveryPdfButton.setDisable(!enabled);
        saveRecoveryJsonButton.setDisable(!enabled);
        boolean cancellationEnabled = enabled && recovery != null && recovery.hasCancellation();
        saveCancellationPdfButton.setDisable(!cancellationEnabled);
        saveCancellationJsonButton.setDisable(!cancellationEnabled);
        boolean showCancellation = recovery != null && recovery.hasCancellation();
        saveCancellationPdfButton.setVisible(showCancellation);
        saveCancellationPdfButton.setManaged(showCancellation);
        saveCancellationJsonButton.setVisible(showCancellation);
        saveCancellationJsonButton.setManaged(showCancellation);
        nextButton.setDisable(!exported);
    }

    private void saveRecoveryPdf() {
        File file = chooseSave("Save Recovery Guide PDF", "timelock-recovery-plan-" + planIdOrNew() + ".pdf", "PDF files", "*.pdf");
        if(file == null) {
            return;
        }
        try {
            ensurePlanMetadata();
            TimelockRecoveryPlan plan = TimelockRecoveryPlan.from(recovery, createdAt, planId);
            TimelockRecoveryPdf.writeRecoveryGuide(file, recovery, plan, createdAt);
            exported = true;
            updateExportButtons();
        } catch(Exception e) {
            log.error("Error saving recovery PDF", e);
            AppServices.showErrorDialog("Error saving PDF", e.getMessage());
        }
    }

    private void saveRecoveryJson() {
        File file = chooseSave("Save BIP-128 JSON", "timelock-recovery-plan-" + planIdOrNew() + ".json", "JSON files", "*.json");
        if(file == null) {
            return;
        }
        try {
            ensurePlanMetadata();
            Files.writeString(file.toPath(), TimelockRecoveryPlan.from(recovery, createdAt, planId).toPrettyJson(), StandardCharsets.UTF_8);
            exported = true;
            updateExportButtons();
        } catch(Exception e) {
            log.error("Error saving recovery JSON", e);
            AppServices.showErrorDialog("Error saving JSON", e.getMessage());
        }
    }

    private void saveCancellationPdf() {
        File file = chooseSave("Save Cancellation Guide PDF", "timelock-cancellation-plan-" + planIdOrNew() + ".pdf", "PDF files", "*.pdf");
        if(file == null) {
            return;
        }
        try {
            ensurePlanMetadata();
            TimelockRecoveryPdf.writeCancellationGuide(file, recovery, createdAt, planId);
            exported = true;
            updateExportButtons();
        } catch(Exception e) {
            log.error("Error saving cancellation PDF", e);
            AppServices.showErrorDialog("Error saving PDF", e.getMessage());
        }
    }

    private void saveCancellationJson() {
        File file = chooseSave("Save Cancellation JSON", "timelock-cancellation-plan-" + planIdOrNew() + ".json", "JSON files", "*.json");
        if(file == null) {
            return;
        }
        try {
            ensurePlanMetadata();
            Files.writeString(file.toPath(), TimelockCancellationPlan.from(recovery, createdAt, planId).toPrettyJson(), StandardCharsets.UTF_8);
            exported = true;
            updateExportButtons();
        } catch(Exception e) {
            log.error("Error saving cancellation JSON", e);
            AppServices.showErrorDialog("Error saving JSON", e.getMessage());
        }
    }

    private void ensurePlanMetadata() {
        if(createdAt == null) {
            createdAt = Instant.now();
        }
        if(planId == null) {
            planId = UUID.randomUUID().toString();
        }
    }

    private String planIdOrNew() {
        ensurePlanMetadata();
        return planId;
    }

    private File chooseSave(String title, String initial, String filterName, String extension) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle(title);
        fileChooser.setInitialFileName(initial);
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(filterName, extension));
        Stage window = new Stage();
        AppServices.moveToActiveWindowScreen(window, 800, 450);
        return fileChooser.showSaveDialog(window);
    }

    private void closeDialog() {
        setResult(exported);
        close();
    }

    private void unregisterEventBus() {
        if(eventBusRegistered) {
            EventManager.get().unregister(this);
            eventBusRegistered = false;
        }
    }

    private boolean confirmClose() {
        if(recovery != null && recovery.getSignedInitiationTx() != null && !exported) {
            Optional<ButtonType> response = AppServices.showWarningDialog("Close without saving?",
                    "The signed Timelock Recovery transactions have not been saved. Close anyway?", ButtonType.CANCEL, ButtonType.YES);
            return response.isPresent() && response.get() == ButtonType.YES;
        }
        return true;
    }

    private void showHelp() {
        Dialog<ButtonType> help = new Dialog<>();
        help.setTitle("Timelock Recovery");
        help.getDialogPane().getStylesheets().add(AppServices.class.getResource("general.css").toExternalForm());
        AppServices.setStageIcon(help.getDialogPane().getScene().getWindow());
        TextArea textArea = new TextArea(loadIntro());
        textArea.setWrapText(true);
        textArea.setEditable(false);
        textArea.setPrefWidth(680);
        textArea.setPrefHeight(480);
        help.getDialogPane().setContent(textArea);
        help.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        help.initOwner(getDialogPane().getScene().getWindow());
        help.initModality(Modality.WINDOW_MODAL);
        help.showAndWait();
    }

    private static String loadIntro() {
        try(InputStream inputStream = TimelockRecoveryDialog.class.getResourceAsStream("/com/sparrowwallet/sparrow/timelockrecovery/intro.txt")) {
            if(inputStream == null) {
                return "See " + TimelockRecovery.SITE_URL;
            }
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        } catch(IOException e) {
            return "See " + TimelockRecovery.SITE_URL;
        }
    }

    private Glyph signGlyph() {
        if(wallet.containsSource(KeystoreSource.HW_USB)) {
            return new Glyph(FontAwesome5Brands.FONT_NAME, FontAwesome5Brands.Glyph.USB);
        }
        return new Glyph(FontAwesome5.FONT_NAME, FontAwesome5.Glyph.PEN_FANCY);
    }

    private Label wrappingNote(String text) {
        Label label = new Label(text);
        label.setWrapText(true);
        label.setMinHeight(Region.USE_PREF_SIZE);
        label.setStyle("-fx-font-size: 11px;");
        label.maxWidthProperty().bind(pages.widthProperty());
        return label;
    }

    private VBox labeled(String text, javafx.scene.Node node) {
        Label label = new Label(text);
        label.setWrapText(true);
        return new VBox(4, label, node);
    }

    private void updateFeeRateLabel() {
        feeRateLabel.setText(unitFormat.getCurrencyFormat().format(feeRange.getFeeRate()) + " sats/vB");
    }

    private static double defaultTimelockFeeRate() {
        Map<Integer, Double> targetRates = AppServices.getTargetBlockFeeRates();
        List<Double> rates = targetRates == null ? List.of() : targetRates.values().stream()
                .filter(rate -> rate != null)
                .sorted()
                .toList();
        double slow = rates.size() >= 2 ? rates.get(1)
                : !rates.isEmpty() ? rates.getFirst()
                : AppServices.getNextBlockMedianFeeRate();
        double sliderMax = AppServices.getFeeRatesRange().getLast();
        return Math.min(Math.max(slow, 1.0) * 15, sliderMax);
    }

    private String formatAmount(long sats) {
        if(bitcoinUnit == BitcoinUnit.BTC) {
            return unitFormat.formatBtcValue(sats) + " BTC";
        }
        return unitFormat.formatSatsValue(sats) + " sats";
    }

    private Callback<TableColumn<RecipientRow, String>, javafx.scene.control.TableCell<RecipientRow, String>> remainingAmountCell() {
        return _ -> new RecipientTextCell() {
            @Override
            public void updateItem(String item, boolean empty) {
                RecipientRow row = getTableRow() == null ? null : getTableRow().getItem();
                if(row != null && row.isRemaining()) {
                    super.updateItem("Remaining", empty);
                    setEditable(false);
                } else {
                    setEditable(true);
                    super.updateItem(item, empty);
                }
            }
        };
    }

    private void updateVerifyAddressDisplay() {
        if(page == Page.SIGN_CANCELLATION && recovery != null && recovery.getCancellationNode() != null) {
            verifyNode = recovery.getCancellationNode();
            verifyAddressHeading.setText("Cancellation address (can be verified on a hardware wallet):");
        } else if(page == Page.CONFIGURE || page == Page.SIGN_INITIATION) {
            verifyNode = initiationNode;
            verifyAddressHeading.setText("Initiation address (can be verified on a hardware wallet):");
        } else {
            verifyAddressBox.setVisible(false);
            return;
        }

        Address address = verifyNode.getAddress();
        verifyAddressField.setText(address.toString());
        verifyAddressQr.setImage(getVerifyAddressQrImage(address.toString()));
        verifyAddressBox.setVisible(true);
        updateDisplayAddressButton(AppServices.getDevices());
    }

    @SuppressWarnings("unchecked")
    private void displayVerifyAddress() {
        if(verifyNode == null) {
            return;
        }
        OutputDescriptor addressDescriptor = OutputDescriptor.getOutputDescriptor(wallet, verifyNode.getKeyPurpose(), verifyNode.getIndex());
        List<Device> possibleDevices = (List<Device>)displayAddressButton.getUserData();
        if(possibleDevices != null && !possibleDevices.isEmpty()) {
            if(possibleDevices.size() > 1 || possibleDevices.get(0).isNeedsPinSent() || possibleDevices.get(0).isNeedsPassphraseSent()) {
                showDeviceDisplayAddressDialog(addressDescriptor);
            } else {
                Device actualDevice = possibleDevices.get(0);
                Hwi.DisplayAddressService displayAddressService = new Hwi.DisplayAddressService(actualDevice, "", wallet.getScriptType(), addressDescriptor,
                        OutputDescriptor.getOutputDescriptor(wallet), wallet.getFullName(), getDeviceRegistration(actualDevice));
                displayAddressService.setOnSucceeded(_ -> updateDeviceRegistrations(actualDevice, displayAddressService.getNewDeviceRegistrations()));
                displayAddressService.setOnFailed(_ -> Platform.runLater(() -> showDeviceDisplayAddressDialog(addressDescriptor)));
                displayAddressService.start();
            }
        } else {
            showDeviceDisplayAddressDialog(addressDescriptor);
        }
    }

    private void showDeviceDisplayAddressDialog(OutputDescriptor addressDescriptor) {
        DeviceDisplayAddressDialog dlg = new DeviceDisplayAddressDialog(wallet, addressDescriptor);
        dlg.initOwner(getDialogPane().getScene().getWindow());
        dlg.showAndWait();
    }

    private void updateDisplayAddressButton(List<Device> devices) {
        OutputDescriptor walletDescriptor = OutputDescriptor.getOutputDescriptor(wallet);
        List<String> walletFingerprints = walletDescriptor.getExtendedPublicKeys().stream()
                .map(extKey -> walletDescriptor.getKeyDerivation(extKey).getMasterFingerprint()).collect(Collectors.toList());
        List<Device> addressDevices = devices.stream().filter(device -> walletFingerprints.contains(device.getFingerprint())).collect(Collectors.toList());
        if(addressDevices.isEmpty()) {
            addressDevices = devices.stream().filter(device -> device.isNeedsPinSent() || device.isNeedsPassphraseSent()).collect(Collectors.toList());
        }

        if(!addressDevices.isEmpty()) {
            displayAddressButton.setVisible(true);
            displayAddressButton.setUserData(addressDevices);
            return;
        }

        if(wallet.getKeystores().stream().anyMatch(keystore -> keystore.getSource().equals(KeystoreSource.HW_USB) || keystore.getSource().equals(KeystoreSource.SW_WATCH))) {
            displayAddressButton.setVisible(true);
            displayAddressButton.setUserData(null);
            return;
        }

        displayAddressButton.setVisible(false);
        displayAddressButton.setUserData(null);
    }

    private void showVerifyAddressQr() {
        if(verifyNode == null || verifyAddressQrDialog != null) {
            return;
        }
        verifyAddressQrDialog = new QRDisplayDialog(verifyNode.getAddress().toString());
        verifyAddressQrDialog.initOwner(getDialogPane().getScene().getWindow());
        verifyAddressQrDialog.showAndWait();
        verifyAddressQrDialog = null;
    }

    private Image getVerifyAddressQrImage(String address) {
        try {
            QRCodeWriter qrCodeWriter = new QRCodeWriter();
            BitMatrix qrMatrix = qrCodeWriter.encode(address, BarcodeFormat.QR_CODE, 130, 130, Map.of(EncodeHintType.MARGIN, 2));
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(qrMatrix, "PNG", baos, new MatrixToImageConfig());
            return new Image(new ByteArrayInputStream(baos.toByteArray()));
        } catch(Exception e) {
            log.error("Error generating address QR", e);
            return null;
        }
    }

    private byte[] getDeviceRegistration(Device device) {
        Optional<Keystore> optKeystore = wallet.getKeystores().stream()
                .filter(keystore -> keystore.getKeyDerivation().getMasterFingerprint().equals(device.getFingerprint()) && keystore.getDeviceRegistration() != null).findFirst();
        return optKeystore.map(Keystore::getDeviceRegistration).orElse(null);
    }

    private void updateDeviceRegistrations(Device device, Set<byte[]> newDeviceRegistrations) {
        if(!newDeviceRegistrations.isEmpty()) {
            List<Keystore> registrationKeystores = wallet.getKeystores().stream()
                    .filter(keystore -> keystore.getKeyDerivation().getMasterFingerprint().equals(device.getFingerprint())).toList();
            if(!registrationKeystores.isEmpty()) {
                registrationKeystores.forEach(keystore -> keystore.setDeviceRegistration(newDeviceRegistrations.iterator().next()));
                EventManager.get().post(new KeystoreDeviceRegistrationsChangedEvent(wallet, registrationKeystores));
            }
        }
    }

    @Subscribe
    public void feeRatesUpdated(FeeRatesUpdatedEvent event) {
        feeRange.updateTrackHighlight();
    }

    @Subscribe
    public void usbDevicesFound(UsbDeviceEvent event) {
        updateDisplayAddressButton(event.getDevices());
    }

    private static class RecipientTextCell extends TextFieldTableCell<RecipientRow, String> {
        public RecipientTextCell() {
            super(new DefaultStringConverter());
        }

        @Override
        public void commitEdit(String value) {
            if(value != null) {
                value = value.trim();
            }
            if(!isEditing() && !Objects.equals(value, getItem())) {
                TableView<RecipientRow> table = getTableView();
                if(table != null) {
                    TableColumn<RecipientRow, String> column = getTableColumn();
                    Event.fireEvent(column, new TableColumn.CellEditEvent<>(
                            table, new TablePosition<>(table, getIndex(), column),
                            TableColumn.editCommitEvent(), value));
                }
            }
            super.commitEdit(value);
        }

        @Override
        public void startEdit() {
            super.startEdit();
            try {
                Field field = TextFieldTableCell.class.getDeclaredField("textField");
                field.setAccessible(true);
                TextField textField = (TextField)field.get(this);
                textField.focusedProperty().addListener((_, _, focused) -> {
                    if(!focused) {
                        commitEdit(getConverter().fromString(textField.getText()));
                        setText(getConverter().fromString(textField.getText()));
                    }
                });
            } catch(Exception e) {
                // Same approach as LabelCell / ServerAliasDialog: textField is private.
            }
        }
    }

    public static class RecipientRow {
        private final StringProperty address;
        private final StringProperty amount;
        private final StringProperty label;
        private final BooleanProperty remaining;

        public RecipientRow(String address, String amount, String label, boolean remaining) {
            this.address = new SimpleStringProperty(address);
            this.amount = new SimpleStringProperty(amount);
            this.label = new SimpleStringProperty(label);
            this.remaining = new SimpleBooleanProperty(remaining);
        }

        public String getAddress() {
            return address.get();
        }

        public void setAddress(String value) {
            address.set(value);
        }

        public StringProperty addressProperty() {
            return address;
        }

        public String getAmount() {
            return amount.get();
        }

        public void setAmount(String value) {
            amount.set(value);
        }

        public StringProperty amountProperty() {
            return amount;
        }

        public String getLabel() {
            return label.get();
        }

        public void setLabel(String value) {
            label.set(value);
        }

        public StringProperty labelProperty() {
            return label;
        }

        public boolean isRemaining() {
            return remaining.get();
        }

        public void setRemaining(boolean value) {
            remaining.set(value);
        }
    }
}
