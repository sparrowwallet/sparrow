package com.craigraw.sparrow.form;

import com.craigraw.drongo.psbt.PSBTOutput;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;

import java.io.IOException;

public class PartialOutputForm extends Form {
    private PSBTOutput psbtOutput;

    public PartialOutputForm(PSBTOutput psbtOutput) {
        this.psbtOutput = psbtOutput;
    }

    public Node getContents() throws IOException {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("partialoutput.fxml"));
        Node node = loader.load();
        PartialOutputController controller = loader.getController();
        controller.setModel(this);
        return node;
    }
}
