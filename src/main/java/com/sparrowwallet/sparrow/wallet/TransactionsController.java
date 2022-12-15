package com.sparrowwallet.sparrow.wallet;

import com.csvreader.CsvWriter;
import com.google.common.eventbus.Subscribe;
import com.sparrowwallet.drongo.BitcoinUnit;
import com.sparrowwallet.drongo.protocol.TransactionInput;
import com.sparrowwallet.drongo.protocol.TransactionOutput;
import com.sparrowwallet.drongo.wallet.BlockTransaction;
import com.sparrowwallet.sparrow.UnitFormat;
import com.sparrowwallet.sparrow.AppServices;
import com.sparrowwallet.sparrow.EventManager;
import com.sparrowwallet.sparrow.control.*;
import com.sparrowwallet.sparrow.event.*;
import com.sparrowwallet.sparrow.io.Config;
import com.sparrowwallet.sparrow.net.ExchangeSource;
import javafx.application.Platform;
import javafx.collections.ListChangeListener;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.TextArea;
import javafx.scene.control.TreeItem;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.controlsfx.control.MasterDetailPane;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.Locale;
import java.util.ResourceBundle;

public class TransactionsController extends WalletFormController implements Initializable {
    private static final Logger log = LoggerFactory.getLogger(TransactionsController.class);

    private static final DateFormat LOG_DATE_FORMAT = new SimpleDateFormat("[MMM dd HH:mm:ss]");

    @FXML
    private CopyableCoinLabel balance;

    @FXML
    private FiatLabel fiatBalance;

    @FXML
    private CopyableCoinLabel mempoolBalance;

    @FXML
    private FiatLabel fiatMempoolBalance;

    @FXML
    private CopyableLabel transactionCount;

    @FXML
    private MasterDetailPane transactionsMasterDetail;

    @FXML
    private TransactionsTreeTable transactionsTable;

    @FXML
    private TextArea loadingLog;

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

        transactionsMasterDetail.setShowDetailNode(Config.get().isShowLoadingLog());
        loadingLog.appendText("Wallet loading history for " + getWalletForm().getWallet().getFullDisplayName());
        loadingLog.setEditable(false);
    }

    private void setTransactionCount(WalletTransactionsEntry walletTransactionsEntry) {
        transactionCount.setText(walletTransactionsEntry.getChildren() != null ? Integer.toString(walletTransactionsEntry.getChildren().size()) : "0");
    }

    public void exportCSV(ActionEvent event) {
        WalletTransactionsEntry walletTransactionsEntry = getWalletForm().getWalletTransactionsEntry();

        Stage window = new Stage();
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Export Transactions as CSV");
        fileChooser.setInitialFileName(getWalletForm().getWallet().getFullName() + ".csv");

        AppServices.moveToActiveWindowScreen(window, 800, 450);
        File file = fileChooser.showSaveDialog(window);
        if(file != null) {
            try(FileOutputStream outputStream = new FileOutputStream(file)) {
                CsvWriter writer = new CsvWriter(outputStream, ',', StandardCharsets.UTF_8);
                writer.writeRecord(new String[] {"Date", "Label", "Value", "Balance", "Fee", "Txid"});
                for(Entry entry : walletTransactionsEntry.getChildren()) {
                    TransactionEntry txEntry = (TransactionEntry)entry;
                    writer.write(txEntry.getBlockTransaction().getDate() == null ? "Unconfirmed" : EntryCell.DATE_FORMAT.format(txEntry.getBlockTransaction().getDate()));
                    writer.write(txEntry.getLabel());
                    writer.write(getCoinValue(txEntry.getValue()));
                    writer.write(getCoinValue(txEntry.getBalance()));
                    Long fee = txEntry.getValue() < 0 ? getFee(txEntry.getBlockTransaction()) : null;
                    writer.write(fee == null ? "" : getCoinValue(fee));
                    writer.write(txEntry.getBlockTransaction().getHash().toString());
                    writer.endRecord();
                }
                writer.close();
            } catch(IOException e) {
                log.error("Error exporting transactions as CSV", e);
                AppServices.showErrorDialog("Error exporting transactions as CSV", e.getMessage());
            }
        }
    }

    private Long getFee(BlockTransaction blockTransaction) {
        long fee = 0L;
        for(TransactionInput txInput : blockTransaction.getTransaction().getInputs()) {
            if(txInput.isCoinBase()) {
                return 0L;
            }

            BlockTransaction inputTx = getWalletForm().getWallet().getWalletTransaction(txInput.getOutpoint().getHash());
            if(inputTx == null || inputTx.getTransaction().getOutputs().size() <= txInput.getOutpoint().getIndex()) {
                return null;
            }
            TransactionOutput spentOutput = inputTx.getTransaction().getOutputs().get((int)txInput.getOutpoint().getIndex());
            fee += spentOutput.getValue();
        }

        for(TransactionOutput txOutput : blockTransaction.getTransaction().getOutputs()) {
            fee -= txOutput.getValue();
        }

        return fee;
    }

    private String getCoinValue(Long value) {
        UnitFormat format = Config.get().getUnitFormat() == null ? UnitFormat.DOT : Config.get().getUnitFormat();
        return BitcoinUnit.BTC.equals(transactionsTable.getBitcoinUnit()) ? format.tableFormatBtcValue(value) : String.format(Locale.ENGLISH, "%d", value);
    }

    private void logMessage(String logMessage) {
        if(logMessage != null) {
            logMessage = logMessage.replace("m/", "../");
            String date = LOG_DATE_FORMAT.format(new Date());
            String logLine = "\n" + date + " " + logMessage;
            Platform.runLater(() -> {
                int lastLineStart = loadingLog.getText().lastIndexOf("\n");
                if(lastLineStart < 0 || !loadingLog.getText().substring(lastLineStart).equals(logLine)) {
                    loadingLog.appendText(logLine);
                    loadingLog.setScrollLeft(0);
                }
            });
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
            setTransactionCount(walletTransactionsEntry);
        }
    }

    @Subscribe
    public void walletHistoryChanged(WalletHistoryChangedEvent event) {
        if(event.getWallet().equals(walletForm.getWallet())) {
            WalletTransactionsEntry walletTransactionsEntry = getWalletForm().getWalletTransactionsEntry();

            //Will automatically update transactionsTable transactions and recalculate balances
            walletTransactionsEntry.updateTransactions();

            transactionsTable.updateHistory();
            balance.setValue(walletTransactionsEntry.getBalance());
            mempoolBalance.setValue(walletTransactionsEntry.getMempoolBalance());
            balanceChart.update(walletTransactionsEntry);
            setTransactionCount(walletTransactionsEntry);
        }
    }

    @Subscribe
    public void walletEntryLabelChanged(WalletEntryLabelsChangedEvent event) {
        if(event.fromThisOrNested(walletForm.getWallet())) {
            for(Entry entry : event.getEntries()) {
                transactionsTable.updateLabel(entry);
            }
            balanceChart.update(getWalletForm().getWalletTransactionsEntry());
        }
    }

    @Subscribe
    public void unitFormatChanged(UnitFormatChangedEvent event) {
        transactionsTable.setUnitFormat(getWalletForm().getWallet(), event.getUnitFormat(), event.getBitcoinUnit());
        balanceChart.setUnitFormat(getWalletForm().getWallet(), event.getUnitFormat(), event.getBitcoinUnit());
        balance.refresh(event.getUnitFormat(), event.getBitcoinUnit());
        mempoolBalance.refresh(event.getUnitFormat(), event.getBitcoinUnit());
        fiatBalance.refresh(event.getUnitFormat());
        fiatMempoolBalance.refresh(event.getUnitFormat());
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

        if(event.getWallet() != null && getWalletForm() != null && getWalletForm().getWallet() == event.getWallet()) {
            String logMessage = event.getStatusMessage();
            if(logMessage == null) {
                if(event instanceof WalletHistoryFinishedEvent) {
                    logMessage = "Finished loading.";
                } else if(event instanceof WalletHistoryFailedEvent) {
                    logMessage = event.getErrorMessage();
                }
            }
            logMessage(logMessage);
        }
    }

    @Subscribe
    public void cormorantStatus(CormorantStatusEvent event) {
        if(event.isFor(walletForm.getWallet())) {
            walletHistoryStatus(new WalletHistoryStatusEvent(walletForm.getWallet(), true, event.getStatus()));
        }
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

    @Subscribe
    public void includeMempoolOutputsChangedEvent(IncludeMempoolOutputsChangedEvent event) {
        walletHistoryChanged(new WalletHistoryChangedEvent(getWalletForm().getWallet(), getWalletForm().getStorage(), Collections.emptyList(), Collections.emptyList()));
    }

    @Subscribe
    public void loadingLogChanged(LoadingLogChangedEvent event) {
        transactionsMasterDetail.setShowDetailNode(event.isVisible());
    }

    @Subscribe
    public void selectEntry(SelectEntryEvent event) {
        if(event.getWallet().equals(getWalletForm().getWallet()) && event.getEntry().getWalletFunction() == Function.TRANSACTIONS) {
            selectEntry(transactionsTable, transactionsTable.getRoot(), event.getEntry());
        }
    }
}
