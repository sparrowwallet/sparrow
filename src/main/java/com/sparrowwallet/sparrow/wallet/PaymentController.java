package com.sparrowwallet.sparrow.wallet;

import com.google.common.base.Throwables;
import com.google.common.eventbus.Subscribe;
import com.sparrowwallet.drongo.BitcoinUnit;
import com.sparrowwallet.drongo.KeyPurpose;
import com.sparrowwallet.drongo.address.Address;
import com.sparrowwallet.drongo.address.InvalidAddressException;
import com.sparrowwallet.drongo.address.P2PKHAddress;
import com.sparrowwallet.drongo.bip47.InvalidPaymentCodeException;
import com.sparrowwallet.drongo.bip47.PaymentCode;
import com.sparrowwallet.drongo.crypto.ECKey;
import com.sparrowwallet.drongo.dns.DnsPaymentCache;
import com.sparrowwallet.drongo.protocol.ScriptType;
import com.sparrowwallet.drongo.protocol.Transaction;
import com.sparrowwallet.drongo.protocol.TransactionOutput;
import com.sparrowwallet.drongo.silentpayments.SilentPayment;
import com.sparrowwallet.drongo.silentpayments.SilentPaymentAddress;
import com.sparrowwallet.drongo.uri.BitcoinURI;
import com.sparrowwallet.drongo.wallet.*;
import com.sparrowwallet.sparrow.*;
import com.sparrowwallet.sparrow.control.*;
import com.sparrowwallet.sparrow.event.*;
import com.sparrowwallet.sparrow.glyphfont.FontAwesome5;
import com.sparrowwallet.sparrow.io.CardApi;
import com.sparrowwallet.sparrow.io.Config;
import com.sparrowwallet.sparrow.io.Storage;
import com.sparrowwallet.sparrow.net.ExchangeSource;
import com.sparrowwallet.drongo.dns.DnsPayment;
import com.sparrowwallet.drongo.dns.DnsPaymentResolver;
import com.sparrowwallet.sparrow.paynym.PayNym;
import com.sparrowwallet.sparrow.paynym.PayNymDialog;
import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.concurrent.Service;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.HBox;
import org.controlsfx.glyphfont.Glyph;
import org.controlsfx.validation.ValidationResult;
import org.controlsfx.validation.ValidationSupport;
import org.controlsfx.validation.Validator;
import org.girod.javafx.svgimage.SVGImage;
import org.girod.javafx.svgimage.SVGLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.util.*;
import java.util.concurrent.TimeoutException;
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

            sendController.updateTransaction();
        }
    };

    private final ObjectProperty<WalletNode> consolidationNodeProperty = new SimpleObjectProperty<>();

    private final ObjectProperty<PayNym> payNymProperty = new SimpleObjectProperty<>();

    private final ObjectProperty<SilentPaymentAddress> silentPaymentAddressProperty = new SimpleObjectProperty<>();

    private final ObjectProperty<DnsPayment> dnsPaymentProperty = new SimpleObjectProperty<>();

    private static final Wallet payNymWallet = new Wallet() {
        @Override
        public String getFullDisplayName() {
            return "PayNym or Payment code...";
        }
    };

    private static final Wallet nfcCardWallet = new Wallet() {
        @Override
        public String getFullDisplayName() {
            return "NFC Card...";
        }
    };

    private final ChangeListener<String> addressListener = new ChangeListener<>() {
        @Override
        public void changed(ObservableValue<? extends String> observable, String oldValue, String newValue) {
            address.leftProperty().set(null);

            if(consolidationNodeProperty.get() != null && !newValue.equals(consolidationNodeProperty.get().getAddress().toString())) {
                consolidationNodeProperty.set(null);
            }

            if(payNymProperty.get() != null && !newValue.equals(payNymProperty.get().nymName())) {
                payNymProperty.set(null);
            }

            if(dnsPaymentProperty.get() != null && !newValue.equals(dnsPaymentProperty.get().hrn())) {
                dnsPaymentProperty.set(null);
            }

            if(silentPaymentAddressProperty.get() != null && !newValue.equals(silentPaymentAddressProperty.get().getAddress())) {
                silentPaymentAddressProperty.set(null);
            }

            try {
                BitcoinURI bitcoinURI = new BitcoinURI(newValue);
                Platform.runLater(() -> updateFromURI(bitcoinURI));
                return;
            } catch(Exception e) {
                //ignore, not a URI
            }

            Optional<String> optDnsPaymentHrn = DnsPayment.getHrn(newValue);
            if(optDnsPaymentHrn.isPresent()) {
                String dnsPaymentHrn = optDnsPaymentHrn.get();
                DnsPayment cachedDnsPayment = DnsPaymentCache.getDnsPayment(dnsPaymentHrn);
                if(cachedDnsPayment != null) {
                    setDnsPayment(cachedDnsPayment);
                    return;
                }

                if(Config.get().hasServer() && !AppServices.isConnected() && !AppServices.isConnecting()) {
                    if(Config.get().getConnectToResolve() == null || Config.get().getConnectToResolve() == Boolean.FALSE) {
                        Platform.runLater(() -> {
                            ConfirmationAlert confirmationAlert = new ConfirmationAlert("Connect to resolve?", "You are currently offline. Connect to resolve the address?", ButtonType.NO, ButtonType.YES);
                            Optional<ButtonType> optType = confirmationAlert.showAndWait();
                            if(confirmationAlert.isDontAskAgain() && optType.isPresent()) {
                                Config.get().setConnectToResolve(optType.get() == ButtonType.YES);
                            }
                            if(optType.isPresent() && optType.get() == ButtonType.YES) {
                                EventManager.get().post(new RequestConnectEvent());
                            }
                        });
                    } else {
                        Platform.runLater(() -> EventManager.get().post(new RequestConnectEvent()));
                    }
                    return;
                }

                DnsPaymentService dnsPaymentService = new DnsPaymentService(dnsPaymentHrn);
                dnsPaymentService.setOnSucceeded(_ -> dnsPaymentService.getValue().ifPresent(dnsPayment -> setDnsPayment(dnsPayment)));
                dnsPaymentService.setOnFailed(failEvent -> {
                    if(failEvent.getSource().getException() != null && !(failEvent.getSource().getException().getCause() instanceof TimeoutException)) {
                        AppServices.showErrorDialog("Validation failed for " + dnsPaymentHrn, Throwables.getRootCause(failEvent.getSource().getException()).getMessage());
                    }
                });
                dnsPaymentService.start();
                return;
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

            try {
                SilentPaymentAddress silentPaymentAddress = SilentPaymentAddress.from(newValue);
                setSilentPaymentAddress(silentPaymentAddress);
            } catch(Exception e) {
                //ignore, not a silent payment address
            }

            try {
                Address toAddress = Address.fromString(newValue);
                WalletNode walletNode = sendController.getWalletNode(toAddress);
                if(walletNode != null) {
                    consolidationNodeProperty.set(walletNode);
                }
                label.requestFocus();
            } catch(Exception e) {
                //ignore, not an address
            }

            revalidateAmount();
            maxButton.setDisable(!isMaxButtonEnabled());
            sendController.updateTransaction();

            if(validationSupport != null) {
                validationSupport.setErrorDecorationEnabled(true);
            }
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
                PayNymDialog payNymDialog = new PayNymDialog(sendController.getWalletForm().getWalletId(), PayNymDialog.Operation.SEND);
                payNymDialog.initOwner(scanQrButton.getScene().getWindow());
                Optional<PayNym> optPayNym = payNymDialog.showAndWait();
                optPayNym.ifPresent(this::setPayNym);
            } else if(newValue == nfcCardWallet) {
                DeviceGetAddressDialog deviceGetAddressDialog = new DeviceGetAddressDialog(Collections.emptyList());
                deviceGetAddressDialog.initOwner(scanQrButton.getScene().getWindow());
                Optional<Address> optAddress = deviceGetAddressDialog.showAndWait();
                if(optAddress.isPresent()) {
                    address.setText(optAddress.get().toString());
                    label.requestFocus();
                }
            } else if(newValue != null) {
                List<Address> existingAddresses = getOtherAddresses();
                WalletNode freshNode = newValue.getFreshNode(KeyPurpose.RECEIVE);
                Address freshAddress = freshNode.getAddress();
                while(existingAddresses.contains(freshAddress) || (freshNode.getLabel() != null && !freshNode.getLabel().isEmpty())) {
                    freshNode = newValue.getFreshNode(KeyPurpose.RECEIVE, freshNode);
                    freshAddress = freshNode.getAddress();
                }
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
        openWallets.setOnShowing(event -> {
            if(!openWallets.getItems().contains(nfcCardWallet) && CardApi.isReaderAvailable()) {
                openWallets.getItems().add(nfcCardWallet);
            } else if(openWallets.getItems().contains(nfcCardWallet) && !CardApi.isReaderAvailable()) {
                openWallets.getItems().remove(nfcCardWallet);
            }
        });

        payNymProperty.addListener((observable, oldValue, payNym) -> {
            revalidateAmount();
        });

        silentPaymentAddressProperty.addListener((observable, oldValue, silentPaymentAddress) -> {
            revalidateAmount();
        });

        dnsPaymentProperty.addListener((observable, oldValue, dnsPayment) -> {
            if(dnsPayment != null) {
                MenuItem copyMenuItem = new MenuItem("Copy URI");
                copyMenuItem.setOnAction(e -> {
                    ClipboardContent content = new ClipboardContent();
                    content.putString(dnsPayment.bitcoinURI().toURIString());
                    Clipboard.getSystemClipboard().setContent(content);
                });
                address.setContextMenu(address.getCustomContextMenu(List.of(copyMenuItem)));
            } else {
                address.setContextMenu(address.getCustomContextMenu(Collections.emptyList()));
            }

            revalidateAmount();
            maxButton.setDisable(!isMaxButtonEnabled());
            sendController.updateTransaction();
        });

        address.setTextFormatter(new TextFormatter<>(change -> {
            String controlNewText = change.getControlNewText();
            if(!controlNewText.equals(controlNewText.trim())) {
                String text = change.getText();
                String newText = text.trim();
                int caretPos = change.getCaretPosition() - text.length() + newText.length();
                change.setText(newText);
                change.selectRange(caretPos, caretPos);
            }
            return change;
        }));

        address.textProperty().addListener(addressListener);
        address.setContextMenu(address.getCustomContextMenu(Collections.emptyList()));

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
                UnitFormat unitFormat = Config.get().getUnitFormat() == null ? UnitFormat.DOT : Config.get().getUnitFormat();
                DecimalFormat df = new DecimalFormat("#.#", unitFormat.getDecimalFormatSymbols());
                df.setMaximumFractionDigits(8);
                amount.setText(df.format(newValue.getValue(value)));
                setFiatAmount(AppServices.getFiatCurrencyExchangeRate(), value);
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
        if(existingPayNym != null && payNym.nymName().equals(existingPayNym.nymName())) {
            sendController.updateTransaction();
        }
    }

    public void setDnsPayment(DnsPayment dnsPayment) {
        if(dnsPayment.hasAddress()) {
            DnsPaymentCache.putDnsPayment(dnsPayment.bitcoinURI().getAddress(), dnsPayment);
        } else if(dnsPayment.hasSilentPaymentAddress()) {
            DnsPaymentCache.putDnsPayment(dnsPayment.bitcoinURI().getSilentPaymentAddress(), dnsPayment);
            setSilentPaymentAddress(dnsPayment.bitcoinURI().getSilentPaymentAddress());
        } else {
            AppServices.showWarningDialog("No Address Provided", "The DNS payment instruction for " + dnsPayment.hrn() + " resolved correctly but did not contain a bitcoin address.");
            return;
        }

        dnsPaymentProperty.set(dnsPayment);
        address.setText(dnsPayment.hrn());
        revalidate(address, addressListener);
        address.leftProperty().set(getBitcoinCharacter());
        if(label.getText().isEmpty() || (label.getText().startsWith("₿") && !label.getText().contains(" "))) {
            label.setText(dnsPayment.toString());
        }
        label.requestFocus();
    }

    private void setSilentPaymentAddress(SilentPaymentAddress silentPaymentAddress) {
        if(!sendController.getWalletForm().getWallet().canSendSilentPayments()) {
            Platform.runLater(() -> AppServices.showErrorDialog("Silent Payments Unsupported", "This wallet does not support sending silent payments. Use a single signature software wallet."));
            return;
        }

        silentPaymentAddressProperty.set(silentPaymentAddress);
        label.requestFocus();
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

        if(CardApi.isReaderAvailable()) {
            openWalletList.add(nfcCardWallet);
        }

        openWallets.setItems(FXCollections.observableList(openWalletList));
    }

    private Node getOpenWalletIcon(Wallet wallet) {
        if(wallet == payNymWallet) {
            return getPayNymGlyph();
        }

        if(wallet == nfcCardWallet) {
            return getNfcCardGlyph();
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
        SilentPaymentAddress silentPaymentAddress = silentPaymentAddressProperty.get();
        if(silentPaymentAddress != null) {
            return SilentPayment.getDummyAddress();
        }

        DnsPayment dnsPayment = dnsPaymentProperty.get();
        if(dnsPayment != null && dnsPayment.hasAddress()) {
            return dnsPayment.bitcoinURI().getAddress();
        }

        PayNym payNym = payNymProperty.get();
        if(payNym == null) {
            return Address.fromString(address.getText());
        }

        try {
            Wallet recipientBip47Wallet = getWalletForPayNym(payNym);
            if(recipientBip47Wallet != null) {
                int index = sendController.getPayNymSendIndex(this);
                WalletNode sendNode = recipientBip47Wallet.getFreshNode(KeyPurpose.SEND);
                for(int i = 0; i < index; i++) {
                    sendNode = recipientBip47Wallet.getFreshNode(KeyPurpose.SEND, sendNode);
                }
                ECKey pubKey = sendNode.getPubKey();
                return recipientBip47Wallet.getScriptType().getAddress(pubKey);
            }
        } catch(InvalidPaymentCodeException e) {
            log.error("Error creating payment code from PayNym", e);
        }

        throw new InvalidAddressException();
    }

    private Wallet getWalletForPayNym(PayNym payNym) throws InvalidPaymentCodeException {
        Wallet masterWallet = sendController.getWalletForm().getMasterWallet();
        return masterWallet.getChildWallet(new PaymentCode(payNym.paymentCode().toString()), payNym.segwit() ? ScriptType.P2WPKH : ScriptType.P2PKH);
    }

    boolean isSentToSamePayNym(PaymentController paymentController) {
        return (this != paymentController && payNymProperty.get() != null && payNymProperty.get().paymentCode().equals(paymentController.payNymProperty.get().paymentCode()));
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
                Payment payment;
                SilentPaymentAddress silentPaymentAddress = silentPaymentAddressProperty.get();
                WalletNode consolidationNode = consolidationNodeProperty.get();
                if(silentPaymentAddress != null) {
                    payment = new SilentPayment(silentPaymentAddress, label.getText(), value, sendAll);
                } else if(consolidationNode != null) {
                    payment = new WalletNodePayment(consolidationNode, label.getText(), value, sendAll);
                } else {
                    payment = new Payment(recipientAddress, label.getText(), value, sendAll);
                }

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
                DnsPayment dnsPayment = DnsPaymentCache.getDnsPayment(payment);
                if(dnsPayment != null) {
                    address.setText(dnsPayment.hrn());
                } else if(payment instanceof SilentPayment silentPayment) {
                    address.setText(silentPayment.getSilentPaymentAddress().getAddress());
                } else {
                    address.setText(payment.getAddress().toString());
                }
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
        consolidationNodeProperty.set(null);
        payNymProperty.set(null);
        dnsPaymentProperty.set(null);
        silentPaymentAddressProperty.set(null);
    }

    public void setMaxInput(ActionEvent event) {
        UtxoSelector utxoSelector = sendController.getUtxoSelector();
        if(utxoSelector == null) {
            MaxUtxoSelector maxUtxoSelector = new MaxUtxoSelector();
            sendController.utxoSelectorProperty().set(maxUtxoSelector);
        } else if(utxoSelector instanceof PresetUtxoSelector presetUtxoSelector && !isValidAddressAndLabel() && sendController.getPaymentTabs().getTabs().size() == 1) {
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
        qrScanDialog.initOwner(scanQrButton.getScene().getWindow());
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
        if(bitcoinURI.getAddress() != null && bitcoinURI.getPayjoinUrl() != null) {
            AppServices.addPayjoinURI(bitcoinURI);
        }
        sendController.updateTransaction();
    }

    private List<Address> getOtherAddresses() {
        List<Address> otherAddresses = new ArrayList<>();
        for(Tab tab : sendController.getPaymentTabs().getTabs()) {
            PaymentController controller = (PaymentController)tab.getUserData();
            if(controller != this) {
                try {
                    otherAddresses.add(controller.getRecipientAddress());
                } catch(InvalidAddressException e) {
                    //ignore
                }
            }
        }

        return otherAddresses;
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
        payNymGlyph.setFontSize(10);
        return payNymGlyph;
    }

    public static Node getBitcoinCharacter() {
        try {
            URL url;
            if(Config.get().getTheme() == Theme.DARK) {
                url = AppServices.class.getResource("/image/bitcoin-character-invert.svg");
            } else {
                url = AppServices.class.getResource("/image/bitcoin-character.svg");
            }
            if(url != null) {
                SVGImage svgImage = SVGLoader.load(url);
                HBox hBox = new HBox();
                hBox.setAlignment(Pos.CENTER);
                hBox.getChildren().add(svgImage);
                hBox.setPadding(new Insets(0, 2, 0, 4));
                return hBox;
            }
        } catch(Exception e) {
            log.error("Could not load bitcoin character");
        }

        return null;
    }

    public static Glyph getNfcCardGlyph() {
        Glyph nfcCardGlyph = new Glyph(FontAwesome5.FONT_NAME, FontAwesome5.Glyph.WIFI);
        nfcCardGlyph.getStyleClass().add("nfccard-icon");
        nfcCardGlyph.setFontSize(12);
        return nfcCardGlyph;
    }

    @Subscribe
    public void bitcoinUnitChanged(BitcoinUnitChangedEvent event) {
        BitcoinUnit unit = sendController.getBitcoinUnit(event.getBitcoinUnit());
        amountUnit.getSelectionModel().select(BitcoinUnit.BTC.equals(unit) ? 0 : 1);
    }

    @Subscribe
    public void unitFormatChanged(UnitFormatChangedEvent event) {
        if(amount.getTextFormatter() instanceof CoinTextFormatter coinTextFormatter && coinTextFormatter.getUnitFormat() != event.getUnitFormat()) {
            Long value = getRecipientValueSats(coinTextFormatter.getUnitFormat(), amountUnit.getSelectionModel().getSelectedItem());
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

    @Subscribe
    public void hideAmountsStatusChanged(HideAmountsStatusEvent event) {
        fiatAmount.refresh(Config.get().getUnitFormat());
    }

    private static class DnsPaymentService extends Service<Optional<DnsPayment>> {
        private final String hrn;

        public DnsPaymentService(String hrn) {
            this.hrn = hrn;
        }

        @Override
        protected Task<Optional<DnsPayment>> createTask() {
            return new Task<>() {
                @Override
                protected Optional<DnsPayment> call() throws Exception {
                    DnsPaymentResolver resolver = new DnsPaymentResolver(hrn);
                    return resolver.resolve();
                }
            };
        }
    }
}
