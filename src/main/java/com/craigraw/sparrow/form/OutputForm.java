package com.craigraw.sparrow.form;

import com.craigraw.drongo.protocol.TransactionOutput;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;

import java.io.IOException;

public class OutputForm extends Form {
    private TransactionOutput transactionOutput;

    public OutputForm(TransactionOutput transactionOutput) {
        this.transactionOutput = transactionOutput;
    }

    public Node getContents() throws IOException {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("output.fxml"));
        Node node = loader.load();
        OutputController controller = loader.getController();
        controller.setModel(this);
        return node;
    }

    public String toString() {
        return "Output #" + transactionOutput.getIndex();
    }
}
