package com.sparrowwallet.sparrow.io;

import com.sparrowwallet.drongo.wallet.WalletModel;

public class SpecterDIYMultisig extends SpecterDesktop {
    @Override
    public String getWalletImportDescription() {
        return "Import file or QR created by using the Multisig Wallet > Settings > Export Wallet Descriptor feature on your Specter DIY device.";
    }

    public String getName() {
        return "Specter DIY Multisig";
    }

    @Override
    public WalletModel getWalletModel() {
        return WalletModel.SPECTER_DIY;
    }
}
