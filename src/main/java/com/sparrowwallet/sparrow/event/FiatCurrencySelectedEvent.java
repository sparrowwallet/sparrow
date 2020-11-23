package com.sparrowwallet.sparrow.event;

import com.sparrowwallet.sparrow.net.ExchangeSource;

import java.util.Currency;

public class FiatCurrencySelectedEvent {
    private final ExchangeSource exchangeSource;
    private final Currency currency;

    public FiatCurrencySelectedEvent(ExchangeSource exchangeSource, Currency currency) {
        this.exchangeSource = exchangeSource;
        this.currency = currency;
    }

    public ExchangeSource getExchangeSource() {
        return exchangeSource;
    }

    public Currency getCurrency() {
        return currency;
    }
}
