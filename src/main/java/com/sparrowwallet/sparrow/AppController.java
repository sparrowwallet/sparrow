package com.sparrowwallet.sparrow;

import com.google.common.base.Charsets;
import com.google.common.eventbus.Subscribe;
import com.google.common.io.ByteSource;
import com.sparrowwallet.drongo.BitcoinUnit;
import com.sparrowwallet.drongo.Network;
import com.sparrowwallet.drongo.SecureString;
import com.sparrowwallet.drongo.Utils;
import com.sparrowwallet.drongo.crypto.ECKey;
import com.sparrowwallet.drongo.crypto.EncryptionType;
import com.sparrowwallet.drongo.crypto.InvalidPasswordException;
import com.sparrowwallet.drongo.crypto.Key;
import com.sparrowwallet.drongo.policy.PolicyType;
import com.sparrowwallet.drongo.protocol.ScriptType;
import com.sparrowwallet.drongo.protocol.Sha256Hash;
import com.sparrowwallet.drongo.protocol.Transaction;
import com.sparrowwallet.drongo.psbt.PSBT;
import com.sparrowwallet.drongo.psbt.PSBTInput;
import com.sparrowwallet.drongo.psbt.PSBTParseException;
import com.sparrowwallet.drongo.wallet.*;
import com.sparrowwallet.sparrow.control.*;
import com.sparrowwallet.sparrow.event.*;
import com.sparrowwallet.sparrow.glyphfont.FontAwesome5;
import com.sparrowwallet.sparrow.io.*;
import com.sparrowwallet.sparrow.net.ElectrumServer;
import com.sparrowwallet.sparrow.net.ServerType;
import com.sparrowwallet.sparrow.preferences.PreferenceGroup;
import com.sparrowwallet.sparrow.preferences.PreferencesDialog;
import com.sparrowwallet.sparrow.transaction.TransactionController;
import com.sparrowwallet.sparrow.transaction.TransactionData;
import com.sparrowwallet.sparrow.transaction.TransactionView;
import com.sparrowwallet.sparrow.wallet.WalletController;
import com.sparrowwallet.sparrow.wallet.WalletForm;
import de.codecentric.centerdevice.MenuToolkit;
import javafx.animation.*;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.collections.ListChangeListener;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.*;
import javafx.scene.layout.StackPane;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.Window;
import javafx.util.Duration;
import org.controlsfx.control.Notifications;
import org.controlsfx.control.StatusBar;
import org.controlsfx.glyphfont.FontAwesome;
import org.controlsfx.glyphfont.Glyph;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.util.*;
import java.util.stream.Collectors;

import static com.sparrowwallet.sparrow.AppServices.*;

public class AppController implements Initializable {
    private static final Logger log = LoggerFactory.getLogger(AppController.class);

    public static final String DRAG_OVER_CLASS = "drag-over";
    public static final double TAB_LABEL_GRAPHIC_OPACITY_INACTIVE = 0.8;
    public static final double TAB_LABEL_GRAPHIC_OPACITY_ACTIVE = 0.95;
    public static final String LOADING_TRANSACTIONS_MESSAGE = "Loading wallet, select Transactions tab to view...";
    public static final String CONNECTION_FAILED_PREFIX = "Connection failed: ";
    public static final String TRYING_ANOTHER_SERVER_MESSAGE = "trying another server...";

    @FXML
    private MenuItem saveTransaction;

    @FXML
    private Menu savePSBT;

    @FXML
    private MenuItem exportWallet;

    @FXML
    private Menu fileMenu;

    @FXML
    private Menu viewMenu;

    @FXML
    private Menu toolsMenu;

    @FXML
    private Menu helpMenu;

    @FXML
    private MenuItem openTransactionIdItem;

    @FXML
    private ToggleGroup bitcoinUnit;

    @FXML
    private ToggleGroup theme;

    @FXML
    private CheckMenuItem openWalletsInNewWindows;

    @FXML
    private CheckMenuItem hideEmptyUsedAddresses;

    @FXML
    private CheckMenuItem useHdCameraResolution;

    @FXML
    private CheckMenuItem showLoadingLog;

    @FXML
    private CheckMenuItem showTxHex;

    @FXML
    private MenuItem minimizeToTray;

    @FXML
    private MenuItem refreshWallet;

    @FXML
    private StackPane rootStack;

    @FXML
    private TabPane tabs;

    @FXML
    private StatusBar statusBar;

    @FXML
    private UnlabeledToggleSwitch serverToggle;

    private PauseTransition wait;

    private Timeline statusTimeline;

    private final Set<Wallet> loadingWallets = new LinkedHashSet<>();

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        EventManager.get().register(this);
    }

    void initializeView() {
        setPlatformApplicationMenu();

        rootStack.setOnDragOver(event -> {
            if(event.getGestureSource() != rootStack && event.getDragboard().hasFiles()) {
                event.acceptTransferModes(TransferMode.COPY_OR_MOVE);
            }
            event.consume();
        });

        rootStack.setOnDragDropped(event -> {
            Dragboard db = event.getDragboard();
            boolean success = false;
            if(db.hasFiles()) {
                for(File file : db.getFiles()) {
                    if(isWalletFile(file)) {
                        openWalletFile(file, true);
                    } else {
                        openTransactionFile(file);
                    }
                }
                success = true;
            }
            event.setDropCompleted(success);
            event.consume();
        });

        rootStack.setOnDragEntered(event -> {
            rootStack.getStyleClass().add(DRAG_OVER_CLASS);
        });

        rootStack.setOnDragExited(event -> {
            rootStack.getStyleClass().removeAll(DRAG_OVER_CLASS);
        });

        tabs.getSelectionModel().selectedItemProperty().addListener((observable, old_val, selectedTab) -> {
            tabs.getTabs().forEach(tab -> ((Label)tab.getGraphic()).getGraphic().setOpacity(TAB_LABEL_GRAPHIC_OPACITY_INACTIVE));
            if(selectedTab != null) {
                Label tabLabel = (Label)selectedTab.getGraphic();
                tabLabel.getGraphic().setOpacity(TAB_LABEL_GRAPHIC_OPACITY_ACTIVE);

                TabData tabData = (TabData)selectedTab.getUserData();
                if(tabData.getType() == TabData.TabType.TRANSACTION) {
                    EventManager.get().post(new TransactionTabSelectedEvent(selectedTab));
                } else if(tabData.getType() == TabData.TabType.WALLET) {
                    EventManager.get().post(new WalletTabSelectedEvent(selectedTab));
                }
            }
        });

        //Draggle tabs introduce unwanted movement when selecting between them
        //tabs.setTabDragPolicy(TabPane.TabDragPolicy.REORDER);
        tabs.getTabs().addListener((ListChangeListener<Tab>) c -> {
            if(c.next() && (c.wasAdded() || c.wasRemoved())) {
                boolean walletAdded = c.getAddedSubList().stream().anyMatch(tab -> ((TabData)tab.getUserData()).getType() == TabData.TabType.WALLET);
                boolean walletRemoved = c.getRemoved().stream().anyMatch(tab -> ((TabData)tab.getUserData()).getType() == TabData.TabType.WALLET);
                if(walletAdded || walletRemoved) {
                    EventManager.get().post(new OpenWalletsEvent(tabs.getScene().getWindow(), getOpenWalletTabData()));
                }

                List<WalletTabData> closedWalletTabs = c.getRemoved().stream().map(tab -> (TabData)tab.getUserData())
                        .filter(tabData -> tabData.getType() == TabData.TabType.WALLET).map(tabData -> (WalletTabData)tabData).collect(Collectors.toList());
                if(!closedWalletTabs.isEmpty()) {
                    EventManager.get().post(new WalletTabsClosedEvent(closedWalletTabs));
                }

                List<TransactionTabData> closedTransactionTabs = c.getRemoved().stream().map(tab -> (TabData)tab.getUserData())
                        .filter(tabData -> tabData.getType() == TabData.TabType.TRANSACTION).map(tabData -> (TransactionTabData)tabData).collect(Collectors.toList());
                if(!closedTransactionTabs.isEmpty()) {
                    EventManager.get().post(new TransactionTabsClosedEvent(closedTransactionTabs));
                }

                if(tabs.getTabs().isEmpty()) {
                    Stage tabStage = (Stage)tabs.getScene().getWindow();
                    tabStage.setTitle("Sparrow");
                }
            }
        });

        tabs.getScene().getWindow().setOnCloseRequest(event -> {
            EventManager.get().unregister(this);
            EventManager.get().post(new OpenWalletsEvent(tabs.getScene().getWindow(), Collections.emptyList()));
        });

        BitcoinUnit unit = Config.get().getBitcoinUnit();
        if(unit == null) {
            unit = BitcoinUnit.AUTO;
            Config.get().setBitcoinUnit(unit);
        }
        final BitcoinUnit selectedUnit = unit;
        Optional<Toggle> selectedUnitToggle = bitcoinUnit.getToggles().stream().filter(toggle -> selectedUnit.equals(toggle.getUserData())).findFirst();
        selectedUnitToggle.ifPresent(toggle -> bitcoinUnit.selectToggle(toggle));

        Theme configTheme = Config.get().getTheme();
        if(configTheme == null) {
            configTheme = Theme.LIGHT;
            Config.get().setTheme(Theme.LIGHT);
        }
        final Theme selectedTheme = configTheme;
        Optional<Toggle> selectedThemeToggle = theme.getToggles().stream().filter(toggle -> selectedTheme.equals(toggle.getUserData())).findFirst();
        selectedThemeToggle.ifPresent(toggle -> theme.selectToggle(toggle));
        setTheme(null);

        openWalletsInNewWindows.setSelected(Config.get().isOpenWalletsInNewWindows());
        hideEmptyUsedAddresses.setSelected(Config.get().isHideEmptyUsedAddresses());
        useHdCameraResolution.setSelected(Config.get().isHdCapture());
        showTxHex.setSelected(Config.get().isShowTransactionHex());
        showLoadingLog.setSelected(Config.get().isShowLoadingLog());
        savePSBT.visibleProperty().bind(saveTransaction.visibleProperty().not());
        exportWallet.setDisable(true);
        refreshWallet.disableProperty().bind(Bindings.or(exportWallet.disableProperty(), Bindings.or(serverToggle.disableProperty(), AppServices.onlineProperty().not())));

        setServerType(Config.get().getServerType());
        serverToggle.setSelected(isConnected());
        serverToggle.setDisable(Config.get().getServerType() == null);
        onlineProperty().bindBidirectional(serverToggle.selectedProperty());
        onlineProperty().addListener((observable, oldValue, newValue) ->  {
            Platform.runLater(() -> setServerToggleTooltip(getCurrentBlockHeight()));
        });
        serverToggle.addEventHandler(MouseEvent.MOUSE_CLICKED, event -> {
            Config.get().setMode(serverToggle.isSelected() ? Mode.ONLINE : Mode.OFFLINE);
        });

        openTransactionIdItem.disableProperty().bind(onlineProperty().not());
    }

    private void setPlatformApplicationMenu() {
        org.controlsfx.tools.Platform platform = org.controlsfx.tools.Platform.getCurrent();
        if(platform == org.controlsfx.tools.Platform.OSX) {
            MenuToolkit tk = MenuToolkit.toolkit();
            MenuItem preferences = new MenuItem("Preferences...");
            preferences.setOnAction(this::openPreferences);
            preferences.setAccelerator(new KeyCodeCombination(KeyCode.COMMA, KeyCombination.META_DOWN));
            Menu defaultApplicationMenu = new Menu("Apple", null, tk.createAboutMenuItem(MainApp.APP_NAME, getAboutStage()), new SeparatorMenuItem(),
                    preferences, new SeparatorMenuItem(),
                    tk.createHideMenuItem(MainApp.APP_NAME), tk.createHideOthersMenuItem(), tk.createUnhideAllMenuItem(), new SeparatorMenuItem(),
                    tk.createQuitMenuItem(MainApp.APP_NAME));
            tk.setApplicationMenu(defaultApplicationMenu);

            fileMenu.getItems().removeIf(item -> item.getStyleClass().contains("osxHide"));
            toolsMenu.getItems().removeIf(item -> item.getStyleClass().contains("osxHide"));
            helpMenu.getItems().removeIf(item -> item.getStyleClass().contains("osxHide"));
        } else if(platform == org.controlsfx.tools.Platform.WINDOWS) {
            toolsMenu.getItems().removeIf(item -> item.getStyleClass().contains("windowsHide"));
        }

        if(platform == org.controlsfx.tools.Platform.UNIX || !TrayManager.isSupported()) {
            viewMenu.getItems().remove(minimizeToTray);
        }
    }

    public void showIntroduction(ActionEvent event) {
        WelcomeDialog welcomeDialog = new WelcomeDialog();
        Optional<Mode> optionalMode = welcomeDialog.showAndWait();
        if(optionalMode.isPresent() && optionalMode.get().equals(Mode.ONLINE)) {
            PreferencesDialog preferencesDialog = new PreferencesDialog(PreferenceGroup.SERVER);
            preferencesDialog.showAndWait();
        }
    }

    public void showDocumentation(ActionEvent event) throws IOException {
        AppServices.get().getApplication().getHostServices().showDocument("https://sparrowwallet.com/docs");
    }

    public void showLogFile(ActionEvent event) throws IOException {
        File logFile = new File(Storage.getSparrowHome(), "sparrow.log");
        if(logFile.exists()) {
            AppServices.get().getApplication().getHostServices().showDocument(logFile.toPath().toUri().toString());
        } else {
            AppServices.showErrorDialog("Log file unavailable", "Cannot find log file at " + logFile.getCanonicalPath());
        }
    }

    public void submitBugReport(ActionEvent event) throws IOException {
        AppServices.get().getApplication().getHostServices().showDocument("https://sparrowwallet.com/submitbugreport");
    }

    public void showAbout(ActionEvent event) {
        Stage aboutStage = getAboutStage();
        aboutStage.show();
    }

    private Stage getAboutStage() {
        try {
            FXMLLoader loader = new FXMLLoader(AppController.class.getResource("about.fxml"));
            Parent root = loader.load();
            AboutController controller = loader.getController();

            Stage stage = new Stage();
            stage.setTitle("About " + MainApp.APP_NAME);
            stage.initStyle(org.controlsfx.tools.Platform.getCurrent() == org.controlsfx.tools.Platform.OSX ? StageStyle.UNDECORATED : StageStyle.DECORATED);
            stage.setResizable(false);
            stage.setScene(new Scene(root));
            controller.setStage(stage);
            controller.initializeView();
            setStageIcon(stage);

            return stage;
        } catch(IOException e) {
            log.error("Error loading about stage", e);
        }

        return null;
    }

    public void installUdevRules(ActionEvent event) {
        Hwi.EnumerateService enumerateService = new Hwi.EnumerateService(null);
        enumerateService.setOnSucceeded(workerStateEvent -> {
            Platform.runLater(this::showInstallUdevMessage);
        });
        enumerateService.setOnFailed(workerStateEvent -> {
            Platform.runLater(this::showInstallUdevMessage);
        });
        enumerateService.start();
    }

    public void showInstallUdevMessage() {
        TextAreaDialog dialog = new TextAreaDialog("sudo " + Config.get().getHwi().getAbsolutePath() + " installudevrules");
        dialog.setTitle("Install Udev Rules");
        dialog.getDialogPane().setHeaderText("Installing udev rules ensures devices can connect over USB.\nThis command requires root privileges.\nOpen a shell and enter the following:");
        dialog.showAndWait();
    }

    public void openTransactionFromFile(ActionEvent event) {
        Stage window = new Stage();

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Open Transaction");
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("All Files", org.controlsfx.tools.Platform.getCurrent().equals(org.controlsfx.tools.Platform.UNIX) ? "*" : "*.*"),
                new FileChooser.ExtensionFilter("PSBT", "*.psbt"),
                new FileChooser.ExtensionFilter("TXN", "*.txn")
        );

        AppServices.moveToActiveWindowScreen(window, 800, 450);
        File file = fileChooser.showOpenDialog(window);
        if (file != null) {
            openTransactionFile(file);
        }
    }

    private void openTransactionFile(File file) {
        for(Tab tab : tabs.getTabs()) {
            TabData tabData = (TabData)tab.getUserData();
            if(tabData instanceof TransactionTabData) {
                TransactionTabData transactionTabData = (TransactionTabData)tabData;
                if(file.equals(transactionTabData.getFile())) {
                    tabs.getSelectionModel().select(tab);
                    return;
                }
            }
        }

        if(file.exists()) {
            try {
                byte[] bytes = new byte[(int)file.length()];
                FileInputStream stream = new FileInputStream(file);
                stream.read(bytes);
                stream.close();
                String name = file.getName();

                try {
                    addTransactionTab(name, file, bytes);
                } catch(ParseException e) {
                    ByteArrayInputStream inputStream = new ByteArrayInputStream(bytes);
                    ByteSource byteSource = new ByteSource() {
                        @Override
                        public InputStream openStream() {
                            return inputStream;
                        }
                    };

                    String text = byteSource.asCharSource(Charsets.UTF_8).read().trim();
                    addTransactionTab(name, file, text);
                }
            } catch(IOException e) {
                showErrorDialog("Error opening file", e.getMessage());
            } catch(PSBTParseException e) {
                showErrorDialog("Invalid PSBT", e.getMessage());
            } catch(TransactionParseException e) {
                showErrorDialog("Invalid transaction", e.getMessage());
            } catch(ParseException e) {
                showErrorDialog("Invalid file", e.getMessage());
            }
        }
    }

    public void openTransactionFromText(ActionEvent event) {
        TextAreaDialog dialog = new TextAreaDialog();
        dialog.setTitle("Open from text");
        dialog.getDialogPane().setHeaderText("Paste a transaction or PSBT:");
        Optional<String> text = dialog.showAndWait();
        if(text.isPresent() && !text.get().isEmpty()) {
            try {
                addTransactionTab(null, null, text.get().trim());
            } catch(PSBTParseException e) {
                showErrorDialog("Invalid PSBT", e.getMessage());
            } catch(TransactionParseException e) {
                showErrorDialog("Invalid transaction", e.getMessage());
            } catch(ParseException e) {
                showErrorDialog("Could not recognise input", e.getMessage());
            }
        }
    }

    public void openTransactionFromId(ActionEvent event) {
        TransactionIdDialog dialog = new TransactionIdDialog();
        Optional<Sha256Hash> optionalTxId = dialog.showAndWait();
        if(optionalTxId.isPresent()) {
            Sha256Hash txId = optionalTxId.get();
            ElectrumServer.TransactionReferenceService transactionReferenceService = new ElectrumServer.TransactionReferenceService(Set.of(txId));
            transactionReferenceService.setOnSucceeded(successEvent -> {
                BlockTransaction blockTransaction = transactionReferenceService.getValue().get(txId);
                if(blockTransaction == null) {
                    showErrorDialog("Invalid transaction ID", "A transaction with that ID could not be found.");
                } else {
                    Platform.runLater(() -> {
                        EventManager.get().post(new ViewTransactionEvent(tabs.getScene().getWindow(), blockTransaction));
                    });
                }
            });
            transactionReferenceService.setOnFailed(failEvent -> {
                Platform.runLater(() -> {
                    Throwable e = failEvent.getSource().getException();
                    log.error("Error fetching transaction " + txId.toString(), e);
                    showErrorDialog("Error fetching transaction", "The server returned an error when fetching the transaction. The server response is contained in sparrow.log");
                });
            });
            transactionReferenceService.start();
        }
    }

    public void openTransactionFromQR(ActionEvent event) {
        QRScanDialog qrScanDialog = new QRScanDialog();
        Optional<QRScanDialog.Result> optionalResult = qrScanDialog.showAndWait();
        if(optionalResult.isPresent()) {
            QRScanDialog.Result result = optionalResult.get();
            if(result.transaction != null) {
                addTransactionTab(null, null, result.transaction);
            } else if(result.psbt != null) {
                addTransactionTab(null, null, result.psbt);
            } else if(result.exception != null) {
                log.error("Error scanning QR", result.exception);
                showErrorDialog("Error scanning QR", result.exception.getMessage());
            } else {
                AppServices.showErrorDialog("Invalid QR Code", "Cannot parse QR code into a transaction or PSBT");
            }
        }
    }

    public void saveTransaction(ActionEvent event) {
        Tab selectedTab = tabs.getSelectionModel().getSelectedItem();
        TabData tabData = (TabData)selectedTab.getUserData();
        if(tabData.getType() == TabData.TabType.TRANSACTION) {
            TransactionTabData transactionTabData = (TransactionTabData)tabData;
            Transaction transaction = transactionTabData.getTransaction();

            Stage window = new Stage();
            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("Save Transaction");

            String fileName = ((Label)selectedTab.getGraphic()).getText();
            if(fileName != null && !fileName.isEmpty()) {
               if(fileName.endsWith(".psbt")) {
                   fileName = fileName.substring(0, fileName.length() - ".psbt".length());
               }

               if(!fileName.endsWith(".txn")) {
                   fileName += ".txn";
               }

               fileChooser.setInitialFileName(fileName);
            }

            AppServices.moveToActiveWindowScreen(window, 800, 450);
            File file = fileChooser.showSaveDialog(window);
            if(file != null) {
                try(PrintWriter writer = new PrintWriter(file, StandardCharsets.UTF_8)) {
                    writer.print(Utils.bytesToHex(transaction.bitcoinSerialize()));
                } catch(IOException e) {
                    log.error("Error saving transaction", e);
                    AppServices.showErrorDialog("Error saving transaction", "Cannot write to " + file.getAbsolutePath());
                }
            }
        }
    }

    public void savePSBTBinary(ActionEvent event) {
        savePSBT(false, true);
    }

    public void savePSBTText(ActionEvent event) {
        savePSBT(true, true);
    }

    public void savePSBTBinaryNoXpubs(ActionEvent event) {
        savePSBT(false, false);
    }

    public void savePSBTTextNoXpubs(ActionEvent event) {
        savePSBT(true, false);
    }

    public void savePSBT(boolean asText, boolean includeXpubs) {
        Tab selectedTab = tabs.getSelectionModel().getSelectedItem();
        TabData tabData = (TabData)selectedTab.getUserData();
        if(tabData.getType() == TabData.TabType.TRANSACTION) {
            TransactionTabData transactionTabData = (TransactionTabData)tabData;

            Stage window = new Stage();
            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("Save PSBT");

            String fileName = ((Label)selectedTab.getGraphic()).getText();
            if(fileName != null && !fileName.isEmpty()) {
                if(!fileName.endsWith(".psbt")) {
                    fileName += ".psbt";
                }

                if(asText) {
                    fileName += ".txt";
                }

                fileChooser.setInitialFileName(fileName);
            }

            AppServices.moveToActiveWindowScreen(window, 800, 450);
            File file = fileChooser.showSaveDialog(window);
            if(file != null) {
                if(!asText && !file.getName().toLowerCase().endsWith(".psbt")) {
                    file = new File(file.getAbsolutePath() + ".psbt");
                }

                try(FileOutputStream outputStream = new FileOutputStream(file)) {
                    if(asText) {
                        PrintWriter writer = new PrintWriter(new OutputStreamWriter(outputStream, StandardCharsets.UTF_8));
                        writer.print(transactionTabData.getPsbt().toBase64String(includeXpubs));
                        writer.flush();
                    } else {
                        outputStream.write(transactionTabData.getPsbt().serialize(includeXpubs));
                    }
                } catch(IOException e) {
                    log.error("Error saving PSBT", e);
                    AppServices.showErrorDialog("Error saving PSBT", "Cannot write to " + file.getAbsolutePath());
                }
            }
        }
    }

    public List<WalletTabData> getOpenWalletTabData() {
        List<WalletTabData> openWalletTabData = new ArrayList<>();

        for(Tab tab : tabs.getTabs()) {
            TabData tabData = (TabData)tab.getUserData();
            if(tabData.getType() == TabData.TabType.WALLET) {
                openWalletTabData.add((WalletTabData)tabData);
            }
        }

        return openWalletTabData;
    }

    public Map<Wallet, Storage> getOpenWallets() {
        Map<Wallet, Storage> openWallets = new LinkedHashMap<>();

        for(WalletTabData walletTabData : getOpenWalletTabData()){
            openWallets.put(walletTabData.getWallet(), walletTabData.getStorage());
        }

        return openWallets;
    }

    public void selectTab(Wallet wallet) {
        for(Tab tab : tabs.getTabs()) {
            TabData tabData = (TabData) tab.getUserData();
            if(tabData.getType() == TabData.TabType.WALLET) {
                WalletTabData walletTabData = (WalletTabData) tabData;
                if(walletTabData.getWallet() == wallet) {
                    tabs.getSelectionModel().select(tab);
                }
            }
        }
    }

    public void closeTab(ActionEvent event) {
        tabs.getTabs().remove(tabs.getSelectionModel().getSelectedItem());
    }

    public void quit(ActionEvent event) {
        try {
            Platform.exit();
        } catch (Exception e) {
            log.error("Error quitting application", e);
        }
    }

    public void openWalletsInNewWindows(ActionEvent event) {
        CheckMenuItem item = (CheckMenuItem)event.getSource();
        Config.get().setOpenWalletsInNewWindows(item.isSelected());
        EventManager.get().post(new OpenWalletsNewWindowsStatusEvent(item.isSelected()));
    }

    public void hideEmptyUsedAddresses(ActionEvent event) {
        CheckMenuItem item = (CheckMenuItem)event.getSource();
        Config.get().setHideEmptyUsedAddresses(item.isSelected());
        EventManager.get().post(new HideEmptyUsedAddressesStatusEvent(item.isSelected()));
    }

    public void useHdCameraResolution(ActionEvent event) {
        CheckMenuItem item = (CheckMenuItem)event.getSource();
        Config.get().setHdCapture(item.isSelected());
    }

    public void showLoadingLog(ActionEvent event) {
        CheckMenuItem item = (CheckMenuItem)event.getSource();
        Config.get().setShowLoadingLog(item.isSelected());
        EventManager.get().post(new LoadingLogChangedEvent(item.isSelected()));
    }

    public void showTxHex(ActionEvent event) {
        CheckMenuItem item = (CheckMenuItem)event.getSource();
        Config.get().setShowTransactionHex(item.isSelected());
        EventManager.get().post(new TransactionTabChangedEvent(tabs.getSelectionModel().getSelectedItem(), item.isSelected()));
    }

    public void setBitcoinUnit(ActionEvent event) {
        MenuItem item = (MenuItem)event.getSource();
        BitcoinUnit unit = (BitcoinUnit)item.getUserData();
        Config.get().setBitcoinUnit(unit);
        EventManager.get().post(new BitcoinUnitChangedEvent(unit));
    }

    private boolean isWalletFile(File file) {
        FileType fileType = IOUtils.getFileType(file);
        return FileType.JSON.equals(fileType) || FileType.BINARY.equals(fileType);
    }

    private void setServerToggleTooltip(Integer currentBlockHeight) {
        Tooltip tooltip = new Tooltip(getServerToggleTooltipText(currentBlockHeight));
        tooltip.setShowDuration(Duration.seconds(15));
        serverToggle.setTooltip(tooltip);
    }

    private String getServerToggleTooltipText(Integer currentBlockHeight) {
        if(AppServices.isConnected()) {
            return "Connected to " + Config.get().getServerAddress() + (currentBlockHeight != null ? " at height " + currentBlockHeight : "") +
                    (Config.get().getServerType() == ServerType.PUBLIC_ELECTRUM_SERVER ? "\nWarning! You are connected to a public server and sharing your transaction data with it.\nFor better privacy, consider using your own Bitcoin Core node or private Electrum server." : "");
        }

        return "Disconnected";
    }

    public void newWallet(ActionEvent event) {
        WalletNameDialog dlg = new WalletNameDialog();
        Optional<WalletNameDialog.NameAndBirthDate> optNameAndBirthDate = dlg.showAndWait();
        if(optNameAndBirthDate.isPresent()) {
            WalletNameDialog.NameAndBirthDate nameAndBirthDate = optNameAndBirthDate.get();
            File walletFile = Storage.getWalletFile(nameAndBirthDate.getName());
            Storage storage = new Storage(walletFile);
            Wallet wallet = new Wallet(nameAndBirthDate.getName(), PolicyType.SINGLE, ScriptType.P2WPKH, nameAndBirthDate.getBirthDate());
            addWalletTabOrWindow(storage, wallet, null, false);
        }
    }

    public void openWallet(ActionEvent event) {
        openWallet(false);
    }

    public void openWallet(boolean forceSameWindow) {
        Stage window = new Stage();
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Open Wallet");
        fileChooser.setInitialDirectory(Storage.getWalletsDir());

        AppServices.moveToActiveWindowScreen(window, 800, 450);
        File file = fileChooser.showOpenDialog(window);
        if(file != null) {
            openWalletFile(file, forceSameWindow);
        }
    }

    public void openWalletFile(File file, boolean forceSameWindow) {
        try {
            Storage storage = new Storage(file);
            FileType fileType = IOUtils.getFileType(file);
            if(FileType.JSON.equals(fileType)) {
                Storage.WalletBackupAndKey walletBackupAndKey = storage.loadWallet();
                checkWalletNetwork(walletBackupAndKey.wallet);
                restorePublicKeysFromSeed(walletBackupAndKey.wallet, null);
                if(!walletBackupAndKey.wallet.isValid()) {
                    throw new IllegalStateException("Wallet file is not valid.");
                }
                addWalletTabOrWindow(storage, walletBackupAndKey.wallet, walletBackupAndKey.backupWallet, forceSameWindow);
            } else if(FileType.BINARY.equals(fileType)) {
                WalletPasswordDialog dlg = new WalletPasswordDialog(file.getName(), WalletPasswordDialog.PasswordRequirement.LOAD);
                Optional<SecureString> optionalPassword = dlg.showAndWait();
                if(optionalPassword.isEmpty()) {
                    return;
                }

                SecureString password = optionalPassword.get();
                Storage.LoadWalletService loadWalletService = new Storage.LoadWalletService(storage, password);
                loadWalletService.setOnSucceeded(workerStateEvent -> {
                    EventManager.get().post(new StorageEvent(storage.getWalletFile(), TimedEvent.Action.END, "Done"));
                    Storage.WalletBackupAndKey walletBackupAndKey = loadWalletService.getValue();
                    try {
                        checkWalletNetwork(walletBackupAndKey.wallet);
                        restorePublicKeysFromSeed(walletBackupAndKey.wallet, walletBackupAndKey.key);
                        addWalletTabOrWindow(storage, walletBackupAndKey.wallet, walletBackupAndKey.backupWallet, forceSameWindow);
                    } catch(Exception e) {
                        showErrorDialog("Error Opening Wallet", e.getMessage());
                    } finally {
                        walletBackupAndKey.key.clear();
                    }
                });
                loadWalletService.setOnFailed(workerStateEvent -> {
                    EventManager.get().post(new StorageEvent(storage.getWalletFile(), TimedEvent.Action.END, "Failed"));
                    Throwable exception = loadWalletService.getException();
                    if(exception instanceof InvalidPasswordException) {
                        Optional<ButtonType> optResponse = showErrorDialog("Invalid Password", "The wallet password was invalid. Try again?", ButtonType.CANCEL, ButtonType.OK);
                        if(optResponse.isPresent() && optResponse.get().equals(ButtonType.OK)) {
                            Platform.runLater(() -> openWalletFile(file, forceSameWindow));
                        }
                    } else {
                        if(!attemptImportWallet(file, password)) {
                            log.error("Error Opening Wallet", exception);
                            showErrorDialog("Error Opening Wallet", exception.getMessage() == null ? "Unsupported file format" : exception.getMessage());
                        }
                        password.clear();
                    }
                });
                EventManager.get().post(new StorageEvent(storage.getWalletFile(), TimedEvent.Action.START, "Decrypting wallet..."));
                loadWalletService.start();
            } else {
                throw new IOException("Unsupported file type");
            }
        } catch(Exception e) {
            if(!attemptImportWallet(file, null)) {
                log.error("Error opening wallet", e);
                showErrorDialog("Error Opening Wallet", e.getMessage() == null ? "Unsupported file format" : e.getMessage());
            }
        }
    }

    private void checkWalletNetwork(Wallet wallet) {
        if(wallet.getNetwork() != null && wallet.getNetwork() != Network.get()) {
            throw new IllegalStateException("Provided " + wallet.getNetwork() + " wallet is invalid on a " + Network.get() + " network. Use a " + wallet.getNetwork() + " configuration to load this wallet.");
        }
    }

    private void restorePublicKeysFromSeed(Wallet wallet, Key key) throws MnemonicException {
        if(wallet.containsPrivateKeys()) {
            //Derive xpub and master fingerprint from seed, potentially with passphrase
            Wallet copy = wallet.copy();
            for(Keystore copyKeystore : copy.getKeystores()) {
                if(copyKeystore.hasSeed()) {
                    if(copyKeystore.getSeed().needsPassphrase()) {
                        KeystorePassphraseDialog passphraseDialog = new KeystorePassphraseDialog(wallet.getName(), copyKeystore);
                        Optional<String> optionalPassphrase = passphraseDialog.showAndWait();
                        if(optionalPassphrase.isPresent()) {
                            copyKeystore.getSeed().setPassphrase(optionalPassphrase.get());
                        } else {
                            return;
                        }
                    } else {
                        copyKeystore.getSeed().setPassphrase("");
                    }
                }
            }

            if(wallet.isEncrypted()) {
                if(key == null) {
                    throw new IllegalStateException("Wallet was not encrypted, but seed is");
                }

                copy.decrypt(key);
            }

            for(int i = 0; i < wallet.getKeystores().size(); i++) {
                Keystore keystore = wallet.getKeystores().get(i);
                if(keystore.hasSeed()) {
                    Keystore copyKeystore = copy.getKeystores().get(i);
                    Keystore derivedKeystore = Keystore.fromSeed(copyKeystore.getSeed(), copyKeystore.getKeyDerivation().getDerivation());
                    keystore.setKeyDerivation(derivedKeystore.getKeyDerivation());
                    keystore.setExtendedPublicKey(derivedKeystore.getExtendedPublicKey());
                    keystore.getSeed().setPassphrase(copyKeystore.getSeed().getPassphrase());
                    copyKeystore.getSeed().clear();
                } else if(keystore.hasMasterPrivateExtendedKey()) {
                    Keystore copyKeystore = copy.getKeystores().get(i);
                    Keystore derivedKeystore = Keystore.fromMasterPrivateExtendedKey(copyKeystore.getMasterPrivateExtendedKey(), copyKeystore.getKeyDerivation().getDerivation());
                    keystore.setKeyDerivation(derivedKeystore.getKeyDerivation());
                    keystore.setExtendedPublicKey(derivedKeystore.getExtendedPublicKey());
                    copyKeystore.getMasterPrivateKey().clear();
                }
            }
        }
    }

    public void importWallet(ActionEvent event) {
        WalletImportDialog dlg = new WalletImportDialog();
        Optional<Wallet> optionalWallet = dlg.showAndWait();
        if(optionalWallet.isPresent()) {
            Wallet wallet = optionalWallet.get();
            addImportedWallet(wallet);
        }
    }

    private boolean attemptImportWallet(File file, SecureString password) {
        List<WalletImport> walletImporters = List.of(new ColdcardSinglesig(), new ColdcardMultisig(),
                new Electrum(),
                new SpecterDesktop(),
                new CoboVaultSinglesig(), new CoboVaultMultisig(),
                new PassportSinglesig());
        for(WalletImport importer : walletImporters) {
            try(FileInputStream inputStream = new FileInputStream(file)) {
                if(importer.isEncrypted(file) && password == null) {
                    WalletPasswordDialog dlg = new WalletPasswordDialog(file.getName(), WalletPasswordDialog.PasswordRequirement.LOAD);
                    Optional<SecureString> optionalPassword = dlg.showAndWait();
                    if(optionalPassword.isPresent()) {
                        password = optionalPassword.get();
                    }
                }

                Wallet wallet = importer.importWallet(inputStream, password == null ? null: password.asString());
                if(wallet.getName() == null) {
                    wallet.setName(file.getName());
                }
                addImportedWallet(wallet);
                return true;
            } catch(Exception e) {
                //ignore
            }
        }

        return false;
    }

    private void addImportedWallet(Wallet wallet) {
        WalletNameDialog nameDlg = new WalletNameDialog(wallet.getName());
        Optional<WalletNameDialog.NameAndBirthDate> optNameAndBirthDate = nameDlg.showAndWait();
        if(optNameAndBirthDate.isPresent()) {
            WalletNameDialog.NameAndBirthDate nameAndBirthDate = optNameAndBirthDate.get();
            wallet.setName(nameAndBirthDate.getName());
            wallet.setBirthDate(nameAndBirthDate.getBirthDate());
        } else {
            return;
        }

        File walletFile = Storage.getExistingWallet(wallet.getName());
        if(walletFile != null) {
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
            AppServices.setStageIcon(alert.getDialogPane().getScene().getWindow());
            alert.setTitle("Existing wallet found");
            alert.setHeaderText("Replace existing wallet?");
            alert.setContentText("Wallet file " + walletFile.getName() + " already exists.\n");
            AppServices.moveToActiveWindowScreen(alert);
            Optional<ButtonType> result = alert.showAndWait();
            if(result.isPresent() && result.get() == ButtonType.CANCEL) {
                return;
            }

            //Close existing wallet first if open
            for(Iterator<Tab> iter = tabs.getTabs().iterator(); iter.hasNext(); ) {
                Tab tab = iter.next();
                TabData tabData = (TabData)tab.getUserData();
                if(tabData.getType() == TabData.TabType.WALLET) {
                    WalletTabData walletTabData = (WalletTabData) tabData;
                    if(walletTabData.getStorage().getWalletFile().equals(walletFile)) {
                        iter.remove();
                    }
                }
            }

            walletFile.delete();
        }

        Storage storage = new Storage(Storage.getWalletFile(wallet.getName()));
        WalletPasswordDialog dlg = new WalletPasswordDialog(wallet.getName(), WalletPasswordDialog.PasswordRequirement.UPDATE_NEW);
        Optional<SecureString> password = dlg.showAndWait();
        if(password.isPresent()) {
            if(password.get().length() == 0) {
                storage.setEncryptionPubKey(Storage.NO_PASSWORD_KEY);
                addWalletTabOrWindow(storage, wallet, null, false);
            } else {
                Storage.KeyDerivationService keyDerivationService = new Storage.KeyDerivationService(storage, password.get());
                keyDerivationService.setOnSucceeded(workerStateEvent -> {
                    EventManager.get().post(new StorageEvent(Storage.getWalletFile(wallet.getName()), TimedEvent.Action.END, "Done"));
                    ECKey encryptionFullKey = keyDerivationService.getValue();
                    Key key = null;

                    try {
                        ECKey encryptionPubKey = ECKey.fromPublicOnly(encryptionFullKey);
                        key = new Key(encryptionFullKey.getPrivKeyBytes(), storage.getKeyDeriver().getSalt(), EncryptionType.Deriver.ARGON2);
                        wallet.encrypt(key);
                        storage.setEncryptionPubKey(encryptionPubKey);
                        addWalletTabOrWindow(storage, wallet, null, false);
                    } finally {
                        encryptionFullKey.clear();
                        if(key != null) {
                            key.clear();
                        }
                    }
                });
                keyDerivationService.setOnFailed(workerStateEvent -> {
                    EventManager.get().post(new StorageEvent(Storage.getWalletFile(wallet.getName()), TimedEvent.Action.END, "Failed"));
                    showErrorDialog("Error encrypting wallet", keyDerivationService.getException().getMessage());
                });
                EventManager.get().post(new StorageEvent(Storage.getWalletFile(wallet.getName()), TimedEvent.Action.START, "Encrypting wallet..."));
                keyDerivationService.start();
            }
        }
    }

    public void exportWallet(ActionEvent event) {
        Tab selectedTab = tabs.getSelectionModel().getSelectedItem();
        TabData tabData = (TabData)selectedTab.getUserData();
        if(tabData.getType() == TabData.TabType.WALLET) {
            WalletTabData walletTabData = (WalletTabData)tabData;
            WalletExportDialog dlg = new WalletExportDialog(walletTabData.getWallet());
            Optional<Wallet> wallet = dlg.showAndWait();
            if(wallet.isPresent()) {
                //Successful export
            }
        }
    }

    public void openPreferences(ActionEvent event) {
        PreferencesDialog preferencesDialog = new PreferencesDialog();
        preferencesDialog.showAndWait();
    }

    public void signVerifyMessage(ActionEvent event) {
        MessageSignDialog messageSignDialog = null;
        Tab tab = tabs.getSelectionModel().getSelectedItem();
        if(tab != null && tab.getUserData() instanceof WalletTabData) {
            WalletTabData walletTabData = (WalletTabData)tab.getUserData();
            Wallet wallet = walletTabData.getWallet();
            if(wallet.getKeystores().size() == 1 &&
                    (wallet.getKeystores().get(0).hasPrivateKey() || wallet.getKeystores().get(0).getSource() == KeystoreSource.HW_USB)) {
                //Can sign and verify
                messageSignDialog = new MessageSignDialog(wallet);
            }
        }

        if(messageSignDialog == null) {
            //Can verify only
            messageSignDialog = new MessageSignDialog();
        }

        messageSignDialog.showAndWait();
    }

    public void minimizeToTray(ActionEvent event) {
        AppServices.get().minimizeStage((Stage)tabs.getScene().getWindow());
    }

    public void refreshWallet(ActionEvent event) {
        Tab selectedTab = tabs.getSelectionModel().getSelectedItem();
        TabData tabData = (TabData)selectedTab.getUserData();
        if(tabData.getType() == TabData.TabType.WALLET) {
            WalletTabData walletTabData = (WalletTabData) tabData;
            Wallet wallet = walletTabData.getWallet();
            Wallet pastWallet = wallet.copy();
            walletTabData.getStorage().backupTempWallet();
            wallet.clearHistory();
            AppServices.clearTransactionHistoryCache(wallet);
            EventManager.get().post(new WalletAddressesChangedEvent(wallet, pastWallet, walletTabData.getStorage().getWalletFile()));
        }
    }

    public void addWalletTabOrWindow(Storage storage, Wallet wallet, Wallet backupWallet, boolean forceSameWindow) {
        Window existingWalletWindow = AppServices.get().getWindowForWallet(storage);
        if(existingWalletWindow instanceof Stage) {
            Stage existingWalletStage = (Stage)existingWalletWindow;
            existingWalletStage.toFront();

            EventManager.get().post(new ViewWalletEvent(existingWalletWindow, wallet, storage));
            return;
        }

        if(!forceSameWindow && Config.get().isOpenWalletsInNewWindows() && !getOpenWallets().isEmpty()) {
            Stage stage = new Stage();
            AppController appController = AppServices.newAppWindow(stage);
            stage.toFront();
            stage.setX(AppServices.get().getWalletWindowMaxX() + 30);
            appController.addWalletTab(storage, wallet, backupWallet);
        } else {
            addWalletTab(storage, wallet, backupWallet);
        }
    }

    public void addWalletTab(Storage storage, Wallet wallet, Wallet backupWallet) {
        try {
            String name = storage.getWalletFile().getName();
            if(name.endsWith(".json")) {
                name = name.substring(0, name.lastIndexOf('.'));
            }
            if(!name.equals(wallet.getName())) {
                wallet.setName(name);
            }
            Tab tab = new Tab("");
            Glyph glyph = new Glyph(FontAwesome5.FONT_NAME, FontAwesome5.Glyph.WALLET);
            glyph.setFontSize(10.0);
            glyph.setOpacity(TAB_LABEL_GRAPHIC_OPACITY_ACTIVE);
            Label tabLabel = new Label(name);
            tabLabel.setGraphic(glyph);
            tabLabel.setGraphicTextGap(5.0);
            tab.setGraphic(tabLabel);
            tab.setContextMenu(getTabContextMenu(tab));
            tab.setClosable(true);
            FXMLLoader walletLoader = new FXMLLoader(getClass().getResource("wallet/wallet.fxml"));
            tab.setContent(walletLoader.load());
            WalletController controller = walletLoader.getController();

            EventManager.get().post(new WalletOpeningEvent(storage, wallet));

            //Note that only one WalletForm is created per wallet tab, and registered to listen for events. All wallet controllers (except SettingsController) share this instance.
            WalletForm walletForm = new WalletForm(storage, wallet, backupWallet);
            EventManager.get().register(walletForm);
            controller.setWalletForm(walletForm);

            TabData tabData = new WalletTabData(TabData.TabType.WALLET, walletForm);
            tab.setUserData(tabData);

            tabs.getTabs().add(tab);
            tabs.getSelectionModel().select(tab);
        } catch(IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void openExamples(ActionEvent event) {
        try {
            addTransactionTab("p2pkh", null, "01000000019c2e0f24a03e72002a96acedb12a632e72b6b74c05dc3ceab1fe78237f886c48010000006a47304402203da9d487be5302a6d69e02a861acff1da472885e43d7528ed9b1b537a8e2cac9022002d1bca03a1e9715a99971bafe3b1852b7a4f0168281cbd27a220380a01b3307012102c9950c622494c2e9ff5a003e33b690fe4832477d32c2d256c67eab8bf613b34effffffff02b6f50500000000001976a914bdf63990d6dc33d705b756e13dd135466c06b3b588ac845e0201000000001976a9145fb0e9755a3424efd2ba0587d20b1e98ee29814a88ac06241559");
            addTransactionTab("p2sh", null, "0100000003a5ee1a0fd80dfbc3142df136ab56e082b799c13aa977c048bdf8f61bd158652c000000006b48304502203b0160de302cded63589a88214fe499a25aa1d86a2ea09129945cd632476a12c022100c77727daf0718307e184d55df620510cf96d4b5814ae3258519c0482c1ca82fa0121024f4102c1f1cf662bf99f2b034eb03edd4e6c96793cb9445ff519aab580649120ffffffff0fce901eb7b7551ba5f414735ff93b83a2a57403df11059ec88245fba2aaf1a0000000006a47304402204089adb8a1de1a9e22aa43b94d54f1e54dc9bea745d57df1a633e03dd9ede3c2022037d1e53e911ed7212186028f2e085f70524930e22eb6184af090ba4ab779a5b90121030644cb394bf381dbec91680bdf1be1986ad93cfb35603697353199fb285a119effffffff0fce901eb7b7551ba5f414735ff93b83a2a57403df11059ec88245fba2aaf1a0010000009300493046022100a07b2821f96658c938fa9c68950af0e69f3b2ce5f8258b3a6ad254d4bc73e11e022100e82fab8df3f7e7a28e91b3609f91e8ebf663af3a4dc2fd2abd954301a5da67e701475121022afc20bf379bc96a2f4e9e63ffceb8652b2b6a097f63fbee6ecec2a49a48010e2103a767c7221e9f15f870f1ad9311f5ab937d79fcaeee15bb2c722bca515581b4c052aeffffffff02a3b81b00000000001976a914ea00917f128f569cbdf79da5efcd9001671ab52c88ac80969800000000001976a9143dec0ead289be1afa8da127a7dbdd425a05e25f688ac00000000");
            addTransactionTab("p2sh-p2wpkh", null, "01000000000101db6b1b20aa0fd7b23880be2ecbd4a98130974cf4748fb66092ac4d3ceb1a5477010000001716001479091972186c449eb1ded22b78e40d009bdf0089feffffff02b8b4eb0b000000001976a914a457b684d7f0d539a46a45bbc043f35b59d0d96388ac0008af2f000000001976a914fd270b1ee6abcaea97fea7ad0402e8bd8ad6d77c88ac02473044022047ac8e878352d3ebbde1c94ce3a10d057c24175747116f8288e5d794d12d482f0220217f36a485cae903c713331d877c1f64677e3622ad4010726870540656fe9dcb012103ad1d8e89212f0b92c74d23bb710c00662ad1470198ac48c43f7d6f93a2a2687392040000");
            addTransactionTab("p2sh-p2wsh", null, "01000000000101708256c5896fb3f00ef37601f8e30c5b460dbcd1fca1cd7199f9b56fc4ecd5400000000023220020615ae01ed1bc1ffaad54da31d7805d0bb55b52dfd3941114330368c1bbf69b4cffffffff01603edb0300000000160014bbef244bcad13cffb68b5cef3017c7423675552204004730440220010d2854b86b90b7c33661ca25f9d9f15c24b88c5c4992630f77ff004b998fb802204106fc3ec8481fa98e07b7e78809ac91b6ccaf60bf4d3f729c5a75899bb664a501473044022046d66321c6766abcb1366a793f9bfd0e11e0b080354f18188588961ea76c5ad002207262381a0661d66f5c39825202524c45f29d500c6476176cd910b1691176858701695221026ccfb8061f235cc110697c0bfb3afb99d82c886672f6b9b5393b25a434c0cbf32103befa190c0c22e2f53720b1be9476dcf11917da4665c44c9c71c3a2d28a933c352102be46dc245f58085743b1cc37c82f0d63a960efa43b5336534275fc469b49f4ac53ae00000000");
            addTransactionTab("p2wpkh", null, "01000000000101109d2e41430bfdec7e6dfb02bf78b5827eeb717ef25210ff3203b0db8c76c9260000000000ffffffff01a032eb0500000000160014bbef244bcad13cffb68b5cef3017c742367555220247304402202f7cac3494e521018ae0be4ca18517639ef7c00658d42a9f938b2b344c8454e2022039a54218832fad5d14b331329d9042c51ee6be287e95e49ee5b96fda1f5ce13f0121026ccfb8061f235cc110697c0bfb3afb99d82c886672f6b9b5393b25a434c0cbf300000000");
            addTransactionTab("p2wsh", null, "0100000000010193a2db37b841b2a46f4e9bb63fe9c1012da3ab7fe30b9f9c974242778b5af8980000000000ffffffff01806fb307000000001976a914bbef244bcad13cffb68b5cef3017c7423675552288ac040047304402203cdcaf02a44e37e409646e8a506724e9e1394b890cb52429ea65bac4cc2403f1022024b934297bcd0c21f22cee0e48751c8b184cc3a0d704cae2684e14858550af7d01483045022100feb4e1530c13e72226dc912dcd257df90d81ae22dbddb5a3c2f6d86f81d47c8e022069889ddb76388fa7948aaa018b2480ac36132009bb9cfade82b651e88b4b137a01695221026ccfb8061f235cc110697c0bfb3afb99d82c886672f6b9b5393b25a434c0cbf32103befa190c0c22e2f53720b1be9476dcf11917da4665c44c9c71c3a2d28a933c352102be46dc245f58085743b1cc37c82f0d63a960efa43b5336534275fc469b49f4ac53ae00000000");
            //addTransactionTab("test1", null, "02000000000102ba4dc5a4a14bfaa941b7d115b379b5e15f960635cf694c178b9116763cbd63b11600000017160014fc164cbcac023f5eacfcead2d17d8768c41949affeffffff074d44d2856beb68ba52e8832da60a1682768c2421c2d9a8109ef4e66babd1fd1e000000171600148c3098be6b430859115f5ee99c84c368afecd0481500400002305310000000000017a914ffaf369c2212b178c7a2c21c9ccdd5d126e74c4187327f0300000000001976a914a7cda2e06b102a143ab606937a01d152e300cd3e88ac02473044022006da0ca227f765179219e08a33026b94e7cacff77f87b8cd8eb1b46d6dda11d6022064faa7912924fd23406b6ed3328f1bbbc3760dc51109a49c1b38bf57029d304f012103c6a2fcd030270427d4abe1041c8af929a9e2dbab07b243673453847ab842ee1f024730440220786316a16095105a0af28dccac5cf80f449dea2ea810a9559a89ecb989c2cb3d02205cbd9913d1217ffec144ae4f2bd895f16d778c2ec49ae9c929fdc8bcc2a2b1db0121024d4985241609d072a59be6418d700e87688f6c4d99a51ad68e66078211f076ee38820900");
            //addTransactionTab("3of3-1s.psbt", null, "70736274ff0100550200000001294c4871c059bb76be81e94b78059ee2e0c9b1b47f38edb6b4e75916062394930000000000feffffff01f82a0000000000001976a914e65b294f890792f2c2725d488567018d660f0cf488ac701c09004f0102aa7ed3044b1635bb800000021bf4bfc48934b7966b39bdebb689525d9b8bfed5c8b16e8c58f9afe4641d6d5f03800b5dbec0355c9f0b5e8227bc903e9d0ff1fe6ced0dcfb6d416541c7412c4331406b57041300000800000008000000080020000804f0102aa7ed3042cd31dee80000002d544b2364010378f8c6cec85f6b7ed83a8203dcdbedb97e2625f431f897b837e0363428de8fcfbfe373c0d9e1e0cc8163d886764bafe71c5822eaa232981356589145f63394f300000800000008000000080020000804f0102aa7ed3049ec7d9f580000002793e04aff18b4e40ebc48bcdc6232c54c69cf7265a38fbd85b35705e34d2d42f03368e79aa2b2b7f736d156905a7a45891df07baa2d0b7f127a537908cb82deed514130a48af300000800000008000000080020000800001012b983a000000000000220020f64748dad1cbad107761aaed5c59f25aba006498d260b440e0a091691350c9aa010569532102f26969eb8d1da34d17d33ff99e2f020cc33b3d11d9798ec14f46b82bc455d3262103171d9b824205cd5db6e9353676a292ca954b24d8310a36fc983469ba3fb507a221037f3794f3be4c4acc086ac84d6902c025713eabf8890f20f44acf0b34e3c0f0f753ae220602f26969eb8d1da34d17d33ff99e2f020cc33b3d11d9798ec14f46b82bc455d3261c130a48af300000800000008000000080020000800000000000000000220603171d9b824205cd5db6e9353676a292ca954b24d8310a36fc983469ba3fb507a21c5f63394f300000800000008000000080020000800000000000000000220203171d9b824205cd5db6e9353676a292ca954b24d8310a36fc983469ba3fb507a24830450221008d27cc4b03bc543726e73b69e7980e7364d6f33f979a5cd9b92fb3d050666bd002204fc81fc9c67baf7c3b77041ed316714a9c117a5bdbb020e8c771ea3bdc342434012206037f3794f3be4c4acc086ac84d6902c025713eabf8890f20f44acf0b34e3c0f0f71c06b570413000008000000080000000800200008000000000000000000000");
            //addTransactionTab("signer.psbt", null, "70736274ff01009a020000000258e87a21b56daf0c23be8e7070456c336f7cbaa5c8757924f545887bb2abdd750000000000ffffffff838d0427d0ec650a68aa46bb0b098aea4422c071b2ca78352a077959d07cea1d0100000000ffffffff0270aaf00800000000160014d85c2b71d0060b09c9886aeb815e50991dda124d00e1f5050000000016001400aea9a2e5f0f876a588df5546e8742d1d87008f00000000000100bb0200000001aad73931018bd25f84ae400b68848be09db706eac2ac18298babee71ab656f8b0000000048473044022058f6fc7c6a33e1b31548d481c826c015bd30135aad42cd67790dab66d2ad243b02204a1ced2604c6735b6393e5b41691dd78b00f0c5942fb9f751856faa938157dba01feffffff0280f0fa020000000017a9140fb9463421696b82c833af241c78c17ddbde493487d0f20a270100000017a91429ca74f8a08f81999428185c97b5d852e4063f618765000000220202dab61ff49a14db6a7d02b0cd1fbb78fc4b18312b5b4e54dae4dba2fbfef536d7483045022100f61038b308dc1da865a34852746f015772934208c6d24454393cd99bdf2217770220056e675a675a6d0a02b85b14e5e29074d8a25a9b5760bea2816f661910a006ea01010304010000000104475221029583bf39ae0a609747ad199addd634fa6108559d6c5cd39b4c2183f1ab96e07f2102dab61ff49a14db6a7d02b0cd1fbb78fc4b18312b5b4e54dae4dba2fbfef536d752ae2206029583bf39ae0a609747ad199addd634fa6108559d6c5cd39b4c2183f1ab96e07f10d90c6a4f000000800000008000000080220602dab61ff49a14db6a7d02b0cd1fbb78fc4b18312b5b4e54dae4dba2fbfef536d710d90c6a4f0000008000000080010000800001012000c2eb0b0000000017a914b7f5faf40e3d40a5a459b1db3535f2b72fa921e8872202023add904f3d6dcf59ddb906b0dee23529b7ffb9ed50e5e86151926860221f0e73473044022065f45ba5998b59a27ffe1a7bed016af1f1f90d54b3aa8f7450aa5f56a25103bd02207f724703ad1edb96680b284b56d4ffcb88f7fb759eabbe08aa30f29b851383d2010103040100000001042200208c2353173743b595dfb4a07b72ba8e42e3797da74e87fe7d9d7497e3b2028903010547522103089dc10c7ac6db54f91329af617333db388cead0c231f723379d1b99030b02dc21023add904f3d6dcf59ddb906b0dee23529b7ffb9ed50e5e86151926860221f0e7352ae2206023add904f3d6dcf59ddb906b0dee23529b7ffb9ed50e5e86151926860221f0e7310d90c6a4f000000800000008003000080220603089dc10c7ac6db54f91329af617333db388cead0c231f723379d1b99030b02dc10d90c6a4f00000080000000800200008000220203a9a4c37f5996d3aa25dbac6b570af0650394492942460b354753ed9eeca5877110d90c6a4f000000800000008004000080002202027f6399757d2eff55a136ad02c684b1838b6556e5f1b6b34282a94b6b5005109610d90c6a4f00000080000000800500008000");
            //addTransactionTab("combiner.psbt", null, "70736274ff01009a020000000258e87a21b56daf0c23be8e7070456c336f7cbaa5c8757924f545887bb2abdd750000000000ffffffff838d0427d0ec650a68aa46bb0b098aea4422c071b2ca78352a077959d07cea1d0100000000ffffffff0270aaf00800000000160014d85c2b71d0060b09c9886aeb815e50991dda124d00e1f5050000000016001400aea9a2e5f0f876a588df5546e8742d1d87008f00000000000100bb0200000001aad73931018bd25f84ae400b68848be09db706eac2ac18298babee71ab656f8b0000000048473044022058f6fc7c6a33e1b31548d481c826c015bd30135aad42cd67790dab66d2ad243b02204a1ced2604c6735b6393e5b41691dd78b00f0c5942fb9f751856faa938157dba01feffffff0280f0fa020000000017a9140fb9463421696b82c833af241c78c17ddbde493487d0f20a270100000017a91429ca74f8a08f81999428185c97b5d852e4063f6187650000002202029583bf39ae0a609747ad199addd634fa6108559d6c5cd39b4c2183f1ab96e07f473044022074018ad4180097b873323c0015720b3684cc8123891048e7dbcd9b55ad679c99022073d369b740e3eb53dcefa33823c8070514ca55a7dd9544f157c167913261118c01220202dab61ff49a14db6a7d02b0cd1fbb78fc4b18312b5b4e54dae4dba2fbfef536d7483045022100f61038b308dc1da865a34852746f015772934208c6d24454393cd99bdf2217770220056e675a675a6d0a02b85b14e5e29074d8a25a9b5760bea2816f661910a006ea01010304010000000104475221029583bf39ae0a609747ad199addd634fa6108559d6c5cd39b4c2183f1ab96e07f2102dab61ff49a14db6a7d02b0cd1fbb78fc4b18312b5b4e54dae4dba2fbfef536d752ae2206029583bf39ae0a609747ad199addd634fa6108559d6c5cd39b4c2183f1ab96e07f10d90c6a4f000000800000008000000080220602dab61ff49a14db6a7d02b0cd1fbb78fc4b18312b5b4e54dae4dba2fbfef536d710d90c6a4f0000008000000080010000800001012000c2eb0b0000000017a914b7f5faf40e3d40a5a459b1db3535f2b72fa921e887220203089dc10c7ac6db54f91329af617333db388cead0c231f723379d1b99030b02dc473044022062eb7a556107a7c73f45ac4ab5a1dddf6f7075fb1275969a7f383efff784bcb202200c05dbb7470dbf2f08557dd356c7325c1ed30913e996cd3840945db12228da5f012202023add904f3d6dcf59ddb906b0dee23529b7ffb9ed50e5e86151926860221f0e73473044022065f45ba5998b59a27ffe1a7bed016af1f1f90d54b3aa8f7450aa5f56a25103bd02207f724703ad1edb96680b284b56d4ffcb88f7fb759eabbe08aa30f29b851383d2010103040100000001042200208c2353173743b595dfb4a07b72ba8e42e3797da74e87fe7d9d7497e3b2028903010547522103089dc10c7ac6db54f91329af617333db388cead0c231f723379d1b99030b02dc21023add904f3d6dcf59ddb906b0dee23529b7ffb9ed50e5e86151926860221f0e7352ae2206023add904f3d6dcf59ddb906b0dee23529b7ffb9ed50e5e86151926860221f0e7310d90c6a4f000000800000008003000080220603089dc10c7ac6db54f91329af617333db388cead0c231f723379d1b99030b02dc10d90c6a4f00000080000000800200008000220203a9a4c37f5996d3aa25dbac6b570af0650394492942460b354753ed9eeca5877110d90c6a4f000000800000008004000080002202027f6399757d2eff55a136ad02c684b1838b6556e5f1b6b34282a94b6b5005109610d90c6a4f00000080000000800500008000");
            addTransactionTab("finalizer.psbt", null, "70736274ff01009a020000000258e87a21b56daf0c23be8e7070456c336f7cbaa5c8757924f545887bb2abdd750000000000ffffffff838d0427d0ec650a68aa46bb0b098aea4422c071b2ca78352a077959d07cea1d0100000000ffffffff0270aaf00800000000160014d85c2b71d0060b09c9886aeb815e50991dda124d00e1f5050000000016001400aea9a2e5f0f876a588df5546e8742d1d87008f00000000000100bb0200000001aad73931018bd25f84ae400b68848be09db706eac2ac18298babee71ab656f8b0000000048473044022058f6fc7c6a33e1b31548d481c826c015bd30135aad42cd67790dab66d2ad243b02204a1ced2604c6735b6393e5b41691dd78b00f0c5942fb9f751856faa938157dba01feffffff0280f0fa020000000017a9140fb9463421696b82c833af241c78c17ddbde493487d0f20a270100000017a91429ca74f8a08f81999428185c97b5d852e4063f6187650000000107da00473044022074018ad4180097b873323c0015720b3684cc8123891048e7dbcd9b55ad679c99022073d369b740e3eb53dcefa33823c8070514ca55a7dd9544f157c167913261118c01483045022100f61038b308dc1da865a34852746f015772934208c6d24454393cd99bdf2217770220056e675a675a6d0a02b85b14e5e29074d8a25a9b5760bea2816f661910a006ea01475221029583bf39ae0a609747ad199addd634fa6108559d6c5cd39b4c2183f1ab96e07f2102dab61ff49a14db6a7d02b0cd1fbb78fc4b18312b5b4e54dae4dba2fbfef536d752ae0001012000c2eb0b0000000017a914b7f5faf40e3d40a5a459b1db3535f2b72fa921e8870107232200208c2353173743b595dfb4a07b72ba8e42e3797da74e87fe7d9d7497e3b20289030108da0400473044022062eb7a556107a7c73f45ac4ab5a1dddf6f7075fb1275969a7f383efff784bcb202200c05dbb7470dbf2f08557dd356c7325c1ed30913e996cd3840945db12228da5f01473044022065f45ba5998b59a27ffe1a7bed016af1f1f90d54b3aa8f7450aa5f56a25103bd02207f724703ad1edb96680b284b56d4ffcb88f7fb759eabbe08aa30f29b851383d20147522103089dc10c7ac6db54f91329af617333db388cead0c231f723379d1b99030b02dc21023add904f3d6dcf59ddb906b0dee23529b7ffb9ed50e5e86151926860221f0e7352ae00220203a9a4c37f5996d3aa25dbac6b570af0650394492942460b354753ed9eeca5877110d90c6a4f000000800000008004000080002202027f6399757d2eff55a136ad02c684b1838b6556e5f1b6b34282a94b6b5005109610d90c6a4f00000080000000800500008000");
        } catch(Exception e) {
            log.error("Error opening examples", e);
        }
    }

    private void addTransactionTab(String name, File file, String string) throws ParseException, PSBTParseException, TransactionParseException {
        if(Utils.isBase64(string) && !Utils.isHex(string)) {
            addTransactionTab(name, file, Base64.getDecoder().decode(string));
        } else if(Utils.isHex(string)) {
            addTransactionTab(name, file, Utils.hexToBytes(string));
        } else {
            throw new ParseException("Input is not base64 or hex", 0);
        }
    }

    private void addTransactionTab(String name, File file, byte[] bytes) throws PSBTParseException, ParseException, TransactionParseException {
        if(PSBT.isPSBT(bytes)) {
            PSBT psbt = new PSBT(bytes);
            addTransactionTab(name, file, psbt);
        } else if(Transaction.isTransaction(bytes)) {
            try {
                Transaction transaction = new Transaction(bytes);
                addTransactionTab(name, file, transaction);
            } catch(Exception e) {
                throw new TransactionParseException(e.getMessage());
            }
        } else {
            throw new ParseException("Not a valid PSBT or transaction", 0);
        }
    }

    private void addTransactionTab(String name, File file, Transaction transaction) {
        addTransactionTab(name, file, transaction, null, null, null, null);
    }

    private void addTransactionTab(String name, File file, PSBT psbt) {
        Window psbtWalletWindow = AppServices.get().getWindowForPSBT(psbt);
        if(psbtWalletWindow != null && !tabs.getScene().getWindow().equals(psbtWalletWindow)) {
            EventManager.get().post(new ViewPSBTEvent(psbtWalletWindow, name, file, psbt));
            if(psbtWalletWindow instanceof Stage) {
                Stage stage = (Stage)psbtWalletWindow;
                stage.toFront();
            }
        } else {
            addTransactionTab(name, file, psbt.getTransaction(), psbt, null, null, null);
        }
    }

    private void addTransactionTab(BlockTransaction blockTransaction, TransactionView initialView, Integer initialIndex) {
        addTransactionTab(null, null, blockTransaction.getTransaction(), null, blockTransaction, initialView, initialIndex);
    }

    private void addTransactionTab(String name, File file, Transaction transaction, PSBT psbt, BlockTransaction blockTransaction, TransactionView initialView, Integer initialIndex) {
        for(Tab tab : tabs.getTabs()) {
            TabData tabData = (TabData)tab.getUserData();
            if(tabData instanceof TransactionTabData) {
                TransactionTabData transactionTabData = (TransactionTabData)tabData;

                //If an exact match bytewise of an existing tab, return that tab
                if(Arrays.equals(transactionTabData.getTransaction().bitcoinSerialize(), transaction.bitcoinSerialize())) {
                    if(transactionTabData.getPsbt() != null && psbt != null && !transactionTabData.getPsbt().isFinalized()) {
                        if(!psbt.isFinalized()) {
                            //As per BIP174, combine PSBTs with matching transactions so long as they are not yet finalized
                            transactionTabData.getPsbt().combine(psbt);
                            if(name != null && !name.isEmpty()) {
                                ((Label)tab.getGraphic()).setText(name);
                            }

                            EventManager.get().post(new PSBTCombinedEvent(transactionTabData.getPsbt()));
                        } else {
                            //If the new PSBT is finalized, copy the finalized fields to the existing unfinalized PSBT
                            for(int i = 0; i < transactionTabData.getPsbt().getPsbtInputs().size(); i++) {
                                PSBTInput existingInput = transactionTabData.getPsbt().getPsbtInputs().get(i);
                                PSBTInput finalizedInput = psbt.getPsbtInputs().get(i);
                                existingInput.setFinalScriptSig(finalizedInput.getFinalScriptSig());
                                existingInput.setFinalScriptWitness(finalizedInput.getFinalScriptWitness());
                                existingInput.clearNonFinalFields();
                            }

                            if(name != null && !name.isEmpty()) {
                                ((Label)tab.getGraphic()).setText(name);
                            }

                            EventManager.get().post(new PSBTFinalizedEvent(transactionTabData.getPsbt()));
                        }
                    }

                    tabs.getSelectionModel().select(tab);
                    return;
                }
            }
        }

        try {
            String tabName = name;

            if(tabName == null || tabName.isEmpty()) {
                tabName = "[" + transaction.getTxId().toString().substring(0, 6) + "]";
            }

            Tab tab = new Tab("");
            Glyph glyph = new Glyph("FontAwesome", FontAwesome.Glyph.SEND);
            glyph.setFontSize(10.0);
            glyph.setOpacity(TAB_LABEL_GRAPHIC_OPACITY_ACTIVE);
            Label tabLabel = new Label(tabName);
            tabLabel.setGraphic(glyph);
            tabLabel.setGraphicTextGap(5.0);
            tab.setGraphic(tabLabel);
            tab.setContextMenu(getTabContextMenu(tab));
            tab.setClosable(true);
            FXMLLoader transactionLoader = new FXMLLoader(getClass().getResource("transaction/transaction.fxml"));
            tab.setContent(transactionLoader.load());
            TransactionController controller = transactionLoader.getController();

            TransactionData transactionData;
            if(psbt != null) {
                transactionData = new TransactionData(name, psbt);
            } else if(blockTransaction != null) {
                transactionData = new TransactionData(name, blockTransaction);
            } else {
                transactionData = new TransactionData(name, transaction);
            }

            controller.setTransactionData(transactionData);
            if(initialView != null) {
                controller.setInitialView(initialView, initialIndex);
            }
            controller.initializeView();

            TabData tabData = new TransactionTabData(TabData.TabType.TRANSACTION, file, transactionData);
            tab.setUserData(tabData);

            tabs.getTabs().add(tab);
            tabs.getSelectionModel().select(tab);
        } catch(IOException e) {
            throw new RuntimeException(e);
        }
    }

    private ContextMenu getTabContextMenu(Tab tab) {
        ContextMenu contextMenu = new ContextMenu();

        MenuItem close = new MenuItem("Close");
        close.setOnAction(event -> {
            tabs.getTabs().remove(tab);
        });

        MenuItem closeOthers = new MenuItem("Close Others");
        closeOthers.setOnAction(event -> {
            List<Tab> otherTabs = new ArrayList<>(tabs.getTabs());
            otherTabs.remove(tab);
            tabs.getTabs().removeAll(otherTabs);
        });

        MenuItem closeAll = new MenuItem("Close All");
        closeAll.setOnAction(event -> {
            tabs.getTabs().removeAll(tabs.getTabs());
        });

        contextMenu.getItems().addAll(close, closeOthers, closeAll);
        return contextMenu;
    }

    public void setServerType(ServerType serverType) {
        if(serverType == ServerType.PUBLIC_ELECTRUM_SERVER && !serverToggle.getStyleClass().contains("public-server")) {
            serverToggle.getStyleClass().add("public-server");
        } else {
            serverToggle.getStyleClass().remove("public-server");
        }

        if(serverType == ServerType.BITCOIN_CORE && !serverToggle.getStyleClass().contains("core-server")) {
            serverToggle.getStyleClass().add("core-server");
        } else {
            serverToggle.getStyleClass().remove("core-server");
        }

        serverToggle.setDisable(false);
    }

    public void setTheme(ActionEvent event) {
        Theme selectedTheme = (Theme)theme.getSelectedToggle().getUserData();
        if(Config.get().getTheme() != selectedTheme) {
            Config.get().setTheme(selectedTheme);
        }

        EventManager.get().post(new ThemeChangedEvent(selectedTheme));
    }

    private void tabLabelStartAnimation(Wallet wallet) {
        tabs.getTabs().stream().filter(tab -> tab.getUserData() instanceof WalletTabData && ((WalletTabData)tab.getUserData()).getWallet() == wallet).forEach(this::tabLabelStartAnimation);
    }

    private void tabLabelStartAnimation(Transaction transaction) {
        tabs.getTabs().stream().filter(tab -> tab.getUserData() instanceof TransactionTabData && ((TransactionTabData)tab.getUserData()).getTransaction().getTxId().equals(transaction.getTxId())).forEach(this::tabLabelStartAnimation);
    }

    private void tabLabelStartAnimation(Tab tab) {
        Label tabLabel = (Label) tab.getGraphic();
        if(tabLabel.getUserData() == null) {
            FadeTransition fadeTransition = new FadeTransition(Duration.millis(1000), tabLabel.getGraphic());
            fadeTransition.setFromValue(tabLabel.getGraphic().getOpacity());
            fadeTransition.setToValue(0.1);
            fadeTransition.setAutoReverse(true);
            fadeTransition.setCycleCount(Animation.INDEFINITE);
            fadeTransition.play();
            tabLabel.setUserData(fadeTransition);
        }
    }

    private void tabLabelAddFailure(Tab tab) {
        Label tabLabel = (Label)tab.getGraphic();
        if(!tabLabel.getStyleClass().contains("failure")) {
            tabLabel.getGraphic().getStyleClass().add("failure");
        }
    }

    private void tabLabelStopAnimation(Wallet wallet) {
        tabs.getTabs().stream().filter(tab -> tab.getUserData() instanceof WalletTabData && ((WalletTabData)tab.getUserData()).getWallet() == wallet).forEach(this::tabLabelStopAnimation);
    }

    private void tabLabelStopAnimation(Transaction transaction) {
        tabs.getTabs().stream().filter(tab -> tab.getUserData() instanceof TransactionTabData && ((TransactionTabData)tab.getUserData()).getTransaction().getTxId().equals(transaction.getTxId())).forEach(this::tabLabelStopAnimation);
    }

    private void tabLabelStopAnimation(Tab tab) {
        Label tabLabel = (Label) tab.getGraphic();
        if(tabLabel.getUserData() != null) {
            FadeTransition fadeTransition = (FadeTransition)tabLabel.getUserData();
            fadeTransition.stop();
            tabLabel.setUserData(null);
            tabLabel.getGraphic().setOpacity(tab.isSelected() ? TAB_LABEL_GRAPHIC_OPACITY_ACTIVE : TAB_LABEL_GRAPHIC_OPACITY_INACTIVE);
        }
    }

    private void tabLabelRemoveFailure(Tab tab) {
        Label tabLabel = (Label)tab.getGraphic();
        tabLabel.getGraphic().getStyleClass().remove("failure");
    }

    @Subscribe
    public void themeChanged(ThemeChangedEvent event) {
        String darkCss = getClass().getResource("darktheme.css").toExternalForm();
        if(event.getTheme() == Theme.DARK) {
            if(!tabs.getScene().getStylesheets().contains(darkCss)) {
                tabs.getScene().getStylesheets().add(darkCss);
            }
        } else {
            tabs.getScene().getStylesheets().remove(darkCss);
        }
    }

    @Subscribe
    public void serverTypeChanged(ServerTypeChangedEvent event) {
        setServerType(event.getServerType());
    }

    @Subscribe
    public void tabSelected(TabSelectedEvent event) {
        if(tabs.getTabs().contains(event.getTab())) {
            String tabName = event.getTabName();
            if(tabs.getScene() != null) {
                Stage tabStage = (Stage)tabs.getScene().getWindow();
                tabStage.setTitle("Sparrow - " + tabName);
            }

            if(event instanceof TransactionTabSelectedEvent) {
                TransactionTabSelectedEvent txTabEvent = (TransactionTabSelectedEvent)event;
                TransactionTabData transactionTabData = txTabEvent.getTransactionTabData();
                if(transactionTabData.getPsbt() == null || transactionTabData.getPsbt().getTransaction() != transactionTabData.getTransaction()) {
                    saveTransaction.setVisible(true);
                    saveTransaction.setDisable(false);
                } else {
                    saveTransaction.setVisible(false);
                }
                exportWallet.setDisable(true);
                showLoadingLog.setDisable(true);
                showTxHex.setDisable(false);
            } else if(event instanceof WalletTabSelectedEvent) {
                WalletTabSelectedEvent walletTabEvent = (WalletTabSelectedEvent)event;
                WalletTabData walletTabData = walletTabEvent.getWalletTabData();
                saveTransaction.setVisible(true);
                saveTransaction.setDisable(true);
                exportWallet.setDisable(walletTabData.getWallet() == null || !walletTabData.getWallet().isValid());
                showLoadingLog.setDisable(false);
                showTxHex.setDisable(true);
            }
        }
    }

    @Subscribe
    public void transactionExtractedEvent(TransactionExtractedEvent event) {
        for(Tab tab : tabs.getTabs()) {
            TabData tabData = (TabData) tab.getUserData();
            if(tabData instanceof TransactionTabData) {
                TransactionTabData transactionTabData = (TransactionTabData)tabData;
                if(transactionTabData.getTransaction() == event.getFinalTransaction()) {
                    saveTransaction.setVisible(true);
                    saveTransaction.setDisable(false);
                }
            }
        }
    }

    @Subscribe
    public void walletAddressesChanged(WalletAddressesChangedEvent event) {
        Tab tab = tabs.getSelectionModel().getSelectedItem();
        TabData tabData = (TabData)tab.getUserData();
        if(tabData instanceof WalletTabData) {
            WalletTabData walletTabData = (WalletTabData)tabData;
            if(walletTabData.getWalletForm().getWalletFile().equals(event.getWalletFile())) {
                exportWallet.setDisable(!event.getWallet().isValid());
            }
        }
    }

    @Subscribe
    public void newWalletTransactions(NewWalletTransactionsEvent event) {
        if(Config.get().isNotifyNewTransactions() && getOpenWallets().containsKey(event.getWallet())) {
            String text;
            if(event.getBlockTransactions().size() == 1) {
                BlockTransaction blockTransaction = event.getBlockTransactions().get(0);
                if(blockTransaction.getHeight() <= 0) {
                    text = "New mempool transaction: ";
                } else {
                    int confirmations = blockTransaction.getConfirmations(getCurrentBlockHeight());
                    if(confirmations == 1) {
                        text = "First transaction confirmation: ";
                    } else if(confirmations <= BlockTransactionHash.BLOCKS_TO_CONFIRM) {
                        text = "Confirming transaction: ";
                    } else {
                        text = "Confirmed transaction: ";
                    }
                }

                text += event.getValueAsText(event.getTotalValue());
            } else {
                if(event.getTotalBlockchainValue() > 0 && event.getTotalMempoolValue() > 0) {
                    text = "New transactions: " + event.getValueAsText(event.getTotalValue()) + " total (" + event.getValueAsText(event.getTotalMempoolValue())  + " in mempool)";
                } else if(event.getTotalMempoolValue() > 0) {
                    text = "New mempool transactions: " + event.getValueAsText(event.getTotalMempoolValue()) + " total";
                } else {
                    text = "New transactions: " + event.getValueAsText(event.getTotalValue()) + " total";
                }
            }

            Window.getWindows().forEach(window -> {
                String notificationStyles = AppController.class.getResource("notificationpopup.css").toExternalForm();
                if(!window.getScene().getStylesheets().contains(notificationStyles)) {
                    window.getScene().getStylesheets().add(notificationStyles);
                }
            });

            Image image = new Image("image/sparrow-small.png", 50, 50, false, false);
            Notifications notificationBuilder = Notifications.create()
                    .title("Sparrow - " + event.getWallet().getName())
                    .text(text)
                    .graphic(new ImageView(image))
                    .hideAfter(Duration.seconds(15))
                    .position(Pos.TOP_RIGHT)
                    .threshold(5, Notifications.create().title("Sparrow").text("Multiple new wallet transactions").graphic(new ImageView(image)))
                    .onAction(e -> selectTab(event.getWallet()));

            //If controlsfx can't find our window, we must set the window ourselves (unfortunately notification is then shown within this window)
            if(org.controlsfx.tools.Utils.getWindow(null) == null) {
                notificationBuilder.owner(tabs.getScene().getWindow());
            }

            notificationBuilder.show();
        }
    }

    @Subscribe
    public void statusUpdated(StatusEvent event) {
        statusBar.setText(event.getStatus());

        if(wait != null && wait.getStatus() == Animation.Status.RUNNING) {
            wait.stop();
        }
        wait = new PauseTransition(Duration.seconds(event.getShowDuration()));
        wait.setOnFinished((e) -> {
            if(statusBar.getText().equals(event.getStatus())) {
                statusBar.setText("");
            }
        });
        wait.play();
    }

    @Subscribe
    public void versionUpdated(VersionUpdatedEvent event) {
        Hyperlink versionUpdateLabel = new Hyperlink("Sparrow " + event.getVersion() + " available");
        versionUpdateLabel.getStyleClass().add("version-hyperlink");
        versionUpdateLabel.setOnAction(event1 -> {
            AppServices.get().getApplication().getHostServices().showDocument("https://www.sparrowwallet.com/download");
        });

        if(statusBar.getRightItems().size() > 0 && statusBar.getRightItems().get(0) instanceof Hyperlink) {
            statusBar.getRightItems().remove(0);
        }

        statusBar.getRightItems().add(0, versionUpdateLabel);
    }

    @Subscribe
    public void timedWorker(TimedEvent event) {
        if(event.getTimeMills() == 0) {
            if(statusTimeline != null && statusTimeline.getStatus() == Animation.Status.RUNNING) {
                statusTimeline.stop();
            }
            statusBar.setText("");
            statusBar.setProgress(event.getTimeMills());
        } else if(event.getTimeMills() < 0) {
            statusBar.setText(event.getStatus());
            statusBar.setProgress(event.getTimeMills());
        } else {
            statusBar.setText(event.getStatus());
            statusTimeline = new Timeline(
                    new KeyFrame(Duration.ZERO, new KeyValue(statusBar.progressProperty(), 0)),
                    new KeyFrame(Duration.millis(event.getTimeMills()), e -> {
                        statusBar.setText("");
                        statusBar.setProgress(0);
                    }, new KeyValue(statusBar.progressProperty(), 1))
            );
            statusTimeline.setCycleCount(1);
            statusTimeline.play();
        }
    }

    @Subscribe
    public void usbDevicesFound(UsbDeviceEvent event) {
        UsbStatusButton usbStatus = null;
        for(Node node : statusBar.getRightItems()) {
            if(node instanceof UsbStatusButton) {
                usbStatus = (UsbStatusButton)node;
            }
        }

        if(event.getDevices().isEmpty()) {
            if(usbStatus != null) {
                statusBar.getRightItems().removeAll(usbStatus);
            }
        } else {
            if(usbStatus == null) {
                usbStatus = new UsbStatusButton();
                statusBar.getRightItems().add(Math.max(statusBar.getRightItems().size() - 1, 0), usbStatus);
            } else {
                usbStatus.getItems().remove(0, usbStatus.getItems().size());
            }

            usbStatus.setDevices(event.getDevices());
        }
    }

    @Subscribe
    public void connectionStart(ConnectionStartEvent event) {
        if(!statusBar.getText().contains(TRYING_ANOTHER_SERVER_MESSAGE)) {
            statusUpdated(new StatusEvent(event.getStatus(), 120));
        }
    }

    @Subscribe
    public void connectionFailed(ConnectionFailedEvent event) {
        String status = CONNECTION_FAILED_PREFIX + event.getMessage();
        statusUpdated(new StatusEvent(status));
    }

    @Subscribe
    public void connection(ConnectionEvent event) {
        String status = "Connected to " + Config.get().getServerAddress() + " at height " + event.getBlockHeight();
        statusUpdated(new StatusEvent(status));
        setServerToggleTooltip(event.getBlockHeight());
    }

    @Subscribe
    public void disconnection(DisconnectionEvent event) {
        serverToggle.setDisable(false);
        if(!AppServices.isConnecting() && !AppServices.isConnected() && !statusBar.getText().startsWith(CONNECTION_FAILED_PREFIX) && !statusBar.getText().contains(TRYING_ANOTHER_SERVER_MESSAGE)) {
            statusUpdated(new StatusEvent("Disconnected"));
        }
        if(statusTimeline == null || statusTimeline.getStatus() != Animation.Status.RUNNING) {
            statusBar.setProgress(0);
        }
        for(Wallet wallet : getOpenWallets().keySet()) {
            tabLabelStopAnimation(wallet);
        }
    }

    @Subscribe
    public void walletTabsClosed(WalletTabsClosedEvent event) {
        if(event.getClosedWalletTabData().stream().map(WalletTabData::getWallet).anyMatch(loadingWallets::remove) && loadingWallets.isEmpty()) {
            if(statusBar.getText().equals(LOADING_TRANSACTIONS_MESSAGE)) {
                statusBar.setText("");
            }
            if(statusTimeline == null || statusTimeline.getStatus() != Animation.Status.RUNNING) {
                statusBar.setProgress(0);
            }
        }
    }

     @Subscribe
    public void transactionReferences(TransactionReferencesEvent event) {
        if(AppServices.isConnected() && event instanceof TransactionReferencesStartedEvent) {
            tabLabelStartAnimation(event.getTransaction());
        } else {
            tabLabelStopAnimation(event.getTransaction());
        }
    }

    @Subscribe
    public void walletHistoryStarted(WalletHistoryStartedEvent event) {
        if(AppServices.isConnected() && getOpenWallets().containsKey(event.getWallet())) {
            if(event.getWalletNode() == null && event.getWallet().getTransactions().isEmpty()) {
                statusUpdated(new StatusEvent(LOADING_TRANSACTIONS_MESSAGE, 120));
                if(statusTimeline == null || statusTimeline.getStatus() != Animation.Status.RUNNING) {
                    statusBar.setProgress(-1);
                    loadingWallets.add(event.getWallet());
                }
            }
            tabLabelStartAnimation(event.getWallet());
        }
    }

    @Subscribe
    public void walletHistoryFinished(WalletHistoryFinishedEvent event) {
        if(getOpenWallets().containsKey(event.getWallet())) {
            if(statusBar.getText().equals(LOADING_TRANSACTIONS_MESSAGE)) {
                statusBar.setText("");
            }
            if(statusTimeline == null || statusTimeline.getStatus() != Animation.Status.RUNNING) {
                statusBar.setProgress(0);
            }
            tabLabelStopAnimation(event.getWallet());
            loadingWallets.remove(event.getWallet());
            tabs.getTabs().stream().filter(tab -> tab.getUserData() instanceof WalletTabData && ((WalletTabData)tab.getUserData()).getWallet() == event.getWallet()).forEach(this::tabLabelRemoveFailure);
        }
    }

    @Subscribe
    public void walletHistoryFailed(WalletHistoryFailedEvent event) {
        walletHistoryFinished(new WalletHistoryFinishedEvent(event.getWallet()));
        tabs.getTabs().stream().filter(tab -> tab.getUserData() instanceof WalletTabData && ((WalletTabData) tab.getUserData()).getWallet() == event.getWallet()).forEach(this::tabLabelAddFailure);
        if(getOpenWallets().containsKey(event.getWallet())) {
            if(AppServices.isConnected()) {
                statusUpdated(new StatusEvent("Error retrieving wallet history" + (Config.get().getServerType() == ServerType.PUBLIC_ELECTRUM_SERVER ? ", " + TRYING_ANOTHER_SERVER_MESSAGE : "")));
            }
        }
    }

    @Subscribe
    public void bwtBootStatus(BwtBootStatusEvent event) {
        serverToggle.setDisable(true);
        if(AppServices.isConnecting()) {
            statusUpdated(new StatusEvent(event.getStatus(), 60));
            if(statusTimeline == null || statusTimeline.getStatus() != Animation.Status.RUNNING) {
                statusBar.setProgress(0.01);
            }
        }
    }

    @Subscribe
    public void bwtSyncStatus(BwtSyncStatusEvent event) {
        serverToggle.setDisable(false);
        if((AppServices.isConnecting() || AppServices.isConnected()) && !event.isCompleted()) {
            statusUpdated(new StatusEvent("Syncing... (" + event.getProgress() + "% complete, synced to " + event.getTipAsString() + ")"));
            if(event.getProgress() > 0 && (statusTimeline == null || statusTimeline.getStatus() != Animation.Status.RUNNING)) {
                statusBar.setProgress((double)event.getProgress() / 100);
            }
        }
    }

    @Subscribe
    public void bwtScanStatus(BwtScanStatusEvent event) {
        serverToggle.setDisable(true);
        if((AppServices.isConnecting() || AppServices.isConnected()) && !event.isCompleted()) {
            statusUpdated(new StatusEvent("Scanning... (" + event.getProgress() + "% complete, " + event.getRemainingAsString() + " remaining)"));
            if(event.getProgress() > 0 && (statusTimeline == null || statusTimeline.getStatus() != Animation.Status.RUNNING)) {
                statusBar.setProgress((double)event.getProgress() / 100);
            }
        }
    }

    @Subscribe
    public void bwtReadyStatus(BwtReadyStatusEvent event) {
        serverToggle.setDisable(false);
        if(statusTimeline == null || statusTimeline.getStatus() != Animation.Status.RUNNING) {
            statusBar.setProgress(0);
        }
    }

    @Subscribe
    public void torBootStatus(TorBootStatusEvent event) {
        serverToggle.setDisable(true);
        statusUpdated(new StatusEvent(event.getStatus(), 120));
    }

    @Subscribe
    public void torFailedStatus(TorFailedStatusEvent event) {
        serverToggle.setDisable(false);
        statusUpdated(new StatusEvent(event.getStatus()));
    }

    @Subscribe
    public void torReadyStatus(TorReadyStatusEvent event) {
        serverToggle.setDisable(false);
        statusUpdated(new StatusEvent(event.getStatus()));
    }

    @Subscribe
    public void torExternalStatus(TorExternalStatusEvent event) {
        serverToggle.setDisable(false);
        statusUpdated(new StatusEvent(event.getStatus()));
    }

    @Subscribe
    public void newBlock(NewBlockEvent event) {
        setServerToggleTooltip(event.getHeight());
    }

    @Subscribe
    public void viewWallet(ViewWalletEvent event) {
        if(tabs.getScene().getWindow().equals(event.getWindow())) {
            for(Tab tab : tabs.getTabs()) {
                TabData tabData = (TabData) tab.getUserData();
                if(tabData.getType() == TabData.TabType.WALLET) {
                    WalletTabData walletTabData = (WalletTabData) tabData;
                    if(event.getStorage().getWalletFile().equals(walletTabData.getStorage().getWalletFile())) {
                        tabs.getSelectionModel().select(tab);
                        return;
                    }
                }
            }
        }
    }

    @Subscribe
    public void viewTransaction(ViewTransactionEvent event) {
        if(tabs.getScene().getWindow().equals(event.getWindow())) {
            addTransactionTab(event.getBlockTransaction(), event.getInitialView(), event.getInitialIndex());
        }
    }

    @Subscribe
    public void viewPSBT(ViewPSBTEvent event) {
        if(tabs.getScene().getWindow().equals(event.getWindow())) {
            addTransactionTab(event.getLabel(), event.getFile(), event.getPsbt());
        }
    }

    @Subscribe
    public void bitcoinUnitChanged(BitcoinUnitChangedEvent event) {
        Optional<Toggle> selectedToggle = bitcoinUnit.getToggles().stream().filter(toggle -> event.getBitcoinUnit().equals(toggle.getUserData())).findFirst();
        selectedToggle.ifPresent(toggle -> bitcoinUnit.selectToggle(toggle));
    }

    @Subscribe
    public void openWalletsInNewWindowsStatusChanged(OpenWalletsNewWindowsStatusEvent event) {
        openWalletsInNewWindows.setSelected(event.isOpenWalletsInNewWindows());
    }

    @Subscribe
    public void hideEmptyUsedAddressesStatusChanged(HideEmptyUsedAddressesStatusEvent event) {
        hideEmptyUsedAddresses.setSelected(event.isHideEmptyUsedAddresses());
    }

    @Subscribe
    public void requestOpenWallets(RequestOpenWalletsEvent event) {
        EventManager.get().post(new OpenWalletsEvent(tabs.getScene().getWindow(), getOpenWalletTabData()));
    }

    @Subscribe
    public void requestWalletOpen(RequestWalletOpenEvent event) {
        if(tabs.getScene().getWindow().equals(event.getWindow())) {
            openWallet(true);
        }
    }

    @Subscribe
    public void requestTransactionOpen(RequestTransactionOpenEvent event) {
        if(tabs.getScene().getWindow().equals(event.getWindow())) {
            openTransactionFromFile(null);
        }
    }

    @Subscribe
    public void requestQRScan(RequestQRScanEvent event) {
        if(tabs.getScene().getWindow().equals(event.getWindow())) {
            openTransactionFromQR(null);
        }
    }
}