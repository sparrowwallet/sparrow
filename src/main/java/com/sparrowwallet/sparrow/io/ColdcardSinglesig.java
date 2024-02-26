package com.sparrowwallet.sparrow.io;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.reflect.TypeToken;
import com.sparrowwallet.drongo.ExtendedKey;
import com.sparrowwallet.drongo.KeyDerivation;
import com.sparrowwallet.drongo.policy.Policy;
import com.sparrowwallet.drongo.policy.PolicyType;
import com.sparrowwallet.drongo.protocol.ScriptType;
import com.sparrowwallet.drongo.wallet.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;

public class ColdcardSinglesig implements KeystoreFileImport, WalletImport {
    private static final Logger log = LoggerFactory.getLogger(ColdcardSinglesig.class);

    @Override
    public String getName() {
        return "Coldcard";
    }

    @Override
    public String getKeystoreImportDescription(int account) {
        return "Import file created by using the Advanced > MicroSD > Export Wallet > Generic JSON > " + account + " feature on your Coldcard. Note this requires firmware version 3.1.3 or later.";
    }

    @Override
    public WalletModel getWalletModel() {
        return WalletModel.COLDCARD;
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

    @Override
    public Keystore getKeystore(ScriptType scriptType, InputStream inputStream, String password) throws ImportException {
        try {
            Gson gson = new Gson();
            Type stringStringMap = new TypeToken<Map<String, JsonElement>>() {
            }.getType();
            Map<String, JsonElement> map = gson.fromJson(new InputStreamReader(inputStream, StandardCharsets.UTF_8), stringStringMap);

            if (map.get("xfp") == null) {
                throw new ImportException("File was not a valid " + getName() + " wallet export");
            }

            String masterFingerprint = map.get("xfp").getAsString();

            for (String key : map.keySet()) {
                if (key.startsWith("bip")) {
                    ColdcardKeystore ck = gson.fromJson(map.get(key), ColdcardKeystore.class);

                    if(ck.name != null) {
                        ScriptType ckScriptType = ScriptType.valueOf(ck.name.replace("p2wpkh-p2sh", "p2sh_p2wpkh").replace("p2sh-p2wpkh", "p2sh_p2wpkh").toUpperCase(Locale.ROOT));
                        if(ckScriptType.equals(scriptType)) {
                            Keystore keystore = new Keystore();
                            keystore.setLabel(getName());
                            keystore.setSource(KeystoreSource.HW_AIRGAPPED);
                            keystore.setWalletModel(WalletModel.COLDCARD);
                            keystore.setKeyDerivation(new KeyDerivation(masterFingerprint, ck.deriv));
                            keystore.setExtendedPublicKey(ExtendedKey.fromDescriptor(ck.xpub));

                            return keystore;
                        }
                    }
                }
            }
        } catch (Exception e) {
            throw new ImportException("Error getting " + getName() + " keystore", e);
        }

        throw new ImportException("Correct derivation not found for script type: " + scriptType);
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

    private static class ColdcardKeystore {
        public String deriv;
        public String name;
        public String xpub;
        public String xfp;
    }
}
