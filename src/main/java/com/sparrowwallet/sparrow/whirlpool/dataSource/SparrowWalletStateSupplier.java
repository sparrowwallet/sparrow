package com.sparrowwallet.sparrow.whirlpool.dataSource;

import com.samourai.wallet.client.indexHandler.IIndexHandler;
import com.samourai.wallet.hd.AddressType;
import com.samourai.wallet.hd.Chain;
import com.samourai.whirlpool.client.wallet.beans.ExternalDestination;
import com.samourai.whirlpool.client.wallet.beans.WhirlpoolAccount;
import com.samourai.whirlpool.client.wallet.data.walletState.WalletStateSupplier;
import com.sparrowwallet.drongo.KeyPurpose;
import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.drongo.wallet.WalletNode;
import com.sparrowwallet.sparrow.whirlpool.Whirlpool;

import java.util.LinkedHashMap;
import java.util.Map;

public class SparrowWalletStateSupplier implements WalletStateSupplier {
    private final String walletId;
    private final Map<String, IIndexHandler> indexHandlerWallets;
    // private int externalIndexDefault;

    public SparrowWalletStateSupplier(String walletId, ExternalDestination externalDestination) throws Exception {
        this.walletId = walletId;
        this.indexHandlerWallets = new LinkedHashMap<>();
        // this.externalIndexDefault = externalDestination != null ? externalDestination.getStartIndex() : 0;
    }

    @Override
    public IIndexHandler getIndexHandlerWallet(WhirlpoolAccount whirlpoolAccount, AddressType addressType, Chain chain) {
        String key = mapKey(whirlpoolAccount, addressType, chain);
        IIndexHandler indexHandler = indexHandlerWallets.get(key);
        if (indexHandler == null) {
            WalletNode walletNode = findWalletNode(whirlpoolAccount, addressType, chain);
            indexHandler = new SparrowIndexHandler(walletNode, 0);
            indexHandlerWallets.put(key, indexHandler);
        }
        return indexHandler;
    }

    @Override
    public IIndexHandler getIndexHandlerExternal() {
        throw new UnsupportedOperationException();
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
    public void setWalletIndex(WhirlpoolAccount whirlpoolAccount, AddressType addressType, Chain chain, int i) throws Exception {
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

    private String mapKey(WhirlpoolAccount whirlpoolAccount, AddressType addressType, Chain chain) {
        return whirlpoolAccount.name()+"_"+addressType.getPurpose()+"_"+chain.getIndex();
    }

    private WalletNode findWalletNode(WhirlpoolAccount whirlpoolAccount, AddressType addressType, Chain chain) {
        Wallet wallet = getWallet();
        if(wallet == null) {
            throw new IllegalStateException("Can't find wallet with walletId " + walletId);
        }

        // account
        Wallet childWallet = Whirlpool.getStandardAccountWallet(whirlpoolAccount, wallet);

        // purpose
        KeyPurpose keyPurpose = chain == Chain.RECEIVE ? KeyPurpose.RECEIVE : KeyPurpose.CHANGE;
        return childWallet.getNode(keyPurpose);
    }

    private Wallet getWallet() {
        return Whirlpool.getWallet(walletId);
    }
}
