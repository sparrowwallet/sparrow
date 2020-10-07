package com.sparrowwallet.sparrow.io;

import com.sparrowwallet.drongo.protocol.ScriptType;
import com.sparrowwallet.drongo.wallet.Keystore;
import com.sparrowwallet.drongo.wallet.WalletModel;

import java.io.InputStream;

public class CoboVaultSinglesig extends ColdcardSinglesig {
    @Override
    public String getName() {
        return "Cobo Vault";
    }

    @Override
    public String getKeystoreImportDescription() {
        return "Import file created by using the Watch-Only Wallet > Generic Wallet > Export Wallet feature on your Cobo Vault.";
    }

    @Override
    public WalletModel getWalletModel() {
        return WalletModel.COBO_VAULT;
    }

    @Override
    public Keystore getKeystore(ScriptType scriptType, InputStream inputStream, String password) throws ImportException {
        Keystore keystore = super.getKeystore(scriptType, inputStream, password);
        keystore.setLabel("Cobo Vault");
        keystore.setWalletModel(getWalletModel());

        return keystore;
    }
}
