package com.sparrowwallet.sparrow.event;

import java.util.Currency;

public class ExchangeRatesUpdatedEvent {
    private final Currency selectedCurrency;
    private final Double rate;

    public ExchangeRatesUpdatedEvent(Currency selectedCurrency, Double rate) {
        this.selectedCurrency = selectedCurrency;
        this.rate = rate;
    }

    public Currency getSelectedCurrency() {
        return selectedCurrency;
    }

    public Double getRate() {
        return rate;
    }
}
