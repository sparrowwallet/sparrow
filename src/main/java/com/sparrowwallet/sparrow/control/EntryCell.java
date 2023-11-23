package com.sparrowwallet.sparrow.control;

import com.sparrowwallet.drongo.KeyPurpose;
import com.sparrowwallet.drongo.Utils;
import com.sparrowwallet.drongo.address.Address;
import com.sparrowwallet.drongo.protocol.*;
import com.sparrowwallet.drongo.wallet.*;
import com.sparrowwallet.sparrow.AppServices;
import com.sparrowwallet.sparrow.EventManager;
import com.sparrowwallet.sparrow.event.*;
import com.sparrowwallet.sparrow.glyphfont.FontAwesome5;
import com.sparrowwallet.sparrow.io.Config;
import com.sparrowwallet.sparrow.wallet.*;
import javafx.application.Platform;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.HBox;
import javafx.util.Duration;
import org.controlsfx.glyphfont.FontAwesome;
import org.controlsfx.glyphfont.Glyph;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class EntryCell extends TreeTableCell<Entry, Entry> implements ConfirmationsListener {
    private static final Logger log = LoggerFactory.getLogger(EntryCell.class);

    public static final DateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm");
    public static final Pattern REPLACED_BY_FEE_SUFFIX = Pattern.compile("(.*?)( \\(Replaced By Fee( #)?(\\d+)?\\)).*?");

    private static EntryCell lastCell;

    private IntegerProperty confirmationsProperty;

    public EntryCell() {
        super();
        setAlignment(Pos.CENTER_LEFT);
        setContentDisplay(ContentDisplay.RIGHT);
        getStyleClass().add("entry-cell");
    }

    @Override
    protected void updateItem(Entry entry, boolean empty) {
        super.updateItem(entry, empty);

        //Return immediately to avoid CPU usage when updating the same invisible cell to determine tableview size (see https://bugs.openjdk.org/browse/JDK-8280442)
        if(this == lastCell && !getTableRow().isVisible()) {
            return;
        }
        lastCell = this;

        applyRowStyles(this, entry);

        if(empty) {
            setText(null);
            setGraphic(null);
        } else {
            if(entry instanceof TransactionEntry) {
                TransactionEntry transactionEntry = (TransactionEntry)entry;
                if(transactionEntry.getBlockTransaction().getHeight() == -1) {
                    setText("Unconfirmed Parent");
                    setContextMenu(new UnconfirmedTransactionContextMenu(transactionEntry));
                } else if(transactionEntry.getBlockTransaction().getHeight() == 0) {
                    setText("Unconfirmed");
                    setContextMenu(new UnconfirmedTransactionContextMenu(transactionEntry));
                } else {
                    String date = DATE_FORMAT.format(transactionEntry.getBlockTransaction().getDate());
                    setText(date);
                    setContextMenu(new TransactionContextMenu(date, transactionEntry.getBlockTransaction()));
                }

                Tooltip tooltip = new Tooltip();
                tooltip.setShowDelay(Duration.millis(250));
                tooltip.setText(getTooltip(transactionEntry));
                setTooltip(tooltip);

                if(transactionEntry.getBlockTransaction().getHeight() <= 0) {
                    tooltip.setOnShowing(event -> {
                        tooltip.setText(getTooltip(transactionEntry));
                    });
                }

                HBox actionBox = new HBox();
                actionBox.getStyleClass().add("cell-actions");
                Button viewTransactionButton = new Button("");
                viewTransactionButton.setGraphic(getViewTransactionGlyph());
                viewTransactionButton.setOnAction(event -> {
                    EventManager.get().post(new ViewTransactionEvent(this.getScene().getWindow(), transactionEntry.getBlockTransaction()));
                });
                actionBox.getChildren().add(viewTransactionButton);

                BlockTransaction blockTransaction = transactionEntry.getBlockTransaction();
                if(blockTransaction.getHeight() <= 0 && canRBF(blockTransaction) &&
                        Config.get().isIncludeMempoolOutputs() && transactionEntry.getWallet().allInputsFromWallet(blockTransaction.getHash())) {
                    Button increaseFeeButton = new Button("");
                    increaseFeeButton.setGraphic(getIncreaseFeeRBFGlyph());
                    increaseFeeButton.setOnAction(event -> {
                        increaseFee(transactionEntry, false);
                    });
                    actionBox.getChildren().add(increaseFeeButton);
                }

                if(blockTransaction.getHeight() <= 0 && containsWalletOutputs(transactionEntry)) {
                    Button cpfpButton = new Button("");
                    cpfpButton.setGraphic(getIncreaseFeeCPFPGlyph());
                    cpfpButton.setOnAction(event -> {
                        createCpfp(transactionEntry);
                    });
                    actionBox.getChildren().add(cpfpButton);
                }

                setGraphic(actionBox);
            } else if(entry instanceof NodeEntry) {
                NodeEntry nodeEntry = (NodeEntry)entry;
                Address address = nodeEntry.getAddress();
                setText(address.toString());
                setContextMenu(new AddressContextMenu(address, nodeEntry.getOutputDescriptor(), nodeEntry, true, getTreeTableView()));
                Tooltip tooltip = new Tooltip();
                tooltip.setShowDelay(Duration.millis(250));
                tooltip.setText(nodeEntry.getNode().toString());
                setTooltip(tooltip);
                getStyleClass().add("address-cell");

                HBox actionBox = new HBox();
                actionBox.getStyleClass().add("cell-actions");

                if(!nodeEntry.getNode().getWallet().isBip47()) {
                    Button receiveButton = new Button("");
                    receiveButton.setGraphic(getReceiveGlyph());
                    receiveButton.setOnAction(event -> {
                        EventManager.get().post(new ReceiveActionEvent(nodeEntry));
                        Platform.runLater(() -> EventManager.get().post(new ReceiveToEvent(nodeEntry)));
                    });
                    actionBox.getChildren().add(receiveButton);
                }

                if(canSignMessage(nodeEntry.getNode())) {
                    Button signMessageButton = new Button("");
                    signMessageButton.setGraphic(getSignMessageGlyph());
                    signMessageButton.setOnAction(event -> {
                        MessageSignDialog messageSignDialog = new MessageSignDialog(nodeEntry.getWallet(), nodeEntry.getNode());
                        messageSignDialog.initOwner(getTreeTableView().getScene().getWindow());
                        messageSignDialog.showAndWait();
                    });
                    actionBox.getChildren().add(signMessageButton);
                }

                setGraphic(actionBox);

                if(nodeEntry.getWallet().isWhirlpoolChildWallet()) {
                    setText(address.toString().substring(0, 20) + "...");
                    setContextMenu(null);
                    setGraphic(new HBox());
                }
            } else if(entry instanceof HashIndexEntry) {
                HashIndexEntry hashIndexEntry = (HashIndexEntry)entry;
                setText(hashIndexEntry.getDescription());
                setContextMenu(getTreeTableView().getStyleClass().contains("bip47") ? null : new HashIndexEntryContextMenu(getTreeTableView(), hashIndexEntry));
                Tooltip tooltip = new Tooltip();
                tooltip.setShowDelay(Duration.millis(250));
                tooltip.setText(hashIndexEntry.getHashIndex().toString());
                setTooltip(tooltip);

                HBox actionBox = new HBox();
                actionBox.getStyleClass().add("cell-actions");
                Button viewTransactionButton = new Button("");
                viewTransactionButton.setGraphic(getViewTransactionGlyph());
                viewTransactionButton.setOnAction(event -> {
                    EventManager.get().post(new ViewTransactionEvent(this.getScene().getWindow(), hashIndexEntry.getBlockTransaction(), hashIndexEntry));
                });
                actionBox.getChildren().add(viewTransactionButton);

                if(hashIndexEntry.getType().equals(HashIndexEntry.Type.OUTPUT) && hashIndexEntry.isSpendable() && !hashIndexEntry.getHashIndex().isSpent()) {
                    Button spendUtxoButton = new Button("");
                    spendUtxoButton.setGraphic(getSendGlyph());
                    spendUtxoButton.setOnAction(event -> {
                        sendSelectedUtxos(getTreeTableView(), hashIndexEntry);
                    });
                    actionBox.getChildren().add(spendUtxoButton);
                }

                setGraphic(getTreeTableView().getStyleClass().contains("bip47") ? null : actionBox);
            }
        }
    }

    @Override
    public IntegerProperty getConfirmationsProperty() {
        if(confirmationsProperty == null) {
            confirmationsProperty = new SimpleIntegerProperty();
            confirmationsProperty.addListener((observable, oldValue, newValue) -> {
                if(newValue.intValue() >= BlockTransactionHash.BLOCKS_TO_CONFIRM) {
                    getStyleClass().remove("confirming");
                    confirmationsProperty.unbind();
                }
            });
        }

        return confirmationsProperty;
    }

    private static void increaseFee(TransactionEntry transactionEntry, boolean cancelTransaction) {
        BlockTransaction blockTransaction = transactionEntry.getBlockTransaction();
        Map<BlockTransactionHashIndex, WalletNode> walletTxos = transactionEntry.getWallet().getWalletTxos();
        List<BlockTransactionHashIndex> utxos = transactionEntry.getChildren().stream()
                .filter(e -> e instanceof HashIndexEntry)
                .map(e -> (HashIndexEntry)e)
                .filter(e -> e.getType().equals(HashIndexEntry.Type.INPUT) && e.isSpendable())
                .map(e -> blockTransaction.getTransaction().getInputs().get((int)e.getHashIndex().getIndex()))
                .filter(i -> Config.get().isMempoolFullRbf() || i.isReplaceByFeeEnabled())
                .map(txInput -> walletTxos.keySet().stream().filter(txo -> txo.getHash().equals(txInput.getOutpoint().getHash()) && txo.getIndex() == txInput.getOutpoint().getIndex()).findFirst().get())
                .collect(Collectors.toList());

        if(utxos.isEmpty()) {
            log.error("No UTXOs to replace");
            AppServices.showErrorDialog("Replace By Fee Error", "Error creating RBF transaction - no replaceable UTXOs were found.");
            return;
        }

        List<TransactionOutput> ourOutputs = transactionEntry.getChildren().stream()
                .filter(e -> e instanceof HashIndexEntry)
                .map(e -> (HashIndexEntry)e)
                .filter(e -> e.getType().equals(HashIndexEntry.Type.OUTPUT))
                .map(e -> blockTransaction.getTransaction().getOutputs().get((int)e.getHashIndex().getIndex()))
                .collect(Collectors.toList());

        List<TransactionOutput> consolidationOutputs = transactionEntry.getChildren().stream()
                .filter(e -> e instanceof HashIndexEntry)
                .map(e -> (HashIndexEntry)e)
                .filter(e -> e.getType().equals(HashIndexEntry.Type.OUTPUT) && e.getKeyPurpose() == KeyPurpose.RECEIVE)
                .map(e -> blockTransaction.getTransaction().getOutputs().get((int)e.getHashIndex().getIndex()))
                .collect(Collectors.toList());

        boolean consolidationTransaction = consolidationOutputs.size() == blockTransaction.getTransaction().getOutputs().size() && consolidationOutputs.size() == 1;
        long changeTotal = ourOutputs.stream().mapToLong(TransactionOutput::getValue).sum() - consolidationOutputs.stream().mapToLong(TransactionOutput::getValue).sum();
        Transaction tx = blockTransaction.getTransaction();
        double vSize = tx.getVirtualSize();
        if(changeTotal == 0) {
            //Add change output length to vSize if change was not present on the original transaction
            TransactionOutput changeOutput = new TransactionOutput(new Transaction(), 1L, transactionEntry.getWallet().getFreshNode(KeyPurpose.CHANGE).getOutputScript());
            vSize += changeOutput.getLength();
        }
        double inputSize = tx.getInputs().get(0).getLength() + (tx.getInputs().get(0).hasWitness() ? (double)tx.getInputs().get(0).getWitness().getLength() / Transaction.WITNESS_SCALE_FACTOR : 0);
        List<TxoFilter> txoFilters = List.of(new ExcludeTxoFilter(utxos), new SpentTxoFilter(blockTransaction.getHash()), new FrozenTxoFilter(), new CoinbaseTxoFilter(transactionEntry.getWallet()));
        double feeRate = blockTransaction.getFeeRate() == null ? AppServices.getMinimumRelayFeeRate() : blockTransaction.getFeeRate();
        List<OutputGroup> outputGroups = transactionEntry.getWallet().getGroupedUtxos(txoFilters, feeRate, AppServices.getMinimumRelayFeeRate(), Config.get().isGroupByAddress())
                .stream().filter(outputGroup -> outputGroup.getEffectiveValue() >= 0).collect(Collectors.toList());
        Collections.shuffle(outputGroups);
        while((double)changeTotal / vSize < getMaxFeeRate() && !outputGroups.isEmpty() && !cancelTransaction && !consolidationTransaction) {
            //If there is insufficient change output, include another random output group so the fee can be increased
            OutputGroup outputGroup = outputGroups.remove(0);
            for(BlockTransactionHashIndex utxo : outputGroup.getUtxos()) {
                utxos.add(utxo);
                changeTotal += utxo.getValue();
                vSize += inputSize;
            }
        }

        Long fee = blockTransaction.getFee();
        if(fee != null) {
            //Replacement tx fees must be greater than the original tx fees by its minimum relay cost
            fee += (long)Math.ceil(vSize * AppServices.getMinimumRelayFeeRate());
        }
        Long rbfFee = fee;

        List<TransactionOutput> externalOutputs = new ArrayList<>(blockTransaction.getTransaction().getOutputs());
        externalOutputs.removeAll(ourOutputs);
        externalOutputs.addAll(consolidationOutputs);
        final long rbfChange = changeTotal;
        List<Payment> payments = externalOutputs.stream().map(txOutput -> {
            try {
                String label = transactionEntry.getLabel() == null ? "" : transactionEntry.getLabel();
                label = REPLACED_BY_FEE_SUFFIX.matcher(label).replaceAll("$1");
                String[] paymentLabels = label.split(", ");
                if(externalOutputs.size() > 1 && externalOutputs.size() == paymentLabels.length) {
                    label = paymentLabels[externalOutputs.indexOf(txOutput)];
                }
                Matcher matcher = REPLACED_BY_FEE_SUFFIX.matcher(transactionEntry.getLabel() == null ? "" : transactionEntry.getLabel());
                if(matcher.matches()) {
                    if(matcher.groupCount() > 3 && matcher.group(4) != null) {
                        int count = Integer.parseInt(matcher.group(4)) + 1;
                        label += " (Replaced By Fee #" + count + ")";
                    } else {
                        label += " (Replaced By Fee #2)";
                    }
                } else {
                    label += " (Replaced By Fee)";
                }

                if(txOutput.getScript().getToAddress() != null) {
                    //Disable change creation by enabling max payment when there is only one output and no additional UTXOs included
                    return new Payment(txOutput.getScript().getToAddress(), label, txOutput.getValue(), blockTransaction.getTransaction().getOutputs().size() == 1 && rbfChange == 0);
                }

                return null;
            } catch(Exception e) {
                log.error("Error creating RBF payment", e);
                return null;
            }
        }).filter(Objects::nonNull).collect(Collectors.toList());

        List<byte[]> opReturns = externalOutputs.stream().map(txOutput -> {
            List<ScriptChunk> scriptChunks = txOutput.getScript().getChunks();
            if(scriptChunks.size() != 2 || scriptChunks.get(0).getOpcode() != ScriptOpCodes.OP_RETURN) {
                return null;
            }
            if(scriptChunks.get(1).getData() != null) {
                return scriptChunks.get(1).getData();
            }

            return null;
        }).filter(Objects::nonNull).collect(Collectors.toList());

        if(payments.isEmpty()) {
            AppServices.showErrorDialog("Replace By Fee Error", "Error creating RBF transaction, check log for details");
            return;
        }

        if(cancelTransaction) {
            Payment existing = payments.get(0);
            Address address = transactionEntry.getWallet().getFreshNode(KeyPurpose.CHANGE).getAddress();
            Payment payment = new Payment(address, existing.getLabel(), existing.getAmount(), true);
            payments.clear();
            payments.add(payment);
            opReturns.clear();
        }

        EventManager.get().post(new SendActionEvent(transactionEntry.getWallet(), utxos));
        Platform.runLater(() -> EventManager.get().post(new SpendUtxoEvent(transactionEntry.getWallet(), utxos, payments, opReturns.isEmpty() ? null : opReturns, rbfFee, true, blockTransaction)));
    }

    private static Double getMaxFeeRate() {
        if(AppServices.getTargetBlockFeeRates() == null || AppServices.getTargetBlockFeeRates().isEmpty()) {
            return 100.0;
        }

        return AppServices.getTargetBlockFeeRates().values().iterator().next();
    }

    private static void createCpfp(TransactionEntry transactionEntry) {
        BlockTransaction blockTransaction = transactionEntry.getBlockTransaction();
        List<BlockTransactionHashIndex> ourOutputs = transactionEntry.getChildren().stream()
                .filter(e -> e instanceof HashIndexEntry)
                .map(e -> (HashIndexEntry)e)
                .filter(e -> e.getType().equals(HashIndexEntry.Type.OUTPUT) && e.isSpendable())
                .map(HashIndexEntry::getHashIndex)
                .collect(Collectors.toList());

        if(ourOutputs.isEmpty()) {
            AppServices.showErrorDialog("No spendable outputs", "None of the outputs on this transaction are spendable.\n\nEnsure that the outputs are not frozen" +
                    (transactionEntry.getConfirmations() <= 0 ? ", and spending unconfirmed UTXOs is allowed." : "."));
            return;
        }

        BlockTransactionHashIndex cpfpUtxo = ourOutputs.get(0);
        Address freshAddress = transactionEntry.getWallet().getFreshNode(KeyPurpose.RECEIVE).getAddress();
        TransactionOutput txOutput = new TransactionOutput(new Transaction(), cpfpUtxo.getValue(), freshAddress.getOutputScript());
        long dustThreshold = freshAddress.getScriptType().getDustThreshold(txOutput, Transaction.DUST_RELAY_TX_FEE);
        double inputSize = freshAddress.getScriptType().getInputVbytes();
        double vSize = inputSize + txOutput.getLength();

        List<TxoFilter> txoFilters = List.of(new ExcludeTxoFilter(List.of(cpfpUtxo)), new SpentTxoFilter(), new FrozenTxoFilter(), new CoinbaseTxoFilter(transactionEntry.getWallet()));
        double feeRate = blockTransaction.getFeeRate() == null ? AppServices.getMinimumRelayFeeRate() : blockTransaction.getFeeRate();
        List<OutputGroup> outputGroups = transactionEntry.getWallet().getGroupedUtxos(txoFilters, feeRate, AppServices.getMinimumRelayFeeRate(), Config.get().isGroupByAddress())
                .stream().filter(outputGroup -> outputGroup.getEffectiveValue() >= 0).collect(Collectors.toList());
        Collections.shuffle(outputGroups);

        List<BlockTransactionHashIndex> utxos = new ArrayList<>();
        utxos.add(cpfpUtxo);
        long inputTotal = cpfpUtxo.getValue();
        while((inputTotal - (long)(getMaxFeeRate() * vSize)) < dustThreshold && !outputGroups.isEmpty()) {
            //If there is insufficient input value, include another random output group so the fee can be increased
            OutputGroup outputGroup = outputGroups.remove(0);
            for(BlockTransactionHashIndex utxo : outputGroup.getUtxos()) {
                utxos.add(utxo);
                inputTotal += utxo.getValue();
                vSize += inputSize;
            }
        }

        String label = transactionEntry.getLabel() == null ? "" : transactionEntry.getLabel();
        label += (label.isEmpty() ? "" : " ") + "(CPFP)";
        Payment payment = new Payment(freshAddress, label, inputTotal, true);

        EventManager.get().post(new SendActionEvent(transactionEntry.getWallet(), utxos));
        Platform.runLater(() -> EventManager.get().post(new SpendUtxoEvent(transactionEntry.getWallet(), utxos, List.of(payment), null, blockTransaction.getFee(), true, null)));
    }

    private static boolean canRBF(BlockTransaction blockTransaction) {
        return Config.get().isMempoolFullRbf() || blockTransaction.getTransaction().isReplaceByFee();
    }

    private static boolean canSignMessage(WalletNode walletNode) {
        Wallet wallet = walletNode.getWallet();
        return wallet.getKeystores().size() == 1 && (!wallet.isBip47() || walletNode.getKeyPurpose() == KeyPurpose.RECEIVE);
    }

    private static boolean containsWalletOutputs(TransactionEntry transactionEntry) {
        return transactionEntry.getChildren().stream()
                .filter(e -> e instanceof HashIndexEntry)
                .map(e -> (HashIndexEntry)e)
                .anyMatch(e -> e.getType().equals(HashIndexEntry.Type.OUTPUT));
    }

    private static void sendSelectedUtxos(TreeTableView<Entry> treeTableView, HashIndexEntry hashIndexEntry) {
        List<HashIndexEntry> utxoEntries = treeTableView.getSelectionModel().getSelectedCells().stream()
                .map(tp -> tp.getTreeItem().getValue())
                .filter(e -> e instanceof HashIndexEntry)
                .map(e -> (HashIndexEntry)e)
                .filter(e -> e.getType().equals(HashIndexEntry.Type.OUTPUT) && e.isSpendable())
                .collect(Collectors.toList());

        if(!utxoEntries.contains(hashIndexEntry)) {
            utxoEntries = List.of(hashIndexEntry);
        }

        final List<BlockTransactionHashIndex> spendingUtxos = utxoEntries.stream().map(HashIndexEntry::getHashIndex).collect(Collectors.toList());
        EventManager.get().post(new SendActionEvent(hashIndexEntry.getWallet(), spendingUtxos));
        Platform.runLater(() -> EventManager.get().post(new SpendUtxoEvent(hashIndexEntry.getWallet(), spendingUtxos)));
    }

    private static void freezeUtxo(TreeTableView<Entry> treeTableView, HashIndexEntry hashIndexEntry) {
        List<BlockTransactionHashIndex> utxos = treeTableView.getSelectionModel().getSelectedCells().stream()
                .map(tp -> tp.getTreeItem().getValue())
                .filter(e -> e instanceof HashIndexEntry && ((HashIndexEntry)e).getType().equals(HashIndexEntry.Type.OUTPUT))
                .map(e -> ((HashIndexEntry)e).getHashIndex())
                .filter(ref -> ref.getStatus() != Status.FROZEN)
                .collect(Collectors.toList());

        utxos.forEach(ref -> ref.setStatus(Status.FROZEN));
        EventManager.get().post(new WalletUtxoStatusChangedEvent(hashIndexEntry.getWallet(), utxos));
    }

    private static void unfreezeUtxo(TreeTableView<Entry> treeTableView, HashIndexEntry hashIndexEntry) {
        List<BlockTransactionHashIndex> utxos = treeTableView.getSelectionModel().getSelectedCells().stream()
                .map(tp -> tp.getTreeItem().getValue())
                .filter(e -> e instanceof HashIndexEntry && ((HashIndexEntry)e).getType().equals(HashIndexEntry.Type.OUTPUT))
                .map(e -> ((HashIndexEntry)e).getHashIndex())
                .filter(ref -> ref.getStatus() == Status.FROZEN)
                .collect(Collectors.toList());

        utxos.forEach(ref -> ref.setStatus(null));
        EventManager.get().post(new WalletUtxoStatusChangedEvent(hashIndexEntry.getWallet(), utxos));
    }

    private String getTooltip(TransactionEntry transactionEntry) {
        String tooltip = transactionEntry.getBlockTransaction().getHash().toString();
        if(transactionEntry.getBlockTransaction().getHeight() <= 0) {
            Double feeRate = transactionEntry.getBlockTransaction().getFeeRate();
            Long vSizefromTip = transactionEntry.getVSizeFromTip();
            if(feeRate != null && vSizefromTip != null) {
                long blocksFromTip = (long)Math.ceil((double)vSizefromTip / Transaction.MAX_BLOCK_SIZE);

                String amount = vSizefromTip + " vB";
                if(vSizefromTip > 1000 * 1000) {
                    amount = String.format("%.2f", (double)vSizefromTip / (1000 * 1000)) + " MvB";
                } else if(vSizefromTip > 1000) {
                    amount = String.format("%.2f", (double)vSizefromTip / 1000) + " kvB";
                }

                tooltip += "\nConfirms in: " + (blocksFromTip > 1 ? blocksFromTip + "+ blocks" : "1 block") + " (" + amount + " from tip)";
            }

            if(feeRate != null) {
                tooltip += "\nFee rate: " + String.format("%.2f", feeRate) + " sats/vB";
            }

            tooltip += "\nRBF: " + (canRBF(transactionEntry.getBlockTransaction()) ? "Enabled" : "Disabled");
        }

        return tooltip;
    }

    private static Glyph getViewTransactionGlyph() {
        Glyph searchGlyph = new Glyph(FontAwesome5.FONT_NAME, FontAwesome5.Glyph.SEARCH);
        searchGlyph.setFontSize(12);
        return searchGlyph;
    }

    private static Glyph getIncreaseFeeRBFGlyph() {
        Glyph increaseFeeGlyph = new Glyph(FontAwesome5.FONT_NAME, FontAwesome5.Glyph.HAND_HOLDING_MEDICAL);
        increaseFeeGlyph.setFontSize(12);
        return increaseFeeGlyph;
    }

    private static Glyph getCancelTransactionRBFGlyph() {
        Glyph cancelTxGlyph = new Glyph(FontAwesome5.FONT_NAME, FontAwesome5.Glyph.BAN);
        cancelTxGlyph.setFontSize(12);
        return cancelTxGlyph;
    }

    private static Glyph getIncreaseFeeCPFPGlyph() {
        Glyph cpfpGlyph = new Glyph(FontAwesome5.FONT_NAME, FontAwesome5.Glyph.SIGN_OUT_ALT);
        cpfpGlyph.setFontSize(12);
        return cpfpGlyph;
    }

    private static Glyph getReceiveGlyph() {
        Glyph receiveGlyph = new Glyph("FontAwesome", FontAwesome.Glyph.ARROW_DOWN);
        receiveGlyph.setFontSize(12);
        return receiveGlyph;
    }

    private static Glyph getSignMessageGlyph() {
        Glyph signMessageGlyph = new Glyph(FontAwesome5.FONT_NAME, FontAwesome5.Glyph.PEN_FANCY);
        signMessageGlyph.setFontSize(12);
        return signMessageGlyph;
    }

    private static Glyph getSendGlyph() {
        Glyph sendGlyph = new Glyph("FontAwesome", FontAwesome.Glyph.SEND);
        sendGlyph.setFontSize(12);
        return sendGlyph;
    }

    private static Glyph getCopyGlyph() {
        Glyph copyGlyph = new Glyph(FontAwesome5.FONT_NAME, FontAwesome5.Glyph.COPY);
        copyGlyph.setFontSize(12);
        return copyGlyph;
    }

    private static Glyph getFreezeGlyph() {
        Glyph copyGlyph = new Glyph(FontAwesome5.FONT_NAME, FontAwesome5.Glyph.SNOWFLAKE);
        copyGlyph.setFontSize(12);
        return copyGlyph;
    }

    private static Glyph getUnfreezeGlyph() {
        Glyph copyGlyph = new Glyph(FontAwesome5.FONT_NAME, FontAwesome5.Glyph.SUN);
        copyGlyph.setFontSize(12);
        return copyGlyph;
    }

    private static class UnconfirmedTransactionContextMenu extends ContextMenu {
        public UnconfirmedTransactionContextMenu(TransactionEntry transactionEntry) {
            BlockTransaction blockTransaction = transactionEntry.getBlockTransaction();
            MenuItem viewTransaction = new MenuItem("View Transaction");
            viewTransaction.setGraphic(getViewTransactionGlyph());
            viewTransaction.setOnAction(AE -> {
                hide();
                EventManager.get().post(new ViewTransactionEvent(this.getOwnerWindow(), blockTransaction));
            });
            getItems().add(viewTransaction);

            if(canRBF(blockTransaction) && Config.get().isIncludeMempoolOutputs() && transactionEntry.getWallet().allInputsFromWallet(blockTransaction.getHash())) {
                MenuItem increaseFee = new MenuItem("Increase Fee (RBF)");
                increaseFee.setGraphic(getIncreaseFeeRBFGlyph());
                increaseFee.setOnAction(AE -> {
                    hide();
                    increaseFee(transactionEntry, false);
                });

                getItems().add(increaseFee);
            }

            if(canRBF(blockTransaction) && Config.get().isIncludeMempoolOutputs() && transactionEntry.getWallet().allInputsFromWallet(blockTransaction.getHash())) {
                MenuItem cancelTx = new MenuItem("Cancel Transaction (RBF)");
                cancelTx.setGraphic(getCancelTransactionRBFGlyph());
                cancelTx.setOnAction(AE -> {
                    hide();
                    increaseFee(transactionEntry, true);
                });

                getItems().add(cancelTx);
            }

            if(containsWalletOutputs(transactionEntry)) {
                MenuItem createCpfp = new MenuItem("Increase Effective Fee (CPFP)");
                createCpfp.setGraphic(getIncreaseFeeCPFPGlyph());
                createCpfp.setOnAction(AE -> {
                    hide();
                    createCpfp(transactionEntry);
                });

                getItems().add(createCpfp);
            }

            MenuItem openBlockExplorer = new MenuItem("Open in Block Explorer");
            openBlockExplorer.setOnAction(AE -> {
                hide();
                AppServices.openBlockExplorer(blockTransaction.getHashAsString());
            });
            getItems().add(openBlockExplorer);

            MenuItem copyTxid = new MenuItem("Copy Transaction ID");
            copyTxid.setOnAction(AE -> {
                hide();
                ClipboardContent content = new ClipboardContent();
                content.putString(blockTransaction.getHashAsString());
                Clipboard.getSystemClipboard().setContent(content);
            });

            getItems().add(copyTxid);
        }
    }

    private static class TransactionContextMenu extends ContextMenu {
        public TransactionContextMenu(String date, BlockTransaction blockTransaction) {
            MenuItem viewTransaction = new MenuItem("View Transaction");
            viewTransaction.setGraphic(getViewTransactionGlyph());
            viewTransaction.setOnAction(AE -> {
                hide();
                EventManager.get().post(new ViewTransactionEvent(this.getOwnerWindow(), blockTransaction));
            });

            MenuItem openBlockExplorer = new MenuItem("Open in Block Explorer");
            openBlockExplorer.setOnAction(AE -> {
                hide();
                AppServices.openBlockExplorer(blockTransaction.getHashAsString());
            });

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

            getItems().addAll(viewTransaction, openBlockExplorer, copyDate, copyTxid, copyHeight);
        }
    }

    public static class AddressContextMenu extends ContextMenu {
        public AddressContextMenu(Address address, String outputDescriptor, NodeEntry nodeEntry, boolean addUtxoItems, TreeTableView<Entry> treetable) {
            if(nodeEntry == null || !nodeEntry.getWallet().isBip47()) {
                MenuItem receiveToAddress = new MenuItem("Receive To");
                receiveToAddress.setGraphic(getReceiveGlyph());
                receiveToAddress.setOnAction(event -> {
                    hide();
                    EventManager.get().post(new ReceiveActionEvent(nodeEntry));
                    Platform.runLater(() -> EventManager.get().post(new ReceiveToEvent(nodeEntry)));
                });
                getItems().add(receiveToAddress);
            }

            if(nodeEntry != null && canSignMessage(nodeEntry.getNode())) {
                MenuItem signVerifyMessage = new MenuItem("Sign/Verify Message");
                signVerifyMessage.setGraphic(getSignMessageGlyph());
                signVerifyMessage.setOnAction(AE -> {
                    hide();
                    MessageSignDialog messageSignDialog = new MessageSignDialog(nodeEntry.getWallet(), nodeEntry.getNode());
                    messageSignDialog.initOwner(treetable.getScene().getWindow());
                    messageSignDialog.showAndWait();
                });
                getItems().add(signVerifyMessage);
            }

            if(addUtxoItems && nodeEntry != null && !nodeEntry.getNode().getUnspentTransactionOutputs().isEmpty()) {
                List<BlockTransactionHashIndex> utxos = nodeEntry.getNode().getUnspentTransactionOutputs().stream().collect(Collectors.toList());
                MenuItem spendUtxos = new MenuItem("Spend UTXOs");
                spendUtxos.setGraphic(getSendGlyph());
                spendUtxos.setOnAction(AE -> {
                    hide();
                    EventManager.get().post(new SendActionEvent(nodeEntry.getWallet(), utxos));
                    Platform.runLater(() -> EventManager.get().post(new SpendUtxoEvent(nodeEntry.getWallet(), utxos)));
                });
                getItems().add(spendUtxos);

                List<BlockTransactionHashIndex> unfrozenUtxos = nodeEntry.getNode().getUnspentTransactionOutputs().stream().filter(utxo -> utxo.getStatus() != Status.FROZEN).collect(Collectors.toList());
                if(!unfrozenUtxos.isEmpty()) {
                    MenuItem freezeUtxos = new MenuItem("Freeze UTXOs");
                    freezeUtxos.setGraphic(getFreezeGlyph());
                    freezeUtxos.setOnAction(AE -> {
                        hide();
                        unfrozenUtxos.forEach(utxo -> utxo.setStatus(Status.FROZEN));
                        EventManager.get().post(new WalletUtxoStatusChangedEvent(nodeEntry.getWallet(), unfrozenUtxos));
                    });
                    getItems().add(freezeUtxos);
                }

                List<BlockTransactionHashIndex> frozenUtxos = nodeEntry.getNode().getUnspentTransactionOutputs().stream().filter(utxo -> utxo.getStatus() == Status.FROZEN).collect(Collectors.toList());
                if(!frozenUtxos.isEmpty()) {
                    MenuItem unfreezeUtxos = new MenuItem("Unfreeze UTXOs");
                    unfreezeUtxos.setGraphic(getUnfreezeGlyph());
                    unfreezeUtxos.setOnAction(AE -> {
                        hide();
                        frozenUtxos.forEach(utxo -> utxo.setStatus(null));
                        EventManager.get().post(new WalletUtxoStatusChangedEvent(nodeEntry.getWallet(), frozenUtxos));
                    });
                    getItems().add(unfreezeUtxos);
                }
            }

            MenuItem copyAddress = new MenuItem("Copy Address");
            copyAddress.setOnAction(AE -> {
                hide();
                ClipboardContent content = new ClipboardContent();
                content.putString(address.toString());
                Clipboard.getSystemClipboard().setContent(content);
            });

            MenuItem copyOutputDescriptor = new MenuItem("Copy Output Descriptor");
            copyOutputDescriptor.setOnAction(AE -> {
                hide();
                ClipboardContent content = new ClipboardContent();
                content.putString(outputDescriptor);
                Clipboard.getSystemClipboard().setContent(content);
            });

            getItems().addAll(copyAddress, copyOutputDescriptor);

            if(nodeEntry != null) {
                MenuItem copyHex = new MenuItem("Copy Script Output Bytes");
                copyHex.setOnAction(AE -> {
                    hide();
                    Script outputScript = nodeEntry.getWallet().getOutputScript(nodeEntry.getNode());
                    ClipboardContent content = new ClipboardContent();
                    content.putString(Utils.bytesToHex(outputScript.getProgram()));
                    Clipboard.getSystemClipboard().setContent(content);
                });
                getItems().add(copyHex);
            }
        }
    }

    static class HashIndexEntryContextMenu extends ContextMenu {
        public HashIndexEntryContextMenu(TreeTableView<Entry> treeTableView, HashIndexEntry hashIndexEntry) {
            MenuItem viewTransaction = new MenuItem("View Transaction");
            viewTransaction.setGraphic(getViewTransactionGlyph());
            viewTransaction.setOnAction(AE -> {
                hide();
                EventManager.get().post(new ViewTransactionEvent(this.getOwnerWindow(), hashIndexEntry.getBlockTransaction()));
            });
            getItems().add(viewTransaction);

            if(hashIndexEntry.getType().equals(HashIndexEntry.Type.OUTPUT) && hashIndexEntry.isSpendable() && !hashIndexEntry.getHashIndex().isSpent()) {
                MenuItem sendSelected = new MenuItem("Send Selected");
                sendSelected.setGraphic(getSendGlyph());
                sendSelected.setOnAction(AE -> {
                    hide();
                    sendSelectedUtxos(treeTableView, hashIndexEntry);
                });
                getItems().add(sendSelected);
            }

            if(hashIndexEntry.getType().equals(HashIndexEntry.Type.OUTPUT) && !hashIndexEntry.getHashIndex().isSpent()) {
                if(hashIndexEntry.getHashIndex().getStatus() == null || hashIndexEntry.getHashIndex().getStatus() != Status.FROZEN) {
                    MenuItem freezeUtxo = new MenuItem("Freeze UTXO");
                    freezeUtxo.setGraphic(getFreezeGlyph());
                    freezeUtxo.setOnAction(AE -> {
                        hide();
                        freezeUtxo(treeTableView, hashIndexEntry);
                    });
                    getItems().add(freezeUtxo);
                } else {
                    MenuItem unfreezeUtxo = new MenuItem("Unfreeze UTXO");
                    unfreezeUtxo.setGraphic(getUnfreezeGlyph());
                    unfreezeUtxo.setOnAction(AE -> {
                        hide();
                        unfreezeUtxo(treeTableView, hashIndexEntry);
                    });
                    getItems().add(unfreezeUtxo);
                }
            }

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
        cell.getStyleClass().remove("unconfirmed-row");
        cell.getStyleClass().remove("summary-row");
        cell.getStyleClass().remove("address-cell");
        cell.getStyleClass().remove("hashindex-row");
        cell.getStyleClass().remove("confirming");
        cell.getStyleClass().remove("negative-amount");
        cell.getStyleClass().remove("spent");
        cell.getStyleClass().remove("unspendable");

        if(entry != null) {
            if(entry instanceof TransactionEntry) {
                cell.getStyleClass().add("transaction-row");
                TransactionEntry transactionEntry = (TransactionEntry)entry;

                if(cell instanceof ConfirmationsListener confirmationsListener) {
                    if(transactionEntry.isConfirming()) {
                        cell.getStyleClass().add("confirming");
                        confirmationsListener.getConfirmationsProperty().bind(transactionEntry.confirmationsProperty());
                    } else {
                        confirmationsListener.getConfirmationsProperty().unbind();
                    }
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
            } else if(entry instanceof WalletSummaryDialog.UnconfirmedEntry) {
                cell.getStyleClass().add("unconfirmed-row");
            } else if(entry instanceof WalletSummaryDialog.SummaryEntry) {
                cell.getStyleClass().add("summary-row");
            }
        }
    }
}
