package com.sparrowwallet.sparrow.wallet;

import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.sparrow.EventManager;
import com.sparrowwallet.sparrow.control.DateStringConverter;
import com.sparrowwallet.sparrow.event.SettingsChangedEvent;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.util.StringConverter;

import java.net.URL;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import java.util.ResourceBundle;
import java.util.stream.Collectors;

public class AdvancedController implements Initializable {
    private static final List<Integer> DEFAULT_WATCH_LIST_ITEMS = List.of(-1, 100, 500, 1000, 5000, 10000);

    @FXML
    private DatePicker birthDate;

    @FXML
    private Spinner<Integer> gapLimit;

    @FXML
    private ComboBox<Integer> watchLast;

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

        gapLimit.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(Wallet.DEFAULT_LOOKAHEAD, 9999, wallet.getGapLimit()));
        gapLimit.valueProperty().addListener((observable, oldValue, newValue) -> {
            wallet.setGapLimit(newValue);
            if(!watchLast.getItems().equals(getWatchListItems(wallet))) {
                Integer value = watchLast.getValue();
                watchLast.setItems(getWatchListItems(wallet));
                watchLast.setValue(watchLast.getItems().contains(value) ? value : DEFAULT_WATCH_LIST_ITEMS.stream().filter(val -> val > wallet.getGapLimit()).findFirst().orElse(-1));
            }
            EventManager.get().post(new SettingsChangedEvent(wallet, SettingsChangedEvent.Type.GAP_LIMIT));
        });

        watchLast.setItems(getWatchListItems(wallet));
        watchLast.setConverter(new StringConverter<>() {
            @Override
            public String toString(Integer value) {
                return value == null ? "" : (value < 0 ? "All" : "Last " + value + " only");
            }

            @Override
            public Integer fromString(String string) {
                return null;
            }
        });
        watchLast.setValue(wallet.getWatchLast() == null || !watchLast.getItems().contains(wallet.getWatchLast()) ? -1 : wallet.getWatchLast());
        watchLast.valueProperty().addListener((observable, oldValue, newValue) -> {
            wallet.setWatchLast(newValue == null || newValue < 0 ? -1 : newValue);
            EventManager.get().post(new SettingsChangedEvent(wallet, SettingsChangedEvent.Type.WATCH_LAST));
        });
    }

    private ObservableList<Integer> getWatchListItems(Wallet wallet) {
        return FXCollections.observableList(DEFAULT_WATCH_LIST_ITEMS.stream().filter(val -> val < 0 || val > wallet.getGapLimit()).collect(Collectors.toList()));
    }
}
