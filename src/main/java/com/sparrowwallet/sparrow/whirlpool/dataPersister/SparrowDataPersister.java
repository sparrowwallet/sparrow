package com.sparrowwallet.sparrow.whirlpool.dataPersister;

import com.samourai.whirlpool.client.wallet.WhirlpoolWallet;
import com.samourai.whirlpool.client.wallet.WhirlpoolWalletConfig;
import com.samourai.whirlpool.client.wallet.data.dataPersister.DataPersister;
import com.samourai.whirlpool.client.wallet.data.utxoConfig.UtxoConfigPersistedSupplier;
import com.samourai.whirlpool.client.wallet.data.utxoConfig.UtxoConfigSupplier;
import com.samourai.whirlpool.client.wallet.data.walletState.WalletStateSupplier;
import com.sparrowwallet.sparrow.whirlpool.dataSource.SparrowWalletStateSupplier;

public class SparrowDataPersister implements DataPersister {
    private final WalletStateSupplier walletStateSupplier;
    private final UtxoConfigSupplier utxoConfigSupplier;

    public SparrowDataPersister(WhirlpoolWallet whirlpoolWallet) throws Exception {
        WhirlpoolWalletConfig config = whirlpoolWallet.getConfig();
        String walletIdentifier = whirlpoolWallet.getWalletIdentifier();
        this.walletStateSupplier = new SparrowWalletStateSupplier(walletIdentifier, config);
        this.utxoConfigSupplier = new UtxoConfigPersistedSupplier(new SparrowUtxoConfigPersister(walletIdentifier));
    }

    @Override
    public void open() throws Exception {
    }

    @Override
    public void close() throws Exception {
    }

    @Override
    public void load() throws Exception {
        utxoConfigSupplier.load();
        walletStateSupplier.load();
    }

    @Override
    public void persist(boolean force) throws Exception {
        utxoConfigSupplier.persist(force);
        walletStateSupplier.persist(force);
    }

    @Override
    public WalletStateSupplier getWalletStateSupplier() {
        return walletStateSupplier;
    }

    @Override
    public UtxoConfigSupplier getUtxoConfigSupplier() {
        return utxoConfigSupplier;
    }
}
