package com.sparrowwallet.sparrow.transaction;

import com.google.common.eventbus.Subscribe;
import com.sparrowwallet.drongo.Utils;
import com.sparrowwallet.drongo.protocol.*;
import com.sparrowwallet.drongo.psbt.PSBT;
import com.sparrowwallet.drongo.psbt.PSBTInput;
import com.sparrowwallet.drongo.psbt.PSBTOutput;
import com.sparrowwallet.sparrow.AppController;
import com.sparrowwallet.sparrow.EventManager;
import com.sparrowwallet.sparrow.event.TransactionChangedEvent;
import com.sparrowwallet.sparrow.event.TransactionTabChangedEvent;
import com.sparrowwallet.sparrow.event.TransactionTabSelectedEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.control.cell.TextFieldTreeCell;
import javafx.scene.layout.Pane;
import javafx.util.StringConverter;
import org.controlsfx.control.MasterDetailPane;
import org.fxmisc.richtext.CodeArea;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URL;
import java.util.ResourceBundle;

public class TransactionController implements Initializable {

    @FXML
    private Node tabContent;

    @FXML
    private MasterDetailPane transactionMasterDetail;

    @FXML
    private TreeView<TransactionForm> txtree;

    @FXML
    private Pane txpane;

    @FXML
    private CodeArea txhex;

    private Transaction transaction;
    private PSBT psbt;
    private int selectedInputIndex = -1;
    private int selectedOutputIndex = -1;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        EventManager.get().register(this);
    }

    private void initializeView() {
        initializeTxTree();
        transactionMasterDetail.setShowDetailNode(AppController.showTxHexProperty);
        refreshTxHex();
    }

    private void initializeTxTree() {
        HeadersForm headersForm = (psbt == null ? new HeadersForm(transaction) : new HeadersForm(psbt));
        TreeItem<TransactionForm> rootItem = new TreeItem<>(headersForm);
        rootItem.setExpanded(true);

        InputsForm inputsForm = (psbt == null ? new InputsForm(transaction) : new InputsForm(psbt));
        TreeItem<TransactionForm> inputsItem = new TreeItem<>(inputsForm);
        inputsItem.setExpanded(true);
        for (TransactionInput txInput : transaction.getInputs()) {
            PSBTInput psbtInput = null;
            if (psbt != null && psbt.getPsbtInputs().size() > txInput.getIndex()) {
                psbtInput = psbt.getPsbtInputs().get(txInput.getIndex());
            }
            InputForm inputForm = (psbt == null ? new InputForm(transaction, txInput) : new InputForm(psbt, psbtInput));
            TreeItem<TransactionForm> inputItem = new TreeItem<>(inputForm);
            inputsItem.getChildren().add(inputItem);
        }

        OutputsForm outputsForm = (psbt == null ? new OutputsForm(transaction) : new OutputsForm(psbt));
        TreeItem<TransactionForm> outputsItem = new TreeItem<>(outputsForm);
        outputsItem.setExpanded(true);
        for (TransactionOutput txOutput : transaction.getOutputs()) {
            PSBTOutput psbtOutput = null;
            if (psbt != null && psbt.getPsbtOutputs().size() > txOutput.getIndex()) {
                psbtOutput = psbt.getPsbtOutputs().get(txOutput.getIndex());
            }
            OutputForm outputForm = (psbt == null ? new OutputForm(transaction, txOutput) : new OutputForm(psbt, psbtOutput));
            TreeItem<TransactionForm> outputItem = new TreeItem<>(outputForm);
            outputsItem.getChildren().add(outputItem);
        }

        rootItem.getChildren().add(inputsItem);
        rootItem.getChildren().add(outputsItem);
        txtree.setRoot(rootItem);

        txtree.setCellFactory(p -> new TextFieldTreeCell<>(new StringConverter<TransactionForm>() {
            @Override
            public String toString(TransactionForm transactionForm) {
                return transactionForm.toString();
            }

            @Override
            public TransactionForm fromString(String string) {
                throw new IllegalStateException("No editing");
            }
        }));

        txtree.getSelectionModel().selectedItemProperty().addListener((observable, old_val, new_val) -> {
            TransactionForm transactionForm = new_val.getValue();
            try {
                Node node = transactionForm.getContents();
                txpane.getChildren().clear();
                txpane.getChildren().add(node);

                if (node instanceof Parent) {
                    Parent parent = (Parent) node;
                    txhex.getStylesheets().clear();
                    txhex.getStylesheets().addAll(parent.getStylesheets());

                    selectedInputIndex = -1;
                    selectedOutputIndex = -1;
                    if (transactionForm instanceof InputForm) {
                        InputForm inputForm = (InputForm) transactionForm;
                        selectedInputIndex = inputForm.getTransactionInput().getIndex();
                    } else if (transactionForm instanceof OutputForm) {
                        OutputForm outputForm = (OutputForm) transactionForm;
                        selectedOutputIndex = outputForm.getTransactionOutput().getIndex();
                    }

                    refreshTxHex();
                }
            } catch (IOException e) {
                throw new IllegalStateException("Can't find pane", e);
            }
        });

        txtree.getSelectionModel().select(txtree.getRoot());
    }

    void refreshTxHex() {
        txhex.clear();

        String hex = "";
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            transaction.bitcoinSerializeToStream(baos);
            hex = Utils.bytesToHex(baos.toByteArray());
        } catch (IOException e) {
            throw new IllegalStateException("Can't happen");
        }

        int cursor = 0;

        //Version
        cursor = addText(hex, cursor, 8, "version");

        if (transaction.hasWitnesses()) {
            //Segwit marker
            cursor = addText(hex, cursor, 2, "segwit-marker");
            //Segwit flag
            cursor = addText(hex, cursor, 2, "segwit-flag");
        }

        //Number of inputs
        VarInt numInputs = new VarInt(transaction.getInputs().size());
        cursor = addText(hex, cursor, numInputs.getSizeInBytes() * 2, "num-inputs");

        //Inputs
        for (int i = 0; i < transaction.getInputs().size(); i++) {
            TransactionInput input = transaction.getInputs().get(i);
            cursor = addText(hex, cursor, 32 * 2, "input-" + getIndexedStyleClass(i, selectedInputIndex, "hash"));
            cursor = addText(hex, cursor, 4 * 2, "input-" + getIndexedStyleClass(i, selectedInputIndex, "index"));
            VarInt scriptLen = new VarInt(input.getScriptBytes().length);
            cursor = addText(hex, cursor, scriptLen.getSizeInBytes() * 2, "input-" + getIndexedStyleClass(i, selectedInputIndex, "sigscript-length"));
            cursor = addText(hex, cursor, (int) scriptLen.value * 2, "input-" + getIndexedStyleClass(i, selectedInputIndex, "sigscript"));
            cursor = addText(hex, cursor, 4 * 2, "input-" + getIndexedStyleClass(i, selectedInputIndex, "sequence"));
        }

        //Number of outputs
        VarInt numOutputs = new VarInt(transaction.getOutputs().size());
        cursor = addText(hex, cursor, numOutputs.getSizeInBytes() * 2, "num-outputs");

        //Outputs
        for (int i = 0; i < transaction.getOutputs().size(); i++) {
            TransactionOutput output = transaction.getOutputs().get(i);
            cursor = addText(hex, cursor, 8 * 2, "output-" + getIndexedStyleClass(i, selectedOutputIndex, "value"));
            VarInt scriptLen = new VarInt(output.getScriptBytes().length);
            cursor = addText(hex, cursor, scriptLen.getSizeInBytes() * 2, "output-" + getIndexedStyleClass(i, selectedOutputIndex, "pubkeyscript-length"));
            cursor = addText(hex, cursor, (int) scriptLen.value * 2, "output-" + getIndexedStyleClass(i, selectedOutputIndex, "pubkeyscript"));
        }

        if (transaction.hasWitnesses()) {
            for (int i = 0; i < transaction.getInputs().size(); i++) {
                TransactionInput input = transaction.getInputs().get(i);
                if (input.hasWitness()) {
                    TransactionWitness witness = input.getWitness();
                    VarInt witnessCount = new VarInt(witness.getPushCount());
                    cursor = addText(hex, cursor, witnessCount.getSizeInBytes() * 2, "witness-" + getIndexedStyleClass(i, selectedInputIndex, "count"));
                    for (byte[] push : witness.getPushes()) {
                        VarInt witnessLen = new VarInt(push.length);
                        cursor = addText(hex, cursor, witnessLen.getSizeInBytes() * 2, "witness-" + getIndexedStyleClass(i, selectedInputIndex, "length"));
                        cursor = addText(hex, cursor, (int) witnessLen.value * 2, "witness-" + getIndexedStyleClass(i, selectedInputIndex, "data"));
                    }
                }
            }
        }

        //Locktime
        cursor = addText(hex, cursor, 8, "locktime");

        if (cursor != hex.length()) {
            throw new IllegalStateException("Cursor position does not match transaction serialisation " + cursor + ": " + hex.length());
        }
    }

    private String getIndexedStyleClass(int iterableIndex, int selectedIndex, String styleClass) {
        if (selectedIndex == -1 || selectedIndex == iterableIndex) {
            return styleClass;
        }

        return "other";
    }

    private int addText(String hex, int cursor, int length, String styleClass) {
        txhex.append(hex.substring(cursor, cursor += length), styleClass);
        return cursor;
    }

    public void setPSBT(PSBT psbt) {
        this.psbt = psbt;
        this.transaction = psbt.getTransaction();

        initializeView();
    }

    public void setTransaction(Transaction transaction) {
        this.transaction = transaction;

        initializeView();
    }

    @Subscribe
    public void transactionChanged(TransactionChangedEvent event) {
        if(event.getTransaction().equals(transaction)) {
            refreshTxHex();
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
}
