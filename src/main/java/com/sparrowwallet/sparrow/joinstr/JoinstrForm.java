package com.sparrowwallet.sparrow.joinstr;

import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.sparrow.io.Storage;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;

public class JoinstrForm {

    private final BooleanProperty lockedProperty = new SimpleBooleanProperty(false);

    private final Storage storage;
    protected Wallet wallet;

    public JoinstrForm(Storage storage, Wallet currentWallet) {
        this.storage = storage;
        this.wallet = currentWallet;
    }

    public Wallet getWallet() {
        return wallet;
    }

    public Storage getStorage() {
        return storage;
    }

    public BooleanProperty lockedProperty() {
        return lockedProperty;
    }


}
