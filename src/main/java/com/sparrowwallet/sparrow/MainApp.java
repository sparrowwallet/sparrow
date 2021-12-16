package com.sparrowwallet.sparrow;

import com.beust.jcommander.JCommander;
import com.sparrowwallet.drongo.Drongo;
import com.sparrowwallet.drongo.Network;
import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.sparrow.glyphfont.FontAwesome5;
import com.sparrowwallet.sparrow.glyphfont.FontAwesome5Brands;
import com.sparrowwallet.sparrow.io.Config;
import com.sparrowwallet.sparrow.io.Storage;
import com.sparrowwallet.sparrow.net.PublicElectrumServer;
import com.sparrowwallet.sparrow.net.ServerType;
import com.sparrowwallet.sparrow.preferences.PreferenceGroup;
import com.sparrowwallet.sparrow.preferences.PreferencesDialog;
import com.sparrowwallet.sparrow.instance.InstanceException;
import com.sparrowwallet.sparrow.instance.InstanceList;
import javafx.application.Application;
import javafx.scene.text.Font;
import javafx.stage.Stage;
import org.controlsfx.glyphfont.GlyphFontRegistry;
import org.controlsfx.tools.Platform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.bridge.SLF4JBridgeHandler;

import java.io.File;
import java.util.*;
import java.util.stream.Collectors;

public class MainApp extends Application {
    public static final String APP_ID = "com.sparrowwallet.sparrow";
    public static final String APP_NAME = "Sparrow";
    public static final String APP_VERSION = "1.5.5";
    public static final String APP_VERSION_SUFFIX = "";
    public static final String APP_HOME_PROPERTY = "sparrow.home";
    public static final String NETWORK_ENV_PROPERTY = "SPARROW_NETWORK";

    private Stage mainStage;

    private static SparrowInstance sparrowInstance;

    @Override
    public void init() throws Exception {
        Thread.setDefaultUncaughtExceptionHandler((t, e) -> {
            if(e instanceof IndexOutOfBoundsException && Arrays.stream(e.getStackTrace()).anyMatch(element -> element.getClassName().equals("javafx.scene.chart.BarChart"))) {
                LoggerFactory.getLogger(MainApp.class).debug("Exception in thread \"" + t.getName() + "\"", e);;
            } else {
                LoggerFactory.getLogger(MainApp.class).error("Exception in thread \"" + t.getName() + "\"", e);
            }
        });
        super.init();
    }

    @Override
    public void start(Stage stage) throws Exception {
        this.mainStage = stage;

        GlyphFontRegistry.register(new FontAwesome5());
        GlyphFontRegistry.register(new FontAwesome5Brands());
        Font.loadFont(AppServices.class.getResourceAsStream("/font/RobotoMono-Regular.ttf"), 13);

        AppServices.initialize(this);

        boolean createNewWallet = false;
        Mode mode = Config.get().getMode();
        if(mode == null) {
            WelcomeDialog welcomeDialog = new WelcomeDialog();
            Optional<Mode> optionalMode = welcomeDialog.showAndWait();
            if(optionalMode.isPresent()) {
                mode = optionalMode.get();
                Config.get().setMode(mode);

                if(mode.equals(Mode.ONLINE)) {
                    PreferencesDialog preferencesDialog = new PreferencesDialog(PreferenceGroup.SERVER, true);
                    Optional<Boolean> optNewWallet = preferencesDialog.showAndWait();
                    createNewWallet = optNewWallet.isPresent() && optNewWallet.get();
                } else if(Network.get() == Network.MAINNET) {
                    Config.get().setServerType(ServerType.PUBLIC_ELECTRUM_SERVER);
                    List<PublicElectrumServer> servers = PublicElectrumServer.getServers();
                    Config.get().setPublicElectrumServer(servers.get(new Random().nextInt(servers.size())).getUrl());
                }
            }
        }

        if(Config.get().getServerType() == null && Config.get().getCoreServer() == null && Config.get().getElectrumServer() != null) {
            Config.get().setServerType(ServerType.ELECTRUM_SERVER);
        }

        if(Config.get().getHdCapture() == null && Platform.getCurrent() == Platform.OSX) {
            Config.get().setHdCapture(Boolean.TRUE);
        }

        System.setProperty(Wallet.ALLOW_DERIVATIONS_MATCHING_OTHER_SCRIPT_TYPES_PROPERTY, Boolean.toString(!Config.get().isValidateDerivationPaths()));

        if(Config.get().getAppHeight() != null && Config.get().getAppWidth() != null) {
            mainStage.setWidth(Config.get().getAppWidth());
            mainStage.setHeight(Config.get().getAppHeight());
        }

        AppController appController = AppServices.newAppWindow(stage);

        if(createNewWallet) {
            appController.newWallet(null);
        }

        List<File> recentWalletFiles = Config.get().getRecentWalletFiles();
        if(recentWalletFiles != null) {
            //Preserve wallet order as far as possible. Unencrypted wallets will still be opened first.
            List<File> encryptedWalletFiles = recentWalletFiles.stream().filter(Storage::isEncrypted).collect(Collectors.toList());
            List<File> sortedWalletFiles = new ArrayList<>(recentWalletFiles);
            sortedWalletFiles.removeAll(encryptedWalletFiles);
            sortedWalletFiles.addAll(encryptedWalletFiles);

            for(File walletFile : sortedWalletFiles) {
                if(walletFile.exists()) {
                    appController.openWalletFile(walletFile, false);
                }
            }
        }

        AppServices.openFileUriArguments(stage);

        AppServices.get().start();
    }

    @Override
    public void stop() throws Exception {
        AppServices.get().stop();
        Config.get().setAppWidth(mainStage.getWidth());
        Config.get().setAppHeight(mainStage.getHeight());
        mainStage.close();
        if(sparrowInstance != null) {
            sparrowInstance.freeLock();
        }
    }

    public static void main(String[] argv) {
        Args args = new Args();
        JCommander jCommander = JCommander.newBuilder().addObject(args).programName(APP_NAME.toLowerCase()).acceptUnknownOptions(true).build();
        jCommander.parse(argv);
        if(args.help) {
            jCommander.usage();
            System.exit(0);
        }

        if(args.level != null) {
            Drongo.setRootLogLevel(args.level);
        }

        if(args.dir != null) {
            System.setProperty(APP_HOME_PROPERTY, args.dir);
            getLogger().info("Using configured Sparrow home folder of " + args.dir);
        }

        if(args.network != null) {
            Network.set(args.network);
        } else {
            String envNetwork = System.getenv(NETWORK_ENV_PROPERTY);
            if(envNetwork != null) {
                try {
                    Network.set(Network.valueOf(envNetwork.toUpperCase()));
                } catch(Exception e) {
                    getLogger().warn("Invalid " + NETWORK_ENV_PROPERTY + " property: " + envNetwork);
                }
            }
        }

        File testnetFlag = new File(Storage.getSparrowHome(), "network-" + Network.TESTNET.getName());
        if(testnetFlag.exists()) {
            Network.set(Network.TESTNET);
        }

        File signetFlag = new File(Storage.getSparrowHome(), "network-" + Network.SIGNET.getName());
        if(signetFlag.exists()) {
            Network.set(Network.SIGNET);
        }

        if(Network.get() != Network.MAINNET) {
            getLogger().info("Using " + Network.get() + " configuration");
        }

        List<String> fileUriArguments = jCommander.getUnknownOptions();

        try {
            sparrowInstance = new SparrowInstance(fileUriArguments);
            sparrowInstance.acquireLock(); //If fileUriArguments is not empty, will exit app after sending fileUriArguments if lock cannot be acquired
        } catch(InstanceException e) {
            getLogger().error("Could not access application lock", e);
        }

        if(!fileUriArguments.isEmpty()) {
            AppServices.parseFileUriArguments(fileUriArguments);
        }

        SLF4JBridgeHandler.removeHandlersForRootLogger();
        SLF4JBridgeHandler.install();
        com.sun.javafx.application.LauncherImpl.launchApplication(MainApp.class, MainAppPreloader.class, argv);
    }

    private static Logger getLogger() {
        return LoggerFactory.getLogger(MainApp.class);
    }

    private static class SparrowInstance extends InstanceList {
        private final List<String> fileUriArguments;

        public SparrowInstance(List<String> fileUriArguments) {
            super(MainApp.APP_ID + "." + Network.get(), !fileUriArguments.isEmpty());
            this.fileUriArguments = fileUriArguments;
        }

        @Override
        protected void receiveMessageList(List<String> messageList) {
            if(messageList != null && !messageList.isEmpty()) {
                AppServices.parseFileUriArguments(messageList);
                AppServices.openFileUriArguments(null);
            }
        }

        @Override
        protected List<String> sendMessageList() {
            return fileUriArguments;
        }

        @Override
        protected void beforeExit() {
            getLogger().info("Opening files/URIs in already running instance, exiting...");
        }
    }
}
