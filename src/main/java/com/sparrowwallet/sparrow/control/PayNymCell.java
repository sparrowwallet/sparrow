package com.sparrowwallet.sparrow.control;

import com.sparrowwallet.sparrow.soroban.PayNym;
import com.sparrowwallet.sparrow.soroban.PayNymController;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;

public class PayNymCell extends ListCell<PayNym> {
    private final PayNymController payNymController;

    public PayNymCell(PayNymController payNymController) {
        super();
        setAlignment(Pos.CENTER_LEFT);
        setContentDisplay(ContentDisplay.LEFT);
        getStyleClass().add("paynym-cell");
        setPrefHeight(50);
        this.payNymController = payNymController;
    }

    @Override
    protected void updateItem(PayNym payNym, boolean empty) {
        super.updateItem(payNym, empty);

        if(empty || payNym == null) {
            setText(null);
            setGraphic(null);
        } else {
            BorderPane pane = new BorderPane();
            pane.setPadding(new Insets(5, 5,5, 5));

            PayNymAvatar payNymAvatar = new PayNymAvatar();
            payNymAvatar.setPrefWidth(30);
            payNymAvatar.setPrefHeight(30);
            payNymAvatar.setPaymentCode(payNym.paymentCode());

            HBox labelBox = new HBox();
            labelBox.setAlignment(Pos.CENTER);
            Label label = new Label(payNym.nymName(), payNymAvatar);
            label.setGraphicTextGap(10);
            labelBox.getChildren().add(label);
            pane.setLeft(labelBox);

            if(getListView().getUserData() == Boolean.TRUE) {
                HBox hBox = new HBox();
                hBox.setAlignment(Pos.CENTER);
                Button button = new Button("Add Contact");
                hBox.getChildren().add(button);
                pane.setRight(hBox);
                button.setOnAction(event -> {
                    button.setDisable(true);
                    payNymController.followPayNym(payNym.paymentCode());
                });
            }

            setText(null);
            setGraphic(pane);
        }
    }
}
