package com.sparrowwallet.sparrow.io;

import com.sparrowwallet.drongo.protocol.ScriptType;
import com.sparrowwallet.drongo.wallet.Keystore;
import com.sparrowwallet.drongo.wallet.WalletModel;

import java.io.InputStream;

public class PassportSinglesig extends ColdcardSinglesig {
    @Override
    public String getName() {
        return "Passport";
    }

    @Override
    public String getKeystoreImportDescription() {
        return "Import file or QR created from New Account > Sparrow > Standard > QR Code/microSD on your Passport. For existing accounts, use Manage Account > Export by QR/microSD.";
    }

    @Override
    public Keystore getKeystore(ScriptType scriptType, InputStream inputStream, String password) throws ImportException {
        Keystore keystore = super.getKeystore(scriptType, inputStream, password);
        keystore.setLabel("Passport");
        keystore.setWalletModel(getWalletModel());

        return keystore;
    }

    @Override
    public WalletModel getWalletModel() {
        return WalletModel.PASSPORT;
    }

    @Override
    public boolean isWalletImportScannable() {
        return true;
    }

    @Override
    public boolean isKeystoreImportScannable() {
        return true;
    }

    @Override
    public String getWalletImportDescription() {
        return getKeystoreImportDescription();
    }
}
