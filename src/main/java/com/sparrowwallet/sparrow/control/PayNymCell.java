package com.sparrowwallet.sparrow.control;

import com.sparrowwallet.sparrow.EventManager;
import com.sparrowwallet.sparrow.event.FollowPayNymEvent;
import com.sparrowwallet.sparrow.soroban.PayNym;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;

public class PayNymCell extends ListCell<PayNym> {
    private final String walletId;

    public PayNymCell(String walletId) {
        super();
        setAlignment(Pos.CENTER_LEFT);
        setContentDisplay(ContentDisplay.LEFT);
        getStyleClass().add("paynym-cell");
        setPrefHeight(50);
        this.walletId = walletId;
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
                Button button = new Button("Follow");
                hBox.getChildren().add(button);
                pane.setRight(hBox);
                button.setOnAction(event -> {
                    EventManager.get().post(new FollowPayNymEvent(walletId, payNym.paymentCode()));
                });
            }

            setText(null);
            setGraphic(pane);
        }
    }
}
