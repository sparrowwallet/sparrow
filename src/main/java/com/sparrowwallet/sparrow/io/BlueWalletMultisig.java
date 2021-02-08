package com.sparrowwallet.sparrow.io;

import com.sparrowwallet.drongo.wallet.Keystore;
import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.drongo.wallet.WalletModel;

import java.io.InputStream;

public class BlueWalletMultisig extends ColdcardMultisig {
    @Override
    public String getName() {
        return "Blue Wallet Vault Multisig";
    }

    @Override
    public WalletModel getWalletModel() {
        return WalletModel.BLUE_WALLET;
    }

    @Override
    public Wallet importWallet(InputStream inputStream, String password) throws ImportException {
        Wallet wallet = super.importWallet(inputStream, password);
        for(Keystore keystore : wallet.getKeystores()) {
            keystore.setLabel(keystore.getLabel().replace("Coldcard", "Blue Wallet"));
            keystore.setWalletModel(WalletModel.BLUE_WALLET);
        }

        return wallet;
    }

    @Override
    public String getWalletImportDescription() {
        return "Import file or QR created by using the Wallet > Export Coordination Setup feature on your Blue Wallet Vault wallet.";
    }

    @Override
    public String getWalletExportDescription() {
        return "Export file that can be read by Blue Wallet using the Add Wallet > Vault > Import wallet feature.";
    }

    @Override
    public boolean isWalletImportScannable() {
        return true;
    }

    @Override
    public boolean isWalletExportScannable() {
        return true;
    }
}
