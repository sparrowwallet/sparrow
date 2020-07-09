package com.sparrowwallet.sparrow.control;

import com.sparrowwallet.drongo.BitcoinUnit;
import javafx.scene.chart.NumberAxis;
import javafx.util.StringConverter;

import java.text.ParseException;

final class CoinAxisFormatter extends StringConverter<Number> {
    private final BitcoinUnit bitcoinUnit;

    public CoinAxisFormatter(NumberAxis axis, BitcoinUnit unit) {
        this.bitcoinUnit = unit;
    }

    @Override
    public String toString(Number object) {
        Double value = bitcoinUnit.getValue(object.longValue());
        return CoinTextFormatter.COIN_FORMAT.format(value);
    }

    @Override
    public Number fromString(String string) {
        try {
            Number number = CoinTextFormatter.COIN_FORMAT.parse(string);
            return bitcoinUnit.getSatsValue(number.doubleValue());
        } catch (ParseException e) {
            throw new RuntimeException(e);
        }
    }
}
