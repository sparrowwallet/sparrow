package com.sparrowwallet.sparrow.io;

import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.drongo.wallet.WalletModel;

public class JadeMultisig extends ColdcardMultisig {
    @Override
    public String getName() {
        return "Jade Multisig";
    }

    @Override
    public WalletModel getWalletModel() {
        return WalletModel.JADE;
    }

    @Override
    public String getWalletExportDescription() {
        return "Export a QR that allows Jade to import a multisig wallet using the Scan feature.";
    }

    @Override
    public String getExportFileExtension(Wallet wallet) {
        return null;
    }

    @Override
    public boolean isWalletExportScannable() {
        return true;
    }

    @Override
    public boolean isWalletExportFile() {
        return false;
    }

    @Override
    public boolean walletExportRequiresDecryption() {
        return false;
    }
}
