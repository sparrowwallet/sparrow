package com.sparrowwallet.sparrow.control;

import com.sparrowwallet.drongo.Utils;
import com.sparrowwallet.drongo.address.Address;
import com.sparrowwallet.drongo.protocol.Transaction;
import com.sparrowwallet.sparrow.EventManager;
import com.sparrowwallet.sparrow.event.ReceiveActionEvent;
import com.sparrowwallet.sparrow.wallet.Entry;
import com.sparrowwallet.sparrow.wallet.NodeEntry;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.event.Event;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.control.cell.TextFieldTreeTableCell;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.HBox;
import javafx.scene.text.Font;
import javafx.util.converter.DefaultStringConverter;
import org.controlsfx.glyphfont.FontAwesome;
import org.controlsfx.glyphfont.Glyph;

import java.lang.reflect.Field;
import java.util.Locale;

public class AddressTreeTable extends TreeTableView<Entry> {
    public void initialize(NodeEntry rootEntry) {
        getStyleClass().add("address-treetable");

        String address = null;
        TreeItem<Entry> rootItem = new TreeItem<>(rootEntry);
        for(Entry childEntry : rootEntry.getChildren()) {
            TreeItem<Entry> childItem = new TreeItem<>(childEntry);
            rootItem.getChildren().add(childItem);
            address = rootEntry.getNode().getAddress().toString();
        }

        rootItem.setExpanded(true);
        setRoot(rootItem);
        setShowRoot(false);

        TreeTableColumn<Entry, Entry> addressCol = new TreeTableColumn<>("Address / Outpoints");
        addressCol.setCellValueFactory((TreeTableColumn.CellDataFeatures<Entry, Entry> param) -> {
            return new ReadOnlyObjectWrapper<>(param.getValue().getValue());
        });
        addressCol.setCellFactory(p -> new DataCell());
        addressCol.setSortable(false);
        getColumns().add(addressCol);

        if(address != null) {
            addressCol.setMinWidth(TextUtils.computeTextWidth(Font.font("Courier"), address, 0.0));
        }

        TreeTableColumn<Entry, String> labelCol = new TreeTableColumn<>("Label");
        labelCol.setCellValueFactory((TreeTableColumn.CellDataFeatures<Entry, String> param) -> {
            return param.getValue().getValue().labelProperty();
        });
        labelCol.setCellFactory(p -> new LabelCell());
        labelCol.setSortable(false);
        getColumns().add(labelCol);

        TreeTableColumn<Entry, Long> amountCol = new TreeTableColumn<>("Amount");
        amountCol.setCellValueFactory((TreeTableColumn.CellDataFeatures<Entry, Long> param) -> {
            return new ReadOnlyObjectWrapper<>(param.getValue().getValue().getAmount());
        });
        amountCol.setCellFactory(p -> new AmountCell());
        amountCol.setSortable(false);
        getColumns().add(amountCol);

        TreeTableColumn<Entry, Entry> actionCol = new TreeTableColumn<>("Actions");
        actionCol.setCellValueFactory((TreeTableColumn.CellDataFeatures<Entry, Entry> param) -> {
            return new ReadOnlyObjectWrapper<>(param.getValue().getValue());
        });
        actionCol.setCellFactory(p -> new ActionCell());
        actionCol.setSortable(false);
        getColumns().add(actionCol);

        setEditable(true);
        setColumnResizePolicy(TreeTableView.CONSTRAINED_RESIZE_POLICY);
    }

    private static class DataCell extends TreeTableCell<Entry, Entry> {
        public DataCell() {
            super();
            getStyleClass().add("address-cell");
        }

        @Override
        protected void updateItem(Entry entry, boolean empty) {
            super.updateItem(entry, empty);

            if(empty) {
                setText(null);
                setGraphic(null);
            } else {
                if(entry instanceof NodeEntry) {
                    NodeEntry nodeEntry = (NodeEntry)entry;
                    Address address = nodeEntry.getNode().getAddress();
                    setText(address.toString());
                    setContextMenu(new AddressContextMenu(address));
                } else {
                    //TODO: Add transaction outpoint
                }
            }
        }
    }

    private static class AddressContextMenu extends ContextMenu {
        public AddressContextMenu(Address address) {
            MenuItem copyAddress = new MenuItem("Copy Address");
            copyAddress.setOnAction(AE -> {
                hide();
                ClipboardContent content = new ClipboardContent();
                content.putString(address.toString());
                Clipboard.getSystemClipboard().setContent(content);
            });

            MenuItem copyHex = new MenuItem("Copy Script Output Bytes");
            copyHex.setOnAction(AE -> {
                hide();
                ClipboardContent content = new ClipboardContent();
                content.putString(Utils.bytesToHex(address.getOutputScriptData()));
                Clipboard.getSystemClipboard().setContent(content);
            });

            getItems().addAll(copyAddress, copyHex);
        }
    }

    private static class LabelCell extends TextFieldTreeTableCell<Entry, String> {
        public LabelCell() {
            super(new DefaultStringConverter());
            getStyleClass().add("label-cell");
        }

        @Override
        public void updateItem(String label, boolean empty) {
            super.updateItem(label, empty);

            if(empty) {
                setText(null);
                setGraphic(null);
            } else {
                setText(label);
                setContextMenu(new LabelContextMenu(label));
            }
        }

        @Override
        public void commitEdit(String label) {
            // This block is necessary to support commit on losing focus, because
            // the baked-in mechanism sets our editing state to false before we can
            // intercept the loss of focus. The default commitEdit(...) method
            // simply bails if we are not editing...
            if (!isEditing() && !label.equals(getItem())) {
                TreeTableView<Entry> treeTable = getTreeTableView();
                if(treeTable != null) {
                    TreeTableColumn<Entry, String> column = getTableColumn();
                    TreeTableColumn.CellEditEvent<Entry, String> event = new TreeTableColumn.CellEditEvent<>(
                            treeTable, new TreeTablePosition<>(treeTable, getIndex(), column),
                            TreeTableColumn.editCommitEvent(), label
                    );
                    Event.fireEvent(column, event);
                }
            }

            super.commitEdit(label);
        }

        @Override
        public void startEdit() {
            super.startEdit();

            try {
                Field f = getClass().getSuperclass().getDeclaredField("textField");
                f.setAccessible(true);
                TextField textField = (TextField)f.get(this);
                textField.focusedProperty().addListener((obs, wasFocused, isNowFocused) -> {
                    if (!isNowFocused) {
                        commitEdit(getConverter().fromString(textField.getText()));
                        setText(getConverter().fromString(textField.getText()));
                    }
                });
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private static class LabelContextMenu extends ContextMenu {
        public LabelContextMenu(String label) {
            MenuItem copyLabel = new MenuItem("Copy Label");
            copyLabel.setOnAction(AE -> {
                hide();
                ClipboardContent content = new ClipboardContent();
                content.putString(label);
                Clipboard.getSystemClipboard().setContent(content);
            });

            getItems().add(copyLabel);
        }
    }

    private static class AmountCell extends TreeTableCell<Entry, Long> {
        public AmountCell() {
            super();
            getStyleClass().add("amount-cell");
        }

        @Override
        protected void updateItem(Long amount, boolean empty) {
            super.updateItem(amount, empty);

            if(empty || amount == null) {
                setText(null);
                setGraphic(null);
            } else {
                String satsValue = String.format(Locale.ENGLISH, "%,d", amount) + " sats";
                String btcValue = CoinLabel.BTC_FORMAT.format(amount.doubleValue() / Transaction.SATOSHIS_PER_BITCOIN) + " BTC";
                Tooltip tooltip = new Tooltip();
                if(amount > CoinLabel.MAX_SATS_SHOWN) {
                    tooltip.setText(satsValue);
                    setText(btcValue);
                } else {
                    tooltip.setText(btcValue);
                    setText(satsValue);
                }
                setTooltip(tooltip);
            }
        }
    }

    private static class ActionCell extends TreeTableCell<Entry, Entry> {
        private final HBox actionBox;
        private final Button receiveButton;

        public ActionCell() {
            super();
            getStyleClass().add("action-cell");

            actionBox = new HBox();
            actionBox.setSpacing(8);
            actionBox.setAlignment(Pos.CENTER);

            receiveButton = new Button("");
            Glyph receiveGlyph = new Glyph("FontAwesome", FontAwesome.Glyph.ARROW_DOWN);
            receiveGlyph.setFontSize(12);
            receiveButton.setGraphic(receiveGlyph);
            receiveButton.setOnAction(event -> {
                NodeEntry nodeEntry = (NodeEntry)getTreeTableView().getTreeItem(getIndex()).getValue();
                EventManager.get().post(new ReceiveActionEvent(nodeEntry));
            });
        }

        @Override
        protected void updateItem(Entry entry, boolean empty) {
            super.updateItem(entry, empty);
            if (empty) {
                setGraphic(null);
            } else {
                actionBox.getChildren().remove(0, actionBox.getChildren().size());
                if(entry instanceof NodeEntry) {
                    actionBox.getChildren().add(receiveButton);
                }

                setGraphic(actionBox);
            }
        }
    }
}
