package com.sparrowwallet.sparrow;

import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.sparrow.io.Storage;
import com.sparrowwallet.sparrow.wallet.WalletForm;

public class WalletTabData extends TabData {
    private final WalletForm walletForm;

    public WalletTabData(TabType type, WalletForm walletForm) {
        super(type);
        this.walletForm = walletForm;
    }

    public WalletForm getWalletForm() {
        return walletForm;
    }

    public Wallet getWallet() {
        return walletForm.getWallet();
    }

    public Storage getStorage() {
        return walletForm.getStorage();
    }
}
