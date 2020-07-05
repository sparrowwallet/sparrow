package com.sparrowwallet.sparrow;

import com.sparrowwallet.drongo.protocol.Transaction;

public enum BitcoinUnit {
    BTC("BTC") {
        @Override
        public long getSatsValue(double unitValue) {
            return (long)(unitValue * Transaction.SATOSHIS_PER_BITCOIN);
        }

        public double getValue(long satsValue) {
            return (double)satsValue / Transaction.SATOSHIS_PER_BITCOIN;
        }
    },
    SATOSHIS("sats") {
        @Override
        public long getSatsValue(double unitValue) {
            return (long)unitValue;
        }

        public double getValue(long satsValue) {
            return (double)satsValue;
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

    public abstract double getValue(long satsValue);

    public double convertFrom(double fromValue, BitcoinUnit fromUnit) {
        long satsValue = fromUnit.getSatsValue(fromValue);
        return getValue(satsValue);
    }

    @Override
    public String toString() {
        return label;
    }
}
