package com.sparrowwallet.sparrow.io;

import com.google.common.io.CharStreams;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;
import com.sparrowwallet.drongo.Utils;
import com.sparrowwallet.drongo.crypto.SamouraiUtil;
import com.sparrowwallet.drongo.protocol.ScriptType;
import com.sparrowwallet.drongo.wallet.*;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@SuppressWarnings("deprecation")
public class Samourai implements KeystoreFileImport {
    @Override
    public String getKeystoreImportDescription(int account) {
        return "Import the wallet backup file samourai.txt exported from the Samourai app. Note that see the full balance, several script types may need to imported in separate wallets.";
    }

    @Override
    public Keystore getKeystore(ScriptType scriptType, InputStream inputStream, String password) throws ImportException {
        try {
            String input = CharStreams.toString(new InputStreamReader(inputStream, StandardCharsets.UTF_8));

            Map<String, JsonElement> map = this.parseJsonInput(input);

            String payload = input;
            if(map.containsKey("payload")) {
                payload = map.get("payload").getAsString();
            }

            int version = 1;
            if(map.containsKey("version")) {
                version = map.get("version").getAsInt();
            }

            String decrypted;
            if(version == 1) {
                decrypted = SamouraiUtil.decrypt(payload, password, SamouraiUtil.DefaultPBKDF2Iterations);
            } else if(version == 2) {
                decrypted = SamouraiUtil.decryptSHA256(payload, password);
            } else {
                throw new ImportException("Unsupported backup version: " + version);
            }

            SamouraiBackup backup = new Gson().fromJson(decrypted, SamouraiBackup.class);
            DeterministicSeed seed = new DeterministicSeed(Utils.hexToBytes(backup.wallet.seed), password, 0);
            Keystore keystore = Keystore.fromSeed(seed, scriptType.getDefaultDerivation());
            keystore.setLabel(getWalletModel().toDisplayString());
            return keystore;
        } catch(JsonParseException e) {
            throw new ImportException("Failed to decrypt the wallet backup file, check if the password is correct.");
        } catch(ImportException e) {
            throw e;
        } catch(Exception e) {
            throw new ImportException("Error importing backup", e);
        }
    }

    private Map<String, JsonElement> parseJsonInput(String input) {
        Gson gson = new Gson();
        Type stringStringMap = new TypeToken<Map<String, JsonElement>>() {
        }.getType();

        try {
            return gson.fromJson(input, stringStringMap);
        } catch (JsonParseException e) {
            int closingBracket = input.indexOf('}');
            String fixedInput = input.substring(0, closingBracket + 1);
            return gson.fromJson(fixedInput, stringStringMap);
        }
    }

    @Override
    public boolean isKeystoreImportScannable() {
        return false;
    }

    @Override
    public boolean isEncrypted(File file) {
        return true;
    }

    @Override
    public String getName() {
        return "Samourai Backup";
    }

    @Override
    public WalletModel getWalletModel() {
        return WalletModel.SAMOURAI;
    }

    private static class SamouraiBackup {
        public SamouraiWallet wallet;
    }

    private static class SamouraiWallet {
        public boolean testnet;
        public String seed;
        public String fingerprint;
    }
}
