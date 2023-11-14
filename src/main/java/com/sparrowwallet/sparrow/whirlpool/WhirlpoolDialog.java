package com.sparrowwallet.sparrow.whirlpool;

import com.samourai.whirlpool.client.tx0.Tx0Preview;
import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.sparrow.AppServices;
import com.sparrowwallet.sparrow.wallet.UtxoEntry;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.event.ActionEvent;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.*;

import java.io.IOException;
import java.util.List;

public class WhirlpoolDialog extends Dialog<Tx0Preview> {
    public WhirlpoolDialog(String walletId, Wallet wallet, List<UtxoEntry> utxoEntries) {
        final DialogPane dialogPane = getDialogPane();
        AppServices.setStageIcon(dialogPane.getScene().getWindow());
        AppServices.onEscapePressed(dialogPane.getScene(), this::close);

        try {
            FXMLLoader whirlpoolLoader = new FXMLLoader(AppServices.class.getResource("whirlpool/whirlpool.fxml"));
            dialogPane.setContent(whirlpoolLoader.load());
            WhirlpoolController whirlpoolController = whirlpoolLoader.getController();
            whirlpoolController.initializeView(walletId, wallet, utxoEntries);

            dialogPane.setPrefWidth(600);
            dialogPane.setPrefHeight(570);
            dialogPane.setMinHeight(dialogPane.getPrefHeight());
            AppServices.moveToActiveWindowScreen(this);

            dialogPane.getStylesheets().add(AppServices.class.getResource("app.css").toExternalForm());
            dialogPane.getStylesheets().add(AppServices.class.getResource("whirlpool/whirlpool.css").toExternalForm());

            final ButtonType nextButtonType = new javafx.scene.control.ButtonType("Next", ButtonBar.ButtonData.OK_DONE);
            final ButtonType backButtonType = new javafx.scene.control.ButtonType("Back", ButtonBar.ButtonData.LEFT);
            final ButtonType cancelButtonType = new javafx.scene.control.ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
            final ButtonType previewButtonType = new javafx.scene.control.ButtonType("Preview Premix", ButtonBar.ButtonData.APPLY);
            dialogPane.getButtonTypes().addAll(nextButtonType, backButtonType, cancelButtonType, previewButtonType);

            Button nextButton = (Button)dialogPane.lookupButton(nextButtonType);
            Button backButton = (Button)dialogPane.lookupButton(backButtonType);
            Button previewButton = (Button)dialogPane.lookupButton(previewButtonType);
            previewButton.setDisable(true);
            whirlpoolController.getTx0PreviewProperty().addListener(new ChangeListener<Tx0Preview>() {
                @Override
                public void changed(ObservableValue<? extends Tx0Preview> observable, Tx0Preview oldValue, Tx0Preview newValue) {
                    previewButton.setDisable(newValue == null);
                }
            });

            nextButton.managedProperty().bind(nextButton.visibleProperty());
            backButton.managedProperty().bind(backButton.visibleProperty());
            previewButton.managedProperty().bind(previewButton.visibleProperty());

            if(wallet.getMasterMixConfig().getScode() == null) {
                backButton.setDisable(true);
            }
            previewButton.visibleProperty().bind(nextButton.visibleProperty().not());

            nextButton.addEventFilter(ActionEvent.ACTION, event -> {
                if(!whirlpoolController.next()) {
                    nextButton.setVisible(false);
                    previewButton.setDefaultButton(true);
                }
                backButton.setDisable(false);
                event.consume();
            });

            backButton.addEventFilter(ActionEvent.ACTION, event -> {
                nextButton.setVisible(true);
                if(!whirlpoolController.back()) {
                    backButton.setDisable(true);
                }
                event.consume();
            });

            setResultConverter(dialogButton -> dialogButton.getButtonData().equals(ButtonBar.ButtonData.APPLY) ? whirlpoolController.getTx0PreviewProperty().get() : null);
        } catch(IOException e) {
            throw new RuntimeException(e);
        }
    }
}
