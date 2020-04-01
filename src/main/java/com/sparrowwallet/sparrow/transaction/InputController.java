package com.sparrowwallet.sparrow.transaction;

import javafx.fxml.Initializable;

import java.net.URL;
import java.util.ResourceBundle;

public class InputController extends TransactionFormController implements Initializable {
    private InputForm inputForm;

    @Override
    public void initialize(URL location, ResourceBundle resources) {

    }

    public void setModel(InputForm form) {
        this.inputForm = form;
    }
}
