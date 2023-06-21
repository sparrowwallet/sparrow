package com.sparrowwallet.sparrow.io;

import com.sparrowwallet.drongo.wallet.WalletModel;

public class SeedSigner extends SpecterDIY {
    @Override
    public String getName() {
        return "SeedSigner";
    }

    @Override
    public String getKeystoreImportDescription(int account) {
        return "Import QR created on your SeedSigner by selecting Export Xpub in the Seeds menu once you have entered your seed.";
    }

    @Override
    public WalletModel getWalletModel() {
        return WalletModel.SEEDSIGNER;
    }

    @Override
    public boolean isFileFormatAvailable() {
        return false;
    }
}
