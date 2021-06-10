package com.sparrowwallet.sparrow.wallet;

import com.sparrowwallet.drongo.policy.Policy;
import com.sparrowwallet.drongo.wallet.Keystore;
import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.sparrow.AppServices;
import com.sparrowwallet.sparrow.EventManager;
import com.sparrowwallet.sparrow.event.KeystoreLabelsChangedEvent;
import com.sparrowwallet.sparrow.event.WalletAddressesChangedEvent;
import com.sparrowwallet.sparrow.event.WalletPasswordChangedEvent;
import com.sparrowwallet.sparrow.io.Storage;
import com.sparrowwallet.sparrow.io.StorageException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
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
    public void saveAndRefresh() throws IOException, StorageException {
        Wallet pastWallet = wallet.copy();

        if(isRefreshNecessary(wallet, walletCopy)) {
            boolean addressChange = isAddressChange();

            if(wallet.isValid()) {
                //Don't create temp backup on changing addresses - there are no labels to lose
                if(!addressChange) {
                    backgroundUpdate(); //Save existing wallet here for the temp backup in case password has been changed - this will update the password on the existing wallet
                    if(AppServices.isConnected()) {
                        //Backup the wallet so labels will survive application shutdown
                        getStorage().backupTempWallet();
                    }
                }

                //Clear transaction history cache before we clear the nodes
                AppServices.clearTransactionHistoryCache(wallet);
            }

            //Clear node tree
            walletCopy.clearNodes();

            Integer childIndex = wallet.isMasterWallet() ? null : wallet.getMasterWallet().getChildWallets().indexOf(wallet);

            //Replace the SettingsWalletForm wallet reference - note that this reference is only shared with the WalletForm wallet with WalletAddressesChangedEvent below
            wallet = walletCopy.copy();

            if(wallet.isMasterWallet()) {
                wallet.getChildWallets().forEach(childWallet -> childWallet.setMasterWallet(wallet));
            } else if(childIndex != null) {
                wallet.getMasterWallet().getChildWallets().set(childIndex, wallet);
            }

            save();

            EventManager.get().post(new WalletAddressesChangedEvent(wallet, addressChange ? null : pastWallet, getWalletId()));
        } else {
            List<Keystore> changedKeystores = new ArrayList<>();
            for(int i = 0; i < wallet.getKeystores().size(); i++) {
                Keystore keystore = wallet.getKeystores().get(i);
                Keystore keystoreCopy = walletCopy.getKeystores().get(i);
                if(!Objects.equals(keystore.getLabel(), keystoreCopy.getLabel())) {
                    keystore.setLabel(keystoreCopy.getLabel());
                    changedKeystores.add(keystore);
                }
            }

            if(!changedKeystores.isEmpty()) {
                EventManager.get().post(new KeystoreLabelsChangedEvent(wallet, pastWallet, getWalletId(), changedKeystores));
            } else {
                //Can only be a password update at this point
                EventManager.get().post(new WalletPasswordChangedEvent(wallet, pastWallet, getWalletId()));
            }
        }
    }

    //Returns true for any change, other than a keystore label change, to trigger a full wallet refresh
    //Even though this is not strictly necessary for some changes, it it better to refresh on saving so background transaction history updates on the old wallet have no effect/are not lost
    private boolean isRefreshNecessary(Wallet original, Wallet changed) {
        if(!original.isValid() || !changed.isValid()) {
            return true;
        }

        if(isAddressChange(original, changed)) {
            return true;
        }

        for(int i = 0; i < original.getKeystores().size(); i++) {
            Keystore originalKeystore = original.getKeystores().get(i);
            Keystore changedKeystore = changed.getKeystores().get(i);

            if(originalKeystore.getSource() != changedKeystore.getSource()) {
                return true;
            }

            if(originalKeystore.getWalletModel() != changedKeystore.getWalletModel()) {
                return true;
            }

            if((originalKeystore.getSeed() == null && changedKeystore.getSeed() != null) || (originalKeystore.getSeed() != null && changedKeystore.getSeed() == null)) {
                return true;
            }

            if((originalKeystore.getMasterPrivateExtendedKey() == null && changedKeystore.getMasterPrivateExtendedKey() != null) || (originalKeystore.getMasterPrivateExtendedKey() != null && changedKeystore.getMasterPrivateExtendedKey() == null)) {
                return true;
            }
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

        if(!Objects.equals(getNumSignaturesRequired(original.getDefaultPolicy()), getNumSignaturesRequired(changed.getDefaultPolicy()))) {
            return true;
        }

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

    private Integer getNumSignaturesRequired(Policy policy) {
        return policy == null ? null : policy.getNumSignaturesRequired();
    }
}
