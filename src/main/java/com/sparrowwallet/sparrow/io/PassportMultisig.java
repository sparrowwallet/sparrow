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
        return "Import file or QR created from Settings > Pair Software Wallet > Sparrow > Multisig > microSD/QR on your Passport.";
    }

    @Override
    public boolean isKeystoreImportScannable() {
        return true;
    }
}
