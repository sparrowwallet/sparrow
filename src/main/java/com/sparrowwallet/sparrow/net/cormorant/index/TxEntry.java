package com.sparrowwallet.sparrow.net.cormorant.index;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.sparrowwallet.drongo.protocol.Transaction;

import java.util.Objects;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class TxEntry implements Comparable<TxEntry> {
    public final int height;
    private final transient int index;
    public final String tx_hash;
    public final Long fee;

    public TxEntry(int height, int index, String tx_hash) {
        this.height = height;
        this.index = index;
        this.tx_hash = tx_hash;
        this.fee = null;
    }

    public TxEntry(int height, int index, String tx_hash, double btcFee) {
        this.height = height;
        this.index = index;
        this.tx_hash = tx_hash;
        this.fee = btcFee > 0.0 ? (long)(btcFee * Transaction.SATOSHIS_PER_BITCOIN) : null;
    }

    @Override
    public boolean equals(Object o) {
        if(this == o) {
            return true;
        }
        if(!(o instanceof TxEntry txEntry)) {
            return false;
        }

        return height == txEntry.height && tx_hash.equals(txEntry.tx_hash) && Objects.equals(fee, txEntry.fee);
    }

    @Override
    public int hashCode() {
        int result = height;
        result = 31 * result + tx_hash.hashCode();
        result = 31 * result + Objects.hashCode(fee);
        return result;
    }

    @Override
    public int compareTo(TxEntry o) {
        if(height <= 0 && o.height > 0) {
            return 1;
        }

        if(height > 0 && o.height <= 0) {
            return -1;
        }

        if(height != o.height) {
            return height - o.height;
        }

        if(height <= 0) {
            return tx_hash.compareTo(o.tx_hash);
        }

        return index - o.index;
    }

    @Override
    public String toString() {
        return "TxEntry{" +
                "height=" + height +
                ", index=" + index +
                ", tx_hash='" + tx_hash + '\'' +
                ", fee=" + fee +
                '}';
    }
}
