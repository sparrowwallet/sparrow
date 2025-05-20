package com.sparrowwallet.sparrow.joinstr;

import com.sparrowwallet.sparrow.joinstr.control.JoinstrInfoPane;
import com.sparrowwallet.sparrow.joinstr.control.JoinstrPoolList;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;

public class OtherPoolsController extends JoinstrFormController {

    @FXML
    private VBox contentVBox;

    @FXML
    private TextField searchTextField;

    @Override
    public void initializeView() {
        try {

            JoinstrPoolList joinstrPoolList = new JoinstrPoolList(JoinstrAction.JOIN);

            JoinstrInfoPane joinstrInfoPane = new JoinstrInfoPane();
            joinstrInfoPane.initInfoPane();

            contentVBox.getChildren().addAll(joinstrPoolList, joinstrInfoPane);

        } catch (Exception e) {
            if(e != null) {}
        }
    }

    public void handleSearchButton(ActionEvent e) {
        if(e.getSource()==searchTextField) {
            System.out.println(searchTextField.getText());
        };
    }

}
