package com.sparrowwallet.sparrow.whirlpool.dataSource;

import com.samourai.wallet.client.indexHandler.IIndexHandler;
import com.samourai.whirlpool.client.mix.handler.DestinationType;
import com.samourai.whirlpool.client.mix.handler.IPostmixHandler;
import com.samourai.whirlpool.client.mix.handler.MixDestination;
import com.samourai.whirlpool.client.utils.ClientUtils;
import com.samourai.whirlpool.client.wallet.WhirlpoolWalletService;
import com.samourai.whirlpool.client.wallet.beans.IndexRange;
import com.sparrowwallet.drongo.KeyPurpose;
import com.sparrowwallet.drongo.address.Address;
import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.drongo.wallet.WalletNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SparrowPostmixHandler implements IPostmixHandler {
    private static final Logger log = LoggerFactory.getLogger(SparrowPostmixHandler.class);

    private final WhirlpoolWalletService whirlpoolWalletService;
    private final Wallet wallet;
    private final KeyPurpose keyPurpose;

    protected MixDestination destination;

    public SparrowPostmixHandler(WhirlpoolWalletService whirlpoolWalletService, Wallet wallet, KeyPurpose keyPurpose) {
        this.whirlpoolWalletService = whirlpoolWalletService;
        this.wallet = wallet;
        this.keyPurpose = keyPurpose;
    }

    protected IndexRange getIndexRange() {
        return IndexRange.FULL;
    }

    public Wallet getWallet() {
        return wallet;
    }

    @Override
    public final MixDestination computeDestinationNext() throws Exception {
        // use "unconfirmed" index to avoid huge index gaps on multiple mix failures
        int index = ClientUtils.computeNextReceiveAddressIndex(getIndexHandler(), getIndexRange());
        this.destination = computeDestination(index);
        if (log.isDebugEnabled()) {
            log.debug(
                    "Mixing to "
                            + destination.getType()
                            + " -> receiveAddress="
                            + destination.getAddress()
                            + ", path="
                            + destination.getPath());
        }
        return destination;
    }

    @Override
    public MixDestination computeDestination(int index) throws Exception {
        // address
        WalletNode node = new WalletNode(wallet, keyPurpose, index);
        Address address = node.getAddress();
        String path = "xpub/" + keyPurpose.getPathIndex().num() + "/" + index;

        log.info("Mixing to external xPub -> receiveAddress=" + address + ", path=" + path);
        return new MixDestination(DestinationType.XPUB, index, address.toString(), path);
    }

    @Override
    public void onMixFail() {
        if(destination != null) {
            // cancel unconfirmed postmix index if output was not registered yet
            getIndexHandler().cancelUnconfirmed(destination.getIndex());
        }
    }

    @Override
    public void onRegisterOutput() {
        // confirm postmix index on REGISTER_OUTPUT success
        getIndexHandler().confirmUnconfirmed(destination.getIndex());
    }

    @Override
    public IIndexHandler getIndexHandler() {
        return whirlpoolWalletService.whirlpoolWallet().getWalletStateSupplier().getIndexHandlerExternal();
    }
}
