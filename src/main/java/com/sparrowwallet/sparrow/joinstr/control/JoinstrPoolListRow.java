package com.sparrowwallet.sparrow.joinstr.control;

import com.sparrowwallet.sparrow.joinstr.JoinstrAction;
import com.sparrowwallet.sparrow.joinstr.JoinstrPool;

import javafx.event.ActionEvent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

public class JoinstrPoolListRow extends GridPane {

    private JoinstrPool poolData;

    /// Constructor for header
    public JoinstrPoolListRow() {

        setMaxWidth(Double.MAX_VALUE);
        setMaxHeight(Double.MAX_VALUE);
        GridPane.setHgrow(this, Priority.ALWAYS);
        setColumnConstraints();

        Label relayLabel = new Label("Relay");
        GridPane.setRowIndex(relayLabel, 0);
        GridPane.setColumnIndex(relayLabel, 0);

        Label pubkeyLabel = new Label("Pubkey (Shortened)");
        GridPane.setRowIndex(pubkeyLabel, 0);
        GridPane.setColumnIndex(pubkeyLabel, 1);

        Label denominationLabel = new Label("Denomination");
        GridPane.setRowIndex(denominationLabel, 0);
        GridPane.setColumnIndex(denominationLabel, 2);

        Label peersLabel = new Label("Peers");
        GridPane.setRowIndex(peersLabel, 0);
        GridPane.setColumnIndex(peersLabel, 3);

        Label timeoutLabel = new Label("Timeout");
        GridPane.setRowIndex(timeoutLabel, 0);
        GridPane.setColumnIndex(timeoutLabel, 4);

        Label actionLabel = new Label("Action");
        GridPane.setRowIndex(actionLabel, 0);
        GridPane.setColumnIndex(actionLabel, 5);

        getChildren().addAll(relayLabel, pubkeyLabel, denominationLabel, peersLabel, timeoutLabel, actionLabel);

        getStyleClass().add("joinstr-list-header");

    }

    /// Constructor for data rows
    public JoinstrPoolListRow(JoinstrPool poolData_, JoinstrAction action) {
        poolData = poolData_;

        setMaxWidth(Double.MAX_VALUE);
        setMaxHeight(Double.MAX_VALUE);
        GridPane.setHgrow(this, Priority.ALWAYS);
        setColumnConstraints();

        VBox relayVBox = new VBox();
        Label relayLabel = new Label(poolData.getRelay());
        relayLabel.getStyleClass().add("joinstr-list-item");

        Label portLabel = new Label("Port: " + poolData.getPort().toString());
        portLabel.getStyleClass().add("joinstr-list-item-subtitle");

        relayVBox.getChildren().addAll(relayLabel, portLabel);
        GridPane.setRowIndex(relayVBox, 0);
        GridPane.setColumnIndex(relayVBox, 0);

        VBox pubkeyVBox = new VBox();
        Label pubkeyLabel = new Label(poolData.getPubkey());
        pubkeyLabel.getStyleClass().add("joinstr-list-item");

        Label pubkeyTypeLabel = new Label("type");
        pubkeyTypeLabel.getStyleClass().add("joinstr-list-item-subtitle");

        pubkeyVBox.getChildren().addAll(pubkeyLabel, pubkeyTypeLabel);
        GridPane.setRowIndex(pubkeyVBox, 0);
        GridPane.setColumnIndex(pubkeyVBox, 1);

        VBox denominationVBox = new VBox();
        Label denominationLabel = new Label(poolData.getDenomination().toString());
        denominationLabel.getStyleClass().add("joinstr-list-item");

        Label denominationMinLabel = new Label("Min: _____ BTC");
        denominationMinLabel.getStyleClass().add("joinstr-list-item-subtitle");

        denominationVBox.getChildren().addAll(denominationLabel, denominationMinLabel);
        GridPane.setRowIndex(denominationVBox, 0);
        GridPane.setColumnIndex(denominationVBox, 2);

        VBox peersVBox = new VBox();
        Label peersLabel = new Label("__/__");
        peersLabel.getStyleClass().add("joinstr-list-item");

        Label peersMinLabel = new Label("Min: -");
        peersMinLabel.getStyleClass().add("joinstr-list-item-subtitle");

        peersVBox.getChildren().addAll(peersLabel, peersMinLabel);
        GridPane.setRowIndex(peersVBox, 0);
        GridPane.setColumnIndex(peersVBox, 3);

        VBox timeoutVBox = new VBox();
        Label timeoutLabel = new Label("0");
        timeoutLabel.getStyleClass().add("joinstr-list-item");

        Label timeoutTypeLabel = new Label("mins");
        timeoutTypeLabel.getStyleClass().add("joinstr-list-item-subtitle");

        timeoutVBox.getChildren().addAll(timeoutLabel, timeoutTypeLabel);
        GridPane.setRowIndex(timeoutVBox, 0);
        GridPane.setColumnIndex(timeoutVBox, 4);

        Button actionButton = new Button();
        actionButton.getStyleClass().add("joinstr-list-action-button");
        switch(action) {
            case JOIN -> {
                actionButton.setText("Join");
                actionButton.setOnAction(event -> {
                    System.out.println("Join " + poolData.getRelay() + "!");
                });
            }
            case REMOVE -> {
                // For MY_POOLS & HISTORY
                actionButton.setText("Remove");
                actionButton.setOnAction(event -> {
                    System.out.println("Remove " + poolData.getRelay() + "!");
                });
            }
        }
        GridPane.setRowIndex(actionButton, 0);
        GridPane.setColumnIndex(actionButton, 5);

        getChildren().addAll( relayVBox, pubkeyVBox, denominationVBox, peersVBox, timeoutVBox, actionButton);
        getStyleClass().add("joinstr-list-row");

    }

    private void setColumnConstraints() {

        ColumnConstraints column1 = new ColumnConstraints();
        column1.setPercentWidth(25);
        ColumnConstraints column2 = new ColumnConstraints();
        column2.setPercentWidth(25);
        ColumnConstraints column3 = new ColumnConstraints();
        column3.setPercentWidth(16);
        ColumnConstraints column4 = new ColumnConstraints();
        column4.setPercentWidth(10);
        ColumnConstraints column5 = new ColumnConstraints();
        column5.setPercentWidth(10);
        ColumnConstraints column6 = new ColumnConstraints();
        column6.setPercentWidth(14);

        getColumnConstraints().addAll(column1, column2, column3, column4, column5, column6);

    }

    public void handleActionButton(ActionEvent e) {
        if(e.getSource()!=null) {
            System.out.println("Action!");
        };
    }

}
