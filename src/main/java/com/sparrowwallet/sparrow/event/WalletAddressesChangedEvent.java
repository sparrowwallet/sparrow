package com.sparrowwallet.sparrow.event;

import com.sparrowwallet.drongo.wallet.Wallet;

import java.io.File;

/**
 * This event is posted when a wallet's addresses are changed (keystores, policy, script type).
 * This event marks a fundamental change that is used to update application level UI, clear node entry caches and similar. It should only be subscribed to by application-level classes.
 * Note that WalletForm does not listen to this event to save the wallet, since the wallet is foreground saved directly in SettingsController before this event is posted.
 * This is because any failure in saving the wallet must be immediately reported to the user.
 * Note that all wallet detail controllers that share a WalletForm, and that class posts WalletNodesChangedEvent once it has cleared it's entry caches.
 */
public class WalletAddressesChangedEvent extends WalletHistoryClearedEvent {
    public WalletAddressesChangedEvent(Wallet wallet, Wallet pastWallet, String walletId) {
        super(wallet, pastWallet, walletId);
    }
}
