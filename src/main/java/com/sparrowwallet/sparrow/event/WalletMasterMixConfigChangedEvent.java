package com.sparrowwallet.sparrow.event;

import com.sparrowwallet.drongo.wallet.Wallet;

public class WalletMasterMixConfigChangedEvent extends WalletMixConfigChangedEvent {
    public WalletMasterMixConfigChangedEvent(Wallet wallet) {
        super(wallet.isMasterWallet() ? wallet : wallet.getMasterWallet());
    }
}
