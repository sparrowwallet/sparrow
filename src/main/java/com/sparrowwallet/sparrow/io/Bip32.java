package com.sparrowwallet.sparrow.io;

import com.sparrowwallet.drongo.ExtendedKey;
import com.sparrowwallet.drongo.crypto.ChildNumber;
import com.sparrowwallet.drongo.wallet.Keystore;
import com.sparrowwallet.drongo.wallet.MasterPrivateExtendedKey;
import com.sparrowwallet.drongo.wallet.WalletModel;

import java.util.List;

public class Bip32 implements KeystoreXprvImport {
    @Override
    public String getName() {
        return "Master Private Key (BIP32)";
    }

    @Override
    public WalletModel getWalletModel() {
        return WalletModel.SEED;
    }

    @Override
    public String getKeystoreImportDescription() {
        return "Import an extended master private key (BIP 32 xprv)";
    }

    @Override
    public Keystore getKeystore(List<ChildNumber> derivation, ExtendedKey xprv) throws ImportException {
        try {
            MasterPrivateExtendedKey masterPrivateExtendedKey = new MasterPrivateExtendedKey(xprv.getKey().getPrivKeyBytes(), xprv.getKey().getChainCode());
            return Keystore.fromMasterPrivateExtendedKey(masterPrivateExtendedKey, derivation);
        } catch(Exception e) {
            throw new ImportException(e);
        }
    }
}
