package com.sparrowwallet.sparrow;

import com.sparrowwallet.drongo.protocol.Transaction;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

public enum UnitFormat {
    DOT {
        private final DecimalFormat btcFormat = new DecimalFormat("0", DecimalFormatSymbols.getInstance(getLocale()));
        private final DecimalFormat tableBtcFormat = new DecimalFormat("0.00000000", DecimalFormatSymbols.getInstance(getLocale()));
        private final DecimalFormat currencyFormat = new DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(getLocale()));

        public DecimalFormat getBtcFormat() {
            btcFormat.setMaximumFractionDigits(8);
            return btcFormat;
        }

        public DecimalFormat getTableBtcFormat() {
            return tableBtcFormat;
        }

        public DecimalFormat getCurrencyFormat() {
            return currencyFormat;
        }

        public Locale getLocale() {
            return Locale.ENGLISH;
        }
    },
    COMMA {
        private final DecimalFormat btcFormat = new DecimalFormat("0", DecimalFormatSymbols.getInstance(getLocale()));
        private final DecimalFormat tableBtcFormat = new DecimalFormat("0.00000000", DecimalFormatSymbols.getInstance(getLocale()));
        private final DecimalFormat currencyFormat = new DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(getLocale()));

        public DecimalFormat getBtcFormat() {
            btcFormat.setMaximumFractionDigits(8);
            return btcFormat;
        }

        public DecimalFormat getTableBtcFormat() {
            return tableBtcFormat;
        }

        public DecimalFormat getCurrencyFormat() {
            return currencyFormat;
        }

        public Locale getLocale() {
            return Locale.GERMAN;
        }
    };

    public abstract Locale getLocale();

    public abstract DecimalFormat getBtcFormat();

    public abstract DecimalFormat getTableBtcFormat();

    public abstract DecimalFormat getCurrencyFormat();

    public String formatBtcValue(Long amount) {
        return getBtcFormat().format(amount.doubleValue() / Transaction.SATOSHIS_PER_BITCOIN);
    }

    public String formatSatsValue(Long amount) {
        return String.format(getLocale(), "%,d", amount);
    }

    public String formatCurrencyValue(double amount) {
        return getCurrencyFormat().format(amount);
    }

    public DecimalFormatSymbols getDecimalFormatSymbols() {
        return DecimalFormatSymbols.getInstance(getLocale());
    }

    public String getGroupingSeparator() {
        return Character.toString(getDecimalFormatSymbols().getGroupingSeparator());
    }

    public String getDecimalSeparator() {
        return Character.toString(getDecimalFormatSymbols().getDecimalSeparator());
    }
}
