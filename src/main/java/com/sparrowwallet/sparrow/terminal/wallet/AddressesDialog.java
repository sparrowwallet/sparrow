package com.sparrowwallet.sparrow.terminal.wallet;

import com.google.common.eventbus.Subscribe;
import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.gui2.*;
import com.googlecode.lanterna.gui2.table.Table;
import com.googlecode.lanterna.gui2.table.TableModel;
import com.sparrowwallet.drongo.KeyPurpose;
import com.sparrowwallet.sparrow.event.UnitFormatChangedEvent;
import com.sparrowwallet.sparrow.event.WalletHistoryChangedEvent;
import com.sparrowwallet.sparrow.event.WalletNodesChangedEvent;
import com.sparrowwallet.sparrow.terminal.SparrowTerminal;
import com.sparrowwallet.sparrow.terminal.wallet.table.AddressTableCell;
import com.sparrowwallet.sparrow.terminal.wallet.table.CoinTableCell;
import com.sparrowwallet.sparrow.terminal.wallet.table.TableCell;
import com.sparrowwallet.sparrow.wallet.Entry;
import com.sparrowwallet.sparrow.wallet.Function;
import com.sparrowwallet.sparrow.wallet.NodeEntry;
import com.sparrowwallet.sparrow.wallet.WalletForm;

import java.util.List;

public class AddressesDialog extends WalletDialog {
    private final Table<TableCell> receiveTable;
    private final Table<TableCell> changeTable;

    public AddressesDialog(WalletForm walletForm) {
        super(walletForm.getWallet().getFullDisplayName() + " Addresses", walletForm);

        setHints(List.of(Hint.CENTERED, Hint.EXPANDED));

        String[] tableColumns = getTableColumns();
        receiveTable = new Table<>(tableColumns);
        receiveTable.setTableCellRenderer(new EntryTableCellRenderer());

        changeTable = new Table<>(tableColumns);
        changeTable.setTableCellRenderer(new EntryTableCellRenderer());

        updateAddresses();

        Panel buttonPanel = new Panel(new GridLayout(5).setHorizontalSpacing(2).setVerticalSpacing(0));
        buttonPanel.addComponent(new EmptySpace(new TerminalSize(15, 1)));
        buttonPanel.addComponent(new EmptySpace(new TerminalSize(15, 1)));
        buttonPanel.addComponent(new EmptySpace(new TerminalSize(15, 1)));
        buttonPanel.addComponent(new Button("Back", () -> onBack(Function.ADDRESSES)));
        buttonPanel.addComponent(new Button("Refresh", this::onRefresh));

        Panel mainPanel = new Panel();
        mainPanel.setLayoutManager(new LinearLayout(Direction.VERTICAL).setSpacing(1));
        mainPanel.addComponent(receiveTable.withBorder(new EmptyBorder("Receive")));
        mainPanel.addComponent(changeTable.withBorder(new EmptyBorder("Change")));
        mainPanel.addComponent(buttonPanel);

        setComponent(mainPanel);

        configureTable(receiveTable, KeyPurpose.RECEIVE);
        configureTable(changeTable, KeyPurpose.CHANGE);
    }

    private void configureTable(Table<TableCell> table, KeyPurpose keyPurpose) {
        table.setSelectAction(() -> {
            NodeEntry nodeEntry = (NodeEntry)getWalletForm().getNodeEntry(keyPurpose).getChildren().get(table.getSelectedRow());
            close();
            WalletData walletData = SparrowTerminal.get().getWalletData().get(getWalletForm().getWalletId());
            ReceiveDialog receiveDialog = walletData.getReceiveDialog();
            receiveDialog.setNodeEntry(nodeEntry);
            receiveDialog.showDialog(SparrowTerminal.get().getGui());
        });

        Integer highestUsedReceiveIndex = getWalletForm().getNodeEntry(keyPurpose).getNode().getHighestUsedIndex();
        if(highestUsedReceiveIndex != null) {
            table.setSelectedRow(highestUsedReceiveIndex + 1);
        }
    }

    private String[] getTableColumns() {
        String address = getWalletForm().getNodeEntry(KeyPurpose.RECEIVE).getAddress().toString();
        return new String[] {centerPad("Address", Math.max(AddressTableCell.ADDRESS_MIN_WIDTH, address.length())), centerPad("Value", CoinTableCell.UTXO_WIDTH)};
    }

    private void updateAddressesLater() {
        SparrowTerminal.get().getGuiThread().invokeLater(this::updateAddresses);
    }

    private void updateAddresses() {
        receiveTable.setTableModel(getTableModel(getWalletForm().getNodeEntry(KeyPurpose.RECEIVE)));
        changeTable.setTableModel(getTableModel(getWalletForm().getNodeEntry(KeyPurpose.CHANGE)));
    }

    private TableModel<TableCell> getTableModel(NodeEntry nodeEntry) {
        TableModel<TableCell> tableModel = new TableModel<>(getTableColumns());
        for(Entry addressEntry : nodeEntry.getChildren()) {
            tableModel.addRow(new AddressTableCell(addressEntry), new CoinTableCell(addressEntry, false));
        }

        return tableModel;
    }

    @Subscribe
    public void walletNodesChanged(WalletNodesChangedEvent event) {
        if(event.getWallet().equals(getWalletForm().getWallet())) {
            updateAddressesLater();
        }
    }

    @Subscribe
    public void walletHistoryChanged(WalletHistoryChangedEvent event) {
        if(event.getWallet().equals(getWalletForm().getWallet())) {
            updateAddressesLater();
        }
    }

    @Subscribe
    public void unitFormatChanged(UnitFormatChangedEvent event) {
        updateAddressesLater();
    }
}
