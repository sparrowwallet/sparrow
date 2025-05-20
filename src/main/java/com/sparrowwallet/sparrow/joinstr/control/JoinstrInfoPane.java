package com.sparrowwallet.sparrow.joinstr.control;

import javafx.scene.control.Label;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.GridPane;

public class JoinstrInfoPane extends AnchorPane {

    public JoinstrInfoPane() { super(); }

    public void initInfoPane() {

        GridPane mainGridPane = new GridPane();

        Label relayTitleLabel = new Label();
        relayTitleLabel.setText("Relay:");
        GridPane.setRowIndex(relayTitleLabel, 0);
        GridPane.setColumnIndex(relayTitleLabel, 0);

        Label pubkeyTitleLabel = new Label();
        pubkeyTitleLabel.setText("Pubkey:");
        GridPane.setRowIndex(pubkeyTitleLabel, 1);
        GridPane.setColumnIndex(pubkeyTitleLabel, 0);

        Label denominationTitleLabel = new Label();
        denominationTitleLabel.setText("Denomination:");
        GridPane.setRowIndex(denominationTitleLabel, 2);
        GridPane.setColumnIndex(denominationTitleLabel, 0);


        Label relayDescLabel = new Label();
        relayDescLabel.setText("Relay desc");
        GridPane.setRowIndex(relayDescLabel, 0);
        GridPane.setColumnIndex(relayDescLabel, 1);

        Label pubkeyDescLabel = new Label();
        pubkeyDescLabel.setText("Pubkey desc");
        GridPane.setRowIndex(pubkeyDescLabel, 1);
        GridPane.setColumnIndex(pubkeyDescLabel, 1);

        Label denominationDescLabel = new Label();
        denominationDescLabel.setText("Denomination desc");
        GridPane.setRowIndex(denominationDescLabel, 2);
        GridPane.setColumnIndex(denominationDescLabel, 1);

        getChildren().add(mainGridPane);
    }

}
