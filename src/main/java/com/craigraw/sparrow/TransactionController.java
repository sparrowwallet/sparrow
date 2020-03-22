package com.craigraw.sparrow;

import com.craigraw.drongo.protocol.*;
import com.craigraw.drongo.psbt.PSBT;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.control.cell.TextFieldTreeCell;
import javafx.util.Callback;
import javafx.util.StringConverter;

import java.net.URL;
import java.util.ResourceBundle;

public class TransactionController implements Initializable {

    @FXML
    private TreeView<TransactionPart> txtree;

    private Transaction transaction;
    private PSBT psbt;

    @Override
    public void initialize(URL location, ResourceBundle resources) {

    }

    private void initialiseTxTree() {
        TreeItem<TransactionPart> rootItem = new TreeItem<>(transaction);
        rootItem.setExpanded(true);

        InputsPart inputsPart = new InputsPart();
        TreeItem<TransactionPart> inputsItem = new TreeItem<TransactionPart>(inputsPart);
        for(TransactionInput input : transaction.getInputs()) {
            TreeItem<TransactionPart> inputItem = new TreeItem<>(input);
            inputsItem.getChildren().add(inputItem);
        }

        OutputsPart outputsPart = new OutputsPart();
        TreeItem<TransactionPart> outputsItem = new TreeItem<TransactionPart>(outputsPart);
        for(TransactionOutput output : transaction.getOutputs()) {
            TreeItem<TransactionPart> outputItem = new TreeItem<>(output);
            outputsItem.getChildren().add(outputItem);
        }

        rootItem.getChildren().add(inputsItem);
        rootItem.getChildren().add(outputsItem);
        txtree.setRoot(rootItem);

        txtree.setCellFactory(new Callback<TreeView<TransactionPart>, TreeCell<TransactionPart>>() {
            @Override
            public TreeCell<TransactionPart> call(TreeView<TransactionPart> p) {
                return new TextFieldTreeCell<TransactionPart>(new StringConverter<TransactionPart>(){

                    @Override
                    public String toString(TransactionPart part) {
                        if(part instanceof Transaction) {
                            Transaction transaction = (Transaction)part;
                            return "Tx " + transaction.getTxId().toString().substring(0, 6) + "...";
                        } else if(part instanceof InputsPart) {
                            return "Inputs";
                        } else if(part instanceof OutputsPart) {
                            return "Outputs";
                        } else if(part instanceof TransactionInput) {
                            TransactionInput input = (TransactionInput)part;
                            return "Input #" + input.getIndex();
                        } else if(part instanceof TransactionOutput) {
                            TransactionOutput output = (TransactionOutput)part;
                            return "Output #" + output.getIndex();
                        }

                        return part.toString();
                    }

                    @Override
                    public TransactionPart fromString(String string) {
                        throw new IllegalStateException("No fromString");
                    }
                });
            }
        });
    }

    public void setPSBT(PSBT psbt) {
        this.psbt = psbt;
        this.transaction = psbt.getTransaction();

        initialiseTxTree();
    }

    private static class InputsPart extends TransactionPart {
        public InputsPart() {
            super(new byte[0], 0);
        }

        @Override
        protected void parse() throws ProtocolException {

        }
    }

    private static class OutputsPart extends TransactionPart {
        public OutputsPart() {
            super(new byte[0], 0);
        }

        @Override
        protected void parse() throws ProtocolException {

        }
    }
}
