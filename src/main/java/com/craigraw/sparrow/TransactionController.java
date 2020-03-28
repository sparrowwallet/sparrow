package com.craigraw.sparrow;

import com.craigraw.drongo.protocol.*;
import com.craigraw.drongo.psbt.PSBT;
import com.craigraw.sparrow.form.*;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.control.cell.TextFieldTreeCell;
import javafx.scene.layout.Pane;
import javafx.util.StringConverter;
import org.bouncycastle.util.encoders.Hex;
import org.fxmisc.richtext.CodeArea;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URL;
import java.util.ResourceBundle;

public class TransactionController implements Initializable, TransactionListener {

    @FXML
    private TreeView<Form> txtree;

    @FXML
    private Pane txpane;

    @FXML
    private CodeArea txhex;

    private Transaction transaction;
    private PSBT psbt;

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
        TreeItem<Form> rootItem = new TreeItem<>(headersForm);
        rootItem.setExpanded(true);

        InputsForm inputsForm = new InputsForm(transaction);
        TreeItem<Form> inputsItem = new TreeItem<>(inputsForm);
        inputsItem.setExpanded(true);
        for(TransactionInput txInput : transaction.getInputs()) {
            InputForm inputForm = new InputForm(txInput);
            TreeItem<Form> inputItem = new TreeItem<>(inputForm);
            inputsItem.getChildren().add(inputItem);
        }

        OutputsForm outputsForm = new OutputsForm(transaction);
        TreeItem<Form> outputsItem = new TreeItem<>(outputsForm);
        outputsItem.setExpanded(true);
        for(TransactionOutput txOutput : transaction.getOutputs()) {
            OutputForm outputForm = new OutputForm(txOutput);
            TreeItem<Form> outputItem = new TreeItem<>(outputForm);
            outputsItem.getChildren().add(outputItem);
        }

        rootItem.getChildren().add(inputsItem);
        rootItem.getChildren().add(outputsItem);
        txtree.setRoot(rootItem);

        txtree.setCellFactory(p -> new TextFieldTreeCell<>(new StringConverter<Form>(){
            @Override
            public String toString(Form form) {
                return form.toString();
            }

            @Override
            public Form fromString(String string) {
                throw new IllegalStateException("No editing");
            }
        }));

        txtree.getSelectionModel().selectedItemProperty().addListener((observable, old_val, new_val) -> {
            Form form = new_val.getValue();

            try {
                txpane.getChildren().removeAll();
                txpane.getChildren().add(form.getContents());
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
            hex = Hex.toHexString(baos.toByteArray());
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
        cursor = addText(hex, cursor, 2, "num-inputs");

        //Inputs
        int totalInputLength = 0;
        for(TransactionInput input : transaction.getInputs()) {
            totalInputLength += input.getLength();
        }
        cursor = addText(hex, cursor, totalInputLength*2, "inputs");

        //Number of outputs
        cursor = addText(hex, cursor, 2, "num-outputs");

        //Outputs
        int totalOutputLength = 0;
        for(TransactionOutput output : transaction.getOutputs()) {
            totalOutputLength += output.getLength();
        }
        cursor = addText(hex, cursor, totalOutputLength*2, "outputs");

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

    private int addText(String hex, int cursor, int length, String description) {
        txhex.append(hex.substring(cursor, cursor+=length), description + "-color");
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
