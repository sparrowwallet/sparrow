package com.sparrowwallet.sparrow.control;

import com.sparrowwallet.drongo.wallet.WalletNode;
import com.sparrowwallet.sparrow.wallet.Entry;
import com.sparrowwallet.sparrow.wallet.TransactionEntry;
import com.sparrowwallet.sparrow.wallet.WalletTransactionsEntry;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.scene.control.Label;
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

        TreeTableColumn<Entry, Number> amountCol = new TreeTableColumn<>("Value");
        amountCol.setCellValueFactory((TreeTableColumn.CellDataFeatures<Entry, Number> param) -> {
            return new ReadOnlyObjectWrapper<>(param.getValue().getValue().getValue());
        });
        amountCol.setCellFactory(p -> new AmountCell());
        amountCol.setSortable(true);
        getColumns().add(amountCol);

        TreeTableColumn<Entry, Number> balanceCol = new TreeTableColumn<>("Balance");
        balanceCol.setCellValueFactory((TreeTableColumn.CellDataFeatures<Entry, Number> param) -> {
            return param.getValue().getValue() instanceof TransactionEntry ? ((TransactionEntry)param.getValue().getValue()).balanceProperty() : new ReadOnlyObjectWrapper<>(null);
        });
        balanceCol.setCellFactory(p -> new AmountCell());
        balanceCol.setSortable(true);
        getColumns().add(balanceCol);

        setPlaceholder(new Label("No transactions"));
        setEditable(true);
        setColumnResizePolicy(TreeTableView.CONSTRAINED_RESIZE_POLICY);
        dateCol.setSortType(TreeTableColumn.SortType.DESCENDING);
        getSortOrder().add(dateCol);
    }

    public void updateAll(WalletTransactionsEntry rootEntry) {
        RecursiveTreeItem<Entry> rootItem = new RecursiveTreeItem<>(rootEntry, Entry::getChildren);
        setRoot(rootItem);
        rootItem.setExpanded(true);

        if(getColumns().size() > 0 && getSortOrder().isEmpty()) {
            TreeTableColumn<Entry, ?> dateCol = getColumns().get(0);
            getSortOrder().add(dateCol);
            dateCol.setSortType(TreeTableColumn.SortType.DESCENDING);
        }
    }

    public void updateHistory(List<WalletNode> updatedNodes) {
        //Recalculate from scratch and update according - any changes may affect the balance of other transactions
        WalletTransactionsEntry rootEntry = (WalletTransactionsEntry)getRoot().getValue();
        rootEntry.updateTransactions();
        sort();
    }
}
