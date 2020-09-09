package com.sparrowwallet.sparrow.wallet;

import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.sparrow.EventManager;
import com.sparrowwallet.sparrow.event.SettingsChangedEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;

import java.net.URL;
import java.util.ResourceBundle;

public class AdvancedController implements Initializable {
    @FXML
    private Spinner<Integer> gapLimit;

    @Override
    public void initialize(URL location, ResourceBundle resources) {

    }

    public void initializeView(Wallet wallet) {
        gapLimit.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(Wallet.DEFAULT_LOOKAHEAD, 10000, wallet.getGapLimit()));
        gapLimit.valueProperty().addListener((observable, oldValue, newValue) -> {
            wallet.setGapLimit(newValue);
            EventManager.get().post(new SettingsChangedEvent(wallet, SettingsChangedEvent.Type.GAP_LIMIT));
        });
    }
}
