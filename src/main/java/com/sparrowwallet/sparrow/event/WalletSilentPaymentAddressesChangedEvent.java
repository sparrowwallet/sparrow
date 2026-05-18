package com.sparrowwallet.sparrow.event;

import com.sparrowwallet.drongo.wallet.Wallet;

public class WalletSilentPaymentAddressesChangedEvent extends WalletChangedEvent {
    public WalletSilentPaymentAddressesChangedEvent(Wallet wallet) {
        super(wallet.resolveMasterWallet());
    }
}
