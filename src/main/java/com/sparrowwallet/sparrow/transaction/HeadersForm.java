package com.sparrowwallet.sparrow.transaction;

import com.sparrowwallet.drongo.protocol.Transaction;
import com.sparrowwallet.drongo.wallet.BlockTransaction;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;

import java.io.IOException;

public class HeadersForm extends TransactionForm {
    public HeadersForm(TransactionData txdata) {
        super(txdata);
    }

    void setFinalTransaction(Transaction finalTransaction) {
        txdata.setTransaction(finalTransaction);
    }

    void setBlockTransaction(BlockTransaction blockTransaction) {
        txdata.setBlockTransaction(blockTransaction);
    }

    @Override
    public Node getContents() throws IOException {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("headers.fxml"));
        Node node = loader.load();
        node.setUserData(this);
        HeadersController controller = loader.getController();
        controller.setModel(this);
        return node;
    }

    @Override
    public TransactionView getView() {
        return TransactionView.HEADERS;
    }

    public String toString() {
        return "Tx [" + getTransaction().calculateTxId(false).toString().substring(0, 6) + "]";
    }
}
