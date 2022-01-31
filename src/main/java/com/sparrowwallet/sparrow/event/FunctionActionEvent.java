package com.sparrowwallet.sparrow.event;

import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.sparrow.wallet.Function;

public class FunctionActionEvent {
    private final Function function;
    private final Wallet wallet;

    public FunctionActionEvent(Function function, Wallet wallet) {
        this.function = function;
        this.wallet = wallet;
    }

    public Function getFunction() {
        return function;
    }

    public Wallet getWallet() {
        return wallet;
    }

    public boolean selectFunction() {
        return true;
    }
}
