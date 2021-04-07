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
    public String getKeystoreImportDescription() {
        return "Import file or QR created from New Account > Sparrow > Multisig > QR Code/microSD on your Passport. For existing accounts, use Manage Account > Export by QR/microSD.";
    }

    @Override
    public boolean isKeystoreImportScannable() {
        return true;
    }

    @Override
    public String getWalletExportDescription() {
        return "As part of the New Account > Sparrow > Multisig flow, Passport will ask you to scan or import the multisig configuration from Sparrow.";
    }

    @Override
    public boolean isWalletExportScannable() {
        return true;
    }
}
