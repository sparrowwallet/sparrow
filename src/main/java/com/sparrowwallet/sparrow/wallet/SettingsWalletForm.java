package com.sparrowwallet.sparrow.wallet;

import com.sparrowwallet.drongo.wallet.Keystore;
import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.sparrow.EventManager;
import com.sparrowwallet.sparrow.event.WalletSettingsChangedEvent;
import com.sparrowwallet.sparrow.io.Storage;

import java.io.IOException;
import java.util.Objects;

/**
 * This class extends WalletForm to allow rollback of wallet changes. It is used exclusively by SettingsController for this purpose.
 * Note it should not be registered to listen for events - this will cause double wallet updates.
 */
public class SettingsWalletForm extends WalletForm {
    private Wallet walletCopy;

    public SettingsWalletForm(Storage storage, Wallet currentWallet) {
        super(storage, currentWallet, null, false);
        this.walletCopy = currentWallet.copy();
    }

    @Override
    public Wallet getWallet() {
        return walletCopy;
    }

    @Override
    public void setWallet(Wallet wallet) {
        this.walletCopy = wallet;
    }

    @Override
    public void revert() {
        this.walletCopy = super.getWallet().copy();
    }

    @Override
    public void saveAndRefresh() throws IOException {
        Wallet pastWallet = null;

        boolean refreshAll = isRefreshNecessary(wallet, walletCopy);
        if(refreshAll) {
            pastWallet = wallet.copy();
            save(); //Save here for the temp backup in case password has been changed
            getStorage().backupTempWallet();
            walletCopy.clearNodes();
        }

        wallet = walletCopy.copy();
        save();

        if(refreshAll) {
            EventManager.get().post(new WalletSettingsChangedEvent(wallet, pastWallet, getWalletFile()));
        }
    }

    private boolean isRefreshNecessary(Wallet original, Wallet changed) {
        if(!original.isValid() || !changed.isValid()) {
            return true;
        }

        if(isAddressChange(original, changed)) {
            return true;
        }

        if(original.getGapLimit() != changed.getGapLimit()) {
            return true;
        }

        if(!Objects.equals(original.getBirthDate(), changed.getBirthDate())) {
            return true;
        }

        return false;
    }

    protected boolean isAddressChange() {
        return isAddressChange(wallet, walletCopy);
    }

    private boolean isAddressChange(Wallet original, Wallet changed) {
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

            if(!Objects.equals(originalKeystore.getKeyDerivation(), changedKeystore.getKeyDerivation())) {
                return true;
            }

            if(!Objects.equals(originalKeystore.getExtendedPublicKey(), changedKeystore.getExtendedPublicKey())) {
                return true;
            }
        }

        return false;
    }
}
