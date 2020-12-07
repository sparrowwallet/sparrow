package com.sparrowwallet.sparrow.event;

import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.sparrow.io.Storage;
import javafx.stage.Window;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class OpenWalletsEvent {
    private final Window window;
    private final Map<Wallet, Storage> walletsMap;

    public OpenWalletsEvent(Window window, Map<Wallet, Storage> walletsMap) {
        this.window = window;
        this.walletsMap = walletsMap;
    }

    public Window getWindow() {
        return window;
    }

    public List<Wallet> getWallets() {
        return new ArrayList<>(walletsMap.keySet());
    }

    public Storage getStorage(Wallet wallet) {
        return walletsMap.get(wallet);
    }

    public Map<Wallet, Storage> getWalletsMap() {
        return walletsMap;
    }
}
