package com.sparrowwallet.sparrow.wallet;

public abstract class WalletFormController {
    public WalletForm walletForm;

    public WalletForm getWalletForm() {
        return walletForm;
    }

    public void setWalletForm(WalletForm walletForm) {
        this.walletForm = walletForm;
        initializeView();
    }

    public abstract void initializeView();
}
