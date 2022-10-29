package com.sparrowwallet.sparrow.terminal.wallet;

import com.google.common.collect.Lists;
import com.google.common.eventbus.Subscribe;
import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.gui2.*;
import com.googlecode.lanterna.gui2.table.Table;
import com.googlecode.lanterna.gui2.table.TableModel;
import com.sparrowwallet.drongo.protocol.Transaction;
import com.sparrowwallet.sparrow.AppServices;
import com.sparrowwallet.sparrow.event.*;
import com.sparrowwallet.sparrow.net.ExchangeSource;
import com.sparrowwallet.sparrow.terminal.SparrowTerminal;
import com.sparrowwallet.sparrow.terminal.wallet.table.CoinTableCell;
import com.sparrowwallet.sparrow.terminal.wallet.table.DateTableCell;
import com.sparrowwallet.sparrow.terminal.wallet.table.TableCell;
import com.sparrowwallet.sparrow.wallet.Entry;
import com.sparrowwallet.sparrow.wallet.Function;
import com.sparrowwallet.sparrow.wallet.WalletForm;
import com.sparrowwallet.sparrow.wallet.WalletTransactionsEntry;

import java.util.List;

public class TransactionsDialog extends WalletDialog {
    private final Label balance;
    private final Label fiatBalance;
    private final Label mempoolBalance;
    private final Label fiatMempoolBalance;
    private final Label transactionCount;
    private final Table<TableCell> transactions;

    private final String[] tableColumns = {centerPad("Date", DateTableCell.TRANSACTION_WIDTH), centerPad("Value", CoinTableCell.TRANSACTION_WIDTH), centerPad("Balance", CoinTableCell.TRANSACTION_WIDTH)};

    public TransactionsDialog(WalletForm walletForm) {
        super(walletForm.getWallet().getFullDisplayName() + " Transactions", walletForm);

        setHints(List.of(Hint.CENTERED, Hint.EXPANDED));
        Panel labelPanel = new Panel(new GridLayout(3).setHorizontalSpacing(5).setVerticalSpacing(0));

        WalletTransactionsEntry walletTransactionsEntry = getWalletForm().getWalletTransactionsEntry();

        labelPanel.addComponent(new Label("Balance"));
        balance = new Label("").addTo(labelPanel);
        fiatBalance = new Label("").addTo(labelPanel);

        labelPanel.addComponent(new Label("Mempool"));
        mempoolBalance = new Label("").addTo(labelPanel);
        fiatMempoolBalance = new Label("").addTo(labelPanel);

        labelPanel.addComponent(new Label("Transactions"));
        transactionCount = new Label("").addTo(labelPanel);
        labelPanel.addComponent(new EmptySpace(TerminalSize.ONE));

        transactions = new Table<>(tableColumns);
        transactions.setTableCellRenderer(new EntryTableCellRenderer());

        updateLabels(walletTransactionsEntry);
        updateHistory(getWalletForm().getWalletTransactionsEntry());

        Panel buttonPanel = new Panel(new GridLayout(5).setHorizontalSpacing(2).setVerticalSpacing(0));
        buttonPanel.addComponent(new EmptySpace(new TerminalSize(15, 1)));
        buttonPanel.addComponent(new EmptySpace(new TerminalSize(15, 1)));
        buttonPanel.addComponent(new EmptySpace(new TerminalSize(15, 1)));
        buttonPanel.addComponent(new Button("Back", () -> onBack(Function.TRANSACTIONS)));
        buttonPanel.addComponent(new Button("Refresh", this::onRefresh));

        Panel mainPanel = new Panel();
        mainPanel.setLayoutManager(new LinearLayout(Direction.VERTICAL).setSpacing(1));
        mainPanel.addComponent(labelPanel);
        mainPanel.addComponent(transactions);
        mainPanel.addComponent(buttonPanel);

        setComponent(mainPanel);
    }

    private void updateHistory(WalletTransactionsEntry walletTransactionsEntry) {
        SparrowTerminal.get().getGuiThread().invokeLater(() -> {
            TableModel<TableCell> tableModel = getTableModel(walletTransactionsEntry);
            transactions.setTableModel(tableModel);
        });
    }

    private TableModel<TableCell> getTableModel(WalletTransactionsEntry walletTransactionsEntry) {
        TableModel<TableCell> tableModel = new TableModel<>(tableColumns);
        for(Entry entry : Lists.reverse(walletTransactionsEntry.getChildren())) {
            tableModel.addRow(new DateTableCell(entry), new CoinTableCell(entry, false), new CoinTableCell(entry, true));
        }

        return tableModel;
    }

    private void updateLabels(WalletTransactionsEntry walletTransactionsEntry) {
        SparrowTerminal.get().getGuiThread().invokeLater(() -> {
            balance.setText(formatBitcoinValue(walletTransactionsEntry.getBalance(), true));
            mempoolBalance.setText(formatBitcoinValue(walletTransactionsEntry.getMempoolBalance(), true));

            if(AppServices.getFiatCurrencyExchangeRate() != null) {
                fiatBalance.setText(formatFiatValue(getFiatValue(walletTransactionsEntry.getBalance(), AppServices.getFiatCurrencyExchangeRate())));
                fiatMempoolBalance.setText(formatFiatValue(getFiatValue(walletTransactionsEntry.getMempoolBalance(), AppServices.getFiatCurrencyExchangeRate())));
            } else {
                fiatBalance.setText("");
                fiatMempoolBalance.setText("");
            }

            setTransactionCount(walletTransactionsEntry);
        });
    }

    private void setTransactionCount(WalletTransactionsEntry walletTransactionsEntry) {
        transactionCount.setText(walletTransactionsEntry.getChildren() != null ? Integer.toString(walletTransactionsEntry.getChildren().size()) : "0");
    }

    @Subscribe
    public void walletNodesChanged(WalletNodesChangedEvent event) {
        if(event.getWallet().equals(getWalletForm().getWallet())) {
            WalletTransactionsEntry walletTransactionsEntry = getWalletForm().getWalletTransactionsEntry();
            updateHistory(walletTransactionsEntry);
            updateLabels(walletTransactionsEntry);
        }
    }

    @Subscribe
    public void walletHistoryChanged(WalletHistoryChangedEvent event) {
        if(event.getWallet().equals(getWalletForm().getWallet())) {
            WalletTransactionsEntry walletTransactionsEntry = getWalletForm().getWalletTransactionsEntry();
            walletTransactionsEntry.updateTransactions();
            updateHistory(walletTransactionsEntry);
            updateLabels(walletTransactionsEntry);
        }
    }

    @Subscribe
    public void unitFormatChanged(UnitFormatChangedEvent event) {
        updateHistory(getWalletForm().getWalletTransactionsEntry());
        updateLabels(getWalletForm().getWalletTransactionsEntry());
    }

    @Subscribe
    public void fiatCurrencySelected(FiatCurrencySelectedEvent event) {
        if(event.getExchangeSource() == ExchangeSource.NONE) {
            SparrowTerminal.get().getGuiThread().invokeLater(() -> {
                fiatBalance.setText("");
                fiatMempoolBalance.setText("");
            });
        }
    }

    @Subscribe
    public void exchangeRatesUpdated(ExchangeRatesUpdatedEvent event) {
        SparrowTerminal.get().getGuiThread().invokeLater(() -> {
            WalletTransactionsEntry walletTransactionsEntry = getWalletForm().getWalletTransactionsEntry();
            fiatBalance.setText(formatFiatValue(getFiatValue(walletTransactionsEntry.getBalance(), event.getCurrencyRate())));
            fiatMempoolBalance.setText(formatFiatValue(getFiatValue(walletTransactionsEntry.getMempoolBalance(), event.getCurrencyRate())));
        });
    }
}
