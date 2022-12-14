package com.sparrowwallet.sparrow.terminal;

import com.googlecode.lanterna.TextColor;
import com.googlecode.lanterna.gui2.DefaultWindowManager;
import com.googlecode.lanterna.gui2.EmptySpace;
import com.googlecode.lanterna.gui2.TextGUIThread;
import com.googlecode.lanterna.screen.Screen;
import com.googlecode.lanterna.screen.TerminalScreen;
import com.googlecode.lanterna.terminal.DefaultTerminalFactory;
import com.googlecode.lanterna.terminal.Terminal;
import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.sparrow.*;
import com.sparrowwallet.sparrow.event.OpenWalletsEvent;
import com.sparrowwallet.sparrow.event.WalletOpenedEvent;
import com.sparrowwallet.sparrow.event.WalletOpeningEvent;
import com.sparrowwallet.sparrow.io.Config;
import com.sparrowwallet.sparrow.io.Storage;
import com.sparrowwallet.sparrow.terminal.wallet.WalletData;
import com.sparrowwallet.sparrow.wallet.WalletForm;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.*;
import java.util.stream.Collectors;

import static com.sparrowwallet.sparrow.terminal.MasterActionListBox.MAX_RECENT_WALLETS;

public class SparrowTerminal extends Application {
    private static final Logger log = LoggerFactory.getLogger(SparrowTerminal.class);

    private static SparrowTerminal sparrowTerminal;

    private Terminal terminal;
    private Screen screen;
    private SparrowTextGui gui;

    private final Map<String, WalletData> walletData = new HashMap<>();
    private final Set<File> lockedWallets = new HashSet<>();

    private static final javafx.stage.Window DEFAULT_WINDOW = new Window() { };

    @Override
    public void init() throws Exception {
        Thread.setDefaultUncaughtExceptionHandler((t, e) -> {
            log.error("Exception in thread \"" + t.getName() + "\"", e);
        });

        AppServices.initialize(this, new TerminalInteractionServices());

        this.terminal = new DefaultTerminalFactory().createTerminal();
        this.screen = new TerminalScreen(terminal);
        this.gui = new SparrowTextGui(this, screen, new DefaultWindowManager(), new EmptySpace(TextColor.ANSI.BLUE));
        EventManager.get().register(gui);

        terminal.addResizeListener((terminal1, newSize) -> {
            gui.handleResize();
        });

        sparrowTerminal = this;

        try {
            getScreen().startScreen();
            getGui().getMainWindow().waitUntilClosed();
        } finally {
            exit();
        }
    }

    @Override
    public void start(Stage primaryStage) throws Exception {

    }

    @Override
    public void stop() throws Exception {

    }

    public Screen getScreen() {
        return screen;
    }

    public SparrowTextGui getGui() {
        return gui;
    }

    public TextGUIThread getGuiThread() {
        return gui.getGUIThread();
    }

    public Map<String, WalletData> getWalletData() {
        return walletData;
    }

    public void exit() {
        try {
            screen.stopScreen();
            Platform.runLater(() -> {
                AppServices.get().stop();
                Platform.exit();
            });
            SparrowWallet.Instance instance = SparrowWallet.getSparrowInstance();
            if(instance != null) {
                instance.freeLock();
            }

            List<File> recentWalletFiles = Config.get().getRecentWalletFiles();
            if(recentWalletFiles != null && !recentWalletFiles.isEmpty()) {
                Set<File> openedWalletFiles = new LinkedHashSet<>(recentWalletFiles);
                openedWalletFiles.removeIf(file -> walletData.values().stream().noneMatch(data -> data.getWalletForm().getWalletFile().equals(file)));
                openedWalletFiles.addAll(Config.get().getRecentWalletFiles().subList(0, Math.min(3, recentWalletFiles.size())));
                Config.get().setRecentWalletFiles(new ArrayList<>(openedWalletFiles));
            }
        } catch(Exception e) {
            log.error("Could not stop terminal screen", e);
        }
    }

    public static SparrowTerminal get() {
        return sparrowTerminal;
    }

    public static void addWallet(Storage storage, Wallet wallet) {
        if(wallet.isNested()) {
            WalletData walletData = SparrowTerminal.get().getWalletData().get(storage.getWalletId(wallet.getMasterWallet()));
            WalletForm walletForm = new WalletForm(storage, wallet);
            EventManager.get().register(walletForm);
            walletData.getWalletForm().getNestedWalletForms().add(walletForm);
        } else {
            EventManager.get().post(new WalletOpeningEvent(storage, wallet));

            WalletForm walletForm = new WalletForm(storage, wallet);
            EventManager.get().register(walletForm);
            SparrowTerminal.get().getWalletData().put(walletForm.getWalletId(), new WalletData(walletForm));

            List<WalletTabData> walletTabDataList = SparrowTerminal.get().getWalletData().values().stream()
                    .map(data -> new WalletTabData(TabData.TabType.WALLET, data.getWalletForm())).collect(Collectors.toList());
            EventManager.get().post(new OpenWalletsEvent(DEFAULT_WINDOW, walletTabDataList));

            if(wallet.isValid()) {
                Platform.runLater(() -> walletForm.refreshHistory(AppServices.getCurrentBlockHeight()));
            }

            Set<File> walletFiles = new LinkedHashSet<>();
            walletFiles.add(storage.getWalletFile());
            if(Config.get().getRecentWalletFiles() != null) {
                walletFiles.addAll(Config.get().getRecentWalletFiles().stream().limit(MAX_RECENT_WALLETS - 1).collect(Collectors.toList()));
            }
            Config.get().setRecentWalletFiles(Config.get().isLoadRecentWallets() ? new ArrayList<>(walletFiles) : Collections.emptyList());
        }

        EventManager.get().post(new WalletOpenedEvent(storage, wallet));
    }

    public boolean isLocked(Storage storage) {
        return lockedWallets.contains(storage.getWalletFile());
    }

    public void lockWallet(Storage storage) {
        lockedWallets.add(storage.getWalletFile());
    }

    public void unlockWallet(Storage storage) {
        lockedWallets.remove(storage.getWalletFile());
    }
}
