package com.sparrowwallet.sparrow.control;

import com.sparrowwallet.sparrow.i18n.LanguagesManager;
import com.sparrowwallet.sparrow.wallet.Entry;
import com.sparrowwallet.sparrow.wallet.TransactionEntry;
import com.sparrowwallet.sparrow.wallet.WalletTransactionsEntry;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.scene.control.TreeTableColumn;
import javafx.scene.control.TreeTableView;

public class TransactionsTreeTable extends CoinTreeTable {
    public void initialize(WalletTransactionsEntry rootEntry) {
        getStyleClass().add("transactions-treetable");
        setUnitFormat(rootEntry.getWallet());

        updateAll(rootEntry);
        setShowRoot(false);

        TreeTableColumn<Entry, Entry> dateCol = new TreeTableColumn<>(LanguagesManager.getMessage("wallet.transactions.table.date"));
        dateCol.setCellValueFactory((TreeTableColumn.CellDataFeatures<Entry, Entry> param) -> {
            return new ReadOnlyObjectWrapper<>(param.getValue().getValue());
        });
        dateCol.setCellFactory(p -> new EntryCell());
        dateCol.setSortable(true);
        getColumns().add(dateCol);

        TreeTableColumn<Entry, String> labelCol = new TreeTableColumn<>(LanguagesManager.getMessage("wallet.transactions.table.label"));
        labelCol.setCellValueFactory((TreeTableColumn.CellDataFeatures<Entry, String> param) -> {
            return param.getValue().getValue().labelProperty();
        });
        labelCol.setCellFactory(p -> new LabelCell());
        labelCol.setSortable(true);
        getColumns().add(labelCol);

        TreeTableColumn<Entry, Number> amountCol = new TreeTableColumn<>(LanguagesManager.getMessage("wallet.transactions.table.value"));
        amountCol.setCellValueFactory((TreeTableColumn.CellDataFeatures<Entry, Number> param) -> {
            return new ReadOnlyObjectWrapper<>(param.getValue().getValue().getValue());
        });
        amountCol.setCellFactory(p -> new CoinCell());
        amountCol.setSortable(true);
        getColumns().add(amountCol);

        TreeTableColumn<Entry, Number> balanceCol = new TreeTableColumn<>(LanguagesManager.getMessage("wallet.transactions.table.balance"));
        balanceCol.setCellValueFactory((TreeTableColumn.CellDataFeatures<Entry, Number> param) -> {
            return param.getValue().getValue() instanceof TransactionEntry ? ((TransactionEntry)param.getValue().getValue()).balanceProperty() : new ReadOnlyObjectWrapper<>(null);
        });
        balanceCol.setCellFactory(p -> new CoinCell());
        balanceCol.setSortable(true);
        getColumns().add(balanceCol);

        setPlaceholder(getDefaultPlaceholder(rootEntry.getWallet()));
        setEditable(true);
        setEqualPreferredColumnWidths();
        setSortColumn(0, TreeTableColumn.SortType.DESCENDING);
    }

    public void updateAll(WalletTransactionsEntry rootEntry) {
        setUnitFormat(rootEntry.getWallet());

        RecursiveTreeItem<Entry> rootItem = new RecursiveTreeItem<>(rootEntry, Entry::getChildren);
        setRoot(rootItem);
        rootItem.setExpanded(true);

        setSortColumn(0, TreeTableColumn.SortType.DESCENDING);
    }

    public void updateHistory() {
        //Transaction entries should have already been updated using WalletTransactionsEntry.updateHistory, so only a resort required
        sort();
        setSortColumn(0, TreeTableColumn.SortType.DESCENDING);
    }

    public void updateLabel(Entry entry) {
        Entry rootEntry = getRoot().getValue();
        rootEntry.updateLabel(entry);
    }
}
