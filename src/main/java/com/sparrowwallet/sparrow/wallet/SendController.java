package com.sparrowwallet.sparrow.wallet;

import com.google.common.eventbus.Subscribe;
import com.sparrowwallet.drongo.address.Address;
import com.sparrowwallet.drongo.address.InvalidAddressException;
import com.sparrowwallet.drongo.wallet.*;
import com.sparrowwallet.sparrow.AppController;
import com.sparrowwallet.sparrow.BitcoinUnit;
import com.sparrowwallet.sparrow.EventManager;
import com.sparrowwallet.sparrow.control.*;
import com.sparrowwallet.sparrow.event.FeeRatesUpdatedEvent;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
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
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.stream.Collectors;

public class SendController extends WalletFormController implements Initializable {
    public static final List<Integer> TARGET_BLOCKS_RANGE = List.of(1, 2, 3, 4, 5, 10, 25, 50);

    public static final double FALLBACK_FEE_RATE = 20000d / 1000;

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
    private TransactionDiagram transactionDiagram;

    @FXML
    private Button clear;

    @FXML
    private Button create;

    private final ObjectProperty<WalletTransaction> walletTransactionProperty = new SimpleObjectProperty<>(null);

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

        walletTransactionProperty.addListener((observable, oldValue, newValue) -> {
            transactionDiagram.update(newValue);
            create.setDisable(false);
        });
    }

    private void addValidation() {
        ValidationSupport validationSupport = new ValidationSupport();
        validationSupport.registerValidator(address, Validator.combine(
                (Control c, String newValue) -> ValidationResult.fromErrorIf( c, "Invalid Address", !newValue.isEmpty() && !isValidAddress())
        ));
        validationSupport.registerValidator(amount, Validator.combine(
                (Control c, String newValue) -> ValidationResult.fromErrorIf( c, "Insufficient Inputs", insufficientInputsProperty.get())
        ));

        validationSupport.setValidationDecorator(new StyleClassValidationDecoration());
    }

    private void updateTransaction() {
        try {
            Address recipientAddress = getAddress();
            Long recipientAmount = getAmount();
            if(recipientAmount != null) {
                Wallet wallet = getWalletForm().getWallet();
                WalletTransaction walletTransaction = wallet.createWalletTransaction(getUtxoSelectors(), recipientAddress, recipientAmount, getFeeRate());
                walletTransactionProperty.setValue(walletTransaction);
                insufficientInputsProperty.set(false);
                return;
            }
        } catch (InvalidAddressException e) {
            //ignore
        } catch (InsufficientFundsException e) {
            insufficientInputsProperty.set(true);
        }

        walletTransactionProperty.setValue(null);
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
        BitcoinUnit bitcoinUnit = feeAmountUnit.getSelectionModel().getSelectedItem();
        if(fee.getText() != null && !fee.getText().isEmpty()) {
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
        Map<Integer, Double> retrievedFeeRates = AppController.getTargetBlockFeeRates();
        if(retrievedFeeRates == null) {
            retrievedFeeRates = TARGET_BLOCKS_RANGE.stream().collect(Collectors.toMap(java.util.function.Function.identity(), v -> FALLBACK_FEE_RATE));
        }

        return  retrievedFeeRates;
    }

    private Double getFeeRate() {
        return getTargetBlocksFeeRates().get(getTargetBlocks());
    }

    private void setFeeRate(Double feeRateAmt) {
        feeRate.setText(String.format("%.2f", feeRateAmt) + " sats/vByte");
    }

    public void setMaxInput(ActionEvent event) {

    }

    public void clear(ActionEvent event) {
        address.setText("");
        label.setText("");
        amount.setText("");
        fee.setText("");
        walletTransactionProperty.setValue(null);
    }

    public void createTransaction(ActionEvent event) {

    }

    @Subscribe
    public void feeRatesUpdated(FeeRatesUpdatedEvent event) {
        feeRatesChart.update(event.getTargetBlockFeeRates());
        feeRatesChart.select(getTargetBlocks());
        setFeeRate(event.getTargetBlockFeeRates().get(getTargetBlocks()));
    }
}
