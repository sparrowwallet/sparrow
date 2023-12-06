package com.sparrowwallet.sparrow.io;

import com.sparrowwallet.drongo.wallet.WalletModel;

public class AirGapVault extends KeystoneSinglesig {
    @Override
    public String getName() {
        return "AirGap Vault";
    }

    @Override
    public String getKeystoreImportDescription(int account) {
        return "Import QR created on your AirGap Vault, by selecting the Secret > Account > Sparrow Wallet";
    }

    @Override
    public WalletModel getWalletModel() {
        return WalletModel.AIRGAP_VAULT;
    }

    @Override
    public boolean isFileFormatAvailable() {
        return false;
    }

    @Override
    public boolean isWalletImportFileFormatAvailable() {
        return false;
    }

    @Override
    public boolean isDeprecated() {
        return true;
    }
}
