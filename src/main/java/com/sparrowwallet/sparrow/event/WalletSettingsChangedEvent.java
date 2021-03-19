package com.sparrowwallet.sparrow.event;

import com.sparrowwallet.drongo.wallet.Wallet;

import java.io.File;

/**
 * This event is posted when a wallet's settings are changed in a way that does not update the wallet addresses or history.
 * For example, changing the keystore source or label will trigger this event, which updates the wallet but avoids a full wallet refresh.
 * It is impossible for the validity of a wallet to change on this event - listen for WalletAddressesChangedEvent for this
 */
public class WalletSettingsChangedEvent extends WalletChangedEvent {
    private final Wallet pastWallet;
    private final File walletFile;

    public WalletSettingsChangedEvent(Wallet wallet, Wallet pastWallet, File walletFile) {
        super(wallet);
        this.pastWallet = pastWallet;
        this.walletFile = walletFile;
    }

    public Wallet getPastWallet() {
        return pastWallet;
    }

    public File getWalletFile() {
        return walletFile;
    }
}
