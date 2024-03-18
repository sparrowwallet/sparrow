package com.sparrowwallet.sparrow.whirlpool.dataSource;

import com.samourai.whirlpool.client.mix.handler.AbstractPostmixHandler;
import com.samourai.whirlpool.client.mix.handler.DestinationType;
import com.samourai.whirlpool.client.mix.handler.MixDestination;
import com.samourai.whirlpool.client.wallet.WhirlpoolWalletService;
import com.samourai.whirlpool.client.wallet.beans.IndexRange;
import com.sparrowwallet.drongo.KeyPurpose;
import com.sparrowwallet.drongo.address.Address;
import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.drongo.wallet.WalletNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// TODO maybe replace with XPubPostmixHandler
public class SparrowPostmixHandler extends AbstractPostmixHandler {
    private static final Logger log = LoggerFactory.getLogger(SparrowPostmixHandler.class);

    private final Wallet wallet;
    private final KeyPurpose keyPurpose;

    public SparrowPostmixHandler(WhirlpoolWalletService whirlpoolWalletService, Wallet wallet, KeyPurpose keyPurpose) {
        super(whirlpoolWalletService.whirlpoolWallet().getWalletStateSupplier().getIndexHandlerExternal(),
                whirlpoolWalletService.whirlpoolWallet().getConfig().getSamouraiNetwork().getParams());
        this.wallet = wallet;
        this.keyPurpose = keyPurpose;
    }

    @Override
    protected IndexRange getIndexRange() {
        return IndexRange.FULL;
    }

    public Wallet getWallet() {
        return wallet;
    }

    @Override
    public MixDestination computeDestination(int index) throws Exception {
        // address
        WalletNode node = new WalletNode(wallet, keyPurpose, index);
        Address address = node.getAddress();
        String path = "xpub/"+keyPurpose.getPathIndex().num()+"/"+index;

        log.info("Mixing to external xPub -> receiveAddress=" + address + ", path=" + path);
        return new MixDestination(DestinationType.XPUB, index, address.toString(), path);
    }
}
