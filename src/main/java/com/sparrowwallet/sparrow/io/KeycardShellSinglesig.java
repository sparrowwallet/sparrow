package com.sparrowwallet.sparrow.io;

import com.sparrowwallet.drongo.protocol.ScriptType;
import com.sparrowwallet.drongo.wallet.Keystore;
import com.sparrowwallet.drongo.wallet.WalletModel;

import java.io.InputStream;

public class KeycardShellSinglesig extends KeystoneSinglesig {
    @Override
    public String getName() {
        return "Keycard Shell";
    }

    @Override
    public String getKeystoreImportDescription(int account) {
        return "Import QR created on your Keycard Shell, by selecting Connect software wallet > Bitcoin";
    }

    @Override
    public WalletModel getWalletModel() {
        return WalletModel.KEYCARD_SHELL;
    }

    @Override
    public Keystore getKeystore(ScriptType scriptType, InputStream inputStream, String password) throws ImportException {
        Keystore keystore = super.getKeystore(scriptType, inputStream, password);
        keystore.setLabel("Keycard Shell");
        keystore.setWalletModel(getWalletModel());

        return keystore;
    }

    @Override
    public boolean isFileFormatAvailable() {
        return false;
    }

    @Override
    public boolean isWalletImportFileFormatAvailable() {
        return false;
    }
}
