package com.sparrowwallet.sparrow.control;

import com.sparrowwallet.drongo.KeyPurpose;
import com.sparrowwallet.drongo.address.Address;
import com.sparrowwallet.drongo.protocol.*;
import com.sparrowwallet.drongo.psbt.PSBT;
import com.sparrowwallet.drongo.wallet.*;
import com.sparrowwallet.sparrow.AppServices;
import com.sparrowwallet.sparrow.EventManager;
import com.sparrowwallet.sparrow.TabData;
import com.sparrowwallet.sparrow.TransactionTabData;
import com.sparrowwallet.sparrow.WalletTabData;
import com.sparrowwallet.sparrow.event.NewBlockEvent;
import com.sparrowwallet.sparrow.event.PSBTFinalizedEvent;
import com.sparrowwallet.sparrow.event.ViewPSBTEvent;
import com.sparrowwallet.sparrow.event.ViewTransactionEvent;
import com.google.common.eventbus.Subscribe;
import com.sparrowwallet.drongo.OutputDescriptor;
import com.sparrowwallet.drongo.wallet.KeystoreSource;
import com.sparrowwallet.sparrow.io.Storage;
import com.sparrowwallet.sparrow.net.ElectrumServer;
import javafx.application.Platform;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.control.cell.CheckBoxTableCell;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.util.StringConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.*;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.stream.Collectors;

public class MigrateUtxosDialog extends Dialog<Void> {
    private static final Logger log = LoggerFactory.getLogger(MigrateUtxosDialog.class);

    private static final String ALIGN_RIGHT = "-fx-alignment: CENTER-RIGHT;";
    private static final String ALIGN_CENTER = "-fx-alignment: CENTER;";

    private final Wallet sourceWallet;
    private final Window ownerWindow;

    // Setup phase UI
    private final VBox setupPane;
    private final TableView<UtxoRow> setupTable;
    private final ComboBox<WalletDestination> destWalletCombo;
    private final TextField feeRateField;
    private final ToggleGroup feeStrategyGroup;
    private final TextField minFeeField;
    private final TextField maxFeeField;
    private final HBox randomRangeBox;
    private final HBox fixedFeeBox;
    private final Label setupSummaryLabel;

    // Manage phase UI
    private final VBox managePane;
    private final TableView<MigrationRow> manageTable;
    private final Label manageSummaryLabel;

    // State
    private final List<MigrationRow> migrationRows = new ArrayList<>();
    private boolean batchSigningActive = false;

    public MigrateUtxosDialog(Wallet sourceWallet, Map<Wallet, Storage> openWallets, Window owner) {
        this.sourceWallet = sourceWallet;
        this.ownerWindow = owner;

        final DialogPane dialogPane = getDialogPane();
        setTitle("Migrate UTXOs");
        dialogPane.getStylesheets().add(AppServices.class.getResource("general.css").toExternalForm());
        dialogPane.setStyle("-fx-font-size: 13px;");
        // Center column headers
        Platform.runLater(() -> dialogPane.getScene().getRoot().setStyle(
                dialogPane.getScene().getRoot().getStyle() +
                "; -fx-alignment: CENTER;"));
        dialogPane.getStylesheets().add("data:text/css," +
                ".table-view .column-header .label { -fx-alignment: CENTER; }");
        dialogPane.setHeaderText("Migrate UTXOs individually to a destination wallet.\nEach UTXO becomes a separate transaction for privacy.");

        // === SETUP PHASE ===
        setupPane = new VBox(10);
        setupPane.setPadding(new Insets(10));

        // Destination wallet selector
        List<WalletDestination> destinations = buildDestinationList(sourceWallet, openWallets);
        HBox destBox = new HBox(10);
        destBox.setAlignment(Pos.CENTER_LEFT);
        destWalletCombo = new ComboBox<>(FXCollections.observableArrayList(destinations));
        destWalletCombo.setConverter(new StringConverter<>() {
            @Override
            public String toString(WalletDestination wd) {
                return wd == null ? "" : wd.displayName();
            }

            @Override
            public WalletDestination fromString(String s) {
                return null;
            }
        });
        destWalletCombo.setPrefWidth(350);
        if(destinations.isEmpty()) {
            destWalletCombo.setPromptText("Open a destination wallet first");
            destWalletCombo.setDisable(true);
        }
        destBox.getChildren().addAll(new Label("Destination:"), destWalletCombo);

        // Fee strategy
        HBox feeBox = new HBox(10);
        feeBox.setAlignment(Pos.CENTER_LEFT);
        feeStrategyGroup = new ToggleGroup();
        RadioButton fixedRadio = new RadioButton("Fixed");
        fixedRadio.setToggleGroup(feeStrategyGroup);
        fixedRadio.setUserData("fixed");
        fixedRadio.setSelected(true);
        RadioButton manualRadio = new RadioButton("Manual per-tx");
        manualRadio.setToggleGroup(feeStrategyGroup);
        manualRadio.setUserData("manual");
        RadioButton randomRadio = new RadioButton("Random range");
        randomRadio.setToggleGroup(feeStrategyGroup);
        randomRadio.setUserData("random");
        feeBox.getChildren().addAll(new Label("Fee strategy:"), fixedRadio, manualRadio, randomRadio);

        // Fixed fee rate input
        fixedFeeBox = new HBox(10);
        fixedFeeBox.setAlignment(Pos.CENTER_LEFT);
        feeRateField = new TextField("1.0");
        feeRateField.setPrefWidth(80);
        fixedFeeBox.getChildren().addAll(new Label("Fee rate (sat/vB):"), feeRateField);

        // Random range input
        randomRangeBox = new HBox(10);
        randomRangeBox.setAlignment(Pos.CENTER_LEFT);
        minFeeField = new TextField("0.5");
        minFeeField.setPrefWidth(60);
        maxFeeField = new TextField("4.0");
        maxFeeField.setPrefWidth(60);
        Button randomizeButton = new Button("Randomize");
        randomRangeBox.getChildren().addAll(new Label("Min (sat/vB):"), minFeeField, new Label("Max:"), maxFeeField, randomizeButton);
        randomRangeBox.setVisible(false);
        randomRangeBox.setManaged(false);

        // Setup table
        setupTable = createSetupTable();

        // Buttons row
        HBox selectButtons = new HBox(10);
        selectButtons.setAlignment(Pos.CENTER_LEFT);
        Button selectAllBtn = new Button("Select All");
        Button deselectAllBtn = new Button("Deselect All");
        selectAllBtn.setOnAction(e -> setupTable.getItems().forEach(r -> r.selectedProperty().set(true)));
        deselectAllBtn.setOnAction(e -> setupTable.getItems().forEach(r -> r.selectedProperty().set(false)));
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Button createPsbtsBtn = new Button("Create PSBTs");
        createPsbtsBtn.setStyle("-fx-font-weight: bold;");
        createPsbtsBtn.setDisable(true);
        selectButtons.getChildren().addAll(selectAllBtn, deselectAllBtn, spacer, createPsbtsBtn);

        setupSummaryLabel = new Label("");
        setupSummaryLabel.setStyle("-fx-font-weight: bold;");

        setupPane.getChildren().addAll(destBox, feeBox, fixedFeeBox, randomRangeBox, setupTable, selectButtons, setupSummaryLabel);
        VBox.setVgrow(setupTable, Priority.ALWAYS);

        // === MANAGE PHASE ===
        managePane = new VBox(10);
        managePane.setPadding(new Insets(10));
        managePane.setVisible(false);
        managePane.setManaged(false);

        manageTable = createManageTable();

        manageSummaryLabel = new Label("");
        manageSummaryLabel.setStyle("-fx-font-weight: bold;");

        HBox manageButtons = new HBox(10);
        manageButtons.setAlignment(Pos.CENTER_LEFT);
        Button clearBtn = new Button("Clear");
        clearBtn.setOnAction(e -> clearMigration());
        Region spacer2 = new Region();
        HBox.setHgrow(spacer2, Priority.ALWAYS);
        Button signAllBtn = new Button("Sign All");
        signAllBtn.setStyle("-fx-font-weight: bold;");
        signAllBtn.setOnAction(e -> signAllUnsigned());
        manageButtons.getChildren().addAll(clearBtn, spacer2, signAllBtn);

        managePane.getChildren().addAll(manageTable, manageButtons, manageSummaryLabel);
        VBox.setVgrow(manageTable, Priority.ALWAYS);

        // Refresh signing status when dialog regains focus
        Platform.runLater(() -> {
            Window dialogWindow = dialogPane.getScene().getWindow();
            dialogWindow.focusedProperty().addListener((obs, wasFocused, isFocused) -> {
                if(isFocused && managePane.isVisible()) {
                    refreshMigrationStatuses();
                }
            });
        });

        // Main content
        VBox mainContent = new VBox();
        mainContent.getChildren().addAll(setupPane, managePane);
        VBox.setVgrow(setupPane, Priority.ALWAYS);
        VBox.setVgrow(managePane, Priority.ALWAYS);
        dialogPane.setContent(mainContent);

        // Hidden CLOSE button type required by JavaFX to allow the X button to work
        dialogPane.getButtonTypes().add(ButtonType.CLOSE);
        dialogPane.lookupButton(ButtonType.CLOSE).setVisible(false);
        dialogPane.lookupButton(ButtonType.CLOSE).setManaged(false);
        // Remove button bar padding since there are no visible buttons
        dialogPane.getStylesheets().add("data:text/css," +
                ".dialog-pane .button-bar { -fx-padding: 0; -fx-min-height: 0; -fx-pref-height: 0; }");

        // Wire up events
        destWalletCombo.valueProperty().addListener((obs, old, newVal) -> {
            updateCreateButton(createPsbtsBtn);
            if(newVal != null) {
                assignDestinationAddresses();
            }
            updateSetupSummary();
        });

        feeStrategyGroup.selectedToggleProperty().addListener((obs, old, newVal) -> {
            String strategy = (String) newVal.getUserData();
            boolean isRandom = "random".equals(strategy);
            boolean isManual = "manual".equals(strategy);
            feeRateField.setDisable(isManual);
            randomRangeBox.setVisible(isRandom);
            randomRangeBox.setManaged(isRandom);
            fixedFeeBox.setVisible(!isRandom);
            fixedFeeBox.setManaged(!isRandom);
            if("fixed".equals(strategy)) {
                applyFixedFeeRate();
            } else if(isRandom) {
                applyRandomFeeRates();
            }
            setupTable.getColumns().get(4).setEditable(isManual);
        });

        feeRateField.textProperty().addListener((obs, old, newVal) -> {
            if("fixed".equals(feeStrategyGroup.getSelectedToggle().getUserData())) {
                applyFixedFeeRate();
            }
        });

        randomizeButton.setOnAction(e -> applyRandomFeeRates());
        createPsbtsBtn.setOnAction(e -> createPsbtsAndShowManagePhase());

        dialogPane.setPrefWidth(1100);
        dialogPane.setPrefHeight(700);
        setResizable(true);
        AppServices.setStageIcon(dialogPane.getScene().getWindow());
        AppServices.moveToActiveWindowScreen(this);
        initModality(Modality.NONE);

        populateUtxos();

        // Load saved migration state if available
        loadMigrationState();

        // Listen for PSBT signing events to auto-close tabs
        EventManager.get().register(this);

        // Save state and unregister when dialog is closed
        setOnCloseRequest(e -> {
            batchSigningActive = false;
            saveMigrationState();
            EventManager.get().unregister(this);
        });
        setResultConverter(btn2 -> {
            saveMigrationState();
            EventManager.get().unregister(this);
            return null;
        });
    }

    // === Destination list builder ===

    private List<WalletDestination> buildDestinationList(Wallet source, Map<Wallet, Storage> openWallets) {
        List<WalletDestination> destinations = new ArrayList<>();
        Wallet sourceMaster = source.isMasterWallet() ? source : source.getMasterWallet();

        for(Wallet child : sourceMaster.getChildWallets()) {
            if(!child.equals(source) && child.isValid() && !child.isWhirlpoolChildWallet()
                    && !child.isBip47() && child.getStandardAccountType() != null) {
                destinations.add(new WalletDestination(child, sourceMaster.getName() + " - " + child.getDisplayName() + " (same wallet)", true));
            }
        }
        if(!source.isMasterWallet() && sourceMaster.isValid()) {
            destinations.add(new WalletDestination(sourceMaster, sourceMaster.getName() + " - " + sourceMaster.getDisplayName() + " (same wallet)", true));
        }

        for(Map.Entry<Wallet, Storage> entry : openWallets.entrySet()) {
            Wallet w = entry.getKey();
            if(w.isValid() && !w.equals(source) && !isRelatedWallet(w, source)) {
                destinations.add(new WalletDestination(w, w.getFullDisplayName(), false));
            }
        }

        return destinations;
    }

    private boolean isRelatedWallet(Wallet w1, Wallet w2) {
        Wallet master1 = w1.isMasterWallet() ? w1 : w1.getMasterWallet();
        Wallet master2 = w2.isMasterWallet() ? w2 : w2.getMasterWallet();
        return master1.equals(master2);
    }

    // === Setup table ===

    @SuppressWarnings("unchecked")
    private TableView<UtxoRow> createSetupTable() {
        TableView<UtxoRow> table = new TableView<>();
        table.setEditable(true);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        table.setFixedCellSize(30);

        TableColumn<UtxoRow, Boolean> selectCol = new TableColumn<>("");
        selectCol.setCellValueFactory(cd -> cd.getValue().selectedProperty());
        selectCol.setCellFactory(CheckBoxTableCell.forTableColumn(selectCol));
        selectCol.setMinWidth(35);
        selectCol.setMaxWidth(35);
        selectCol.setStyle(ALIGN_CENTER);

        TableColumn<UtxoRow, String> utxoCol = new TableColumn<>("UTXO");
        utxoCol.setCellValueFactory(cd -> cd.getValue().utxoIdProperty());

        TableColumn<UtxoRow, String> destCol = new TableColumn<>("Dest. Address");
        destCol.setCellValueFactory(cd -> cd.getValue().destAddressProperty());

        TableColumn<UtxoRow, String> valueCol = new TableColumn<>("Value (sats)");
        valueCol.setCellValueFactory(cd -> cd.getValue().valueDisplayProperty());

        TableColumn<UtxoRow, String> feeCol = new TableColumn<>("Fee (sat/vB)");
        feeCol.setCellValueFactory(cd -> cd.getValue().feeRateDisplayProperty());
        feeCol.setCellFactory(col -> new EditableTextFieldCell<>());
        feeCol.setOnEditCommit(event -> {
            try {
                double newRate = Double.parseDouble(event.getNewValue());
                if(newRate > 0) {
                    event.getRowValue().setFeeRate(newRate);
                    updateSetupSummary();
                }
            } catch(NumberFormatException e) {
                // revert
            }
        });
        feeCol.setEditable(false);

        TableColumn<UtxoRow, String> labelCol = new TableColumn<>("Label");
        labelCol.setCellValueFactory(cd -> cd.getValue().labelProperty());

        table.getColumns().addAll(selectCol, utxoCol, destCol, valueCol, feeCol, labelCol);
        return table;
    }

    // === Manage table ===

    @SuppressWarnings("unchecked")
    private TableView<MigrationRow> createManageTable() {
        TableView<MigrationRow> table = new TableView<>();
        table.setEditable(true);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        table.setFixedCellSize(30);

        TableColumn<MigrationRow, String> utxoCol = new TableColumn<>("UTXO");
        utxoCol.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().utxoId));

        TableColumn<MigrationRow, String> destCol = new TableColumn<>("Dest. Address");
        destCol.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().destAddress));
        destCol.setCellFactory(col -> {
            TableCell<MigrationRow, String> cell = new TableCell<>() {
                @Override
                protected void updateItem(String item, boolean empty) {
                    super.updateItem(item, empty);
                    setText(empty || item == null ? null : item);
                }
            };
            cell.setOnMouseClicked(event -> {
                if(event.getClickCount() == 2 && !cell.isEmpty() && cell.getItem() != null) {
                    QRDisplayDialog qrDialog = new QRDisplayDialog(cell.getItem());
                    qrDialog.initOwner(getDialogPane().getScene().getWindow());
                    qrDialog.showAndWait();
                }
            });
            return cell;
        });

        TableColumn<MigrationRow, String> valueCol = new TableColumn<>("Value (sats)");
        valueCol.setCellValueFactory(cd -> new SimpleStringProperty(String.format("%,d", cd.getValue().value)));

        TableColumn<MigrationRow, String> feeCol = new TableColumn<>("Fee (sat/vB)");
        feeCol.setCellValueFactory(cd -> new SimpleStringProperty(String.format("%.1f", cd.getValue().feeRate)));
        feeCol.setCellFactory(col -> new EditableTextFieldCell<>() {
            @Override
            public void startEdit() {
                MigrationRow row = getTableRow() != null ? getTableRow().getItem() : null;
                if(row == null || row.getStatus() != MigrationStatus.UNSIGNED) {
                    return;
                }
                super.startEdit();
            }
        });
        feeCol.setOnEditCommit(event -> {
            MigrationRow row = event.getRowValue();
            try {
                double newRate = Double.parseDouble(event.getNewValue().trim());
                if(newRate > 0) {
                    row.feeRate = newRate;
                    saveMigrationState();
                }
            } catch(NumberFormatException ignored) {
            }
            manageTable.refresh();
        });
        feeCol.setEditable(true);

        TableColumn<MigrationRow, String> labelCol = new TableColumn<>("Label");
        labelCol.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().label));

        TableColumn<MigrationRow, String> targetBlockCol = new TableColumn<>("Target Block");
        targetBlockCol.setCellValueFactory(cd -> {
            MigrationRow row = cd.getValue();
            if(row.targetBlock <= 0) {
                return new SimpleStringProperty("");
            }
            if(row.getStatus() == MigrationStatus.BROADCAST) {
                return new SimpleStringProperty(String.format("%,d", row.targetBlock));
            }
            return new SimpleStringProperty(String.format("%,d", row.targetBlock));
        });
        targetBlockCol.setStyle(ALIGN_CENTER);

        TableColumn<MigrationRow, Void> actionCol = new TableColumn<>("Status");
        actionCol.setCellFactory(col -> new ActionCell());
        actionCol.setStyle(ALIGN_CENTER);
        actionCol.setMinWidth(ACTION_BTN_WIDTH + 20);

        table.getColumns().addAll(utxoCol, destCol, valueCol, feeCol, labelCol, targetBlockCol, actionCol);

        // Double-click on row to view transaction details
        table.setRowFactory(tv -> {
            TableRow<MigrationRow> tableRow = new TableRow<>();
            tableRow.setOnMouseClicked(event -> {
                if(event.getClickCount() == 2 && !tableRow.isEmpty()) {
                    MigrationRow row = tableRow.getItem();
                    if(row.getStatus() != MigrationStatus.UNSIGNED) {
                        showTransactionDetails(row);
                    }
                }
            });

            ContextMenu contextMenu = new ContextMenu();
            MenuItem viewItem = new MenuItem("View transaction");
            viewItem.setOnAction(e -> {
                MigrationRow row = tableRow.getItem();
                if(row != null) {
                    showTransactionDetails(row);
                }
            });
            MenuItem redoItem = new MenuItem("Reset to unsigned");
            redoItem.setOnAction(e -> {
                MigrationRow row = tableRow.getItem();
                if(row != null) {
                    row.setStatus(MigrationStatus.UNSIGNED);
                    manageTable.refresh();
                    saveMigrationState();
                }
            });
            MenuItem verifyItem = new MenuItem("Verify address on device");
            verifyItem.setOnAction(e -> {
                MigrationRow row = tableRow.getItem();
                if(row != null) {
                    verifyDestAddressOnDevice(row);
                }
            });

            tableRow.setOnMousePressed(e -> {
                if(e.isSecondaryButtonDown() && !tableRow.isEmpty()) {
                    MigrationRow row = tableRow.getItem();
                    contextMenu.getItems().clear();
                    if(row == null) {
                        tableRow.setContextMenu(null);
                        return;
                    }
                    if(row.getStatus() != MigrationStatus.UNSIGNED) {
                        contextMenu.getItems().add(viewItem);
                        if(row.getStatus() == MigrationStatus.SIGNED) {
                            contextMenu.getItems().add(redoItem);
                        }
                    }
                    if(isDestWalletHardware(row) && row.getStatus() == MigrationStatus.UNSIGNED) {
                        contextMenu.getItems().add(verifyItem);
                    }
                    if(!contextMenu.getItems().isEmpty()) {
                        tableRow.setContextMenu(contextMenu);
                    } else {
                        tableRow.setContextMenu(null);
                    }
                }
            });

            return tableRow;
        });

        return table;
    }

    // === Phase switching ===

    private void showSetupPhase() {
        managePane.setVisible(false);
        managePane.setManaged(false);
        setupPane.setVisible(true);
        setupPane.setManaged(true);
    }

    private void showManagePhase() {
        setupPane.setVisible(false);
        setupPane.setManaged(false);
        managePane.setVisible(true);
        managePane.setManaged(true);
    }

    private void clearMigration() {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "Clear all PSBTs and go back to setup? This cannot be undone.", ButtonType.YES, ButtonType.NO);
        confirm.initOwner(getDialogPane().getScene().getWindow());
        Optional<ButtonType> result = confirm.showAndWait();
        if(result.isPresent() && result.get() == ButtonType.YES) {
            migrationRows.clear();
            manageTable.getItems().clear();
            saveMigrationState();
            showSetupPhase();
        }
    }

    // === Populate UTXOs ===

    private void populateUtxos() {
        Map<BlockTransactionHashIndex, WalletNode> spendableUtxos = sourceWallet.getSpendableUtxos();
        List<UtxoRow> rows = new ArrayList<>();
        for(Map.Entry<BlockTransactionHashIndex, WalletNode> entry : spendableUtxos.entrySet()) {
            rows.add(new UtxoRow(entry.getKey(), entry.getValue(), 1.0));
        }
        setupTable.setItems(FXCollections.observableArrayList(rows));
    }

    private void assignDestinationAddresses() {
        WalletDestination wd = destWalletCombo.getValue();
        if(wd == null) {
            return;
        }
        Wallet destWallet = wd.wallet();

        WalletNode currentNode = null;
        for(UtxoRow row : setupTable.getItems()) {
            WalletNode freshNode = destWallet.getFreshNode(KeyPurpose.RECEIVE, currentNode);
            row.setDestAddress(freshNode.getAddress().toString());
            row.setDestWalletNode(freshNode);
            currentNode = freshNode;
        }
        setupTable.refresh();
    }

    // === Fee logic ===

    private void applyFixedFeeRate() {
        try {
            double rate = Double.parseDouble(feeRateField.getText());
            if(rate > 0) {
                for(UtxoRow row : setupTable.getItems()) {
                    row.setFeeRate(rate);
                }
                updateSetupSummary();
                setupTable.refresh();
            }
        } catch(NumberFormatException e) {
            // ignore
        }
    }

    private void applyRandomFeeRates() {
        try {
            double min = Double.parseDouble(minFeeField.getText());
            double max = Double.parseDouble(maxFeeField.getText());
            if(min > 0 && max >= min) {
                Random random = new Random();
                for(UtxoRow row : setupTable.getItems()) {
                    double rate = min + (max - min) * random.nextDouble();
                    rate = Math.round(rate * 10.0) / 10.0;
                    row.setFeeRate(rate);
                }
                updateSetupSummary();
                setupTable.refresh();
            }
        } catch(NumberFormatException e) {
            // ignore
        }
    }

    // === Button state ===

    private void updateCreateButton(Button createButton) {
        boolean hasDestination = destWalletCombo.getValue() != null;
        boolean hasSelection = setupTable.getItems().stream().anyMatch(UtxoRow::isSelected);
        createButton.setDisable(!hasDestination || !hasSelection);
    }

    private void updateSetupSummary() {
        List<UtxoRow> selected = setupTable.getItems().stream().filter(UtxoRow::isSelected).toList();
        if(selected.isEmpty()) {
            setupSummaryLabel.setText("");
            return;
        }
        long totalValue = selected.stream().mapToLong(r -> r.getUtxo().getValue()).sum();
        long totalFees = selected.stream().mapToLong(r -> (long) Math.ceil(110 * r.getFeeRate())).sum();
        setupSummaryLabel.setText(String.format("%d transactions | Total: %,d sats | Est. fees: ~%,d sats",
                selected.size(), totalValue, totalFees));
    }

    // === Create PSBTs and switch to manage phase ===

    private void createPsbtsAndShowManagePhase() {
        WalletDestination wd = destWalletCombo.getValue();
        if(wd == null) {
            return;
        }

        List<UtxoRow> selected = setupTable.getItems().stream().filter(UtxoRow::isSelected).toList();
        if(selected.isEmpty()) {
            return;
        }

        migrationRows.clear();
        int errors = 0;
        Integer currentHeight = AppServices.getCurrentBlockHeight();
        if(currentHeight == null) {
            AppServices.showErrorDialog("Not connected", "A server connection is required to set transaction locktimes.");
            return;
        }
        Random random = new Random();
        long nextTargetBlock = currentHeight + 5;

        for(UtxoRow row : selected) {
            try {
                long targetBlock = nextTargetBlock;
                PSBT psbt = createSingleUtxoPsbt(row, targetBlock);
                long fee = (long) Math.ceil(estimateTxVsize(row) * row.getFeeRate());
                MigrationRow mr = new MigrationRow(
                        row.getUtxoId(), row.getUtxo().getValue(),
                        row.getUtxo().getLabel() != null ? row.getUtxo().getLabel() : "",
                        row.getDestAddress(), row.getFeeRate(), fee, psbt
                );
                mr.destWallet = wd.wallet();
                mr.destWalletNode = row.getDestWalletNode();
                mr.targetBlock = targetBlock;
                migrationRows.add(mr);
                nextTargetBlock = targetBlock + 1 + random.nextInt(5);
            } catch(Exception e) {
                errors++;
                log.error("Failed to create PSBT for " + row.getUtxoId(), e);
            }
        }

        if(errors > 0) {
            AppServices.showErrorDialog("PSBT Creation", errors + " PSBT(s) failed to create. " + migrationRows.size() + " succeeded.");
        }

        if(migrationRows.isEmpty()) {
            return;
        }

        manageTable.setItems(FXCollections.observableArrayList(migrationRows));
        updateManageSummary();
        showManagePhase();
        saveMigrationState();
    }

    private double estimateTxVsize(UtxoRow row) {
        BlockTransaction prevBlockTx = sourceWallet.getWalletTransaction(row.getUtxo().getHash());
        TransactionOutput prevTxOut = prevBlockTx.getTransaction().getOutputs().get((int) row.getUtxo().getIndex());

        Transaction sizeTx = new Transaction();
        sizeTx.setVersion(2);
        Wallet.addDummySpendingInput(sizeTx, row.getSourceNode(), prevTxOut);
        sizeTx.addOutput(row.getUtxo().getValue(), row.getDestWalletNode().getAddress());
        return sizeTx.getVirtualSize();
    }

    private PSBT createSingleUtxoPsbt(UtxoRow row, long locktime) {
        BlockTransactionHashIndex utxo = row.getUtxo();
        WalletNode sourceNode = row.getSourceNode();
        Address destAddress = row.getDestWalletNode().getAddress();
        double feeRate = row.getFeeRate();

        BlockTransaction prevBlockTx = sourceWallet.getWalletTransaction(utxo.getHash());
        TransactionOutput prevTxOut = prevBlockTx.getTransaction().getOutputs().get((int) utxo.getIndex());

        // Size estimation
        Transaction sizeTx = new Transaction();
        sizeTx.setVersion(2);
        Wallet.addDummySpendingInput(sizeTx, sourceNode, prevTxOut);
        sizeTx.addOutput(utxo.getValue(), destAddress);
        long fee = (long) Math.ceil(sizeTx.getVirtualSize() * feeRate);
        long outputValue = utxo.getValue() - fee;

        if(outputValue <= 0) {
            throw new IllegalArgumentException("Fee (" + fee + " sats) exceeds UTXO value (" + utxo.getValue() + " sats)");
        }

        TransactionOutput dustCheck = new TransactionOutput(sizeTx, outputValue, destAddress.getOutputScript());
        long dustThreshold = sourceWallet.getDustThreshold(dustCheck, feeRate);
        if(outputValue < dustThreshold) {
            throw new IllegalArgumentException("Output " + outputValue + " sats below dust threshold " + dustThreshold + " sats");
        }

        // Build final tx
        Transaction finalTx = new Transaction();
        finalTx.setVersion(2);
        if(locktime > 0) {
            finalTx.setLocktime(locktime);
        }
        TransactionInput finalInput = Wallet.addDummySpendingInput(finalTx, sourceNode, prevTxOut);
        finalInput.setSequenceNumber(TransactionInput.SEQUENCE_RBF_ENABLED);
        TransactionOutput txOutput = finalTx.addOutput(outputValue, destAddress);

        Map<BlockTransactionHashIndex, WalletNode> selectedUtxos = new LinkedHashMap<>();
        selectedUtxos.put(utxo, sourceNode);

        Payment payment = new Payment(destAddress, utxo.getLabel(), outputValue, false);
        List<WalletTransaction.Output> outputs = List.of(new WalletTransaction.PaymentOutput(txOutput, payment));

        Map<Sha256Hash, BlockTransaction> inputTransactions = new LinkedHashMap<>();
        inputTransactions.put(utxo.getHash(), prevBlockTx);

        WalletTransaction walletTransaction = new WalletTransaction(
                sourceWallet, finalTx,
                List.of(new PresetUtxoSelector(selectedUtxos.keySet())),
                List.of(selectedUtxos),
                List.of(payment), outputs,
                Collections.emptyMap(), fee, inputTransactions
        );

        return walletTransaction.createPSBT();
    }

    // === Manage phase actions ===

    private void refreshMigrationStatuses() {
        boolean changed = false;
        for(MigrationRow row : migrationRows) {
            if(row.getStatus() == MigrationStatus.UNSIGNED && row.psbt.isSigned()) {
                row.setStatus(MigrationStatus.SIGNED);
                changed = true;
            }
        }
        manageTable.refresh();
        updateManageSummary();
        if(changed) {
            saveMigrationState();
        }
    }

    private void updateManageSummary() {
        long total = migrationRows.size();
        long unsigned = migrationRows.stream().filter(r -> r.getStatus() == MigrationStatus.UNSIGNED).count();
        long signed = migrationRows.stream().filter(r -> r.getStatus() == MigrationStatus.SIGNED).count();
        long broadcast = migrationRows.stream().filter(r -> r.getStatus() == MigrationStatus.BROADCAST).count();
        long confirmed = migrationRows.stream().filter(r -> r.getStatus() == MigrationStatus.CONFIRMED).count();
        long failed = migrationRows.stream().filter(r -> r.getStatus() == MigrationStatus.FAILED).count();
        Integer currentHeight = AppServices.getCurrentBlockHeight();
        String blockInfo = currentHeight != null ? " | Block: " + String.format("%,d", currentHeight) : "";
        manageSummaryLabel.setText(String.format("%d total | %d unsigned | %d signed | %d unconfirmed | %d confirmed%s%s",
                total, unsigned, signed, broadcast, confirmed, failed > 0 ? " | " + failed + " failed" : "", blockInfo));
    }

    private void openPsbtForSigning(MigrationRow row) {
        // Opens the PSBT in a Sparrow transaction tab where the user can verify, sign (USB/QR/software), and return
        EventManager.get().post(new ViewPSBTEvent(ownerWindow, "Migration: " + row.utxoId, null, row.psbt));
        // Minimize dialog so user can focus on signing; auto-restores after PSBTFinalizedEvent
        Window dialogWindow = getDialogPane().getScene().getWindow();
        if(dialogWindow instanceof Stage stage) {
            stage.setIconified(true);
        }
    }

    private void signAllUnsigned() {
        long unsignedCount = migrationRows.stream().filter(r -> r.getStatus() == MigrationStatus.UNSIGNED).count();
        if(unsignedCount == 0) {
            AppServices.showErrorDialog("Nothing to sign", "No unsigned transactions.");
            return;
        }
        batchSigningActive = true;
        openNextUnsignedForBatch();
    }

    private void openNextUnsignedForBatch() {
        Optional<MigrationRow> nextUnsigned = migrationRows.stream()
                .filter(r -> r.getStatus() == MigrationStatus.UNSIGNED)
                .findFirst();
        if(nextUnsigned.isPresent()) {
            openPsbtForSigning(nextUnsigned.get());
            Platform.runLater(() -> attachBatchTabCloseListener(nextUnsigned.get()));
        } else {
            batchSigningActive = false;
            Window dialogWindow = getDialogPane().getScene().getWindow();
            if(dialogWindow instanceof Stage stage) {
                stage.setIconified(false);
                stage.toFront();
            }
        }
    }

    private void attachBatchTabCloseListener(MigrationRow row) {
        if(ownerWindow == null || ownerWindow.getScene() == null) {
            return;
        }
        javafx.scene.Node node = ownerWindow.getScene().getRoot().lookup("#tabs");
        if(node instanceof TabPane tabPane) {
            Sha256Hash txId = row.psbt.getTransaction().getTxId();
            for(Tab tab : tabPane.getTabs()) {
                if(tab.getUserData() instanceof TransactionTabData ttd
                        && ttd.getTransaction().getTxId().equals(txId)) {
                    tab.setOnClosed(e -> {
                        if(row.getStatus() == MigrationStatus.UNSIGNED) {
                            batchSigningActive = false;
                            Platform.runLater(() -> {
                                Window dialogWindow = getDialogPane().getScene().getWindow();
                                if(dialogWindow instanceof Stage stage) {
                                    stage.setIconified(false);
                                    stage.toFront();
                                }
                            });
                        }
                    });
                    break;
                }
            }
        }
    }

    private void showTransactionDetails(MigrationRow row) {
        // Open as a read-only Transaction view (no PSBT = no broadcast button)
        try {
            Transaction tx = row.psbt.extractTransaction();
            EventManager.get().post(new ViewTransactionEvent(ownerWindow, tx));
        } catch(Exception e) {
            log.error("Failed to extract transaction", e);
            EventManager.get().post(new ViewTransactionEvent(ownerWindow, row.psbt.getTransaction()));
        }
        // Minimize dialog so user can focus on the transaction view
        Window dialogWindow = getDialogPane().getScene().getWindow();
        if(dialogWindow instanceof Stage stage) {
            stage.setIconified(true);
        }
    }

    @Subscribe
    public void psbtFinalized(PSBTFinalizedEvent event) {
        // Check if this finalized PSBT belongs to one of our migration rows
        Sha256Hash eventTxId = event.getPsbt().getTransaction().getTxId();
        for(MigrationRow row : migrationRows) {
            if(row.psbt.getTransaction().getTxId().equals(eventTxId)) {
                Platform.runLater(() -> {
                    row.setStatus(MigrationStatus.SIGNED);
                    manageTable.refresh();
                    updateManageSummary();
                    saveMigrationState();

                    // Close the PSBT tab in the main window
                    closeMigrationTab(eventTxId);

                    if(batchSigningActive) {
                        // Chain to next unsigned PSBT
                        openNextUnsignedForBatch();
                    } else {
                        // Single sign — restore dialog
                        Window dialogWindow = getDialogPane().getScene().getWindow();
                        if(dialogWindow instanceof Stage stage) {
                            stage.setIconified(false);
                            stage.toFront();
                        }
                    }
                });
                break;
            }
        }
    }

    @Subscribe
    public void newBlock(NewBlockEvent event) {
        int height = event.getHeight();
        Platform.runLater(() -> {
            checkConfirmations();
            manageTable.refresh();
        });

        List<MigrationRow> ready = migrationRows.stream()
                .filter(r -> r.getStatus() == MigrationStatus.SIGNED && r.targetBlock > 0 && height >= r.targetBlock)
                .toList();
        if(ready.isEmpty()) {
            return;
        }

        // Stagger broadcasts with random delays when multiple are ready (e.g. after reopening Sparrow)
        Random random = new Random();
        long delayMs = 0;
        for(MigrationRow row : ready) {
            final long thisDelay = delayMs;
            new Thread(() -> {
                try {
                    if(thisDelay > 0) {
                        Thread.sleep(thisDelay);
                    }
                    Platform.runLater(() -> autoBroadcast(row));
                } catch(InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }).start();
            delayMs += 60_000L + random.nextInt(240_000); // 1-5 min between each
        }
    }

    private void autoBroadcast(MigrationRow row) {
        if(row.getStatus() != MigrationStatus.SIGNED || !row.psbt.isSigned()) {
            return;
        }
        log.info("Auto-broadcasting migration tx {} at target block {}", row.utxoId, row.targetBlock);
        broadcastTransactionSilent(row, () -> Platform.runLater(() -> {
            manageTable.refresh();
            updateManageSummary();
            saveMigrationState();
            checkMigrationComplete();
        }));
    }

    private void checkConfirmations() {
        boolean changed = false;
        for(MigrationRow row : migrationRows) {
            if(row.getStatus() == MigrationStatus.BROADCAST) {
                Sha256Hash txId = row.psbt.getTransaction().getTxId();
                BlockTransaction blockTx = sourceWallet.getTransactions().get(txId);
                if(blockTx != null && blockTx.getHeight() > 0) {
                    row.setStatus(MigrationStatus.CONFIRMED);
                    changed = true;
                }
            }
        }
        if(changed) {
            updateManageSummary();
            saveMigrationState();
            checkMigrationComplete();
        }
    }

    private void checkMigrationComplete() {
        boolean allConfirmed = migrationRows.stream().allMatch(r -> r.getStatus() == MigrationStatus.CONFIRMED);
        if(allConfirmed) {
            // Find the destination wallet from the first row
            Wallet destWallet = migrationRows.stream()
                    .filter(r -> r.destWallet != null)
                    .map(r -> r.destWallet)
                    .findFirst().orElse(null);

            Alert info = new Alert(Alert.AlertType.INFORMATION,
                    "All transactions have been confirmed.\nMigration complete.", ButtonType.OK);
            info.initOwner(getDialogPane().getScene().getWindow());
            info.showAndWait();

            // Clear migration and close dialog
            migrationRows.clear();
            saveMigrationState();
            close();

            // Switch to destination wallet tab
            if(destWallet != null && ownerWindow != null && ownerWindow.getScene() != null) {
                javafx.scene.Node node = ownerWindow.getScene().getRoot().lookup("#tabs");
                if(node instanceof TabPane tabPane) {
                    for(Tab tab : tabPane.getTabs()) {
                        if(tab.getUserData() instanceof WalletTabData) {
                            TabPane subTabs = (TabPane) tab.getContent();
                            for(Tab subTab : subTabs.getTabs()) {
                                if(subTab.getUserData() instanceof WalletTabData wtd && wtd.getWallet() == destWallet) {
                                    tabPane.getSelectionModel().select(tab);
                                    subTabs.getSelectionModel().select(subTab);
                                    return;
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private void closeMigrationTab(Sha256Hash txId) {
        if(ownerWindow == null || ownerWindow.getScene() == null) {
            return;
        }
        // Find the main TabPane by fx:id and close the matching transaction tab
        javafx.scene.Node node = ownerWindow.getScene().getRoot().lookup("#tabs");
        if(node instanceof TabPane tabPane) {
            tabPane.getTabs().removeIf(tab -> {
                if(tab.getUserData() instanceof TransactionTabData ttd) {
                    return ttd.getTransaction().getTxId().equals(txId);
                }
                return false;
            });
        }
    }

    private void broadcastTransaction(MigrationRow row) {
        if(!row.psbt.isSigned()) {
            AppServices.showErrorDialog("Not Signed", "This transaction has not been signed yet. Open it to sign first.");
            return;
        }

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "Broadcast transaction for " + row.utxoId + "?\nThis cannot be undone.", ButtonType.YES, ButtonType.NO);
        confirm.initOwner(getDialogPane().getScene().getWindow());
        Optional<ButtonType> result = confirm.showAndWait();
        if(result.isEmpty() || result.get() != ButtonType.YES) {
            return;
        }

        try {
            Transaction finalTx = row.psbt.extractTransaction();
            ElectrumServer.BroadcastTransactionService broadcastService = new ElectrumServer.BroadcastTransactionService(finalTx, row.fee);
            broadcastService.setOnSucceeded(event -> {
                row.setStatus(MigrationStatus.BROADCAST);
                Platform.runLater(() -> {
                    manageTable.refresh();
                    updateManageSummary();
                    saveMigrationState();
                    AppServices.showErrorDialog("Broadcast Successful", "Transaction " + row.utxoId + " has been broadcast.");
                });
            });
            broadcastService.setOnFailed(event -> {
                row.setStatus(MigrationStatus.FAILED);
                Platform.runLater(() -> {
                    manageTable.refresh();
                    updateManageSummary();
                    saveMigrationState();
                    AppServices.showErrorDialog("Broadcast Failed", event.getSource().getException().getMessage());
                });
            });
            broadcastService.start();
        } catch(Exception e) {
            row.setStatus(MigrationStatus.FAILED);
            manageTable.refresh();
            updateManageSummary();
            AppServices.showErrorDialog("Broadcast Failed", e.getMessage());
        }
    }

    private void exportAllPsbts() {
        List<MigrationRow> toExport = migrationRows.stream()
                .filter(r -> r.getStatus() == MigrationStatus.UNSIGNED || r.getStatus() == MigrationStatus.SIGNED)
                .toList();
        if(toExport.isEmpty()) {
            AppServices.showErrorDialog("Nothing to export", "No PSBTs to export.");
            return;
        }

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Export All PSBTs");
        fileChooser.setInitialFileName("migration_psbts.json");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("JSON file", "*.json"));
        File file = fileChooser.showSaveDialog(getDialogPane().getScene().getWindow());
        if(file != null) {
            JsonArray psbtsArray = new JsonArray();
            for(int i = 0; i < migrationRows.size(); i++) {
                MigrationRow row = migrationRows.get(i);
                if(row.getStatus() == MigrationStatus.UNSIGNED || row.getStatus() == MigrationStatus.SIGNED) {
                    JsonObject obj = new JsonObject();
                    obj.addProperty("utxoId", row.utxoId);
                    obj.addProperty("status", row.getStatus().name());
                    obj.addProperty("psbt", Base64.getEncoder().encodeToString(row.psbt.serialize()));
                    psbtsArray.add(obj);
                }
            }
            try(Writer writer = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8)) {
                new GsonBuilder().setPrettyPrinting().create().toJson(psbtsArray, writer);
            } catch(IOException e) {
                log.error("Failed to export PSBTs", e);
                AppServices.showErrorDialog("Export Failed", e.getMessage());
                return;
            }
            log.info("Exported {} PSBT(s) to {}", psbtsArray.size(), file.getName());
        }
    }

    private void importSignedPsbts() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Import PSBTs");
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("JSON file", "*.json")
        );
        List<File> files = fileChooser.showOpenMultipleDialog(getDialogPane().getScene().getWindow());
        if(files != null && !files.isEmpty()) {
            List<byte[]> psbtDataList = readPsbtFiles(files);

            if(migrationRows.isEmpty()) {
                // Import from Setup phase: load PSBTs as new migration rows
                int loaded = 0;
                for(byte[] data : psbtDataList) {
                    try {
                        PSBT psbt = new PSBT(data);
                        Transaction tx = psbt.getTransaction();
                        String txId = tx.getTxId().toString();
                        String utxoId = tx.getInputs().getFirst().getOutpoint().getHash().toString().substring(0, 8)
                                + "...:" + tx.getInputs().getFirst().getOutpoint().getIndex();
                        long value = 0;
                        long fee = 0;
                        String destAddress = "";
                        if(!tx.getOutputs().isEmpty()) {
                            TransactionOutput out = tx.getOutputs().getFirst();
                            value = out.getValue();
                            destAddress = out.getScript().getToAddress().toString();
                        }
                        // Estimate original UTXO value and fee from tx
                        if(psbt.getPsbtInputs() != null && !psbt.getPsbtInputs().isEmpty()) {
                            var psbtInput = psbt.getPsbtInputs().getFirst();
                            if(psbtInput.getWitnessUtxo() != null) {
                                long inputValue = psbtInput.getWitnessUtxo().getValue();
                                fee = inputValue - value;
                                value = inputValue;
                            } else if(psbtInput.getNonWitnessUtxo() != null) {
                                long inputIdx = tx.getInputs().getFirst().getOutpoint().getIndex();
                                long inputValue = psbtInput.getNonWitnessUtxo().getOutputs().get((int) inputIdx).getValue();
                                fee = inputValue - tx.getOutputs().getFirst().getValue();
                                value = inputValue;
                            }
                        }
                        double feeRate = fee > 0 ? (double) fee / tx.getVirtualSize() : 0;

                        MigrationRow mr = new MigrationRow(utxoId, value, "", destAddress, feeRate, fee, psbt);
                        if(psbt.isSigned()) {
                            mr.setStatus(MigrationStatus.SIGNED);
                        }
                        resolveDestWallet(mr);
                        migrationRows.add(mr);
                        loaded++;
                    } catch(Exception e) {
                        log.error("Failed to parse PSBT for import", e);
                    }
                }

                if(loaded > 0) {
                    manageTable.setItems(FXCollections.observableArrayList(migrationRows));
                    updateManageSummary();
                    showManagePhase();
                    saveMigrationState();
                    log.info("Imported {} PSBT(s)", loaded);
                }
            } else {
                // Import from Manage phase: combine signed PSBTs with existing rows
                int combined = 0;
                for(byte[] data : psbtDataList) {
                    try {
                        PSBT signedPsbt = new PSBT(data);
                        for(MigrationRow row : migrationRows) {
                            if(row.getStatus() == MigrationStatus.UNSIGNED
                                    && row.psbt.getTransaction().getTxId().equals(signedPsbt.getTransaction().getTxId())) {
                                row.psbt.combine(signedPsbt);
                                if(row.psbt.isSigned()) {
                                    row.setStatus(MigrationStatus.SIGNED);
                                    combined++;
                                }
                                break;
                            }
                        }
                    } catch(Exception e) {
                        log.error("Failed to parse PSBT", e);
                    }
                }

                manageTable.refresh();
                updateManageSummary();
                if(combined > 0) {
                    saveMigrationState();
                    log.info("Imported {} signed PSBT(s)", combined);
                }
            }
        }
    }

    private List<byte[]> readPsbtFiles(List<File> files) {
        List<byte[]> psbtDataList = new ArrayList<>();
        for(File file : files) {
            try {
                String content = Files.readString(file.toPath(), StandardCharsets.UTF_8);
                JsonArray array = JsonParser.parseString(content).getAsJsonArray();
                for(JsonElement el : array) {
                    String b64 = el.getAsJsonObject().get("psbt").getAsString();
                    psbtDataList.add(Base64.getDecoder().decode(b64));
                }
            } catch(IOException e) {
                log.error("Failed to read file " + file.getName(), e);
            }
        }
        return psbtDataList;
    }

    private void broadcastAllSigned() {
        List<MigrationRow> signed = migrationRows.stream()
                .filter(r -> r.getStatus() == MigrationStatus.SIGNED)
                .toList();
        if(signed.isEmpty()) {
            AppServices.showErrorDialog("Nothing to broadcast", "No signed transactions ready to broadcast.");
            return;
        }

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "Broadcast " + signed.size() + " signed transaction(s)?\nThis cannot be undone.", ButtonType.YES, ButtonType.NO);
        confirm.initOwner(getDialogPane().getScene().getWindow());
        Optional<ButtonType> result = confirm.showAndWait();
        if(result.isPresent() && result.get() == ButtonType.YES) {
            final int[] success = {0};
            final int[] failed = {0};
            final int total = signed.size();
            for(MigrationRow row : signed) {
                broadcastTransactionSilent(row, () -> {
                    if(row.getStatus() == MigrationStatus.BROADCAST) {
                        success[0]++;
                    } else {
                        failed[0]++;
                    }
                    if(success[0] + failed[0] == total) {
                        log.info("Broadcast complete: {} of {} succeeded, {} failed", success[0], total, failed[0]);
                    }
                });
            }
        }
    }

    private void broadcastTransactionSilent(MigrationRow row, Runnable onDone) {
        if(!row.psbt.isSigned()) {
            row.setStatus(MigrationStatus.FAILED);
            Platform.runLater(() -> {
                manageTable.refresh();
                updateManageSummary();
                saveMigrationState();
            });
            if(onDone != null) onDone.run();
            return;
        }

        try {
            Transaction finalTx = row.psbt.extractTransaction();
            ElectrumServer.BroadcastTransactionService broadcastService = new ElectrumServer.BroadcastTransactionService(finalTx, row.fee);
            broadcastService.setOnSucceeded(event -> {
                row.setStatus(MigrationStatus.BROADCAST);
                Platform.runLater(() -> {
                    manageTable.refresh();
                    updateManageSummary();
                    saveMigrationState();
                });
                if(onDone != null) onDone.run();
            });
            broadcastService.setOnFailed(event -> {
                row.setStatus(MigrationStatus.FAILED);
                Platform.runLater(() -> {
                    manageTable.refresh();
                    updateManageSummary();
                    saveMigrationState();
                });
                if(onDone != null) onDone.run();
            });
            broadcastService.start();
        } catch(Exception e) {
            row.setStatus(MigrationStatus.FAILED);
            Platform.runLater(() -> {
                manageTable.refresh();
                updateManageSummary();
                saveMigrationState();
            });
            if(onDone != null) onDone.run();
        }
    }

    // === Destination wallet resolution ===

    private void resolveDestWallet(MigrationRow mr) {
        try {
            Address addr = Address.fromString(mr.destAddress);
            for(Wallet w : AppServices.get().getOpenWallets().keySet()) {
                Map<Address, WalletNode> addresses = w.getWalletAddresses();
                WalletNode node = addresses.get(addr);
                if(node != null) {
                    mr.destWallet = w;
                    mr.destWalletNode = node;
                    log.debug("Resolved dest wallet for {} -> {}", mr.destAddress, w.getFullName());
                    return;
                }
                // Also check child wallets directly
                for(Wallet child : w.getChildWallets()) {
                    if(child.isValid()) {
                        Map<Address, WalletNode> childAddresses = child.getWalletAddresses();
                        WalletNode childNode = childAddresses.get(addr);
                        if(childNode != null) {
                            mr.destWallet = child;
                            mr.destWalletNode = childNode;
                            log.debug("Resolved dest wallet (child) for {} -> {}", mr.destAddress, child.getFullName());
                            return;
                        }
                    }
                }
            }
            log.debug("Could not resolve dest wallet for address {}", mr.destAddress);
        } catch(Exception e) {
            log.error("Error resolving dest wallet for address {}", mr.destAddress, e);
        }
    }

    // === Hardware wallet address verification ===

    private boolean isDestWalletHardware(MigrationRow row) {
        if(row.destWallet == null) {
            return false;
        }
        return row.destWallet.getKeystores().stream().anyMatch(ks ->
                ks.getSource().equals(KeystoreSource.HW_USB)
                || ks.getSource().equals(KeystoreSource.SW_WATCH));
    }

    private void verifyDestAddressOnDevice(MigrationRow row) {
        if(row.destWallet == null || row.destWalletNode == null) {
            AppServices.showErrorDialog("Cannot verify", "Destination wallet information is not available for this transaction.");
            return;
        }

        OutputDescriptor addressDescriptor = OutputDescriptor.getOutputDescriptor(row.destWallet,
                row.destWalletNode.getKeyPurpose(), row.destWalletNode.getIndex());
        DeviceDisplayAddressDialog dlg = new DeviceDisplayAddressDialog(row.destWallet, addressDescriptor);
        dlg.initOwner(getDialogPane().getScene().getWindow());
        dlg.showAndWait();
    }

    // === Persistence ===

    private File getMigrationFile() {
        File migrationsDir = new File(Storage.getSparrowDir(), "migrations");
        if(!migrationsDir.exists()) {
            migrationsDir.mkdirs();
        }
        String walletId = sourceWallet.getFullName().replaceAll("[^a-zA-Z0-9_-]", "_");
        return new File(migrationsDir, walletId + ".json");
    }

    private void saveMigrationState() {
        if(migrationRows.isEmpty()) {
            // Remove file if no active migration
            File file = getMigrationFile();
            if(file.exists()) {
                file.delete();
            }
            return;
        }

        // Don't save if all are confirmed (migration complete)
        boolean allDone = migrationRows.stream().allMatch(r -> r.getStatus() == MigrationStatus.CONFIRMED);
        if(allDone) {
            File file = getMigrationFile();
            if(file.exists()) {
                file.delete();
            }
            return;
        }

        try {
            JsonObject root = new JsonObject();
            root.addProperty("sourceWallet", sourceWallet.getFullName());
            root.addProperty("savedAt", System.currentTimeMillis());

            JsonArray rows = new JsonArray();
            for(MigrationRow row : migrationRows) {
                JsonObject obj = new JsonObject();
                obj.addProperty("utxoId", row.utxoId);
                obj.addProperty("value", row.value);
                obj.addProperty("label", row.label);
                obj.addProperty("destAddress", row.destAddress);
                obj.addProperty("feeRate", row.feeRate);
                obj.addProperty("fee", row.fee);
                obj.addProperty("status", row.getStatus().name());
                obj.addProperty("targetBlock", row.targetBlock);
                obj.addProperty("psbt", Base64.getEncoder().encodeToString(row.psbt.serialize()));
                rows.add(obj);
            }
            root.add("migrations", rows);

            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            Files.writeString(getMigrationFile().toPath(), gson.toJson(root), StandardCharsets.UTF_8);
            log.info("Saved migration state: " + migrationRows.size() + " rows");
        } catch(Exception e) {
            log.error("Failed to save migration state", e);
        }
    }

    private void loadMigrationState() {
        File file = getMigrationFile();
        if(!file.exists()) {
            return;
        }

        try {
            String json = Files.readString(file.toPath(), StandardCharsets.UTF_8);
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            JsonArray rows = root.getAsJsonArray("migrations");

            migrationRows.clear();
            for(JsonElement el : rows) {
                JsonObject obj = el.getAsJsonObject();
                String utxoId = obj.get("utxoId").getAsString();
                long value = obj.get("value").getAsLong();
                String label = obj.has("label") ? obj.get("label").getAsString() : "";
                String destAddress = obj.get("destAddress").getAsString();
                double feeRate = obj.get("feeRate").getAsDouble();
                long fee = obj.get("fee").getAsLong();
                String statusStr = obj.get("status").getAsString();
                byte[] psbtBytes = Base64.getDecoder().decode(obj.get("psbt").getAsString());

                PSBT psbt = new PSBT(psbtBytes);
                MigrationRow mr = new MigrationRow(utxoId, value, label, destAddress, feeRate, fee, psbt);
                mr.setStatus(MigrationStatus.valueOf(statusStr));
                mr.targetBlock = obj.has("targetBlock") ? obj.get("targetBlock").getAsLong() : 0;
                resolveDestWallet(mr);
                migrationRows.add(mr);
            }

            if(!migrationRows.isEmpty()) {
                manageTable.setItems(FXCollections.observableArrayList(migrationRows));
                updateManageSummary();
                showManagePhase();
                log.info("Restored migration state: " + migrationRows.size() + " rows");
            }
        } catch(Exception e) {
            log.error("Failed to load migration state", e);
        }
    }

    // === Inner types ===

    public enum MigrationStatus {
        UNSIGNED("Unsigned"),
        SIGNED("Signed"),
        BROADCAST("Broadcast"),
        CONFIRMED("Confirmed"),
        FAILED("Failed");

        private final String label;

        MigrationStatus(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    public record WalletDestination(Wallet wallet, String displayName, boolean sameWallet) {
    }

    public static class MigrationRow {
        final String utxoId;
        final long value;
        final String label;
        final String destAddress;
        double feeRate;
        final long fee;
        final PSBT psbt;
        Wallet destWallet;
        WalletNode destWalletNode;
        long targetBlock;
        private final SimpleObjectProperty<MigrationStatus> status = new SimpleObjectProperty<>(MigrationStatus.UNSIGNED);

        public MigrationRow(String utxoId, long value, String label, String destAddress, double feeRate, long fee, PSBT psbt) {
            this.utxoId = utxoId;
            this.value = value;
            this.label = label;
            this.destAddress = destAddress;
            this.feeRate = feeRate;
            this.fee = fee;
            this.psbt = psbt;
        }

        public MigrationStatus getStatus() { return status.get(); }
        public void setStatus(MigrationStatus s) { status.set(s); }
        public SimpleObjectProperty<MigrationStatus> statusProperty() { return status; }
    }

    // === Table cells ===

    private class StatusCell extends TableCell<MigrationRow, MigrationStatus> {
        @Override
        protected void updateItem(MigrationStatus item, boolean empty) {
            super.updateItem(item, empty);
            setText(null);
            setGraphic(null);
            setStyle("");
            if(empty || item == null) {
                return;
            }
            switch(item) {
                case UNSIGNED -> {
                    setText("Unsigned");
                    setStyle(ALIGN_CENTER + " -fx-text-fill: white;");
                }
                case SIGNED -> {
                    setText("Signed");
                    setStyle(ALIGN_CENTER + " -fx-text-fill: white; -fx-font-weight: bold;");
                }
                case BROADCAST -> {
                    setText("Signed");
                    setStyle(ALIGN_CENTER + " -fx-text-fill: white; -fx-font-weight: bold;");
                }
                case CONFIRMED -> {
                    setText("Signed");
                    setStyle(ALIGN_CENTER + " -fx-text-fill: white; -fx-font-weight: bold;");
                }
                case FAILED -> {
                    setText("Failed");
                    setStyle(ALIGN_CENTER + " -fx-text-fill: white;");
                }
            }
        }
    }

    private static final double ACTION_BTN_WIDTH = 85;

    private class ActionCell extends TableCell<MigrationRow, Void> {
        private final HBox buttons = new HBox(5);

        public ActionCell() {
            buttons.setAlignment(Pos.CENTER);
        }

        @Override
        protected void updateItem(Void item, boolean empty) {
            super.updateItem(item, empty);
            if(empty || getTableRow() == null || getTableRow().getItem() == null) {
                setGraphic(null);
                return;
            }

            MigrationRow row = getTableRow().getItem();
            buttons.getChildren().clear();

            switch(row.getStatus()) {
                case UNSIGNED -> {
                    Button openBtn = new Button("Sign Tx");
                    openBtn.setPrefWidth(ACTION_BTN_WIDTH);
                    openBtn.setOnAction(e -> openPsbtForSigning(row));
                    buttons.getChildren().add(openBtn);
                }
                case SIGNED -> {
                    Label waitLabel = new Label("Scheduled");
                    waitLabel.setStyle("-fx-text-fill: #e8a838; -fx-font-weight: bold;");
                    waitLabel.setPrefWidth(ACTION_BTN_WIDTH);
                    waitLabel.setAlignment(Pos.CENTER);
                    buttons.getChildren().add(waitLabel);
                }
                case BROADCAST -> {
                    Label unconfLabel = new Label("Unconfirmed");
                    unconfLabel.setStyle("-fx-text-fill: #e8a838; -fx-font-weight: bold;");
                    unconfLabel.setPrefWidth(ACTION_BTN_WIDTH);
                    unconfLabel.setAlignment(Pos.CENTER);
                    buttons.getChildren().add(unconfLabel);
                }
                case CONFIRMED -> {
                    Label confirmedLabel = new Label("Confirmed");
                    confirmedLabel.setStyle("-fx-text-fill: #1e88cf; -fx-font-weight: bold;");
                    confirmedLabel.setPrefWidth(ACTION_BTN_WIDTH);
                    confirmedLabel.setAlignment(Pos.CENTER);
                    buttons.getChildren().add(confirmedLabel);
                }
                case FAILED -> {
                    Button retryBtn = new Button("Retry");
                    retryBtn.setPrefWidth(ACTION_BTN_WIDTH);
                    retryBtn.setOnAction(e -> {
                        row.setStatus(MigrationStatus.SIGNED);
                        manageTable.refresh();
                    });
                    buttons.getChildren().add(retryBtn);
                }
            }

            setGraphic(buttons);
        }
    }

    // === Setup table row ===

    public static class UtxoRow {
        private final BlockTransactionHashIndex utxo;
        private final WalletNode sourceNode;
        private final SimpleBooleanProperty selected = new SimpleBooleanProperty(true);
        private final SimpleStringProperty destAddress = new SimpleStringProperty("");
        private final SimpleDoubleProperty feeRate;
        private WalletNode destWalletNode;

        public UtxoRow(BlockTransactionHashIndex utxo, WalletNode sourceNode, double initialFeeRate) {
            this.utxo = utxo;
            this.sourceNode = sourceNode;
            this.feeRate = new SimpleDoubleProperty(initialFeeRate);
        }

        public BlockTransactionHashIndex getUtxo() { return utxo; }
        public WalletNode getSourceNode() { return sourceNode; }
        public boolean isSelected() { return selected.get(); }
        public SimpleBooleanProperty selectedProperty() { return selected; }

        public String getUtxoId() {
            return utxo.getHash().toString().substring(0, 8) + "...:" + utxo.getIndex();
        }

        public SimpleStringProperty utxoIdProperty() { return new SimpleStringProperty(getUtxoId()); }
        public SimpleStringProperty valueDisplayProperty() { return new SimpleStringProperty(String.format("%,d", utxo.getValue())); }
        public SimpleStringProperty labelProperty() { return new SimpleStringProperty(utxo.getLabel() != null ? utxo.getLabel() : ""); }

        public String getDestAddress() { return destAddress.get(); }
        public void setDestAddress(String address) { destAddress.set(address); }
        public SimpleStringProperty destAddressProperty() { return destAddress; }

        public WalletNode getDestWalletNode() { return destWalletNode; }
        public void setDestWalletNode(WalletNode node) { this.destWalletNode = node; }

        public double getFeeRate() { return feeRate.get(); }
        public void setFeeRate(double rate) { feeRate.set(rate); }
        public SimpleStringProperty feeRateDisplayProperty() { return new SimpleStringProperty(String.format("%.1f", feeRate.get())); }
    }

    // Editable cell for fee column
    private static class EditableTextFieldCell<S> extends TableCell<S, String> {
        private TextField textField;

        @Override
        public void startEdit() {
            super.startEdit();
            if(textField == null) {
                textField = new TextField();
                textField.setStyle(ALIGN_RIGHT);
                textField.setOnAction(e -> commitEdit(textField.getText()));
                textField.focusedProperty().addListener((obs, wasFocused, isNow) -> {
                    if(!isNow) {
                        commitEdit(textField.getText());
                    }
                });
            }
            textField.setText(getItem());
            setGraphic(textField);
            setText(null);
            textField.selectAll();
            textField.requestFocus();
        }

        @Override
        public void cancelEdit() {
            super.cancelEdit();
            setText(getItem());
            setGraphic(null);
        }

        @Override
        protected void updateItem(String item, boolean empty) {
            super.updateItem(item, empty);
            if(empty || item == null) {
                setText(null);
                setGraphic(null);
            } else if(isEditing()) {
                if(textField != null) {
                    textField.setText(item);
                }
                setText(null);
                setGraphic(textField);
            } else {
                setText(item);
                setGraphic(null);
            }
        }
    }
}
