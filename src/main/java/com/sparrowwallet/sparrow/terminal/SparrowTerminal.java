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
import com.sparrowwallet.sparrow.AppServices;
import com.sparrowwallet.sparrow.EventManager;
import com.sparrowwallet.sparrow.SparrowWallet;
import com.sparrowwallet.sparrow.terminal.wallet.WalletData;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

public class SparrowTerminal extends Application {
    private static final Logger log = LoggerFactory.getLogger(SparrowTerminal.class);

    private static SparrowTerminal sparrowTerminal;

    private Terminal terminal;
    private Screen screen;
    private SparrowTextGui gui;

    private final Map<String, WalletData> walletData = new HashMap<>();

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
        } catch(Exception e) {
            log.error("Could not stop terminal screen", e);
        }
    }

    public static SparrowTerminal get() {
        return sparrowTerminal;
    }
}
