package com.sparrowwallet.sparrow.wallet;

import com.google.common.eventbus.Subscribe;
import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.sparrow.AppServices;
import com.sparrowwallet.sparrow.EventManager;
import com.sparrowwallet.sparrow.event.WalletMixConfigChangedEvent;
import com.sparrowwallet.sparrow.whirlpool.Whirlpool;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.*;
import org.controlsfx.tools.Borders;

import java.io.IOException;
import java.util.NoSuchElementException;

public class MixToDialog extends Dialog<Boolean> {
    private final Wallet wallet;
    private final Button applyButton;

    public MixToDialog(Wallet wallet) {
        final DialogPane dialogPane = getDialogPane();
        AppServices.setStageIcon(dialogPane.getScene().getWindow());
        this.wallet = wallet;

        try {
            FXMLLoader mixToLoader = new FXMLLoader(AppServices.class.getResource("wallet/mixto.fxml"));
            dialogPane.setContent(Borders.wrap(mixToLoader.load()).emptyBorder().buildAll());
            MixToController mixToController = mixToLoader.getController();
            mixToController.initializeView(wallet);

            Whirlpool whirlpool = AppServices.getWhirlpoolServices().getWhirlpool(wallet);
            final ButtonType closeButtonType = new javafx.scene.control.ButtonType("Close", ButtonBar.ButtonData.CANCEL_CLOSE);
            final ButtonType applyButtonType = new javafx.scene.control.ButtonType(whirlpool.isStarted() ? "Restart Whirlpool" : "Apply", ButtonBar.ButtonData.APPLY);
            dialogPane.getButtonTypes().addAll(closeButtonType, applyButtonType);

            applyButton = (Button)dialogPane.lookupButton(applyButtonType);
            applyButton.setDisable(true);
            applyButton.setDefaultButton(true);

            try {
                AppServices.getWhirlpoolServices().getWhirlpoolMixToWalletId(wallet.getMasterMixConfig());
            } catch(NoSuchElementException e) {
                applyButton.setDisable(false);
            }

            dialogPane.setPrefWidth(400);
            dialogPane.setPrefHeight(300);
            AppServices.moveToActiveWindowScreen(this);

            setResultConverter(dialogButton -> dialogButton == applyButtonType);

            setOnCloseRequest(event -> {
                EventManager.get().unregister(this);
            });
            EventManager.get().register(this);
        }
        catch(IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Subscribe
    public void walletMixConfigChanged(WalletMixConfigChangedEvent event) {
        if(event.getWallet() == (wallet.isMasterWallet() ? wallet : wallet.getMasterWallet())) {
            applyButton.setDisable(false);
        }
    }
}
