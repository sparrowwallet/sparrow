package com.sparrowwallet.sparrow.event;

import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.sparrow.wallet.Function;
import com.sparrowwallet.sparrow.wallet.NodeEntry;

public class ReceiveActionEvent extends FunctionActionEvent {
    public ReceiveActionEvent(NodeEntry receiveEntry) {
        super(Function.RECEIVE, receiveEntry.getWallet());
    }

    public ReceiveActionEvent(Wallet wallet) {
        super(Function.RECEIVE, wallet);
    }
}
