package com.sparrowwallet.sparrow.io.ckcard;

import com.sparrowwallet.drongo.wallet.WalletModel;

public class Satschip extends Tapsigner {
    @Override
    public String getKeystoreImportDescription(int account) {
        return "Import the keystore from your Satschip by placing it on the card reader.";
    }

    @Override
    public String getName() {
        return "Satschip";
    }

    @Override
    public WalletModel getWalletModel() {
        return WalletModel.SATSCHIP;
    }
}
