package com.sparrowwallet.sparrow.event;

import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.sparrow.WalletTabData;
import com.sparrowwallet.sparrow.io.Storage;
import javafx.stage.Window;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class OpenWalletsEvent {
    private final Window window;
    private final List<WalletTabData> walletTabDataList;

    public OpenWalletsEvent(Window window, List<WalletTabData> walletTabDataList) {
        this.window = window;
        this.walletTabDataList = walletTabDataList;
    }

    public Window getWindow() {
        return window;
    }

    public List<WalletTabData> getWalletTabDataList() {
        return walletTabDataList;
    }

    public Map<Wallet, Storage> getWalletsMap() {
        Map<Wallet, Storage> openWallets = new LinkedHashMap<>();

        for(WalletTabData walletTabData : walletTabDataList){
            openWallets.put(walletTabData.getWallet(), walletTabData.getStorage());
        }

        return openWallets;
    }

    public List<Wallet> getWallets() {
        return new ArrayList<>(getWalletsMap().keySet());
    }

    public Storage getStorage(Wallet wallet) {
        return getWalletsMap().get(wallet);
    }
}
