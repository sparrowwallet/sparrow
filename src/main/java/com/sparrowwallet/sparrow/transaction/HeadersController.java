package com.sparrowwallet.sparrow.transaction;

import com.sparrowwallet.drongo.protocol.Transaction;
import com.sparrowwallet.sparrow.EventManager;
import com.sparrowwallet.sparrow.control.IdLabel;
import com.sparrowwallet.sparrow.control.CopyableLabel;
import com.sparrowwallet.sparrow.event.TransactionChangedEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import tornadofx.control.DateTimePicker;
import tornadofx.control.Field;
import tornadofx.control.Fieldset;
import com.google.common.eventbus.Subscribe;

import java.net.URL;
import java.time.*;
import java.util.ResourceBundle;

public class HeadersController extends TransactionFormController implements Initializable {
    public static final String LOCKTIME_DATE_FORMAT = "yyyy-MM-dd HH:mm:ss";

    private HeadersForm headersForm;

    @FXML
    private IdLabel id;

    @FXML
    private Spinner<Integer> version;

    @FXML
    private CopyableLabel segwit;

    @FXML
    private ToggleGroup locktimeToggleGroup;

    @FXML
    private ToggleButton locktimeNoneType;

    @FXML
    private ToggleButton locktimeBlockType;

    @FXML
    private ToggleButton locktimeDateType;

    @FXML
    private Fieldset locktimeFieldset;

    @FXML
    private Field locktimeNoneField;

    @FXML
    private Field locktimeBlockField;

    @FXML
    private Field locktimeDateField;

    @FXML
    private Spinner<Integer> locktimeNone;

    @FXML
    private Spinner<Integer> locktimeBlock;

    @FXML
    private DateTimePicker locktimeDate;

    @FXML
    private CopyableLabel size;

    @FXML
    private CopyableLabel virtualSize;

    @FXML
    private CopyableLabel fee;

    @FXML
    private CopyableLabel feeRate;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        EventManager.get().register(this);
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
            EventManager.get().post(new TransactionChangedEvent(tx));
        });

        String type = "Legacy";
        if(tx.isSegwit()) {
            type = "Segwit";
            if(tx.getSegwitVersion() == 2) {
                type = "Taproot";
            }
        }
        segwit.setText(type);

        locktimeToggleGroup.selectedToggleProperty().addListener((ov, old_toggle, new_toggle) -> {
            if(locktimeToggleGroup.getSelectedToggle() != null) {
                String selection = locktimeToggleGroup.getSelectedToggle().getUserData().toString();
                if(selection.equals("none")) {
                    locktimeFieldset.getChildren().remove(locktimeDateField);
                    locktimeFieldset.getChildren().remove(locktimeBlockField);
                    locktimeFieldset.getChildren().remove(locktimeNoneField);
                    locktimeFieldset.getChildren().add(locktimeNoneField);
                    tx.setLocktime(0);
                    EventManager.get().post(new TransactionChangedEvent(tx));
                } else if(selection.equals("block")) {
                    locktimeFieldset.getChildren().remove(locktimeDateField);
                    locktimeFieldset.getChildren().remove(locktimeBlockField);
                    locktimeFieldset.getChildren().remove(locktimeNoneField);
                    locktimeFieldset.getChildren().add(locktimeBlockField);
                    Integer block = locktimeBlock.getValue();
                    if(block != null) {
                        tx.setLocktime(block);
                        EventManager.get().post(new TransactionChangedEvent(tx));
                    }
                } else {
                    locktimeFieldset.getChildren().remove(locktimeBlockField);
                    locktimeFieldset.getChildren().remove(locktimeDateField);
                    locktimeFieldset.getChildren().remove(locktimeNoneField);
                    locktimeFieldset.getChildren().add(locktimeDateField);
                    LocalDateTime date = locktimeDate.getDateTimeValue();
                    if(date != null) {
                        locktimeDate.setDateTimeValue(date);
                        tx.setLocktime(date.toEpochSecond(OffsetDateTime.now(ZoneId.systemDefault()).getOffset()));
                        EventManager.get().post(new TransactionChangedEvent(tx));
                    }
                }
            }
        });

        locktimeNone.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(0, (int)Transaction.MAX_BLOCK_LOCKTIME-1, 0));
        if(tx.getLocktime() < Transaction.MAX_BLOCK_LOCKTIME) {
            locktimeBlock.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(0, (int)Transaction.MAX_BLOCK_LOCKTIME-1, (int)tx.getLocktime()));
            if(tx.getLocktime() == 0) {
                locktimeToggleGroup.selectToggle(locktimeNoneType);
            } else {
                locktimeToggleGroup.selectToggle(locktimeBlockType);
            }
            LocalDateTime date = Instant.now().atZone(ZoneId.systemDefault()).toLocalDateTime();
            locktimeDate.setDateTimeValue(date);
        } else {
            locktimeBlock.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(0, (int)Transaction.MAX_BLOCK_LOCKTIME-1));
            LocalDateTime date = Instant.ofEpochSecond(tx.getLocktime()).atZone(ZoneId.systemDefault()).toLocalDateTime();
            locktimeDate.setDateTimeValue(date);
            locktimeToggleGroup.selectToggle(locktimeDateType);
        }

        locktimeBlock.valueProperty().addListener((obs, oldValue, newValue) -> {
            tx.setLocktime(newValue);
            EventManager.get().post(new TransactionChangedEvent(tx));
        });

        locktimeDate.setFormat(LOCKTIME_DATE_FORMAT);
        locktimeDate.dateTimeValueProperty().addListener((obs, oldValue, newValue) -> {
            tx.setLocktime(newValue.toEpochSecond(OffsetDateTime.now(ZoneId.systemDefault()).getOffset()));
            EventManager.get().post(new TransactionChangedEvent(tx));
        });

        size.setText(tx.getSize() + " B");
        virtualSize.setText(tx.getVirtualSize() + " vB");

        Long feeAmt = null;
        if(headersForm.getPsbt() != null) {
            feeAmt = headersForm.getPsbt().getFee();
        }

        if(feeAmt != null) {
            fee.setText(feeAmt + " sats");
            double feeRateAmt = feeAmt.doubleValue() / tx.getVirtualSize();
            feeRate.setText(String.format("%.2f", feeRateAmt) + " sats/vByte");
        } else {
            fee.setText("Unknown");
        }
    }

    private void updateTxId() {
        id.setText(headersForm.getTransaction().calculateTxId(false).toString());
    }

    @Subscribe
    public void transactionChanged(TransactionChangedEvent event) {
        if(headersForm.getTransaction().equals(event.getTransaction())) {
            updateTxId();
            boolean locktimeEnabled = headersForm.getTransaction().isLocktimeSequenceEnabled();
            locktimeNoneType.setDisable(!locktimeEnabled);
            locktimeBlockType.setDisable(!locktimeEnabled);
            locktimeBlock.setDisable(!locktimeEnabled);
            locktimeDateType.setDisable(!locktimeEnabled);
            locktimeDate.setDisable(!locktimeEnabled);
        }
    }
}
