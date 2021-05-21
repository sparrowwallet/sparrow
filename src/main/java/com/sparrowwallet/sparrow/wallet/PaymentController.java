package com.sparrowwallet.sparrow.wallet;

import com.google.common.eventbus.Subscribe;
import com.sparrowwallet.drongo.BitcoinUnit;
import com.sparrowwallet.drongo.address.Address;
import com.sparrowwallet.drongo.address.InvalidAddressException;
import com.sparrowwallet.drongo.address.P2PKHAddress;
import com.sparrowwallet.drongo.protocol.Transaction;
import com.sparrowwallet.drongo.protocol.TransactionOutput;
import com.sparrowwallet.drongo.uri.BitcoinURI;
import com.sparrowwallet.drongo.wallet.MaxUtxoSelector;
import com.sparrowwallet.drongo.wallet.Payment;
import com.sparrowwallet.drongo.wallet.UtxoSelector;
import com.sparrowwallet.sparrow.AppServices;
import com.sparrowwallet.sparrow.CurrencyRate;
import com.sparrowwallet.sparrow.EventManager;
import com.sparrowwallet.sparrow.control.*;
import com.sparrowwallet.sparrow.event.BitcoinUnitChangedEvent;
import com.sparrowwallet.sparrow.event.ExchangeRatesUpdatedEvent;
import com.sparrowwallet.sparrow.event.FiatCurrencySelectedEvent;
import com.sparrowwallet.sparrow.io.Config;
import com.sparrowwallet.sparrow.net.ExchangeSource;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import org.controlsfx.validation.ValidationResult;
import org.controlsfx.validation.ValidationSupport;
import org.controlsfx.validation.Validator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URL;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.*;

import static com.sparrowwallet.sparrow.AppServices.showErrorDialog;

public class PaymentController extends WalletFormController implements Initializable {
    private static final Logger log = LoggerFactory.getLogger(PaymentController.class);

    private SendController sendController;

    private ValidationSupport validationSupport;

    @FXML
    private CopyableTextField address;

    @FXML
    private TextField label;

    @FXML
    private TextField amount;

    @FXML
    private ComboBox<BitcoinUnit> amountUnit;

    @FXML
    private FiatLabel fiatAmount;

    @FXML
    private Label amountStatus;

    @FXML
    private ToggleButton maxButton;

    @FXML
    private Button addPaymentButton;

    private final ChangeListener<String> amountListener = new ChangeListener<>() {
        @Override
        public void changed(ObservableValue<? extends String> observable, String oldValue, String newValue) {
            if(sendController.getUtxoSelector() instanceof MaxUtxoSelector) {
                sendController.utxoSelectorProperty().setValue(null);
            }

            for(Tab tab : sendController.getPaymentTabs().getTabs()) {
                PaymentController controller = (PaymentController) tab.getUserData();
                controller.setSendMax(false);
            }

            Long recipientValueSats = getRecipientValueSats();
            if(recipientValueSats != null) {
                setFiatAmount(AppServices.getFiatCurrencyExchangeRate(), recipientValueSats);
            } else {
                fiatAmount.setText("");
            }

            sendController.updateTransaction();
        }
    };

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        EventManager.get().register(this);
    }

    public void setSendController(SendController sendController) {
        this.sendController = sendController;
        this.validationSupport = sendController.getValidationSupport();
    }

    @Override
    public void initializeView() {
        address.textProperty().addListener((observable, oldValue, newValue) -> {
            try {
                BitcoinURI bitcoinURI = new BitcoinURI(newValue);
                Platform.runLater(() -> updateFromURI(bitcoinURI));
                return;
            } catch(Exception e) {
                //ignore, not a URI
            }

            revalidate(amount, amountListener);
            maxButton.setDisable(!isValidAddressAndLabel());
            sendController.updateTransaction();

            if(validationSupport != null) {
                validationSupport.setErrorDecorationEnabled(true);
            }
        });

        label.textProperty().addListener((observable, oldValue, newValue) -> {
            maxButton.setDisable(!isValidAddressAndLabel());
            sendController.getCreateButton().setDisable(sendController.getWalletTransaction() == null || newValue == null || newValue.isEmpty() || sendController.isInsufficientFeeRate());
            sendController.updateTransaction();
        });

        amount.setTextFormatter(new CoinTextFormatter());
        amount.textProperty().addListener(amountListener);

        amountUnit.getSelectionModel().select(BitcoinUnit.BTC.equals(sendController.getBitcoinUnit(Config.get().getBitcoinUnit())) ? 0 : 1);
        amountUnit.valueProperty().addListener((observable, oldValue, newValue) -> {
            Long value = getRecipientValueSats(oldValue);
            if(value != null) {
                DecimalFormat df = new DecimalFormat("#.#", DecimalFormatSymbols.getInstance(Locale.ENGLISH));
                df.setMaximumFractionDigits(8);
                amount.setText(df.format(newValue.getValue(value)));
            }
        });

        maxButton.setDisable(!isValidAddressAndLabel());
        sendController.utxoLabelSelectionProperty().addListener((observable, oldValue, newValue) -> {
            maxButton.setText("Max" + newValue);
        });
        amountStatus.managedProperty().bind(amountStatus.visibleProperty());
        amountStatus.setVisible(sendController.isInsufficientInputs());
        sendController.insufficientInputsProperty().addListener((observable, oldValue, newValue) -> {
            amountStatus.setVisible(newValue);
        });

        Optional<Tab> firstTab = sendController.getPaymentTabs().getTabs().stream().findFirst();
        if(firstTab.isPresent()) {
            PaymentController controller = (PaymentController)firstTab.get().getUserData();
            String firstLabel = controller.label.getText();
            label.setText(firstLabel);
        }

        addValidation(validationSupport);
    }

    private void addValidation(ValidationSupport validationSupport) {
        this.validationSupport = validationSupport;

        validationSupport.registerValidator(address, Validator.combine(
                (Control c, String newValue) -> ValidationResult.fromErrorIf( c, "Invalid Address", !newValue.isEmpty() && !isValidRecipientAddress())
        ));
        validationSupport.registerValidator(label, Validator.combine(
                Validator.createEmptyValidator("Label is required")
        ));
        validationSupport.registerValidator(amount, Validator.combine(
                (Control c, String newValue) -> ValidationResult.fromErrorIf( c, "Insufficient Inputs", sendController.isInsufficientInputs()),
                (Control c, String newValue) -> ValidationResult.fromErrorIf( c, "Insufficient Value", getRecipientValueSats() != null && getRecipientValueSats() <= getRecipientDustThreshold())
        ));
    }

    private boolean isValidRecipientAddress() {
        try {
            getRecipientAddress();
            return true;
        } catch (InvalidAddressException e) {
            return false;
        }
    }

    private boolean isValidAddressAndLabel() {
        return isValidRecipientAddress() && !label.getText().isEmpty();
    }

    private Address getRecipientAddress() throws InvalidAddressException {
        return Address.fromString(address.getText());
    }

    private Long getRecipientValueSats() {
        return getRecipientValueSats(amountUnit.getSelectionModel().getSelectedItem());
    }

    private Long getRecipientValueSats(BitcoinUnit bitcoinUnit) {
        if(amount.getText() != null && !amount.getText().isEmpty()) {
            double fieldValue = Double.parseDouble(amount.getText().replaceAll(",", ""));
            return bitcoinUnit.getSatsValue(fieldValue);
        }

        return null;
    }

    private void setRecipientValueSats(long recipientValue) {
        amount.textProperty().removeListener(amountListener);
        DecimalFormat df = new DecimalFormat("#.#", DecimalFormatSymbols.getInstance(Locale.ENGLISH));
        df.setMaximumFractionDigits(8);
        amount.setText(df.format(amountUnit.getValue().getValue(recipientValue)));
        amount.textProperty().addListener(amountListener);
    }

    private long getRecipientDustThreshold() {
        Address address;
        try {
            address = getRecipientAddress();
        } catch(InvalidAddressException e) {
            address = new P2PKHAddress(new byte[20]);
        }

        TransactionOutput txOutput = new TransactionOutput(new Transaction(), 1L, address.getOutputScript());
        return address.getScriptType().getDustThreshold(txOutput, Transaction.DUST_RELAY_TX_FEE);
    }

    private void setFiatAmount(CurrencyRate currencyRate, Long amount) {
        if(amount != null && currencyRate != null && currencyRate.isAvailable()) {
            fiatAmount.set(currencyRate, amount);
        }
    }

    public void revalidate() {
        revalidate(amount, amountListener);
    }

    private void revalidate(TextField field, ChangeListener<String> listener) {
        field.textProperty().removeListener(listener);
        String amt = field.getText();
        int caret = field.getCaretPosition();
        field.setText(amt + "0");
        field.setText(amt);
        field.positionCaret(caret);
        field.textProperty().addListener(listener);
    }

    public boolean isValidPayment() {
        try {
            getPayment();
            return true;
        } catch(IllegalStateException e) {
            return false;
        }
    }

    public Payment getPayment() {
        return getPayment(isSendMax());
    }

    public Payment getPayment(boolean sendAll) {
        try {
            Address address = getRecipientAddress();
            Long value = sendAll ? Long.valueOf(getRecipientDustThreshold() + 1) : getRecipientValueSats();

            if(!label.getText().isEmpty() && value != null && value > getRecipientDustThreshold()) {
                return new Payment(address, label.getText(), value, sendAll);
            }
        } catch(InvalidAddressException e) {
            //ignore
        }

        throw new IllegalStateException("Invalid payment specified");
    }

    public void setPayment(Payment payment) {
        if(getRecipientValueSats() == null || payment.getAmount() != getRecipientValueSats()) {
            if(payment.getAddress() != null) {
                address.setText(payment.getAddress().toString());
            }
            if(payment.getLabel() != null && !label.getText().equals(payment.getLabel())) {
                label.setText(payment.getLabel());
            }
            if(payment.getAmount() >= 0) {
                setRecipientValueSats(payment.getAmount());
            }
            setFiatAmount(AppServices.getFiatCurrencyExchangeRate(), payment.getAmount());
        }
    }

    public void clear() {
        address.setText("");
        label.setText("");

        amount.textProperty().removeListener(amountListener);
        amount.setText("");
        amount.textProperty().addListener(amountListener);

        fiatAmount.setText("");
        setSendMax(false);

        amountStatus.setVisible(false);
    }

    public void setMaxInput(ActionEvent event) {
        UtxoSelector utxoSelector = sendController.getUtxoSelector();
        if(utxoSelector == null) {
            MaxUtxoSelector maxUtxoSelector = new MaxUtxoSelector();
            sendController.utxoSelectorProperty().set(maxUtxoSelector);
        }

        try {
            List<Payment> payments = new ArrayList<>();
            for(Tab tab : sendController.getPaymentTabs().getTabs()) {
                PaymentController controller = (PaymentController)tab.getUserData();
                if(controller != this) {
                    controller.setSendMax(false);
                    payments.add(controller.getPayment());
                } else {
                    setSendMax(true);
                    payments.add(getPayment());
                }
            }
            sendController.updateTransaction(payments);
        } catch(IllegalStateException e) {
            //ignore, validation errors
        }
    }

    public void scanQrAddress(ActionEvent event) {
        QRScanDialog qrScanDialog = new QRScanDialog();
        Optional<QRScanDialog.Result> optionalResult = qrScanDialog.showAndWait();
        if(optionalResult.isPresent()) {
            QRScanDialog.Result result = optionalResult.get();
            if(result.uri != null) {
                updateFromURI(result.uri);
            } else if(result.exception != null) {
                log.error("Error scanning QR", result.exception);
                showErrorDialog("Error scanning QR", result.exception.getMessage());
            }
        }
    }

    private void updateFromURI(BitcoinURI bitcoinURI) {
        if(bitcoinURI.getAddress() != null) {
            address.setText(bitcoinURI.getAddress().toString());
        }
        if(bitcoinURI.getLabel() != null) {
            label.setText(bitcoinURI.getLabel());
        }
        if(bitcoinURI.getAmount() != null) {
            setRecipientValueSats(bitcoinURI.getAmount());
            setFiatAmount(AppServices.getFiatCurrencyExchangeRate(), bitcoinURI.getAmount());
        }
        if(bitcoinURI.getPayjoinUrl() != null) {
            AppServices.addPayjoinURI(bitcoinURI);
        }
        sendController.updateTransaction();
    }

    public void addPayment(ActionEvent event) {
        sendController.addPaymentTab();
    }

    public Button getAddPaymentButton() {
        return addPaymentButton;
    }

    public boolean isSendMax() {
        return maxButton.isSelected();
    }

    public void setSendMax(boolean sendMax) {
        maxButton.setSelected(sendMax);
    }

    @Subscribe
    public void bitcoinUnitChanged(BitcoinUnitChangedEvent event) {
        BitcoinUnit unit = sendController.getBitcoinUnit(event.getBitcoinUnit());
        amountUnit.getSelectionModel().select(BitcoinUnit.BTC.equals(unit) ? 0 : 1);
    }

    @Subscribe
    public void fiatCurrencySelected(FiatCurrencySelectedEvent event) {
        if(event.getExchangeSource() == ExchangeSource.NONE) {
            fiatAmount.setCurrency(null);
            fiatAmount.setBtcRate(0.0);
        }
    }

    @Subscribe
    public void exchangeRatesUpdated(ExchangeRatesUpdatedEvent event) {
        setFiatAmount(event.getCurrencyRate(), getRecipientValueSats());
    }
}
