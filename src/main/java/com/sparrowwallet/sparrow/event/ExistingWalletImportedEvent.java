package com.sparrowwallet.sparrow.event;

import com.sparrowwallet.drongo.wallet.Wallet;

public class ExistingWalletImportedEvent {
    private final String existingWalletId;
    private final Wallet importedWallet;

    public ExistingWalletImportedEvent(String existingWalletId, Wallet importedWallet) {
        this.existingWalletId = existingWalletId;
        this.importedWallet = importedWallet;
    }

    public String getExistingWalletId() {
        return existingWalletId;
    }

    public Wallet getImportedWallet() {
        return importedWallet;
    }
}
