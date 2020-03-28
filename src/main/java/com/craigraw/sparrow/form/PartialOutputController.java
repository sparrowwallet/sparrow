package com.craigraw.sparrow.form;

import javafx.fxml.Initializable;

import java.net.URL;
import java.util.ResourceBundle;

public class PartialOutputController implements Initializable {
    private PartialOutputForm partialOutputForm;

    @Override
    public void initialize(URL location, ResourceBundle resources) {

    }

    public void setModel(PartialOutputForm form) {
        this.partialOutputForm = form;
    }
}
