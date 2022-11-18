package com.sparrowwallet.sparrow.io;

import com.sparrowwallet.drongo.protocol.ScriptType;
import com.sparrowwallet.drongo.wallet.Keystore;
import com.sparrowwallet.drongo.wallet.WalletModel;

import java.io.InputStream;

public class PassportMultisig extends ColdcardMultisig {
    @Override
    public String getName() {
        return "Passport Multisig";
    }

    @Override
    public WalletModel getWalletModel() {
        return WalletModel.PASSPORT;
    }

    @Override
    public Keystore getKeystore(ScriptType scriptType, InputStream inputStream, String password) throws ImportException {
        Keystore keystore = super.getKeystore(scriptType, inputStream, password);
        keystore.setLabel("Passport");
        keystore.setWalletModel(getWalletModel());

        return keystore;
    }

    @Override
    public String getKeystoreImportDescription(int account) {
        return "Import file or QR created from Manage Account > Connect Wallet > Sparrow > Multisig > QR Code/microSD on your Passport.";
    }

    @Override
    public boolean isKeystoreImportScannable() {
        return true;
    }

    @Override
    public String getWalletExportDescription() {
        return "As part of the Multisig connection flow, Passport will ask you to scan or import the multisig configuration from Sparrow. To import this configuration manually, go to Settings > Bitcoin > Multisig > Import from QR Code/microSD.";
    }

    @Override
    public boolean isWalletExportScannable() {
        return true;
    }
}
