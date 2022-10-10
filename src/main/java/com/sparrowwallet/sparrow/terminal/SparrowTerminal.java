package com.sparrowwallet.sparrow.terminal;

import com.googlecode.lanterna.TextColor;
import com.googlecode.lanterna.gui2.DefaultWindowManager;
import com.googlecode.lanterna.gui2.EmptySpace;
import com.googlecode.lanterna.screen.Screen;
import com.googlecode.lanterna.screen.TerminalScreen;
import com.googlecode.lanterna.terminal.DefaultTerminalFactory;
import com.googlecode.lanterna.terminal.Terminal;
import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.sparrow.AppServices;
import com.sparrowwallet.sparrow.EventManager;
import com.sparrowwallet.sparrow.terminal.wallet.WalletData;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class SparrowTerminal extends Application {
    private static final Logger log = LoggerFactory.getLogger(SparrowTerminal.class);

    private static SparrowTerminal sparrowTerminal;

    private Terminal terminal;
    private Screen screen;
    private SparrowTextGui gui;

    private final Map<Wallet, WalletData> walletData = new HashMap<>();

    @Override
    public void init() throws Exception {
        AppServices.initialize(this, new TerminalInteractionServices());

        this.terminal = new DefaultTerminalFactory().createTerminal();
        this.screen = new TerminalScreen(terminal);
        this.gui = new SparrowTextGui(this, screen, new DefaultWindowManager(), new EmptySpace(TextColor.ANSI.BLUE));
        EventManager.get().register(gui);

        sparrowTerminal = this;

        getScreen().startScreen();
        getGui().getMainWindow().waitUntilClosed();
    }

    @Override
    public void start(Stage primaryStage) throws Exception {

    }

    public Screen getScreen() {
        return screen;
    }

    public SparrowTextGui getGui() {
        return gui;
    }

    public Map<Wallet, WalletData> getWalletData() {
        return walletData;
    }

    public void stop() {
        try {
            screen.stopScreen();
            terminal.exitPrivateMode();
            Platform.runLater(() -> {
                AppServices.get().stop();
                Platform.exit();
            });
        } catch(Exception e) {
            log.error("Could not stop terminal", e);
        }
    }

    public static SparrowTerminal get() {
        return sparrowTerminal;
    }
}
