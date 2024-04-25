package com.sparrowwallet.sparrow.terminal.wallet;

import com.google.common.eventbus.Subscribe;
import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.gui2.*;
import com.googlecode.lanterna.gui2.table.Table;
import com.googlecode.lanterna.gui2.table.TableModel;
import com.sparrowwallet.drongo.wallet.*;
import com.sparrowwallet.sparrow.AppServices;
import com.sparrowwallet.sparrow.EventManager;
import com.sparrowwallet.sparrow.event.*;
import com.sparrowwallet.sparrow.io.Config;
import com.sparrowwallet.sparrow.net.ExchangeSource;
import com.sparrowwallet.sparrow.terminal.SparrowTerminal;
import com.sparrowwallet.sparrow.terminal.wallet.table.*;
import com.sparrowwallet.sparrow.wallet.*;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class UtxosDialog extends WalletDialog {
    private final Label balance;
    private final Label fiatBalance;
    private final Label mempoolBalance;
    private final Label fiatMempoolBalance;
    private final Label utxoCount;
    private final Table<TableCell> utxos;

    public UtxosDialog(WalletForm walletForm) {
        super(walletForm.getWallet().getFullDisplayName() + " UTXOs", walletForm);

        setHints(List.of(Hint.CENTERED, Hint.EXPANDED));
        Panel labelPanel = new Panel(new GridLayout(3).setHorizontalSpacing(5).setVerticalSpacing(0));

        WalletUtxosEntry walletUtxosEntry = getWalletForm().getWalletUtxosEntry();

        labelPanel.addComponent(new Label("Balance"));
        balance = new Label("").addTo(labelPanel);
        fiatBalance = new Label("").addTo(labelPanel);

        labelPanel.addComponent(new Label("Mempool"));
        mempoolBalance = new Label("").addTo(labelPanel);
        fiatMempoolBalance = new Label("").addTo(labelPanel);

        labelPanel.addComponent(new Label("UTXOs"));
        utxoCount = new Label("").addTo(labelPanel);
        labelPanel.addComponent(new EmptySpace(TerminalSize.ONE));

        utxos = new Table<>(getTableColumns());
        utxos.setTableCellRenderer(new EntryTableCellRenderer());
        utxos.setSelectAction(() -> {
            if(utxos.getTableModel().getRowCount() > utxos.getSelectedRow()) {
                TableCell dateCell = utxos.getTableModel().getRow(utxos.getSelectedRow()).get(0);
                dateCell.setSelected(!dateCell.isSelected());
            }
        });
        utxos.setInputFilter((interactable, keyStroke) -> {
            if(keyStroke.getCharacter() == Character.valueOf('f')) {
                if(utxos.getTableModel().getRowCount() > utxos.getSelectedRow()) {
                    TableCell dateCell = utxos.getTableModel().getRow(utxos.getSelectedRow()).get(0);
                    if(dateCell.getEntry() instanceof UtxoEntry utxoEntry) {
                        utxoEntry.getHashIndex().setStatus(utxoEntry.getHashIndex().getStatus() == Status.FROZEN ? null : Status.FROZEN);
                        utxos.invalidate();
                        EventManager.get().post(new WalletUtxoStatusChangedEvent(utxoEntry.getWallet(), List.of(utxoEntry.getHashIndex())));
                    }
                }
            }

            return true;
        });

        updateLabels(walletUtxosEntry);
        updateHistory(getWalletForm().getWalletUtxosEntry());

        Panel buttonPanel = new Panel(new GridLayout(5).setHorizontalSpacing(2).setVerticalSpacing(0));

        buttonPanel.addComponent(new EmptySpace(new TerminalSize(15, 1)));
        buttonPanel.addComponent(new EmptySpace(new TerminalSize(15, 1)));
        buttonPanel.addComponent(new EmptySpace(new TerminalSize(15, 1)));
        buttonPanel.addComponent(new Button("Back", () -> onBack(Function.UTXOS)));
        buttonPanel.addComponent(new Button("Refresh", this::onRefresh));

        Panel mainPanel = new Panel();
        mainPanel.setLayoutManager(new LinearLayout(Direction.VERTICAL).setSpacing(1));
        mainPanel.addComponent(labelPanel);
        mainPanel.addComponent(utxos);
        mainPanel.addComponent(buttonPanel);

        setComponent(mainPanel);
    }

    private String[] getTableColumns() {
        if(getWalletForm().getWallet().isWhirlpoolMixWallet()) {
            return new String[] {centerPad("Date", DateTableCell.UTXO_WIDTH), centerPad("Output", OutputTableCell.WIDTH),
                    centerPad("Mixes", MixTableCell.WIDTH), centerPad("Value", CoinTableCell.UTXO_WIDTH)};
        }

        return new String[] {centerPad("Date", DateTableCell.UTXO_WIDTH), centerPad("Output", OutputTableCell.WIDTH),
                centerPad("Address", AddressTableCell.UTXO_WIDTH), centerPad("Value", CoinTableCell.UTXO_WIDTH)};
    }

    private void updateHistory(WalletUtxosEntry walletUtxosEntry) {
        SparrowTerminal.get().getGuiThread().invokeLater(() -> {
            TableModel<TableCell> tableModel = getTableModel(walletUtxosEntry);
            utxos.setTableModel(tableModel);
            if(utxos.getTheme() != null && utxos.getRenderer().getViewTopRow() >= tableModel.getRowCount()) {
                utxos.getRenderer().setViewTopRow(0);
            }
        });
    }

    private TableModel<TableCell> getTableModel(WalletUtxosEntry walletUtxosEntry) {
        TableModel<TableCell> tableModel = new TableModel<>(getTableColumns());
        List<Entry> utxoList = new ArrayList<>(walletUtxosEntry.getChildren());
        utxoList.sort((o1, o2) -> Long.compare(o2.getValue(), o1.getValue()));

        for(Entry entry : utxoList) {
            if(walletUtxosEntry.getWallet().isWhirlpoolMixWallet()) {
                tableModel.addRow(new DateTableCell(entry), new OutputTableCell(entry), new MixTableCell(entry), new CoinTableCell(entry, false));
            } else {
                tableModel.addRow(new DateTableCell(entry), new OutputTableCell(entry), new AddressTableCell(entry), new CoinTableCell(entry, false));
            }
        }

        return tableModel;
    }

    private void updateLabels(WalletUtxosEntry walletUtxosEntry) {
        SparrowTerminal.get().getGuiThread().invokeLater(() -> {
            balance.setText(formatBitcoinValue(walletUtxosEntry.getBalance(), true));
            mempoolBalance.setText(formatBitcoinValue(walletUtxosEntry.getMempoolBalance(), true));

            if(AppServices.getFiatCurrencyExchangeRate() != null && Config.get().getExchangeSource() != ExchangeSource.NONE) {
                fiatBalance.setText(formatFiatValue(getFiatValue(walletUtxosEntry.getBalance(), AppServices.getFiatCurrencyExchangeRate())));
                fiatMempoolBalance.setText(formatFiatValue(getFiatValue(walletUtxosEntry.getMempoolBalance(), AppServices.getFiatCurrencyExchangeRate())));
            } else {
                fiatBalance.setText("");
                fiatMempoolBalance.setText("");
            }

            setUtxoCount(walletUtxosEntry);
        });
    }

    private void setUtxoCount(WalletUtxosEntry walletUtxosEntry) {
        utxoCount.setText(walletUtxosEntry.getChildren() != null ? Integer.toString(walletUtxosEntry.getChildren().size()) : "0");
    }

    private List<UtxoEntry> getSelectedEntries() {
        return utxos.getTableModel().getRows().stream().map(row -> row.get(0)).filter(TableCell::isSelected).map(dateCell -> (UtxoEntry)dateCell.getEntry()).collect(Collectors.toList());
    }

    @Subscribe
    public void walletNodesChanged(WalletNodesChangedEvent event) {
        if(event.getWallet().equals(getWalletForm().getWallet())) {
            WalletUtxosEntry walletUtxosEntry = getWalletForm().getWalletUtxosEntry();
            updateHistory(walletUtxosEntry);
            updateLabels(walletUtxosEntry);
        }
    }

    @Subscribe
    public void walletHistoryChanged(WalletHistoryChangedEvent event) {
        if(event.getWallet().equals(getWalletForm().getWallet())) {
            WalletUtxosEntry walletUtxosEntry = getWalletForm().getWalletUtxosEntry();
            walletUtxosEntry.updateUtxos();
            updateHistory(walletUtxosEntry);
            updateLabels(walletUtxosEntry);
        }
    }

    @Subscribe
    public void includeMempoolOutputsChangedEvent(IncludeMempoolOutputsChangedEvent event) {
        updateHistory(getWalletForm().getWalletUtxosEntry());
        updateLabels(getWalletForm().getWalletUtxosEntry());
    }

    @Subscribe
    public void unitFormatChanged(UnitFormatChangedEvent event) {
        updateHistory(getWalletForm().getWalletUtxosEntry());
        updateLabels(getWalletForm().getWalletUtxosEntry());
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
            WalletUtxosEntry walletUtxosEntry = getWalletForm().getWalletUtxosEntry();
            fiatBalance.setText(formatFiatValue(getFiatValue(walletUtxosEntry.getBalance(), event.getCurrencyRate())));
            fiatMempoolBalance.setText(formatFiatValue(getFiatValue(walletUtxosEntry.getMempoolBalance(), event.getCurrencyRate())));
        });
    }
}
