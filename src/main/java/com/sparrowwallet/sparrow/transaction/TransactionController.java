package com.sparrowwallet.sparrow.transaction;

import com.sparrowwallet.drongo.Utils;
import com.sparrowwallet.drongo.protocol.*;
import com.sparrowwallet.drongo.psbt.PSBT;
import com.sparrowwallet.sparrow.EventManager;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.control.cell.TextFieldTreeCell;
import javafx.scene.layout.Pane;
import javafx.util.StringConverter;
import org.fxmisc.richtext.CodeArea;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URL;
import java.util.ResourceBundle;

public class TransactionController implements Initializable, TransactionListener {

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
        EventManager.get().subscribe(this);
    }

    private void initializeView() {
        initializeTxTree();
        refreshTxHex();
    }

    private void initializeTxTree() {
        HeadersForm headersForm = new HeadersForm(transaction, psbt);
        TreeItem<TransactionForm> rootItem = new TreeItem<>(headersForm);
        rootItem.setExpanded(true);

        InputsForm inputsForm = new InputsForm(transaction, psbt);
        TreeItem<TransactionForm> inputsItem = new TreeItem<>(inputsForm);
        inputsItem.setExpanded(true);
        for(TransactionInput txInput : transaction.getInputs()) {
            InputForm inputForm = new InputForm(txInput);
            TreeItem<TransactionForm> inputItem = new TreeItem<>(inputForm);
            inputsItem.getChildren().add(inputItem);
        }

        OutputsForm outputsForm = new OutputsForm(transaction);
        TreeItem<TransactionForm> outputsItem = new TreeItem<>(outputsForm);
        outputsItem.setExpanded(true);
        for(TransactionOutput txOutput : transaction.getOutputs()) {
            OutputForm outputForm = new OutputForm(txOutput);
            TreeItem<TransactionForm> outputItem = new TreeItem<>(outputForm);
            outputsItem.getChildren().add(outputItem);
        }

        rootItem.getChildren().add(inputsItem);
        rootItem.getChildren().add(outputsItem);
        txtree.setRoot(rootItem);

        txtree.setCellFactory(p -> new TextFieldTreeCell<>(new StringConverter<TransactionForm>(){
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

                if(node instanceof Parent) {
                    Parent parent = (Parent)node;
                    txhex.getStylesheets().clear();
                    txhex.getStylesheets().addAll(parent.getStylesheets());

                    selectedInputIndex = -1;
                    selectedOutputIndex = -1;
                    if(transactionForm instanceof InputForm) {
                        InputForm inputForm = (InputForm)transactionForm;
                        selectedInputIndex = inputForm.getTransactionInput().getIndex();
                    } else if(transactionForm instanceof OutputForm) {
                        OutputForm outputForm = (OutputForm)transactionForm;
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
        } catch(IOException e) {
            throw new IllegalStateException("Can't happen");
        }

        int cursor = 0;

        //Version
        cursor = addText(hex, cursor, 8, "version");

        if(transaction.hasWitnesses()) {
            //Segwit marker
            cursor = addText(hex, cursor, 2, "segwit-marker");
            //Segwit flag
            cursor = addText(hex, cursor, 2, "segwit-flag");
        }

        //Number of inputs
        VarInt numInputs = new VarInt(transaction.getInputs().size());
        cursor = addText(hex, cursor, numInputs.getSizeInBytes()*2, "num-inputs");

        //Inputs
        for (int i = 0; i < transaction.getInputs().size(); i++) {
            TransactionInput input = transaction.getInputs().get(i);
            cursor = addText(hex, cursor, 32*2, "input-" + getIndexedStyleClass(i, selectedInputIndex, "hash"));
            cursor = addText(hex, cursor, 4*2, "input-" + getIndexedStyleClass(i, selectedInputIndex, "index"));
            VarInt scriptLen = new VarInt(input.getScriptBytes().length);
            cursor = addText(hex, cursor, scriptLen.getSizeInBytes()*2, "input-" + getIndexedStyleClass(i, selectedInputIndex, "sigscript-length"));
            cursor = addText(hex, cursor, (int)scriptLen.value*2, "input-" + getIndexedStyleClass(i, selectedInputIndex, "sigscript"));
            cursor = addText(hex, cursor, 4*2, "input-" + getIndexedStyleClass(i, selectedInputIndex, "sequence"));
        }

        //Number of outputs
        VarInt numOutputs = new VarInt(transaction.getOutputs().size());
        cursor = addText(hex, cursor, numOutputs.getSizeInBytes()*2, "num-outputs");

        //Outputs
        for (int i = 0; i < transaction.getOutputs().size(); i++) {
            TransactionOutput output = transaction.getOutputs().get(i);
            cursor = addText(hex, cursor, 8*2, "output-" + getIndexedStyleClass(i, selectedOutputIndex, "value"));
            VarInt scriptLen = new VarInt(output.getScriptBytes().length);
            cursor = addText(hex, cursor, scriptLen.getSizeInBytes()*2, "output-" + getIndexedStyleClass(i, selectedOutputIndex, "pubkeyscript-length"));
            cursor = addText(hex, cursor, (int)scriptLen.value*2, "output-" + getIndexedStyleClass(i, selectedOutputIndex, "pubkeyscript"));
        }

        if(transaction.hasWitnesses()) {
            int totalWitnessLength = 0;
            for(TransactionInput input : transaction.getInputs()) {
                totalWitnessLength += input.getWitness().getLength();
            }
            cursor = addText(hex, cursor, totalWitnessLength*2, "witnesses");
        }

        //Locktime
        cursor = addText(hex, cursor, 8, "locktime");

        if(cursor != hex.length()) {
            throw new IllegalStateException("Cursor position does not match transaction serialisation " + cursor + ": " + hex.length());
        }
    }

    private String getIndexedStyleClass(int iterableIndex, int selectedIndex, String styleClass) {
        if(selectedIndex == -1 || selectedIndex == iterableIndex) {
            return styleClass;
        }

        return "other";
    }

    private int addText(String hex, int cursor, int length, String styleClass) {
        txhex.append(hex.substring(cursor, cursor+=length), styleClass);
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

    @Override
    public void updated(Transaction transaction) {
        refreshTxHex();
        txtree.refresh();
    }
}
