package com.sparrowwallet.sparrow.joinstr;

import com.sparrowwallet.sparrow.joinstr.control.JoinstrPoolList;

import javafx.fxml.FXML;

public class OtherPoolsController extends JoinstrFormController {

    @FXML
    private JoinstrPoolList joinstrPoolList;

    @Override
    public void initializeView() {
        joinstrPoolList.fillList();
    }
}
