package com.sparrowwallet.sparrow.wallet;

import com.sparrowwallet.sparrow.BaseController;

public abstract class WalletFormController extends BaseController {
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
