package com.sparrowwallet.sparrow.event;

import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.sparrow.io.Storage;
import javafx.stage.Window;

public class ViewWalletEvent {
    private final Window window;
    private final Wallet wallet;
    private final Storage storage;

    public ViewWalletEvent(Window window, Wallet wallet, Storage storage) {
        this.window = window;
        this.wallet = wallet;
        this.storage = storage;
    }

    public Window getWindow() {
        return window;
    }

    public Wallet getWallet() {
        return wallet;
    }

    public Storage getStorage() {
        return storage;
    }
}
