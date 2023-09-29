package com.sparrowwallet.sparrow.control;

import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.sparrow.EventManager;
import com.sparrowwallet.sparrow.event.WalletLabelChangedEvent;
import com.sparrowwallet.sparrow.glyphfont.FontAwesome5;
import com.sparrowwallet.sparrow.paynym.PayNym;
import com.sparrowwallet.sparrow.paynym.PayNymController;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import org.controlsfx.glyphfont.Glyph;

import java.util.Optional;

public class PayNymCell extends ListCell<PayNym> {
    private final PayNymController payNymController;
    private final boolean contact;

    public PayNymCell(PayNymController payNymController, boolean contact) {
        super();
        setAlignment(Pos.CENTER_LEFT);
        setContentDisplay(ContentDisplay.LEFT);
        getStyleClass().add("paynym-cell");
        setPrefHeight(50);
        this.payNymController = payNymController;
        this.contact = contact;
    }

    @Override
    protected void updateItem(PayNym payNym, boolean empty) {
        super.updateItem(payNym, empty);

        getStyleClass().remove("unlinked");

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

            if(getListView().getUserData() == Boolean.TRUE || (!contact && payNymController != null && payNymController.isFollowing(payNym) == Boolean.FALSE)) {
                HBox hBox = new HBox();
                hBox.setAlignment(Pos.CENTER);
                Button button = new Button("Add Contact");
                hBox.getChildren().add(button);
                pane.setRight(hBox);
                button.setOnAction(event -> {
                    button.setDisable(true);
                    payNymController.followPayNym(payNym.paymentCode());
                });
            } else if(contact && payNymController != null) {
                HBox hBox = new HBox();
                hBox.setAlignment(Pos.CENTER);
                pane.setRight(hBox);

                if(payNymController.isLinked(payNym)) {
                    Label linkedLabel = new Label("Linked", getLinkGlyph());
                    linkedLabel.setTooltip(new Tooltip("You can send non-collaboratively to this contact."));
                    hBox.getChildren().add(linkedLabel);
                } else {
                    Button linkButton = new Button("Link Contact", getLinkGlyph());
                    linkButton.setTooltip(new Tooltip("Create a transaction that will enable you to send non-collaboratively to this contact."));
                    hBox.getChildren().add(linkButton);
                    linkButton.setOnAction(event -> {
                        linkButton.setDisable(true);
                        payNymController.linkPayNym(payNym);
                    });

                    if(payNymController.isSelectLinkedOnly()) {
                        getStyleClass().add("unlinked");
                    }
                }
            }

            setText(null);
            setGraphic(pane);

            if(contact && payNymController != null && payNymController.isLinked(payNym)) {
                setContextMenu(new PayNymCellContextMenu(payNym));
            } else {
                setContextMenu(null);
            }
        }
    }

    public static Glyph getLinkGlyph() {
        Glyph failGlyph = new Glyph(FontAwesome5.FONT_NAME, FontAwesome5.Glyph.LINK);
        failGlyph.setFontSize(12);
        return failGlyph;
    }

    private class PayNymCellContextMenu extends ContextMenu {
        public PayNymCellContextMenu(PayNym payNym) {
            MenuItem rename = new MenuItem("Rename Contact...");
            rename.setOnAction(event -> {
                WalletLabelDialog walletLabelDialog = new WalletLabelDialog(payNym.nymName(), "Contact");
                walletLabelDialog.initOwner(PayNymCell.this.getScene().getWindow());
                Optional<String> optLabel = walletLabelDialog.showAndWait();
                if(optLabel.isPresent()) {
                    int index = getListView().getItems().indexOf(payNym);
                    for(Wallet childWallet : payNymController.getMasterWallet().getChildWallets()) {
                        if(childWallet.isBip47()
                                && childWallet.getKeystores().get(0).getExternalPaymentCode().equals(payNym.paymentCode())
                                && (childWallet.getLabel() == null || childWallet.getLabel().startsWith(payNym.nymName()))) {
                            childWallet.setLabel(optLabel.get() + " " + childWallet.getScriptType().getName());
                            EventManager.get().post(new WalletLabelChangedEvent(childWallet));
                        }
                    }

                    payNymController.updateFollowing();
                    getListView().getSelectionModel().select(index);
                }
            });

            getItems().add(rename);
        }
    }
}
