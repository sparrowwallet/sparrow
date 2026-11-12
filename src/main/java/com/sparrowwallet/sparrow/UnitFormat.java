package com.sparrowwallet.sparrow;

import com.sparrowwallet.drongo.protocol.Transaction;

import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

public enum UnitFormat {
    DOT('.', ','),
    COMMA(',', '.'),
    SPACE(',', ' ');

    private final char decimalSeparator;
    private final char groupingSeparator;

    //DecimalFormat is not thread safe, and amounts are formatted on background threads (such as exports) as well as in UI table cells
    private final ThreadLocal<Formats> formats = ThreadLocal.withInitial(() -> new Formats(getDecimalFormatSymbols()));

    UnitFormat(char decimalSeparator, char groupingSeparator) {
        this.decimalSeparator = decimalSeparator;
        this.groupingSeparator = groupingSeparator;
    }

    public DecimalFormatSymbols getDecimalFormatSymbols() {
        //Some system locales write numbers with their own digits, which amount fields could neither show consistently nor read back
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.ROOT);
        symbols.setDecimalSeparator(decimalSeparator);
        symbols.setGroupingSeparator(groupingSeparator);
        return symbols;
    }

    public DecimalFormat getBtcFormat() {
        return formats.get().btcFormat;
    }

    public DecimalFormat getSatsFormat() {
        return formats.get().satsFormat;
    }

    public DecimalFormat getTableBtcFormat() {
        return formats.get().tableBtcFormat;
    }

    public DecimalFormat getCurrencyFormat() {
        return formats.get().currencyFormat;
    }

    public DecimalFormat getTableCurrencyFormat() {
        return formats.get().tableCurrencyFormat;
    }

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

    public String formatDecimalValue(double value, int fractionDigits) {
        return formatDecimalValue(value, fractionDigits, fractionDigits);
    }

    public String formatDecimalValue(double value, int minimumFractionDigits, int maximumFractionDigits) {
        DecimalFormat decimalFormat = new DecimalFormat("#,##0", getDecimalFormatSymbols());
        decimalFormat.setMinimumFractionDigits(minimumFractionDigits);
        decimalFormat.setMaximumFractionDigits(maximumFractionDigits);
        decimalFormat.setRoundingMode(RoundingMode.HALF_UP);
        return decimalFormat.format(value);
    }

    public String getGroupingSeparator() {
        return Character.toString(groupingSeparator);
    }

    public String getDecimalSeparator() {
        return Character.toString(decimalSeparator);
    }

    //Locales that group digits with a space mostly write it as a no-break, narrow no-break or thin space, so an amount copied from a spreadsheet or bank reads the same
    public String normaliseGroupingSeparators(String text) {
        return groupingSeparator == ' ' && text != null ? text.replaceAll("[\\u00A0\\u2007\\u2009\\u202F]", " ") : text;
    }

    private static class Formats {
        private final DecimalFormat btcFormat;
        private final DecimalFormat satsFormat;
        private final DecimalFormat tableBtcFormat;
        private final DecimalFormat currencyFormat;
        private final DecimalFormat tableCurrencyFormat;

        private Formats(DecimalFormatSymbols symbols) {
            btcFormat = new DecimalFormat("0", symbols);
            btcFormat.setMaximumFractionDigits(8);
            satsFormat = new DecimalFormat("#,##0", symbols);
            tableBtcFormat = new DecimalFormat("0.00000000", symbols);
            currencyFormat = new DecimalFormat("#,##0.00", symbols);
            tableCurrencyFormat = new DecimalFormat("0.00", symbols);
        }
    }
}
