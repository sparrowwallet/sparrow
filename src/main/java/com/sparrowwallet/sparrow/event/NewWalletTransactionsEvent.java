package com.sparrowwallet.sparrow.event;

import com.sparrowwallet.drongo.BitcoinUnit;
import com.sparrowwallet.drongo.protocol.Transaction;
import com.sparrowwallet.drongo.wallet.BlockTransaction;
import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.sparrow.control.CoinLabel;
import com.sparrowwallet.sparrow.io.Config;

import java.util.List;
import java.util.Locale;

public class NewWalletTransactionsEvent {
    private final Wallet wallet;
    private final List<BlockTransaction> blockTransactions;
    private final long totalBlockchainValue;
    private final long totalMempoolValue;

    public NewWalletTransactionsEvent(Wallet wallet, List<BlockTransaction> blockTransactions, long totalBlockchainValue, long totalMempoolValue) {
        this.wallet = wallet;
        this.blockTransactions = blockTransactions;
        this.totalBlockchainValue = totalBlockchainValue;
        this.totalMempoolValue = totalMempoolValue;
    }

    public Wallet getWallet() {
        return wallet;
    }

    public List<BlockTransaction> getBlockTransactions() {
        return blockTransactions;
    }

    public long getTotalValue() {
        return totalBlockchainValue + totalMempoolValue;
    }

    public long getTotalBlockchainValue() {
        return totalBlockchainValue;
    }

    public long getTotalMempoolValue() {
        return totalMempoolValue;
    }

    public String getValueAsText(long value) {
        BitcoinUnit unit = Config.get().getBitcoinUnit();
        if(unit == null || unit.equals(BitcoinUnit.AUTO)) {
            unit = (value >= BitcoinUnit.getAutoThreshold() ? BitcoinUnit.BTC : BitcoinUnit.SATOSHIS);
        }

        if(unit == BitcoinUnit.BTC) {
            return CoinLabel.getBTCFormat().format((double) value / Transaction.SATOSHIS_PER_BITCOIN) + " BTC";
        }

        return String.format(Locale.ENGLISH, "%,d", value) + " sats";
    }
}
