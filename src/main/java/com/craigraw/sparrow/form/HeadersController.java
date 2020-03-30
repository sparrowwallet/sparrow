package com.craigraw.sparrow.form;

import com.craigraw.drongo.protocol.Transaction;
import com.craigraw.sparrow.EventManager;
import com.craigraw.sparrow.TransactionListener;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import tornadofx.control.DateTimePicker;
import tornadofx.control.Field;
import tornadofx.control.Fieldset;

import java.net.URL;
import java.time.*;
import java.util.ResourceBundle;

public class HeadersController implements Initializable, TransactionListener {
    private HeadersForm headersForm;

    private static final long MAX_BLOCK_LOCKTIME = 500000000L;

    @FXML
    private TextField id;

    @FXML
    private Spinner<Integer> version;

    @FXML
    private TextField segwit;

    @FXML
    private ToggleGroup locktimeType;

    @FXML
    private ToggleButton locktimeBlockType;

    @FXML
    private ToggleButton locktimeDateType;

    @FXML
    private Fieldset locktimeFieldset;

    @FXML
    private Field locktimeBlockField;

    @FXML
    private Field locktimeDateField;

    @FXML
    private Spinner<Integer> locktimeBlock;

    @FXML
    private DateTimePicker locktimeDate;

    @FXML
    private TextField fee;

    @FXML
    private TextField size;

    @FXML
    private TextField virtualSize;

    @FXML
    private TextField feeRateField;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        EventManager.get().subscribe(this);
    }

    void setModel(HeadersForm form) {
        this.headersForm = form;
        initializeView();
    }

    private void initializeView() {
        Transaction tx = headersForm.getTransaction();

        updateTxId();

        version.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 2, (int)tx.getVersion()));
        version.valueProperty().addListener((obs, oldValue, newValue) -> {
            tx.setVersion(newValue);
            EventManager.get().notify(tx);
        });

        String type = "Legacy";
        if(tx.isSegwit()) {
            type = "Segwit";
            if(tx.getSegwitVersion() == 2) {
                type = "Taproot";
            }
        }
        segwit.setText(type);

        locktimeType.selectedToggleProperty().addListener((ov, old_toggle, new_toggle) -> {
            if(locktimeType.getSelectedToggle() != null) {
                String selection = locktimeType.getSelectedToggle().getUserData().toString();
                if(selection.equals("block")) {
                    locktimeFieldset.getChildren().remove(locktimeDateField);
                    locktimeFieldset.getChildren().remove(locktimeBlockField);
                    locktimeFieldset.getChildren().add(locktimeBlockField);
                    Integer block = locktimeBlock.getValue();
                    if(block != null) {
                        tx.setLockTime(block);
                        EventManager.get().notify(tx);
                    }
                } else {
                    locktimeFieldset.getChildren().remove(locktimeBlockField);
                    locktimeFieldset.getChildren().remove(locktimeDateField);
                    locktimeFieldset.getChildren().add(locktimeDateField);
                    LocalDateTime date = locktimeDate.getDateTimeValue();
                    if(date != null) {
                        tx.setLockTime(date.toEpochSecond(OffsetDateTime.now(ZoneId.systemDefault()).getOffset()));
                        EventManager.get().notify(tx);
                    }
                }
            }
        });

        if(tx.getLockTime() < MAX_BLOCK_LOCKTIME) {
            locktimeBlock.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(0, (int)MAX_BLOCK_LOCKTIME-1, (int)tx.getLockTime()));
            locktimeType.selectToggle(locktimeBlockType);
        } else {
            locktimeBlock.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(0, (int)MAX_BLOCK_LOCKTIME-1));
            LocalDateTime date = Instant.ofEpochSecond(tx.getLockTime()).atZone(ZoneId.systemDefault()).toLocalDateTime();
            locktimeDate.setDateTimeValue(date);
            locktimeType.selectToggle(locktimeDateType);
        }

        locktimeBlock.valueProperty().addListener((obs, oldValue, newValue) -> {
            tx.setLockTime(newValue);
            EventManager.get().notify(tx);
        });

        locktimeDate.setFormat("yyyy-MM-dd HH:mm:ss");
        locktimeDate.dateTimeValueProperty().addListener((obs, oldValue, newValue) -> {
            tx.setLockTime(newValue.toEpochSecond(OffsetDateTime.now(ZoneId.systemDefault()).getOffset()));
            EventManager.get().notify(tx);
        });

        size.setText(tx.getSize() + " B");
        virtualSize.setText(tx.getVirtualSize() + " vB");

        Long feeAmt = null;
        if(headersForm.getPsbt() != null) {
            feeAmt = headersForm.getPsbt().getFee();
        }

        if(feeAmt != null) {
            fee.setText(feeAmt + " sats");
            double feeRate = feeAmt.doubleValue() / tx.getVirtualSize();
            feeRateField.setText(String.format("%.2f", feeRate) + " sats/vByte");
        } else {
            fee.setText("Unknown");
        }
    }

    private void updateTxId() {
        id.setText(headersForm.getTransaction().calculateTxId(false).toString());
    }

    @Override
    public void updated(Transaction transaction) {
        updateTxId();
    }
}
