package com.sparrowwallet.sparrow.transaction;

import javafx.fxml.Initializable;

import java.net.URL;
import java.util.ResourceBundle;

public class OutputController extends TransactionFormController implements Initializable {
    private OutputForm outputForm;

    @Override
    public void initialize(URL location, ResourceBundle resources) {

    }

    public void setModel(OutputForm form) {
        this.outputForm = form;
    }
}
