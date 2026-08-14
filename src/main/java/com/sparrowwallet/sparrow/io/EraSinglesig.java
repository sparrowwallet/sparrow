package com.sparrowwallet.sparrow.io;

import com.sparrowwallet.drongo.policy.PolicyType;
import com.sparrowwallet.drongo.protocol.ScriptType;
import com.sparrowwallet.drongo.wallet.Keystore;
import com.sparrowwallet.drongo.wallet.WalletModel;

import java.io.InputStream;

public class EraSinglesig extends KeystoneSinglesig {
    @Override
    public String getName() {
        return "ERA Wallet";
    }

    @Override
    public String getKeystoreImportDescription(int account) {
        return "Import QR created on your ERA Wallet by opening your wallet and selecting Link > Sparrow from the wallet menu.";
    }

    @Override
    public WalletModel getWalletModel() {
        return WalletModel.ERA_WALLET;
    }

    @Override
    public Keystore getKeystore(PolicyType policyType, ScriptType scriptType, InputStream inputStream, String password) throws ImportException {
        Keystore keystore = super.getKeystore(policyType, scriptType, inputStream, password);
        keystore.setLabel("ERA Wallet");

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
