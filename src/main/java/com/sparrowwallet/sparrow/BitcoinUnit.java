package com.sparrowwallet.sparrow;

import com.sparrowwallet.drongo.protocol.Transaction;

public enum BitcoinUnit {
    BTC("BTC") {
        @Override
        public long getSatsValue(double unitValue) {
            return (long)(unitValue * Transaction.SATOSHIS_PER_BITCOIN);
        }
    },
    SATOSHIS("sats") {
        @Override
        public long getSatsValue(double unitValue) {
            return (long)unitValue;
        }
    };

    private final String label;

    BitcoinUnit(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    public abstract long getSatsValue(double unitValue);

    @Override
    public String toString() {
        return label;
    }
}
