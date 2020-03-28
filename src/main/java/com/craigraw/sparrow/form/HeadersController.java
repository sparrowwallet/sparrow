package com.craigraw.sparrow.form;

import com.craigraw.drongo.protocol.Transaction;
import com.craigraw.sparrow.EventManager;
import com.craigraw.sparrow.TransactionListener;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import tornadofx.control.DateTimePicker;

import java.net.URL;
import java.time.*;
import java.util.ResourceBundle;

public class HeadersController implements Initializable, TransactionListener {
    private HeadersForm headersForm;

    private static final long MAX_BLOCK_LOCKTIME = 500000000L;

    @FXML
    private GridPane layout;

    @FXML
    private TextField idField;

    @FXML
    private Spinner<Integer> versionField;

    @FXML
    private Label segwitField;

    @FXML
    private Label segwitVersionLabel;

    @FXML
    private Spinner<Integer> segwitVersionField;

    @FXML
    private ToggleGroup locktimeTypeField;

    @FXML
    private ToggleButton locktimeBlockTypeField;

    @FXML
    private ToggleButton locktimeDateTypeField;

    @FXML
    private Spinner<Integer> locktimeBlockField;

    @FXML
    private DateTimePicker locktimeDateField;

    @FXML
    private Label feeField;

    @FXML
    private Label sizeField;

    @FXML
    private Label virtualSizeField;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        EventManager.get().subscribe(this);
    }

    void setModel(HeadersForm form) {
        this.headersForm = form;
        Transaction tx = headersForm.getTransaction();

        updateTxId();

        versionField.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 2, (int)tx.getVersion()));
        versionField.valueProperty().addListener((obs, oldValue, newValue) -> {
            tx.setVersion(newValue);
            EventManager.get().notify(tx);
        });

        segwitField.setText(tx.isSegwit() ? "Segwit" : "Legacy" );
        if(tx.isSegwit()) {
            segwitVersionField.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 2, tx.getSegwitVersion()));
            segwitVersionField.valueProperty().addListener((obs, oldValue, newValue) -> {
                tx.setSegwitVersion(newValue);
                EventManager.get().notify(tx);
            });
        } else {
            layout.getChildren().removeAll(segwitVersionLabel, segwitVersionField);
        }

        locktimeTypeField.selectedToggleProperty().addListener((ov, old_toggle, new_toggle) -> {
            if(locktimeTypeField.getSelectedToggle() != null) {
                String selection = locktimeTypeField.getSelectedToggle().getUserData().toString();
                if(selection.equals("block")) {
                    locktimeBlockField.setVisible(true);
                    locktimeDateField.setVisible(false);
                    Integer block = locktimeBlockField.getValue();
                    if(block != null) {
                        tx.setLockTime(block);
                        EventManager.get().notify(tx);
                    }
                } else {
                    locktimeBlockField.setVisible(false);
                    locktimeDateField.setVisible(true);
                    LocalDateTime date = locktimeDateField.getDateTimeValue();
                    if(date != null) {
                        tx.setLockTime(date.toEpochSecond(OffsetDateTime.now(ZoneId.systemDefault()).getOffset()));
                        EventManager.get().notify(tx);
                    }
                }
            }
        });

        if(tx.getLockTime() < MAX_BLOCK_LOCKTIME) {
            locktimeBlockField.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(0, (int)MAX_BLOCK_LOCKTIME-1, (int)tx.getLockTime()));
            locktimeBlockField.setVisible(true);
            locktimeDateField.setVisible(false);
            locktimeTypeField.selectToggle(locktimeBlockTypeField);
        } else {
            LocalDateTime date = Instant.ofEpochSecond(tx.getLockTime()).atZone(ZoneId.systemDefault()).toLocalDateTime();
            locktimeDateField.setDateTimeValue(date);
            locktimeBlockField.setVisible(false);
            locktimeDateField.setVisible(true);
            locktimeTypeField.selectToggle(locktimeDateTypeField);
        }

        locktimeBlockField.valueProperty().addListener((obs, oldValue, newValue) -> {
            tx.setLockTime(newValue);
            EventManager.get().notify(tx);
        });

        locktimeDateField.setFormat("yyyy-MM-dd HH:mm:ss");
        locktimeDateField.dateTimeValueProperty().addListener((obs, oldValue, newValue) -> {
            tx.setLockTime(newValue.toEpochSecond(OffsetDateTime.now(ZoneId.systemDefault()).getOffset()));
            EventManager.get().notify(tx);
        });

        if(form.getPsbt() != null) {
            feeField.setText(form.getPsbt().getFee().toString() + " sats");
        } else {
            feeField.setText("Unknown");
        }

        sizeField.setText(tx.getSize() + " B");

        virtualSizeField.setText(tx.getVirtualSize() + " vB");
    }

    private void updateTxId() {
        idField.setText(headersForm.getTransaction().calculateTxId(false).toString());
    }

    @Override
    public void updated(Transaction transaction) {
        updateTxId();
    }
}
