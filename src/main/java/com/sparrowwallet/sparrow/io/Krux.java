package com.sparrowwallet.sparrow.io;

import com.sparrowwallet.drongo.wallet.WalletModel;

public class Krux extends SpecterDIY {
    @Override
    public String getName() {
        return "Krux";
    }

    @Override
    public String getKeystoreImportDescription(int account) {
        return "Import file or QR created on your Krux by selecting Extended Public Key from the main menu once you have loaded your mnemonic.";
    }

    @Override
    public WalletModel getWalletModel() {
        return WalletModel.KRUX;
    }
}
