package com.sparrowwallet.sparrow.io;

import com.sparrowwallet.drongo.crypto.ChildNumber;
import com.sparrowwallet.drongo.crypto.DeterministicKey;
import com.sparrowwallet.drongo.crypto.HDKeyDerivation;
import com.sparrowwallet.drongo.wallet.Keystore;
import com.sparrowwallet.drongo.wallet.MasterPrivateExtendedKey;
import com.sparrowwallet.drongo.wallet.MnemonicException;
import com.sparrowwallet.drongo.wallet.WalletModel;
import com.sparrowwallet.drongo.wallet.bip93.Codex32;

import java.util.List;

public class Bip93 implements KeystoreCodexImport {
    @Override
    public String getName() {
        return "Codex32 (BIP93)";
    }

    @Override
    public WalletModel getWalletModel() {
        return WalletModel.SEED;
    }

    @Override
    public String getKeystoreImportDescription() {
        return "Import your Codex32 secret share. Only a single share is currently supported.";
    }

    @Override
    public Keystore getKeystore(List<ChildNumber> derivation, String secretShare) throws ImportException {
        try {
            Codex32.Codex32Data secretData = Codex32.decode(secretShare);
            DeterministicKey key = HDKeyDerivation.createMasterPrivateKey(secretData.payloadToBip32Secret());
            MasterPrivateExtendedKey mpek = new MasterPrivateExtendedKey(key);
            Keystore keystore = Keystore.fromMasterPrivateExtendedKey(mpek, derivation);
            keystore.setLabel("BIP93");
            return keystore;
        } catch(MnemonicException e) {
            throw new ImportException(e);
        }
    }
}
