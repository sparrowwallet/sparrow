package com.sparrowwallet.sparrow.control;

import com.sparrowwallet.drongo.BitcoinUnit;
import com.sparrowwallet.sparrow.UnitFormat;
import javafx.scene.chart.NumberAxis;
import javafx.util.StringConverter;

import java.text.ParseException;

final class CoinAxisFormatter extends StringConverter<Number> {
    private final UnitFormat unitFormat;
    private final BitcoinUnit bitcoinUnit;

    public CoinAxisFormatter(NumberAxis axis, UnitFormat format, BitcoinUnit unit) {
        this.unitFormat = format;
        this.bitcoinUnit = unit;
    }

    @Override
    public String toString(Number object) {
        Double value = bitcoinUnit.getValue(object.longValue());
        return new CoinTextFormatter(unitFormat).getCoinFormat().format(value);
    }

    @Override
    public Number fromString(String string) {
        try {
            Number number = new CoinTextFormatter(unitFormat).getCoinFormat().parse(string);
            return bitcoinUnit.getSatsValue(number.doubleValue());
        } catch (ParseException e) {
            throw new RuntimeException(e);
        }
    }
}
