package com.sparrowwallet.sparrow;

import com.beust.jcommander.JCommander;
import com.google.common.eventbus.Subscribe;
import com.sparrowwallet.drongo.*;
import com.sparrowwallet.drongo.address.Address;
import com.sparrowwallet.drongo.crypto.*;
import com.sparrowwallet.drongo.dns.DnsPayment;
import com.sparrowwallet.drongo.dns.DnsPaymentCache;
import com.sparrowwallet.drongo.policy.PolicyType;
import com.sparrowwallet.drongo.protocol.*;
import com.sparrowwallet.drongo.psbt.*;
import com.sparrowwallet.drongo.silentpayments.SilentPaymentAddress;
import com.sparrowwallet.drongo.wallet.*;
import com.sparrowwallet.hummingbird.UR;
import com.sparrowwallet.hummingbird.registry.CryptoPSBT;
import com.sparrowwallet.sparrow.control.*;
import com.sparrowwallet.sparrow.event.*;
import com.sparrowwallet.sparrow.glyphfont.FontAwesome5;
import com.sparrowwallet.sparrow.io.*;
import com.sparrowwallet.sparrow.io.bbqr.BBQR;
import com.sparrowwallet.sparrow.io.bbqr.BBQRType;
import com.sparrowwallet.sparrow.net.ElectrumServer;
import com.sparrowwallet.sparrow.net.ServerType;
import com.sparrowwallet.sparrow.settings.SettingsGroup;
import com.sparrowwallet.sparrow.settings.SettingsDialog;
import com.sparrowwallet.sparrow.paynym.PayNymDialog;
import com.sparrowwallet.sparrow.transaction.TransactionController;
import com.sparrowwallet.sparrow.transaction.TransactionData;
import com.sparrowwallet.sparrow.transaction.TransactionView;
import com.sparrowwallet.sparrow.wallet.Entry;
import com.sparrowwallet.sparrow.wallet.WalletController;
import com.sparrowwallet.sparrow.wallet.WalletForm;
import de.jangassen.MenuToolkit;
import javafx.animation.*;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.WeakChangeListener;
import javafx.collections.ListChangeListener;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Side;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.scene.input.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.stage.*;
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
import java.nio.file.Files;
import java.text.ParseException;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

import static com.sparrowwallet.sparrow.AppServices.*;
import static com.sparrowwallet.sparrow.control.DownloadVerifierDialog.*;

public class AppController implements Initializable {
    private static final Logger log = LoggerFactory.getLogger(AppController.class);

    public static final String DRAG_OVER_CLASS = "drag-over";
    public static final int TAB_LABEL_MAX_WIDTH = 300;
    public static final double TAB_LABEL_GRAPHIC_OPACITY_INACTIVE = 0.8;
    public static final double TAB_LABEL_GRAPHIC_OPACITY_ACTIVE = 0.95;
    public static final String LOADING_TRANSACTIONS_MESSAGE = "Loading wallet, select Transactions tab to view...";
    public static final String CONNECTION_FAILED_PREFIX = "Connection failed: ";
    public static final String TRYING_ANOTHER_SERVER_MESSAGE = "trying another server...";
    public static final String JPACKAGE_APP_PATH = "jpackage.app-path";

    @FXML
    private MenuItem saveTransaction;

    @FXML
    private MenuItem showTransaction;

    @FXML
    private Menu savePSBT;

    @FXML
    private MenuItem savePSBTBinary;

    @FXML
    private MenuItem showPSBT;

    @FXML
    private MenuItem exportWallet;

    @FXML
    private MenuItem renameWallet;

    @FXML
    private MenuItem deleteWallet;

    @FXML
    private MenuItem closeTab;

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
    private ToggleGroup unitFormat;

    @FXML
    private ToggleGroup theme;

    @FXML
    private CheckMenuItem openWalletsInNewWindows;
    private static final BooleanProperty openWalletsInNewWindowsProperty = new SimpleBooleanProperty();

    @FXML
    private CheckMenuItem hideEmptyUsedAddresses;
    private static final BooleanProperty hideEmptyUsedAddressesProperty = new SimpleBooleanProperty();

    @FXML
    private CheckMenuItem useHdCameraResolution;
    private static final BooleanProperty useHdCameraResolutionProperty = new SimpleBooleanProperty();

    @FXML
    private CheckMenuItem mirrorCameraImage;
    private static final BooleanProperty mirrorCameraImageProperty = new SimpleBooleanProperty();

    @FXML
    private CheckMenuItem showLoadingLog;
    private static final BooleanProperty showLoadingLogProperty = new SimpleBooleanProperty();

    @FXML
    private CheckMenuItem showTxHex;
    private static final BooleanProperty showTxHexProperty = new SimpleBooleanProperty();

    @FXML
    private MenuItem minimizeToTray;

    @FXML
    private MenuItem lockWallet;

    @FXML
    private MenuItem lockAllWallets;

    @FXML
    private MenuItem showWalletSummary;

    @FXML
    private MenuItem searchWallet;

    @FXML
    private MenuItem refreshWallet;

    @FXML
    private MenuItem sendToMany;

    @FXML
    private MenuItem sweepPrivateKey;

    @FXML
    private MenuItem showPayNym;

    @FXML
    private Menu switchServer;

    @FXML
    private CheckMenuItem preventSleep;
    private static final BooleanProperty preventSleepProperty = new SimpleBooleanProperty();

    @FXML
    private Menu restart;

    @FXML
    private StackPane rootStack;

    @FXML
    private TabPane tabs;

    @FXML
    private StatusBar statusBar;

    @FXML
    private UnlabeledToggleSwitch serverToggle;

    private Storage.KeyDerivationService keyDerivationService;

    private PauseTransition wait;

    private Timeline statusTimeline;

    private SearchWalletDialog searchWalletDialog;

    private SendToManyDialog sendToManyDialog;

    private DownloadVerifierDialog downloadVerifierDialog;

    private Tab previouslySelectedTab;

    private boolean subTabsVisible;

    private final Set<Wallet> loadingWallets = new LinkedHashSet<>();

    private final Set<Wallet> emptyLoadingWallets = new LinkedHashSet<>();

    private final Map<File, File> renamedWallets = new HashMap<>();

    private final ChangeListener<Boolean> serverToggleOnlineListener = (observable, oldValue, newValue) -> {
        Platform.runLater(() -> setServerToggleTooltip(getCurrentBlockHeight()));
    };

    private final ListChangeListener<Tab> tabsChangeListener = (c) -> {
        if(c.next() && (c.wasAdded() || c.wasRemoved())) {
            if(c.wasRemoved() && previouslySelectedTab != null) {
                tabs.getSelectionModel().select(previouslySelectedTab);
            }

            boolean walletAdded = c.getAddedSubList().stream().anyMatch(tab -> ((TabData) tab.getUserData()).getType() == TabData.TabType.WALLET);
            boolean walletRemoved = c.getRemoved().stream().anyMatch(tab -> ((TabData) tab.getUserData()).getType() == TabData.TabType.WALLET);
            if(walletAdded || walletRemoved) {
                EventManager.get().post(new OpenWalletsEvent(tabs.getScene().getWindow(), getOpenWalletTabData()));
            }

            List<WalletTabData> closedWalletTabs = c.getRemoved().stream().filter(tab -> tab.getUserData() instanceof WalletTabData)
                    .flatMap(tab -> ((TabPane) tab.getContent()).getTabs().stream().map(subTab -> (WalletTabData) subTab.getUserData())).collect(Collectors.toList());
            if(!closedWalletTabs.isEmpty()) {
                EventManager.get().post(new WalletTabsClosedEvent(closedWalletTabs));
            }

            List<TransactionTabData> closedTransactionTabs = c.getRemoved().stream().map(tab -> (TabData) tab.getUserData())
                    .filter(tabData -> tabData.getType() == TabData.TabType.TRANSACTION).map(tabData -> (TransactionTabData) tabData).collect(Collectors.toList());
            if(!closedTransactionTabs.isEmpty()) {
                EventManager.get().post(new TransactionTabsClosedEvent(closedTransactionTabs));
            }

            closeTab.setDisable(tabs.getTabs().isEmpty());
            if(tabs.getTabs().isEmpty()) {
                Stage tabStage = (Stage) tabs.getScene().getWindow();
                tabStage.setTitle("Sparrow");
                saveTransaction.setVisible(true);
                saveTransaction.setDisable(true);
                exportWallet.setDisable(true);
            }
        }
    };

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        EventManager.get().register(this);
    }

    void initializeView() {
        Platform.runLater(this::setPlatformApplicationMenu);

        rootStack.getScene().getWindow().setOnHiding(windowEvent -> {
            if(searchWalletDialog != null && searchWalletDialog.isShowing()) {
                searchWalletDialog.close();
            }
            if(sendToManyDialog != null && sendToManyDialog.isShowing()) {
                sendToManyDialog.close();
            }
        });

        rootStack.setOnDragOver(event -> {
            if(event.getGestureSource() != rootStack && event.getDragboard().hasFiles()) {
                event.acceptTransferModes(TransferMode.LINK);
            }
            event.consume();
        });

        rootStack.setOnDragDropped(event -> {
            Dragboard db = event.getDragboard();
            boolean success = false;
            if(db.hasFiles()) {
                openFiles(db.getFiles());
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

        tabs.getSelectionModel().selectedItemProperty().addListener((observable, previouslySelectedTab, selectedTab) -> {
            if(tabs.getTabs().contains(previouslySelectedTab)) {
                this.previouslySelectedTab = previouslySelectedTab;
            }
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
        tabs.getTabs().addListener(tabsChangeListener);

        tabs.getScene().getWindow().setOnCloseRequest(event -> {
            EventManager.get().unregister(this);
            EventManager.get().post(new OpenWalletsEvent(tabs.getScene().getWindow(), Collections.emptyList()));
        });

        tabs.setPickOnBounds(false);

        registerShortcuts();

        BitcoinUnit unit = Config.get().getBitcoinUnit();
        if(unit == null) {
            unit = BitcoinUnit.AUTO;
            Config.get().setBitcoinUnit(unit);
        }
        final BitcoinUnit selectedUnit = unit;
        Optional<Toggle> selectedUnitToggle = bitcoinUnit.getToggles().stream().filter(toggle -> selectedUnit.equals(toggle.getUserData())).findFirst();
        selectedUnitToggle.ifPresent(toggle -> bitcoinUnit.selectToggle(toggle));
        Optional<Toggle> otherUnitToggle = bitcoinUnit.getToggles().stream().filter(toggle ->
                (List.of(BitcoinUnit.AUTO, BitcoinUnit.SATOSHIS).contains(selectedUnit) && BitcoinUnit.BTC.equals(toggle.getUserData()) || (selectedUnit == BitcoinUnit.BTC && BitcoinUnit.SATOSHIS.equals(toggle.getUserData())))).findFirst();
        otherUnitToggle.ifPresent(toggle -> ((RadioMenuItem)toggle).setAccelerator(new KeyCodeCombination(KeyCode.B, KeyCombination.SHORTCUT_DOWN)));

        UnitFormat format = Config.get().getUnitFormat();
        if(format == null) {
            format = UnitFormat.DOT;
            Config.get().setUnitFormat(format);
        }
        final UnitFormat selectedFormat = format;
        Optional<Toggle> selectedFormatToggle = unitFormat.getToggles().stream().filter(toggle -> selectedFormat.equals(toggle.getUserData())).findFirst();
        selectedFormatToggle.ifPresent(toggle -> unitFormat.selectToggle(toggle));

        Theme configTheme = Config.get().getTheme();
        if(configTheme == null) {
            configTheme = Theme.LIGHT;
            Config.get().setTheme(Theme.LIGHT);
        }
        final Theme selectedTheme = configTheme;
        Optional<Toggle> selectedThemeToggle = theme.getToggles().stream().filter(toggle -> selectedTheme.equals(toggle.getUserData())).findFirst();
        selectedThemeToggle.ifPresent(toggle -> theme.selectToggle(toggle));
        setTheme(null);

        openWalletsInNewWindowsProperty.set(Config.get().isOpenWalletsInNewWindows());
        openWalletsInNewWindows.selectedProperty().bindBidirectional(openWalletsInNewWindowsProperty);
        hideEmptyUsedAddressesProperty.set(Config.get().isHideEmptyUsedAddresses());
        hideEmptyUsedAddresses.selectedProperty().bindBidirectional(hideEmptyUsedAddressesProperty);
        useHdCameraResolutionProperty.set(Config.get().getWebcamResolution() == null || Config.get().getWebcamResolution().isWidescreenAspect());
        useHdCameraResolution.selectedProperty().bindBidirectional(useHdCameraResolutionProperty);
        mirrorCameraImageProperty.set(Config.get().isMirrorCapture());
        mirrorCameraImage.selectedProperty().bindBidirectional(mirrorCameraImageProperty);
        showTxHexProperty.set(Config.get().isShowTransactionHex());
        showTxHex.selectedProperty().bindBidirectional(showTxHexProperty);
        showLoadingLogProperty.set(Config.get().isShowLoadingLog());
        showLoadingLog.selectedProperty().bindBidirectional(showLoadingLogProperty);
        preventSleepProperty.set(Config.get().isPreventSleep());
        preventSleep.selectedProperty().bindBidirectional(preventSleepProperty);

        MenuItem homeItem = new MenuItem("Home Folder...");
        homeItem.setOnAction(this::restartInHome);
        restart.getItems().add(homeItem);
        List<Network> networks = new ArrayList<>(List.of(Network.MAINNET, Network.TESTNET, Network.TESTNET4, Network.SIGNET));
        networks.remove(Network.get());
        for(Network network : networks) {
            MenuItem networkItem = new MenuItem(network.toDisplayString());
            networkItem.setOnAction(event -> restart(event, network));
            restart.getItems().add(networkItem);
        }
        restart.setVisible(System.getProperty(JPACKAGE_APP_PATH) != null);

        saveTransaction.setDisable(true);
        showTransaction.visibleProperty().bind(Bindings.and(saveTransaction.visibleProperty(), saveTransaction.disableProperty().not()));
        showTransaction.disableProperty().bind(saveTransaction.disableProperty());
        savePSBT.visibleProperty().bind(saveTransaction.visibleProperty().not());
        savePSBTBinary.disableProperty().bind(saveTransaction.visibleProperty());
        showPSBT.visibleProperty().bind(saveTransaction.visibleProperty().not());
        exportWallet.setDisable(true);
        renameWallet.disableProperty().bind(exportWallet.disableProperty());
        deleteWallet.disableProperty().bind(exportWallet.disableProperty());
        closeTab.setDisable(true);
        lockWallet.setDisable(true);
        showWalletSummary.disableProperty().bind(exportWallet.disableProperty());
        searchWallet.disableProperty().bind(exportWallet.disableProperty());
        refreshWallet.disableProperty().bind(Bindings.or(exportWallet.disableProperty(), Bindings.or(serverToggle.disableProperty(), AppServices.onlineProperty().not())));
        sendToMany.disableProperty().bind(exportWallet.disableProperty());
        sweepPrivateKey.disableProperty().bind(Bindings.or(serverToggle.disableProperty(), AppServices.onlineProperty().not()));
        showPayNym.setDisable(true);

        configureSwitchServer();
        setServerType(Config.get().getServerType());
        serverToggle.setSelected(isConnected());
        serverToggle.setDisable(!Config.get().hasServer());
        onlineProperty().bindBidirectional(serverToggle.selectedProperty());
        onlineProperty().addListener(new WeakChangeListener<>(serverToggleOnlineListener));
        serverToggle.addEventHandler(MouseEvent.MOUSE_CLICKED, event -> {
            Config.get().setMode(serverToggle.isSelected() ? Mode.ONLINE : Mode.OFFLINE);
        });

        openTransactionIdItem.disableProperty().bind(onlineProperty().not());
        setNetworkLabel();
    }

    private void registerShortcuts() {
        OsType osType = OsType.getCurrent();
        if(osType == OsType.MACOS) {
            tabs.getScene().addEventFilter(KeyEvent.KEY_PRESSED, event -> {
                if(event.isShortcutDown() && event.isAltDown() && (event.getCode() == KeyCode.LEFT || event.getCode() == KeyCode.RIGHT)) {
                    int currentIndex = tabs.getSelectionModel().getSelectedIndex();
                    if(event.getCode() == KeyCode.LEFT && currentIndex > 0) {
                        tabs.getSelectionModel().select(currentIndex - 1);
                    } else if(event.getCode() == KeyCode.RIGHT && currentIndex < tabs.getTabs().size() - 1) {
                        tabs.getSelectionModel().select(currentIndex + 1);
                    }
                }
            });
        }
    }

    private void setPlatformApplicationMenu() {
        OsType osType = OsType.getCurrent();
        if(osType == OsType.MACOS) {
            MenuToolkit tk = MenuToolkit.toolkit();
            MenuItem settings = new MenuItem("Settings...");
            settings.setOnAction(this::openSettings);
            settings.setAccelerator(new KeyCodeCombination(KeyCode.COMMA, KeyCombination.META_DOWN));
            Menu defaultApplicationMenu = new Menu("Apple", null, tk.createAboutMenuItem(SparrowWallet.APP_NAME, getAboutStage()), new SeparatorMenuItem(),
                    settings, new SeparatorMenuItem(),
                    tk.createHideMenuItem(SparrowWallet.APP_NAME), tk.createHideOthersMenuItem(), tk.createUnhideAllMenuItem(), new SeparatorMenuItem(),
                    tk.createQuitMenuItem(SparrowWallet.APP_NAME));
            tk.setApplicationMenu(defaultApplicationMenu);

            fileMenu.getItems().removeIf(item -> item.getStyleClass().contains("osxHide"));
            toolsMenu.getItems().removeIf(item -> item.getStyleClass().contains("osxHide"));
            helpMenu.getItems().removeIf(item -> item.getStyleClass().contains("osxHide"));
        } else if(osType == OsType.WINDOWS) {
            toolsMenu.getItems().removeIf(item -> item.getStyleClass().contains("windowsHide"));
        }

        if(osType == OsType.UNIX || !TrayManager.isSupported()) {
            viewMenu.getItems().remove(minimizeToTray);
        }
    }

    private void setNetworkLabel() {
        if(Network.get() != Network.MAINNET) {
            Platform.runLater(() -> {
                StackPane tabBackground = (StackPane)tabs.lookup(".tab-header-background");
                if(tabBackground != null) {
                    HBox hBox = new HBox();
                    Label label = new Label(Network.get().toDisplayString());
                    label.setPadding(new Insets(0, 10, 0, 0));
                    hBox.getChildren().add(label);
                    hBox.setAlignment(Pos.CENTER_RIGHT);
                    tabBackground.getChildren().add(hBox);
                }
            });
        }
    }

    public void showIntroduction(ActionEvent event) {
        WelcomeDialog welcomeDialog = new WelcomeDialog();
        welcomeDialog.initOwner(rootStack.getScene().getWindow());
        Optional<Mode> optionalMode = welcomeDialog.showAndWait();
        if(optionalMode.isPresent() && optionalMode.get().equals(Mode.ONLINE)) {
            openSettings(SettingsGroup.SERVER);
        }
    }

    public void showDocumentation(ActionEvent event) {
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

    public void openSupport(ActionEvent event) {
        AppServices.get().getApplication().getHostServices().showDocument("https://sparrowwallet.com/opensupport");
    }

    public void submitBugReport(ActionEvent event) {
        ButtonType supportType = new ButtonType("Get Support", ButtonBar.ButtonData.LEFT);
        ButtonType bugType = new ButtonType("Submit Bug Report", ButtonBar.ButtonData.YES);
        Optional<ButtonType> optResponse = showWarningDialog("Submit Bug Report", "Please note that this facility is for bug reports and feature requests only. There is a community of Sparrow users who can assist with support requests.", supportType, bugType);

        if(optResponse.isPresent()) {
            if(optResponse.get() == bugType) {
                AppServices.get().getApplication().getHostServices().showDocument("https://sparrowwallet.com/submitbugreport");
            } else {
                openSupport(event);
            }
        }
    }

    public void showAbout(ActionEvent event) {
        Stage aboutStage = getAboutStage();
        aboutStage.show();
    }

    private Stage getAboutStage() {
        try {
            FXMLLoader loader = new FXMLLoader(AppController.class.getResource("about.fxml"));
            StackPane root = loader.load();
            AboutController controller = loader.getController();

            if(OsType.getCurrent() == OsType.WINDOWS) {
                root.setBorder(new Border(new BorderStroke(Color.DARKGRAY, BorderStrokeStyle.SOLID, CornerRadii.EMPTY, BorderWidths.DEFAULT)));
            }

            Stage stage = new Stage(StageStyle.UNDECORATED);
            stage.setTitle("About " + SparrowWallet.APP_NAME);
            stage.initOwner(tabs.getScene().getWindow());
            stage.initModality(Modality.WINDOW_MODAL);
            stage.setResizable(false);
            Scene scene = new Scene(root);
            AppServices.onEscapePressed(scene, stage::close);
            stage.setScene(scene);
            controller.setStage(stage);
            controller.initializeView();
            setStageIcon(stage);
            stage.setOnShowing(event -> {
                AppServices.moveToActiveWindowScreen(stage, 600, 460);
            });

            return stage;
        } catch(IOException e) {
            log.error("Error loading about stage", e);
        }

        return null;
    }

    public void installUdevRules(ActionEvent event) {
        String commands = """
                sudo install -m 644 /opt/sparrowwallet/lib/runtime/conf/udev/*.rules /etc/udev/rules.d
                sudo udevadm control --reload
                sudo udevadm trigger
                sudo groupadd -f plugdev
                sudo usermod -aG plugdev `whoami`
                """;
        String home = System.getProperty(JPACKAGE_APP_PATH);
        if(home != null && !home.startsWith("/opt/sparrowwallet") && home.endsWith("bin/Sparrow")) {
            home = home.replace("bin/Sparrow", "");
            commands = commands.replace("/opt/sparrowwallet/", home);
        }

        TextAreaDialog dialog = new TextAreaDialog(commands, false);
        dialog.initOwner(rootStack.getScene().getWindow());
        dialog.setTitle("Install udev Rules");
        dialog.getDialogPane().setHeaderText("""
                Installing udev rules ensures devices can connect over USB.
                These commands require root privileges.
                Open a shell and enter the following.
                """);
        dialog.showAndWait();
    }

    public void openTransactionFromFile(ActionEvent event) {
        Stage window = new Stage();

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Open Transaction");
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("All Files", OsType.getCurrent().equals(OsType.UNIX) ? "*" : "*.*"),
                new FileChooser.ExtensionFilter("PSBT", "*.psbt"),
                new FileChooser.ExtensionFilter("TXN", "*.txn")
        );

        AppServices.moveToActiveWindowScreen(window, 800, 450);
        List<File> files = fileChooser.showOpenMultipleDialog(window);
        if(files != null) {
            for(File file : files) {
                openTransactionFile(file);
            }
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
                byte[] bytes = Files.readAllBytes(file.toPath());
                String name = file.getName();

                if(Utils.isHex(bytes) || Utils.isBase64(bytes)) {
                    addTransactionTab(name, file, new String(bytes, StandardCharsets.UTF_8).trim());
                } else {
                    addTransactionTab(name, file, bytes);
                }
            } catch(IOException e) {
                showErrorDialog("Error opening file", e.getMessage());
            } catch(PSBTParseException e) {
                showErrorDialog("Invalid PSBT", e.getMessage());
            } catch(TransactionParseException e) {
                showErrorDialog("Invalid transaction", e.getMessage());
            } catch(Exception e) {
                showErrorDialog("Invalid file", "Cannot recognise the format of the " + file.getName() + " file.");
            }
        }
    }

    public void openTransactionFromText(ActionEvent event) {
        TextAreaDialog dialog = new TextAreaDialog();
        dialog.initOwner(rootStack.getScene().getWindow());
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
        dialog.initOwner(rootStack.getScene().getWindow());
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
        qrScanDialog.initOwner(rootStack.getScene().getWindow());
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

    public void showTransaction(ActionEvent event) {
        Tab selectedTab = tabs.getSelectionModel().getSelectedItem();
        TabData tabData = (TabData)selectedTab.getUserData();
        if(tabData.getType() == TabData.TabType.TRANSACTION) {
            TransactionTabData transactionTabData = (TransactionTabData) tabData;
            Transaction transaction = transactionTabData.getTransaction();

            try {
                byte[] txBytes = transaction.bitcoinSerialize();
                UR ur = UR.fromBytes(txBytes);
                BBQR bbqr = new BBQR(BBQRType.TXN, txBytes);
                QRDisplayDialog qrDisplayDialog = new QRDisplayDialog(ur, bbqr, false, false, false);
                qrDisplayDialog.initOwner(rootStack.getScene().getWindow());
                qrDisplayDialog.showAndWait();
            } catch(Exception e) {
                log.error("Error creating UR", e);
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
                fileName = fileName.replace('/', '_');
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
                if(!asText && !file.getName().toLowerCase(Locale.ROOT).endsWith(".psbt")) {
                    file = new File(file.getAbsolutePath() + ".psbt");
                }

                try(FileOutputStream outputStream = new FileOutputStream(file)) {
                    if(asText) {
                        PrintWriter writer = new PrintWriter(new OutputStreamWriter(outputStream, StandardCharsets.UTF_8));
                        writer.print(transactionTabData.getPsbt().getForExport().toBase64String(includeXpubs));
                        writer.flush();
                    } else {
                        outputStream.write(transactionTabData.getPsbt().getForExport().serialize(includeXpubs, true));
                    }
                } catch(IOException e) {
                    log.error("Error saving PSBT", e);
                    AppServices.showErrorDialog("Error saving PSBT", "Cannot write to " + file.getAbsolutePath());
                }
            }
        }
    }

    public void copyPSBTHex(ActionEvent event) {
        copyPSBT(false);
    }

    public void copyPSBTBase64(ActionEvent event) {
        copyPSBT(true);
    }

    public void copyPSBT(boolean asBase64) {
        Tab selectedTab = tabs.getSelectionModel().getSelectedItem();
        TabData tabData = (TabData)selectedTab.getUserData();
        if(tabData.getType() == TabData.TabType.TRANSACTION) {
            TransactionTabData transactionTabData = (TransactionTabData)tabData;
            String data = asBase64 ? transactionTabData.getPsbt().getForExport().toBase64String() : transactionTabData.getPsbt().getForExport().toString();

            ClipboardContent content = new ClipboardContent();
            content.putString(data);
            Clipboard.getSystemClipboard().setContent(content);
        }
    }

    public void showPSBT(ActionEvent event) {
        Tab selectedTab = tabs.getSelectionModel().getSelectedItem();
        TabData tabData = (TabData)selectedTab.getUserData();
        if(tabData.getType() == TabData.TabType.TRANSACTION) {
            TransactionTabData transactionTabData = (TransactionTabData)tabData;

            byte[] psbtBytes = transactionTabData.getPsbt().getForExport().serialize();
            CryptoPSBT cryptoPSBT = new CryptoPSBT(psbtBytes);
            BBQR bbqr = new BBQR(BBQRType.PSBT, psbtBytes);
            QRDisplayDialog qrDisplayDialog = new QRDisplayDialog(cryptoPSBT.toUR(), bbqr, false, true, false);
            qrDisplayDialog.initOwner(rootStack.getScene().getWindow());
            qrDisplayDialog.show();
        }
    }

    public List<WalletTabData> getOpenWalletTabData() {
        List<WalletTabData> openWalletTabData = new ArrayList<>();

        for(Tab tab : tabs.getTabs()) {
            if(tab.getUserData() instanceof WalletTabData) {
                TabPane subTabs = (TabPane)tab.getContent();
                for(Tab subTab : subTabs.getTabs()) {
                    openWalletTabData.add((WalletTabData)subTab.getUserData());
                }
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
            if(tab.getUserData() instanceof WalletTabData) {
                TabPane subTabs = (TabPane)tab.getContent();
                for(Tab subTab : subTabs.getTabs()) {
                    WalletTabData walletTabData = (WalletTabData)subTab.getUserData();
                    if(walletTabData.getWallet() == wallet) {
                        tabs.getSelectionModel().select(tab);
                        subTabs.getSelectionModel().select(subTab);
                    }
                }
            }
        }
    }

    public void renameWallet(ActionEvent event) {
        renameWallet(getSelectedWalletForm());
    }

    public void deleteWallet(ActionEvent event) {
        deleteWallet(getSelectedWalletForm());
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
        if(Config.get().getWebcamResolution().isStandardAspect() && item.isSelected()) {
            Config.get().setWebcamResolution(WebcamResolution.HD);
        } else if(Config.get().getWebcamResolution().isWidescreenAspect() && !item.isSelected()) {
            Config.get().setWebcamResolution(WebcamResolution.VGA);
        }
    }

    public void mirrorCameraImage(ActionEvent event) {
        CheckMenuItem item = (CheckMenuItem)event.getSource();
        Config.get().setMirrorCapture(item.isSelected());
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

    public void setUnitFormat(ActionEvent event) {
        MenuItem item = (MenuItem)event.getSource();
        UnitFormat format = (UnitFormat)item.getUserData();
        Config.get().setUnitFormat(format);
        EventManager.get().post(new UnitFormatChangedEvent(format));
    }

    public void preventSleep(ActionEvent event) {
        CheckMenuItem item = (CheckMenuItem)event.getSource();
        Config.get().setPreventSleep(item.isSelected());
        AppServices.get().setPreventSleep(item.isSelected());
    }

    public void restartInHome(ActionEvent event) {
        Args args = getRestartArgs();
        File initialDir = null;
        if(args.dir != null) {
            initialDir = new File(args.dir);
        }

        Stage window = new Stage();
        DirectoryChooser directoryChooser = new DirectoryChooser();
        directoryChooser.setTitle("Choose Sparrow Home Folder");
        directoryChooser.setInitialDirectory(initialDir == null || !initialDir.exists() ? Storage.getSparrowHome() : initialDir);
        File newHome = directoryChooser.showDialog(window);

        if(newHome != null) {
            args.dir = newHome.getAbsolutePath();
            restart(event, args);
        }
    }

    public void restart(ActionEvent event, Network network) {
        if(System.getProperty(JPACKAGE_APP_PATH) == null) {
            throw new IllegalStateException("Property " + JPACKAGE_APP_PATH + " is not present");
        }

        Args args = getRestartArgs();
        args.network = network;
        restart(event, args);
    }

    private static Args getRestartArgs() {
        Args args = new Args();
        ProcessHandle.current().info().arguments().ifPresent(argv -> {
            JCommander jCommander = JCommander.newBuilder().addObject(args).acceptUnknownOptions(true).build();
            jCommander.parse(argv);
        });

        return args;
    }

    private void restart(ActionEvent event, Args args) {
        try {
            List<String> cmd = new ArrayList<>();
            cmd.add(System.getProperty(JPACKAGE_APP_PATH));
            cmd.addAll(args.toParams());
            final ProcessBuilder builder = new ProcessBuilder(cmd);
            if(OsType.getCurrent() == OsType.UNIX) {
                Map<String, String> env = builder.environment();
                env.remove("LD_LIBRARY_PATH");
            }
            builder.start();
            quit(event);
        } catch(Exception e) {
            log.error("Error restarting application", e);
        }
    }

    public void openFiles(List<File> files) {
        boolean verifyOpened = false;
        for(File file : files) {
            if(isWalletFile(file)) {
                openWalletFile(file, true);
            } else if(isVerifyDownloadFile(file)) {
                if(!verifyOpened) {
                    verifyDownload(new ActionEvent(file, rootStack));
                    verifyOpened = true;
                }
            } else {
                openTransactionFile(file);
            }
        }
    }

    private void setServerToggleTooltip(Integer currentBlockHeight) {
        Tooltip tooltip = new Tooltip(getServerToggleTooltipText(currentBlockHeight));
        tooltip.setShowDuration(Duration.seconds(15));
        serverToggle.setTooltip(tooltip);
    }

    private String getServerToggleTooltipText(Integer currentBlockHeight) {
        if(AppServices.isConnected()) {
            return "Connected to " + Config.get().getServerDisplayName() + (currentBlockHeight != null ? " at height " + currentBlockHeight : "") +
                    (Config.get().getServerType() == ServerType.PUBLIC_ELECTRUM_SERVER ? "\nWarning! You are connected to a public server and sharing your transaction data with it.\nFor better privacy, consider using your own Bitcoin Core node or private Electrum server." : "");
        } else if(AppServices.isConnecting()) {
            return "Connecting...";
        }

        return "Disconnected";
    }

    public void newWallet(ActionEvent event) {
        WalletNameDialog dlg = new WalletNameDialog();
        dlg.initOwner(rootStack.getScene().getWindow());
        Optional<WalletNameDialog.NameAndBirthDate> optNameAndBirthDate = dlg.showAndWait();
        if(optNameAndBirthDate.isPresent()) {
            WalletNameDialog.NameAndBirthDate nameAndBirthDate = optNameAndBirthDate.get();
            File walletFile = Storage.getWalletFile(nameAndBirthDate.getName());
            Storage storage = new Storage(walletFile);
            Wallet wallet = new Wallet(nameAndBirthDate.getName(), PolicyType.SINGLE, ScriptType.P2WPKH, nameAndBirthDate.getBirthDate());
            addWalletTabOrWindow(storage, wallet, false);
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
        List<File> files = fileChooser.showOpenMultipleDialog(window);
        if(files != null) {
            for(File file : files) {
                openWalletFile(file, forceSameWindow);
            }
        }
    }

    public void openWalletFile(File file, boolean forceSameWindow) {
        try {
            Storage storage = new Storage(file);
            if(!storage.isEncrypted()) {
                Storage.LoadWalletService loadWalletService = new Storage.LoadWalletService(storage);
                loadWalletService.setExecutor(Storage.LoadWalletService.getSingleThreadedExecutor());
                loadWalletService.setOnSucceeded(workerStateEvent -> {
                    WalletAndKey walletAndKey = loadWalletService.getValue();
                    openWallet(storage, walletAndKey, this, forceSameWindow);
                });
                loadWalletService.setOnFailed(workerStateEvent -> {
                    Throwable exception = workerStateEvent.getSource().getException();
                    if(exception instanceof StorageException) {
                        showErrorDialog("Error Opening Wallet", exception.getMessage());
                    } else if(!attemptImportWallet(file, null)) {
                        log.error("Error opening wallet", exception);
                        showErrorDialog("Error Opening Wallet", exception.getMessage() == null || exception.getMessage().contains("Expected BEGIN_OBJECT") ? "Unsupported wallet file format." : exception.getMessage());
                    }
                });
                loadWalletService.start();
            } else {
                WalletPasswordDialog dlg = new WalletPasswordDialog(storage.getWalletName(null), WalletPasswordDialog.PasswordRequirement.LOAD);
                dlg.initOwner(rootStack.getScene().getWindow());
                Optional<SecureString> optionalPassword = dlg.showAndWait();
                if(optionalPassword.isEmpty()) {
                    return;
                }

                SecureString password = optionalPassword.get();
                Storage.LoadWalletService loadWalletService = new Storage.LoadWalletService(storage, password);
                loadWalletService.setOnSucceeded(workerStateEvent -> {
                    EventManager.get().post(new StorageEvent(storage.getWalletId(null), TimedEvent.Action.END, "Done"));
                    WalletAndKey walletAndKey = loadWalletService.getValue();
                    openWallet(storage, walletAndKey, this, forceSameWindow);
                });
                loadWalletService.setOnFailed(workerStateEvent -> {
                    EventManager.get().post(new StorageEvent(storage.getWalletId(null), TimedEvent.Action.END, "Failed"));
                    Throwable exception = loadWalletService.getException();
                    if(exception instanceof InvalidPasswordException) {
                        Optional<ButtonType> optResponse = showErrorDialog("Invalid Password", "The wallet password was invalid. Try again?", ButtonType.CANCEL, ButtonType.OK);
                        if(optResponse.isPresent() && optResponse.get().equals(ButtonType.OK)) {
                            Platform.runLater(() -> openWalletFile(file, forceSameWindow));
                        }
                    } else {
                        if(exception instanceof StorageException) {
                            showErrorDialog("Error Opening Wallet", exception.getMessage());
                        } else if(!attemptImportWallet(file, password)) {
                            log.error("Error Opening Wallet", exception);
                            showErrorDialog("Error Opening Wallet", exception.getMessage() == null || exception.getMessage().contains("Expected BEGIN_OBJECT") ? "Unsupported wallet file format." : exception.getMessage());
                        }
                        password.clear();
                    }
                });
                EventManager.get().post(new StorageEvent(storage.getWalletId(null), TimedEvent.Action.START, "Decrypting wallet..."));
                loadWalletService.start();
            }
        } catch(Exception e) {
            if(e instanceof IOException && e.getMessage().startsWith("The process cannot access the file because another process has locked")) {
                log.error("Error opening wallet", e);
                showErrorDialog("Error Opening Wallet", "The wallet file is locked. Is another instance of " + SparrowWallet.APP_NAME + " already running?");
            } else if(!attemptImportWallet(file, null)) {
                log.error("Error opening wallet", e);
                showErrorDialog("Error Opening Wallet", e.getMessage() == null ? "Unsupported file format" : e.getMessage());
            }
        }
    }

    private void openWallet(Storage storage, WalletAndKey walletAndKey, AppController appController, boolean forceSameWindow) {
        try {
            storage.restorePublicKeysFromSeed(walletAndKey.getWallet(), walletAndKey.getKey());
            if(!walletAndKey.getWallet().isValid()) {
                throw new IllegalStateException("Wallet file is not valid.");
            }
            AppController walletAppController = appController.addWalletTabOrWindow(storage, walletAndKey.getWallet(), forceSameWindow);
            for(Map.Entry<WalletAndKey, Storage> entry : walletAndKey.getChildWallets().entrySet()) {
                openWallet(entry.getValue(), entry.getKey(), walletAppController, true);
            }
            if(walletAndKey.getWallet().isMasterWallet()) {
                Platform.runLater(() -> selectTab(walletAndKey.getWallet()));
            }
        } catch(Exception e) {
            log.error("Error opening wallet", e);
            showErrorDialog("Error Opening Wallet", e.getMessage());
        } finally {
            walletAndKey.clear();
        }
    }

    public void importWallet(ActionEvent event) {
        List<WalletForm> selectedWalletForms = getSelectedWalletForms();
        WalletImportDialog dlg = new WalletImportDialog(selectedWalletForms);
        dlg.initOwner(rootStack.getScene().getWindow());
        Optional<Wallet> optionalWallet = dlg.showAndWait();
        if(optionalWallet.isPresent()) {
            Wallet wallet = optionalWallet.get();

            List<WalletTabData> walletTabData = getOpenWalletTabData();
            List<ExtendedKey> xpubs = wallet.getKeystores().stream().map(Keystore::getExtendedPublicKey).collect(Collectors.toList());
            Optional<WalletForm> optNewWalletForm = walletTabData.stream()
                    .map(WalletTabData::getWalletForm)
                    .filter(wf -> wf.getSettingsWalletForm() != null && wf.getSettingsWalletForm().getWallet().getPolicyType() == PolicyType.MULTI &&
                            wf.getSettingsWalletForm().getWallet().getScriptType() == wallet.getScriptType() && !wf.getSettingsWalletForm().getWallet().isValid() &&
                            wf.getSettingsWalletForm().getWallet().getKeystores().stream().map(Keystore::getExtendedPublicKey).anyMatch(xpubs::contains)).findFirst();
            if(optNewWalletForm.isPresent()) {
                EventManager.get().post(new ExistingWalletImportedEvent(optNewWalletForm.get().getWalletId(), wallet));
                selectTab(optNewWalletForm.get().getWallet());
            } else if(selectedWalletForms.isEmpty() || wallet != selectedWalletForms.get(0).getWallet()) {
                addImportedWallet(wallet);
            }
        }
    }

    private boolean attemptImportWallet(File file, SecureString password) {
        List<WalletImport> walletImporters = List.of(new ColdcardSinglesig(), new ColdcardMultisig(),
                new Electrum(),
                new SpecterDesktop(),
                new Descriptor(),
                new CoboVaultSinglesig(), new CoboVaultMultisig(),
                new PassportSinglesig(),
                new KeystoneSinglesig(), new KeystoneMultisig(),
                new CaravanMultisig(),
                new KeycardShellSinglesig(), new KeycardShellMultisig());
        for(WalletImport importer : walletImporters) {
            if(importer.isDeprecated() && !Config.get().isShowDeprecatedImportExport()) {
                continue;
            }

            try(FileInputStream inputStream = new FileInputStream(file)) {
                if(importer.isEncrypted(file) && password == null) {
                    WalletPasswordDialog dlg = new WalletPasswordDialog(file.getName(), WalletPasswordDialog.PasswordRequirement.LOAD);
                    dlg.initOwner(rootStack.getScene().getWindow());
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
        if(AppServices.disallowAnyInvalidDerivationPaths(wallet)) {
            return;
        }

        WalletNameDialog nameDlg = new WalletNameDialog(wallet.getName(), true, wallet.getBirthDate());
        nameDlg.initOwner(rootStack.getScene().getWindow());
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
                if(tab.getUserData() instanceof WalletTabData) {
                    TabPane subTabs = (TabPane)tab.getContent();
                    for(Tab subTab : subTabs.getTabs()) {
                        WalletTabData walletTabData = (WalletTabData)subTab.getUserData();
                        if(walletTabData.getStorage().getWalletFile().equals(walletFile)) {
                            iter.remove();
                        }
                    }
                }
            }

            walletFile.delete();
        }

        if(wallet.isEncrypted()) {
            throw new IllegalArgumentException("Imported wallet must be unencrypted");
        }

        Storage storage = new Storage(Storage.getWalletFile(wallet.getName()));
        WalletPasswordDialog dlg = new WalletPasswordDialog(wallet.getName(), WalletPasswordDialog.PasswordRequirement.UPDATE_NEW);
        dlg.initOwner(rootStack.getScene().getWindow());
        Optional<SecureString> password = dlg.showAndWait();
        if(password.isPresent()) {
            if(password.get().length() == 0) {
                try {
                    storage.setEncryptionPubKey(Storage.NO_PASSWORD_KEY);
                    storage.saveWallet(wallet);
                    storage.restorePublicKeysFromSeed(wallet, null);
                    addWalletTabOrWindow(storage, wallet, false);

                    for(Wallet childWallet : wallet.getChildWallets()) {
                        storage.saveWallet(childWallet);
                        storage.restorePublicKeysFromSeed(childWallet, null);
                        addWalletTabOrWindow(storage, childWallet, false);
                    }
                    Platform.runLater(() -> selectTab(wallet));
                } catch(IOException | StorageException | MnemonicException e) {
                    log.error("Error saving imported wallet", e);
                }
            } else {
                keyDerivationService = new Storage.KeyDerivationService(storage, password.get());
                keyDerivationService.setOnSucceeded(workerStateEvent -> {
                    EventManager.get().post(new StorageEvent(Storage.getWalletFile(wallet.getName()).getAbsolutePath(), TimedEvent.Action.END, "Done"));
                    ECKey encryptionFullKey = keyDerivationService.getValue();
                    Key key = null;

                    try {
                        ECKey encryptionPubKey = ECKey.fromPublicOnly(encryptionFullKey);
                        key = new Key(encryptionFullKey.getPrivKeyBytes(), storage.getKeyDeriver().getSalt(), EncryptionType.Deriver.ARGON2);
                        wallet.encrypt(key);
                        storage.setEncryptionPubKey(encryptionPubKey);
                        storage.saveWallet(wallet);
                        storage.restorePublicKeysFromSeed(wallet, key);
                        addWalletTabOrWindow(storage, wallet, false);

                        for(Wallet childWallet : wallet.getChildWallets()) {
                            if(!childWallet.isNested()) {
                                childWallet.encrypt(key);
                            }
                            storage.saveWallet(childWallet);
                            storage.restorePublicKeysFromSeed(childWallet, key);
                            addWalletTabOrWindow(storage, childWallet, false);
                        }
                        Platform.runLater(() -> selectTab(wallet));
                    } catch(IOException | StorageException | MnemonicException e) {
                        log.error("Error saving imported wallet", e);
                    } finally {
                        encryptionFullKey.clear();
                        if(key != null) {
                            key.clear();
                        }
                        keyDerivationService = null;
                    }
                });
                keyDerivationService.setOnFailed(workerStateEvent -> {
                    EventManager.get().post(new StorageEvent(Storage.getWalletFile(wallet.getName()).getAbsolutePath(), TimedEvent.Action.END, "Failed"));
                    showErrorDialog("Error encrypting wallet", keyDerivationService.getException().getMessage());
                    keyDerivationService = null;
                });
                EventManager.get().post(new StorageEvent(Storage.getWalletFile(wallet.getName()).getAbsolutePath(), TimedEvent.Action.START, "Encrypting wallet..."));
                keyDerivationService.start();
            }
        }
    }

    public void exportWallet(ActionEvent event) {
        WalletForm selectedWalletForm = getSelectedWalletForm();
        if(selectedWalletForm != null) {
            WalletExportDialog dlg = new WalletExportDialog(selectedWalletForm, getSelectedWalletForms());
            dlg.initOwner(rootStack.getScene().getWindow());
            Optional<Wallet> wallet = dlg.showAndWait();
            if(wallet.isPresent()) {
                //Successful export
            }
        }
    }

    public void openSettings(ActionEvent event) {
        openSettings(SettingsGroup.GENERAL);
    }

    public void openServerSettings(ActionEvent event) {
        openSettings(SettingsGroup.SERVER);
    }

    private void openSettings(SettingsGroup settingsGroup) {
        SettingsDialog settingsDialog = new SettingsDialog(settingsGroup);
        settingsDialog.initOwner(rootStack.getScene().getWindow());
        settingsDialog.showAndWait();
        configureSwitchServer();
        serverToggle.setDisable(!Config.get().hasServer());
    }

    public void signVerifyMessage(ActionEvent event) {
        MessageSignDialog messageSignDialog = null;
        WalletForm selectedWalletForm = getSelectedWalletForm();
        if(selectedWalletForm != null) {
            Wallet wallet = selectedWalletForm.getWallet();
            if(wallet.getKeystores().size() == 1) {
                //Can sign and verify
                messageSignDialog = new MessageSignDialog(wallet);
            }
        }

        if(messageSignDialog == null) {
            //Can verify only
            messageSignDialog = new MessageSignDialog();
        }

        messageSignDialog.initOwner(rootStack.getScene().getWindow());
        messageSignDialog.showAndWait();
    }

    public void sendToMany(ActionEvent event) {
        sendToMany(Collections.emptyList());
    }

    private void sendToMany(List<Payment> initialPayments) {
        if(sendToManyDialog != null) {
            Stage stage = (Stage)sendToManyDialog.getDialogPane().getScene().getWindow();
            stage.setAlwaysOnTop(true);
            stage.setAlwaysOnTop(false);
            return;
        }

        WalletForm selectedWalletForm = getSelectedWalletForm();
        if(selectedWalletForm != null) {
            Wallet wallet = selectedWalletForm.getWallet();
            BitcoinUnit bitcoinUnit = Config.get().getBitcoinUnit();
            if(bitcoinUnit == BitcoinUnit.AUTO) {
                bitcoinUnit = wallet.getAutoUnit();
            }

            sendToManyDialog = new SendToManyDialog(bitcoinUnit, initialPayments);
            sendToManyDialog.initModality(Modality.NONE);
            Optional<List<Payment>> optPayments = sendToManyDialog.showAndWait();
            sendToManyDialog = null;
            optPayments.ifPresent(payments -> {
                if(!payments.isEmpty()) {
                    EventManager.get().post(new SendActionEvent(wallet, new ArrayList<>(wallet.getSpendableUtxos().keySet())));
                    Platform.runLater(() -> EventManager.get().post(new SendPaymentsEvent(wallet, payments)));
                }
            });
        }
    }

    public void sweepPrivateKey(ActionEvent event) {
        Wallet wallet = null;
        WalletForm selectedWalletForm = getSelectedWalletForm();
        if(selectedWalletForm != null && selectedWalletForm.getWallet().isValid()) {
            wallet = selectedWalletForm.getWallet();
        }

        PrivateKeySweepDialog dialog = new PrivateKeySweepDialog(wallet);
        dialog.initOwner(rootStack.getScene().getWindow());
        Optional<Transaction> optTransaction = dialog.showAndWait();
        optTransaction.ifPresent(transaction -> addTransactionTab(null, null, transaction));
    }

    public void showPayNym(ActionEvent event) {
        WalletForm selectedWalletForm = getSelectedWalletForm();
        if(selectedWalletForm != null) {
            PayNymDialog payNymDialog = new PayNymDialog(selectedWalletForm.getWalletId());
            payNymDialog.initOwner(rootStack.getScene().getWindow());
            payNymDialog.showAndWait();
        }
    }

    public void verifyDownload(ActionEvent event) {
        if(downloadVerifierDialog != null) {
            Stage stage = (Stage)downloadVerifierDialog.getDialogPane().getScene().getWindow();
            stage.setAlwaysOnTop(true);
            stage.setAlwaysOnTop(false);
            if(event.getSource() instanceof File file) {
                downloadVerifierDialog.setInitialFile(file);
                downloadVerifierDialog.setSignatureFile(file);
            }
            return;
        }

        downloadVerifierDialog = new DownloadVerifierDialog(event.getSource() instanceof File file ? file : null);
        downloadVerifierDialog.initOwner(rootStack.getScene().getWindow());
        downloadVerifierDialog.showAndWait();
        downloadVerifierDialog = null;
    }

    public void minimizeToTray(ActionEvent event) {
        AppServices.get().minimizeStage((Stage)tabs.getScene().getWindow());
    }

    public void lockWallet(ActionEvent event) {
        WalletForm selectedWalletForm = getSelectedWalletForm();
        if(selectedWalletForm != null) {
            EventManager.get().post(new WalletLockEvent(selectedWalletForm.getMasterWallet()));
        }
    }

    public void lockWallets(ActionEvent event) {
        for(Tab tab : tabs.getTabs()) {
            TabData tabData = (TabData)tab.getUserData();
            if(tabData instanceof WalletTabData walletTabData) {
                if(!walletTabData.getWalletForm().isLocked()) {
                    EventManager.get().post(new WalletLockEvent(walletTabData.getWalletForm().getMasterWallet()));
                }
            }
        }
    }

    private boolean allWalletsLocked(Wallet lockingWallet) {
        for(Tab tab : tabs.getTabs()) {
            TabData tabData = (TabData)tab.getUserData();
            if(tabData instanceof WalletTabData walletTabData && walletTabData.getWallet() != lockingWallet && !walletTabData.getWalletForm().isLocked()) {
                return false;
            }
        }

        return true;
    }

    public void searchWallet(ActionEvent event) {
        Tab selectedTab = tabs.getSelectionModel().getSelectedItem();
        if(selectedTab != null) {
            TabData tabData = (TabData) selectedTab.getUserData();
            if(tabData instanceof WalletTabData) {
                TabPane subTabs = (TabPane) selectedTab.getContent();
                List<WalletForm> walletForms = subTabs.getTabs().stream().map(subTab -> ((WalletTabData)subTab.getUserData()).getWalletForm()).collect(Collectors.toList());
                if(!walletForms.isEmpty()) {
                    searchWallets(walletForms);
                }
            }
        }
    }

    public void searchAllWallets(ActionEvent event) {
        List<WalletForm> allWalletForms = new ArrayList<>();
        for(Tab tab : tabs.getTabs()) {
            TabData tabData = (TabData)tab.getUserData();
            if(tabData instanceof WalletTabData) {
                TabPane subTabs = (TabPane)tab.getContent();
                allWalletForms.addAll(subTabs.getTabs().stream().map(subTab -> ((WalletTabData)subTab.getUserData()).getWalletForm())
                        .filter(walletForm -> walletForm.getWallet().isValid() && !walletForm.isLocked()).collect(Collectors.toList()));
            }
        }

        if(allWalletForms.isEmpty()) {
            showErrorDialog("No wallets", "There are no open and unlocked wallets to search.");
        } else {
            searchWallets(allWalletForms);
        }
    }

    private void searchWallets(List<WalletForm> walletForms) {
        if(searchWalletDialog != null) {
            if(!searchWalletDialog.getWalletForms().equals(walletForms)) {
                searchWalletDialog.close();
            } else {
                Stage stage = (Stage)searchWalletDialog.getDialogPane().getScene().getWindow();
                stage.setAlwaysOnTop(true);
                stage.setAlwaysOnTop(false);
                return;
            }
        }

        Platform.runLater(() -> {
            searchWalletDialog = new SearchWalletDialog(walletForms);
            searchWalletDialog.initModality(Modality.NONE);
            Optional<Entry> optEntry = searchWalletDialog.showAndWait();
            if(optEntry.isPresent()) {
                Entry entry = optEntry.get();
                EventManager.get().post(new FunctionActionEvent(entry.getWalletFunction(), entry.getWallet()));
                Platform.runLater(() -> EventManager.get().post(new SelectEntryEvent(entry)));
            }
            searchWalletDialog = null;
        });
    }

    public void showAllWalletsSummary(ActionEvent event) {
        List<List<WalletForm>> allWalletForms = new ArrayList<>();
        for(Tab tab : tabs.getTabs()) {
            if(tab.getUserData() instanceof WalletTabData) {
                TabPane subTabs = (TabPane)tab.getContent();
                allWalletForms.add(subTabs.getTabs().stream().map(subTab -> ((WalletTabData)subTab.getUserData()).getWalletForm())
                        .filter(walletForm -> walletForm.getWallet().isValid() && !walletForm.isLocked()).collect(Collectors.toList()));
            }
        }

        if(allWalletForms.isEmpty() || allWalletForms.stream().allMatch(List::isEmpty)) {
            showErrorDialog("No wallets", "There are no open and unlocked wallets to summarize.");
        } else {
            WalletSummaryDialog walletSummaryDialog = new WalletSummaryDialog(allWalletForms);
            walletSummaryDialog.initOwner(rootStack.getScene().getWindow());
            walletSummaryDialog.showAndWait();
        }
    }

    public void showWalletSummary(ActionEvent event) {
        Tab selectedTab = tabs.getSelectionModel().getSelectedItem();
        if(selectedTab != null) {
            TabData tabData = (TabData) selectedTab.getUserData();
            if(tabData instanceof WalletTabData) {
                TabPane subTabs = (TabPane) selectedTab.getContent();
                List<WalletForm> walletForms = subTabs.getTabs().stream().map(subTab -> ((WalletTabData)subTab.getUserData()).getWalletForm()).collect(Collectors.toList());
                if(!walletForms.isEmpty()) {
                    WalletSummaryDialog walletSummaryDialog = new WalletSummaryDialog(List.of(walletForms));
                    walletSummaryDialog.initOwner(rootStack.getScene().getWindow());
                    walletSummaryDialog.showAndWait();
                }
            }
        }
    }

    public void refreshWallet(ActionEvent event) {
        WalletForm selectedWalletForm = getSelectedWalletForm();
        if(selectedWalletForm != null) {
            Wallet wallet = selectedWalletForm.getWallet();
            Wallet pastWallet = wallet.copy();
            wallet.clearHistory();
            AppServices.clearTransactionHistoryCache(wallet);
            EventManager.get().post(new WalletHistoryClearedEvent(wallet, pastWallet, selectedWalletForm.getWalletId()));
        }
    }

    public AppController addWalletTabOrWindow(Storage storage, Wallet wallet, boolean forceSameWindow) {
        Window existingWalletWindow = AppServices.get().getWindowForWallet(storage.getWalletId(wallet));
        if(existingWalletWindow instanceof Stage) {
            Stage existingWalletStage = (Stage)existingWalletWindow;
            existingWalletStage.toFront();

            EventManager.get().post(new ViewWalletEvent(existingWalletWindow, wallet, storage));
            return this;
        }

        if(!forceSameWindow && Config.get().isOpenWalletsInNewWindows() && !getOpenWallets().isEmpty()) {
            Stage stage = new Stage();
            AppController appController = AppServices.newAppWindow(stage);
            stage.toFront();
            stage.setX(AppServices.get().getWalletWindowMaxX() + 30);
            appController.addWalletTab(storage, wallet);
            return appController;
        } else {
            addWalletTab(storage, wallet);
            return this;
        }
    }

    public void addWalletTab(Storage storage, Wallet wallet) {
        if(wallet.isMasterWallet()) {
            String name = storage.getWalletName(wallet);
            if(!name.equals(wallet.getName())) {
                wallet.setName(name);
            }
            Tab tab = new Tab("");
            WalletIcon walletIcon = new WalletIcon(storage, wallet);
            walletIcon.setOpacity(TAB_LABEL_GRAPHIC_OPACITY_ACTIVE);
            Label tabLabel = new Label(name);
            tabLabel.setGraphic(walletIcon);
            tabLabel.setGraphicTextGap(5.0);
            tab.setGraphic(tabLabel);
            tab.setClosable(true);

            TabPane subTabs = new TabPane();
            subTabs.setSide(Side.LEFT);
            setSubTabsVisible(subTabs, areSubTabsVisible());
            subTabs.rotateGraphicProperty().set(true);
            tab.setContent(subTabs);

            WalletForm walletForm = addWalletSubTab(subTabs, storage, wallet);
            TabData tabData = new WalletTabData(TabData.TabType.WALLET, walletForm);
            tab.setUserData(tabData);
            tab.setContextMenu(getTabContextMenu(tab));
            walletForm.lockedProperty().addListener((observable, oldValue, newValue) -> {
                setSubTabsVisible(subTabs, !newValue && areSubTabsVisible());
            });

            subTabs.getSelectionModel().selectedItemProperty().addListener((observable, old_val, selectedTab) -> {
                if(selectedTab != null) {
                    EventManager.get().post(new WalletTabSelectedEvent(tab));
                }
            });

            subTabs.getTabs().addListener((ListChangeListener<Tab>) c -> {
                if(c.next() && (c.wasAdded() || c.wasRemoved())) {
                    EventManager.get().post(new OpenWalletsEvent(tabs.getScene().getWindow(), getOpenWalletTabData()));
                }
            });

            tabs.getTabs().add(tab);
            tabs.getSelectionModel().select(tab);

            File oldWalletFile = renamedWallets.remove(storage.getWalletFile());
            if(oldWalletFile != null) {
                deleteStorage(new Storage(oldWalletFile), false);
            }
        } else {
            for(Tab walletTab : tabs.getTabs()) {
                TabData tabData = (TabData)walletTab.getUserData();
                if(tabData instanceof WalletTabData) {
                    WalletTabData walletTabData = (WalletTabData)tabData;
                    if(walletTabData.getWallet() == wallet.getMasterWallet()) {
                        if(wallet.isNested()) {
                            WalletForm walletForm = new WalletForm(storage, wallet);
                            EventManager.get().register(walletForm);
                            walletTabData.getWalletForm().getNestedWalletForms().add(walletForm);
                        } else {
                            TabPane subTabs = (TabPane)walletTab.getContent();
                            addWalletSubTab(subTabs, storage, wallet);
                            Tab masterTab = subTabs.getTabs().stream().filter(tab -> ((WalletTabData)tab.getUserData()).getWallet().isMasterWallet()).findFirst().orElse(subTabs.getTabs().get(0));
                            Label masterLabel = (Label)masterTab.getGraphic();
                            masterLabel.setText(wallet.getMasterWallet().getLabel() != null ? wallet.getMasterWallet().getLabel() : wallet.getMasterWallet().getAutomaticName());
                            Platform.runLater(() -> {
                                setSubTabsVisible(subTabs, true);
                            });
                        }
                    }
                }
            }
        }

        EventManager.get().post(new WalletOpenedEvent(storage, wallet));
    }

    private void setSubTabsVisible(TabPane subTabs, boolean visible) {
        if(visible) {
            subTabs.getStyleClass().remove("master-only");
            if(!subTabs.getStyleClass().contains("wallet-subtabs")) {
                subTabs.getStyleClass().add("wallet-subtabs");
            }
        } else {
            if(!subTabs.getStyleClass().contains("master-only")) {
                subTabs.getStyleClass().add("master-only");
            }
            subTabs.getStyleClass().remove("wallet-subtabs");
        }
    }

    private void setSubTabsVisible(boolean visible) {
        for(Tab tab : tabs.getTabs()) {
            TabData tabData = (TabData) tab.getUserData();
            if(tabData instanceof WalletTabData) {
                setSubTabsVisible((TabPane)tab.getContent(), visible);
            }
        }
    }

    private boolean areSubTabsVisible() {
        if(subTabsVisible) {
            return true;
        }

        for(Wallet wallet : AppServices.get().getOpenWallets().keySet()) {
            if(wallet.getChildWallets().stream().anyMatch(childWallet -> !childWallet.isNested())) {
                subTabsVisible = true;
                return true;
            }
        }

        return false;
    }

    public WalletForm addWalletSubTab(TabPane subTabs, Storage storage, Wallet wallet) {
        try {
            Tab subTab = new Tab();
            subTab.setClosable(false);
            String label = wallet.getLabel() != null ? wallet.getLabel() : (wallet.isMasterWallet() ? wallet.getAutomaticName() : wallet.getName());
            Label subTabLabel = new Label(label);
            subTabLabel.setPadding(new Insets(0, 3, 0, 3));
            subTabLabel.setGraphic(getSubTabGlyph(wallet));
            subTabLabel.setContentDisplay(ContentDisplay.TOP);
            subTabLabel.setAlignment(Pos.TOP_CENTER);
            if(isSubTabLabelTruncated(subTabLabel, label)) {
                subTabLabel.setTooltip(new Tooltip(label));
            }
            subTab.setGraphic(subTabLabel);
            FXMLLoader walletLoader = new FXMLLoader(getClass().getResource("wallet/wallet.fxml"));
            subTab.setContent(walletLoader.load());
            WalletController controller = walletLoader.getController();

            EventManager.get().post(new WalletOpeningEvent(storage, wallet));

            //Note that only one WalletForm is created per wallet tab, and registered to listen for events. All wallet controllers (except SettingsController) share this instance.
            WalletForm walletForm = new WalletForm(storage, wallet);
            EventManager.get().register(walletForm);
            controller.setWalletForm(walletForm);

            TabData tabData = new WalletTabData(TabData.TabType.WALLET, walletForm);
            subTab.setUserData(tabData);
            if(!wallet.isWhirlpoolChildWallet()) {
                subTab.setContextMenu(getSubTabContextMenu(storage, wallet, subTabs, subTab));
            }

            subTabs.getTabs().add(subTab);
            subTabs.getTabs().sort((o1, o2) -> {
                WalletTabData tabData1 = (WalletTabData) o1.getUserData();
                WalletTabData tabData2 = (WalletTabData) o2.getUserData();
                return tabData1.getWallet().compareTo(tabData2.getWallet());
            });
            subTabs.getSelectionModel().select(subTab);

            if(wallet.isValid()) {
                Platform.runLater(() -> walletForm.refreshHistory(AppServices.getCurrentBlockHeight()));
            }

            return walletForm;
        } catch(IOException e) {
            throw new RuntimeException(e);
        }
    }

    private Glyph getSubTabGlyph(Wallet wallet) {
        Glyph tabGlyph;
        StandardAccount standardAccount = wallet.getStandardAccountType();
        if(standardAccount == StandardAccount.WHIRLPOOL_PREMIX) {
            tabGlyph = new Glyph(FontAwesome5.FONT_NAME, FontAwesome5.Glyph.RANDOM);
        } else if(standardAccount == StandardAccount.WHIRLPOOL_POSTMIX) {
            tabGlyph = new Glyph("FontAwesome", FontAwesome.Glyph.SEND);
        } else if(standardAccount == StandardAccount.WHIRLPOOL_BADBANK) {
            tabGlyph = new Glyph(FontAwesome5.FONT_NAME, FontAwesome5.Glyph.BIOHAZARD);
        } else {
            tabGlyph = new Glyph(FontAwesome5.FONT_NAME, FontAwesome5.Glyph.ARROW_DOWN);
        }

        tabGlyph.setFontSize(12);
        return tabGlyph;
    }

    public WalletForm getSelectedWalletForm() {
        Tab selectedTab = tabs.getSelectionModel().getSelectedItem();
        if(selectedTab != null) {
            TabData tabData = (TabData)selectedTab.getUserData();
            if(tabData instanceof WalletTabData) {
                TabPane subTabs = (TabPane)selectedTab.getContent();
                Tab selectedSubTab = subTabs.getSelectionModel().getSelectedItem();
                WalletTabData subWalletTabData = (WalletTabData)selectedSubTab.getUserData();
                return subWalletTabData.getWalletForm();
            }
        }

        return null;
    }

    public List<WalletForm> getSelectedWalletForms() {
        Tab selectedTab = tabs.getSelectionModel().getSelectedItem();
        if(selectedTab != null) {
            TabData tabData = (TabData) selectedTab.getUserData();
            if(tabData instanceof WalletTabData) {
                TabPane subTabs = (TabPane) selectedTab.getContent();
                return subTabs.getTabs().stream().map(subTab -> ((WalletTabData) subTab.getUserData()).getWalletForm()).collect(Collectors.toList());
            }
        }

        return Collections.emptyList();
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
            //Don't verify signatures here - provided PSBT may omit UTXO data that can be found when combining with an existing PSBT
            PSBT psbt = new PSBT(bytes, false);
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
        //Convert to PSBTv0 first
        if(psbt.getVersion() != null && psbt.getVersion() >= 2) {
            psbt.convertVersion(0);
        }

        //Add any missing previous outputs if available in open wallets
        for(PSBTInput psbtInput : psbt.getPsbtInputs()) {
            if(psbtInput.getUtxo() == null) {
                for(Wallet wallet : AppServices.get().getOpenWallets().keySet().stream().filter(Wallet::isValid).toList()) {
                    TransactionOutPoint outpoint = psbtInput.getInput().getOutpoint();
                    BlockTransaction blockTransaction = wallet.getWalletTransaction(outpoint.getHash());
                    if(blockTransaction != null && blockTransaction.getTransaction().getOutputs().size() > outpoint.getIndex()) {
                        psbtInput.setNonWitnessUtxo(blockTransaction.getTransaction());
                        ScriptType type = psbtInput.getScriptType();
                        if(type != null && Arrays.asList(ScriptType.WITNESS_TYPES).contains(type)) {
                            psbtInput.setWitnessUtxo(blockTransaction.getTransaction().getOutputs().get((int)outpoint.getIndex()));
                            psbtInput.setNonWitnessUtxo(null);
                        }
                        break;
                    }
                }
            }
        }

        //Add DNS payment information if not already cached
        for(PSBTOutput psbtOutput : psbt.getPsbtOutputs()) {
            if(psbtOutput.getDnssecProof() != null && !psbtOutput.getDnssecProof().isEmpty()) {
                Address address = psbtOutput.getScript() != null ? psbtOutput.getScript().getToAddress() : null;
                if(address != null && DnsPaymentCache.getDnsPayment(address) == null) {
                    try {
                        Optional<DnsPayment> optDnsPayment = psbtOutput.getDnsPayment();
                        if(optDnsPayment.isPresent() && address.equals(optDnsPayment.get().bitcoinURI().getAddress())) {
                            DnsPaymentCache.putDnsPayment(address, optDnsPayment.get());
                        }
                    } catch(Exception e) {
                        log.debug("Error resolving DNS payment", e);
                    }
                }

                SilentPaymentAddress silentPaymentAddress = psbtOutput.getSilentPaymentAddress();
                if(address != null && silentPaymentAddress == null) {
                    silentPaymentAddress = AppServices.get().getOpenWallets().keySet().stream()
                            .map(wallet -> wallet.getSilentPaymentAddress(address)).filter(Objects::nonNull).findFirst().orElse(null);
                }
                if(silentPaymentAddress != null && DnsPaymentCache.getDnsPayment(silentPaymentAddress) == null) {
                    try {
                        Optional<DnsPayment> optDnsPayment = psbtOutput.getDnsPayment();
                        if(optDnsPayment.isPresent() && silentPaymentAddress.equals(optDnsPayment.get().bitcoinURI().getSilentPaymentAddress())) {
                            DnsPaymentCache.putDnsPayment(silentPaymentAddress, optDnsPayment.get());
                        }
                    } catch(Exception e) {
                        log.debug("Error resolving DNS payment", e);
                    }
                }
            }
        }

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

    private void addTransactionTab(Transaction transaction, TransactionView initialView, Integer initialIndex) {
        addTransactionTab(null, null, transaction, null, null, initialView, initialIndex);
    }

    private void addTransactionTab(BlockTransaction blockTransaction, TransactionView initialView, Integer initialIndex) {
        addTransactionTab(blockTransaction.getLabel(), null, blockTransaction.getTransaction(), null, blockTransaction, initialView, initialIndex);
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

        if(psbt != null) {
            try {
                //Any PSBTs that have reached this point could not be combined with an existing PSBT. Verify signatures before continuing
                psbt.verifySignatures();
            } catch(PSBTSignatureException e) {
                AppServices.showErrorDialog("Invalid PSBT", e.getMessage());
                return;
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
            tabLabel.setMaxWidth(TAB_LABEL_MAX_WIDTH);
            tabLabel.setGraphic(glyph);
            tabLabel.setGraphicTextGap(5.0);
            if(TextUtils.computeTextWidth(tabLabel.getFont(), tabName, 0.0D) > TAB_LABEL_MAX_WIDTH) {
                Tooltip tooltip = new Tooltip(tabName);
                tabLabel.setTooltip(tooltip);
            }
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

        if(tab.getUserData() instanceof WalletTabData walletTabData) {
            MenuItem lock = new MenuItem("Lock");
            Glyph lockGlyph = new Glyph("FontAwesome", FontAwesome.Glyph.LOCK);
            lockGlyph.setFontSize(12);
            lock.setGraphic(lockGlyph);
            lock.disableProperty().bind(walletTabData.getWalletForm().lockedProperty());
            lock.setOnAction(event -> {
                EventManager.get().post(new WalletLockEvent(walletTabData.getWallet()));
            });
            contextMenu.getItems().addAll(lock);
        }

        MenuItem moveRight = new MenuItem("Move Right");
        moveRight.setAccelerator(new KeyCodeCombination(KeyCode.RIGHT, KeyCombination.CONTROL_DOWN, KeyCombination.SHIFT_DOWN));
        moveRight.setOnAction(event -> {
            int currentIndex = tabs.getSelectionModel().getSelectedIndex();
            if(currentIndex + 1 >= tabs.getTabs().size()) {
                return;
            }
            Tab selectedTab = tabs.getSelectionModel().getSelectedItem();
            tabs.getTabs().removeListener(tabsChangeListener);
            tabs.getTabs().remove(selectedTab);
            tabs.getTabs().add(currentIndex + 1, selectedTab);
            tabs.getTabs().addListener(tabsChangeListener);
            tabs.getSelectionModel().select(selectedTab);
            EventManager.get().post(new RequestOpenWalletsEvent());   //Rearrange recent files list
        });
        MenuItem moveLeft = new MenuItem("Move Left");
        moveLeft.setAccelerator(new KeyCodeCombination(KeyCode.LEFT, KeyCombination.CONTROL_DOWN, KeyCombination.SHIFT_DOWN));
        moveLeft.setOnAction(event -> {
            int currentIndex = tabs.getSelectionModel().getSelectedIndex();
            if(currentIndex == 0) {
                return;
            }
            Tab selectedTab = tabs.getSelectionModel().getSelectedItem();
            tabs.getTabs().removeListener(tabsChangeListener);
            tabs.getTabs().remove(selectedTab);
            tabs.getTabs().add(currentIndex - 1, selectedTab);
            tabs.getTabs().addListener(tabsChangeListener);
            tabs.getSelectionModel().select(selectedTab);
            EventManager.get().post(new RequestOpenWalletsEvent());   //Rearrange recent files list
        });
        contextMenu.getItems().addAll(moveRight, moveLeft);

        contextMenu.setOnShowing(event -> {
            moveRight.setDisable(tabs.getTabs().indexOf(tab) == tabs.getTabs().size() - 1);
            moveLeft.setDisable(tabs.getTabs().indexOf(tab) == 0);
        });

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

        contextMenu.getItems().addAll(new SeparatorMenuItem(), close, closeOthers, closeAll);

        if(tab.getUserData() instanceof WalletTabData walletTabData) {
            Menu walletIcon = new Menu("Wallet Icon");
            MenuItem custom = new MenuItem("Custom...");
            custom.setOnAction(event -> {
                setCustomIcon(walletTabData.getWallet());
            });
            MenuItem reset = new MenuItem("Reset");
            reset.setOnAction(event -> {
                resetIcon(walletTabData.getWalletForm());
            });
            walletIcon.getItems().addAll(custom, reset);

            MenuItem delete = new MenuItem("Delete...");
            delete.setOnAction(event -> {
                deleteWallet(walletTabData.getWalletForm());
            });
            contextMenu.getItems().addAll(new SeparatorMenuItem(), walletIcon, delete);
        }

        return contextMenu;
    }

    private void setCustomIcon(Wallet wallet) {
        Stage window = new Stage();

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Open Image");
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("All Files", OsType.getCurrent().equals(OsType.UNIX) ? "*" : "*.*"),
                new FileChooser.ExtensionFilter("Images", "*.png", "*.jpg", "*.jpeg", "*.gif")
        );

        AppServices.moveToActiveWindowScreen(window, 800, 450);
        File file = fileChooser.showOpenDialog(window);
        if(file != null) {
            try {
                byte[] iconData = ImageUtils.resize(file, WalletIcon.SAVE_WIDTH, WalletIcon.SAVE_HEIGHT);
                WalletConfig walletConfig = wallet.getMasterWalletConfig();
                walletConfig.setIconData(iconData, true);
                EventManager.get().post(new WalletConfigChangedEvent(wallet));
            } catch(Exception e) {
                log.error("Error creating custom wallet icon", e);
                showErrorDialog("Error creating custom wallet icon", e.getMessage());
            }
        }
    }

    private void resetIcon(WalletForm walletForm) {
        Wallet masterWallet = walletForm.getMasterWallet();
        if(masterWallet.getWalletConfig() != null && masterWallet.getWalletConfig().isUserIcon()) {
            masterWallet.getWalletConfig().setIconData(null, false);
            EventManager.get().post(new WalletConfigChangedEvent(masterWallet));
        }
    }

    private void renameWallet(WalletForm selectedWalletForm) {
        WalletNameDialog walletNameDialog = new WalletNameDialog(selectedWalletForm.getMasterWallet().getName(), false, null, true);
        walletNameDialog.initOwner(rootStack.getScene().getWindow());
        Optional<WalletNameDialog.NameAndBirthDate> optName = walletNameDialog.showAndWait();
        if(optName.isPresent()) {
            File walletFile = Storage.getWalletFile(optName.get().getName() + "." + PersistenceType.DB.getExtension());
            if(walletFile.exists()) {
                showErrorDialog("Error renaming wallet", "Wallet file " + walletFile.getAbsolutePath() + " already exists.");
                return;
            }

            Storage.CopyWalletService copyWalletService = new Storage.CopyWalletService(selectedWalletForm.getWallet(), walletFile);
            copyWalletService.setOnSucceeded(event -> {
                renamedWallets.put(walletFile, selectedWalletForm.getStorage().getWalletFile());
                tabs.getTabs().remove(tabs.getSelectionModel().getSelectedItem());
                openWalletFile(walletFile, true);
            });
            copyWalletService.setOnFailed(event -> {
                log.error("Error renaming wallet", event.getSource().getException());
                showErrorDialog("Error renaming wallet", event.getSource().getException().getMessage());
            });
            copyWalletService.start();
        }
    }

    private void deleteWallet(WalletForm selectedWalletForm) {
        Optional<ButtonType> optButtonType = AppServices.showWarningDialog("Delete " + selectedWalletForm.getWallet().getMasterName() + "?", "The wallet file and any backups will be deleted. Are you sure?", ButtonType.NO, ButtonType.YES);
        if(optButtonType.isPresent() && optButtonType.get() == ButtonType.YES) {
            Storage storage = selectedWalletForm.getStorage();
            if(selectedWalletForm.getMasterWallet().isEncrypted()) {
                WalletPasswordDialog dlg = new WalletPasswordDialog(selectedWalletForm.getWallet().getMasterName(), WalletPasswordDialog.PasswordRequirement.LOAD);
                dlg.initOwner(rootStack.getScene().getWindow());
                Optional<SecureString> password = dlg.showAndWait();
                if(password.isPresent()) {
                    keyDerivationService = new Storage.KeyDerivationService(storage, password.get(), true);
                    keyDerivationService.setOnSucceeded(workerStateEvent -> {
                        EventManager.get().post(new StorageEvent(selectedWalletForm.getWalletId(), TimedEvent.Action.END, "Done"));
                        ECKey encryptionFullKey = keyDerivationService.getValue();

                        try {
                            tabs.getTabs().remove(tabs.getSelectionModel().getSelectedItem());
                            deleteStorage(storage, true);
                        } finally {
                            encryptionFullKey.clear();
                            keyDerivationService = null;
                        }
                    });
                    keyDerivationService.setOnFailed(workerStateEvent -> {
                        EventManager.get().post(new StorageEvent(selectedWalletForm.getWalletId(), TimedEvent.Action.END, "Failed"));
                        if(keyDerivationService.getException() instanceof InvalidPasswordException) {
                            Optional<ButtonType> optResponse = showErrorDialog("Invalid Password", "The wallet password was invalid. Try again?", ButtonType.CANCEL, ButtonType.OK);
                            if(optResponse.isPresent() && optResponse.get().equals(ButtonType.OK)) {
                                Platform.runLater(() -> deleteWallet(getSelectedWalletForm()));
                            }
                        } else {
                            log.error("Error deriving wallet key", keyDerivationService.getException());
                        }
                        keyDerivationService = null;
                    });
                    EventManager.get().post(new StorageEvent(selectedWalletForm.getWalletId(), TimedEvent.Action.START, "Decrypting wallet..."));
                    keyDerivationService.start();
                }
            } else {
                tabs.getTabs().remove(tabs.getSelectionModel().getSelectedItem());
                deleteStorage(storage, true);
            }
        }
    }

    private void deleteStorage(Storage storage, boolean deleteBackups) {
        if(storage.isClosed()) {
            Platform.runLater(() -> {
                Storage.DeleteWalletService deleteWalletService = new Storage.DeleteWalletService(storage, deleteBackups);
                deleteWalletService.setDelay(Duration.seconds(3));
                deleteWalletService.setPeriod(Duration.hours(1));
                deleteWalletService.setOnSucceeded(event -> {
                    deleteWalletService.cancel();
                    if(!deleteWalletService.getValue()) {
                        showErrorDialog("Error deleting wallet", "Could not delete " + storage.getWalletFile().getName()  + ". Please delete this file manually.");
                    }
                });
                deleteWalletService.setOnFailed(event -> {
                    deleteWalletService.cancel();
                    showErrorDialog("Error deleting wallet", "Could not delete " + storage.getWalletFile().getName()  + ". Please delete this file manually.");
                });
                deleteWalletService.start();
            });
        } else {
            Platform.runLater(() -> deleteStorage(storage, deleteBackups));
        }
    }

    private ContextMenu getSubTabContextMenu(Storage storage, Wallet wallet, TabPane subTabs, Tab subTab) {
        String walletId = storage.getWalletId(wallet);

        ContextMenu contextMenu = new ContextMenu();
        MenuItem rename = new MenuItem("Rename Account");
        rename.setOnAction(event -> {
            Label subTabLabel = (Label)subTab.getGraphic();
            WalletLabelDialog walletLabelDialog = new WalletLabelDialog(subTabLabel.getText());
            walletLabelDialog.initOwner(rootStack.getScene().getWindow());
            Optional<String> optLabel = walletLabelDialog.showAndWait();
            if(optLabel.isPresent()) {
                String label = optLabel.get();
                subTabLabel.setText(label);
                if(isSubTabLabelTruncated(subTabLabel, label)) {
                    subTabLabel.setTooltip(new Tooltip(label));
                } else {
                    subTabLabel.setTooltip(null);
                }

                Wallet renamedWallet = AppServices.get().getWallet(walletId);
                renamedWallet.setLabel(label);
                EventManager.get().post(new WalletLabelChangedEvent(renamedWallet));
            }
        });
        contextMenu.getItems().add(rename);

        if(!wallet.isMasterWallet() && !wallet.isWhirlpoolChildWallet()) {
            MenuItem delete = new MenuItem("Delete Account");
            delete.setOnAction(event -> {
                Optional<ButtonType> optButtonType = AppServices.showWarningDialog("Delete Wallet Account?", "Labels applied in this wallet account will be lost. Are you sure?", ButtonType.CANCEL, ButtonType.OK);
                if(optButtonType.isPresent() && optButtonType.get() == ButtonType.OK) {
                    Wallet deletedWallet = AppServices.get().getWallet(walletId);
                    EventManager.get().post(new WalletDeletedEvent(deletedWallet));
                    subTabs.getTabs().remove(subTab);
                    if(subTabs.getTabs().size() == 1) {
                        setSubTabsVisible(subTabs, areSubTabsVisible());
                    }
                }
            });
            contextMenu.getItems().add(delete);
        }

        contextMenu.setOnShowing(event -> {
            Wallet renameWallet = AppServices.get().getWallet(walletId);
            rename.setDisable(!renameWallet.isValid());
        });

        return contextMenu;
    }

    private boolean isSubTabLabelTruncated(Label subTabLabel, String label) {
        return TextUtils.computeTextWidth(subTabLabel.getFont(), label, 0.0D) > (90-6);
    }

    private void configureSwitchServer() {
        switchServer.getItems().clear();

        Config config = Config.get();
        if(config.getServerType() == ServerType.BITCOIN_CORE && config.getRecentCoreServers() != null && config.getRecentCoreServers().size() > 1) {
            for(Server server : config.getRecentCoreServers()) {
                switchServer.getItems().add(getSwitchServerMenuItem(ServerType.BITCOIN_CORE, server));
            }
        } else if(config.getServerType() == ServerType.ELECTRUM_SERVER && config.getRecentElectrumServers() != null && config.getRecentElectrumServers().size() > 1) {
            for(Server server : config.getRecentElectrumServers()) {
                switchServer.getItems().add(getSwitchServerMenuItem(ServerType.ELECTRUM_SERVER, server));
            }
        }

        switchServer.setVisible(!switchServer.getItems().isEmpty());
    }

    private CheckMenuItem getSwitchServerMenuItem(ServerType serverType, Server server) {
        CheckMenuItem checkMenuItem = new CheckMenuItem(server.getDisplayName());
        boolean selected = (serverType == ServerType.BITCOIN_CORE ? server.equals(Config.get().getCoreServer()) : server.equals(Config.get().getElectrumServer()));
        checkMenuItem.setSelected(selected);
        checkMenuItem.setOnAction(event -> {
            if(!selected) {
                boolean online = onlineProperty().get();
                onlineProperty().set(false);
                if(serverType == ServerType.BITCOIN_CORE) {
                    Config.get().setCoreServer(server);
                } else if(serverType == ServerType.ELECTRUM_SERVER) {
                    Config.get().setElectrumServer(server);
                }
                Platform.runLater(() -> {
                    onlineProperty().set(online);
                    configureSwitchServer();
                });
            } else {
                checkMenuItem.setSelected(true);
            }
        });

        return checkMenuItem;
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

        serverToggle.setDisable(!Config.get().hasServer());
    }

    public void setTheme(ActionEvent event) {
        Theme selectedTheme = (Theme)theme.getSelectedToggle().getUserData();
        if(Config.get().getTheme() != selectedTheme) {
            Config.get().setTheme(selectedTheme);
        }

        EventManager.get().post(new ThemeChangedEvent(selectedTheme));
    }

    private void serverToggleStartAnimation() {
        Node thumbArea = serverToggle.lookup(".thumb-area");
        if(thumbArea != null) {
            Timeline timeline = AnimationUtil.getPulse(thumbArea, Duration.millis(600), 1.0, 0.25, 8);
            timeline.play();
            serverToggle.setUserData(new AnimationUtil.AnimatedNode(thumbArea, timeline));
        }
    }

    private void serverToggleStopAnimation() {
        if(serverToggle.getUserData() != null) {
            AnimationUtil.AnimatedNode animatedNode = (AnimationUtil.AnimatedNode)serverToggle.getUserData();
            animatedNode.timeline().stop();
            animatedNode.node().setOpacity(1.0);
            serverToggle.setUserData(null);
        }
    }

    private void tabLabelStartAnimation(Wallet wallet) {
        tabs.getTabs().stream().filter(tab -> tab.getUserData() instanceof WalletTabData && ((TabPane)tab.getContent()).getTabs().stream().map(subTab -> ((WalletTabData)subTab.getUserData()).getWallet()).anyMatch(tabWallet -> tabWallet == wallet)).forEach(this::tabLabelStartAnimation);
    }

    private void tabLabelStartAnimation(Transaction transaction) {
        tabs.getTabs().stream().filter(tab -> tab.getUserData() instanceof TransactionTabData && ((TransactionTabData)tab.getUserData()).getTransaction().getTxId().equals(transaction.getTxId())).forEach(this::tabLabelStartAnimation);
    }

    private void tabLabelStartAnimation(Tab tab) {
        Label tabLabel = (Label) tab.getGraphic();
        if(tabLabel.getUserData() == null) {
            Timeline timeline = AnimationUtil.getPulse(tabLabel.getGraphic(), Duration.millis(1000), tabLabel.getGraphic().getOpacity(), 0.1, 8);
            timeline.play();
            tabLabel.setUserData(timeline);
        }
    }

    private void tabLabelAddFailure(Tab tab) {
        Label tabLabel = (Label)tab.getGraphic();
        WalletIcon walletIcon = (WalletIcon)tabLabel.getGraphic();
        if(walletIcon.addFailure()) {
            tabLabel.setTooltip(new Tooltip("Error loading transaction history from server"));
        }
    }

    private void tabLabelStopAnimation(Wallet wallet) {
        Set<Wallet> relatedWallets = new HashSet<>(wallet.isMasterWallet() ? wallet.getChildWallets() : wallet.getMasterWallet().getChildWallets());
        relatedWallets.remove(wallet);
        if(!wallet.isMasterWallet()) {
            relatedWallets.add(wallet.getMasterWallet());
        }

        if(loadingWallets.stream().noneMatch(relatedWallets::contains)) {
            tabs.getTabs().stream().filter(tab -> tab.getUserData() instanceof WalletTabData && ((TabPane)tab.getContent()).getTabs().stream().map(subTab -> ((WalletTabData)subTab.getUserData()).getWallet()).anyMatch(tabWallet -> tabWallet == wallet)).forEach(this::tabLabelStopAnimation);
        }
    }

    private void tabLabelStopAnimation(Transaction transaction) {
        tabs.getTabs().stream().filter(tab -> tab.getUserData() instanceof TransactionTabData && ((TransactionTabData)tab.getUserData()).getTransaction().getTxId().equals(transaction.getTxId())).forEach(this::tabLabelStopAnimation);
    }

    private void tabLabelStopAnimation(Tab tab) {
        Label tabLabel = (Label) tab.getGraphic();
        if(tabLabel.getUserData() != null) {
            Animation animation = (Animation)tabLabel.getUserData();
            animation.stop();
            tabLabel.setUserData(null);
            tabLabel.getGraphic().setOpacity(tab.isSelected() ? TAB_LABEL_GRAPHIC_OPACITY_ACTIVE : TAB_LABEL_GRAPHIC_OPACITY_INACTIVE);
        }
    }

    private void tabLabelRemoveFailure(Tab tab) {
        Label tabLabel = (Label)tab.getGraphic();
        WalletIcon walletIcon = (WalletIcon)tabLabel.getGraphic();
        walletIcon.removeFailure();
        tabLabel.setTooltip(null);
    }

    private void setTorIcon() {
        TorStatusLabel torStatusLabel = null;
        for(Node node : statusBar.getRightItems()) {
            if(node instanceof TorStatusLabel) {
                torStatusLabel = (TorStatusLabel)node;
            }
        }

        if(!AppServices.isUsingProxy()) {
            if(torStatusLabel != null) {
                torStatusLabel.update();
                statusBar.getRightItems().removeAll(torStatusLabel);
            }
        } else {
            if(torStatusLabel == null) {
                torStatusLabel = new TorStatusLabel();
                statusBar.getRightItems().add(Math.max(statusBar.getRightItems().size() - 2, 0), torStatusLabel);
            } else {
                torStatusLabel.update();
            }
        }
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

        for(Tab tab : tabs.getTabs()) {
            if(tab.getUserData() instanceof WalletTabData) {
                Label tabLabel = (Label)tab.getGraphic();
                WalletIcon walletIcon = (WalletIcon)tabLabel.getGraphic();
                walletIcon.refresh();
            }
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
                lockWallet.setDisable(true);
                exportWallet.setDisable(true);
                showLoadingLog.setDisable(true);
                showTxHex.setDisable(false);
                showPayNym.setDisable(true);
            } else if(event instanceof WalletTabSelectedEvent) {
                WalletTabSelectedEvent walletTabEvent = (WalletTabSelectedEvent)event;
                WalletTabData walletTabData = walletTabEvent.getWalletTabData();
                saveTransaction.setVisible(true);
                saveTransaction.setDisable(true);
                lockWallet.setDisable(walletTabData.getWalletForm().lockedProperty().get());
                exportWallet.setDisable(walletTabData.getWallet() == null || !walletTabData.getWallet().isValid() || walletTabData.getWalletForm().isLocked());
                refreshWallet.setText(walletTabData.getWallet() == null || walletTabData.getWalletForm().getMasterWallet().getChildWallets().stream().allMatch(Wallet::isNested) ? "Refresh Wallet" : "Refresh Wallet Account");
                showLoadingLog.setDisable(false);
                showTxHex.setDisable(true);
                showPayNym.setDisable(exportWallet.isDisable() || !walletTabData.getWallet().hasPaymentCode());
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
        WalletForm selectedWalletForm = getSelectedWalletForm();
        if(selectedWalletForm != null) {
            if(selectedWalletForm.getWalletId().equals(event.getWalletId())) {
                exportWallet.setDisable(!event.getWallet().isValid() || selectedWalletForm.isLocked());
                showPayNym.setDisable(exportWallet.isDisable() || !event.getWallet().hasPaymentCode());
            }
        }

        for(Tab walletTab : tabs.getTabs()) {
            TabData tabData = (TabData) walletTab.getUserData();
            if(tabData instanceof WalletTabData walletTabData) {
                if(walletTabData.getWalletForm().getWalletId().equals(event.getWalletId()) && event.getWallet().isMasterWallet()) {
                    TabPane subTabs = (TabPane)walletTab.getContent();
                    Tab masterTab = subTabs.getTabs().stream().filter(tab -> ((WalletTabData)tab.getUserData()).getWallet().isMasterWallet()).findFirst().orElse(subTabs.getTabs().get(0));
                    Label masterLabel = (Label)masterTab.getGraphic();
                    masterLabel.setText(event.getWallet().getLabel() != null ? event.getWallet().getLabel() : event.getWallet().getAutomaticName());
                    Label tabLabel = (Label)walletTab.getGraphic();
                    WalletIcon walletIcon = (WalletIcon)tabLabel.getGraphic();
                    walletIcon.setWallet(event.getWallet());
                }
            }
        }
    }

    @Subscribe
    public void newWalletTransactions(NewWalletTransactionsEvent event) {
        if(Config.get().isNotifyNewTransactions() && getOpenWallets().containsKey(event.getWallet())) {
            for(Tab tab : tabs.getTabs()) {
                if(tab.getUserData() instanceof WalletTabData) {
                    TabPane subTabs = (TabPane)tab.getContent();
                    for(Tab subTab : subTabs.getTabs()) {
                        TabData tabData = (TabData)subTab.getUserData();
                        if(tabData instanceof WalletTabData walletTabData) {
                            if(walletTabData.getWallet().equals(event.getWallet()) && walletTabData.getWalletForm().lockedProperty().get()) {
                                return;
                            }
                        }
                    }
                }
            }

            List<BlockTransaction> blockTransactions = new ArrayList<>(event.getBlockTransactions());
            List<BlockTransaction> whirlpoolTransactions = event.getUnspentConfirmingWhirlpoolMixTransactions();
            blockTransactions.removeAll(whirlpoolTransactions);

            if(!whirlpoolTransactions.isEmpty()) {
                BlockTransaction blockTransaction = whirlpoolTransactions.get(0);
                String status;
                String walletName = event.getWallet().getMasterName() + " " + event.getWallet().getName().toLowerCase(Locale.ROOT);
                long value = blockTransaction.getTransaction().getOutputs().iterator().next().getValue();
                long mempoolValue = whirlpoolTransactions.stream().filter(tx -> tx.getHeight() <= 0).mapToLong(tx -> value).sum();
                long blockchainValue = whirlpoolTransactions.stream().filter(tx -> tx.getHeight() > 0).mapToLong(tx -> value).sum();

                if(mempoolValue > 0) {
                    status = "New " + walletName + " mempool transaction" + (mempoolValue > value ? "s: " : ": ") + event.getValueAsText(mempoolValue);
                } else {
                    status = "Confirming " + walletName + " transaction" + (blockchainValue > value ? "s: " : ": ") + event.getValueAsText(blockchainValue);
                }

                statusUpdated(new StatusEvent(status));
            }

            String text = null;
            if(blockTransactions.size() == 1) {
                BlockTransaction blockTransaction = blockTransactions.get(0);
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
            } else if(blockTransactions.size() > 1) {
                if(event.getTotalBlockchainValue() > 0 && event.getTotalMempoolValue() > 0) {
                    text = "New transactions: " + event.getValueAsText(event.getTotalValue()) + " total";
                } else if(event.getTotalMempoolValue() > 0) {
                    text = "New mempool transactions: " + event.getValueAsText(event.getTotalMempoolValue()) + " total";
                } else {
                    text = "New transactions: " + event.getValueAsText(event.getTotalValue()) + " total";
                }
            }

            if(text != null) {
                Window.getWindows().forEach(window -> {
                    String notificationStyles = AppController.class.getResource("notificationpopup.css").toExternalForm();
                    if(!window.getScene().getStylesheets().contains(notificationStyles)) {
                        window.getScene().getStylesheets().add(notificationStyles);
                    }
                });

                String walletName = event.getWallet().getFullDisplayName();
                if(walletName.length() > 40) {
                    walletName = walletName.substring(0, 40) + "...";
                }

                Notifications notificationBuilder = Notifications.create()
                        .title("Sparrow - " + walletName)
                        .text(text)
                        .graphic(new DialogImage(DialogImage.Type.SPARROW))
                        .hideAfter(Duration.seconds(15))
                        .position(Pos.TOP_RIGHT)
                        .threshold(5, Notifications.create().title("Sparrow").text("Multiple new wallet transactions").graphic(new DialogImage(DialogImage.Type.SPARROW)))
                        .onAction(e -> selectTab(event.getWallet()));

                //If controlsfx can't find our window, we must set the window ourselves (unfortunately notification is then shown within this window)
                if(org.controlsfx.tools.Utils.getWindow(null) == null) {
                    notificationBuilder.owner(tabs.getScene().getWindow());
                }

                notificationBuilder.show();
            }
        }
    }

    @Subscribe
    public void statusUpdated(StatusEvent event) {
        statusBar.setText(event.getStatus());
        statusBar.setGraphic(event.getGraphic());

        if(wait != null && wait.getStatus() == Animation.Status.RUNNING) {
            wait.stop();
        }
        wait = new PauseTransition(Duration.seconds(event.getShowDuration()));
        wait.setOnFinished((e) -> {
            if(statusBar.getText().equals(event.getStatus())) {
                statusBar.setText("");
                statusBar.setGraphic(null);
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

        Hyperlink existingUpdateLabel = null;
        for(Node node : statusBar.getRightItems()) {
            if(node instanceof Hyperlink) {
                existingUpdateLabel = (Hyperlink)node;
            }
        }

        if(existingUpdateLabel != null) {
            statusBar.getRightItems().remove(existingUpdateLabel);
        }

        statusBar.getRightItems().add(0, versionUpdateLabel);
    }

    @Subscribe
    public void timedWorker(TimedEvent event) {
        statusBar.setGraphic(null);
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
                        statusBar.setGraphic(null);
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
        serverToggleStartAnimation();
    }

    @Subscribe
    public void connectionFailed(ConnectionFailedEvent event) {
        String status = CONNECTION_FAILED_PREFIX + event.getMessage();
        Hyperlink hyperlink = new Hyperlink("Server Settings");
        hyperlink.setOnAction(this::openServerSettings);
        statusUpdated(new StatusEvent(status, hyperlink));
        serverToggleStopAnimation();
        setTorIcon();
    }

    @Subscribe
    public void connection(ConnectionEvent event) {
        String status = "Connected to " + Config.get().getServerDisplayName() + " at height " + event.getBlockHeight();
        statusUpdated(new StatusEvent(status));
        setServerToggleTooltip(event.getBlockHeight());
        serverToggleStopAnimation();
        setTorIcon();
        configureSwitchServer();
    }

    @Subscribe
    public void disconnection(DisconnectionEvent event) {
        serverToggle.setDisable(false);
        if(!AppServices.isConnecting() && !AppServices.isConnected() && !statusBar.getText().startsWith(CONNECTION_FAILED_PREFIX) && !statusBar.getText().contains(TRYING_ANOTHER_SERVER_MESSAGE)) {
            statusUpdated(new StatusEvent("Disconnected (click toggle on the right to connect)", 240));
        }
        if(statusTimeline == null || statusTimeline.getStatus() != Animation.Status.RUNNING) {
            statusBar.setProgress(0);
        }
        for(Wallet wallet : getOpenWallets().keySet()) {
            tabLabelStopAnimation(wallet);
        }
        serverToggleStopAnimation();
    }

    @Subscribe
    public void walletOpened(WalletOpenedEvent walletOpenedEvent) {
        if(!subTabsVisible && walletOpenedEvent.getWallet().getChildWallets().stream().anyMatch(childWallet -> !childWallet.isNested())) {
            subTabsVisible = true;
            setSubTabsVisible(true);
        }
    }

    @Subscribe
    public void walletTabsClosed(WalletTabsClosedEvent event) {
        event.getClosedWalletTabData().stream().map(WalletTabData::getWallet).forEach(loadingWallets::remove);
        if(event.getClosedWalletTabData().stream().map(WalletTabData::getWallet).anyMatch(emptyLoadingWallets::remove) && emptyLoadingWallets.isEmpty()) {
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
            if(event.getWalletNodes() == null && !event.getWallet().hasTransactions()) {
                statusUpdated(new StatusEvent(LOADING_TRANSACTIONS_MESSAGE, 120));
                if(statusTimeline == null || statusTimeline.getStatus() != Animation.Status.RUNNING) {
                    statusBar.setProgress(-1);
                    emptyLoadingWallets.add(event.getWallet());
                }
            }
            loadingWallets.add(event.getWallet());
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
            emptyLoadingWallets.remove(event.getWallet());
            loadingWallets.remove(event.getWallet());
            tabLabelStopAnimation(event.getWallet());
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
    public void cormorantSyncStatus(CormorantSyncStatusEvent event) {
        serverToggle.setDisable(false);
        if((AppServices.isConnecting() || AppServices.isConnected()) && !event.isCompleted()) {
            statusUpdated(new StatusEvent("Syncing... (" + event.getProgress() + "% complete, synced to " + event.getTipAsString() + ")"));
            if(event.getProgress() > 0 && (statusTimeline == null || statusTimeline.getStatus() != Animation.Status.RUNNING)) {
                statusBar.setProgress((double)event.getProgress() / 100);
            }
        }
    }

    @Subscribe
    public void cormorantScanStatus(CormorantScanStatusEvent event) {
        serverToggle.setDisable(true);
        if((AppServices.isConnecting() || AppServices.isConnected()) && !event.isCompleted()) {
            statusUpdated(new StatusEvent("Scanning... (" + event.getProgress() + "% complete" + (event.getRemainingAsString().isEmpty() ? ")" : ", " + event.getRemainingAsString() + " remaining)")));
            if(event.getProgress() > 0 && (statusTimeline == null || statusTimeline.getStatus() != Animation.Status.RUNNING)) {
                statusBar.setProgress((double)event.getProgress() / 100);
            }
        } else if(event.isCompleted()) {
            serverToggle.setDisable(false);
            statusBar.setProgress(0);
            if(statusBar.getText().startsWith("Scanning...")) {
                statusBar.setText("");
            }
        }
    }

    @Subscribe
    public void cormorantPruneStatus(CormorantPruneStatusEvent event) {
        if(event.legacyWalletExists()) {
            Optional<ButtonType> optButtonType = AppServices.showErrorDialog("Error importing Bitcoin Core descriptor wallet",
                    "The connected node is pruned at " + event.getPruneDateAsString() + ", but the wallet birthday for " + event.getWallet().getFullDisplayName() + " is set to " + event.getScanDateAsString() + ".\n\n" +
                            "Do you want to try using the existing legacy Bitcoin Core wallet?", ButtonType.YES, ButtonType.NO);
            if(optButtonType.isPresent() && optButtonType.get() == ButtonType.YES) {
                Config.get().setUseLegacyCoreWallet(true);
                onlineProperty().set(false);
                Platform.runLater(() -> onlineProperty().set(true));
            }
        } else {
            AppServices.showErrorDialog("Error importing Bitcoin Core descriptor wallet",
                    "The connected node is pruned at " + event.getPruneDateAsString() + ", but the wallet birthday for " + event.getWallet().getFullDisplayName() + " is set to " + event.getScanDateAsString() + ".");
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
        setTorIcon();
    }

    @Subscribe
    public void torFailedStatus(TorFailedStatusEvent event) {
        serverToggle.setDisable(false);
        statusUpdated(new StatusEvent(event.getStatus()));
        setTorIcon();
    }

    @Subscribe
    public void torReadyStatus(TorReadyStatusEvent event) {
        serverToggle.setDisable(false);
        statusUpdated(new StatusEvent(event.getStatus()));
        setTorIcon();
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
                if(tab.getUserData() instanceof WalletTabData) {
                    TabPane subTabs = (TabPane)tab.getContent();
                    for(Tab subTab : subTabs.getTabs()) {
                        WalletTabData walletTabData = (WalletTabData)subTab.getUserData();
                        if(event.getStorage().getWalletId(event.getWallet()).equals(walletTabData.getWalletForm().getWalletId())) {
                            tabs.getSelectionModel().select(tab);
                            subTabs.getSelectionModel().select(subTab);
                            return;
                        }
                    }
                }
            }

            for(Tab tab : tabs.getTabs()) {
                if(tab.getUserData() instanceof WalletTabData) {
                    TabPane subTabs = (TabPane)tab.getContent();
                    for(Tab subTab : subTabs.getTabs()) {
                        WalletTabData walletTabData = (WalletTabData)subTab.getUserData();
                        if(event.getStorage().getWalletFile().equals(walletTabData.getStorage().getWalletFile())) {
                            tabs.getSelectionModel().select(tab);
                            subTabs.getSelectionModel().select(subTab);
                            return;
                        }
                    }
                }
            }
        }
    }

    @Subscribe
    public void viewTransaction(ViewTransactionEvent event) {
        if(tabs.getScene().getWindow().equals(event.getWindow())) {
            if(event.getBlockTransaction() != null) {
                addTransactionTab(event.getBlockTransaction(), event.getInitialView(), event.getInitialIndex());
            } else {
                addTransactionTab(event.getTransaction(), event.getInitialView(), event.getInitialIndex());
            }
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
        bitcoinUnit.getToggles().forEach(toggle -> {
            RadioMenuItem menuItem = (RadioMenuItem)toggle;
            if(List.of(BitcoinUnit.AUTO, BitcoinUnit.SATOSHIS).contains(event.getBitcoinUnit()) && BitcoinUnit.BTC.equals(toggle.getUserData()) || (event.getBitcoinUnit() == BitcoinUnit.BTC && BitcoinUnit.SATOSHIS.equals(toggle.getUserData()))) {
                menuItem.setAccelerator(new KeyCodeCombination(KeyCode.B, KeyCombination.SHORTCUT_DOWN));
            } else {
                menuItem.setAccelerator(null);
            }
        });
    }

    @Subscribe
    public void unitFormatChanged(UnitFormatChangedEvent event) {
        Optional<Toggle> selectedToggle = unitFormat.getToggles().stream().filter(toggle -> event.getUnitFormat().equals(toggle.getUserData())).findFirst();
        selectedToggle.ifPresent(toggle -> unitFormat.selectToggle(toggle));
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
            if(event.getFile() != null) {
                Optional<Tab> optExisting = tabs.getTabs().stream().filter(tab -> tab.getUserData() instanceof WalletTabData walletTabData && walletTabData.getStorage().getWalletFile().equals(event.getFile())).findFirst();
                optExisting.ifPresent(tab -> tabs.getTabs().remove(tab));
                openWalletFile(event.getFile(), true);
            } else {
                openWallet(true);
            }
        }
    }

    @Subscribe
    public void requestTransactionOpen(RequestTransactionOpenEvent event) {
        if(tabs.getScene().getWindow().equals(event.getWindow())) {
            if(event.getFile() != null) {
                openTransactionFile(event.getFile());
            } else {
                openTransactionFromFile(null);
            }
        }
    }

    @Subscribe
    public void requestQRScan(RequestQRScanEvent event) {
        if(tabs.getScene().getWindow().equals(event.getWindow())) {
            openTransactionFromQR(null);
        }
    }

    @Subscribe
    public void requestVerifyDownloadOpen(RequestVerifyDownloadEvent event) {
        if(tabs.getScene().getWindow().equals(event.getWindow())) {
            verifyDownload(new ActionEvent(event.getFile(), rootStack));
        }
    }

    @Subscribe
    public void requestSendToMany(RequestSendToManyEvent event) {
        sendToMany(event.getPayments());
    }

    @Subscribe
    public void functionAction(FunctionActionEvent event) {
        selectTab(event.getWallet());
    }

    @Subscribe
    public void childWalletsAdded(ChildWalletsAddedEvent event) {
        Storage storage = AppServices.get().getOpenWallets().get(event.getWallet());
        if(storage == null) {
            throw new IllegalStateException("Cannot find storage for master wallet");
        }

        for(Wallet childWallet : event.getChildWallets()) {
            addWalletTab(storage, childWallet);
        }
    }

    @Subscribe
    public void walletLock(WalletLockEvent event) {
        WalletForm selectedWalletForm = getSelectedWalletForm();
        if(selectedWalletForm != null && selectedWalletForm.getMasterWallet().equals(event.getWallet())) {
            lockWallet.setDisable(true);
            exportWallet.setDisable(true);
        }

        lockAllWallets.setDisable(allWalletsLocked(event.getWallet()));
    }

    @Subscribe
    public void walletUnlock(WalletUnlockEvent event) {
        WalletForm selectedWalletForm = getSelectedWalletForm();
        if(selectedWalletForm != null && selectedWalletForm.getMasterWallet().equals(event.getWallet())) {
            lockWallet.setDisable(false);
            exportWallet.setDisable(!event.getWallet().isValid());
            lockAllWallets.setDisable(false);
        }
    }

    @Subscribe
    public void walletConfigChanged(WalletConfigChangedEvent event) {
        for(Tab tab : tabs.getTabs()) {
            if(tab.getUserData() instanceof WalletTabData walletTabData) {
                if(walletTabData.getWallet() == event.getWallet()) {
                    Label tabLabel = (Label)tab.getGraphic();
                    WalletIcon walletIcon = (WalletIcon)tabLabel.getGraphic();
                    walletIcon.refresh();
                }
            }
        }
    }

    @Subscribe
    public void webcamResolutionChanged(WebcamResolutionChangedEvent event) {
        useHdCameraResolutionProperty.set(event.getResolution().isWidescreenAspect());
    }

    @Subscribe
    public void webcamMirroredChanged(WebcamMirroredChangedEvent event) {
        mirrorCameraImageProperty.set(event.isMirrored());
    }
}
