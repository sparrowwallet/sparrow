package com.sparrowwallet.sparrow.control;

import com.sparrowwallet.drongo.Utils;
import com.sparrowwallet.drongo.address.Address;
import com.sparrowwallet.drongo.protocol.Transaction;
import com.sparrowwallet.sparrow.EventManager;
import com.sparrowwallet.sparrow.event.ReceiveActionEvent;
import com.sparrowwallet.sparrow.event.ReceiveToEvent;
import com.sparrowwallet.sparrow.event.ViewTransactionEvent;
import com.sparrowwallet.sparrow.glyphfont.FontAwesome5;
import com.sparrowwallet.sparrow.wallet.Entry;
import com.sparrowwallet.sparrow.wallet.HashIndexEntry;
import com.sparrowwallet.sparrow.wallet.NodeEntry;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.event.Event;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.control.cell.TextFieldTreeTableCell;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.Region;
import javafx.scene.text.Font;
import javafx.util.converter.DefaultStringConverter;
import org.controlsfx.glyphfont.FontAwesome;
import org.controlsfx.glyphfont.Glyph;

import java.lang.reflect.Field;
import java.util.Locale;

public class AddressTreeTable extends TreeTableView<Entry> {
    public void initialize(NodeEntry rootEntry) {
        getStyleClass().add("address-treetable");

        String address = rootEntry.getAddress().toString();
        RecursiveTreeItem<Entry> rootItem = new RecursiveTreeItem<>(rootEntry, Entry::getChildren);
        setRoot(rootItem);

        rootItem.setExpanded(true);
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

        TreeTableColumn<Entry, Long> amountCol = new TreeTableColumn<>("Value");
        amountCol.setCellValueFactory((TreeTableColumn.CellDataFeatures<Entry, Long> param) -> {
            return new ReadOnlyObjectWrapper<>(param.getValue().getValue().getValue());
        });
        amountCol.setCellFactory(p -> new AmountCell());
        amountCol.setSortable(false);
        getColumns().add(amountCol);

        setEditable(true);
        setColumnResizePolicy(TreeTableView.CONSTRAINED_RESIZE_POLICY);

        Integer highestUsedIndex = rootEntry.getNode().getHighestUsedIndex();
        if(highestUsedIndex != null) {
            scrollTo(highestUsedIndex);
        }

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

    private static void applyRowStyles(TreeTableCell<?, ?> cell, Entry entry) {
        cell.getStyleClass().remove("node-row");
        cell.getStyleClass().remove("hashindex-row");
        cell.getStyleClass().remove("spent");

        if(entry != null) {
            if(entry instanceof NodeEntry) {
                cell.getStyleClass().add("node-row");
            } else if(entry instanceof HashIndexEntry) {
                cell.getStyleClass().add("hashindex-row");
                HashIndexEntry hashIndexEntry = (HashIndexEntry)entry;
                if(hashIndexEntry.isSpent()) {
                    cell.getStyleClass().add("spent");
                }
            }
        }
    }

    private static class DataCell extends TreeTableCell<Entry, Entry> {
        public DataCell() {
            super();
            setAlignment(Pos.CENTER_LEFT);
            setContentDisplay(ContentDisplay.RIGHT);
            getStyleClass().add("data-cell");
        }

        @Override
        protected void updateItem(Entry entry, boolean empty) {
            super.updateItem(entry, empty);

            applyRowStyles(this, entry);
            getStyleClass().remove("address-cell");

            if(empty) {
                setText(null);
                setGraphic(null);
            } else {
                if(entry instanceof NodeEntry) {
                    NodeEntry nodeEntry = (NodeEntry)entry;
                    Address address = nodeEntry.getAddress();
                    setText(address.toString());
                    setContextMenu(new AddressContextMenu(address, nodeEntry.getOutputDescriptor()));
                    Tooltip tooltip = new Tooltip();
                    tooltip.setText(nodeEntry.getNode().getDerivationPath());
                    setTooltip(tooltip);
                    getStyleClass().add("address-cell");

                    Button receiveButton = new Button("");
                    Glyph receiveGlyph = new Glyph("FontAwesome", FontAwesome.Glyph.ARROW_DOWN);
                    receiveGlyph.setFontSize(12);
                    receiveButton.setGraphic(receiveGlyph);
                    receiveButton.setOnAction(event -> {
                        EventManager.get().post(new ReceiveActionEvent(nodeEntry));
                        Platform.runLater(() -> EventManager.get().post(new ReceiveToEvent(nodeEntry)));
                    });
                    setGraphic(receiveButton);
                } else if(entry instanceof HashIndexEntry) {
                    HashIndexEntry hashIndexEntry = (HashIndexEntry)entry;
                    setText(hashIndexEntry.getDescription());
                    setContextMenu(new HashIndexEntryContextMenu(hashIndexEntry));
                    Tooltip tooltip = new Tooltip();
                    tooltip.setText(hashIndexEntry.getHashIndex().toString());
                    setTooltip(tooltip);

                    Button viewTransactionButton = new Button("");
                    Glyph searchGlyph = new Glyph(FontAwesome5.FONT_NAME, FontAwesome5.Glyph.SEARCH);
                    searchGlyph.setFontSize(12);
                    viewTransactionButton.setGraphic(searchGlyph);
                    viewTransactionButton.setOnAction(event -> {
                        EventManager.get().post(new ViewTransactionEvent(hashIndexEntry.getBlockTransaction(), hashIndexEntry));
                    });
                    setGraphic(viewTransactionButton);
                }
            }
        }
    }

    private static class AddressContextMenu extends ContextMenu {
        public AddressContextMenu(Address address, String outputDescriptor) {
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

            MenuItem copyOutputDescriptor = new MenuItem("Copy Output Descriptor");
            copyOutputDescriptor.setOnAction(AE -> {
                hide();
                ClipboardContent content = new ClipboardContent();
                content.putString(outputDescriptor);
                Clipboard.getSystemClipboard().setContent(content);
            });

            getItems().addAll(copyAddress, copyHex, copyOutputDescriptor);
        }
    }

    private static class HashIndexEntryContextMenu extends ContextMenu {
        public HashIndexEntryContextMenu(HashIndexEntry hashIndexEntry) {
            String label = "Copy " + (hashIndexEntry.getType().equals(HashIndexEntry.Type.OUTPUT) ? "Transaction Output" : "Transaction Input");
            MenuItem copyHashIndex = new MenuItem(label);
            copyHashIndex.setOnAction(AE -> {
                hide();
                ClipboardContent content = new ClipboardContent();
                content.putString(hashIndexEntry.getHashIndex().toString());
                Clipboard.getSystemClipboard().setContent(content);
            });

            getItems().add(copyHashIndex);
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
                applyRowStyles(this, getTreeTableView().getTreeItem(getIndex()).getValue());

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
            setContentDisplay(ContentDisplay.RIGHT);
        }

        @Override
        protected void updateItem(Long amount, boolean empty) {
            super.updateItem(amount, empty);

            if(empty || amount == null) {
                setText(null);
                setGraphic(null);
            } else {
                applyRowStyles(this, getTreeTableView().getTreeItem(getIndex()).getValue());

                String satsValue = String.format(Locale.ENGLISH, "%,d", amount);
                String btcValue = CoinLabel.getBTCFormat().format(amount.doubleValue() / Transaction.SATOSHIS_PER_BITCOIN) + " BTC";

                Entry entry = getTreeTableView().getTreeItem(getIndex()).getValue();
                if(entry instanceof HashIndexEntry) {
                    Region node = new Region();
                    node.setPrefWidth(10);
                    setGraphic(node);

                    if(((HashIndexEntry) entry).getType() == HashIndexEntry.Type.INPUT) {
                        satsValue = "-" + satsValue;
                    }
                } else {
                    setGraphic(null);
                }

                Tooltip tooltip = new Tooltip();
                tooltip.setText(btcValue);

                setText(satsValue);
                setTooltip(tooltip);
            }
        }
    }
}
