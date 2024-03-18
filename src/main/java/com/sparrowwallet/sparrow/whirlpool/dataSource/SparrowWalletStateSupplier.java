package com.sparrowwallet.sparrow.whirlpool.dataSource;

import com.samourai.wallet.bipWallet.BipDerivation;
import com.samourai.wallet.bipWallet.BipWallet;
import com.samourai.wallet.client.indexHandler.IIndexHandler;
import com.samourai.wallet.constants.SamouraiAccount;
import com.samourai.wallet.hd.Chain;
import com.samourai.whirlpool.client.wallet.beans.ExternalDestination;
import com.samourai.whirlpool.client.wallet.data.walletState.WalletStateSupplier;
import com.samourai.whirlpool.client.whirlpool.WhirlpoolClientConfig;
import com.sparrowwallet.drongo.KeyPurpose;
import com.sparrowwallet.drongo.crypto.ChildNumber;
import com.sparrowwallet.drongo.wallet.MixConfig;
import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.drongo.wallet.WalletNode;
import com.sparrowwallet.sparrow.whirlpool.Whirlpool;

import java.util.LinkedHashMap;
import java.util.Map;

public class SparrowWalletStateSupplier implements WalletStateSupplier {
    private final String walletId;
    private final Map<String, IIndexHandler> indexHandlerWallets;
    private final WhirlpoolClientConfig config;
    private IIndexHandler externalIndexHandler;

    public SparrowWalletStateSupplier(String walletId, WhirlpoolClientConfig config) {
        this.walletId = walletId;
        this.indexHandlerWallets = new LinkedHashMap<>();
        this.config = config;
    }

    @Override
    public IIndexHandler getIndexHandlerWallet(BipWallet bipWallet, Chain chain) {
        SamouraiAccount samouraiAccount = bipWallet.getAccount();
        String key = mapKey(bipWallet, chain);
        IIndexHandler indexHandler = indexHandlerWallets.get(key);
        if (indexHandler == null) {
            Wallet wallet = findWallet(samouraiAccount);
            KeyPurpose keyPurpose = (chain == Chain.RECEIVE ? KeyPurpose.RECEIVE : KeyPurpose.CHANGE);
            WalletNode walletNode = wallet.getNode(keyPurpose);

            //Ensure mix config is present
            MixConfig mixConfig = wallet.getMixConfig();
            if(mixConfig == null) {
                mixConfig = new MixConfig();
                wallet.setMixConfig(mixConfig);
            }

            indexHandler = new SparrowIndexHandler(wallet, walletNode, 0);
            indexHandlerWallets.put(key, indexHandler);
        }

        return indexHandler;
    }

    @Override
    public IIndexHandler getIndexHandlerExternal() {
        ExternalDestination externalDestination = config.getExternalDestination();
        if(externalDestination == null) {
            throw new IllegalStateException("External destination has not been set");
        }

        if(externalIndexHandler == null) {
            Wallet externalWallet = null;
            if(externalDestination.getPostmixHandlerCustom() != null
                    && externalDestination.getPostmixHandlerCustom() instanceof SparrowPostmixHandler sparrowPostmixHandler) {
                externalWallet = sparrowPostmixHandler.getWallet();
            } else if(externalDestination.getXpub() != null) {
                externalWallet = SparrowDataSource.getWallet(externalDestination.getXpub());
            }

            if(externalWallet == null) {
                throw new IllegalStateException("Cannot find wallet for external destination " + externalDestination);
            }

            //Ensure mix config is present to save indexes
            MixConfig mixConfig = externalWallet.getMixConfig();
            if(mixConfig == null) {
                mixConfig = new MixConfig();
                externalWallet.setMixConfig(mixConfig);
            }

            KeyPurpose keyPurpose = KeyPurpose.fromChildNumber(new ChildNumber(externalDestination.getChain()));
            WalletNode externalNode = externalWallet.getNode(keyPurpose);
            externalIndexHandler = new SparrowIndexHandler(externalWallet, externalNode);
        }

        return externalIndexHandler;
    }

    @Override
    public boolean isInitialized() {
        return true;
    }

    @Override
    public void setInitialized(boolean b) {
        // nothing required
    }

    @Override
    public boolean isNymClaimed() {
        return false; // nothing required
    }

    @Override
    public void setNymClaimed(boolean value) {
        // nothing required
    }

    @Override
    public void load() throws Exception {
        // nothing required
    }

    @Override
    public boolean persist(boolean b) throws Exception {
        // nothing required
        return false;
    }

    private String mapKey(BipWallet bipWallet, Chain chain) {
        SamouraiAccount samouraiAccount = bipWallet.getAccount();
        BipDerivation derivation = bipWallet.getDerivation();
        return samouraiAccount.name()+"_"+derivation.getPurpose()+"_"+chain.getIndex();
    }

    private Wallet findWallet(SamouraiAccount samouraiAccount) {
        Wallet wallet = getWallet();
        if(wallet == null) {
            throw new IllegalStateException("Can't find wallet with walletId " + walletId);
        }

        return Whirlpool.getStandardAccountWallet(samouraiAccount, wallet);
    }

    private Wallet getWallet() {
        return Whirlpool.getWallet(walletId);
    }
}
