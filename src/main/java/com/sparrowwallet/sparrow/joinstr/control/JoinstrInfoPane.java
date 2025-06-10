package com.sparrowwallet.sparrow.joinstr.control;

import com.sparrowwallet.sparrow.joinstr.JoinstrPool;

import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;

public class JoinstrInfoPane extends VBox {

    private Label titleLabel;
    private Label relayLabel;
    private Label pubkeyLabel;
    private Label denominationLabel;
    private Label relayValueLabel;
    private Label pubkeyValueLabel;
    private Label denominationValueLabel;

    public JoinstrInfoPane() {
        setStyle("-fx-background-color: #222222; -fx-padding: 15;");
        setSpacing(10);
    }

    public void initInfoPane() {
        titleLabel = new Label("Selected Pool Details");
        titleLabel.setStyle("-fx-font-size: 16px; -fx-text-fill: white; -fx-font-weight: bold;");
        getChildren().add(titleLabel);

        GridPane detailsGrid = new GridPane();
        detailsGrid.setHgap(10);
        detailsGrid.setVgap(10);

        ColumnConstraints column1 = new ColumnConstraints();
        column1.setPrefWidth(100);
        ColumnConstraints column2 = new ColumnConstraints();
        column2.setPrefWidth(400);
        detailsGrid.getColumnConstraints().addAll(column1, column2);

        relayLabel = new Label("Relay:");
        relayLabel.setStyle("-fx-text-fill: #aaaaaa;");
        relayValueLabel = new Label();
        relayValueLabel.setStyle("-fx-text-fill: white;");

        pubkeyLabel = new Label("Pubkey:");
        pubkeyLabel.setStyle("-fx-text-fill: #aaaaaa;");
        pubkeyValueLabel = new Label();
        pubkeyValueLabel.setStyle("-fx-text-fill: white;");

        denominationLabel = new Label("Denomination:");
        denominationLabel.setStyle("-fx-text-fill: #aaaaaa;");
        denominationValueLabel = new Label();
        denominationValueLabel.setStyle("-fx-text-fill: white;");

        detailsGrid.add(relayLabel, 0, 0);
        detailsGrid.add(relayValueLabel, 1, 0);
        detailsGrid.add(pubkeyLabel, 0, 1);
        detailsGrid.add(pubkeyValueLabel, 1, 1);
        detailsGrid.add(denominationLabel, 0, 2);
        detailsGrid.add(denominationValueLabel, 1, 2);

        getChildren().add(detailsGrid);
    }

    public void updatePoolInfo(JoinstrPool pool) {
        if (pool != null) {
            relayValueLabel.setText(pool.getRelay());
            pubkeyValueLabel.setText(pool.getPubkey());
            denominationValueLabel.setText(pool.getDenomination());
        } else {
            clearPoolInfo();
        }
    }

    public void clearPoolInfo() {
        relayValueLabel.setText("");
        pubkeyValueLabel.setText("");
        denominationValueLabel.setText("");
    }
}