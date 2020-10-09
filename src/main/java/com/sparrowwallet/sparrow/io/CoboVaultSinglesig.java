package com.sparrowwallet.sparrow.io;

import com.google.gson.Gson;
import com.sparrowwallet.drongo.ExtendedKey;
import com.sparrowwallet.drongo.KeyDerivation;
import com.sparrowwallet.drongo.policy.Policy;
import com.sparrowwallet.drongo.policy.PolicyType;
import com.sparrowwallet.drongo.protocol.ScriptType;
import com.sparrowwallet.drongo.wallet.Keystore;
import com.sparrowwallet.drongo.wallet.KeystoreSource;
import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.drongo.wallet.WalletModel;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;

public class CoboVaultSinglesig implements KeystoreFileImport, WalletImport {
    @Override
    public String getName() {
        return "Cobo Vault";
    }

    @Override
    public String getKeystoreImportDescription() {
        return "Import file or QR created by using the Watch-Only Wallet > Generic Wallet > Export Wallet feature on your Cobo Vault.";
    }

    @Override
    public WalletModel getWalletModel() {
        return WalletModel.COBO_VAULT;
    }

    @Override
    public Keystore getKeystore(ScriptType scriptType, InputStream inputStream, String password) throws ImportException {
        try {
            Gson gson = new Gson();
            CoboVaultSinglesigKeystore coboKeystore = gson.fromJson(new InputStreamReader(inputStream), CoboVaultSinglesigKeystore.class);

            if(coboKeystore.MasterFingerprint == null || coboKeystore.AccountKeyPath == null || coboKeystore.ExtPubKey == null) {
                throw new ImportException("Not a valid " + getName() + " keystore export");
            }

            Keystore keystore = new Keystore();
            keystore.setLabel(getName());
            keystore.setSource(KeystoreSource.HW_AIRGAPPED);
            keystore.setWalletModel(WalletModel.COBO_VAULT);
            keystore.setKeyDerivation(new KeyDerivation(coboKeystore.MasterFingerprint.toLowerCase(), "m/" + coboKeystore.AccountKeyPath));
            keystore.setExtendedPublicKey(ExtendedKey.fromDescriptor(coboKeystore.ExtPubKey));

            ExtendedKey.Header header = ExtendedKey.Header.fromExtendedKey(coboKeystore.ExtPubKey);
            if(header.getDefaultScriptType() != scriptType) {
                throw new ImportException("This wallet's script type (" + scriptType + ") does not match the " + getName() + " script type (" + header.getDefaultScriptType() + ")");
            }

            return keystore;
        } catch (Exception e) {
            throw new ImportException(e);
        }
    }

    @Override
    public String getWalletImportDescription() {
        return getKeystoreImportDescription();
    }

    @Override
    public Wallet importWallet(InputStream inputStream, String password) throws ImportException {
        //Use default of P2WPKH
        Keystore keystore = getKeystore(ScriptType.P2WPKH, inputStream, "");

        Wallet wallet = new Wallet();
        wallet.setPolicyType(PolicyType.SINGLE);
        wallet.setScriptType(ScriptType.P2WPKH);
        wallet.getKeystores().add(keystore);
        wallet.setDefaultPolicy(Policy.getPolicy(PolicyType.SINGLE, ScriptType.P2WPKH, wallet.getKeystores(), null));

        if(!wallet.isValid()) {
            throw new ImportException("Wallet is in an inconsistent state.");
        }

        return wallet;
    }

    @Override
    public boolean isEncrypted(File file) {
        return false;
    }

    @Override
    public boolean isScannable() {
        return true;
    }

    private static class CoboVaultSinglesigKeystore {
        public String ExtPubKey;
        public String MasterFingerprint;
        public String AccountKeyPath;
    }
}
