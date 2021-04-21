package com.sparrowwallet.sparrow.preferences;

import com.sparrowwallet.drongo.BitcoinUnit;
import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.sparrow.EventManager;
import com.sparrowwallet.sparrow.control.UnlabeledToggleSwitch;
import com.sparrowwallet.sparrow.event.*;
import com.sparrowwallet.sparrow.io.Config;
import com.sparrowwallet.sparrow.net.ExchangeSource;
import com.sparrowwallet.sparrow.net.FeeRatesSource;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Currency;
import java.util.List;

public class GeneralPreferencesController extends PreferencesDetailController {
    private static final Logger log = LoggerFactory.getLogger(GeneralPreferencesController.class);

    @FXML
    private ComboBox<BitcoinUnit> bitcoinUnit;

    @FXML
    private ComboBox<FeeRatesSource> feeRatesSource;

    @FXML
    private ComboBox<Currency> fiatCurrency;

    @FXML
    private ComboBox<ExchangeSource> exchangeSource;

    @FXML
    private UnlabeledToggleSwitch loadRecentWallets;

    @FXML
    private UnlabeledToggleSwitch validateDerivationPaths;

    @FXML
    private UnlabeledToggleSwitch groupByAddress;

    @FXML
    private UnlabeledToggleSwitch includeMempoolOutputs;

    @FXML
    private UnlabeledToggleSwitch notifyNewTransactions;

    @FXML
    private UnlabeledToggleSwitch checkNewVersions;

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
        } else {
            bitcoinUnit.setValue(BitcoinUnit.AUTO);
        }

        bitcoinUnit.valueProperty().addListener((observable, oldValue, newValue) -> {
            config.setBitcoinUnit(newValue);
            EventManager.get().post(new BitcoinUnitChangedEvent(newValue));
        });

        if(config.getFeeRatesSource() != null) {
            feeRatesSource.setValue(config.getFeeRatesSource());
        } else {
            feeRatesSource.getSelectionModel().select(1);
            config.setFeeRatesSource(feeRatesSource.getValue());
        }

        feeRatesSource.valueProperty().addListener((observable, oldValue, newValue) -> {
            config.setFeeRatesSource(newValue);
            EventManager.get().post(new FeeRatesSourceChangedEvent(newValue));
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

        loadRecentWallets.setSelected(config.isLoadRecentWallets());
        loadRecentWallets.selectedProperty().addListener((observableValue, oldValue, newValue) -> {
            config.setLoadRecentWallets(newValue);
            EventManager.get().post(new RequestOpenWalletsEvent());
        });

        validateDerivationPaths.setSelected(config.isValidateDerivationPaths());
        validateDerivationPaths.selectedProperty().addListener((observableValue, oldValue, newValue) -> {
            config.setValidateDerivationPaths(newValue);
            System.setProperty(Wallet.ALLOW_DERIVATIONS_MATCHING_OTHER_SCRIPT_TYPES_PROPERTY, Boolean.toString(!newValue));
        });

        groupByAddress.setSelected(config.isGroupByAddress());
        includeMempoolOutputs.setSelected(config.isIncludeMempoolOutputs());
        groupByAddress.selectedProperty().addListener((observableValue, oldValue, newValue) -> {
            config.setGroupByAddress(newValue);
        });
        includeMempoolOutputs.selectedProperty().addListener((observableValue, oldValue, newValue) -> {
            config.setIncludeMempoolOutputs(newValue);
            EventManager.get().post(new IncludeMempoolOutputsChangedEvent());
        });

        notifyNewTransactions.setSelected(config.isNotifyNewTransactions());
        notifyNewTransactions.selectedProperty().addListener((observableValue, oldValue, newValue) -> {
            config.setNotifyNewTransactions(newValue);
        });

        checkNewVersions.setSelected(config.isCheckNewVersions());
        checkNewVersions.selectedProperty().addListener((observableValue, oldValue, newValue) -> {
            config.setCheckNewVersions(newValue);
            EventManager.get().post(new VersionCheckStatusEvent(newValue));
        });
    }

    private void updateCurrencies(ExchangeSource exchangeSource) {
        ExchangeSource.CurrenciesService currenciesService = new ExchangeSource.CurrenciesService(exchangeSource);
        currenciesService.setOnSucceeded(event -> {
            updateCurrencies(currenciesService.getValue());
        });
        currenciesService.setOnFailed(event -> {
            log.error("Error retrieving currencies", event.getSource().getException());
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
