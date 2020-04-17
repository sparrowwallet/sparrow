package com.sparrowwallet.sparrow.wallet;

import com.sparrowwallet.drongo.wallet.Wallet;

public class WalletForm {
    private Wallet wallet;

    public WalletForm(Wallet wallet) {
        this.wallet = wallet;
    }

    public Wallet getWallet() {
        return wallet;
    }

    public void setWallet(Wallet wallet) {
        this.wallet = wallet;
    }
}
