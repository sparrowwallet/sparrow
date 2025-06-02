package com.sparrowwallet.sparrow.joinstr;

import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.sparrow.io.Storage;
import com.sparrowwallet.sparrow.wallet.WalletForm;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;

public class JoinstrForm {

    private final BooleanProperty lockedProperty = new SimpleBooleanProperty(false);

    private final WalletForm walletForm;

    public JoinstrForm(WalletForm walletForm) {
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

    public BooleanProperty lockedProperty() {
        return lockedProperty;
    }


}
