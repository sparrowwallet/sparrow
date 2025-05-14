package com.sparrowwallet.sparrow.joinstr.control;

import com.sparrowwallet.sparrow.AppController;

import java.io.IOException;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.Label;
import javafx.scene.layout.Pane;

public class JoinstrPoolListItem extends Pane {

    @FXML
    private Label lblTest;

    public JoinstrPoolListItem() {

        FXMLLoader loader = new FXMLLoader(AppController.class.getResource("joinstr/control/joinstrpoollistitem.fxml"));
        loader.setRoot(this);
        loader.setController(this);

        try {
            loader.load();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

    }

    public void setLabel(String txt) {
        lblTest.setText(txt);
    }

}
