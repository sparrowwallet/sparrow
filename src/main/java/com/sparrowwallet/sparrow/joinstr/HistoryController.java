package com.sparrowwallet.sparrow.joinstr;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.TextField;

public class HistoryController extends JoinstrFormController {

    @FXML
    private TextField searchTextField;

    @Override
    public void initializeView() {

    }

    public void handleSearchButton(ActionEvent e) {
        if(e.getSource()==searchTextField) {
            System.out.println(searchTextField.getText());
        };
    }

}
