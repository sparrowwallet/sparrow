package com.sparrowwallet.sparrow.wallet;

import com.google.common.eventbus.Subscribe;
import com.sparrowwallet.drongo.address.Address;
import com.sparrowwallet.drongo.address.InvalidAddressException;
import com.sparrowwallet.drongo.protocol.Transaction;
import com.sparrowwallet.drongo.wallet.*;
import com.sparrowwallet.sparrow.AppController;
import com.sparrowwallet.sparrow.BitcoinUnit;
import com.sparrowwallet.sparrow.EventManager;
import com.sparrowwallet.sparrow.control.CopyableLabel;
import com.sparrowwallet.sparrow.control.CopyableTextField;
import com.sparrowwallet.sparrow.control.FeeRatesChart;
import com.sparrowwallet.sparrow.control.TextFieldValidator;
import com.sparrowwallet.sparrow.event.FeeRatesUpdatedEvent;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.util.StringConverter;
import org.controlsfx.validation.ValidationResult;
import org.controlsfx.validation.ValidationSupport;
import org.controlsfx.validation.Validator;
import org.controlsfx.validation.decoration.StyleClassValidationDecoration;

import java.net.URL;
import java.util.*;

public class SendController extends WalletFormController implements Initializable {
    public static final List<Integer> TARGET_BLOCKS_RANGE = List.of(1, 2, 3, 4, 5, 10, 25, 50);

    @FXML
    private CopyableTextField address;

    @FXML
    private TextField label;

    @FXML
    private TextField amount;

    @FXML
    private ComboBox<BitcoinUnit> amountUnit;

    @FXML
    private Slider targetBlocks;

    @FXML
    private CopyableLabel feeRate;

    @FXML
    private TextField fee;

    @FXML
    private ComboBox<BitcoinUnit> feeAmountUnit;

    @FXML
    private FeeRatesChart feeRatesChart;

    @FXML
    private Button clear;

    @FXML
    private Button select;

    @FXML
    private Button create;

    private ObservableList<BlockTransactionHashIndex> inputs;

    private final BooleanProperty insufficientInputsProperty = new SimpleBooleanProperty(false);

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        EventManager.get().register(this);
    }

    @Override
    public void initializeView() {
        addValidation();

        address.textProperty().addListener((observable, oldValue, newValue) -> {
            updateTransaction();
        });

        amount.setTextFormatter(new TextFieldValidator(TextFieldValidator.ValidationModus.MAX_FRACTION_DIGITS, 15).getFormatter());
        amountUnit.getSelectionModel().select(0);
        amount.textProperty().addListener((observable, oldValue, newValue) -> {
            updateTransaction();
        });
        insufficientInputsProperty.addListener((observable, oldValue, newValue) -> {
            String amt = amount.getText();
            amount.setText(amt + " ");
            amount.setText(amt);
        });

        targetBlocks.setMin(0);
        targetBlocks.setMax(TARGET_BLOCKS_RANGE.size() - 1);
        targetBlocks.setMajorTickUnit(1);
        targetBlocks.setMinorTickCount(0);
        targetBlocks.setLabelFormatter(new StringConverter<Double>() {
            @Override
            public String toString(Double object) {
                return Integer.toString(TARGET_BLOCKS_RANGE.get(object.intValue()));
            }

            @Override
            public Double fromString(String string) {
                return (double)TARGET_BLOCKS_RANGE.indexOf(Integer.valueOf(string));
            }
        });
        targetBlocks.valueProperty().addListener((observable, oldValue, newValue) -> {
            Map<Integer, Double> targetBlocksFeeRates = getTargetBlocksFeeRates();
            Integer target = getTargetBlocks();

            if(targetBlocksFeeRates != null) {
                setFeeRate(targetBlocksFeeRates.get(target));
                feeRatesChart.select(target);
            } else {
                feeRate.setText("Unknown");
            }

            Tooltip tooltip = new Tooltip("Target confirmation within " + target + " blocks");
            targetBlocks.setTooltip(tooltip);

            //TODO: Set fee based on tx size
        });

        feeAmountUnit.getSelectionModel().select(1);

        feeRatesChart.initialize();
        Map<Integer, Double> targetBlocksFeeRates = getTargetBlocksFeeRates();
        if(targetBlocksFeeRates != null) {
            feeRatesChart.update(targetBlocksFeeRates);
        } else {
            feeRate.setText("Unknown");
        }

        setTargetBlocks(5);

        fee.textProperty().addListener((observable, oldValue, newValue) -> {
            updateTransaction();
        });

        select.managedProperty().bind(select.visibleProperty());
        create.managedProperty().bind(create.visibleProperty());
        if(inputs == null || inputs.isEmpty()) {
            create.setVisible(false);
        } else {
            select.setVisible(false);
        }
    }

    private void addValidation() {
        ValidationSupport validationSupport = new ValidationSupport();
        validationSupport.registerValidator(address, Validator.combine(
                (Control c, String newValue) -> ValidationResult.fromErrorIf( c, "Invalid Address", !isValidAddress())
        ));
        validationSupport.registerValidator(amount, Validator.combine(
                (Control c, String newValue) -> ValidationResult.fromErrorIf( c, "Insufficient Inputs", insufficientInputsProperty.get())
        ));

        validationSupport.setValidationDecorator(new StyleClassValidationDecoration());
    }

    private void updateTransaction() {
        try {
            Address address = getAddress();
            Long amount = getAmount();
            if(amount != null) {
                Collection<BlockTransactionHashIndex> selectedInputs = selectInputs(amount);

                Transaction transaction = new Transaction();
            }
        } catch (InvalidAddressException e) {
            //ignore
        } catch (InvalidTransactionException.InsufficientInputsException e) {
            insufficientInputsProperty.set(true);
        }
    }

    private Collection<BlockTransactionHashIndex> selectInputs(Long targetValue) throws InvalidTransactionException.InsufficientInputsException {
        Set<BlockTransactionHashIndex> utxos = getWalletForm().getWallet().getWalletUtxos().keySet();

        for(UtxoSelector utxoSelector : getUtxoSelectors()) {
            Collection<BlockTransactionHashIndex> selectedInputs = utxoSelector.select(targetValue, utxos);
            long total = selectedInputs.stream().mapToLong(BlockTransactionHashIndex::getValue).sum();
            if(total > targetValue) {
                return selectedInputs;
            }
        }

        throw new InvalidTransactionException.InsufficientInputsException("Not enough inputs for output value " + targetValue);
    }

    private List<UtxoSelector> getUtxoSelectors() {
        UtxoSelector priorityUtxoSelector = new PriorityUtxoSelector(AppController.getCurrentBlockHeight());
        return List.of(priorityUtxoSelector);
    }

    private boolean isValidAddress() {
        try {
            getAddress();
        } catch (InvalidAddressException e) {
            return false;
        }

        return true;
    }

    private Address getAddress() throws InvalidAddressException {
        return Address.fromString(address.getText());
    }

    private Long getAmount() {
        BitcoinUnit bitcoinUnit = amountUnit.getSelectionModel().getSelectedItem();
        if(amount.getText() != null && !amount.getText().isEmpty()) {
            Double fieldValue = Double.parseDouble(amount.getText());
            return bitcoinUnit.getSatsValue(fieldValue);
        }

        return null;
    }

    private Long getFee() {
        BitcoinUnit bitcoinUnit = amountUnit.getSelectionModel().getSelectedItem();
        if(amount.getText() != null && !amount.getText().isEmpty()) {
            Double fieldValue = Double.parseDouble(amount.getText());
            return bitcoinUnit.getSatsValue(fieldValue);
        }

        return null;
    }

    private Integer getTargetBlocks() {
        int index = (int)targetBlocks.getValue();
        return TARGET_BLOCKS_RANGE.get(index);
    }

    private void setTargetBlocks(Integer target) {
        int index = TARGET_BLOCKS_RANGE.indexOf(target);
        targetBlocks.setValue(index);
        feeRatesChart.select(target);
    }

    private Map<Integer, Double> getTargetBlocksFeeRates() {
        return AppController.getTargetBlockFeeRates();
    }

    private void setFeeRate(Double feeRateAmt) {
        feeRate.setText(String.format("%.2f", feeRateAmt) + " sats/vByte");
    }

    public void setMaxInput(ActionEvent event) {

    }

    @Subscribe
    public void feeRatesUpdated(FeeRatesUpdatedEvent event) {
        feeRatesChart.update(event.getTargetBlockFeeRates());
        feeRatesChart.select(getTargetBlocks());
        setFeeRate(event.getTargetBlockFeeRates().get(getTargetBlocks()));
    }
}
