package com.sparrowwallet.sparrow.terminal.wallet;

import com.google.common.eventbus.Subscribe;
import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.gui2.*;
import com.googlecode.lanterna.gui2.table.Table;
import com.googlecode.lanterna.gui2.table.TableModel;
import com.samourai.whirlpool.client.wallet.beans.MixProgress;
import com.samourai.whirlpool.client.whirlpool.beans.Pool;
import com.sparrowwallet.drongo.protocol.Sha256Hash;
import com.sparrowwallet.drongo.wallet.*;
import com.sparrowwallet.sparrow.AppServices;
import com.sparrowwallet.sparrow.EventManager;
import com.sparrowwallet.sparrow.event.*;
import com.sparrowwallet.sparrow.net.ExchangeSource;
import com.sparrowwallet.sparrow.terminal.ModalDialog;
import com.sparrowwallet.sparrow.terminal.SparrowTerminal;
import com.sparrowwallet.sparrow.terminal.wallet.table.*;
import com.sparrowwallet.sparrow.wallet.*;
import com.sparrowwallet.sparrow.whirlpool.Whirlpool;
import com.sparrowwallet.sparrow.whirlpool.WhirlpoolServices;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.WeakChangeListener;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;

public class UtxosDialog extends WalletDialog {
    private final Label balance;
    private final Label fiatBalance;
    private final Label mempoolBalance;
    private final Label fiatMempoolBalance;
    private final Label utxoCount;
    private final Table<TableCell> utxos;

    private Button startMix;
    private Button mixTo;
    private Button mixSelected;

    private final ChangeListener<Boolean> mixingOnlineListener = (observable, oldValue, newValue) -> {
        SparrowTerminal.get().getGuiThread().invokeLater(() -> startMix.setEnabled(newValue));
    };

    private final ChangeListener<Boolean> mixingStartingListener = (observable, oldValue, newValue) -> {
        try {
            SparrowTerminal.get().getGuiThread().invokeAndWait(() -> {
                startMix.setEnabled(!newValue && AppServices.onlineProperty().get());
                startMix.setLabel(newValue && AppServices.onlineProperty().get() ? "Starting Mixing..." : isMixing() ? "Stop Mixing" : "Start Mixing");
                mixTo.setEnabled(!newValue);
            });
        } catch(InterruptedException e) {
            //ignore
        }
    };

    private final ChangeListener<Boolean> mixingStoppingListener = (observable, oldValue, newValue) -> {
        try {
            SparrowTerminal.get().getGuiThread().invokeAndWait(() -> {
                startMix.setEnabled(!newValue && AppServices.onlineProperty().get());
                startMix.setLabel(newValue ? "Stopping Mixing..." : isMixing() ? "Stop Mixing" : "Start Mixing");
                mixTo.setEnabled(!newValue);
            });
        } catch(InterruptedException e) {
            //ignore
        }
    };

    private final ChangeListener<Boolean> mixingListener = (observable, oldValue, newValue) -> {
        if(!newValue) {
            WalletUtxosEntry walletUtxosEntry = getWalletForm().getWalletUtxosEntry();
            for(Entry entry : walletUtxosEntry.getChildren()) {
                UtxoEntry utxoEntry = (UtxoEntry)entry;
                if(utxoEntry.getMixStatus() != null && utxoEntry.getMixStatus().getMixProgress() != null
                        && utxoEntry.getMixStatus().getMixProgress().getMixStep() != null
                        && utxoEntry.getMixStatus().getMixProgress().getMixStep().isInterruptable()) {
                    whirlpoolMix(new WhirlpoolMixEvent(getWalletForm().getWallet(), utxoEntry.getHashIndex(), (MixProgress)null));
                }
            }
        }

        startMix.setLabel(newValue ? "Stop Mixing" : "Start Mixing");
    };

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
                updateMixSelectedButton();
            }
        });

        updateLabels(walletUtxosEntry);
        updateHistory(getWalletForm().getWalletUtxosEntry());

        Panel buttonPanel = new Panel(new GridLayout(5).setHorizontalSpacing(2).setVerticalSpacing(0));
        if(getWalletForm().getWallet().isWhirlpoolMixWallet()) {
            startMix = new Button("Start Mixing", this::toggleMixing).setSize(new TerminalSize(20, 1)).addTo(buttonPanel);
            startMix.setEnabled(AppServices.onlineProperty().get());

            mixTo = new Button("Mix to...", this::showMixToDialog);
            if(getWalletForm().getWallet().getStandardAccountType() == StandardAccount.WHIRLPOOL_POSTMIX) {
                buttonPanel.addComponent(mixTo);
            } else {
                buttonPanel.addComponent(new EmptySpace(new TerminalSize(15, 1)));
            }

            Whirlpool whirlpool  = AppServices.getWhirlpoolServices().getWhirlpool(getWalletForm().getWallet());
            if(whirlpool != null) {
                startMix.setLabel(whirlpool.isMixing() ? "Stop Mixing" : "Start Mixing");
                if(whirlpool.startingProperty().getValue()) {
                    mixingStartingListener.changed(whirlpool.startingProperty(), null, whirlpool.startingProperty().getValue());
                }
                whirlpool.startingProperty().addListener(new WeakChangeListener<>(mixingStartingListener));
                if(whirlpool.stoppingProperty().getValue()) {
                    mixingStoppingListener.changed(whirlpool.stoppingProperty(), null, whirlpool.stoppingProperty().getValue());
                }
                whirlpool.stoppingProperty().addListener(new WeakChangeListener<>(mixingStoppingListener));
                whirlpool.mixingProperty().addListener(new WeakChangeListener<>(mixingListener));
                updateMixToButton();
            }

            AppServices.onlineProperty().addListener(new WeakChangeListener<>(mixingOnlineListener));

            buttonPanel.addComponent(new EmptySpace(new TerminalSize(15, 1)));
            buttonPanel.addComponent(new Button("Back", () -> onBack(Function.UTXOS)));
            buttonPanel.addComponent(new Button("Refresh", this::onRefresh));
        } else {
            if(WhirlpoolServices.canWalletMix(getWalletForm().getWallet())) {
                mixSelected = new Button("Mix Selected", this::mixSelected);
                mixSelected.setEnabled(false);
                buttonPanel.addComponent(mixSelected);
            } else {
                buttonPanel.addComponent(new EmptySpace(new TerminalSize(15, 1)));
            }

            buttonPanel.addComponent(new EmptySpace(new TerminalSize(15, 1)));
            buttonPanel.addComponent(new EmptySpace(new TerminalSize(15, 1)));
            buttonPanel.addComponent(new Button("Back", () -> onBack(Function.UTXOS)));
            buttonPanel.addComponent(new Button("Refresh", this::onRefresh));
        }

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

            if(AppServices.getFiatCurrencyExchangeRate() != null) {
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

    private boolean isMixing() {
        Whirlpool whirlpool  = AppServices.getWhirlpoolServices().getWhirlpool(getWalletForm().getWallet());
        return whirlpool != null && whirlpool.isMixing();
    }

    public void toggleMixing() {
        if(isMixing()) {
            stopMixing();
        } else {
            startMixing();
        }
    }

    public void startMixing() {
        startMix.setEnabled(false);

        Platform.runLater(() -> {
            getWalletForm().getWallet().getMasterMixConfig().setMixOnStartup(Boolean.TRUE);
            EventManager.get().post(new WalletMasterMixConfigChangedEvent(getWalletForm().getWallet()));

            Whirlpool whirlpool = AppServices.getWhirlpoolServices().getWhirlpool(getWalletForm().getWallet());
            if(whirlpool != null && !whirlpool.isStarted() && AppServices.isConnected()) {
                AppServices.getWhirlpoolServices().startWhirlpool(getWalletForm().getWallet(), whirlpool, true);
            }
        });
    }

    public void stopMixing() {
        startMix.setEnabled(AppServices.onlineProperty().get());

        Platform.runLater(() -> {
            getWalletForm().getWallet().getMasterMixConfig().setMixOnStartup(Boolean.FALSE);
            EventManager.get().post(new WalletMasterMixConfigChangedEvent(getWalletForm().getWallet()));

            Whirlpool whirlpool = AppServices.getWhirlpoolServices().getWhirlpool(getWalletForm().getWallet());
            if(whirlpool.isStarted()) {
                AppServices.getWhirlpoolServices().stopWhirlpool(whirlpool, true);
            } else {
                //Ensure http clients are shutdown
                whirlpool.shutdown();
            }
        });
    }

    public void showMixToDialog() {
        MixToDialog mixToDialog = new MixToDialog(getWalletForm());
        MixConfig changedMixConfig = (MixConfig)mixToDialog.showDialog(SparrowTerminal.get().getGui());

        if(changedMixConfig != null) {
            MixConfig mixConfig = getWalletForm().getWallet().getMasterMixConfig();

            mixConfig.setMixToWalletName(changedMixConfig.getMixToWalletName());
            mixConfig.setMixToWalletFile(changedMixConfig.getMixToWalletFile());
            mixConfig.setMinMixes(changedMixConfig.getMinMixes());
            mixConfig.setIndexRange(changedMixConfig.getIndexRange());

            Platform.runLater(() -> {
                EventManager.get().post(new WalletMasterMixConfigChangedEvent(getWalletForm().getWallet()));

                Whirlpool whirlpool = AppServices.getWhirlpoolServices().getWhirlpool(getWalletForm().getWallet());
                whirlpool.setPostmixIndexRange(mixConfig.getIndexRange());
                try {
                    String mixToWalletId = AppServices.getWhirlpoolServices().getWhirlpoolMixToWalletId(mixConfig);
                    whirlpool.setMixToWallet(mixToWalletId, mixConfig.getMinMixes());
                } catch(NoSuchElementException e) {
                    mixConfig.setMixToWalletName(null);
                    mixConfig.setMixToWalletFile(null);
                    EventManager.get().post(new WalletMasterMixConfigChangedEvent(getWalletForm().getWallet()));
                    whirlpool.setMixToWallet(null, null);
                }

                SparrowTerminal.get().getGuiThread().invokeLater(this::updateMixToButton);
                if(whirlpool.isStarted()) {
                    //Will automatically restart
                    AppServices.getWhirlpoolServices().stopWhirlpool(whirlpool, false);
                }
            });
        }
    }

    private void updateMixToButton() {
        if(mixTo == null) {
            return;
        }

        MixConfig mixConfig = getWalletForm().getWallet().getMasterMixConfig();
        if(mixConfig != null && mixConfig.getMixToWalletName() != null) {
            mixTo.setLabel("Mixing to " + mixConfig.getMixToWalletName());
            try {
                String mixToWalletId = AppServices.getWhirlpoolServices().getWhirlpoolMixToWalletId(mixConfig);
                String mixToName = AppServices.get().getWallet(mixToWalletId).getFullDisplayName();
                mixTo.setLabel("Mixing to " + mixToName);
            } catch(NoSuchElementException e) {
                mixTo.setLabel("! Not Open");
            }
        } else {
            mixTo.setLabel("Mix to...");
        }
    }

    private void updateMixSelectedButton() {
        if(mixSelected == null) {
            return;
        }

        mixSelected.setEnabled(!getSelectedEntries().isEmpty());
    }

    private List<UtxoEntry> getSelectedEntries() {
        return utxos.getTableModel().getRows().stream().map(row -> row.get(0)).filter(TableCell::isSelected).map(dateCell -> (UtxoEntry)dateCell.getEntry()).collect(Collectors.toList());
    }

    private void mixSelected() {
        MixDialog mixDialog = new MixDialog(getWalletForm().getMasterWalletId(), getWalletForm(), getSelectedEntries());
        Pool pool = mixDialog.showDialog(SparrowTerminal.get().getGui());

        if(pool != null) {
            Wallet wallet = getWalletForm().getWallet();
            if(wallet.isMasterWallet() && !wallet.isWhirlpoolMasterWallet()) {
                addAccount(wallet, StandardAccount.WHIRLPOOL_PREMIX, () -> broadcastPremix(pool));
            } else {
                Platform.runLater(() -> broadcastPremix(pool));
            }
        }
    }

    public void broadcastPremix(Pool pool) {
        ModalDialog broadcastingDialog = new ModalDialog(getWalletForm().getWallet().getFullDisplayName(), "Broadcasting premix...");
        SparrowTerminal.get().getGuiThread().invokeLater(() -> SparrowTerminal.get().getGui().addWindow(broadcastingDialog));

        //The WhirlpoolWallet has already been configured
        Whirlpool whirlpool = AppServices.getWhirlpoolServices().getWhirlpool(getWalletForm().getStorage().getWalletId(getWalletForm().getMasterWallet()));
        List<BlockTransactionHashIndex> utxos = getSelectedEntries().stream().map(HashIndexEntry::getHashIndex).collect(Collectors.toList());
        Whirlpool.Tx0BroadcastService tx0BroadcastService = new Whirlpool.Tx0BroadcastService(whirlpool, pool, utxos);
        tx0BroadcastService.setOnSucceeded(workerStateEvent -> {
            Sha256Hash txid = tx0BroadcastService.getValue();
            SparrowTerminal.get().getGuiThread().invokeLater(() -> {
                SparrowTerminal.get().getGui().removeWindow(broadcastingDialog);
                AppServices.showSuccessDialog("Broadcast Successful", "Premix transaction id:\n" + txid.toString());
            });
        });
        tx0BroadcastService.setOnFailed(workerStateEvent -> {
            Throwable exception = workerStateEvent.getSource().getException();
            while(exception.getCause() != null) {
                exception = exception.getCause();
            }
            String message = exception.getMessage();
            SparrowTerminal.get().getGuiThread().invokeLater(() -> {
                SparrowTerminal.get().getGui().removeWindow(broadcastingDialog);
                AppServices.showErrorDialog("Error broadcasting premix transaction", message);
            });
        });
        tx0BroadcastService.start();
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
    public void whirlpoolMix(WhirlpoolMixEvent event) {
        if(event.getWallet().equals(getWalletForm().getWallet())) {
            WalletUtxosEntry walletUtxosEntry = getWalletForm().getWalletUtxosEntry();
            for(Entry entry : walletUtxosEntry.getChildren()) {
                UtxoEntry utxoEntry = (UtxoEntry)entry;
                if(utxoEntry.getHashIndex().equals(event.getUtxo())) {
                    if(event.getNextUtxo() != null) {
                        utxoEntry.setNextMixUtxo(event.getNextUtxo());
                    } else if(event.getMixFailReason() != null) {
                        utxoEntry.setMixFailReason(event.getMixFailReason(), event.getMixError());
                    } else {
                        utxoEntry.setMixProgress(event.getMixProgress());
                    }

                    TableModel<TableCell> tableModel = utxos.getTableModel();
                    for(int row = 0; row < tableModel.getRowCount(); row++) {
                        UtxoEntry tableEntry = (UtxoEntry)tableModel.getRow(row).get(0).getEntry();
                        if(tableEntry.getHashIndex().equals(event.getUtxo())) {
                            final int utxoRow = row;
                            SparrowTerminal.get().getGuiThread().invokeLater(() -> {
                                tableModel.setCell(2, utxoRow, new MixTableCell(utxoEntry));
                            });
                        }
                    }
                }
            }
        }
    }

    @Subscribe
    public void newBlock(NewBlockEvent event) {
        Whirlpool whirlpool = AppServices.getWhirlpoolServices().getWhirlpool(getWalletForm().getWallet());
        if(whirlpool != null) {
            for(Entry entry : getWalletForm().getWalletUtxosEntry().getChildren()) {
                UtxoEntry utxoEntry = (UtxoEntry)entry;
                MixProgress mixProgress = whirlpool.getMixProgress(utxoEntry.getHashIndex());
                if(mixProgress != null || utxoEntry.getMixStatus() == null || (utxoEntry.getMixStatus().getMixFailReason() == null && utxoEntry.getMixStatus().getNextMixUtxo() == null)) {
                    whirlpoolMix(new WhirlpoolMixEvent(getWalletForm().getWallet(), utxoEntry.getHashIndex(), mixProgress));
                }
            }
        }
    }

    @Subscribe
    public void openWallets(OpenWalletsEvent event) {
        SparrowTerminal.get().getGuiThread().invokeLater(this::updateMixToButton);
    }

    @Subscribe
    public void walletLabelChanged(WalletLabelChangedEvent event) {
        SparrowTerminal.get().getGuiThread().invokeLater(this::updateMixToButton);
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
