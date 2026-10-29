package com.sparrowwallet.sparrow.event;

import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.sparrow.wallet.Function;

/**
 * Selects a wallet function from View > Go To or its shortcut, and moves focus to the function's button in the wallet's menu
 */
public class FunctionShortcutEvent extends FunctionActionEvent {
    public FunctionShortcutEvent(Function function, Wallet wallet) {
        super(function, wallet);
    }

    @Override
    public boolean selectFunction() {
        return false;
    }
}
