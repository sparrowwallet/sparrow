package com.sparrowwallet.sparrow;

import com.beust.jcommander.JCommander;
import com.sparrowwallet.drongo.Drongo;
import com.sparrowwallet.drongo.Network;
import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.sparrow.glyphfont.FontAwesome5;
import com.sparrowwallet.sparrow.glyphfont.FontAwesome5Brands;
import com.sparrowwallet.sparrow.io.Config;
import com.sparrowwallet.sparrow.io.FileType;
import com.sparrowwallet.sparrow.io.IOUtils;
import com.sparrowwallet.sparrow.io.Storage;
import com.sparrowwallet.sparrow.net.Bwt;
import com.sparrowwallet.sparrow.net.PublicElectrumServer;
import com.sparrowwallet.sparrow.net.ServerType;
import com.sparrowwallet.sparrow.preferences.PreferenceGroup;
import com.sparrowwallet.sparrow.preferences.PreferencesDialog;
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
    public static final String APP_NAME = "Sparrow";
    public static final String APP_VERSION = "1.3.1";
    public static final String APP_HOME_PROPERTY = "sparrow.home";
    public static final String NETWORK_ENV_PROPERTY = "SPARROW_NETWORK";

    private Stage mainStage;

    @Override
    public void init() throws Exception {
        Thread.setDefaultUncaughtExceptionHandler((t, e) -> LoggerFactory.getLogger(MainApp.class).error("Exception in thread \"" + t.getName() + "\"", e));
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
                Config.get().setCoreWallet(Bwt.DEFAULT_CORE_WALLET);

                if(mode.equals(Mode.ONLINE)) {
                    PreferencesDialog preferencesDialog = new PreferencesDialog(PreferenceGroup.SERVER, true);
                    Optional<Boolean> optNewWallet = preferencesDialog.showAndWait();
                    createNewWallet = optNewWallet.isPresent() && optNewWallet.get();
                } else if(Network.get() == Network.MAINNET) {
                    Config.get().setServerType(ServerType.PUBLIC_ELECTRUM_SERVER);
                    Config.get().setPublicElectrumServer(PublicElectrumServer.values()[new Random().nextInt(PublicElectrumServer.values().length)].getUrl());
                }
            }
        }

        if(Config.get().getServerType() == null && Config.get().getCoreServer() == null && Config.get().getElectrumServer() != null) {
            Config.get().setServerType(ServerType.ELECTRUM_SERVER);
        } else if(Config.get().getServerType() == ServerType.BITCOIN_CORE && Config.get().getCoreWallet() == null) {
            Config.get().setCoreMultiWallet(Boolean.TRUE);
            Config.get().setCoreWallet("");
        }

        if(Config.get().getHdCapture() == null && Platform.getCurrent() == Platform.OSX) {
            Config.get().setHdCapture(Boolean.TRUE);
        }

        System.setProperty(Wallet.ALLOW_DERIVATIONS_MATCHING_OTHER_SCRIPT_TYPES_PROPERTY, Boolean.toString(!Config.get().isValidateDerivationPaths()));

        AppController appController = AppServices.newAppWindow(stage);

        if(createNewWallet) {
            appController.newWallet(null);
        }

        List<File> recentWalletFiles = Config.get().getRecentWalletFiles();
        if(recentWalletFiles != null) {
            //Re-sort to preserve wallet order as far as possible. Unencrypted wallets will still be opened first.
            List<File> encryptedWalletFiles = recentWalletFiles.stream().filter(file -> FileType.BINARY.equals(IOUtils.getFileType(file))).collect(Collectors.toList());
            Collections.reverse(encryptedWalletFiles);
            List<File> sortedWalletFiles = new ArrayList<>(recentWalletFiles);
            sortedWalletFiles.removeAll(encryptedWalletFiles);
            sortedWalletFiles.addAll(encryptedWalletFiles);

            for(File walletFile : sortedWalletFiles) {
                if(walletFile.exists()) {
                    appController.openWalletFile(walletFile, false);
                }
            }
        }

        AppServices.get().start();
    }

    @Override
    public void stop() throws Exception {
        AppServices.get().stop();
        mainStage.close();
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

        SLF4JBridgeHandler.removeHandlersForRootLogger();
        SLF4JBridgeHandler.install();
        com.sun.javafx.application.LauncherImpl.launchApplication(MainApp.class, MainAppPreloader.class, argv);
    }

    private static Logger getLogger() {
        return LoggerFactory.getLogger(MainApp.class);
    }
}
