package com.sparrowwallet.sparrow.terminal.preferences;

import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.gui2.*;
import com.googlecode.lanterna.gui2.dialogs.DialogWindow;
import com.sparrowwallet.drongo.BitcoinUnit;
import com.sparrowwallet.sparrow.EventManager;
import com.sparrowwallet.sparrow.UnitFormat;
import com.sparrowwallet.sparrow.event.BitcoinUnitChangedEvent;
import com.sparrowwallet.sparrow.event.FiatCurrencySelectedEvent;
import com.sparrowwallet.sparrow.event.UnitFormatChangedEvent;
import com.sparrowwallet.sparrow.io.Config;
import com.sparrowwallet.sparrow.net.ExchangeSource;
import javafx.application.Platform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Currency;
import java.util.List;

public class GeneralDialog extends DialogWindow {
    private static final Logger log = LoggerFactory.getLogger(GeneralDialog.class);

    private final ComboBox<BitcoinUnit> bitcoinUnit;
    private final ComboBox<String> unitFormat;

    private final ComboBox<Currency> fiatCurrency;
    private final ComboBox<ExchangeSource> exchangeSource;

    private final ComboBox.Listener fiatCurrencyListener = new ComboBox.Listener() {
        @Override
        public void onSelectionChanged(int selectedIndex, int previousSelection, boolean changedByUserInteraction) {
            Currency newValue = fiatCurrency.getSelectedItem();
            if(newValue != null) {
                Config.get().setFiatCurrency(newValue);
                Platform.runLater(() -> {
                    EventManager.get().post(new FiatCurrencySelectedEvent(exchangeSource.getSelectedItem(), newValue));
                });
            }
        }
    };

    public GeneralDialog() {
        super("General Preferences");

        setHints(List.of(Hint.CENTERED));

        Panel mainPanel = new Panel();
        mainPanel.setLayoutManager(new GridLayout(2).setHorizontalSpacing(5));

        mainPanel.addComponent(new Label("Bitcoin Unit"));
        bitcoinUnit = new ComboBox<>(BitcoinUnit.values());
        mainPanel.addComponent(bitcoinUnit);

        mainPanel.addComponent(new Label("Unit Format"));
        unitFormat = new ComboBox<>("1,234.56", "1.235,56");
        mainPanel.addComponent(unitFormat);

        mainPanel.addComponent(new EmptySpace(TerminalSize.ONE));
        mainPanel.addComponent(new EmptySpace(TerminalSize.ONE));

        mainPanel.addComponent(new Label("Currency"));
        fiatCurrency = new ComboBox<>();
        mainPanel.addComponent(fiatCurrency);

        mainPanel.addComponent(new Label("Exchange rate source"));
        exchangeSource = new ComboBox<>(ExchangeSource.values());
        mainPanel.addComponent(exchangeSource);

        bitcoinUnit.setSelectedItem(Config.get().getBitcoinUnit() == null ? BitcoinUnit.AUTO : Config.get().getBitcoinUnit());
        unitFormat.setSelectedIndex(Config.get().getUnitFormat() == UnitFormat.COMMA ? 1 : 0);

        if(Config.get().getExchangeSource() == null) {
            Config.get().setExchangeSource(ExchangeSource.COINGECKO);
        }
        exchangeSource.setSelectedItem(Config.get().getExchangeSource());

        bitcoinUnit.addListener((int selectedIndex, int previousSelection, boolean changedByUserInteraction) -> {
            BitcoinUnit newValue = bitcoinUnit.getSelectedItem();
            Config.get().setBitcoinUnit(newValue);
            Platform.runLater(() -> {
                EventManager.get().post(new BitcoinUnitChangedEvent(newValue));
            });
        });

        unitFormat.addListener((int selectedIndex, int previousSelection, boolean changedByUserInteraction) -> {
            UnitFormat format = selectedIndex == 1 ? UnitFormat.COMMA : UnitFormat.DOT;
            Config.get().setUnitFormat(format);
            Platform.runLater(() -> {
                EventManager.get().post(new UnitFormatChangedEvent(format));
            });
        });

        exchangeSource.addListener((int selectedIndex, int previousSelection, boolean changedByUserInteraction) -> {
            ExchangeSource source = exchangeSource.getSelectedItem();
            Config.get().setExchangeSource(source);
            updateCurrencies(source);
        });

        updateCurrencies(exchangeSource.getSelectedItem());

        Panel buttonPanel = new Panel();
        buttonPanel.setLayoutManager(new GridLayout(2).setHorizontalSpacing(1));
        buttonPanel.addComponent(new Button("Done", this::onDone).setLayoutData(GridLayout.createLayoutData(GridLayout.Alignment.CENTER, GridLayout.Alignment.CENTER, true, false)));

        mainPanel.addComponent(new EmptySpace(TerminalSize.ONE));
        mainPanel.addComponent(new EmptySpace(TerminalSize.ONE));
        mainPanel.addComponent(new EmptySpace(TerminalSize.ONE));

        buttonPanel.setLayoutData(GridLayout.createLayoutData(GridLayout.Alignment.END, GridLayout.Alignment.CENTER,false,false)).addTo(mainPanel);
        setComponent(mainPanel);
    }

    private void onDone() {
        close();
    }

    private void updateCurrencies(ExchangeSource exchangeSource) {
        Platform.runLater(() -> {
            ExchangeSource.CurrenciesService currenciesService = new ExchangeSource.CurrenciesService(exchangeSource);
            currenciesService.setOnSucceeded(event -> {
                updateCurrencies(currenciesService.getValue());
            });
            currenciesService.setOnFailed(event -> {
                log.error("Error retrieving currencies", event.getSource().getException());
            });
            currenciesService.start();
        });
    }

    private void updateCurrencies(List<Currency> currencies) {
        fiatCurrency.removeListener(fiatCurrencyListener);

        fiatCurrency.clearItems();
        currencies.forEach(fiatCurrency::addItem);

        Currency configCurrency = Config.get().getFiatCurrency();
        if(configCurrency != null && currencies.contains(configCurrency)) {
            fiatCurrency.setVisible(true);
            fiatCurrency.setSelectedItem(configCurrency);
        } else if(!currencies.isEmpty()) {
            fiatCurrency.setVisible(true);
            fiatCurrency.setSelectedIndex(0);
            Config.get().setFiatCurrency(fiatCurrency.getSelectedItem());
        } else {
            fiatCurrency.setVisible(false);
        }

        //Always fire event regardless of previous selection to update rates
        Platform.runLater(() -> {
            EventManager.get().post(new FiatCurrencySelectedEvent(exchangeSource.getSelectedItem(), fiatCurrency.getSelectedItem()));
        });

        fiatCurrency.addListener(fiatCurrencyListener);
    }
}
