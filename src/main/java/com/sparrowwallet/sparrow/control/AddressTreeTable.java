package com.sparrowwallet.sparrow.control;

import com.sparrowwallet.drongo.wallet.WalletNode;
import com.sparrowwallet.sparrow.AppServices;
import com.sparrowwallet.sparrow.EventManager;
import com.sparrowwallet.sparrow.event.ReceiveActionEvent;
import com.sparrowwallet.sparrow.event.ReceiveToEvent;
import com.sparrowwallet.sparrow.event.ShowTransactionsCountEvent;
import com.sparrowwallet.sparrow.io.Config;
import com.sparrowwallet.sparrow.wallet.Entry;
import com.sparrowwallet.sparrow.wallet.NodeEntry;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.ListChangeListener;
import javafx.scene.control.*;
import javafx.scene.input.MouseButton;

import java.util.*;

public class AddressTreeTable extends CoinTreeTable {
    public void initialize(NodeEntry rootEntry) {
        getStyleClass().add("address-treetable");
        setUnitFormat(rootEntry.getWallet());

        String address = rootEntry.getAddress().toString();
        updateAll(rootEntry);
        setShowRoot(false);

        TreeTableColumn<Entry, Entry> addressCol = new TreeTableColumn<>("Address / Outpoints");
        addressCol.setCellValueFactory((TreeTableColumn.CellDataFeatures<Entry, Entry> param) -> {
            return new ReadOnlyObjectWrapper<>(param.getValue().getValue());
        });
        addressCol.setCellFactory(p -> new EntryCell());
        addressCol.setSortable(false);
        getColumns().add(addressCol);

        if(address != null && !rootEntry.getWallet().isWhirlpoolChildWallet()) {
            addressCol.setMinWidth(TextUtils.computeTextWidth(AppServices.getMonospaceFont(), address, 0.0));
        }

        TreeTableColumn<Entry, String> labelCol = new TreeTableColumn<>("Label");
        labelCol.setCellValueFactory((TreeTableColumn.CellDataFeatures<Entry, String> param) -> {
            return param.getValue().getValue().labelProperty();
        });
        labelCol.setCellFactory(p -> new LabelCell());
        labelCol.setSortable(false);
        getColumns().add(labelCol);

        TreeTableColumn<Entry, Number> countCol = new TreeTableColumn<>("Transactions");
        countCol.setCellValueFactory((TreeTableColumn.CellDataFeatures<Entry, Number> param) -> {
            return new ReadOnlyObjectWrapper<>(param.getValue().getValue().getChildren().size());
        });
        countCol.setCellFactory(p -> new NumberCell());
        countCol.setSortable(false);
        countCol.setVisible(Config.get().isShowAddressTransactionCount());
        getColumns().add(countCol);

        TreeTableColumn<Entry, Number> amountCol = new TreeTableColumn<>("Value");
        amountCol.setCellValueFactory((TreeTableColumn.CellDataFeatures<Entry, Number> param) -> {
            return new ReadOnlyObjectWrapper<>(param.getValue().getValue().getValue());
        });
        amountCol.setCellFactory(p -> new CoinCell());
        amountCol.setSortable(false);
        getColumns().add(amountCol);

        ContextMenu contextMenu = new ContextMenu();
        CheckMenuItem showCountItem = new CheckMenuItem("Show Transaction Count");
        contextMenu.setOnShowing(event -> {
            showCountItem.setSelected(Config.get().isShowAddressTransactionCount());
        });
        showCountItem.setOnAction(event -> {
            boolean show = !Config.get().isShowAddressTransactionCount();
            Config.get().setShowAddressTransactionCount(show);
            EventManager.get().post(new ShowTransactionsCountEvent(show));
        });
        contextMenu.getItems().add(showCountItem);
        getColumns().forEach(col -> col.setContextMenu(contextMenu));

        setEditable(true);
        setColumnResizePolicy(TreeTableView.CONSTRAINED_RESIZE_POLICY);

        addressCol.setSortType(TreeTableColumn.SortType.ASCENDING);
        getSortOrder().add(addressCol);

        Integer highestUsedIndex = rootEntry.getNode().getHighestUsedIndex();
        if(highestUsedIndex != null) {
            OptionalInt tableIndex = rootEntry.getChildren().stream().filter(childEntry -> ((NodeEntry)childEntry).getNode().getIndex() == highestUsedIndex + 1).mapToInt(childEntry -> rootEntry.getChildren().indexOf(childEntry)).findFirst();
            if(tableIndex.isPresent() && tableIndex.getAsInt() > 5) {
                scrollTo(tableIndex.getAsInt());
            }
        }

        if(!rootEntry.getWallet().isWhirlpoolChildWallet()) {
            setOnMouseClicked(mouseEvent -> {
                if(mouseEvent.getButton().equals(MouseButton.PRIMARY)){
                    if(mouseEvent.getClickCount() == 2) {
                        TreeItem<Entry> treeItem = getSelectionModel().getSelectedItem();
                        if(treeItem != null && treeItem.getChildren().isEmpty()) {
                            Entry entry = getSelectionModel().getSelectedItem().getValue();
                            if(entry instanceof NodeEntry) {
                                NodeEntry nodeEntry = (NodeEntry)entry;
                                EventManager.get().post(new ReceiveActionEvent(nodeEntry));
                                Platform.runLater(() -> EventManager.get().post(new ReceiveToEvent(nodeEntry)));
                            }
                        }
                    }
                }
            });
        }

        rootEntry.getChildren().addListener((ListChangeListener<Entry>) c -> {
            this.refresh();
        });
    }

    public void updateAll(NodeEntry rootEntry) {
        setUnitFormat(rootEntry.getWallet());

        RecursiveTreeItem<Entry> rootItem = new RecursiveTreeItem<>(rootEntry, Entry::getChildren);
        setRoot(rootItem);
        rootItem.setExpanded(true);

        if(getColumns().size() > 0 && getSortOrder().isEmpty()) {
            TreeTableColumn<Entry, ?> addressCol = getColumns().get(0);
            getSortOrder().add(addressCol);
            addressCol.setSortType(TreeTableColumn.SortType.ASCENDING);
        }
    }

    public void updateHistory(List<WalletNode> updatedNodes) {
        //We only ever add child nodes - never remove in order to keep a full sequence (unless hide empty used addresses is set)
        NodeEntry rootEntry = (NodeEntry)getRoot().getValue();

        Map<WalletNode, NodeEntry> childNodes = new HashMap<>();
        for(Entry childEntry : rootEntry.getChildren()) {
            NodeEntry nodeEntry = (NodeEntry)childEntry;
            childNodes.put(nodeEntry.getNode(), nodeEntry);
        }

        for(WalletNode updatedNode : updatedNodes) {
            NodeEntry existingEntry = childNodes.get(updatedNode);
            if(existingEntry != null) {
                existingEntry.refreshChildren();

                if(Config.get().isHideEmptyUsedAddresses() && existingEntry.getValue() == 0L) {
                    rootEntry.getChildren().remove(existingEntry);
                }
            } else {
                NodeEntry nodeEntry = new NodeEntry(rootEntry.getWallet(), updatedNode);

                if(Config.get().isHideEmptyUsedAddresses()) {
                    int index = 0;
                    for( ; index < rootEntry.getChildren().size(); index++) {
                        existingEntry = (NodeEntry)rootEntry.getChildren().get(index);
                        if(nodeEntry.compareTo(existingEntry) < 0) {
                             break;
                        }
                    }
                    rootEntry.getChildren().add(index, nodeEntry);
                } else {
                    rootEntry.getChildren().add(nodeEntry);
                }
            }
        }

        refresh();
    }

    public void updateLabel(Entry entry) {
        Entry rootEntry = getRoot().getValue();
        rootEntry.updateLabel(entry);
    }

    public void showTransactionsCount(boolean show) {
        getColumns().stream().filter(col -> col.getText().equals("Transactions")).forEach(col -> col.setVisible(show));
    }
}
