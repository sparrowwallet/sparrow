package com.craigraw.sparrow.form;

import javafx.fxml.Initializable;

import java.net.URL;
import java.util.ResourceBundle;

public class OutputsController implements Initializable {
    private OutputsForm outputsForm;

    @Override
    public void initialize(URL location, ResourceBundle resources) {

    }

    public void setModel(OutputsForm form) {
        this.outputsForm = form;
    }
}
