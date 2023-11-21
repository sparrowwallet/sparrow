package com.sparrowwallet.sparrow.wallet;

import com.sparrowwallet.drongo.policy.Policy;
import com.sparrowwallet.drongo.wallet.DeterministicSeed;
import com.sparrowwallet.drongo.wallet.Keystore;
import com.sparrowwallet.drongo.wallet.MasterPrivateExtendedKey;
import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.sparrow.AppServices;
import com.sparrowwallet.sparrow.EventManager;
import com.sparrowwallet.sparrow.event.*;
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
    private final WalletForm appWalletForm;

    public SettingsWalletForm(Storage storage, Wallet currentWallet, WalletForm appWalletForm) {
        super(storage, currentWallet);
        this.walletCopy = currentWallet.copy();
        this.walletCopy.setMasterWallet(walletCopy.isMasterWallet() ? null : walletCopy.getMasterWallet().copy());
        this.appWalletForm = appWalletForm;
    }

    @Override
    public Wallet getWallet() {
        return walletCopy;
    }

    @Override
    public void setWallet(Wallet wallet) {
        this.walletCopy = wallet;
    }

    public WalletForm getAppWalletForm() {
        return appWalletForm;
    }

    @Override
    public void revert() {
        this.walletCopy = super.getWallet().copy();
        this.walletCopy.setMasterWallet(walletCopy.isMasterWallet() ? null : walletCopy.getMasterWallet().copy());
    }

    @Override
    public void saveAndRefresh() throws IOException, StorageException {
        Wallet pastWallet = wallet.copy();

        if(isRefreshNecessary(wallet, walletCopy)) {
            boolean addressChange = isAddressChange();

            if(wallet.isValid()) {
                //Clear transaction history cache before we clear the nodes
                AppServices.clearTransactionHistoryCache(wallet);
            }

            //Clear node tree, detaching and saving any labels from the existing wallet
            walletCopy.clearNodes(wallet);

            //Retrieve master or child wallets from current active wallet before overwriting
            Wallet masterWallet = wallet.isMasterWallet() ? null : wallet.getMasterWallet();
            Integer childIndex = wallet.isMasterWallet() ? null : wallet.getMasterWallet().getChildWallets().indexOf(wallet);
            List<Wallet> childWallets = wallet.getChildWallets();

            //Replace the SettingsWalletForm wallet reference - note that this reference is only shared with the WalletForm wallet with WalletAddressesChangedEvent below
            wallet = walletCopy.copy();

            //Restore bidirectional links between original master or child wallets
            if(wallet.isMasterWallet()) {
                wallet.setChildWallets(childWallets);
                wallet.getChildWallets().forEach(childWallet -> childWallet.setMasterWallet(wallet));
            } else if(masterWallet != null && childIndex != null) {
                wallet.setMasterWallet(masterWallet);
                masterWallet.getChildWallets().set(childIndex, wallet);
            }

            save();

            EventManager.get().post(new WalletAddressesChangedEvent(wallet, addressChange ? null : pastWallet, getWalletId()));
        } else {
            List<Keystore> labelChangedKeystores = getLabelChangedKeystores(wallet, walletCopy);
            if(!labelChangedKeystores.isEmpty()) {
                EventManager.get().post(new KeystoreLabelsChangedEvent(wallet, pastWallet, getWalletId(), labelChangedKeystores));
            }

            if(!Objects.equals(wallet.getWatchLast(), walletCopy.getWatchLast())) {
                EventManager.get().post(new WalletWatchLastChangedEvent(wallet, pastWallet, getWalletId(), walletCopy.getWatchLast()));
            }

            Wallet masterWallet = getMasterWallet();
            Wallet masterWalletCopy = walletCopy.isMasterWallet() ? walletCopy : walletCopy.getMasterWallet();
            List<Keystore> encryptionChangedKeystores = getEncryptionChangedKeystores(masterWallet, masterWalletCopy);
            if(!encryptionChangedKeystores.isEmpty()) {
                EventManager.get().post(new KeystoreEncryptionChangedEvent(masterWallet, masterWallet.copy(), getStorage().getWalletId(masterWallet), encryptionChangedKeystores));
            }

            for(Wallet childWallet : masterWallet.getChildWallets()) {
                Wallet childWalletCopy = masterWalletCopy.getChildWallet(childWallet.getName());
                if(childWalletCopy != null) {
                    List<Keystore> childEncryptionChangedKeystores = getEncryptionChangedKeystores(childWallet, childWalletCopy);
                    if(!childEncryptionChangedKeystores.isEmpty()) {
                        EventManager.get().post(new KeystoreEncryptionChangedEvent(childWallet, childWallet.copy(), getStorage().getWalletId(childWallet), childEncryptionChangedKeystores));
                    }
                }
            }

            if(labelChangedKeystores.isEmpty() && encryptionChangedKeystores.isEmpty()) {
                //Can only be a wallet password change on a wallet without private keys
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

    private List<Keystore> getLabelChangedKeystores(Wallet original, Wallet changed) {
        List<Keystore> changedKeystores = new ArrayList<>();
        for(int i = 0; i < original.getKeystores().size(); i++) {
            Keystore originalKeystore = original.getKeystores().get(i);
            Keystore changedKeystore = changed.getKeystores().get(i);

            if(!Objects.equals(originalKeystore.getLabel(), changedKeystore.getLabel())) {
                originalKeystore.setLabel(changedKeystore.getLabel());
                changedKeystores.add(originalKeystore);
            }
        }

        return changedKeystores;
    }

    private List<Keystore> getEncryptionChangedKeystores(Wallet original, Wallet changed) {
        List<Keystore> changedKeystores = new ArrayList<>();
        for(int i = 0; i < original.getKeystores().size(); i++) {
            Keystore originalKeystore = original.getKeystores().get(i);
            Keystore changedKeystore = changed.getKeystores().get(i);

            if(originalKeystore.hasSeed() && changedKeystore.hasSeed()) {
                if(!Objects.equals(originalKeystore.getSeed().getEncryptedData(), changedKeystore.getSeed().getEncryptedData())) {
                    DeterministicSeed changedSeed = changedKeystore.getSeed().copy();
                    changedSeed.setId(originalKeystore.getSeed().getId());
                    originalKeystore.setSeed(changedSeed);
                    changedKeystores.add(originalKeystore);
                }
            }

            if(originalKeystore.hasMasterPrivateExtendedKey() && changedKeystore.hasMasterPrivateExtendedKey()) {
                if(!Objects.equals(originalKeystore.getMasterPrivateExtendedKey().getEncryptedData(), changedKeystore.getMasterPrivateExtendedKey().getEncryptedData())) {
                    MasterPrivateExtendedKey changedMpek = changedKeystore.getMasterPrivateExtendedKey().copy();
                    changedMpek.setId(originalKeystore.getMasterPrivateExtendedKey().getId());
                    originalKeystore.setMasterPrivateExtendedKey(changedMpek);
                    changedKeystores.add(originalKeystore);
                }
            }
        }

        return changedKeystores;
    }

    public void childWalletSaved(Wallet childWallet) {
        //Update the child wallets for the master wallet of the local walletCopy to ensure all KeystoreEncryptionChangedEvents are posted
        Wallet masterWalletCopy = walletCopy.isMasterWallet() ? walletCopy : walletCopy.getMasterWallet();
        Wallet childWalletCopy = masterWalletCopy.getChildWallet(childWallet.getName());
        if(childWalletCopy == null) {
            childWalletCopy = childWallet.copy();
            childWalletCopy.setMasterWallet(masterWalletCopy);
            masterWalletCopy.getChildWallets().add(childWallet.copy());
        }
    }

    public void gapLimitChanged(int gapLimit) {
        walletCopy.setGapLimit(gapLimit);
    }
}
