package com.sparrowwallet.sparrow.wallet;

import com.google.common.eventbus.Subscribe;
import com.sparrowwallet.drongo.BitcoinUnit;
import com.sparrowwallet.drongo.KeyPurpose;
import com.sparrowwallet.drongo.address.Address;
import com.sparrowwallet.drongo.address.InvalidAddressException;
import com.sparrowwallet.drongo.address.P2PKHAddress;
import com.sparrowwallet.drongo.bip47.InvalidPaymentCodeException;
import com.sparrowwallet.drongo.bip47.PaymentCode;
import com.sparrowwallet.drongo.crypto.ECKey;
import com.sparrowwallet.drongo.protocol.ScriptType;
import com.sparrowwallet.drongo.protocol.Transaction;
import com.sparrowwallet.drongo.protocol.TransactionOutput;
import com.sparrowwallet.drongo.uri.BitcoinURI;
import com.sparrowwallet.drongo.wallet.*;
import com.sparrowwallet.sparrow.UnitFormat;
import com.sparrowwallet.sparrow.AppServices;
import com.sparrowwallet.sparrow.CurrencyRate;
import com.sparrowwallet.sparrow.EventManager;
import com.sparrowwallet.sparrow.control.*;
import com.sparrowwallet.sparrow.event.*;
import com.sparrowwallet.sparrow.glyphfont.FontAwesome5;
import com.sparrowwallet.sparrow.io.Config;
import com.sparrowwallet.sparrow.io.Storage;
import com.sparrowwallet.sparrow.net.ExchangeSource;
import com.sparrowwallet.sparrow.paynym.PayNym;
import com.sparrowwallet.sparrow.paynym.PayNymAddress;
import com.sparrowwallet.sparrow.paynym.PayNymDialog;
import com.sparrowwallet.sparrow.soroban.*;
import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.Node;
import javafx.scene.control.*;
import org.controlsfx.glyphfont.Glyph;
import org.controlsfx.validation.ValidationResult;
import org.controlsfx.validation.ValidationSupport;
import org.controlsfx.validation.Validator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URL;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.sparrowwallet.sparrow.AppServices.showErrorDialog;

public class PaymentController extends WalletFormController implements Initializable {
    private static final Logger log = LoggerFactory.getLogger(PaymentController.class);

    public static final long MINIMUM_P2PKH_OUTPUT_SATS = 546L;

    private SendController sendController;

    private ValidationSupport validationSupport;

    @FXML
    private ComboBox<Wallet> openWallets;

    @FXML
    private ComboBoxTextField address;

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
    private Label dustStatus;

    @FXML
    private ToggleButton maxButton;

    @FXML
    private Button scanQrButton;

    @FXML
    private Button addPaymentButton;

    private final BooleanProperty emptyAmountProperty = new SimpleBooleanProperty(true);

    private final BooleanProperty dustAmountProperty = new SimpleBooleanProperty();

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
                dustAmountProperty.set(recipientValueSats < getRecipientDustThreshold());
                emptyAmountProperty.set(false);
            } else {
                fiatAmount.setText("");
                dustAmountProperty.set(false);
                emptyAmountProperty.set(true);
            }

            updateMixOnlyStatus();

            sendController.updateTransaction();
        }
    };

    private final ObjectProperty<PayNym> payNymProperty = new SimpleObjectProperty<>();

    private static final Wallet payNymWallet = new Wallet() {
        @Override
        public String getFullDisplayName() {
            return "PayNym or Payment code...";
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
        updateOpenWallets();
        openWallets.prefWidthProperty().bind(address.widthProperty());
        openWallets.valueProperty().addListener((observable, oldValue, newValue) -> {
            if(newValue == payNymWallet) {
                boolean selectLinkedOnly = sendController.getPaymentTabs().getTabs().size() > 1 || !SorobanServices.canWalletMix(sendController.getWalletForm().getWallet());
                PayNymDialog payNymDialog = new PayNymDialog(sendController.getWalletForm().getWalletId(), PayNymDialog.Operation.SEND, selectLinkedOnly);
                Optional<PayNym> optPayNym = payNymDialog.showAndWait();
                optPayNym.ifPresent(this::setPayNym);
            } else if(newValue != null) {
                WalletNode freshNode = newValue.getFreshNode(KeyPurpose.RECEIVE);
                Address freshAddress = freshNode.getAddress();
                address.setText(freshAddress.toString());
                label.requestFocus();
            }
        });
        openWallets.setCellFactory(c -> new ListCell<>() {
            @Override
            protected void updateItem(Wallet wallet, boolean empty) {
                super.updateItem(wallet, empty);
                if(empty || wallet == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    setText(wallet.getFullDisplayName() + (wallet == sendController.getWalletForm().getWallet() ? " (Consolidation)" : ""));
                    setGraphic(getOpenWalletIcon(wallet));
                }
            }
        });

        payNymProperty.addListener((observable, oldValue, payNym) -> {
            updateMixOnlyStatus(payNym);
            revalidateAmount();
        });

        address.textProperty().addListener((observable, oldValue, newValue) -> {
            address.leftProperty().set(null);

            if(payNymProperty.get() != null && !newValue.equals(payNymProperty.get().nymName())) {
                payNymProperty.set(null);
            }

            try {
                BitcoinURI bitcoinURI = new BitcoinURI(newValue);
                Platform.runLater(() -> updateFromURI(bitcoinURI));
                return;
            } catch(Exception e) {
                //ignore, not a URI
            }

            if(sendController.getWalletForm().getWallet().hasPaymentCode()) {
                try {
                    PaymentCode paymentCode = new PaymentCode(newValue);
                    Wallet recipientBip47Wallet = sendController.getWalletForm().getWallet().getChildWallet(paymentCode, sendController.getWalletForm().getWallet().getScriptType());
                    if(recipientBip47Wallet == null && sendController.getWalletForm().getWallet().getScriptType() != ScriptType.P2PKH) {
                        recipientBip47Wallet = sendController.getWalletForm().getWallet().getChildWallet(paymentCode, ScriptType.P2PKH);
                    }

                    if(recipientBip47Wallet != null) {
                        PayNym payNym = PayNym.fromWallet(recipientBip47Wallet);
                        Platform.runLater(() -> setPayNym(payNym));
                    } else if(!paymentCode.equals(sendController.getWalletForm().getWallet().getPaymentCode())) {
                        ButtonType previewType = new ButtonType("Preview Transaction", ButtonBar.ButtonData.YES);
                        Optional<ButtonType> optButton = AppServices.showAlertDialog("Send notification transaction?", "This payment code is not yet linked with a notification transaction. Send a notification transaction?", Alert.AlertType.CONFIRMATION, ButtonType.CANCEL, previewType);
                        if(optButton.isPresent() && optButton.get() == previewType) {
                            Payment payment = new Payment(paymentCode.getNotificationAddress(), "Link " + paymentCode.toAbbreviatedString(), MINIMUM_P2PKH_OUTPUT_SATS, false);
                            Platform.runLater(() -> EventManager.get().post(new SpendUtxoEvent(sendController.getWalletForm().getWallet(), List.of(payment), List.of(new byte[80]), paymentCode)));
                        } else {
                            Platform.runLater(() -> address.setText(""));
                        }
                    }
                } catch(Exception e) {
                    //ignore, not a payment code
                }
            }

            revalidateAmount();
            maxButton.setDisable(!isMaxButtonEnabled());
            sendController.updateTransaction();

            if(validationSupport != null) {
                validationSupport.setErrorDecorationEnabled(true);
            }
        });

        label.textProperty().addListener((observable, oldValue, newValue) -> {
            maxButton.setDisable(!isMaxButtonEnabled());
            sendController.getCreateButton().setDisable(sendController.getWalletTransaction() == null || newValue == null || newValue.isEmpty() || sendController.isInsufficientFeeRate());
            sendController.updateTransaction();
        });

        amount.setTextFormatter(new CoinTextFormatter(Config.get().getUnitFormat()));
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

        maxButton.setDisable(!isMaxButtonEnabled());
        sendController.utxoSelectorProperty().addListener((observable, oldValue, newValue) -> {
            maxButton.setDisable(!isMaxButtonEnabled());
        });
        sendController.getPaymentTabs().getTabs().addListener((ListChangeListener<Tab>) c -> {
            maxButton.setDisable(!isMaxButtonEnabled());
        });
        sendController.utxoLabelSelectionProperty().addListener((observable, oldValue, newValue) -> {
            maxButton.setText("Max" + newValue);
        });
        amountStatus.managedProperty().bind(amountStatus.visibleProperty());
        amountStatus.visibleProperty().bind(sendController.insufficientInputsProperty().and(dustAmountProperty.not()).and(emptyAmountProperty.not()));
        dustStatus.managedProperty().bind(dustStatus.visibleProperty());
        dustStatus.visibleProperty().bind(dustAmountProperty);

        Optional<Tab> firstTab = sendController.getPaymentTabs().getTabs().stream().findFirst();
        if(firstTab.isPresent()) {
            PaymentController controller = (PaymentController)firstTab.get().getUserData();
            String firstLabel = controller.label.getText();
            label.setText(firstLabel);
        }

        addValidation(validationSupport);
    }

    public void setPayNym(PayNym payNym) {
        PayNym existingPayNym = payNymProperty.get();
        payNymProperty.set(payNym);
        address.setText(payNym.nymName());
        address.leftProperty().set(getPayNymGlyph());
        label.requestFocus();
        if(existingPayNym != null && payNym.nymName().equals(existingPayNym.nymName()) && payNym.isCollaborativeSend() != existingPayNym.isCollaborativeSend()) {
            sendController.updateTransaction();
        }
    }

    public void updateMixOnlyStatus() {
        updateMixOnlyStatus(payNymProperty.get());
    }

    public void updateMixOnlyStatus(PayNym payNym) {
        boolean mixOnly = false;
        try {
            mixOnly = payNym != null && getRecipientAddress() instanceof PayNymAddress;
        } catch(InvalidAddressException e) {
            log.error("Error creating payment code from PayNym", e);
        }

        addPaymentButton.setDisable(mixOnly);
        if(mixOnly) {
            sendController.setPayNymMixOnlyPayment();
        }
    }

    private void updateOpenWallets() {
        updateOpenWallets(AppServices.get().getOpenWallets().keySet());
    }

    private void updateOpenWallets(Collection<Wallet> wallets) {
        List<Wallet> openWalletList = wallets.stream().filter(wallet -> wallet.isValid()
                && (wallet == sendController.getWalletForm().getWallet() || !wallet.isWhirlpoolChildWallet())
                && !wallet.isBip47()).collect(Collectors.toList());

        if(sendController.getWalletForm().getWallet().hasPaymentCode()) {
            openWalletList.add(payNymWallet);
        }

        openWallets.setItems(FXCollections.observableList(openWalletList));
    }

    private Node getOpenWalletIcon(Wallet wallet) {
        if(wallet == payNymWallet) {
            return getPayNymGlyph();
        }

        Wallet masterWallet = wallet.isMasterWallet() ? wallet : wallet.getMasterWallet();
        Storage storage = AppServices.get().getOpenWallets().get(masterWallet);
        if(storage != null) {
            return new WalletIcon(storage, masterWallet);
        }

        return null;
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
                (Control c, String newValue) -> ValidationResult.fromErrorIf( c, "Insufficient Inputs", getRecipientValueSats() != null && sendController.isInsufficientInputs()),
                (Control c, String newValue) -> ValidationResult.fromErrorIf( c, "Insufficient Value", getRecipientValueSats() != null && getRecipientValueSats() < getRecipientDustThreshold())
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

    private boolean isMaxButtonEnabled() {
        return isValidAddressAndLabel() || (sendController.utxoSelectorProperty().get() instanceof PresetUtxoSelector && sendController.getPaymentTabs().getTabs().size() == 1);
    }

    private Address getRecipientAddress() throws InvalidAddressException {
        PayNym payNym = payNymProperty.get();
        if(payNym == null) {
            return Address.fromString(address.getText());
        }

        if(!payNym.isCollaborativeSend()) {
            try {
                Wallet recipientBip47Wallet = getWalletForPayNym(payNym);
                if(recipientBip47Wallet != null) {
                    WalletNode sendNode = recipientBip47Wallet.getFreshNode(KeyPurpose.SEND);
                    ECKey pubKey = sendNode.getPubKey();
                    Address address = recipientBip47Wallet.getScriptType().getAddress(pubKey);
                    if(sendController.getPaymentTabs().getTabs().size() > 1 || (getRecipientValueSats() != null && getRecipientValueSats() > getRecipientDustThreshold(address)) || maxButton.isSelected()) {
                        return address;
                    }
                }
            } catch(InvalidPaymentCodeException e) {
                log.error("Error creating payment code from PayNym", e);
            }
        }

        return new PayNymAddress(payNym);
    }

    private Wallet getWalletForPayNym(PayNym payNym) throws InvalidPaymentCodeException {
        Wallet masterWallet = sendController.getWalletForm().getMasterWallet();
        return masterWallet.getChildWallet(new PaymentCode(payNym.paymentCode().toString()), payNym.segwit() ? ScriptType.P2WPKH : ScriptType.P2PKH);
    }

    private Long getRecipientValueSats() {
        return getRecipientValueSats(amountUnit.getSelectionModel().getSelectedItem());
    }

    private Long getRecipientValueSats(BitcoinUnit bitcoinUnit) {
        return getRecipientValueSats(Config.get().getUnitFormat(), bitcoinUnit);
    }

    private Long getRecipientValueSats(UnitFormat unitFormat, BitcoinUnit bitcoinUnit) {
        if(amount.getText() != null && !amount.getText().isEmpty()) {
            UnitFormat format = unitFormat == null ? UnitFormat.DOT : unitFormat;
            double fieldValue = Double.parseDouble(amount.getText().replaceAll(Pattern.quote(format.getGroupingSeparator()), "").replaceAll(",", "."));
            return bitcoinUnit.getSatsValue(fieldValue);
        }

        return null;
    }

    private void setRecipientValueSats(long recipientValue) {
        amount.textProperty().removeListener(amountListener);
        UnitFormat unitFormat = Config.get().getUnitFormat() == null ? UnitFormat.DOT : Config.get().getUnitFormat();
        DecimalFormat df = new DecimalFormat("#.#", unitFormat.getDecimalFormatSymbols());
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

        if(address instanceof PayNymAddress && SorobanServices.canWalletMix(sendController.getWalletForm().getWallet())) {
            return 0;
        }

        return getRecipientDustThreshold(address);
    }

    private long getRecipientDustThreshold(Address address) {
        TransactionOutput txOutput = new TransactionOutput(new Transaction(), 1L, address.getOutputScript());
        return address.getScriptType().getDustThreshold(txOutput, Transaction.DUST_RELAY_TX_FEE);
    }

    private void setFiatAmount(CurrencyRate currencyRate, Long amount) {
        if(amount != null && currencyRate != null && currencyRate.isAvailable()) {
            fiatAmount.set(currencyRate, amount);
        }
    }

    public void revalidateAmount() {
        revalidate(amount, amountListener);
        Long recipientValueSats = getRecipientValueSats();
        dustAmountProperty.set(recipientValueSats != null && recipientValueSats < getRecipientDustThreshold());
        emptyAmountProperty.set(recipientValueSats == null);
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
            Address recipientAddress = getRecipientAddress();
            Long value = sendAll ? Long.valueOf(getRecipientDustThreshold() + 1) : getRecipientValueSats();

            if(!label.getText().isEmpty() && value != null && value >= getRecipientDustThreshold()) {
                Payment payment = new Payment(recipientAddress, label.getText(), value, sendAll);
                if(address.getUserData() != null) {
                    payment.setType((Payment.Type)address.getUserData());
                }

                return payment;
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
                address.setUserData(payment.getType());
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
        try {
            AppServices.clearPayjoinURI(getRecipientAddress());
        } catch(InvalidAddressException e) {
            //ignore
        }

        address.setText("");
        label.setText("");

        amount.textProperty().removeListener(amountListener);
        amount.setText("");
        amount.textProperty().addListener(amountListener);

        fiatAmount.setText("");
        setSendMax(false);

        dustAmountProperty.set(false);
        payNymProperty.set(null);
    }

    public void setMaxInput(ActionEvent event) {
        UtxoSelector utxoSelector = sendController.getUtxoSelector();
        if(utxoSelector == null) {
            MaxUtxoSelector maxUtxoSelector = new MaxUtxoSelector();
            sendController.utxoSelectorProperty().set(maxUtxoSelector);
        } else if(utxoSelector instanceof PresetUtxoSelector && !isValidAddressAndLabel() && sendController.getPaymentTabs().getTabs().size() == 1) {
            PresetUtxoSelector presetUtxoSelector = (PresetUtxoSelector)utxoSelector;
            Payment payment = new Payment(null, null, presetUtxoSelector.getPresetUtxos().stream().mapToLong(BlockTransactionHashIndex::getValue).sum(), true);
            setPayment(payment);
            return;
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
            } else if(result.payload != null) {
                address.setText(result.payload);
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

    public void setInputFieldsDisabled(boolean disable) {
        address.setDisable(disable);
        label.setDisable(disable);
        amount.setDisable(disable);
        amountUnit.setDisable(disable);
        scanQrButton.setDisable(disable);
        addPaymentButton.setDisable(disable);
        maxButton.setDisable(disable);
    }

    public static Glyph getPayNymGlyph() {
        Glyph payNymGlyph = new Glyph(FontAwesome5.FONT_NAME, FontAwesome5.Glyph.ROBOT);
        payNymGlyph.getStyleClass().add("paynym-icon");
        payNymGlyph.setFontSize(12);
        return payNymGlyph;
    }

    @Subscribe
    public void bitcoinUnitChanged(BitcoinUnitChangedEvent event) {
        BitcoinUnit unit = sendController.getBitcoinUnit(event.getBitcoinUnit());
        amountUnit.getSelectionModel().select(BitcoinUnit.BTC.equals(unit) ? 0 : 1);
    }

    @Subscribe
    public void unitFormatChanged(UnitFormatChangedEvent event) {
        if(amount.getTextFormatter() instanceof CoinTextFormatter coinTextFormatter && coinTextFormatter.getUnitFormat() != event.getUnitFormat()) {
            Long value = getRecipientValueSats(coinTextFormatter.getUnitFormat(), event.getBitcoinUnit());
            amount.setTextFormatter(new CoinTextFormatter(event.getUnitFormat()));

            if(value != null) {
                setRecipientValueSats(value);
            }
        }
        fiatAmount.refresh(event.getUnitFormat());
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

    @Subscribe
    public void openWallets(OpenWalletsEvent event) {
        updateOpenWallets(event.getWallets());
    }
}
