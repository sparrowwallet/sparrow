package com.craigraw.sparrow.form;

import javafx.fxml.Initializable;

import java.net.URL;
import java.util.ResourceBundle;

public class PartialInputController implements Initializable {
    private PartialInputForm partialInputForm;

    @Override
    public void initialize(URL location, ResourceBundle resources) {

    }

    public void setModel(PartialInputForm form) {
        this.partialInputForm = form;
    }
}
