package com.sparrowwallet.sparrow;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.text.DecimalFormat;
import java.util.Locale;

public class UnitFormatTest {
    @Test
    public void testFormatOutput() {
        Assertions.assertEquals("1,234,567", UnitFormat.DOT.formatSatsValue(1234567L));
        Assertions.assertEquals("1,234,567.89", UnitFormat.DOT.formatCurrencyValue(1234567.89));
        Assertions.assertEquals("1.234.567", UnitFormat.COMMA.formatSatsValue(1234567L));
        Assertions.assertEquals("1.234.567,89", UnitFormat.COMMA.formatCurrencyValue(1234567.89));
        Assertions.assertEquals("1 234 567", UnitFormat.SPACE.formatSatsValue(1234567L));
        Assertions.assertEquals("1 234 567,89", UnitFormat.SPACE.formatCurrencyValue(1234567.89));
        Assertions.assertEquals("0,12345678", UnitFormat.SPACE.formatBtcValue(12345678L));
    }

    @Test
    public void testFormatIgnoresDefaultLocale() {
        Locale defaultLocale = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("fa-IR"));
            Assertions.assertEquals("1 234 567,89", UnitFormat.SPACE.getCurrencyFormat().format(1234567.89));
            Assertions.assertEquals("1,234.5", new DecimalFormat("#,##0.##", UnitFormat.DOT.getDecimalFormatSymbols()).format(1234.5));
            Assertions.assertEquals("-1.234,50", UnitFormat.COMMA.formatDecimalValue(-1234.5, 2));
        } finally {
            Locale.setDefault(defaultLocale);
        }
    }

    @Test
    public void testFormatDecimalValue() {
        Assertions.assertEquals("0.13", UnitFormat.DOT.formatDecimalValue(0.125, 2));
        Assertions.assertEquals("1 234,6", UnitFormat.SPACE.formatDecimalValue(1234.56, 1));
        Assertions.assertEquals("0,125", UnitFormat.COMMA.formatDecimalValue(0.125, 0, 3));
        Assertions.assertEquals("8", UnitFormat.COMMA.formatDecimalValue(8.0, 0, 3));
    }

    @Test
    public void testNormaliseGroupingSeparators() {
        Assertions.assertEquals("1 234 567,89", UnitFormat.SPACE.normaliseGroupingSeparators("1 234 567,89"));
        Assertions.assertEquals("1 234,5", UnitFormat.SPACE.normaliseGroupingSeparators("1 234,5"));
        Assertions.assertEquals("1 234.5", UnitFormat.DOT.normaliseGroupingSeparators("1 234.5"));
        Assertions.assertEquals("1 234,5", UnitFormat.COMMA.normaliseGroupingSeparators("1 234,5"));
    }
}
