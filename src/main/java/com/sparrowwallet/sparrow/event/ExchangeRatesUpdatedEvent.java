package com.sparrowwallet.sparrow.event;

import com.sparrowwallet.sparrow.CurrencyRate;

import java.util.Currency;

public class ExchangeRatesUpdatedEvent {
    private final Currency currency;
    private final Double btcRate;

    public ExchangeRatesUpdatedEvent(Currency currency, Double btcRate) {
        this.currency = currency;
        this.btcRate = btcRate;
    }

    public Currency getCurrency() {
        return currency;
    }

    public Double getBtcRate() {
        return btcRate;
    }

    public CurrencyRate getCurrencyRate() {
        return new CurrencyRate(currency, btcRate);
    }
}
