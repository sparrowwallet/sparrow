package com.sparrowwallet.sparrow.io;

import com.sparrowwallet.drongo.wallet.WalletModel;

public class SeedSigner extends SpecterDIY {
    @Override
    public String getName() {
        return "SeedSigner";
    }

    @Override
    public String getKeystoreImportDescription() {
        return "Import QR created on your SeedSigner by selecting xPub from Seed in the Seed Tools menu once you have entered your seed.";
    }

    @Override
    public WalletModel getWalletModel() {
        return WalletModel.SEEDSIGNER;
    }
}
