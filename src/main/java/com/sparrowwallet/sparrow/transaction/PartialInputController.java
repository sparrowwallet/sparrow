package com.sparrowwallet.sparrow.transaction;

import javafx.fxml.Initializable;

import java.net.URL;
import java.util.ResourceBundle;

public class PartialInputController extends TransactionFormController implements Initializable {
    private PartialInputForm partialInputForm;

    @Override
    public void initialize(URL location, ResourceBundle resources) {

    }

    public void setModel(PartialInputForm form) {
        this.partialInputForm = form;
    }
}
