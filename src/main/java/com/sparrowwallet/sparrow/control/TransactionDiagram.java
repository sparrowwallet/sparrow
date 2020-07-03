package com.sparrowwallet.sparrow.control;

import com.sparrowwallet.drongo.address.Address;
import com.sparrowwallet.drongo.wallet.BlockTransactionHashIndex;
import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.drongo.wallet.WalletNode;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.*;

import java.util.Collection;

public class TransactionDiagram extends GridPane {
    public TransactionDiagram() {
        int columns = 5;
        double percentWidth = 100.0 / columns;

        for(int i = 0; i < columns; i++) {
            ColumnConstraints columnConstraints = new ColumnConstraints();
            columnConstraints.setPercentWidth(percentWidth);
            getColumnConstraints().add(columnConstraints);
        }
    }

    public void update(Wallet wallet, Collection<BlockTransactionHashIndex> inputs, Address toAddress, WalletNode changeNode, long fee) {
        Pane inputsPane = getInputsLabels(inputs);
        GridPane.setConstraints(inputsPane, 0, 0);

        Pane txPane = getTransactionPane();
        GridPane.setConstraints(inputsPane, 2, 0);

        Pane outputsPane = getOutputsLabels(wallet, toAddress, changeNode, fee);
        GridPane.setConstraints(inputsPane, 4, 0);

        getChildren().clear();
        getChildren().addAll(inputsPane, txPane, outputsPane);
    }

    private Pane getInputsLabels(Collection<BlockTransactionHashIndex> inputs) {
        VBox inputsBox = new VBox();
        inputsBox.setAlignment(Pos.CENTER_RIGHT);
        inputsBox.getChildren().add(createSpacer());
        for(BlockTransactionHashIndex input : inputs) {
            String desc = input.getLabel() != null && !input.getLabel().isEmpty() ? input.getLabel() : input.getHashAsString().substring(0, 8) + "...:" + input.getIndex();
            Label label = new Label(desc);
            inputsBox.getChildren().add(label);
            inputsBox.getChildren().add(createSpacer());
        }

        return inputsBox;
    }

    private Pane getOutputsLabels(Wallet wallet, Address toAddress, WalletNode changeNode, long fee) {
        VBox outputsBox = new VBox();
        outputsBox.setAlignment(Pos.CENTER_LEFT);
        outputsBox.getChildren().add(createSpacer());

        String addressDesc = toAddress.toString();
        Label addressLabel = new Label(addressDesc);
        outputsBox.getChildren().add(addressLabel);
        outputsBox.getChildren().add(createSpacer());

        String changeDesc = wallet.getAddress(changeNode).toString();
        Label changeLabel = new Label(changeDesc);
        outputsBox.getChildren().add(changeLabel);
        outputsBox.getChildren().add(createSpacer());

        String feeDesc = "Fee";
        Label feeLabel = new Label(feeDesc);
        outputsBox.getChildren().add(feeLabel);
        outputsBox.getChildren().add(createSpacer());

        return outputsBox;
    }

    private Pane getTransactionPane() {
        VBox txPane = new VBox();
        txPane.setAlignment(Pos.CENTER);
        txPane.getChildren().add(createSpacer());

        String txDesc = "Transaction";
        Label txLabel = new Label(txDesc);
        txPane.getChildren().add(txLabel);
        txPane.getChildren().add(createSpacer());

        return txPane;
    }

    private Node createSpacer() {
        final Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);
        return spacer;
    }
}
