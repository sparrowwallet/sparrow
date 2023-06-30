package com.sparrowwallet.sparrow;

import com.google.common.eventbus.Subscribe;
import com.google.common.net.HostAndPort;
import com.sparrowwallet.drongo.Network;
import com.sparrowwallet.drongo.SecureString;
import com.sparrowwallet.drongo.address.Address;
import com.sparrowwallet.drongo.bip47.PaymentCode;
import com.sparrowwallet.drongo.crypto.ECKey;
import com.sparrowwallet.drongo.crypto.EncryptionType;
import com.sparrowwallet.drongo.crypto.InvalidPasswordException;
import com.sparrowwallet.drongo.crypto.Key;
import com.sparrowwallet.drongo.policy.PolicyType;
import com.sparrowwallet.drongo.wallet.*;
import com.sparrowwallet.sparrow.control.WalletPasswordDialog;
import com.sparrowwallet.sparrow.glyphfont.FontAwesome5;
import com.sparrowwallet.sparrow.net.Auth47;
import com.sparrowwallet.drongo.protocol.BlockHeader;
import com.sparrowwallet.drongo.protocol.ScriptType;
import com.sparrowwallet.drongo.protocol.Transaction;
import com.sparrowwallet.drongo.psbt.PSBT;
import com.sparrowwallet.drongo.uri.BitcoinURI;
import com.sparrowwallet.sparrow.control.TrayManager;
import com.sparrowwallet.sparrow.event.*;
import com.sparrowwallet.sparrow.io.*;
import com.sparrowwallet.sparrow.net.*;
import com.sparrowwallet.sparrow.paynym.PayNymService;
import com.sparrowwallet.sparrow.soroban.SorobanServices;
import com.sparrowwallet.sparrow.whirlpool.WhirlpoolServices;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.concurrent.ScheduledService;
import javafx.concurrent.Task;
import javafx.concurrent.Worker;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.Dialog;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.text.Font;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.util.Duration;
import org.controlsfx.glyphfont.Glyph;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.awt.desktop.OpenFilesHandler;
import java.awt.desktop.OpenURIHandler;
import java.awt.event.KeyEvent;
import java.io.File;
import java.io.IOException;
import java.net.*;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

public class AppServices {
    private static final Logger log = LoggerFactory.getLogger(AppServices.class);

    private static final int SERVER_PING_PERIOD_SECS = 60;
    private static final int PUBLIC_SERVER_RETRY_PERIOD_SECS = 3;
    private static final int PRIVATE_SERVER_RETRY_PERIOD_SECS = 15;
    public static final int ENUMERATE_HW_PERIOD_SECS = 30;
    private static final int RATES_PERIOD_SECS = 5 * 60;
    private static final int VERSION_CHECK_PERIOD_HOURS = 24;
    private static final int CONNECTION_DELAY_SECS = 2;
    private static final ExchangeSource DEFAULT_EXCHANGE_SOURCE = ExchangeSource.COINGECKO;
    private static final Currency DEFAULT_FIAT_CURRENCY = Currency.getInstance("USD");
    private static final String TOR_DEFAULT_PROXY_CIRCUIT_ID = "default";

    public static final List<Integer> TARGET_BLOCKS_RANGE = List.of(1, 2, 3, 4, 5, 10, 25, 50);
    public static final List<Long> FEE_RATES_RANGE = List.of(1L, 2L, 4L, 8L, 16L, 32L, 64L, 128L, 256L, 512L, 1024L);
    public static final double FALLBACK_FEE_RATE = 20000d / 1000;

    private static AppServices INSTANCE;

    private final WhirlpoolServices whirlpoolServices = new WhirlpoolServices();

    private final SorobanServices sorobanServices = new SorobanServices();

    private InteractionServices interactionServices;

    private static PayNymService payNymService;

    private final Application application;

    private final Map<Window, List<WalletTabData>> walletWindows = new LinkedHashMap<>();

    private TrayManager trayManager;

    private static Image windowIcon;

    private static final BooleanProperty onlineProperty = new SimpleBooleanProperty(false);

    private ExchangeSource.RatesService ratesService;

    private ElectrumServer.ConnectionService connectionService;

    private Hwi.ScheduledEnumerateService deviceEnumerateService;

    private VersionCheckService versionCheckService;

    private TorService torService;

    private ScheduledService<Void> preventSleepService;

    private static Integer currentBlockHeight;

    private static BlockHeader latestBlockHeader;

    private static Map<Integer, Double> targetBlockFeeRates;

    private static final TreeMap<Date, Set<MempoolRateSize>> mempoolHistogram = new TreeMap<>();

    private static Double minimumRelayFeeRate;

    private static CurrencyRate fiatCurrencyExchangeRate;

    private static List<Device> devices;

    private static final List<File> argFiles = new ArrayList<>();

    private static final List<URI> argUris = new ArrayList<>();

    private static final Map<Address, BitcoinURI> payjoinURIs = new HashMap<>();

    private final ChangeListener<Boolean> onlineServicesListener = new ChangeListener<>() {
        @Override
        public void changed(ObservableValue<? extends Boolean> observable, Boolean oldValue, Boolean online) {
            if(online) {
                if(Config.get().requiresInternalTor() && !isTorRunning()) {
                    if(torService.getState() == Worker.State.SCHEDULED) {
                        torService.cancel();
                        torService.reset();
                    }

                    if(torService.getState() != Worker.State.RUNNING) {
                        torService.start();
                    }
                } else {
                    restartServices();
                }
            } else {
                connectionService.cancel();
                ratesService.cancel();
                versionCheckService.cancel();
            }
        }
    };

    private static final OpenURIHandler openURIHandler = event -> {
        openURI(event.getURI());
    };

    private static final OpenFilesHandler openFilesHandler = event -> {
        openFiles(event.getFiles(), null);
    };

    private AppServices(Application application, InteractionServices interactionServices) {
        this.application = application;
        this.interactionServices = interactionServices;
        EventManager.get().register(this);
        EventManager.get().register(whirlpoolServices);
        EventManager.get().register(sorobanServices);
    }

    public void start() {
        Config config = Config.get();
        connectionService = createConnectionService();
        ratesService = createRatesService(config.getExchangeSource(), config.getFiatCurrency());
        versionCheckService = createVersionCheckService();
        torService = createTorService();
        preventSleepService = createPreventSleepService();

        onlineProperty.addListener(onlineServicesListener);

        if(config.getMode() == Mode.ONLINE) {
            if(config.requiresInternalTor()) {
                torService.start();
            } else {
                restartServices();
            }
        }

        addURIHandlers();
    }

    private void restartServices() {
        Config config = Config.get();
        if(config.hasServer()) {
            restartService(connectionService);
        }

        if(config.isFetchRates()) {
            restartService(ratesService);
        }

        if(config.isCheckNewVersions() && Network.get() == Network.MAINNET && Interface.get() == Interface.DESKTOP) {
            restartService(versionCheckService);
        }

        if(config.isPreventSleep()) {
            restartService(preventSleepService);
        }
    }

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

        if(payNymService != null) {
            PayNymService.ShutdownService shutdownService = new PayNymService.ShutdownService(payNymService);
            shutdownService.start();
        }

        if(Tor.getDefault() != null) {
            Tor.getDefault().getTorManager().destroy(true, success -> {});
        }
    }

    private ElectrumServer.ConnectionService createConnectionService() {
        ElectrumServer.ConnectionService connectionService = new ElectrumServer.ConnectionService();
        //Delay startup on first connection to Bitcoin Core to allow any unencrypted wallets to open first
        connectionService.setDelay(Config.get().getServerType() == ServerType.BITCOIN_CORE ? Duration.seconds(CONNECTION_DELAY_SECS) : Duration.ZERO);
        connectionService.setPeriod(Duration.seconds(SERVER_PING_PERIOD_SECS));
        connectionService.setRestartOnFailure(true);
        EventManager.get().register(connectionService);

        connectionService.setOnRunning(workerStateEvent -> {
            connectionService.setDelay(Duration.ZERO);
            if(!ElectrumServer.isConnected()) {
                EventManager.get().post(new ConnectionStartEvent(Config.get().getServerDisplayName()));
            }
        });
        connectionService.setOnSucceeded(successEvent -> {
            connectionService.setPeriod(Duration.seconds(SERVER_PING_PERIOD_SECS));
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
            connectionService.closeConnection();

            if(failEvent.getSource().getException() instanceof ServerConfigException) {
                connectionService.setRestartOnFailure(false);
            }

            if(failEvent.getSource().getException() instanceof TlsServerException tlsServerException && failEvent.getSource().getException().getCause() != null) {
                connectionService.setRestartOnFailure(false);
                if(tlsServerException.getCause().getMessage().contains("PKIX path building failed")) {
                    File crtFile = Config.get().getElectrumServerCert();
                    if(crtFile != null && Config.get().getServerType() == ServerType.ELECTRUM_SERVER) {
                        AppServices.showErrorDialog("SSL Handshake Failed", "The configured server certificate at " + crtFile.getAbsolutePath() + " did not match the certificate provided by the server at " + tlsServerException.getServer().getHost() + "." +
                                "\n\nThis may be simply due to a certificate renewal, or it may indicate a man-in-the-middle attack." +
                                "\n\nChange the configured server certificate if you would like to proceed.");
                    } else {
                        crtFile = Storage.getCertificateFile(tlsServerException.getServer().getHost());
                        if(crtFile != null) {
                            Optional<ButtonType> optButton = AppServices.showErrorDialog("SSL Handshake Failed", "The certificate provided by the server at " + tlsServerException.getServer().getHost() + " appears to have changed." +
                                    "\n\nThis may be simply due to a certificate renewal, or it may indicate a man-in-the-middle attack." +
                                    "\n\nDo you still want to proceed?", ButtonType.NO, ButtonType.YES);
                            if(optButton.isPresent() && optButton.get() == ButtonType.YES) {
                                if(crtFile.delete()) {
                                    Platform.runLater(() -> restartService(connectionService));
                                    return;
                                } else {
                                    AppServices.showErrorDialog("Could not delete certificate", "The certificate file at " + crtFile.getAbsolutePath() + " could not be deleted.\n\nPlease delete this file manually.");
                                }
                            }
                        }
                    }
                } else if(tlsServerException.getCause().getCause() instanceof UnknownCertificateExpiredException expiredException) {
                    Optional<ButtonType> optButton = AppServices.showErrorDialog("SSL Handshake Failed", "The certificate provided by the server at " + tlsServerException.getServer().getHost() + " has expired. "
                            + tlsServerException.getMessage() + "." +
                            "\n\nDo you still want to proceed?", ButtonType.NO, ButtonType.YES);
                    if(optButton.isPresent() && optButton.get() == ButtonType.YES) {
                        Storage.saveCertificate(tlsServerException.getServer().getHost(), expiredException.getCertificate());
                        Platform.runLater(() -> restartService(connectionService));
                        return;
                    }
                }
            }

            if(failEvent.getSource().getException() instanceof ProxyServerException && Config.get().isUseProxy() && Config.get().isAutoSwitchProxy() && Config.get().requiresTor()) {
                Config.get().setUseProxy(false);
                Platform.runLater(() -> restartService(torService));
                return;
            }

            onlineProperty.removeListener(onlineServicesListener);
            onlineProperty.setValue(false);
            onlineProperty.addListener(onlineServicesListener);

            if(Config.get().getServerType() == ServerType.PUBLIC_ELECTRUM_SERVER) {
                Config.get().changePublicServer();
                connectionService.setPeriod(Duration.seconds(PUBLIC_SERVER_RETRY_PERIOD_SECS));
            } else {
                connectionService.setPeriod(Duration.seconds(PRIVATE_SERVER_RETRY_PERIOD_SECS));
            }

            log.debug("Connection failed", failEvent.getSource().getException());
            EventManager.get().post(new ConnectionFailedEvent(failEvent.getSource().getException()));
        });

        return connectionService;
    }

    private ExchangeSource.RatesService createRatesService(ExchangeSource exchangeSource, Currency currency) {
        ExchangeSource.RatesService ratesService = new ExchangeSource.RatesService(
                exchangeSource == null ? DEFAULT_EXCHANGE_SOURCE : exchangeSource,
                currency == null ? DEFAULT_FIAT_CURRENCY : currency);
        ratesService.setPeriod(Duration.seconds(RATES_PERIOD_SECS));
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
        enumerateService.setPeriod(Duration.seconds(Config.get().getEnumerateHwPeriod()));
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

    private TorService createTorService() {
        TorService torService = new TorService();
        torService.setPeriod(Duration.hours(1000));
        torService.setRestartOnFailure(true);

        torService.setOnRunning(workerStateEvent -> {
            EventManager.get().post(new TorBootStatusEvent());
        });
        torService.setOnSucceeded(workerStateEvent -> {
            Tor.setDefault(torService.getValue());
            torService.cancel();
            restartServices();
            EventManager.get().post(new TorReadyStatusEvent());
        });
        torService.setOnFailed(workerStateEvent -> {
            EventManager.get().post(new TorFailedStatusEvent(workerStateEvent.getSource().getException()));
        });

        return torService;
    }

    private ScheduledService<Void> createPreventSleepService() {
        ScheduledService<Void> preventSleepService = new ScheduledService<Void>() {
            @Override
            protected Task<Void> createTask() {
                return new Task<>() {
                    protected Void call() {
                        try {
                            Robot robot = new Robot();
                            robot.keyRelease(KeyEvent.VK_F16);
                        } catch(Exception e) {
                            log.debug("Error preventing sleep", e);
                        }

                        return null;
                    }
                };
            }
        };

        preventSleepService.setPeriod(Duration.minutes(1));
        return preventSleepService;
    }

    public void setPreventSleep(boolean preventSleep) {
        if(preventSleepService != null) {
            if(preventSleep) {
                restartService(preventSleepService);
            } else {
                preventSleepService.cancel();
            }
        }
    }

    public static boolean isTorRunning() {
        return Tor.getDefault() != null;
    }

    public static boolean isUsingProxy() {
        return isTorRunning() || Config.get().isUseProxy();
    }

    public static Proxy getProxy() {
        return getProxy(TOR_DEFAULT_PROXY_CIRCUIT_ID);
    }

    public static Proxy getProxy(String proxyCircuitId) {
        Config config = Config.get();
        Proxy proxy = null;
        if(config.isUseProxy()) {
            HostAndPort proxyHostAndPort = HostAndPort.fromString(config.getProxyServer());
            InetSocketAddress proxyAddress = new InetSocketAddress(proxyHostAndPort.getHost(), proxyHostAndPort.getPortOrDefault(ProxyTcpOverTlsTransport.DEFAULT_PROXY_PORT));
            proxy = new Proxy(Proxy.Type.SOCKS, proxyAddress);
        } else if(AppServices.isTorRunning()) {
            proxy = Tor.getDefault().getProxy();
        }

        //Setting new proxy authentication credentials will force a new Tor circuit to be created
        if(proxy != null) {
            Authenticator.setDefault(new Authenticator() {
                public PasswordAuthentication getPasswordAuthentication() {
                    return (new PasswordAuthentication("user", proxyCircuitId.toCharArray()));
                }
            });
        }

        return proxy;
    }

    public static void initialize(Application application) {
        INSTANCE = new AppServices(application, new DefaultInteractionServices());
    }

    public static void initialize(Application application, InteractionServices interactionServices) {
        INSTANCE = new AppServices(application, interactionServices);
    }

    public static AppServices get() {
        return INSTANCE;
    }

    public static WhirlpoolServices getWhirlpoolServices() {
        return get().whirlpoolServices;
    }

    public static SorobanServices getSorobanServices() {
        return get().sorobanServices;
    }

    public static InteractionServices getInteractionServices() {
        return get().interactionServices;
    }

    public static PayNymService getPayNymService() {
        if(payNymService == null) {
            HostAndPort torProxy = getTorProxy();
            payNymService = new PayNymService(torProxy);
        } else {
            HostAndPort torProxy = getTorProxy();
            if(!Objects.equals(payNymService.getTorProxy(), torProxy)) {
                payNymService.setTorProxy(getTorProxy());
            }
        }

        return payNymService;
    }

    public static HostAndPort getTorProxy() {
        return AppServices.isTorRunning() ?
                Tor.getDefault().getProxyHostAndPort() :
                (Config.get().getProxyServer() == null || Config.get().getProxyServer().isEmpty() || !Config.get().isUseProxy() ? null : HostAndPort.fromString(Config.get().getProxyServer()));
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
            stage.setMinHeight(708);
            stage.setScene(scene);
            stage.getIcons().add(getWindowIcon());

            appController.initializeView();
            stage.show();
            return appController;
        } catch(IOException e) {
            log.error("Could not load app FXML", e);
            throw new IllegalStateException(e);
        }
    }

    private static Image getWindowIcon() {
        if(windowIcon == null) {
            windowIcon = new Image(SparrowWallet.class.getResourceAsStream("/image/sparrow-icon.png"));
        }

        return windowIcon;
    }

    public static boolean isReducedWindowHeight(Node node) {
        return (node.getScene() != null && node.getScene().getWindow().getHeight() < 768);
    }

    public Application getApplication() {
        return application;
    }

    public void minimizeStage(Stage stage) {
        if(trayManager == null) {
            trayManager = new TrayManager();
        }

        trayManager.addStage(stage);
        stage.hide();
    }

    public static void onEscapePressed(Scene scene, Runnable runnable) {
        scene.setOnKeyPressed(event -> {
            if(event.getCode() == KeyCode.ESCAPE) {
                runnable.run();
            }
        });
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

    public Wallet getWallet(String walletId) {
        return getOpenWallets().entrySet().stream().filter(entry -> entry.getValue().getWalletId(entry.getKey()).equals(walletId)).map(Map.Entry::getKey).findFirst().orElse(null);
    }

    public WalletTransaction getCreatedTransaction(Set<BlockTransactionHashIndex> utxos) {
        for(List<WalletTabData> walletTabDataList : walletWindows.values()) {
            for(WalletTabData walletTabData : walletTabDataList) {
                if(walletTabData.getWalletForm().getCreatedWalletTransaction() != null && utxos.equals(walletTabData.getWalletForm().getCreatedWalletTransaction().getSelectedUtxos().keySet())) {
                    return walletTabData.getWalletForm().getCreatedWalletTransaction();
                }
            }
        }

        return null;
    }

    public Window getWindowForWallet(String walletId) {
        Optional<Window> optWindow = walletWindows.entrySet().stream().filter(entry -> entry.getValue().stream().anyMatch(walletTabData -> walletTabData.getWalletForm().getWalletId().equals(walletId))).map(Map.Entry::getKey).findFirst();
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

    public static BlockHeader getLatestBlockHeader() {
        return latestBlockHeader;
    }

    public static Double getDefaultFeeRate() {
        int defaultTarget = TARGET_BLOCKS_RANGE.get((TARGET_BLOCKS_RANGE.size() / 2) - 1);
        return getTargetBlockFeeRates() == null ? FALLBACK_FEE_RATE : getTargetBlockFeeRates().get(defaultTarget);
    }

    public static Double getMinimumFeeRate() {
        Optional<Double> optMinFeeRate = getTargetBlockFeeRates().values().stream().min(Double::compareTo);
        Double minRate = optMinFeeRate.orElse(FALLBACK_FEE_RATE);
        return Math.max(minRate, Transaction.DUST_RELAY_TX_FEE);
    }

    public static Map<Integer, Double> getTargetBlockFeeRates() {
        return targetBlockFeeRates;
    }

    public static TreeMap<Date, Set<MempoolRateSize>> getMempoolHistogram() {
        return mempoolHistogram;
    }

    private void addMempoolRateSizes(Set<MempoolRateSize> rateSizes) {
        if(rateSizes.isEmpty()) {
            return;
        }

        LocalDateTime dateMinute = LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES);
        if(mempoolHistogram.isEmpty()) {
            mempoolHistogram.put(Date.from(dateMinute.minusMinutes(1).atZone(ZoneId.systemDefault()).toInstant()), rateSizes);
        }

        mempoolHistogram.put(Date.from(dateMinute.atZone(ZoneId.systemDefault()).toInstant()), rateSizes);

        Date yesterday = Date.from(LocalDateTime.now().minusDays(1).atZone(ZoneId.systemDefault()).toInstant());
        mempoolHistogram.keySet().removeIf(date -> date.before(yesterday));

        ZonedDateTime twoHoursAgo = LocalDateTime.now().minusHours(2).atZone(ZoneId.systemDefault());
        mempoolHistogram.keySet().removeIf(date -> {
            ZonedDateTime dateTime = date.toInstant().atZone(ZoneId.systemDefault());
            return dateTime.isBefore(twoHoursAgo) && (dateTime.getMinute() % 10 != 0);
        });
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

    public static void clearPayjoinURI(Address address) {
        payjoinURIs.remove(address);
    }

    public static void clearTransactionHistoryCache(Wallet wallet) {
        ElectrumServer.clearRetrievedScriptHashes(wallet);

        for(Wallet childWallet : wallet.getChildWallets()) {
            if(childWallet.isNested()) {
                AppServices.clearTransactionHistoryCache(childWallet);
            }
        }
    }

    public static boolean isWalletFile(File file) {
        return Storage.isWalletFile(file);
    }

    public static Optional<ButtonType> showWarningDialog(String title, String content, ButtonType... buttons) {
        return showAlertDialog(title, content, Alert.AlertType.WARNING, buttons);
    }

    public static Optional<ButtonType> showErrorDialog(String title, String content, ButtonType... buttons) {
        return showAlertDialog(title, content == null ? "See log file (Help menu)" : content, Alert.AlertType.ERROR, buttons);
    }

    public static Optional<ButtonType> showSuccessDialog(String title, String content, ButtonType... buttons) {
        Glyph successGlyph = new Glyph(FontAwesome5.FONT_NAME, FontAwesome5.Glyph.CHECK_CIRCLE);
        successGlyph.getStyleClass().add("success");
        successGlyph.setFontSize(50);

        return showAlertDialog(title, content, Alert.AlertType.INFORMATION, successGlyph, buttons);
    }

    public static Optional<ButtonType> showAlertDialog(String title, String content, Alert.AlertType alertType, ButtonType... buttons) {
        return showAlertDialog(title, content, alertType, null, buttons);
    }

    public static Optional<ButtonType> showAlertDialog(String title, String content, Alert.AlertType alertType, Node graphic, ButtonType... buttons) {
        return getInteractionServices().showAlert(title, content, alertType, graphic, buttons);
    }

    public static void setStageIcon(Window window) {
        Stage stage = (Stage)window;
        stage.getIcons().add(getWindowIcon());

        if(stage.getScene() != null && Config.get().getTheme() == Theme.DARK) {
            stage.getScene().getStylesheets().add(AppServices.class.getResource("darktheme.css").toExternalForm());
        }
    }

    public static Window getActiveWindow() {
        return Stage.getWindows().stream().filter(Window::isFocused).findFirst().orElse(get().walletWindows.keySet().iterator().hasNext() ? get().walletWindows.keySet().iterator().next() : null);
    }

    public static void moveToActiveWindowScreen(Dialog<?> dialog) {
        Window activeWindow = getActiveWindow();
        if(activeWindow != null) {
            moveToWindowScreen(activeWindow, dialog);
        }
    }

    public static void moveToActiveWindowScreen(Window newWindow, double newWindowWidth, double newWindowHeight) {
        Window activeWindow = getActiveWindow();
        if(activeWindow != null) {
            moveToWindowScreen(activeWindow, newWindow, newWindowWidth, newWindowHeight);
        }
    }

    public static void moveToWindowScreen(Window currentWindow, Dialog<?> dialog) {
        Window newWindow = dialog.getDialogPane().getScene().getWindow();
        DialogPane dialogPane = dialog.getDialogPane();
        double dialogWidth = dialogPane.getPrefWidth() > 0.0 ? dialogPane.getPrefWidth() : (dialogPane.getWidth() > 0.0 ? dialogPane.getWidth() : 360);
        double dialogHeight = dialogPane.getPrefHeight() > 0.0 ? dialogPane.getPrefHeight() : (dialogPane.getHeight() > 0.0 ? dialogPane.getHeight() : 200);
        moveToWindowScreen(currentWindow, newWindow, dialogWidth, dialogHeight);
    }

    public static void moveToWindowScreen(Window currentWindow, Window newWindow, double newWindowWidth, double newWindowHeight) {
        Screen currentScreen = Screen.getScreens().stream().filter(screen -> screen.getVisualBounds().contains(currentWindow.getX(), currentWindow.getY())).findFirst().orElse(null);
        if(currentScreen != null
                && ((!Double.isNaN(newWindow.getX()) && !Double.isNaN(newWindow.getY())) || !Screen.getPrimary().getVisualBounds().contains(currentWindow.getX(), currentWindow.getY()))
                && !currentScreen.getVisualBounds().contains(newWindow.getX(), newWindow.getY())) {
            double x = currentWindow.getX() + (currentWindow.getWidth() / 2) - (newWindowWidth / 2);
            double y = currentWindow.getY() + (currentWindow.getHeight() / 2.2) - (newWindowHeight / 2);
            newWindow.setX(x);
            newWindow.setY(y);
        }
    }

    public static void openBlockExplorer(String txid) {
        if(Config.get().isBlockExplorerDisabled()) {
            return;
        }

        Server blockExplorer = Config.get().getBlockExplorer() == null ? BlockExplorer.MEMPOOL_SPACE.getServer() : Config.get().getBlockExplorer();
        String url = blockExplorer.getUrl();
        if(url.contains("{0}")) {
            url = url.replace("{0}", txid);
        } else {
            if(Network.get() != Network.MAINNET) {
                url += "/" + Network.get().getName();
            }
            url += "/tx/" + txid;
        }
        AppServices.get().getApplication().getHostServices().showDocument(url);
    }

    static void parseFileUriArguments(List<String> fileUriArguments) {
        for(String fileUri : fileUriArguments) {
            try {
                File file = new File(fileUri.replace("~", System.getProperty("user.home")));
                if(file.exists()) {
                    argFiles.add(file);
                    continue;
                }
                URI uri = new URI(fileUri);
                argUris.add(uri);
            } catch(URISyntaxException e) {
                log.warn("Could not parse " + fileUri + " as a valid file or URI");
            } catch(Exception e) {
                //ignore
            }
        }
    }

    public static void openFileUriArguments(Window window) {
        openFiles(argFiles, window);
        argFiles.clear();

        for(URI argUri : argUris) {
            openURI(argUri);
        }
        argUris.clear();
    }

    private static void openFiles(List<File> files, Window window) {
        final List<File> openFiles = new ArrayList<>(files);
        Platform.runLater(() -> {
            Window openWindow = window;
            if(openWindow == null) {
                openWindow = getActiveWindow();
            }

            if(openWindow instanceof Stage) {
                ((Stage)openWindow).setAlwaysOnTop(true);
                ((Stage)openWindow).setAlwaysOnTop(false);
            }

            for(File file : openFiles) {
                if(isWalletFile(file)) {
                    EventManager.get().post(new RequestWalletOpenEvent(openWindow, file));
                } else {
                    EventManager.get().post(new RequestTransactionOpenEvent(openWindow, file));
                }
            }
        });
    }

    private static void openURI(URI uri) {
        Platform.runLater(() -> {
            if("bitcoin".equals(uri.getScheme())) {
                openBitcoinUri(uri);
            } else if(("auth47").equals(uri.getScheme())) {
                openAuth47Uri(uri);
            } else if(("lightning").equals(uri.getScheme())) {
                openLnurlAuthUri(uri);
            }
        });
    }

    public static void addURIHandlers() {
        try {
            if(Desktop.isDesktopSupported()) {
                if(Desktop.getDesktop().isSupported(Desktop.Action.APP_OPEN_FILE)) {
                    Desktop.getDesktop().setOpenFileHandler(openFilesHandler);
                }
                if(Desktop.getDesktop().isSupported(Desktop.Action.APP_OPEN_URI)) {
                    Desktop.getDesktop().setOpenURIHandler(openURIHandler);
                }
            }
        } catch(Exception e) {
            log.error("Could not add URI handler", e);
        }
    }

    private static void openBitcoinUri(URI uri) {
        try {
            BitcoinURI bitcoinURI = new BitcoinURI(uri.toString());
            List<PolicyType> policyTypes = Arrays.asList(PolicyType.values());
            List<ScriptType> scriptTypes = Arrays.asList(ScriptType.ADDRESSABLE_TYPES);
            Wallet wallet = selectWallet(policyTypes, scriptTypes, true, false, "pay from", false);

            if(wallet != null) {
                final Wallet sendingWallet = wallet;
                EventManager.get().post(new SendActionEvent(sendingWallet, new ArrayList<>(sendingWallet.getWalletUtxos().keySet()), true));
                Platform.runLater(() -> EventManager.get().post(new SendPaymentsEvent(sendingWallet, List.of(bitcoinURI.toPayment()))));
            }
        } catch(Exception e) {
            showErrorDialog("Not a valid bitcoin URI", e.getMessage());
        }
    }

    private static void openAuth47Uri(URI uri) {
        try {
            Auth47 auth47 = new Auth47(uri);
            List<ScriptType> scriptTypes = PaymentCode.SEGWIT_SCRIPT_TYPES;
            Wallet wallet = selectWallet(List.of(PolicyType.SINGLE), scriptTypes, false, true, "login to " + auth47.getCallback().getHost(), true);

            if(wallet != null) {
                try {
                    auth47.sendResponse(wallet);
                    EventManager.get().post(new StatusEvent("Successfully authenticated to " + auth47.getCallback().getHost()));
                } catch(Exception e) {
                    log.error("Error authenticating auth47 URI", e);
                    showErrorDialog("Error authenticating", "Failed to authenticate.\n\n" + e.getMessage());
                }
            }
        } catch(Exception e) {
            log.error("Not a valid auth47 URI", e);
            showErrorDialog("Not a valid auth47 URI", e.getMessage());
        }
    }

    private static void openLnurlAuthUri(URI uri) {
        try {
            LnurlAuth lnurlAuth = new LnurlAuth(uri);
            List<ScriptType> scriptTypes = ScriptType.getAddressableScriptTypes(PolicyType.SINGLE);
            Wallet wallet = selectWallet(List.of(PolicyType.SINGLE), scriptTypes, true, true, lnurlAuth.getLoginMessage(), true);

            if(wallet != null) {
                if(wallet.isEncrypted()) {
                    Storage storage = AppServices.get().getOpenWallets().get(wallet);
                    Wallet copy = wallet.copy();
                    WalletPasswordDialog dlg = new WalletPasswordDialog(copy.getMasterName(), WalletPasswordDialog.PasswordRequirement.LOAD);
                    Optional<SecureString> password = dlg.showAndWait();
                    if(password.isPresent()) {
                        Storage.KeyDerivationService keyDerivationService = new Storage.KeyDerivationService(storage, password.get(), true);
                        keyDerivationService.setOnSucceeded(workerStateEvent -> {
                            EventManager.get().post(new StorageEvent(storage.getWalletId(wallet), TimedEvent.Action.END, "Done"));
                            ECKey encryptionFullKey = keyDerivationService.getValue();
                            Key key = new Key(encryptionFullKey.getPrivKeyBytes(), storage.getKeyDeriver().getSalt(), EncryptionType.Deriver.ARGON2);
                            copy.decrypt(key);
                            try {
                                lnurlAuth.sendResponse(copy);
                                EventManager.get().post(new StatusEvent("Successfully authenticated to " + lnurlAuth.getDomain()));
                            } catch(Exception e) {
                                showErrorDialog("Error authenticating", "Failed to authenticate.\n\n" + e.getMessage());
                            } finally {
                                key.clear();
                                encryptionFullKey.clear();
                                password.get().clear();
                            }
                        });
                        keyDerivationService.setOnFailed(workerStateEvent -> {
                            EventManager.get().post(new StorageEvent(storage.getWalletId(wallet), TimedEvent.Action.END, "Failed"));
                            if(keyDerivationService.getException() instanceof InvalidPasswordException) {
                                Optional<ButtonType> optResponse = showErrorDialog("Invalid Password", "The wallet password was invalid. Try again?", ButtonType.CANCEL, ButtonType.OK);
                                if(optResponse.isPresent() && optResponse.get().equals(ButtonType.OK)) {
                                    Platform.runLater(() -> openLnurlAuthUri(uri));
                                }
                            } else {
                                log.error("Error deriving wallet key", keyDerivationService.getException());
                            }
                        });
                        EventManager.get().post(new StorageEvent(storage.getWalletId(wallet), TimedEvent.Action.START, "Decrypting wallet..."));
                        keyDerivationService.start();
                    }
                } else {
                    try {
                        lnurlAuth.sendResponse(wallet);
                        EventManager.get().post(new StatusEvent("Successfully authenticated to " + lnurlAuth.getDomain()));
                    } catch(LnurlAuth.LnurlAuthException e) {
                        showErrorDialog("Error authenticating", "Failed to authenticate.\n\n" + e.getMessage());
                    } catch(Exception e) {
                        log.error("Failed to authenticate using LNURL-auth", e);
                        showErrorDialog("Error authenticating", "Failed to authenticate.\n\n" + e.getMessage());
                    }
                }
            }
        } catch(Exception e) {
            log.error("Not a valid LNURL-auth URI", e);
            showErrorDialog("Not a valid LNURL-auth URI", e.getMessage());
        }
    }

    private static Wallet selectWallet(List<PolicyType> policyTypes, List<ScriptType> scriptTypes, boolean taprootAllowed, boolean privateKeysRequired, String actionDescription, boolean alwaysAsk) {
        Wallet wallet = null;
        List<Wallet> wallets = get().getOpenWallets().keySet().stream().filter(w -> w.isValid() && policyTypes.contains(w.getPolicyType()) && scriptTypes.contains(w.getScriptType())
                && (!privateKeysRequired || w.getKeystores().stream().allMatch(Keystore::hasPrivateKey))).collect(Collectors.toList());
        if(wallets.isEmpty()) {
            boolean taprootOpen = get().getOpenWallets().keySet().stream().anyMatch(w -> w.getScriptType() == ScriptType.P2TR);
            showErrorDialog("No wallet available", "Open a" + (taprootOpen && !taprootAllowed ? " non-Taproot" : "") + (privateKeysRequired ? " software" : "") + " wallet to " + actionDescription + ".");
        } else if(wallets.size() == 1 && !alwaysAsk) {
            wallet = wallets.iterator().next();
        } else {
            ChoiceDialog<Wallet> walletChoiceDialog = new ChoiceDialog<>(wallets.iterator().next(), wallets);
            walletChoiceDialog.setTitle("Choose Wallet");
            walletChoiceDialog.setHeaderText("Choose a wallet to " + actionDescription);
            Image image = new Image("/image/sparrow-small.png");
            walletChoiceDialog.getDialogPane().setGraphic(new ImageView(image));
            setStageIcon(walletChoiceDialog.getDialogPane().getScene().getWindow());
            moveToActiveWindowScreen(walletChoiceDialog);
            Optional<Wallet> optWallet = walletChoiceDialog.showAndWait();
            if(optWallet.isPresent()) {
                wallet = optWallet.get();
            }
        }

        return wallet;
    }

    public static Font getMonospaceFont() {
        return Font.font("Roboto Mono", 13);
    }

    @Subscribe
    public void newConnection(ConnectionEvent event) {
        currentBlockHeight = event.getBlockHeight();
        System.setProperty(Network.BLOCK_HEIGHT_PROPERTY, Integer.toString(currentBlockHeight));
        minimumRelayFeeRate = Math.max(event.getMinimumRelayFeeRate(), Transaction.DEFAULT_MIN_RELAY_FEE);
        latestBlockHeader = event.getBlockHeader();
        Config.get().addRecentServer();
    }

    @Subscribe
    public void usbDevicesFound(UsbDeviceEvent event) {
        devices = Collections.unmodifiableList(event.getDevices());
    }

    @Subscribe
    public void newBlock(NewBlockEvent event) {
        currentBlockHeight = event.getHeight();
        System.setProperty(Network.BLOCK_HEIGHT_PROPERTY, Integer.toString(currentBlockHeight));
        latestBlockHeader = event.getBlockHeader();
        String status = "Updating to new block height " + event.getHeight();
        EventManager.get().post(new StatusEvent(status));
    }

    @Subscribe
    public void feesUpdated(FeeRatesUpdatedEvent event) {
        targetBlockFeeRates = event.getTargetBlockFeeRates();
    }

    @Subscribe
    public void mempoolRateSizes(MempoolRateSizesUpdatedEvent event) {
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

        if(Config.get().getMode() != Mode.OFFLINE && event.isEnabled() && Network.get() == Network.MAINNET) {
            versionCheckService = createVersionCheckService();
            versionCheckService.start();
        }
    }

    @Subscribe
    public void openWallets(OpenWalletsEvent event) {
        if(event.getWalletTabDataList().isEmpty()) {
            List<WalletTabData> closedTabData = walletWindows.remove(event.getWindow());
            if(closedTabData != null && !closedTabData.isEmpty()) {
                EventManager.get().post(new WalletTabsClosedEvent(closedTabData));
            }
        } else {
            walletWindows.put(event.getWindow(), event.getWalletTabDataList());
        }

        List<WalletTabData> allWallets = walletWindows.values().stream().flatMap(Collection::stream).collect(Collectors.toList());

        Platform.runLater(() -> {
            if(!Window.getWindows().isEmpty()) {
                List<File> walletFiles = allWallets.stream().filter(walletTabData -> walletTabData.getWallet().getMasterWallet() == null).map(walletTabData -> walletTabData.getStorage().getWalletFile()).filter(File::exists).collect(Collectors.toList());
                Config.get().setRecentWalletFiles(Config.get().isLoadRecentWallets() ? walletFiles : Collections.emptyList());
            }
        });

        boolean usbWallet = false;
        for(WalletTabData walletTabData : allWallets) {
            Wallet wallet = walletTabData.getWallet();
            Storage storage = walletTabData.getStorage();

            if(Interface.get() == Interface.DESKTOP && (!storage.getWalletFile().exists() || wallet.containsSource(KeystoreSource.HW_USB) || CardApi.isReaderAvailable())) {
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
        if(Config.get().hasServer()) {
            onlineProperty.set(true);
        }
    }

    @Subscribe
    public void requestDisconnect(RequestDisconnectEvent event) {
        onlineProperty.set(false);
        //Ensure services don't try to reconnect later
        Platform.runLater(() -> {
            connectionService.cancel();
            ratesService.cancel();
            versionCheckService.cancel();
        });
    }

    @Subscribe
    public void walletAddressesChanged(WalletAddressesChangedEvent event) {
        restartBwt(event.getWallet());
    }

    @Subscribe
    public void walletOpening(WalletOpeningEvent event) {
        if(Config.get().getServerType() == ServerType.BITCOIN_CORE) {
            Platform.runLater(() -> restartBwt(event.getWallet()));
        }
    }

    @Subscribe
    public void childWalletsAdded(ChildWalletsAddedEvent event) {
        if(event.getChildWallets().stream().anyMatch(Wallet::isNested)) {
            restartBwt(event.getWallet());
        }
    }

    @Subscribe
    public void walletHistoryChanged(WalletHistoryChangedEvent event) {
        if(Config.get().getServerType() == ServerType.BITCOIN_CORE && event.getNestedHistoryChangedNodes().stream().anyMatch(node -> node.getTransactionOutputs().isEmpty())) {
            Platform.runLater(() -> restartBwt(event.getWallet()));
        }
    }

    private void restartBwt(Wallet wallet) {
        if(Config.get().getServerType() == ServerType.BITCOIN_CORE && connectionService != null && connectionService.isConnectionRunning() && wallet.isValid()) {
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

    @Subscribe
    public void walletHistoryFailed(WalletHistoryFailedEvent event) {
        if(Config.get().getServerType() == ServerType.PUBLIC_ELECTRUM_SERVER && isConnected()) {
            onlineProperty.set(false);
            log.warn("Failed to fetch wallet history from " + Config.get().getServerDisplayName() + ", reconnecting to another server...");
            Config.get().changePublicServer();
            onlineProperty.set(true);
        }
    }
}
