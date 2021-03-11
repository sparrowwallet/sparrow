package com.sparrowwallet.sparrow.wallet;

import com.csvreader.CsvWriter;
import com.google.common.eventbus.Subscribe;
import com.sparrowwallet.drongo.BitcoinUnit;
import com.sparrowwallet.drongo.protocol.Transaction;
import com.sparrowwallet.sparrow.AppServices;
import com.sparrowwallet.sparrow.CurrencyRate;
import com.sparrowwallet.sparrow.EventManager;
import com.sparrowwallet.sparrow.control.*;
import com.sparrowwallet.sparrow.event.*;
import com.sparrowwallet.sparrow.net.ExchangeSource;
import javafx.collections.ListChangeListener;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.TreeItem;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.ResourceBundle;

public class TransactionsController extends WalletFormController implements Initializable {
    private static final Logger log = LoggerFactory.getLogger(TransactionsController.class);

    @FXML
    private CoinLabel balance;

    @FXML
    private FiatLabel fiatBalance;

    @FXML
    private CoinLabel mempoolBalance;

    @FXML
    private FiatLabel fiatMempoolBalance;

    @FXML
    private CopyableLabel transactionCount;

    @FXML
    private TransactionsTreeTable transactionsTable;

    @FXML
    private BalanceChart balanceChart;

    @FXML
    private Button exportCsv;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        EventManager.get().register(this);
    }

    @Override
    public void initializeView() {
        WalletTransactionsEntry walletTransactionsEntry = getWalletForm().getWalletTransactionsEntry();

        transactionsTable.initialize(walletTransactionsEntry);

        balance.valueProperty().addListener((observable, oldValue, newValue) -> {
            setFiatBalance(fiatBalance, AppServices.getFiatCurrencyExchangeRate(), newValue.longValue());
        });
        balance.setValue(walletTransactionsEntry.getBalance());
        mempoolBalance.valueProperty().addListener((observable, oldValue, newValue) -> {
            setFiatBalance(fiatMempoolBalance, AppServices.getFiatCurrencyExchangeRate(), newValue.longValue());
        });
        mempoolBalance.setValue(walletTransactionsEntry.getMempoolBalance());
        setTransactionCount(walletTransactionsEntry);
        balanceChart.initialize(walletTransactionsEntry);

        transactionsTable.getSelectionModel().getSelectedIndices().addListener((ListChangeListener<Integer>) c -> {
            TreeItem<Entry> selectedItem = transactionsTable.getSelectionModel().getSelectedItem();
            if(selectedItem != null && selectedItem.getValue() instanceof TransactionEntry) {
                balanceChart.select((TransactionEntry)selectedItem.getValue());
            }
        });
    }

    private void setFiatBalance(FiatLabel fiatLabel, CurrencyRate currencyRate, long balance) {
        if(currencyRate != null && currencyRate.isAvailable() && balance > 0) {
            fiatLabel.set(currencyRate, balance);
        } else {
            fiatLabel.setCurrency(null);
            fiatLabel.setBtcRate(0.0);
        }
    }

    private void setTransactionCount(WalletTransactionsEntry walletTransactionsEntry) {
        transactionCount.setText(walletTransactionsEntry.getChildren() != null ? Integer.toString(walletTransactionsEntry.getChildren().size()) : "0");
    }

    public void exportCSV(ActionEvent event) {
        WalletTransactionsEntry walletTransactionsEntry = getWalletForm().getWalletTransactionsEntry();

        Stage window = new Stage();
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Export Transactions as CSV");
        fileChooser.setInitialFileName(getWalletForm().getWallet().getName() + ".csv");

        File file = fileChooser.showSaveDialog(window);
        if(file != null) {
            try(FileOutputStream outputStream = new FileOutputStream(file)) {
                CsvWriter writer = new CsvWriter(outputStream, ',', StandardCharsets.UTF_8);
                writer.writeRecord(new String[] {"Date", "Label", "Value", "Balance"});
                for(Entry entry : walletTransactionsEntry.getChildren()) {
                    TransactionEntry txEntry = (TransactionEntry)entry;
                    writer.write(txEntry.getBlockTransaction().getDate() == null ? "Unconfirmed" : EntryCell.DATE_FORMAT.format(txEntry.getBlockTransaction().getDate()));
                    writer.write(txEntry.getLabel());
                    writer.write(getCoinValue(txEntry.getValue()));
                    writer.write(getCoinValue(txEntry.getBalance()));
                    writer.endRecord();
                }
                writer.close();
            } catch(IOException e) {
                log.error("Error exporting transactions as CSV", e);
                AppServices.showErrorDialog("Error exporting transactions as CSV", e.getMessage());
            }
        }
    }

    private String getCoinValue(Long value) {
        return BitcoinUnit.BTC.equals(transactionsTable.getBitcoinUnit()) ?
                CoinLabel.getBTCFormat().format(value.doubleValue() / Transaction.SATOSHIS_PER_BITCOIN) :
                String.format(Locale.ENGLISH, "%d", value);
    }

    @Subscribe
    public void walletNodesChanged(WalletNodesChangedEvent event) {
        if(event.getWallet().equals(walletForm.getWallet())) {
            WalletTransactionsEntry walletTransactionsEntry = getWalletForm().getWalletTransactionsEntry();

            transactionsTable.updateAll(walletTransactionsEntry);
            balance.setValue(walletTransactionsEntry.getBalance());
            mempoolBalance.setValue(walletTransactionsEntry.getMempoolBalance());
            balanceChart.update(walletTransactionsEntry);
            setTransactionCount(walletTransactionsEntry);
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
            setTransactionCount(walletTransactionsEntry);
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
            fiatMempoolBalance.setCurrency(null);
            fiatMempoolBalance.setBtcRate(0.0);
        }
    }

    @Subscribe
    public void exchangeRatesUpdated(ExchangeRatesUpdatedEvent event) {
        setFiatBalance(fiatBalance, event.getCurrencyRate(), getWalletForm().getWalletTransactionsEntry().getBalance());
        setFiatBalance(fiatMempoolBalance, event.getCurrencyRate(), getWalletForm().getWalletTransactionsEntry().getMempoolBalance());
    }

    @Subscribe
    public void walletHistoryStatus(WalletHistoryStatusEvent event) {
        transactionsTable.updateHistoryStatus(event);
    }

    @Subscribe
    public void bwtSyncStatus(BwtSyncStatusEvent event) {
        walletHistoryStatus(new WalletHistoryStatusEvent(walletForm.getWallet(), true, event.getStatus()));
    }

    @Subscribe
    public void bwtScanStatus(BwtScanStatusEvent event) {
        walletHistoryStatus(new WalletHistoryStatusEvent(walletForm.getWallet(), true, event.getStatus()));
    }

    @Subscribe
    public void bwtShutdown(BwtShutdownEvent event) {
        walletHistoryStatus(new WalletHistoryStatusEvent(walletForm.getWallet(), false));
    }

    @Subscribe
    private void connectionFailed(ConnectionFailedEvent event) {
        walletHistoryStatus(new WalletHistoryStatusEvent(walletForm.getWallet(), false));
    }

    @Subscribe
    public void walletUtxoStatusChanged(WalletUtxoStatusChangedEvent event) {
        if(event.getWallet().equals(getWalletForm().getWallet())) {
            transactionsTable.refresh();
        }
    }
}
