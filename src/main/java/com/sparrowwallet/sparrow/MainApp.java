package com.sparrowwallet.sparrow;

import com.beust.jcommander.JCommander;
import com.sparrowwallet.drongo.Network;
import com.sparrowwallet.sparrow.control.WelcomeDialog;
import com.sparrowwallet.sparrow.glyphfont.FontAwesome5;
import com.sparrowwallet.sparrow.glyphfont.FontAwesome5Brands;
import com.sparrowwallet.sparrow.io.Config;
import com.sparrowwallet.sparrow.io.FileType;
import com.sparrowwallet.sparrow.io.IOUtils;
import com.sparrowwallet.sparrow.io.Storage;
import com.sparrowwallet.sparrow.preferences.PreferenceGroup;
import com.sparrowwallet.sparrow.preferences.PreferencesDialog;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.text.Font;
import javafx.stage.Stage;
import org.controlsfx.glyphfont.GlyphFontRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.*;
import java.util.stream.Collectors;

public class MainApp extends Application {
    private static final Logger log = LoggerFactory.getLogger(MainApp.class);

    public static final String APP_NAME = "Sparrow";
    public static final String APP_VERSION = "0.9.7";
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
        Font.loadFont(AppController.class.getResourceAsStream("/font/RobotoMono-Regular.ttf"), 13);

        boolean createNewWallet = false;
        Mode mode = Config.get().getMode();
        if(mode == null) {
            WelcomeDialog welcomeDialog = new WelcomeDialog(getHostServices());
            Optional<Mode> optionalMode = welcomeDialog.showAndWait();
            if(optionalMode.isPresent()) {
                mode = optionalMode.get();
                Config.get().setMode(mode);

                if(mode.equals(Mode.ONLINE)) {
                    PreferencesDialog preferencesDialog = new PreferencesDialog(PreferenceGroup.SERVER, true);
                    Optional<Boolean> optNewWallet = preferencesDialog.showAndWait();
                    createNewWallet = optNewWallet.isPresent() && optNewWallet.get();
                }
            }
        }

        FXMLLoader transactionLoader = new FXMLLoader(getClass().getResource("app.fxml"));
        Parent root = transactionLoader.load();
        AppController appController = transactionLoader.getController();
        appController.setApplication(this);

        Scene scene = new Scene(root);
        scene.getStylesheets().add(getClass().getResource("app.css").toExternalForm());

        stage.setTitle("Sparrow");
        stage.setMinWidth(650);
        stage.setMinHeight(700);
        stage.setScene(scene);
        stage.getIcons().add(new Image(MainApp.class.getResourceAsStream("/image/sparrow.png")));

        appController.initializeView();

        stage.show();

        if(createNewWallet) {
            appController.newWallet(null);
        }

        List<File> recentWalletFiles = Config.get().getRecentWalletFiles();
        if(recentWalletFiles != null) {
            //Resort to preserve wallet order as far as possible. Unencrypted wallets will still be opened first.
            List<File> encryptedWalletFiles = recentWalletFiles.stream().filter(file -> FileType.BINARY.equals(IOUtils.getFileType(file))).collect(Collectors.toList());
            Collections.reverse(encryptedWalletFiles);
            List<File> sortedWalletFiles = new ArrayList<>(recentWalletFiles);
            sortedWalletFiles.removeAll(encryptedWalletFiles);
            sortedWalletFiles.addAll(encryptedWalletFiles);

            for(File walletFile : sortedWalletFiles) {
                if(walletFile.exists()) {
                    Platform.runLater(() -> appController.openWalletFile(walletFile));
                }
            }
        }
    }

    @Override
    public void stop() throws Exception {
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

        if(args.dir != null) {
            log.info("Using configured Sparrow home folder of " + args.dir);
            System.setProperty(APP_HOME_PROPERTY, args.dir);
        }

        if(args.network != null) {
            Network.set(args.network);
        } else {
            String envNetwork = System.getenv(NETWORK_ENV_PROPERTY);
            if(envNetwork != null) {
                try {
                    Network.set(Network.valueOf(envNetwork.toUpperCase()));
                } catch(Exception e) {
                    log.warn("Invalid " + NETWORK_ENV_PROPERTY + " property: " + envNetwork);
                }
            }
        }

        File testnetFlag = new File(Storage.getSparrowHome(), "network-" + Network.TESTNET.getName());
        if(testnetFlag.exists()) {
            Network.set(Network.TESTNET);
        }

        if(Network.get() != Network.MAINNET) {
            log.info("Using " + Network.get() + " configuration");
        }

        com.sun.javafx.application.LauncherImpl.launchApplication(MainApp.class, MainAppPreloader.class, argv);
    }
}
