package com.sparrowwallet.sparrow;

import com.sparrowwallet.drongo.protocol.Transaction;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

public enum UnitFormat {
    DOT {
        private final DecimalFormat btcFormat = new DecimalFormat("0", getDecimalFormatSymbols());
        private final DecimalFormat satsFormat = new DecimalFormat("#,##0", getDecimalFormatSymbols());
        private final DecimalFormat tableBtcFormat = new DecimalFormat("0.00000000", getDecimalFormatSymbols());
        private final DecimalFormat currencyFormat = new DecimalFormat("#,##0.00", getDecimalFormatSymbols());
        private final DecimalFormat tableCurrencyFormat = new DecimalFormat("0.00", getDecimalFormatSymbols());

        public DecimalFormat getBtcFormat() {
            btcFormat.setMaximumFractionDigits(8);
            return btcFormat;
        }

        public DecimalFormat getSatsFormat() {
            return satsFormat;
        }

        public DecimalFormat getTableBtcFormat() {
            return tableBtcFormat;
        }

        public DecimalFormat getCurrencyFormat() {
            return currencyFormat;
        }

        public DecimalFormat getTableCurrencyFormat() {
            return tableCurrencyFormat;
        }

        public DecimalFormatSymbols getDecimalFormatSymbols() {
            DecimalFormatSymbols symbols = new DecimalFormatSymbols();
            symbols.setDecimalSeparator('.');
            symbols.setGroupingSeparator(',');
            return symbols;
        }
    },
    COMMA {
        private final DecimalFormat btcFormat = new DecimalFormat("0", getDecimalFormatSymbols());
        private final DecimalFormat satsFormat = new DecimalFormat("#,##0", getDecimalFormatSymbols());
        private final DecimalFormat tableBtcFormat = new DecimalFormat("0.00000000", getDecimalFormatSymbols());
        private final DecimalFormat currencyFormat = new DecimalFormat("#,##0.00", getDecimalFormatSymbols());
        private final DecimalFormat tableCurrencyFormat = new DecimalFormat("0.00", getDecimalFormatSymbols());

        public DecimalFormat getBtcFormat() {
            btcFormat.setMaximumFractionDigits(8);
            return btcFormat;
        }

        public DecimalFormat getSatsFormat() {
            return satsFormat;
        }

        public DecimalFormat getTableBtcFormat() {
            return tableBtcFormat;
        }

        public DecimalFormat getCurrencyFormat() {
            return currencyFormat;
        }

        public DecimalFormat getTableCurrencyFormat() {
            return tableCurrencyFormat;
        }

        public DecimalFormatSymbols getDecimalFormatSymbols() {
            DecimalFormatSymbols symbols = new DecimalFormatSymbols();
            symbols.setDecimalSeparator(',');
            symbols.setGroupingSeparator('.');
            return symbols;
        }
    };

    public abstract DecimalFormatSymbols getDecimalFormatSymbols();

    public abstract DecimalFormat getBtcFormat();

    public abstract DecimalFormat getSatsFormat();

    public abstract DecimalFormat getTableBtcFormat();

    public abstract DecimalFormat getCurrencyFormat();

    public abstract DecimalFormat getTableCurrencyFormat();

    public String formatBtcValue(Long amount) {
        return getBtcFormat().format(amount.doubleValue() / Transaction.SATOSHIS_PER_BITCOIN);
    }

    public String tableFormatBtcValue(Long amount) {
        return getTableBtcFormat().format(amount.doubleValue() / Transaction.SATOSHIS_PER_BITCOIN);
    }

    public String formatSatsValue(Long amount) {
        return getSatsFormat().format(amount);
    }

    public String formatCurrencyValue(double amount) {
        return getCurrencyFormat().format(amount);
    }

    public String tableFormatCurrencyValue(double amount) {
        return getTableCurrencyFormat().format(amount);
    }

    public String getGroupingSeparator() {
        return Character.toString(getDecimalFormatSymbols().getGroupingSeparator());
    }

    public String getDecimalSeparator() {
        return Character.toString(getDecimalFormatSymbols().getDecimalSeparator());
    }
}
