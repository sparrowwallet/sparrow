package com.sparrowwallet.sparrow.whirlpool.dataSource;

import com.google.common.eventbus.Subscribe;
import com.samourai.wallet.api.backend.beans.WalletResponse;
import com.samourai.wallet.chain.ChainSupplier;
import com.sparrowwallet.drongo.protocol.Sha256Hash;
import com.sparrowwallet.sparrow.AppServices;
import com.sparrowwallet.sparrow.EventManager;
import com.sparrowwallet.sparrow.event.NewBlockEvent;

public class SparrowChainSupplier implements ChainSupplier {
    private int storedBlockHeight;
    private WalletResponse.InfoBlock latestBlock;

    public SparrowChainSupplier(Integer storedBlockHeight) {
        this.storedBlockHeight = AppServices.getCurrentBlockHeight() == null ?
                (storedBlockHeight!=null?storedBlockHeight:0)
                : AppServices.getCurrentBlockHeight();
        this.latestBlock = computeLatestBlock();
        EventManager.get().register(this);
    }

    public void close() {
        EventManager.get().unregister(this);
    }

    private WalletResponse.InfoBlock computeLatestBlock() {
        WalletResponse.InfoBlock latestBlock = new WalletResponse.InfoBlock();
        latestBlock.height = AppServices.getCurrentBlockHeight() == null ? storedBlockHeight : AppServices.getCurrentBlockHeight();
        latestBlock.hash = Sha256Hash.ZERO_HASH.toString();
        latestBlock.time = AppServices.getLatestBlockHeader() == null ? 1 : AppServices.getLatestBlockHeader().getTime();
        return latestBlock;
    }

    @Override
    public WalletResponse.InfoBlock getLatestBlock() {
        return latestBlock;
    }

    @Subscribe
    public void newBlock(NewBlockEvent event) {
        this.latestBlock = computeLatestBlock();
    }
}
