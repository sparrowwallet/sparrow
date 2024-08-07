package com.sparrowwallet.sparrow.io;

import com.sparrowwallet.drongo.crypto.ChildNumber;
import com.sparrowwallet.drongo.wallet.*;
import com.sparrowwallet.drongo.wallet.slip39.RecoveryState;
import com.sparrowwallet.drongo.wallet.slip39.Share;

import java.nio.charset.StandardCharsets;
import java.util.List;

public class Slip39 implements KeystoreMnemonicShareImport {
    @Override
    public String getName() {
        return "Mnemonic Shares (SLIP39)";
    }

    @Override
    public WalletModel getWalletModel() {
        return WalletModel.SEED;
    }

    @Override
    public String getKeystoreImportDescription() {
        return "Import your 20 or 33 word mnemonic shares and optional passphrase.";
    }

    @Override
    public Keystore getKeystore(List<ChildNumber> derivation, List<List<String>> mnemonicShares, String passphrase) throws ImportException {
        try {
            RecoveryState recoveryState = new RecoveryState();
            for(List<String> mnemonicWords : mnemonicShares) {
                Share share = Share.fromMnemonic(String.join(" ", mnemonicWords));
                recoveryState.addShare(share);
            }

            if(recoveryState.isComplete()) {
                byte[] secret = recoveryState.recover(passphrase.getBytes(StandardCharsets.UTF_8));
                DeterministicSeed seed = new DeterministicSeed(secret, passphrase, System.currentTimeMillis(), DeterministicSeed.Type.SLIP39);
                return Keystore.fromSeed(seed, derivation);
            } else {
                throw new Slip39ProgressException(recoveryState.getShortStatus(), recoveryState.getStatus());
            }
        } catch(MnemonicException e) {
            throw new ImportException(e);
        }
    }

    public static class Slip39ProgressException extends ImportException {
        private final String title;

        public Slip39ProgressException(String title, String message) {
            super(message);
            this.title = title;
        }

        public String getTitle() {
            return title;
        }
    }
}
