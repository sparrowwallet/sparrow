package com.sparrowwallet.sparrow;

import com.google.common.eventbus.Subscribe;
import com.sparrowwallet.drongo.address.Address;
import com.sparrowwallet.drongo.protocol.Transaction;
import com.sparrowwallet.drongo.psbt.PSBT;
import com.sparrowwallet.drongo.uri.BitcoinURI;
import com.sparrowwallet.drongo.wallet.KeystoreSource;
import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.sparrow.event.*;
import com.sparrowwallet.sparrow.io.Config;
import com.sparrowwallet.sparrow.io.Device;
import com.sparrowwallet.sparrow.io.Hwi;
import com.sparrowwallet.sparrow.io.Storage;
import com.sparrowwallet.sparrow.net.*;
import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.concurrent.ScheduledService;
import javafx.concurrent.Worker;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.image.Image;
import javafx.scene.text.Font;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.util.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

public class AppServices {
    private static final Logger log = LoggerFactory.getLogger(AppServices.class);

    private static final int SERVER_PING_PERIOD = 1 * 60 * 1000;
    private static final int ENUMERATE_HW_PERIOD = 30 * 1000;
    private static final int RATES_PERIOD = 5 * 60 * 1000;
    private static final int VERSION_CHECK_PERIOD_HOURS = 24;
    private static final ExchangeSource DEFAULT_EXCHANGE_SOURCE = ExchangeSource.COINGECKO;
    private static final Currency DEFAULT_FIAT_CURRENCY = Currency.getInstance("USD");

    private static AppServices INSTANCE;

    private final MainApp application;

    private final Map<Window, List<WalletTabData>> walletWindows = new LinkedHashMap<>();

    private static final BooleanProperty onlineProperty = new SimpleBooleanProperty(false);

    private ExchangeSource.RatesService ratesService;

    private ElectrumServer.ConnectionService connectionService;

    private Hwi.ScheduledEnumerateService deviceEnumerateService;

    private VersionCheckService versionCheckService;

    private static Integer currentBlockHeight;

    private static Map<Integer, Double> targetBlockFeeRates;

    private static final Map<Date, Set<MempoolRateSize>> mempoolHistogram = new TreeMap<>();

    private static Double minimumRelayFeeRate;

    private static CurrencyRate fiatCurrencyExchangeRate;

    private static List<Device> devices;

    private static final Map<Address, BitcoinURI> payjoinURIs = new HashMap<>();

    private final ChangeListener<Boolean> onlineServicesListener = new ChangeListener<>() {
        @Override
        public void changed(ObservableValue<? extends Boolean> observable, Boolean oldValue, Boolean online) {
            if(online) {
                restartService(connectionService);

                if(ratesService.getExchangeSource() != ExchangeSource.NONE) {
                    restartService(ratesService);
                }

                if(Config.get().isCheckNewVersions()) {
                    restartService(versionCheckService);
                }
            } else {
                connectionService.cancel();
                ratesService.cancel();
                versionCheckService.cancel();
            }
        }
    };

    private void restartService(ScheduledService<?> service) {
        if(service.isRunning()) {
            service.cancel();
        }

        if(service.getState() == Worker.State.CANCELLED || service.getState() == Worker.State.FAILED) {
            service.reset();
        }

        if(!service.isRunning()) {
            service.start();
        }
    }

    public AppServices(MainApp application) {
        this.application = application;
        EventManager.get().register(this);
    }

    public void start() {
        Config config = Config.get();
        connectionService = createConnectionService();
        if(config.getMode() == Mode.ONLINE && config.getServerAddress() != null && !config.getServerAddress().isEmpty()) {
            connectionService.start();
        }

        ExchangeSource source = config.getExchangeSource() != null ? config.getExchangeSource() : DEFAULT_EXCHANGE_SOURCE;
        Currency currency = config.getFiatCurrency() != null ? config.getFiatCurrency() : DEFAULT_FIAT_CURRENCY;
        ratesService = createRatesService(source, currency);
        if(config.getMode() == Mode.ONLINE && source != ExchangeSource.NONE) {
            ratesService.start();
        }

        versionCheckService = createVersionCheckService();
        if(config.getMode() == Mode.ONLINE && config.isCheckNewVersions()) {
            versionCheckService.start();
        }

        onlineProperty.addListener(onlineServicesListener);
    }

    public void stop() {
        if(connectionService != null) {
            connectionService.cancel();
        }

        if(ratesService != null) {
            ratesService.cancel();
        }

        if(versionCheckService != null) {
            versionCheckService.cancel();
        }
    }

    private ElectrumServer.ConnectionService createConnectionService() {
        ElectrumServer.ConnectionService connectionService = new ElectrumServer.ConnectionService();
        connectionService.setPeriod(new Duration(SERVER_PING_PERIOD));
        connectionService.setRestartOnFailure(true);

        EventManager.get().register(connectionService);
        connectionService.statusProperty().addListener((observable, oldValue, newValue) -> {
            if(connectionService.isRunning()) {
                EventManager.get().post(new StatusEvent(newValue));
            }
        });

        connectionService.setOnSucceeded(successEvent -> {
            connectionService.setRestartOnFailure(true);

            onlineProperty.removeListener(onlineServicesListener);
            onlineProperty.setValue(true);
            onlineProperty.addListener(onlineServicesListener);

            if(connectionService.getValue() != null) {
                EventManager.get().post(connectionService.getValue());
            }
        });
        connectionService.setOnFailed(failEvent -> {
            //Close connection here to create a new transport next time we try
            connectionService.resetConnection();

            if(failEvent.getSource().getException() instanceof ServerConfigException) {
                connectionService.setRestartOnFailure(false);
            }

            onlineProperty.removeListener(onlineServicesListener);
            onlineProperty.setValue(false);
            onlineProperty.addListener(onlineServicesListener);

            log.debug("Connection failed", failEvent.getSource().getException());
            EventManager.get().post(new ConnectionFailedEvent(failEvent.getSource().getException()));
        });

        return connectionService;
    }

    private ExchangeSource.RatesService createRatesService(ExchangeSource exchangeSource, Currency currency) {
        ExchangeSource.RatesService ratesService = new ExchangeSource.RatesService(exchangeSource, currency);
        ratesService.setPeriod(new Duration(RATES_PERIOD));
        ratesService.setRestartOnFailure(true);

        ratesService.setOnSucceeded(successEvent -> {
            EventManager.get().post(ratesService.getValue());
        });

        return ratesService;
    }

    private VersionCheckService createVersionCheckService() {
        VersionCheckService versionCheckService = new VersionCheckService();
        versionCheckService.setDelay(Duration.seconds(10));
        versionCheckService.setPeriod(Duration.hours(VERSION_CHECK_PERIOD_HOURS));
        versionCheckService.setRestartOnFailure(true);

        versionCheckService.setOnSucceeded(successEvent -> {
            VersionUpdatedEvent event = versionCheckService.getValue();
            if(event != null) {
                EventManager.get().post(event);
            }
        });

        return versionCheckService;
    }

    private Hwi.ScheduledEnumerateService createDeviceEnumerateService() {
        Hwi.ScheduledEnumerateService enumerateService = new Hwi.ScheduledEnumerateService(null);
        enumerateService.setPeriod(new Duration(ENUMERATE_HW_PERIOD));
        enumerateService.setOnSucceeded(workerStateEvent -> {
            List<Device> devices = enumerateService.getValue();

            //Null devices are returned if the app is currently prompting for a pin. Otherwise, the enumerate clears the pin screen
            if(devices != null) {
                //If another instance of HWI is currently accessing the usb interface, HWI returns empty device models. Ignore this run if that happens
                List<Device> validDevices = devices.stream().filter(device -> device.getModel() != null).collect(Collectors.toList());
                if(validDevices.size() == devices.size()) {
                    Platform.runLater(() -> EventManager.get().post(new UsbDeviceEvent(devices)));
                }
            }
        });

        return enumerateService;
    }

    static void initialize(MainApp application) {
        INSTANCE = new AppServices(application);
    }

    public static AppServices get() {
        return INSTANCE;
    }

    public static AppController newAppWindow(Stage stage) {
        try {
            FXMLLoader appLoader = new FXMLLoader(AppServices.class.getResource("app.fxml"));
            Parent root = appLoader.load();
            AppController appController = appLoader.getController();

            Scene scene = new Scene(root);
            scene.getStylesheets().add(AppServices.class.getResource("app.css").toExternalForm());

            stage.setTitle("Sparrow");
            stage.setMinWidth(650);
            stage.setMinHeight(800);
            stage.setScene(scene);
            stage.getIcons().add(new Image(MainApp.class.getResourceAsStream("/image/sparrow.png")));

            appController.initializeView();
            stage.show();
            return appController;
        } catch(IOException e) {
            log.error("Could not load app FXML", e);
            throw new IllegalStateException(e);
        }
    }

    public MainApp getApplication() {
        return application;
    }

    public Map<Wallet, Storage> getOpenWallets() {
        Map<Wallet, Storage> openWallets = new LinkedHashMap<>();
        for(List<WalletTabData> walletTabDataList : walletWindows.values()) {
            for(WalletTabData walletTabData : walletTabDataList) {
                openWallets.put(walletTabData.getWallet(), walletTabData.getStorage());
            }
        }

        return openWallets;
    }

    public Window getWindowForWallet(Storage storage) {
        Optional<Window> optWindow = walletWindows.entrySet().stream().filter(entry -> entry.getValue().stream().anyMatch(walletTabData -> walletTabData.getStorage().getWalletFile().equals(storage.getWalletFile()))).map(Map.Entry::getKey).findFirst();
        return optWindow.orElse(null);
    }

    public Window getWindowForPSBT(PSBT psbt) {
        Optional<Window> optWindow = walletWindows.entrySet().stream().filter(entry -> entry.getValue().stream().anyMatch(walletTabData -> walletTabData.getWallet().canSign(psbt))).map(Map.Entry::getKey).findFirst();
        return optWindow.orElse(null);
    }

    public double getWalletWindowMaxX() {
        return walletWindows.keySet().stream().mapToDouble(Window::getX).max().orElse(0d);
    }

    public static boolean isConnecting() {
        return get().connectionService != null && get().connectionService.isConnecting();
    }

    public static boolean isConnected() {
        return onlineProperty.get() && get().connectionService != null && get().connectionService.isConnected();
    }

    public static BooleanProperty onlineProperty() {
        return onlineProperty;
    }

    public static Integer getCurrentBlockHeight() {
        return currentBlockHeight;
    }

    public static Map<Integer, Double> getTargetBlockFeeRates() {
        return targetBlockFeeRates;
    }

    public static Map<Date, Set<MempoolRateSize>> getMempoolHistogram() {
        return mempoolHistogram;
    }

    private void addMempoolRateSizes(Set<MempoolRateSize> rateSizes) {
        LocalDateTime dateMinute = LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES);
        if(mempoolHistogram.isEmpty()) {
            mempoolHistogram.put(Date.from(dateMinute.minusMinutes(1).atZone(ZoneId.systemDefault()).toInstant()), rateSizes);
        }

        mempoolHistogram.put(Date.from(dateMinute.atZone(ZoneId.systemDefault()).toInstant()), rateSizes);
    }

    public static Double getMinimumRelayFeeRate() {
        return minimumRelayFeeRate == null ? Transaction.DEFAULT_MIN_RELAY_FEE : minimumRelayFeeRate;
    }

    public static CurrencyRate getFiatCurrencyExchangeRate() {
        return fiatCurrencyExchangeRate;
    }

    public static List<Device> getDevices() {
        return devices == null ? new ArrayList<>() : devices;
    }

    public static BitcoinURI getPayjoinURI(Address address) {
        return payjoinURIs.get(address);
    }

    public static void addPayjoinURI(BitcoinURI bitcoinURI) {
        if(bitcoinURI.getPayjoinUrl() == null) {
            throw new IllegalArgumentException("Not a payjoin URI");
        }
        payjoinURIs.put(bitcoinURI.getAddress(), bitcoinURI);
    }

    public static Optional<ButtonType> showWarningDialog(String title, String content, ButtonType... buttons) {
        return showAlertDialog(title, content, Alert.AlertType.WARNING, buttons);
    }

    public static Optional<ButtonType> showErrorDialog(String title, String content, ButtonType... buttons) {
        return showAlertDialog(title, content, Alert.AlertType.ERROR, buttons);
    }

    public static Optional<ButtonType> showAlertDialog(String title, String content, Alert.AlertType alertType, ButtonType... buttons) {
        Alert alert = new Alert(alertType, content, buttons);
        setStageIcon(alert.getDialogPane().getScene().getWindow());
        alert.getDialogPane().getScene().getStylesheets().add(AppServices.class.getResource("general.css").toExternalForm());
        alert.setTitle(title);
        alert.setHeaderText(title);
        return alert.showAndWait();
    }

    public static void setStageIcon(Window window) {
        Stage stage = (Stage)window;
        stage.getIcons().add(new Image(AppServices.class.getResourceAsStream("/image/sparrow.png")));

        if(stage.getScene() != null && Config.get().getTheme() == Theme.DARK) {
            stage.getScene().getStylesheets().add(AppServices.class.getResource("darktheme.css").toExternalForm());
        }
    }

    public static Font getMonospaceFont() {
        return Font.font("Roboto Mono", 13);
    }

    @Subscribe
    public void newConnection(ConnectionEvent event) {
        currentBlockHeight = event.getBlockHeight();
        targetBlockFeeRates = event.getTargetBlockFeeRates();
        addMempoolRateSizes(event.getMempoolRateSizes());
        minimumRelayFeeRate = event.getMinimumRelayFeeRate();
        String banner = event.getServerBanner();
        String status = "Connected to " + Config.get().getServerAddress() + " at height " + event.getBlockHeight();
        EventManager.get().post(new StatusEvent(status));
    }

    @Subscribe
    public void connectionFailed(ConnectionFailedEvent event) {
        String reason = event.getException().getCause() != null ? event.getException().getCause().getMessage() : event.getException().getMessage();
        String status = "Connection error: " + reason;
        EventManager.get().post(new StatusEvent(status));
    }

    @Subscribe
    public void usbDevicesFound(UsbDeviceEvent event) {
        devices = Collections.unmodifiableList(event.getDevices());
    }

    @Subscribe
    public void newBlock(NewBlockEvent event) {
        currentBlockHeight = event.getHeight();
        String status = "Updating to new block height " + event.getHeight();
        EventManager.get().post(new StatusEvent(status));
    }

    @Subscribe
    public void feesUpdated(FeeRatesUpdatedEvent event) {
        targetBlockFeeRates = event.getTargetBlockFeeRates();
        addMempoolRateSizes(event.getMempoolRateSizes());
    }

    @Subscribe
    public void feeRateSourceChanged(FeeRatesSourceChangedEvent event) {
        ElectrumServer.FeeRatesService feeRatesService = new ElectrumServer.FeeRatesService();
        feeRatesService.setOnSucceeded(workerStateEvent -> {
            EventManager.get().post(feeRatesService.getValue());
        });
        //Perform once-off fee rates retrieval to immediately change displayed rates
        feeRatesService.start();
    }

    @Subscribe
    public void fiatCurrencySelected(FiatCurrencySelectedEvent event) {
        if(ratesService != null) {
            ratesService.cancel();

            if(Config.get().getMode() != Mode.OFFLINE && event.getExchangeSource() != ExchangeSource.NONE) {
                ratesService = createRatesService(event.getExchangeSource(), event.getCurrency());
                ratesService.start();
            }
        }
    }

    @Subscribe
    public void exchangeRatesUpdated(ExchangeRatesUpdatedEvent event) {
        fiatCurrencyExchangeRate = event.getCurrencyRate();
    }

    @Subscribe
    public void versionCheckStatus(VersionCheckStatusEvent event) {
        versionCheckService.cancel();

        if(Config.get().getMode() != Mode.OFFLINE && event.isEnabled()) {
            versionCheckService = createVersionCheckService();
            versionCheckService.start();
        }
    }

    @Subscribe
    public void openWallets(OpenWalletsEvent event) {
        if(event.getWalletTabDataList().isEmpty()) {
            walletWindows.remove(event.getWindow());
        } else {
            walletWindows.put(event.getWindow(), event.getWalletTabDataList());
        }

        List<WalletTabData> allWallets = walletWindows.values().stream().flatMap(Collection::stream).collect(Collectors.toList());

        Platform.runLater(() -> {
            if(!Window.getWindows().isEmpty()) {
                List<File> walletFiles = allWallets.stream().map(walletTabData -> walletTabData.getStorage().getWalletFile()).collect(Collectors.toList());
                Config.get().setRecentWalletFiles(walletFiles);
            }
        });

        boolean usbWallet = false;
        for(WalletTabData walletTabData : allWallets) {
            Wallet wallet = walletTabData.getWallet();
            Storage storage = walletTabData.getStorage();

            if(!storage.getWalletFile().exists() || wallet.containsSource(KeystoreSource.HW_USB)) {
                usbWallet = true;

                if(deviceEnumerateService == null) {
                    deviceEnumerateService = createDeviceEnumerateService();
                }

                if(deviceEnumerateService.getState() == Worker.State.CANCELLED) {
                    deviceEnumerateService.reset();
                }

                if(!deviceEnumerateService.isRunning()) {
                    deviceEnumerateService.start();
                }

                break;
            }
        }

        if(!usbWallet && deviceEnumerateService != null && deviceEnumerateService.isRunning()) {
            deviceEnumerateService.cancel();
            EventManager.get().post(new UsbDeviceEvent(Collections.emptyList()));
        }
    }

    @Subscribe
    public void requestConnect(RequestConnectEvent event) {
        onlineProperty.set(true);
    }

    @Subscribe
    public void requestDisconnect(RequestDisconnectEvent event) {
        onlineProperty.set(false);
    }

    @Subscribe
    public void walletSettingsChanged(WalletSettingsChangedEvent event) {
        restartBwt(event.getWallet());
    }

    @Subscribe
    public void walletOpening(WalletOpeningEvent event) {
        restartBwt(event.getWallet());
    }

    private void restartBwt(Wallet wallet) {
        if(Config.get().getServerType() == ServerType.BITCOIN_CORE && isConnected() && wallet.isValid()) {
            connectionService.cancel();
        }
    }

    @Subscribe
    public void bwtShutdown(BwtShutdownEvent event) {
        if(onlineProperty().get() && !connectionService.isRunning()) {
            connectionService.reset();
            connectionService.start();
        }
    }
}
