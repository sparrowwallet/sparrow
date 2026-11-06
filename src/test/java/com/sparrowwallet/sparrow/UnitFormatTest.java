package com.sparrowwallet.sparrow;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.text.DecimalFormatSymbols;

public class UnitFormatTest {
    
    @Test
    public void testDotFormatProperties() {
        UnitFormat format = UnitFormat.DOT;
        Assertions.assertEquals(".", format.getDecimalSeparator());
        Assertions.assertEquals(",", format.getGroupingSeparator());

        DecimalFormatSymbols symbols = format.getDecimalFormatSymbols();
        Assertions.assertEquals('.', symbols.getDecimalSeparator());
        Assertions.assertEquals(',', symbols.getGroupingSeparator());
    }

    @Test
    public void testDotFormatOutput() {
        UnitFormat format = UnitFormat.DOT;
        Assertions.assertEquals("1,234,567", format.formatSatsValue(1234567L));
        Assertions.assertEquals("1,234,567.89", format.formatCurrencyValue(1234567.89));
    }

    @Test
    public void testCommaFormatProperties() {
        UnitFormat format = UnitFormat.COMMA;
        Assertions.assertEquals(",", format.getDecimalSeparator());
        Assertions.assertEquals(".", format.getGroupingSeparator());

        DecimalFormatSymbols symbols = format.getDecimalFormatSymbols();
        Assertions.assertEquals(',', symbols.getDecimalSeparator());
        Assertions.assertEquals('.', symbols.getGroupingSeparator());
    }

    @Test
    public void testCommaFormatOutput() {
        UnitFormat format = UnitFormat.COMMA;
        Assertions.assertEquals("1.234.567", format.formatSatsValue(1234567L));
        Assertions.assertEquals("1.234.567,89", format.formatCurrencyValue(1234567.89));
    }

    @Test
    public void testSpaceFormatProperties() {
        UnitFormat format = UnitFormat.SPACE;
        Assertions.assertEquals(",", format.getDecimalSeparator());
        Assertions.assertEquals(" ", format.getGroupingSeparator());

        DecimalFormatSymbols symbols = format.getDecimalFormatSymbols();
        Assertions.assertEquals(',', symbols.getDecimalSeparator());
        Assertions.assertEquals(' ', symbols.getGroupingSeparator());
    }

    @Test
    public void testSpaceFormatOutput() {
        UnitFormat format = UnitFormat.SPACE;
        Assertions.assertEquals("1 234 567", format.formatSatsValue(1234567L));
        Assertions.assertEquals("1 234 567,89", format.formatCurrencyValue(1234567.89));
    }
}
