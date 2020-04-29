package com.sparrowwallet.sparrow.external;

import com.google.common.io.CharStreams;
import com.sparrowwallet.drongo.ExtendedPublicKey;
import com.sparrowwallet.drongo.KeyDerivation;
import com.sparrowwallet.drongo.Utils;
import com.sparrowwallet.drongo.policy.Policy;
import com.sparrowwallet.drongo.policy.PolicyType;
import com.sparrowwallet.drongo.protocol.ScriptType;
import com.sparrowwallet.drongo.wallet.Keystore;
import com.sparrowwallet.drongo.wallet.Wallet;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.List;

import static com.sparrowwallet.drongo.protocol.ScriptType.*;

public class ColdcardSinglesig implements SinglesigWalletImport {
    public static final List<ScriptType> ALLOWED_SCRIPT_TYPES = List.of(P2PKH, P2SH_P2WPKH, P2WPKH);

    @Override
    public String getName() {
        return "Coldcard";
    }

    @Override
    public Wallet importWallet(InputStream inputStream, ScriptType scriptType) throws ImportException {
        if(!ALLOWED_SCRIPT_TYPES.contains(scriptType)) {
            throw new ImportException("Script type of " + scriptType + " is not allowed");
        }

        Wallet wallet = new Wallet();
        wallet.setPolicyType(PolicyType.SINGLE);
        wallet.setScriptType(scriptType);
        String masterFingerprint = null;

        try {
            List<String> lines = CharStreams.readLines(new InputStreamReader(inputStream));

            for (String line : lines) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }

                if(line.startsWith("xpub")) {
                    ExtendedPublicKey masterXpub = ExtendedPublicKey.fromDescriptor(line);
                    masterFingerprint = Utils.bytesToHex(masterXpub.getPubKey().getFingerprint()).toUpperCase();
                    wallet.setName("Coldcard " + masterFingerprint);
                    continue;
                }

                String[] keyValue = line.split("=>");
                if(keyValue.length == 2) {
                    String key = keyValue[0].trim();
                    String value = keyValue[1].trim();

                    if(!key.equals("m") && scriptType.getDefaultDerivationPath().startsWith(key)) {
                        ExtendedPublicKey extPubKey = ExtendedPublicKey.fromDescriptor(value);
                        Keystore keystore = new Keystore();
                        keystore.setKeyDerivation(new KeyDerivation(masterFingerprint, key));
                        keystore.setExtendedPublicKey(extPubKey);
                        wallet.getKeystores().add(keystore);
                        break;
                    }
                }
            }

            wallet.setDefaultPolicy(Policy.getPolicy(PolicyType.SINGLE, scriptType, wallet.getKeystores(), 1));
            return wallet;
        } catch(Exception e) {
            throw new ImportException(e);
        }
    }

    @Override
    public String getWalletImportDescription() {
        return "Import file created by using the Advanced > Dump Summary feature on your Coldcard";
    }
}
