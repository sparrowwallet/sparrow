package com.sparrowwallet.sparrow.event;

import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.sparrow.io.Storage;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class OpenWalletsEvent {
    private final Map<Wallet, Storage> walletsMap;

    public OpenWalletsEvent(Map<Wallet, Storage> walletsMap) {
        this.walletsMap = walletsMap;
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
