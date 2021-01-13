package com.sparrowwallet.sparrow.wallet;

import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.sparrow.EventManager;
import com.sparrowwallet.sparrow.control.DateStringConverter;
import com.sparrowwallet.sparrow.event.SettingsChangedEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;

import java.net.URL;
import java.time.ZoneId;
import java.util.Date;
import java.util.ResourceBundle;

public class AdvancedController implements Initializable {
    @FXML
    private DatePicker birthDate;

    @FXML
    private Spinner<Integer> gapLimit;

    @Override
    public void initialize(URL location, ResourceBundle resources) {

    }

    public void initializeView(Wallet wallet) {
        birthDate.setConverter(new DateStringConverter());
        if(wallet.getBirthDate() != null) {
            birthDate.setValue(wallet.getBirthDate().toInstant().atZone(ZoneId.systemDefault()).toLocalDate());
        }
        birthDate.valueProperty().addListener((observable, oldValue, newValue) -> {
            if(newValue != null) {
                wallet.setBirthDate(Date.from(newValue.atStartOfDay().atZone(ZoneId.systemDefault()).toInstant()));
                EventManager.get().post(new SettingsChangedEvent(wallet, SettingsChangedEvent.Type.BIRTH_DATE));
            }
        });

        gapLimit.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(Wallet.DEFAULT_LOOKAHEAD, 10000, wallet.getGapLimit()));
        gapLimit.valueProperty().addListener((observable, oldValue, newValue) -> {
            wallet.setGapLimit(newValue);
            EventManager.get().post(new SettingsChangedEvent(wallet, SettingsChangedEvent.Type.GAP_LIMIT));
        });
    }
}
