package com.sparrowwallet.sparrow.joinstr;

import com.sparrowwallet.sparrow.joinstr.control.JoinstrInfoPane;
import com.sparrowwallet.sparrow.joinstr.control.JoinstrPoolList;

import javafx.beans.property.SimpleStringProperty;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;

public class OtherPoolsController extends JoinstrFormController {

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

            // Add sample pool data
            addSamplePoolData();

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

    private void addSamplePoolData() {
        // Create the two sample pools
        JoinstrPool pool1 = new JoinstrPool(
                "relay.joinstr.xyz",
                "03ab4...e92f",
                "0.001 BTC",
                "4/5",
                "00:00:00 UTC"
        );

        JoinstrPool pool2 = new JoinstrPool(
                "relay.joinstr.xyz",
                "02c4f...19a3",
                "0.005 BTC",
                "3/7",
                "00:00:00 UTC"
        );

        joinstrPoolList.addPool(pool1);
        joinstrPoolList.addPool(pool2);
    }

    private void filterPools(String searchText) {
        joinstrPoolList.filterPools(searchText);
    }

    public void handleSearchButton(ActionEvent e) {
        if(e.getSource() == searchTextField) {
            filterPools(searchTextField.getText());
        }
    }

    public static class JoinstrPool {
        private final SimpleStringProperty relay;
        private final SimpleStringProperty pubkey;
        private final SimpleStringProperty denomination;
        private final SimpleStringProperty peers;
        private final SimpleStringProperty timeout;

        public JoinstrPool(String relay, String pubkey, String denomination,
                           String peers, String timeout) {
            this.relay = new SimpleStringProperty(relay);
            this.pubkey = new SimpleStringProperty(pubkey);
            this.denomination = new SimpleStringProperty(denomination);
            this.peers = new SimpleStringProperty(peers);
            this.timeout = new SimpleStringProperty(timeout);
        }

        public String getRelay() { return relay.get(); }
        public String getPubkey() { return pubkey.get(); }
        public String getDenomination() { return denomination.get(); }
        public String getPeers() { return peers.get(); }
        public String getTimeout() { return timeout.get(); }
    }
}