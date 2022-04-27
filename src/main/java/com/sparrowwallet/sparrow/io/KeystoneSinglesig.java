package com.sparrowwallet.sparrow.io;

import com.google.gson.Gson;
import com.sparrowwallet.drongo.ExtendedKey;
import com.sparrowwallet.drongo.KeyDerivation;
import com.sparrowwallet.drongo.OutputDescriptor;
import com.sparrowwallet.drongo.policy.Policy;
import com.sparrowwallet.drongo.policy.PolicyType;
import com.sparrowwallet.drongo.protocol.ScriptType;
import com.sparrowwallet.drongo.wallet.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;

public class KeystoneSinglesig implements KeystoreFileImport, WalletImport {
    private static final Logger log = LoggerFactory.getLogger(KeystoneSinglesig.class);

    @Override
    public String getName() {
        return "Keystone";
    }

    @Override
    public String getKeystoreImportDescription(int account) {
        return "Import file or QR created by using the My Keystone > ... > Export Wallet feature on your Keystone. Make sure to set the Watch-only Wallet to Sparrow in the Settings first.";
    }

    @Override
    public WalletModel getWalletModel() {
        return WalletModel.KEYSTONE;
    }

    @Override
    public Keystore getKeystore(ScriptType scriptType, InputStream inputStream, String password) throws ImportException {
        try {
            String outputDescriptor = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8)).lines().collect(Collectors.joining("\n"));
            OutputDescriptor descriptor = OutputDescriptor.getOutputDescriptor(outputDescriptor);

            if(descriptor.isMultisig()) {
                throw new IllegalArgumentException("Output descriptor describes a multisig wallet");
            }

            if(descriptor.getScriptType() != scriptType) {
                throw new IllegalArgumentException("Output descriptor describes a " + descriptor.getScriptType().getDescription() + " wallet");
            }

            ExtendedKey xpub = descriptor.getSingletonExtendedPublicKey();
            KeyDerivation keyDerivation = descriptor.getKeyDerivation(xpub);

            Keystore keystore = new Keystore();
            keystore.setLabel(getName());
            keystore.setSource(KeystoreSource.HW_AIRGAPPED);
            keystore.setWalletModel(WalletModel.KEYSTONE);
            keystore.setKeyDerivation(keyDerivation);
            keystore.setExtendedPublicKey(xpub);

            return keystore;
        } catch (IllegalArgumentException e) {
            throw new ImportException("Error getting " + getName() + " keystore - not an output descriptor", e);
        } catch (Exception e) {
            throw new ImportException("Error getting " + getName() + " keystore", e);
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

        try {
            wallet.checkWallet();
        } catch(InvalidWalletException e) {
            throw new ImportException("Imported " + getName() + " wallet was invalid: " + e.getMessage());
        }

        return wallet;
    }

    @Override
    public boolean isEncrypted(File file) {
        return false;
    }

    @Override
    public boolean isWalletImportScannable() {
        return true;
    }

    @Override
    public boolean isKeystoreImportScannable() {
        return true;
    }
}
