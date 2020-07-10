package com.sparrowwallet.sparrow.wallet;

import com.google.common.eventbus.Subscribe;
import com.sparrowwallet.sparrow.AppController;
import com.sparrowwallet.sparrow.CurrencyRate;
import com.sparrowwallet.sparrow.EventManager;
import com.sparrowwallet.sparrow.control.BalanceChart;
import com.sparrowwallet.sparrow.control.CoinLabel;
import com.sparrowwallet.sparrow.control.FiatLabel;
import com.sparrowwallet.sparrow.control.TransactionsTreeTable;
import com.sparrowwallet.sparrow.event.*;
import com.sparrowwallet.sparrow.io.ExchangeSource;
import javafx.collections.ListChangeListener;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.TreeItem;
import javafx.scene.input.MouseEvent;

import java.net.URL;
import java.util.ResourceBundle;

public class TransactionsController extends WalletFormController implements Initializable {

    @FXML
    private CoinLabel balance;

    @FXML
    private FiatLabel fiatBalance;

    @FXML
    private CoinLabel mempoolBalance;

    @FXML
    private TransactionsTreeTable transactionsTable;

    @FXML
    private BalanceChart balanceChart;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        EventManager.get().register(this);
    }

    @Override
    public void initializeView() {
        WalletTransactionsEntry walletTransactionsEntry = getWalletForm().getWalletTransactionsEntry();

        transactionsTable.initialize(walletTransactionsEntry);

        balance.setValue(walletTransactionsEntry.getBalance());
        setFiatBalance(AppController.getFiatCurrencyExchangeRate(), walletTransactionsEntry.getBalance());
        mempoolBalance.setValue(walletTransactionsEntry.getMempoolBalance());
        balanceChart.initialize(walletTransactionsEntry);

        transactionsTable.getSelectionModel().getSelectedIndices().addListener((ListChangeListener<Integer>) c -> {
            TreeItem<Entry> selectedItem = transactionsTable.getSelectionModel().getSelectedItem();
            if(selectedItem != null && selectedItem.getValue() instanceof TransactionEntry) {
                balanceChart.select((TransactionEntry)selectedItem.getValue());
            }
        });
    }

    private void setFiatBalance(CurrencyRate currencyRate, long balance) {
        if(currencyRate != null && currencyRate.isAvailable()) {
            fiatBalance.set(currencyRate, balance);
        }
    }

    @Subscribe
    public void walletNodesChanged(WalletNodesChangedEvent event) {
        if(event.getWallet().equals(walletForm.getWallet())) {
            WalletTransactionsEntry walletTransactionsEntry = getWalletForm().getWalletTransactionsEntry();

            transactionsTable.updateAll(walletTransactionsEntry);
            balance.setValue(walletTransactionsEntry.getBalance());
            mempoolBalance.setValue(walletTransactionsEntry.getMempoolBalance());
            balanceChart.update(walletTransactionsEntry);
        }
    }

    @Subscribe
    public void walletHistoryChanged(WalletHistoryChangedEvent event) {
        if(event.getWallet().equals(walletForm.getWallet())) {
            WalletTransactionsEntry walletTransactionsEntry = getWalletForm().getWalletTransactionsEntry();

            //Will automatically update transactionsTable transactions and recalculate balances
            walletTransactionsEntry.updateTransactions();

            transactionsTable.updateHistory(event.getHistoryChangedNodes());
            balance.setValue(walletTransactionsEntry.getBalance());
            mempoolBalance.setValue(walletTransactionsEntry.getMempoolBalance());
            balanceChart.update(walletTransactionsEntry);
        }
    }

    @Subscribe
    public void walletEntryLabelChanged(WalletEntryLabelChangedEvent event) {
        if(event.getWallet().equals(walletForm.getWallet())) {
            transactionsTable.updateLabel(event.getEntry());
            balanceChart.update(getWalletForm().getWalletTransactionsEntry());
        }
    }

    @Subscribe
    public void bitcoinUnitChanged(BitcoinUnitChangedEvent event) {
        transactionsTable.setBitcoinUnit(getWalletForm().getWallet(), event.getBitcoinUnit());
        balanceChart.setBitcoinUnit(getWalletForm().getWallet(), event.getBitcoinUnit());
        balance.refresh(event.getBitcoinUnit());
        mempoolBalance.refresh(event.getBitcoinUnit());
    }

    @Subscribe
    public void fiatCurrencySelected(FiatCurrencySelectedEvent event) {
        if(event.getExchangeSource() == ExchangeSource.NONE) {
            fiatBalance.setCurrency(null);
            fiatBalance.setBtcRate(0.0);
        }
    }

    @Subscribe
    public void exchangeRatesUpdated(ExchangeRatesUpdatedEvent event) {
        setFiatBalance(event.getCurrencyRate(), getWalletForm().getWalletTransactionsEntry().getBalance());
    }

    //TODO: Remove
    public void advanceBlock(MouseEvent event) {
        Integer currentBlock = getWalletForm().getWallet().getStoredBlockHeight();
        getWalletForm().getWallet().setStoredBlockHeight(currentBlock+1);
        System.out.println("Advancing from " + currentBlock + " to " + getWalletForm().getWallet().getStoredBlockHeight());
        EventManager.get().post(new WalletBlockHeightChangedEvent(getWalletForm().getWallet(), currentBlock+1));
    }
}
