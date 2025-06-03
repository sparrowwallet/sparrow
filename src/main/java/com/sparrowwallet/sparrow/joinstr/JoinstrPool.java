package com.sparrowwallet.sparrow.joinstr;

import com.sparrowwallet.drongo.address.Address;
import com.sparrowwallet.drongo.protocol.Transaction;
import com.sparrowwallet.drongo.protocol.TransactionOutput;
import com.sparrowwallet.drongo.psbt.PSBT;
import com.sparrowwallet.drongo.wallet.BlockTransactionHashIndex;
import com.sparrowwallet.drongo.wallet.BnBUtxoSelector;
import com.sparrowwallet.drongo.wallet.CoinbaseTxoFilter;
import com.sparrowwallet.drongo.wallet.FrozenTxoFilter;
import com.sparrowwallet.drongo.wallet.InsufficientFundsException;
import com.sparrowwallet.drongo.wallet.KnapsackUtxoSelector;
import com.sparrowwallet.drongo.wallet.Payment;
import com.sparrowwallet.drongo.wallet.SpentTxoFilter;
import com.sparrowwallet.drongo.wallet.TxoFilter;
import com.sparrowwallet.drongo.wallet.UtxoSelector;
import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.drongo.wallet.WalletNode;
import com.sparrowwallet.drongo.wallet.WalletTransaction;
import com.sparrowwallet.sparrow.AppServices;
import com.sparrowwallet.sparrow.io.Config;
import com.sparrowwallet.sparrow.net.Tor;

import java.util.*;

public class JoinstrPool {

    private final Integer port;     // Nostr Port ?
    private final String pubkey;
    private final long denomination;   // Should be in sats, like transaction amounts

    public JoinstrPool(Integer port, String pubkey, long denomination) {

        this.port = port;
        this.pubkey = pubkey;
        this.denomination = denomination;

    }

    private String getNostrRelay() {
        return Config.get().getNostrRelay();
    }

    public Integer getPort() {
        return port;
    }

    public String getPubkey() {
        return pubkey;
    }

    public long getDenomination() {
        return denomination;
    }

    public void getNewTorRoute() {

        Tor tor = Tor.getDefault();
        tor.changeIdentity();

    }

    public void publicNostrEvent() {
        // TODO: Publish a nostr event with pool info
    }

    public void waitForPeers() {
        // TODO: Wait for others to join
    }


}
