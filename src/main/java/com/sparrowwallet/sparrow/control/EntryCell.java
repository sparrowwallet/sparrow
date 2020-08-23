package com.sparrowwallet.sparrow.control;

import com.sparrowwallet.drongo.Utils;
import com.sparrowwallet.drongo.address.Address;
import com.sparrowwallet.drongo.wallet.BlockTransaction;
import com.sparrowwallet.sparrow.EventManager;
import com.sparrowwallet.sparrow.event.*;
import com.sparrowwallet.sparrow.glyphfont.FontAwesome5;
import com.sparrowwallet.sparrow.wallet.*;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.HBox;
import org.controlsfx.glyphfont.FontAwesome;
import org.controlsfx.glyphfont.Glyph;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.stream.Collectors;

class EntryCell extends TreeTableCell<Entry, Entry> {
    private static final DateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm");

    public EntryCell() {
        super();
        setAlignment(Pos.CENTER_LEFT);
        setContentDisplay(ContentDisplay.RIGHT);
        getStyleClass().add("entry-cell");
    }

    @Override
    protected void updateItem(Entry entry, boolean empty) {
        super.updateItem(entry, empty);

        applyRowStyles(this, entry);

        if(empty) {
            setText(null);
            setGraphic(null);
        } else {
            if(entry instanceof TransactionEntry) {
                TransactionEntry transactionEntry = (TransactionEntry)entry;
                if(transactionEntry.getBlockTransaction().getHeight() == -1) {
                    setText("Unconfirmed Parent");
                    setContextMenu(new UnconfirmedTransactionContextMenu(transactionEntry.getBlockTransaction()));
                } else if(transactionEntry.getBlockTransaction().getHeight() == 0) {
                    setText("Unconfirmed");
                    setContextMenu(new UnconfirmedTransactionContextMenu(transactionEntry.getBlockTransaction()));
                } else {
                    String date = DATE_FORMAT.format(transactionEntry.getBlockTransaction().getDate());
                    setText(date);
                    setContextMenu(new TransactionContextMenu(date, transactionEntry.getBlockTransaction()));
                }

                Tooltip tooltip = new Tooltip();
                tooltip.setText(transactionEntry.getBlockTransaction().getHash().toString());
                setTooltip(tooltip);

                Button viewTransactionButton = new Button("");
                Glyph searchGlyph = new Glyph(FontAwesome5.FONT_NAME, FontAwesome5.Glyph.SEARCH);
                searchGlyph.setFontSize(12);
                viewTransactionButton.setGraphic(searchGlyph);
                viewTransactionButton.setOnAction(event -> {
                    EventManager.get().post(new ViewTransactionEvent(transactionEntry.getBlockTransaction()));
                });
                setGraphic(viewTransactionButton);
            } else if(entry instanceof NodeEntry) {
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

                HBox actionBox = new HBox();
                Button viewTransactionButton = new Button("");
                Glyph searchGlyph = new Glyph(FontAwesome5.FONT_NAME, FontAwesome5.Glyph.SEARCH);
                searchGlyph.setFontSize(12);
                viewTransactionButton.setGraphic(searchGlyph);
                viewTransactionButton.setOnAction(event -> {
                    EventManager.get().post(new ViewTransactionEvent(hashIndexEntry.getBlockTransaction(), hashIndexEntry));
                });
                actionBox.getChildren().add(viewTransactionButton);

                if(hashIndexEntry.getType().equals(HashIndexEntry.Type.OUTPUT) && hashIndexEntry.isSpendable()) {
                    Button spendUtxoButton = new Button("");
                    Glyph sendGlyph = new Glyph("FontAwesome", FontAwesome.Glyph.SEND);
                    sendGlyph.setFontSize(12);
                    spendUtxoButton.setGraphic(sendGlyph);
                    spendUtxoButton.setOnAction(event -> {
                        List<HashIndexEntry> utxoEntries = getTreeTableView().getSelectionModel().getSelectedCells().stream()
                                .map(tp -> tp.getTreeItem().getValue())
                                .filter(e -> e instanceof HashIndexEntry)
                                .map(e -> (HashIndexEntry)e)
                                .filter(e -> e.getType().equals(HashIndexEntry.Type.OUTPUT) && e.isSpendable())
                                .collect(Collectors.toList());

                        if(!utxoEntries.contains(hashIndexEntry)) {
                            utxoEntries = List.of(hashIndexEntry);
                        }

                        final List<HashIndexEntry> spendingUtxoEntries = utxoEntries;
                        EventManager.get().post(new SendActionEvent(utxoEntries));
                        Platform.runLater(() -> EventManager.get().post(new SpendUtxoEvent(spendingUtxoEntries)));
                    });
                    actionBox.getChildren().add(spendUtxoButton);
                }

                setGraphic(actionBox);
            }
        }
    }

    private static class UnconfirmedTransactionContextMenu extends ContextMenu {
        public UnconfirmedTransactionContextMenu(BlockTransaction blockTransaction) {
            MenuItem copyTxid = new MenuItem("Copy Transaction ID");
            copyTxid.setOnAction(AE -> {
                hide();
                ClipboardContent content = new ClipboardContent();
                content.putString(blockTransaction.getHashAsString());
                Clipboard.getSystemClipboard().setContent(content);
            });

            getItems().addAll(copyTxid);
        }
    }

    private static class TransactionContextMenu extends ContextMenu {
        public TransactionContextMenu(String date, BlockTransaction blockTransaction) {
            MenuItem copyDate = new MenuItem("Copy Date");
            copyDate.setOnAction(AE -> {
                hide();
                ClipboardContent content = new ClipboardContent();
                content.putString(date);
                Clipboard.getSystemClipboard().setContent(content);
            });

            MenuItem copyTxid = new MenuItem("Copy Transaction ID");
            copyTxid.setOnAction(AE -> {
                hide();
                ClipboardContent content = new ClipboardContent();
                content.putString(blockTransaction.getHashAsString());
                Clipboard.getSystemClipboard().setContent(content);
            });

            MenuItem copyHeight = new MenuItem("Copy Block Height");
            copyHeight.setOnAction(AE -> {
                hide();
                ClipboardContent content = new ClipboardContent();
                content.putString(blockTransaction.getHeight() > 0 ? Integer.toString(blockTransaction.getHeight()) : "Mempool");
                Clipboard.getSystemClipboard().setContent(content);
            });

            getItems().addAll(copyDate, copyTxid, copyHeight);
        }
    }

    public static class AddressContextMenu extends ContextMenu {
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

    public static void applyRowStyles(TreeTableCell<?, ?> cell, Entry entry) {
        cell.getStyleClass().remove("transaction-row");
        cell.getStyleClass().remove("node-row");
        cell.getStyleClass().remove("utxo-row");
        cell.getStyleClass().remove("address-cell");
        cell.getStyleClass().remove("hashindex-row");
        cell.getStyleClass().remove("spent");
        cell.getStyleClass().remove("unspendable");

        if(entry != null) {
            if(entry instanceof TransactionEntry) {
                cell.getStyleClass().add("transaction-row");
                TransactionEntry transactionEntry = (TransactionEntry)entry;
                if(transactionEntry.isConfirming()) {
                    cell.getStyleClass().add("confirming");
                    transactionEntry.confirmationsProperty().addListener((observable, oldValue, newValue) -> {
                        if(!transactionEntry.isConfirming()) {
                            cell.getStyleClass().remove("confirming");
                        }
                    });
                }
            } else if(entry instanceof NodeEntry) {
                cell.getStyleClass().add("node-row");
            } else if(entry instanceof UtxoEntry) {
                cell.getStyleClass().add("utxo-row");
                UtxoEntry utxoEntry = (UtxoEntry)entry;
                if(!utxoEntry.isSpendable()) {
                    cell.getStyleClass().add("unspendable");
                }
            } else if(entry instanceof HashIndexEntry) {
                cell.getStyleClass().add("hashindex-row");
                HashIndexEntry hashIndexEntry = (HashIndexEntry)entry;
                if(hashIndexEntry.isSpent()) {
                    cell.getStyleClass().add("spent");
                }
            }
        }
    }
}
