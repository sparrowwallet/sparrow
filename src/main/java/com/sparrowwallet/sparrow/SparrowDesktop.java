package com.sparrowwallet.sparrow;

import com.sparrowwallet.drongo.Network;
import com.sparrowwallet.drongo.OsType;
import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.sparrow.control.WalletIcon;
import com.sparrowwallet.sparrow.glyphfont.FontAwesome5;
import com.sparrowwallet.sparrow.glyphfont.FontAwesome5Brands;
import com.sparrowwallet.sparrow.io.Config;
import com.sparrowwallet.sparrow.io.Storage;
import com.sparrowwallet.sparrow.net.PublicElectrumServer;
import com.sparrowwallet.sparrow.net.ServerType;
import com.sparrowwallet.sparrow.settings.SettingsGroup;
import com.sparrowwallet.sparrow.settings.SettingsDialog;
import javafx.application.Application;
import javafx.scene.text.Font;
import javafx.stage.Stage;
import org.controlsfx.glyphfont.GlyphFontRegistry;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.net.URL;
import java.util.*;
import java.util.stream.Collectors;

public class SparrowDesktop extends Application {
    private Stage mainStage;

    @Override
    public void init() throws Exception {
        Thread.setDefaultUncaughtExceptionHandler((t, e) -> {
            if(e instanceof IndexOutOfBoundsException && Arrays.stream(e.getStackTrace()).anyMatch(element -> element.getClassName().equals("javafx.scene.chart.BarChart"))) {
                LoggerFactory.getLogger(SparrowWallet.class).debug("Exception in thread \"" + t.getName() + "\"", e);;
            } else {
                LoggerFactory.getLogger(SparrowWallet.class).error("Exception in thread \"" + t.getName() + "\"", e);
            }
        });
        super.init();
    }

    @Override
    public void start(Stage stage) throws Exception {
        this.mainStage = stage;

        initializeFonts();
        URL.setURLStreamHandlerFactory(protocol -> WalletIcon.PROTOCOL.equals(protocol) ? new WalletIcon.WalletIconStreamHandler() : null);

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
                    SettingsDialog settingsDialog = new SettingsDialog(SettingsGroup.SERVER, true);
                    Optional<Boolean> optNewWallet = settingsDialog.showAndWait();
                    createNewWallet = optNewWallet.isPresent() && optNewWallet.get();
                } else if(Network.get() == Network.MAINNET) {
                    Config.get().setServerType(ServerType.PUBLIC_ELECTRUM_SERVER);
                    List<PublicElectrumServer> servers = PublicElectrumServer.getServers();
                    Config.get().setPublicElectrumServer(servers.get(new Random().nextInt(servers.size())).getServer());
                }
            }
        }

        if(Config.get().getServerType() == null && Config.get().getCoreServer() == null && Config.get().getElectrumServer() != null) {
            Config.get().setServerType(ServerType.ELECTRUM_SERVER);
        }

        System.setProperty(Wallet.ALLOW_DERIVATIONS_MATCHING_OTHER_SCRIPT_TYPES_PROPERTY, Boolean.toString(!Config.get().isValidateDerivationPaths()));
        System.setProperty(Wallet.ALLOW_DERIVATIONS_MATCHING_OTHER_NETWORKS_PROPERTY, Boolean.toString(!Config.get().isValidateDerivationPaths()));

        if(Config.get().getAppHeight() != null && Config.get().getAppWidth() != null) {
            mainStage.setWidth(Config.get().getAppWidth());
            mainStage.setHeight(Config.get().getAppHeight());
        }

        AppController appController = AppServices.newAppWindow(stage);

        final boolean showNewWallet = createNewWallet;
        //Delay opening new dialogs on Wayland
        AppServices.runAfterDelay(AppServices.isOnWayland() ? 1000 : 0, () -> {
            if(showNewWallet) {
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

            AppServices.openFileUriArgumentsAfterWalletLoading(stage);

            AppServices.get().start();
        });
    }

    private void initializeFonts() {
        GlyphFontRegistry.register(new FontAwesome5());
        GlyphFontRegistry.register(new FontAwesome5Brands());
        Font.loadFont(AppServices.class.getResourceAsStream("/font/RobotoMono-Regular.ttf"), 13);
        Font.loadFont(AppServices.class.getResourceAsStream("/font/RobotoMono-Italic.ttf"), 11);
        if(OsType.getCurrent() == OsType.MACOS) {
            Font.loadFont(AppServices.class.getResourceAsStream("/font/LiberationSans-Regular.ttf"), 13);
        }
    }

    @Override
    public void stop() throws Exception {
        AppServices.get().stop();
        Config.get().setAppWidth(mainStage.getWidth());
        Config.get().setAppHeight(mainStage.getHeight());
        mainStage.close();
        SparrowWallet.Instance instance = SparrowWallet.getSparrowInstance();
        if(instance != null) {
            instance.freeLock();
        }
    }
}
