package com.sparrowwallet.sparrow.joinstr.control;

import com.sparrowwallet.sparrow.AppController;

import java.io.IOException;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.layout.VBox;
import javafx.scene.layout.Pane;

public class JoinstrPoolList extends Pane {

    @FXML
    private VBox listVBox;

    public JoinstrPoolList() {

        FXMLLoader loader = new FXMLLoader(AppController.class.getResource("joinstr/control/joinstrpoollist.fxml"));
        loader.setRoot(this);
        loader.setController(this);

        try {
            loader.load();

        } catch (IOException e) {
            throw new RuntimeException(e);
        }

    }

    public void fillList() {

        JoinstrPoolListItem listItem1 = new JoinstrPoolListItem();
        listItem1.setLabel("Pool #1");
        listVBox.getChildren().add(listItem1);

        JoinstrPoolListItem listItem2 = new JoinstrPoolListItem();
        listItem2.setLabel("Pool #2");
        listVBox.getChildren().add(listItem2);

        JoinstrPoolListItem listItem3 = new JoinstrPoolListItem();
        listItem3.setLabel("Pool #3");
        listVBox.getChildren().add(listItem3);

    }

}
