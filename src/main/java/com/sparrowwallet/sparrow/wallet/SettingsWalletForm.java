package com.sparrowwallet.sparrow.wallet;

import com.sparrowwallet.drongo.wallet.Keystore;
import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.sparrow.EventManager;
import com.sparrowwallet.sparrow.event.WalletSettingsChangedEvent;
import com.sparrowwallet.sparrow.io.Storage;

import java.io.IOException;

/**
 * This class extends WalletForm to allow rollback of wallet changes. It is used exclusively by SettingsController for this purpose.
 * Note it should not be registered to listen for events - this will cause double wallet updates.
 */
public class SettingsWalletForm extends WalletForm {
    private Wallet walletCopy;

    public SettingsWalletForm(Storage storage, Wallet currentWallet) {
        super(storage, currentWallet);
        this.walletCopy = currentWallet.copy();
    }

    @Override
    public Wallet getWallet() {
        return walletCopy;
    }

    @Override
    public void revert() {
        this.walletCopy = super.getWallet().copy();
    }

    @Override
    public void saveAndRefresh() throws IOException {
        boolean refreshAll = changesScriptHashes(wallet, walletCopy);
        if(refreshAll) {
            walletCopy.clearNodes();
        }

        wallet = walletCopy.copy();
        save();

        if(refreshAll) {
            EventManager.get().post(new WalletSettingsChangedEvent(wallet, getWalletFile()));
        }
    }

    private boolean changesScriptHashes(Wallet original, Wallet changed) {
        if(!original.isValid() || !changed.isValid()) {
            return true;
        }

        if(original.getPolicyType() != changed.getPolicyType()) {
            return true;
        }

        if(original.getScriptType() != changed.getScriptType()) {
            return true;
        }

        //TODO: Determine if Miniscript has changed for custom policies

        if(original.getKeystores().size() != changed.getKeystores().size()) {
            return true;
        }

        for(int i = 0; i < original.getKeystores().size(); i++) {
            Keystore originalKeystore = original.getKeystores().get(i);
            Keystore changedKeystore = changed.getKeystores().get(i);

            if(!originalKeystore.getKeyDerivation().equals(changedKeystore.getKeyDerivation())) {
                return true;
            }

            if(!originalKeystore.getExtendedPublicKey().equals(changedKeystore.getExtendedPublicKey())) {
                return true;
            }
        }

        return false;
    }
}
