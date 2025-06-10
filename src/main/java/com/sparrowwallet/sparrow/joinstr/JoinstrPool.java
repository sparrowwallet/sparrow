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

import javafx.beans.property.SimpleStringProperty;

public class JoinstrPool {

    private final SimpleStringProperty relay;
    private final SimpleStringProperty pubkey;
    private final SimpleStringProperty denomination;
    private final SimpleStringProperty peers;
    private final SimpleStringProperty timeout;

    public JoinstrPool(String relay, String pubkey, String denomination,
                       String peers, String timeout) {
        this.relay = new SimpleStringProperty(relay);
        this.pubkey = new SimpleStringProperty(pubkey);
        this.denomination = new SimpleStringProperty(denomination);
        this.peers = new SimpleStringProperty(peers);
        this.timeout = new SimpleStringProperty(timeout);
    }

    public String getRelay() { return relay.get(); }
    public String getPubkey() { return pubkey.get(); }
    public String getDenomination() { return denomination.get(); }
    public String getPeers() { return peers.get(); }
    public String getTimeout() { return timeout.get(); }

}
