package com.craigraw.sparrow.form;

import com.craigraw.drongo.psbt.PSBTInput;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;

import java.io.IOException;

public class PartialInputForm extends Form {
    private PSBTInput psbtInput;

    public PartialInputForm(PSBTInput psbtInput) {
        this.psbtInput = psbtInput;
    }

    public Node getContents() throws IOException {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("partialinput.fxml"));
        Node node = loader.load();
        PartialInputController controller = loader.getController();
        controller.setModel(this);
        return node;
    }
}
