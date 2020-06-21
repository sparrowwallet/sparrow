package com.sparrowwallet.sparrow.control;

import com.sparrowwallet.drongo.wallet.WalletNode;
import com.sparrowwallet.sparrow.wallet.Entry;
import com.sparrowwallet.sparrow.wallet.TransactionEntry;
import com.sparrowwallet.sparrow.wallet.WalletTransactionsEntry;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.scene.control.TreeTableColumn;
import javafx.scene.control.TreeTableView;

import java.util.List;

public class TransactionsTreeTable extends TreeTableView<Entry> {
    public void initialize(WalletTransactionsEntry rootEntry) {
        getStyleClass().add("transactions-treetable");

        updateAll(rootEntry);
        setShowRoot(false);

        TreeTableColumn<Entry, Entry> dateCol = new TreeTableColumn<>("Date");
        dateCol.setCellValueFactory((TreeTableColumn.CellDataFeatures<Entry, Entry> param) -> {
            return new ReadOnlyObjectWrapper<>(param.getValue().getValue());
        });
        dateCol.setCellFactory(p -> new EntryCell());
        dateCol.setSortable(true);
        getColumns().add(dateCol);

        TreeTableColumn<Entry, String> labelCol = new TreeTableColumn<>("Label");
        labelCol.setCellValueFactory((TreeTableColumn.CellDataFeatures<Entry, String> param) -> {
            return param.getValue().getValue().labelProperty();
        });
        labelCol.setCellFactory(p -> new LabelCell());
        labelCol.setSortable(true);
        getColumns().add(labelCol);

        TreeTableColumn<Entry, Long> amountCol = new TreeTableColumn<>("Value");
        amountCol.setCellValueFactory((TreeTableColumn.CellDataFeatures<Entry, Long> param) -> {
            return new ReadOnlyObjectWrapper<>(param.getValue().getValue().getValue());
        });
        amountCol.setCellFactory(p -> new AmountCell());
        amountCol.setSortable(true);
        getColumns().add(amountCol);

        TreeTableColumn<Entry, Long> balanceCol = new TreeTableColumn<>("Balance");
        balanceCol.setCellValueFactory((TreeTableColumn.CellDataFeatures<Entry, Long> param) -> {
            return new ReadOnlyObjectWrapper<>(param.getValue().getValue() instanceof TransactionEntry ? ((TransactionEntry)param.getValue().getValue()).getBalance() : null);
        });
        balanceCol.setCellFactory(p -> new AmountCell());
        balanceCol.setSortable(true);
        getColumns().add(balanceCol);

        setEditable(true);
        setColumnResizePolicy(TreeTableView.CONSTRAINED_RESIZE_POLICY);
        dateCol.setSortType(TreeTableColumn.SortType.DESCENDING);
        getSortOrder().add(dateCol);
    }

    public void updateAll(WalletTransactionsEntry rootEntry) {
        RecursiveTreeItem<Entry> rootItem = new RecursiveTreeItem<>(rootEntry, Entry::getChildren);
        setRoot(rootItem);
        rootItem.setExpanded(true);
    }

    public void updateHistory(List<WalletNode> updatedNodes) {
        WalletTransactionsEntry rootEntry = (WalletTransactionsEntry)getRoot().getValue();
        rootEntry.updateTransactions();
    }
}
