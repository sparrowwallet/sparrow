package com.sparrowwallet.sparrow.form;

import javafx.fxml.Initializable;

import java.net.URL;
import java.util.ResourceBundle;

public class OutputController implements Initializable {
    private OutputForm outputForm;

    @Override
    public void initialize(URL location, ResourceBundle resources) {

    }

    public void setModel(OutputForm form) {
        this.outputForm = form;
    }
}
