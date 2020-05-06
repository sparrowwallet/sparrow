package com.sparrowwallet.sparrow.io;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.reflect.TypeToken;
import com.sparrowwallet.drongo.ExtendedKey;
import com.sparrowwallet.drongo.KeyDerivation;
import com.sparrowwallet.drongo.protocol.ScriptType;
import com.sparrowwallet.drongo.wallet.Keystore;
import com.sparrowwallet.drongo.wallet.KeystoreSource;
import com.sparrowwallet.drongo.wallet.WalletModel;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.util.Map;

public class ColdcardSinglesig implements KeystoreFileImport {
    @Override
    public String getName() {
        return "Coldcard";
    }

    @Override
    public String getKeystoreImportDescription() {
        return "Import file created by using the Advanced > MicroSD > Export Wallet > Generic JSON feature on your Coldcard";
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
    public Keystore getKeystore(ScriptType scriptType, InputStream inputStream, String password) throws ImportException {
        try {
            Gson gson = new Gson();
            Type stringStringMap = new TypeToken<Map<String, JsonElement>>() {
            }.getType();
            Map<String, JsonElement> map = gson.fromJson(new InputStreamReader(inputStream), stringStringMap);

            if (map.get("xfp") == null) {
                throw new ImportException("This is not a valid Coldcard wallet export");
            }

            String masterFingerprint = map.get("xfp").getAsString();

            for (String key : map.keySet()) {
                if (key.startsWith("bip")) {
                    ColdcardKeystore ck = gson.fromJson(map.get(key), ColdcardKeystore.class);

                    if(ck.name != null) {
                        ScriptType ckScriptType = ScriptType.valueOf(ck.name.replace("p2wpkh-p2sh", "p2sh_p2wpkh").toUpperCase());
                        if(ckScriptType.equals(scriptType)) {
                            Keystore keystore = new Keystore();
                            keystore.setLabel("Coldcard " + masterFingerprint.toUpperCase());
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
            throw new ImportException(e);
        }

        throw new ImportException("Correct derivation not found for script type: " + scriptType);
    }

    private static class ColdcardKeystore {
        public String deriv;
        public String name;
        public String xpub;
        public String xfp;
    }
}
