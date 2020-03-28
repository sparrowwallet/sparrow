package com.craigraw.sparrow.form;

import javafx.fxml.Initializable;

import java.net.URL;
import java.util.ResourceBundle;

public class InputsController implements Initializable {
    private InputsForm inputsForm;

    @Override
    public void initialize(URL location, ResourceBundle resources) {

    }

    public void setModel(InputsForm form) {
        this.inputsForm = form;
    }
}
