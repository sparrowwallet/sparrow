package com.sparrowwallet.sparrow.transaction;

import com.google.common.eventbus.Subscribe;
import com.sparrowwallet.drongo.protocol.*;
import com.sparrowwallet.drongo.psbt.PSBT;
import com.sparrowwallet.drongo.psbt.PSBTInput;
import com.sparrowwallet.drongo.psbt.PSBTOutput;
import com.sparrowwallet.drongo.wallet.BlockTransaction;
import com.sparrowwallet.sparrow.AppServices;
import com.sparrowwallet.sparrow.EventManager;
import com.sparrowwallet.sparrow.TransactionTabData;
import com.sparrowwallet.sparrow.control.TransactionDiagram;
import com.sparrowwallet.sparrow.control.TransactionHexArea;
import com.sparrowwallet.sparrow.event.*;
import com.sparrowwallet.sparrow.io.Config;
import com.sparrowwallet.sparrow.net.ElectrumServer;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.layout.Pane;
import org.controlsfx.control.MasterDetailPane;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URL;
import java.util.*;

public class TransactionController implements Initializable {
    private static final Logger log = LoggerFactory.getLogger(TransactionController.class);

    @FXML
    private Node tabContent;

    @FXML
    private MasterDetailPane transactionMasterDetail;

    @FXML
    private TreeView<TransactionForm> txtree;

    @FXML
    private Pane txpane;

    @FXML
    private TransactionHexArea txhex;

    private TransactionData txdata;

    private TransactionView initialView;
    private Integer initialIndex;

    private int selectedInputIndex = -1;
    private int selectedOutputIndex = -1;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        EventManager.get().register(this);
    }

    public void initializeView() {
        initializeTxTree();
        transactionMasterDetail.setDividerPosition(0.82);
        transactionMasterDetail.setShowDetailNode(Config.get().isShowTransactionHex());
        txhex.setTransaction(getTransaction());
        highlightTxHex();
        fetchThisAndInputBlockTransactions(0, Math.min(getTransaction().getInputs().size(), PageForm.PAGE_SIZE));
        fetchOutputBlockTransactions(0, Math.min(getTransaction().getOutputs().size(), PageForm.PAGE_SIZE));

        if(TransactionView.INPUT.equals(initialView) && initialIndex >= PageForm.PAGE_SIZE) {
            fetchThisAndInputBlockTransactions(initialIndex, initialIndex + 1);
        } else if(TransactionView.OUTPUT.equals(initialView) && initialIndex >= PageForm.PAGE_SIZE) {
            fetchOutputBlockTransactions(initialIndex, initialIndex + 1);
        }

        transactionMasterDetail.sceneProperty().addListener((observable, oldScene, newScene) -> {
            if(oldScene == null && newScene != null) {
                transactionMasterDetail.setDividerPosition(AppServices.isReducedWindowHeight(transactionMasterDetail) ? 0.9 : 0.82);
            }
        });
    }

    private void initializeTxTree() {
        HeadersForm headersForm = new HeadersForm(txdata);
        TreeItem<TransactionForm> rootItem = new TreeItem<>(headersForm);
        rootItem.setExpanded(true);

        InputsForm inputsForm = new InputsForm(txdata);
        TreeItem<TransactionForm> inputsItem = new TreeItem<>(inputsForm);
        inputsItem.setExpanded(true);
        boolean inputPagingAdded = false;
        for(int i = 0; i < getTransaction().getInputs().size(); i++) {
            if(i < PageForm.PAGE_SIZE) {
                TreeItem<TransactionForm> inputItem = createInputTreeItem(i);
                inputsItem.getChildren().add(inputItem);
            } else if(TransactionView.INPUT.equals(initialView) && i == initialIndex) {
                TreeItem<TransactionForm> inputItem = createInputTreeItem(i);
                inputsItem.getChildren().add(inputItem);
                inputPagingAdded = false;
            } else if(!inputPagingAdded) {
                PageForm pageForm = new PageForm(TransactionView.INPUT, i, Math.min(getTransaction().getInputs().size(), i + PageForm.PAGE_SIZE));
                TreeItem<TransactionForm> pageItem = new TreeItem<>(pageForm);
                inputsItem.getChildren().add(pageItem);
                inputPagingAdded = true;
            }
        }

        OutputsForm outputsForm = new OutputsForm(txdata);
        TreeItem<TransactionForm> outputsItem = new TreeItem<>(outputsForm);
        outputsItem.setExpanded(true);
        boolean outputPagingAdded = false;
        for(int i = 0; i < getTransaction().getOutputs().size(); i++) {
            if(i < PageForm.PAGE_SIZE) {
                TreeItem<TransactionForm> outputItem = createOutputTreeItem(i);
                outputsItem.getChildren().add(outputItem);
            } else if(TransactionView.OUTPUT.equals(initialView) && i == initialIndex) {
                TreeItem<TransactionForm> outputItem = createOutputTreeItem(i);
                outputsItem.getChildren().add(outputItem);
                outputPagingAdded = false;
            } else if(!outputPagingAdded) {
                PageForm pageForm = new PageForm(TransactionView.OUTPUT, i, Math.min(getTransaction().getOutputs().size(), i + PageForm.PAGE_SIZE));
                TreeItem<TransactionForm> pageItem = new TreeItem<>(pageForm);
                outputsItem.getChildren().add(pageItem);
                outputPagingAdded = true;
            }
        }

        rootItem.getChildren().add(inputsItem);
        rootItem.getChildren().add(outputsItem);
        txtree.setRoot(rootItem);

        txtree.setCellFactory(tc -> new TreeCell<>() {
            @Override
            protected void updateItem(TransactionForm form, boolean empty) {
                super.updateItem(form, empty);

                setText(null);
                setGraphic(null);
                setTooltip(null);
                setContextMenu(null);

                if(form != null) {
                    setText(form.toString());

                    if(form.getSigningWallet() != null) {
                        if(form instanceof InputForm) {
                            InputForm inputForm = (InputForm)form;
                            if(inputForm.isWalletTxo()) {
                                setGraphic(TransactionDiagram.getTxoGlyph());
                            } else {
                                setGraphic(TransactionDiagram.getMixGlyph());
                            }
                        }
                        if(form instanceof OutputForm) {
                            OutputForm outputForm = (OutputForm)form;
                            if(outputForm.isWalletConsolidation()) {
                                setGraphic(TransactionDiagram.getConsolidationGlyph());
                            } else if(outputForm.isWalletChange()) {
                                setGraphic(TransactionDiagram.getChangeGlyph());
                            } else {
                                setGraphic(TransactionDiagram.getPaymentGlyph());
                            }
                        }
                    }
                }
            }
        });

        txdata.signingWalletProperty().addListener((observable, oldValue, newValue) -> {
            txtree.refresh();
        });

        txtree.getSelectionModel().selectedItemProperty().addListener((observable, old_val, selectedItem) -> {
            TransactionForm transactionForm = selectedItem.getValue();
            if(transactionForm instanceof PageForm) {
                PageForm pageForm = (PageForm)transactionForm;
                Optional<TreeItem<TransactionForm>> optParentItem = txtree.getRoot().getChildren().stream()
                        .filter(item -> item.getValue().getView().equals(pageForm.getView().equals(TransactionView.INPUT) ? TransactionView.INPUTS : TransactionView.OUTPUTS)).findFirst();

                if(optParentItem.isPresent()) {
                    TreeItem<TransactionForm> parentItem = optParentItem.get();
                    int treeIndex = parentItem.getChildren().indexOf(selectedItem);
                    parentItem.getChildren().remove(selectedItem);

                    int max = pageForm.getView().equals(TransactionView.INPUT) ? getTransaction().getInputs().size() : getTransaction().getOutputs().size();
                    for(int i = pageForm.getPageStart(); i < max && i < pageForm.getPageEnd(); i++) {
                        int txIndex = i;
                        TreeItem<TransactionForm> newItem = pageForm.getView().equals(TransactionView.INPUT) ? createInputTreeItem(txIndex) : createOutputTreeItem(txIndex);
                        Optional<TreeItem<TransactionForm>> optionalExisting = parentItem.getChildren().stream().filter(item -> ((IndexedTransactionForm)item.getValue()).getIndex() == txIndex).findFirst();

                        boolean pageItem = false;
                        if(optionalExisting.isPresent() && optionalExisting.get().getValue() instanceof PageForm) {
                            parentItem.getChildren().remove(optionalExisting.get());
                            pageItem = true;
                        }

                        if(optionalExisting.isEmpty() || pageItem) {
                            if(treeIndex >= parentItem.getChildren().size()) {
                                parentItem.getChildren().add(newItem);
                            } else {
                                parentItem.getChildren().add(treeIndex, newItem);
                            }
                        }

                        treeIndex++;
                    }

                    if(pageForm.getPageEnd() < max) {
                        PageForm nextPageForm = new PageForm(pageForm.getView(), pageForm.getPageEnd(), Math.min(max, pageForm.getPageEnd() + PageForm.PAGE_SIZE));
                        TreeItem<TransactionForm> nextPageItem = new TreeItem<>(nextPageForm);
                        if(pageForm.getPageEnd() >= parentItem.getChildren().size()) {
                            parentItem.getChildren().add(nextPageItem);
                        } else {
                            parentItem.getChildren().add(pageForm.getPageEnd(), nextPageItem);
                        }
                    }

                    if(pageForm.getView().equals(TransactionView.INPUT)) {
                        fetchThisAndInputBlockTransactions(pageForm.getPageStart(), Math.min(max, pageForm.getPageEnd()));
                    } else {
                        fetchOutputBlockTransactions(pageForm.getPageStart(), Math.min(max, pageForm.getPageEnd()));
                    }

                    setTreeSelection(pageForm.getView(), pageForm.getPageStart());
                }
            } else {
                Node detailPane = null;
                for(Node txdetail : txpane.getChildren()) {
                    TransactionForm childForm = (TransactionForm)txdetail.getUserData();
                    if(transactionForm == childForm) {
                        detailPane = txdetail;
                        txdetail.setViewOrder(0);
                    } else {
                        txdetail.setViewOrder(1);
                    }
                }

                try {
                    if(detailPane == null) {
                        detailPane = transactionForm.getContents();
                        detailPane.setViewOrder(0);
                        txpane.getChildren().add(detailPane);
                    }
                } catch (IOException e) {
                    throw new IllegalStateException("Can't find pane", e);
                }

                if(detailPane instanceof Parent) {
                    Parent parent = (Parent)detailPane;
                    txhex.getStylesheets().clear();
                    txhex.getStylesheets().addAll(parent.getStylesheets());

                    selectedInputIndex = -1;
                    selectedOutputIndex = -1;
                    if(transactionForm instanceof InputForm) {
                        InputForm inputForm = (InputForm) transactionForm;
                        selectedInputIndex = inputForm.getTransactionInput().getIndex();
                    } else if(transactionForm instanceof OutputForm) {
                        OutputForm outputForm = (OutputForm) transactionForm;
                        selectedOutputIndex = outputForm.getTransactionOutput().getIndex();
                    }

                    Platform.runLater(this::highlightTxHex);
                }
            }
        });

        if(initialView != null) {
            Platform.runLater(() -> setTreeSelection(initialView, initialIndex));
        } else {
            txtree.getSelectionModel().select(txtree.getRoot());
        }
    }

    private TreeItem<TransactionForm> createInputTreeItem(int inputIndex) {
        TransactionInput txInput = getTransaction().getInputs().get(inputIndex);
        PSBTInput psbtInput = null;
        if(getPSBT() != null && getPSBT().getPsbtInputs().size() > txInput.getIndex()) {
            psbtInput = getPSBT().getPsbtInputs().get(txInput.getIndex());
        }
        InputForm inputForm = (getPSBT() != null ? new InputForm(txdata, psbtInput) : new InputForm(txdata, txInput));
        return new TreeItem<>(inputForm);
    }

    private TreeItem<TransactionForm> createOutputTreeItem(int outputIndex) {
        TransactionOutput txOutput = getTransaction().getOutputs().get(outputIndex);
        PSBTOutput psbtOutput = null;
        if (getPSBT() != null && getPSBT().getPsbtOutputs().size() > txOutput.getIndex()) {
            psbtOutput = getPSBT().getPsbtOutputs().get(txOutput.getIndex());
        }
        OutputForm outputForm = (getPSBT() != null ? new OutputForm(txdata, psbtOutput) : new OutputForm(txdata, txOutput));
        return new TreeItem<>(outputForm);
    }

    public void setTreeSelection(TransactionView view, Integer index) {
        TreeItem<TransactionForm> treeItem = getTreeItem(view, index);
        txtree.getSelectionModel().select(treeItem);
        txtree.scrollTo(txtree.getRow(treeItem));
    }

    private TreeItem<TransactionForm> getTreeItem(TransactionView view, Integer index) {
        return getTreeItem(txtree.getRoot(), view, index);
    }

    private TreeItem<TransactionForm> getTreeItem(TreeItem<TransactionForm> treeItem, TransactionView view, Integer index) {
        if(treeItem.getValue().getView().equals(view)) {
            if(view.equals(TransactionView.INPUT) || view.equals(TransactionView.OUTPUT)) {
                IndexedTransactionForm txForm = (IndexedTransactionForm)treeItem.getValue();
                if(txForm.getIndex() == index) {
                    return treeItem;
                }
            } else {
                return treeItem;
            }
        }

        for(TreeItem<TransactionForm> childItem : treeItem.getChildren()) {
            TreeItem<TransactionForm> foundItem = getTreeItem(childItem, view, index);
            if(foundItem != null) {
                return foundItem;
            }
        }

        return null;
    }

    void highlightTxHex() {
        txhex.applyHighlighting(getTransaction(), selectedInputIndex, selectedOutputIndex);
    }

    private void fetchThisAndInputBlockTransactions(int indexStart, int indexEnd) {
        if(AppServices.isConnected() && indexStart < getTransaction().getInputs().size()) {
            Set<Sha256Hash> references = new HashSet<>();
            if(getPSBT() == null) {
                references.add(getTransaction().getTxId());
            }

            int maxIndex = Math.min(getTransaction().getInputs().size(), indexEnd);
            for(int i = indexStart; i < maxIndex; i++) {
                TransactionInput input = getTransaction().getInputs().get(i);
                if(!input.isCoinBase()) {
                    references.add(input.getOutpoint().getHash());
                }
            }

            if(references.isEmpty()) {
                return;
            }

            ElectrumServer.TransactionReferenceService transactionReferenceService = new ElectrumServer.TransactionReferenceService(references);
            transactionReferenceService.setOnSucceeded(successEvent -> {
                Map<Sha256Hash, BlockTransaction> transactionMap = transactionReferenceService.getValue();
                BlockTransaction thisBlockTx = null;
                Map<Sha256Hash, BlockTransaction> inputTransactions = new HashMap<>();
                for(Sha256Hash txid : transactionMap.keySet()) {
                    BlockTransaction blockTx = transactionMap.get(txid);
                    if(txid.equals(getTransaction().getTxId())) {
                        thisBlockTx = blockTx;
                    } else {
                        inputTransactions.put(txid, blockTx);
                        references.remove(txid);
                    }
                }

                references.remove(getTransaction().getTxId());
                if (!references.isEmpty()) {
                    log.warn("Failed to retrieve all referenced input transactions, aborting transaction fetch");
                    return;
                }

                final BlockTransaction blockTx = thisBlockTx;
                Platform.runLater(() -> {
                    EventManager.get().post(new BlockTransactionFetchedEvent(getTransaction(), blockTx, inputTransactions, indexStart, maxIndex));
                });
            });
            transactionReferenceService.setOnFailed(failedEvent -> {
                log.error("Error fetching transaction or input references", failedEvent.getSource().getException());
                EventManager.get().post(new TransactionReferencesFailedEvent(getTransaction(), failedEvent.getSource().getException(), indexStart, maxIndex));
            });
            EventManager.get().post(new TransactionReferencesStartedEvent(getTransaction(), indexStart, maxIndex));
            transactionReferenceService.start();
        }
    }

    private void fetchOutputBlockTransactions(int indexStart, int indexEnd) {
        if(AppServices.isConnected() && getPSBT() == null && indexStart < getTransaction().getOutputs().size()) {
            int maxIndex = Math.min(getTransaction().getOutputs().size(), indexEnd);
            ElectrumServer.TransactionOutputsReferenceService transactionOutputsReferenceService = new ElectrumServer.TransactionOutputsReferenceService(getTransaction(), indexStart, maxIndex);
            transactionOutputsReferenceService.setOnSucceeded(successEvent -> {
                List<BlockTransaction> outputTransactions = transactionOutputsReferenceService.getValue();
                Platform.runLater(() -> {
                    EventManager.get().post(new BlockTransactionOutputsFetchedEvent(getTransaction(), outputTransactions, indexStart, maxIndex));
                });
            });
            transactionOutputsReferenceService.setOnFailed(failedEvent -> {
                log.error("Error fetching transaction output references", failedEvent.getSource().getException());
                EventManager.get().post(new TransactionReferencesFailedEvent(getTransaction(), failedEvent.getSource().getException(), indexStart, maxIndex));
            });
            EventManager.get().post(new TransactionReferencesStartedEvent(getTransaction(), indexStart, maxIndex));
            transactionOutputsReferenceService.start();
        }
    }

    public void setTransactionData(TransactionData transactionData) {
        this.txdata = transactionData;
    }

    public Transaction getTransaction() {
        return txdata.getTransaction();
    }

    public PSBT getPSBT() {
        return txdata.getPsbt();
    }

    public BlockTransaction getBlockTransaction() {
        return txdata.getBlockTransaction();
    }

    public void setInitialView(TransactionView initialView, Integer initialIndex) {
        this.initialView = initialView;
        this.initialIndex = initialIndex;
    }

    @Subscribe
    public void viewTransaction(ViewTransactionEvent event) {
        if(txdata.getTransaction().getTxId().equals(event.getBlockTransaction().getTransaction().getTxId())) {
            TreeItem<TransactionForm> existingItem = getTreeItem(event.getInitialView(), event.getInitialIndex());
            if(existingItem != null && !(existingItem.getValue() instanceof PageForm)) {
                setTreeSelection(event.getInitialView(), event.getInitialIndex());
            } else if(event.getInitialView().equals(TransactionView.INPUT) || event.getInitialView().equals(TransactionView.OUTPUT)) {
                TreeItem<TransactionForm> parentItem = getTreeItem(event.getInitialView().equals(TransactionView.INPUT) ? TransactionView.INPUTS : TransactionView.OUTPUTS, null);
                TreeItem<TransactionForm> newItem = event.getInitialView().equals(TransactionView.INPUT) ? createInputTreeItem(event.getInitialIndex()) : createOutputTreeItem(event.getInitialIndex());

                int max = event.getInitialView().equals(TransactionView.INPUT) ? getTransaction().getInputs().size() : getTransaction().getOutputs().size();
                PageForm nextPageForm = new PageForm(event.getInitialView(), event.getInitialIndex() + 1, Math.min(max, event.getInitialIndex() + 1 + PageForm.PAGE_SIZE));
                TreeItem<TransactionForm> nextPageItem = new TreeItem<>(nextPageForm);

                if(existingItem != null) {
                    parentItem.getChildren().remove(existingItem);
                }

                int highestIndex = ((IndexedTransactionForm)parentItem.getChildren().get(parentItem.getChildren().size() - 1).getValue()).getIndex();
                if(event.getInitialIndex() < highestIndex) {
                    for(int i = 0; i < parentItem.getChildren().size(); i++) {
                        TreeItem<TransactionForm> childItem = parentItem.getChildren().get(i);
                        IndexedTransactionForm txForm = (IndexedTransactionForm)childItem.getValue();
                        if(txForm.getIndex() > event.getInitialIndex()) {
                            parentItem.getChildren().add(i, newItem);
                            if(txForm.getIndex() != event.getInitialIndex() + 1) {
                                parentItem.getChildren().add(i + 1, nextPageItem);
                            }
                            break;
                        }
                    }
                } else {
                    parentItem.getChildren().add(newItem);
                    if((event.getInitialIndex() + 1) != max) {
                        parentItem.getChildren().add(nextPageItem);
                    }
                }

                if(event.getInitialView().equals(TransactionView.INPUT)) {
                    fetchThisAndInputBlockTransactions(event.getInitialIndex(), event.getInitialIndex() + 1);
                } else {
                    fetchOutputBlockTransactions(event.getInitialIndex(), event.getInitialIndex() + 1);
                }

                setTreeSelection(event.getInitialView(), event.getInitialIndex());
            }
        }
    }

    @Subscribe
    public void transactionChanged(TransactionChangedEvent event) {
        if(event.getTransaction().equals(getTransaction())) {
            txhex.setTransaction(getTransaction());
            highlightTxHex();
            txtree.refresh();
        }
    }

    @Subscribe
    public void tabSelected(TransactionTabSelectedEvent event) {

    }

    @Subscribe
    public void tabChanged(TransactionTabChangedEvent event) {
        transactionMasterDetail.setShowDetailNode(event.isTxHexVisible());
    }

    @Subscribe
    public void blockTransactionFetched(BlockTransactionFetchedEvent event) {
        if(event.getTxId().equals(getTransaction().getTxId())) {
            if(event.getBlockTransaction() != null && (!Sha256Hash.ZERO_HASH.equals(event.getBlockTransaction().getBlockHash()) || txdata.getBlockTransaction() == null)) {
                txdata.setBlockTransaction(event.getBlockTransaction());
            }
            if(txdata.getInputTransactions() == null) {
                txdata.setInputTransactions(event.getInputTransactions());
            } else {
                txdata.getInputTransactions().putAll(event.getInputTransactions());
            }
            txdata.updateInputsFetchedRange(event.getPageStart(), event.getPageEnd());
        }
    }

    @Subscribe
    public void blockTransactionOutputsFetched(BlockTransactionOutputsFetchedEvent event) {
        if (event.getTxId().equals(getTransaction().getTxId())) {
            if(txdata.getOutputTransactions() == null) {
                txdata.setOutputTransactions(event.getOutputTransactions());
            } else {
                for(int i = 0; i < event.getOutputTransactions().size(); i++) {
                    BlockTransaction outputTransaction = event.getOutputTransactions().get(i);
                    if(outputTransaction != null) {
                        txdata.getOutputTransactions().set(i, outputTransaction);
                    }
                }
            }
            txdata.updateOutputsFetchedRange(event.getPageStart(), event.getPageEnd());
        }
    }

    @Subscribe
    public void transactionExtracted(TransactionExtractedEvent event) {
        if(event.getPsbt().equals(getPSBT())) {
            txhex.setTransaction(getTransaction());
            highlightTxHex();
            txtree.refresh();
        }
    }

    @Subscribe
    public void transactionTabsClosed(TransactionTabsClosedEvent event) {
        for(TransactionTabData tabData : event.getClosedTransactionTabData()) {
            if(tabData.getTransactionData() == txdata) {
                EventManager.get().unregister(this);
            }
        }
    }
}