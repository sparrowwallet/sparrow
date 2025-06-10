package com.sparrowwallet.sparrow.joinstr;

import com.sparrowwallet.sparrow.io.Config;
import com.sparrowwallet.sparrow.joinstr.control.JoinstrInfoPane;
import com.sparrowwallet.sparrow.joinstr.control.JoinstrPoolList;

import java.util.ArrayList;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;

public class MyPoolsController extends JoinstrFormController {

    @FXML
    private VBox contentVBox;

    @FXML
    private TextField searchTextField;

    private JoinstrPoolList joinstrPoolList;
    private JoinstrInfoPane joinstrInfoPane;

    @Override
    public void initializeView() {
        try {
            joinstrPoolList = new JoinstrPoolList();

            joinstrPoolList.configureWithJoinButtons();

            // Add pool store data
            addPoolStoreData();

            joinstrInfoPane = new JoinstrInfoPane();
            joinstrInfoPane.initInfoPane();
            joinstrInfoPane.setVisible(false);
            joinstrInfoPane.setManaged(false);

            joinstrPoolList.setOnPoolSelectedListener(pool -> {
                if (pool != null) {
                    joinstrInfoPane.setVisible(true);
                    joinstrInfoPane.setManaged(true);
                    joinstrInfoPane.updatePoolInfo(pool);
                } else {
                    joinstrInfoPane.setVisible(false);
                    joinstrInfoPane.setManaged(false);
                }
            });

            contentVBox.getChildren().addAll(joinstrPoolList, joinstrInfoPane);

            searchTextField.textProperty().addListener((observable, oldValue, newValue) -> {
                filterPools(newValue);
            });

        } catch (Exception e) {
            if(e != null) {
                e.printStackTrace();
            }
        }

    }

    private void addPoolStoreData() {

        ArrayList<JoinstrPool> pools = Config.get().getPoolStore();
        for (JoinstrPool pool: pools) {
            joinstrPoolList.addPool(pool);
        }

    }

    private void filterPools(String searchText) {
        joinstrPoolList.filterPools(searchText);
    }

    public void handleSearchButton(ActionEvent e) {
        if(e.getSource()==searchTextField) {
            System.out.println(searchTextField.getText());
        };
    }

}
