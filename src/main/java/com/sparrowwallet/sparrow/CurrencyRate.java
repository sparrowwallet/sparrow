package com.sparrowwallet.sparrow;

import java.util.Currency;

public class CurrencyRate {
    private final Currency currency;
    private final Double btcRate;

    public CurrencyRate(Currency currency, Double btcRate) {
        this.currency = currency;
        this.btcRate = btcRate;
    }

    public Currency getCurrency() {
        return currency;
    }

    public boolean isAvailable() {
        return btcRate != null && btcRate > 0.0;
    }

    public Double getBtcRate() {
        return btcRate;
    }
}
