package com.sparrowwallet.sparrow.whirlpool.dataPersister;

import com.samourai.wallet.util.AbstractOrchestrator;
import com.samourai.whirlpool.client.wallet.WhirlpoolWallet;
import com.samourai.whirlpool.client.wallet.WhirlpoolWalletConfig;
import com.samourai.whirlpool.client.wallet.data.dataPersister.DataPersister;
import com.samourai.whirlpool.client.wallet.data.utxoConfig.UtxoConfigPersistedSupplier;
import com.samourai.whirlpool.client.wallet.data.utxoConfig.UtxoConfigSupplier;
import com.samourai.whirlpool.client.wallet.data.walletState.WalletStateSupplier;
import com.sparrowwallet.sparrow.whirlpool.dataSource.SparrowWalletStateSupplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SparrowDataPersister implements DataPersister {
    private static final Logger log = LoggerFactory.getLogger(SparrowDataPersister.class);

    private final WalletStateSupplier walletStateSupplier;
    private final UtxoConfigSupplier utxoConfigSupplier;

    private AbstractOrchestrator persistOrchestrator;
    private final int persistDelaySeconds;

    public SparrowDataPersister(WhirlpoolWallet whirlpoolWallet, int persistDelaySeconds) throws Exception {
        WhirlpoolWalletConfig config = whirlpoolWallet.getConfig();
        String walletIdentifier = whirlpoolWallet.getWalletIdentifier();
        this.walletStateSupplier = new SparrowWalletStateSupplier(walletIdentifier, config);
        this.utxoConfigSupplier = new UtxoConfigPersistedSupplier(new SparrowUtxoConfigPersister(walletIdentifier));
        this.persistDelaySeconds = persistDelaySeconds;
    }

    @Override
    public void open() throws Exception {
        startPersistOrchestrator();
    }

    protected void startPersistOrchestrator() {
        persistOrchestrator = new AbstractOrchestrator(persistDelaySeconds * 1000) {
            @Override
            protected void runOrchestrator() {
                try {
                    persist(false);
                } catch (Exception e) {
                    log.error("Error persisting Whirlpool data", e);
                }
            }
        };

        persistOrchestrator.start(true);
    }

    @Override
    public void close() throws Exception {
        persistOrchestrator.stop();
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
