package com.sparrowwallet.sparrow.preferences;

import com.sparrowwallet.drongo.BitcoinUnit;
import com.sparrowwallet.sparrow.EventManager;
import com.sparrowwallet.sparrow.event.BitcoinUnitChangedEvent;
import com.sparrowwallet.sparrow.event.FiatCurrencySelectedEvent;
import com.sparrowwallet.sparrow.io.Config;
import com.sparrowwallet.sparrow.io.ExchangeSource;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;

import java.util.Currency;
import java.util.List;

public class GeneralPreferencesController extends PreferencesDetailController {
    @FXML
    private ComboBox<BitcoinUnit> bitcoinUnit;

    @FXML
    private ComboBox<Currency> fiatCurrency;

    @FXML
    private ComboBox<ExchangeSource> exchangeSource;

    private final ChangeListener<Currency> fiatCurrencyListener = new ChangeListener<Currency>() {
        @Override
        public void changed(ObservableValue<? extends Currency> observable, Currency oldValue, Currency newValue) {
            if (newValue != null) {
                Config.get().setFiatCurrency(newValue);
                EventManager.get().post(new FiatCurrencySelectedEvent(exchangeSource.getValue(), newValue));
            }
        }
    };

    @Override
    public void initializeView(Config config) {
        if(config.getBitcoinUnit() != null) {
            bitcoinUnit.setValue(config.getBitcoinUnit());
        }

        bitcoinUnit.valueProperty().addListener((observable, oldValue, newValue) -> {
            config.setBitcoinUnit(newValue);
            EventManager.get().post(new BitcoinUnitChangedEvent(newValue));
        });

        if(config.getExchangeSource() != null) {
            exchangeSource.setValue(config.getExchangeSource());
        } else {
            exchangeSource.getSelectionModel().select(2);
            config.setExchangeSource(exchangeSource.getValue());
        }

        exchangeSource.valueProperty().addListener((observable, oldValue, source) -> {
            config.setExchangeSource(source);
            updateCurrencies(source);
        });

        updateCurrencies(exchangeSource.getSelectionModel().getSelectedItem());
    }

    private void updateCurrencies(ExchangeSource exchangeSource) {
        ExchangeSource.CurrenciesService currenciesService = new ExchangeSource.CurrenciesService(exchangeSource);
        currenciesService.setOnSucceeded(event -> {
            updateCurrencies(currenciesService.getValue());
        });
        currenciesService.start();
    }

    private void updateCurrencies(List<Currency> currencies) {
        fiatCurrency.valueProperty().removeListener(fiatCurrencyListener);

        fiatCurrency.getItems().clear();
        fiatCurrency.getItems().addAll(currencies);

        Currency configCurrency = Config.get().getFiatCurrency();
        if(configCurrency != null && currencies.contains(configCurrency)) {
            fiatCurrency.setDisable(false);
            fiatCurrency.setValue(configCurrency);
        } else if(!currencies.isEmpty()) {
            fiatCurrency.setDisable(false);
            fiatCurrency.getSelectionModel().select(0);
            Config.get().setFiatCurrency(fiatCurrency.getValue());
        } else {
            fiatCurrency.setDisable(true);
        }

        //Always fire event regardless of previous selection to update rates
        EventManager.get().post(new FiatCurrencySelectedEvent(exchangeSource.getValue(), fiatCurrency.getValue()));

        fiatCurrency.valueProperty().addListener(fiatCurrencyListener);
    }
}
