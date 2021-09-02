package com.sparrowwallet.sparrow.whirlpool.dataSource;

import com.samourai.wallet.client.indexHandler.IIndexHandler;
import com.samourai.wallet.util.XPubUtil;
import com.samourai.whirlpool.client.mix.handler.DestinationType;
import com.samourai.whirlpool.client.mix.handler.IPostmixHandler;
import com.samourai.whirlpool.client.mix.handler.MixDestination;
import com.samourai.whirlpool.client.wallet.WhirlpoolWalletService;
import com.sparrowwallet.drongo.KeyPurpose;
import com.sparrowwallet.drongo.address.Address;
import com.sparrowwallet.drongo.wallet.Wallet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SparrowPostmixHandler implements IPostmixHandler {
    private static final Logger log = LoggerFactory.getLogger(SparrowPostmixHandler.class);

    private final WhirlpoolWalletService whirlpoolWalletService;
    private final Wallet wallet;
    private final KeyPurpose keyPurpose;
    private final int startIndex;

    protected MixDestination destination;

    public SparrowPostmixHandler(WhirlpoolWalletService whirlpoolWalletService, Wallet wallet, KeyPurpose keyPurpose, int startIndex) {
        this.whirlpoolWalletService = whirlpoolWalletService;
        this.wallet = wallet;
        this.keyPurpose = keyPurpose;
        this.startIndex = startIndex;
    }

    public Wallet getWallet() {
        return wallet;
    }

    protected MixDestination computeNextDestination() throws Exception {
        // index
        int index = Math.max(getIndexHandler().getAndIncrementUnconfirmed(), startIndex);

        // address
        Address address = wallet.getAddress(keyPurpose, index);
        String path = XPubUtil.getInstance().getPath(index, keyPurpose.getPathIndex().num());

        log.info("Mixing to external xPub -> receiveAddress=" + address + ", path=" + path);
        return new MixDestination(DestinationType.XPUB, index, address.toString(), path);
    }

    @Override
    public MixDestination getDestination() {
        return destination; // may be NULL
    }

    public final MixDestination computeDestination() throws Exception {
        // use "unconfirmed" index to avoid huge index gaps on multiple mix failures
        this.destination = computeNextDestination();
        return destination;
    }

    @Override
    public void onMixFail() {
        if(destination != null) {
            getIndexHandler().cancelUnconfirmed(destination.getIndex());
        }
    }

    @Override
    public void onRegisterOutput() {
        // confirm receive address even when REGISTER_OUTPUT fails, to avoid 'ouput already registered'
        getIndexHandler().confirmUnconfirmed(destination.getIndex());
    }

    private IIndexHandler getIndexHandler() {
        return whirlpoolWalletService.whirlpoolWallet().getWalletStateSupplier().getIndexHandlerExternal();
    }
}
