package com.sparrowwallet.sparrow.wallet;

import com.google.common.eventbus.Subscribe;
import com.sparrowwallet.drongo.BitcoinUnit;
import com.sparrowwallet.drongo.KeyPurpose;
import com.sparrowwallet.drongo.Network;
import com.sparrowwallet.drongo.SecureString;
import com.sparrowwallet.drongo.address.Address;
import com.sparrowwallet.drongo.address.InvalidAddressException;
import com.sparrowwallet.drongo.bip47.PaymentCode;
import com.sparrowwallet.drongo.bip47.SecretPoint;
import com.sparrowwallet.drongo.crypto.ECKey;
import com.sparrowwallet.drongo.protocol.*;
import com.sparrowwallet.drongo.psbt.PSBT;
import com.sparrowwallet.drongo.wallet.*;
import com.sparrowwallet.sparrow.*;
import com.sparrowwallet.sparrow.control.*;
import com.sparrowwallet.sparrow.event.*;
import com.sparrowwallet.sparrow.glyphfont.FontAwesome5;
import com.sparrowwallet.sparrow.io.Config;
import com.sparrowwallet.sparrow.io.Storage;
import com.sparrowwallet.sparrow.net.*;
import com.sparrowwallet.sparrow.paynym.PayNym;
import com.sparrowwallet.sparrow.paynym.PayNymService;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.property.*;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.beans.value.WeakChangeListener;
import javafx.collections.ListChangeListener;
import javafx.concurrent.Service;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import javafx.util.StringConverter;
import org.controlsfx.glyphfont.Glyph;
import org.controlsfx.validation.ValidationResult;
import org.controlsfx.validation.ValidationSupport;
import org.controlsfx.validation.Validator;
import org.controlsfx.validation.decoration.StyleClassValidationDecoration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tornadofx.control.Field;

import java.io.IOException;
import java.net.URL;
import java.text.DecimalFormat;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.sparrowwallet.sparrow.AppServices.*;

public class SendController extends WalletFormController implements Initializable {
    private static final Logger log = LoggerFactory.getLogger(SendController.class);

    @FXML
    private TabPane paymentTabs;

    @FXML
    private ToggleGroup feeSelectionToggleGroup;

    @FXML
    private ToggleButton targetBlocksToggle;

    @FXML
    private ToggleButton mempoolSizeToggle;

    @FXML
    private ToggleButton recentBlocksToggle;

    @FXML
    private Field targetBlocksField;

    @FXML
    private Slider targetBlocks;

    @FXML
    private Field feeRangeField;

    @FXML
    private FeeRangeSlider feeRange;

    @FXML
    private CopyableLabel feeRate;

    @FXML
    private Label cpfpFeeRate;

    @FXML
    private Label feeRatePriority;

    @FXML
    private Glyph feeRatePriorityGlyph;

    @FXML
    private TextField fee;

    @FXML
    private ComboBox<BitcoinUnit> feeAmountUnit;

    @FXML
    private FiatLabel fiatFeeAmount;

    @FXML
    private BlockTargetFeeRatesChart blockTargetFeeRatesChart;

    @FXML
    private MempoolSizeFeeRatesChart mempoolSizeFeeRatesChart;

    @FXML
    private RecentBlocksView recentBlocksView;

    @FXML
    private TransactionDiagram transactionDiagram;

    @FXML
    private ToggleGroup optimizationToggleGroup;

    @FXML
    private ToggleButton efficiencyToggle;

    @FXML
    private ToggleButton privacyToggle;

    @FXML
    private HelpLabel optimizationHelp;

    @FXML
    private Label privacyAnalysis;

    @FXML
    private Button clearButton;

    @FXML
    private Button createButton;

    @FXML
    private Button notificationButton;

    private StackPane tabHeader;

    private final BooleanProperty userFeeSet = new SimpleBooleanProperty(false);

    private final ObjectProperty<UtxoSelector> utxoSelectorProperty = new SimpleObjectProperty<>(null);

    private final ObjectProperty<TxoFilter> txoFilterProperty = new SimpleObjectProperty<>(null);

    private final ObjectProperty<PaymentCode> paymentCodeProperty = new SimpleObjectProperty<>(null);

    private final ObjectProperty<WalletTransaction> walletTransactionProperty = new SimpleObjectProperty<>(null);

    private final BooleanProperty insufficientInputsProperty = new SimpleBooleanProperty(false);

    private final StringProperty utxoLabelSelectionProperty = new SimpleStringProperty("");

    private final ObjectProperty<BlockTransaction> replacedTransactionProperty = new SimpleObjectProperty<>(null);

    private final ObjectProperty<FeeRatesSelection> feeRatesSelectionProperty = new SimpleObjectProperty<>(null);

    private final List<byte[]> opReturnsList = new ArrayList<>();

    private final Set<WalletNode> excludedChangeNodes = new HashSet<>();

    private final Map<Wallet, Map<Address, WalletNode>> addressNodeMap = new HashMap<>();

    private final ChangeListener<String> feeListener = new ChangeListener<>() {
        @Override
        public void changed(ObservableValue<? extends String> observable, String oldValue, String newValue) {
            userFeeSet.set(true);
            if(newValue.isEmpty()) {
                fiatFeeAmount.setText("");
            } else {
                setFiatFeeAmount(AppServices.getFiatCurrencyExchangeRate(), getFeeValueSats());
            }

            createButton.setDisable(isInsufficientFeeRate());
            setTargetBlocks(getTargetBlocks());
            updateTransaction();
        }
    };

    private final ChangeListener<Number> targetBlocksListener = new ChangeListener<>() {
        @Override
        public void changed(ObservableValue<? extends Number> observable, Number oldValue, Number newValue) {
            Map<Integer, Double> targetBlocksFeeRates = getTargetBlocksFeeRates();
            Integer target = getTargetBlocks();

            if(targetBlocksFeeRates != null) {
                setFeeRate(targetBlocksFeeRates.get(target));
                blockTargetFeeRatesChart.select(target);
            } else {
                feeRate.setText("Unknown");
            }

            Tooltip tooltip = new Tooltip("Target inclusion within " + target + " blocks");
            targetBlocks.setTooltip(tooltip);

            userFeeSet.set(false);
            for(Tab tab : paymentTabs.getTabs()) {
                PaymentController controller = (PaymentController)tab.getUserData();
                controller.revalidateAmount();
            }
            updateTransaction();
        }
    };

    private final ChangeListener<Number> feeRangeListener = new ChangeListener<>() {
        @Override
        public void changed(ObservableValue<? extends Number> observable, Number oldValue, Number newValue) {
            setFeeRate(getFeeRangeRate());
            userFeeSet.set(false);
            for(Tab tab : paymentTabs.getTabs()) {
                PaymentController controller = (PaymentController)tab.getUserData();
                controller.revalidateAmount();
            }
            updateTransaction();
        }
    };

    private final ChangeListener<Boolean> broadcastButtonsOnlineListener = (observable, oldValue, newValue) -> {
        notificationButton.setDisable(walletTransactionProperty.get() == null || isInsufficientFeeRate() || !newValue);
    };

    private ValidationSupport validationSupport;

    private WalletTransactionService walletTransactionService;

    private boolean overrideOptimizationStrategy;

    private boolean updateDefaultFeeRate;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        EventManager.get().register(this);
    }

    @Override
    public void initializeView() {
        addValidation();

        addPaymentTab();
        initializeTabHeader(0);

        paymentTabs.getTabs().addListener((ListChangeListener<Tab>) c -> {
            if(tabHeader != null) {
                tabHeader.setVisible(c.getList().size() > 1);
            }

            if(c.getList().size() > 1) {
                if(!paymentTabs.getStyleClass().contains("multiple-tabs")) {
                    paymentTabs.getStyleClass().add("multiple-tabs");
                }
                paymentTabs.getTabs().forEach(tab -> {
                    tab.setClosable(true);
                });
            } else {
                paymentTabs.getStyleClass().remove("multiple-tabs");
                Tab remainingTab = paymentTabs.getTabs().get(0);
                remainingTab.setClosable(false);
                remainingTab.setText("1");
            }

            updateTransaction();
        });

        insufficientInputsProperty.addListener((observable, oldValue, newValue) -> {
            for(Tab tab : paymentTabs.getTabs()) {
                PaymentController controller = (PaymentController)tab.getUserData();
                controller.revalidateAmount();
            }
            revalidate(fee, feeListener);
        });

        targetBlocksField.managedProperty().bind(targetBlocksField.visibleProperty());
        targetBlocks.setMin(0);
        targetBlocks.setMax(TARGET_BLOCKS_RANGE.size() - 1);
        targetBlocks.setMajorTickUnit(1);
        targetBlocks.setMinorTickCount(0);
        targetBlocks.setLabelFormatter(new StringConverter<Double>() {
            @Override
            public String toString(Double object) {
                String blocks = Integer.toString(TARGET_BLOCKS_RANGE.get(object.intValue()));
                return (object.intValue() == TARGET_BLOCKS_RANGE.size() - 1) ? blocks + "+" : blocks;
            }

            @Override
            public Double fromString(String string) {
                return (double)TARGET_BLOCKS_RANGE.indexOf(Integer.valueOf(string.replace("+", "")));
            }
        });
        targetBlocks.valueProperty().addListener(targetBlocksListener);

        feeRangeField.managedProperty().bind(feeRangeField.visibleProperty());
        feeRangeField.visibleProperty().bind(targetBlocksField.visibleProperty().not());
        feeRange.valueProperty().addListener(feeRangeListener);

        blockTargetFeeRatesChart.managedProperty().bind(blockTargetFeeRatesChart.visibleProperty());
        blockTargetFeeRatesChart.visibleProperty().bind(Bindings.equal(feeRatesSelectionProperty, FeeRatesSelection.BLOCK_TARGET));
        blockTargetFeeRatesChart.initialize();
        Map<Integer, Double> targetBlocksFeeRates = getTargetBlocksFeeRates();
        if(targetBlocksFeeRates != null) {
            blockTargetFeeRatesChart.update(targetBlocksFeeRates);
        } else {
            feeRate.setText("Unknown");
        }

        mempoolSizeFeeRatesChart.managedProperty().bind(mempoolSizeFeeRatesChart.visibleProperty());
        mempoolSizeFeeRatesChart.visibleProperty().bind(Bindings.equal(feeRatesSelectionProperty, FeeRatesSelection.MEMPOOL_SIZE));
        mempoolSizeFeeRatesChart.initialize();
        Map<Date, Set<MempoolRateSize>> mempoolHistogram = getMempoolHistogram();
        if(mempoolHistogram != null) {
            mempoolSizeFeeRatesChart.update(mempoolHistogram);
        }

        recentBlocksView.managedProperty().bind(recentBlocksView.visibleProperty());
        recentBlocksView.visibleProperty().bind(Bindings.equal(feeRatesSelectionProperty, FeeRatesSelection.RECENT_BLOCKS));
        List<BlockSummary> blockSummaries = AppServices.getBlockSummaries().values().stream().sorted().toList();
        if(!blockSummaries.isEmpty()) {
            recentBlocksView.update(blockSummaries, AppServices.getNextBlockMedianFeeRate());
        }

        feeRatesSelectionProperty.addListener((_, oldValue, newValue) -> {
            boolean isBlockTargetSelection = (newValue == FeeRatesSelection.BLOCK_TARGET);
            boolean wasBlockTargetSelection = (oldValue == FeeRatesSelection.BLOCK_TARGET || oldValue == null);
            targetBlocksField.setVisible(isBlockTargetSelection);
            if(isBlockTargetSelection) {
                setTargetBlocks(getTargetBlocks(getFeeRangeRate()));
                updateTransaction();
            } else if(wasBlockTargetSelection) {
                setFeeRangeRate(getTargetBlocksFeeRates().get(getTargetBlocks()));
                updateTransaction();
            }
        });

        FeeRatesSelection feeRatesSelection = Config.get().getFeeRatesSelection();
        feeRatesSelection = (feeRatesSelection == null ? FeeRatesSelection.RECENT_BLOCKS : feeRatesSelection);
        cpfpFeeRate.managedProperty().bind(cpfpFeeRate.visibleProperty());
        cpfpFeeRate.setVisible(false);
        setDefaultFeeRate();
        feeRatesSelectionProperty.set(feeRatesSelection);
        feeSelectionToggleGroup.selectToggle(feeRatesSelection == FeeRatesSelection.BLOCK_TARGET ? targetBlocksToggle :
                (feeRatesSelection == FeeRatesSelection.MEMPOOL_SIZE ? mempoolSizeToggle : recentBlocksToggle));
        feeSelectionToggleGroup.selectedToggleProperty().addListener((observable, oldValue, newValue) -> {
            if(newValue != null) {
                FeeRatesSelection newFeeRatesSelection = (FeeRatesSelection)newValue.getUserData();
                Config.get().setFeeRatesSelection(newFeeRatesSelection);
                EventManager.get().post(new FeeRatesSelectionChangedEvent(getWalletForm().getWallet(), newFeeRatesSelection));
            };
        });

        fee.setTextFormatter(new CoinTextFormatter(Config.get().getUnitFormat()));
        fee.textProperty().addListener(feeListener);

        BitcoinUnit unit = getBitcoinUnit(Config.get().getBitcoinUnit());
        feeAmountUnit.getSelectionModel().select(BitcoinUnit.BTC.equals(unit) ? 0 : 1);
        feeAmountUnit.valueProperty().addListener((observable, oldValue, newValue) -> {
            Long value = getFeeValueSats(oldValue);
            if(value != null) {
                setFeeValueSats(value);
            }
        });

        userFeeSet.addListener((observable, oldValue, newValue) -> {
            blockTargetFeeRatesChart.select(0);

            Node thumb = getSliderThumb();
            if(thumb != null) {
                if(newValue) {
                    thumb.getStyleClass().add("inactive");
                } else {
                    thumb.getStyleClass().remove("inactive");
                }
            }
        });

        utxoLabelSelectionProperty.addListener((observable, oldValue, newValue) -> {
            clearButton.setText("Clear" + newValue);
        });

        utxoSelectorProperty.addListener((observable, oldValue, utxoSelector) -> {
            updateMaxClearButtons(utxoSelector, txoFilterProperty.get());
        });

        txoFilterProperty.addListener((observable, oldValue, txoFilter) -> {
            updateMaxClearButtons(utxoSelectorProperty.get(), txoFilter);
        });

        walletTransactionProperty.addListener((observable, oldValue, walletTransaction) -> {
            setEffectiveFeeRate(walletTransaction);
            if(walletTransaction != null) {
                setPayments(walletTransaction.getPayments().stream().filter(payment -> payment.getType() != Payment.Type.FAKE_MIX).collect(Collectors.toList()));

                double feeRate = walletTransaction.getFeeRate();
                if(userFeeSet.get()) {
                    setTargetBlocks(getTargetBlocks(feeRate));
                    setFeeRangeRate(feeRate);
                    revalidate(fee, feeListener);
                } else {
                    setFeeValueSats(walletTransaction.getFee());
                }

                setFeeRate(feeRate);
            }

            transactionDiagram.update(walletTransaction);
            updatePrivacyAnalysis(walletTransaction);
            createButton.setDisable(walletTransaction == null || isInsufficientFeeRate());
            notificationButton.setDisable(walletTransaction == null || isInsufficientFeeRate() || !AppServices.isConnected());
        });

        transactionDiagram.sceneProperty().addListener((observable, oldScene, newScene) -> {
            if(oldScene == null && newScene != null) {
                transactionDiagram.update(walletTransactionProperty.get());
                newScene.getWindow().heightProperty().addListener((observable1, oldValue, newValue) -> {
                    transactionDiagram.update(walletTransactionProperty.get());
                });
            }
        });

        efficiencyToggle.setOnAction(event -> {
            if(StandardAccount.isWhirlpoolMixAccount(getWalletForm().getWallet().getStandardAccountType()) && !overrideOptimizationStrategy) {
                AppServices.showWarningDialog("Privacy may be lost!", "It is recommended to optimize for privacy when sending coinjoined outputs.");
                overrideOptimizationStrategy = true;
            }
            Config.get().setSendOptimizationStrategy(OptimizationStrategy.EFFICIENCY);
            updateTransaction();
        });
        privacyToggle.setOnAction(event -> {
            Config.get().setSendOptimizationStrategy(OptimizationStrategy.PRIVACY);
            updateTransaction();
        });
        setPreferredOptimizationStrategy();
        updatePrivacyAnalysis(null);
        optimizationHelp.managedProperty().bind(optimizationHelp.visibleProperty());
        privacyAnalysis.managedProperty().bind(privacyAnalysis.visibleProperty());
        optimizationHelp.visibleProperty().bind(privacyAnalysis.visibleProperty().not());

        createButton.managedProperty().bind(createButton.visibleProperty());
        notificationButton.managedProperty().bind(notificationButton.visibleProperty());
        createButton.visibleProperty().bind(notificationButton.visibleProperty().not());
        notificationButton.setVisible(false);
        AppServices.onlineProperty().addListener(new WeakChangeListener<>(broadcastButtonsOnlineListener));
    }

    private void initializeTabHeader(int count) {
        final int lookupCount = count;
        Platform.runLater(() -> {
            StackPane stackPane = (StackPane)paymentTabs.lookup(".tab-header-area");
            if(stackPane != null) {
                tabHeader = stackPane;
                tabHeader.managedProperty().bind(tabHeader.visibleProperty());
                tabHeader.setVisible(paymentTabs.getTabs().size() > 1);
                paymentTabs.getStyleClass().remove("initial");
            } else if(lookupCount < 20) {
                initializeTabHeader(lookupCount+1);
            }
        });
    }

    public BitcoinUnit getBitcoinUnit(BitcoinUnit bitcoinUnit) {
        BitcoinUnit unit = bitcoinUnit;
        if(unit == null || unit.equals(BitcoinUnit.AUTO)) {
            unit = getWalletForm().getWallet().getAutoUnit();
        }
        return unit;
    }

    public ValidationSupport getValidationSupport() {
        return validationSupport;
    }

    private void addValidation() {
        validationSupport = new ValidationSupport();
        validationSupport.setValidationDecorator(new StyleClassValidationDecoration());
        validationSupport.registerValidator(fee, Validator.combine(
                (Control c, String newValue) -> ValidationResult.fromErrorIf( c, "Insufficient Inputs", userFeeSet.get() && insufficientInputsProperty.get()),
                (Control c, String newValue) -> ValidationResult.fromErrorIf( c, "Insufficient Fee", isInsufficientFeeRate()),
                (Control c, String newValue) -> ValidationResult.fromWarningIf( c, "Fee Rate Below Minimum", isBelowMinimumFeeRate())
        ));

        validationSupport.setErrorDecorationEnabled(false);
    }

    public void addPaymentTab() {
        if(Config.get().getSuggestSendToMany() == null && openSendToMany()) {
            return;
        }

        Tab tab = getPaymentTab();
        paymentTabs.getTabs().add(tab);
        paymentTabs.getSelectionModel().select(tab);
    }

    private boolean openSendToMany() {
        try {
            List<Payment> payments = getPayments();
            if(payments.size() == 3) {
                ConfirmationAlert confirmationAlert = new ConfirmationAlert("Open Send To Many?", "Open the Tools > Send To Many dialog to add multiple payments?", ButtonType.NO, ButtonType.YES);
                Optional<ButtonType> optType = confirmationAlert.showAndWait();
                if(confirmationAlert.isDontAskAgain() && optType.isPresent()) {
                    Config.get().setSuggestSendToMany(optType.get() == ButtonType.YES);
                }
                if(optType.isPresent() && optType.get() == ButtonType.YES) {
                    Platform.runLater(() -> EventManager.get().post(new RequestSendToManyEvent(payments)));
                    return true;
                }
            }
        } catch(Exception e) {
            //ignore
        }

        return false;
    }

    public Tab getPaymentTab() {
        OptionalInt highestTabNo = paymentTabs.getTabs().stream().mapToInt(tab -> Integer.parseInt(tab.getText().trim())).max();
        Tab tab = new Tab(" " + (highestTabNo.isPresent() ? highestTabNo.getAsInt() + 1 : 1) + " ");

        try {
            FXMLLoader paymentLoader = new FXMLLoader(AppServices.class.getResource("wallet/payment.fxml"));
            tab.setContent(paymentLoader.load());
            PaymentController controller = paymentLoader.getController();
            controller.setSendController(this);
            controller.initializeView();
            tab.setUserData(controller);
            return tab;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public List<Payment> getPayments() {
        List<Payment> payments = new ArrayList<>();
        for(Tab tab : paymentTabs.getTabs()) {
            PaymentController controller = (PaymentController)tab.getUserData();
            payments.add(controller.getPayment());
        }

        return payments;
    }

    public void setPayments(List<Payment> payments) {
        while(paymentTabs.getTabs().size() < payments.size()) {
            addPaymentTab();
        }

        while(paymentTabs.getTabs().size() > payments.size()) {
            paymentTabs.getTabs().remove(paymentTabs.getTabs().size() - 1);
        }

        for(int i = 0; i < paymentTabs.getTabs().size(); i++) {
            Payment payment = payments.get(i);
            PaymentController controller = (PaymentController)paymentTabs.getTabs().get(i).getUserData();
            controller.setPayment(payment);
        }
    }

    int getPayNymSendIndex(PaymentController paymentController) {
        int index = 0;
        for(Tab tab : paymentTabs.getTabs()) {
            PaymentController controller = (PaymentController)tab.getUserData();
            if(controller == paymentController) {
                break;
            } else if(controller.isSentToSamePayNym(paymentController)) {
                index++;
            }
        }

        return index;
    }

    public void updateTransaction() {
        updateTransaction(null);
    }

    public void updateTransaction(boolean sendAll) {
        try {
            if(paymentTabs.getTabs().size() == 1) {
                PaymentController controller = (PaymentController)paymentTabs.getTabs().get(0).getUserData();
                controller.setSendMax(sendAll);
                updateTransaction(List.of(controller.getPayment()));
            } else {
                updateTransaction(null);
            }
        } catch(IllegalStateException e) {
            //ignore
        }
    }

    public void updateTransaction(List<Payment> transactionPayments) {
        if(walletTransactionService != null && walletTransactionService.isRunning()) {
            walletTransactionService.setIgnoreResult(true);
            walletTransactionService.cancel();
        }

        try {
            List<Payment> payments = transactionPayments != null ? transactionPayments : getPayments();
            updateOptimizationButtons(payments);
            if(!userFeeSet.get() || (getFeeValueSats() != null && getFeeValueSats() > 0)) {
                Wallet wallet = getWalletForm().getWallet();
                Long userFee = userFeeSet.get() ? getFeeValueSats() : null;
                double feeRate = getUserFeeRate();
                Integer currentBlockHeight = AppServices.getCurrentBlockHeight();
                boolean groupByAddress = Config.get().isGroupByAddress();
                boolean includeMempoolOutputs = Config.get().isIncludeMempoolOutputs();
                BlockTransaction replacedTransaction = replacedTransactionProperty.get();

                walletTransactionService = new WalletTransactionService(addressNodeMap, wallet, getUtxoSelectors(payments), getTxoFilters(),
                        payments, opReturnsList, excludedChangeNodes,
                        feeRate, getMinimumFeeRate(), userFee, currentBlockHeight, groupByAddress, includeMempoolOutputs, replacedTransaction);
                walletTransactionService.setOnSucceeded(event -> {
                    if(!walletTransactionService.isIgnoreResult()) {
                        walletTransactionProperty.setValue(walletTransactionService.getValue());
                        insufficientInputsProperty.set(false);
                    }
                });
                walletTransactionService.setOnFailed(event -> {
                    if(!walletTransactionService.isIgnoreResult()) {
                        transactionDiagram.clear();
                        walletTransactionProperty.setValue(null);
                        if(event.getSource().getException() instanceof InsufficientFundsException) {
                            insufficientInputsProperty.set(true);
                        }
                    }
                });

                final WalletTransactionService currentWalletTransactionService = walletTransactionService;
                final KeyFrame delay = new KeyFrame(Duration.millis(200), e -> {
                    if(currentWalletTransactionService.isRunning()) {
                        transactionDiagram.update(currentWalletTransactionService.getMessage());
                        currentWalletTransactionService.messageProperty().addListener((observable1, oldValue, newValue) -> {
                            if(currentWalletTransactionService.isRunning()) {
                                transactionDiagram.update(newValue);
                            }
                        });
                        createButton.setDisable(true);
                        notificationButton.setDisable(true);
                    }
                });
                final Timeline timeline = new Timeline(delay);
                timeline.play();

                walletTransactionService.start();
            }
        } catch(InvalidAddressException | IllegalStateException e) {
            walletTransactionProperty.setValue(null);
        }
    }

    private List<UtxoSelector> getUtxoSelectors(List<Payment> payments) throws InvalidAddressException {
        if(utxoSelectorProperty.get() != null) {
            return List.of(utxoSelectorProperty.get());
        }

        Wallet wallet = getWalletForm().getWallet();
        long noInputsFee = wallet.getNoInputsFee(getPayments(), getUserFeeRate());
        long costOfChange = wallet.getCostOfChange(getUserFeeRate(), getMinimumFeeRate());

        List<UtxoSelector> selectors = new ArrayList<>();
        OptimizationStrategy optimizationStrategy = (OptimizationStrategy)optimizationToggleGroup.getSelectedToggle().getUserData();
        if(optimizationStrategy == OptimizationStrategy.PRIVACY
                && payments.size() == 1
                && (payments.get(0).getAddress().getScriptType() == getWalletForm().getWallet().getFreshNode(KeyPurpose.RECEIVE).getAddress().getScriptType())) {
            selectors.add(new StonewallUtxoSelector(payments.get(0).getAddress().getScriptType(), noInputsFee));
        }

        selectors.addAll(List.of(new BnBUtxoSelector(noInputsFee, costOfChange), new KnapsackUtxoSelector(noInputsFee)));
        return selectors;
    }

    private static class WalletTransactionService extends Service<WalletTransaction> {
        private final Map<Wallet, Map<Address, WalletNode>> addressNodeMap;
        private final Wallet wallet;
        private final List<UtxoSelector> utxoSelectors;
        private final List<TxoFilter> txoFilters;
        private final List<Payment> payments;
        private final List<byte[]> opReturns;
        private final Set<WalletNode> excludedChangeNodes;
        private final double feeRate;
        private final double longTermFeeRate;
        private final Long fee;
        private final Integer currentBlockHeight;
        private final boolean groupByAddress;
        private final boolean includeMempoolOutputs;
        private final BlockTransaction replacedTransaction;
        private boolean ignoreResult;

        public WalletTransactionService(Map<Wallet, Map<Address, WalletNode>> addressNodeMap,
                                        Wallet wallet, List<UtxoSelector> utxoSelectors, List<TxoFilter> txoFilters,
                                        List<Payment> payments, List<byte[]> opReturns, Set<WalletNode> excludedChangeNodes,
                                        double feeRate, double longTermFeeRate, Long fee, Integer currentBlockHeight, boolean groupByAddress, boolean includeMempoolOutputs, BlockTransaction replacedTransaction) {
            this.addressNodeMap = addressNodeMap;
            this.wallet = wallet;
            this.utxoSelectors = utxoSelectors;
            this.txoFilters = txoFilters;
            this.payments = payments;
            this.opReturns = opReturns;
            this.excludedChangeNodes = excludedChangeNodes;
            this.feeRate = feeRate;
            this.longTermFeeRate = longTermFeeRate;
            this.fee = fee;
            this.currentBlockHeight = currentBlockHeight;
            this.groupByAddress = groupByAddress;
            this.includeMempoolOutputs = includeMempoolOutputs;
            this.replacedTransaction = replacedTransaction;
        }

        @Override
        protected Task<WalletTransaction> createTask() {
            return new Task<>() {
                protected WalletTransaction call() throws InsufficientFundsException {
                    try {
                        return getWalletTransaction();
                    } catch(InsufficientFundsException e) {
                        if(e.getTargetValue() != null && replacedTransaction != null && utxoSelectors.size() == 1 && utxoSelectors.get(0) instanceof PresetUtxoSelector presetUtxoSelector) {
                            //Creating RBF transaction - include additional UTXOs if available to pay desired fee
                            List<TxoFilter> filters = new ArrayList<>(txoFilters);
                            filters.add(presetUtxoSelector.asExcludeTxoFilter());
                            List<OutputGroup> outputGroups = wallet.getGroupedUtxos(filters, feeRate, AppServices.getMinimumRelayFeeRate(), Config.get().isGroupByAddress())
                                    .stream().filter(outputGroup -> outputGroup.getEffectiveValue() >= 0).collect(Collectors.toList());
                            Collections.shuffle(outputGroups);

                            while(!outputGroups.isEmpty() && presetUtxoSelector.getPresetUtxos().stream().mapToLong(BlockTransactionHashIndex::getValue).sum() < e.getTargetValue()) {
                                OutputGroup outputGroup = outputGroups.remove(0);
                                for(BlockTransactionHashIndex utxo : outputGroup.getUtxos()) {
                                    presetUtxoSelector.getPresetUtxos().add(utxo);
                                }
                            }

                            return getWalletTransaction();
                        }

                        throw e;
                    }
                }

                private WalletTransaction getWalletTransaction() throws InsufficientFundsException {
                    updateMessage("Selecting UTXOs...");
                    WalletTransaction walletTransaction = wallet.createWalletTransaction(utxoSelectors, txoFilters, payments, opReturns, excludedChangeNodes,
                            feeRate, longTermFeeRate, fee, currentBlockHeight, groupByAddress, includeMempoolOutputs);
                    updateMessage("Deriving keys...");
                    walletTransaction.updateAddressNodeMap(addressNodeMap, walletTransaction.getWallet());
                    return walletTransaction;
                }
            };
        }

        public boolean isIgnoreResult() {
            return ignoreResult;
        }

        public void setIgnoreResult(boolean ignoreResult) {
            this.ignoreResult = ignoreResult;
        }
    }

    private List<TxoFilter> getTxoFilters() {
        SpentTxoFilter spentTxoFilter = new SpentTxoFilter(replacedTransactionProperty.get() == null ? null : replacedTransactionProperty.get().getHash());

        TxoFilter txoFilter = txoFilterProperty.get();
        if(txoFilter != null) {
            return List.of(txoFilter, spentTxoFilter, new FrozenTxoFilter(), new CoinbaseTxoFilter(getWalletForm().getWallet()));
        }

        return List.of(spentTxoFilter, new FrozenTxoFilter(), new CoinbaseTxoFilter(getWalletForm().getWallet()));
    }

    private void setDefaultFeeRate() {
        int defaultTarget = TARGET_BLOCKS_RANGE.get((TARGET_BLOCKS_RANGE.size() / 2) - 1);
        int index = TARGET_BLOCKS_RANGE.indexOf(defaultTarget);
        Double defaultRate = getTargetBlocksFeeRates().get(defaultTarget);
        targetBlocks.setValue(index);
        blockTargetFeeRatesChart.select(defaultTarget);
        recentBlocksView.updateFeeRate(defaultRate);
        setFeeRangeRate(defaultRate);
        setFeeRate(getFeeRangeRate());
        if(Network.get().equals(Network.MAINNET) && defaultRate == getFallbackFeeRate()) {
            //Update the selected fee rate once fee rates have been received
            updateDefaultFeeRate = true;
        }
    }

    private Long getFeeValueSats() {
        return getFeeValueSats(feeAmountUnit.getSelectionModel().getSelectedItem());
    }

    private Long getFeeValueSats(BitcoinUnit bitcoinUnit) {
        return getFeeValueSats(Config.get().getUnitFormat(), bitcoinUnit);
    }

    private Long getFeeValueSats(UnitFormat unitFormat, BitcoinUnit bitcoinUnit) {
        if(fee.getText() != null && !fee.getText().isEmpty()) {
            UnitFormat format = unitFormat == null ? UnitFormat.DOT : unitFormat;
            double fieldValue = Double.parseDouble(fee.getText().replaceAll(Pattern.quote(format.getGroupingSeparator()), "").replaceAll(",", "."));
            return bitcoinUnit.getSatsValue(fieldValue);
        }

        return null;
    }

    private void setFeeValueSats(long feeValue) {
        fee.textProperty().removeListener(feeListener);
        UnitFormat unitFormat = Config.get().getUnitFormat() == null ? UnitFormat.DOT : Config.get().getUnitFormat();
        DecimalFormat df = new DecimalFormat("#.#", unitFormat.getDecimalFormatSymbols());
        df.setMaximumFractionDigits(8);
        fee.setText(df.format(feeAmountUnit.getValue().getValue(feeValue)));
        fee.textProperty().addListener(feeListener);
        setFiatFeeAmount(AppServices.getFiatCurrencyExchangeRate(), feeValue);
    }

    private Integer getTargetBlocks() {
        int index = (int)targetBlocks.getValue();
        return TARGET_BLOCKS_RANGE.get(index);
    }

    private Integer getTargetBlocks(double feeRate) {
        Map<Integer, Double> targetBlocksFeeRates = getTargetBlocksFeeRates();
        int maxTargetBlocks = 1;
        for(Integer targetBlocks : targetBlocksFeeRates.keySet()) {
            if(TARGET_BLOCKS_RANGE.contains(targetBlocks)) {
                maxTargetBlocks = Math.max(maxTargetBlocks, targetBlocks);
                Double candidate = targetBlocksFeeRates.get(targetBlocks);
                if(Math.round(feeRate) >= Math.round(candidate)) {
                    return targetBlocks;
                }
            }
        }

        return maxTargetBlocks;
    }

    private void setTargetBlocks(Integer target) {
        targetBlocks.valueProperty().removeListener(targetBlocksListener);
        int index = TARGET_BLOCKS_RANGE.indexOf(target);
        targetBlocks.setValue(index);
        blockTargetFeeRatesChart.select(target);
        Tooltip tooltip = new Tooltip("Target inclusion within " + target + " blocks");
        targetBlocks.setTooltip(tooltip);
        targetBlocks.valueProperty().addListener(targetBlocksListener);
    }

    private Map<Integer, Double> getTargetBlocksFeeRates() {
        Map<Integer, Double> retrievedFeeRates = AppServices.getTargetBlockFeeRates();
        if(retrievedFeeRates == null) {
            retrievedFeeRates = TARGET_BLOCKS_RANGE.stream().collect(Collectors.toMap(java.util.function.Function.identity(), v -> getFallbackFeeRate(),
                    (u, v) -> { throw new IllegalStateException("Duplicate target blocks"); },
                    LinkedHashMap::new));
        }

        return retrievedFeeRates;
    }

    private Double getFeeRangeRate() {
        return feeRange.getFeeRate();
    }

    private void setFeeRangeRate(Double feeRate) {
        feeRange.valueProperty().removeListener(feeRangeListener);
        feeRange.setFeeRate(feeRate);
        feeRange.valueProperty().addListener(feeRangeListener);
    }

    /**
     * This method retrieves the fee rate used as input to constructing the transaction.
     * Where the user has set a custom fee amount, using the slider fee rate can mean the UTXO selectors underestimate the UTXO effective values and fail to find a solution
     * In this case, use a fee rate of 1 sat/VB for maximum flexibility
     *
     * @return the fee rate to use when constructing a transaction
     */
    public Double getUserFeeRate() {
        return (userFeeSet.get() ? Transaction.DEFAULT_MIN_RELAY_FEE : getFeeRate());
    }

    public Double getFeeRate() {
        if(targetBlocksField.isVisible()) {
            return getTargetBlocksFeeRates().get(getTargetBlocks());
        } else {
            return getFeeRangeRate();
        }
    }

    private Double getMinimumFeeRate() {
        Optional<Double> optMinFeeRate = getTargetBlocksFeeRates().values().stream().min(Double::compareTo);
        double minRate = optMinFeeRate.orElse(getFallbackFeeRate());
        Double userFeeRate = getFeeRate();
        if(userFeeRate != null) {
            minRate = Math.min(userFeeRate, minRate);
        }
        return Math.max(minRate, Transaction.DUST_RELAY_TX_FEE);
    }

    private Map<Date, Set<MempoolRateSize>> getMempoolHistogram() {
        return AppServices.getMempoolHistogram();
    }

    public boolean isBelowMinimumFeeRate() {
        return walletTransactionProperty.get() != null && walletTransactionProperty.get().getFeeRate() < AppServices.getMinimumRelayFeeRate();
    }

    public boolean isInsufficientFeeRate() {
        return getFeeValueSats() == null || getFeeValueSats() == 0;
    }

    private void setFeeRate(Double feeRateAmt) {
        UnitFormat format = Config.get().getUnitFormat() == null ? UnitFormat.DOT : Config.get().getUnitFormat();
        feeRate.setText(format.getCurrencyFormat().format(feeRateAmt) + (cpfpFeeRate.isVisible() ? "" : " sats/vB"));
        setFeeRatePriority(feeRateAmt);
    }

    private void setEffectiveFeeRate(WalletTransaction walletTransaction) {
        List<BlockTransaction> unconfirmedUtxoTxs = walletTransaction == null ? Collections.emptyList() :
                walletTransaction.getSelectedUtxos().keySet().stream().filter(ref -> ref.getHeight() <= 0)
                        .map(ref -> getWalletForm().getWallet().getWalletTransaction(ref.getHash()))
                        .filter(Objects::nonNull).distinct().collect(Collectors.toList());
        if(!unconfirmedUtxoTxs.isEmpty() && unconfirmedUtxoTxs.stream().allMatch(blkTx -> blkTx.getFee() != null && blkTx.getFee() > 0)) {
            long utxoTxFee = unconfirmedUtxoTxs.stream().mapToLong(BlockTransaction::getFee).sum();
            double utxoTxSize = unconfirmedUtxoTxs.stream().mapToDouble(blkTx -> blkTx.getTransaction().getVirtualSize()).sum();
            long thisFee = walletTransaction.getFee();
            double thisSize = walletTransaction.getTransaction().getVirtualSize();
            double thisRate = thisFee / thisSize;
            double effectiveRate = (utxoTxFee + thisFee) / (utxoTxSize + thisSize);
            if(thisRate > effectiveRate) {
                UnitFormat format = Config.get().getUnitFormat() == null ? UnitFormat.DOT : Config.get().getUnitFormat();
                String strEffectiveRate = format.getCurrencyFormat().format(effectiveRate);
                Tooltip tooltip = new Tooltip("CPFP (Child Pays For Parent)\n" + strEffectiveRate + " sats/vB effective rate");
                cpfpFeeRate.setTooltip(tooltip);
                cpfpFeeRate.setVisible(true);
                cpfpFeeRate.setText(strEffectiveRate + " sats/vB (CPFP)");
            } else {
                cpfpFeeRate.setVisible(false);
            }
        } else {
            cpfpFeeRate.setVisible(false);
        }
    }

    private void setFeeRatePriority(Double feeRateAmt) {
        feeRateAmt = Math.round(feeRateAmt * 100.0) / 100.0; // Round to 2 decimal places
        Map<Integer, Double> targetBlocksFeeRates = getTargetBlocksFeeRates();
        if(targetBlocksFeeRates.get(Integer.MAX_VALUE) != null) {
            Double minFeeRate = targetBlocksFeeRates.get(Integer.MAX_VALUE);
            if(feeRateAmt > 0.01 && feeRateAmt < minFeeRate) {
                feeRatePriority.setText("Below Minimum");
                feeRatePriority.setTooltip(new Tooltip("Transactions at this fee rate can be purged from the default sized mempool"));
                feeRatePriorityGlyph.setStyle("-fx-text-fill: #a0a1a7cc");
                feeRatePriorityGlyph.setIcon(FontAwesome5.Glyph.EXCLAMATION_CIRCLE);
                return;
            }

            Double lowestBlocksRate = targetBlocksFeeRates.get(TARGET_BLOCKS_RANGE.get(TARGET_BLOCKS_RANGE.size() - 1));
            if(lowestBlocksRate >= minFeeRate && feeRateAmt < (minFeeRate + ((lowestBlocksRate - minFeeRate) / 2)) && !isPayjoinTx()) {
                feeRatePriority.setText("Try Then Replace");
                feeRatePriority.setTooltip(new Tooltip("Send a transaction, verify it appears in the destination wallet, then RBF to get it confirmed or sent to another address"));
                feeRatePriorityGlyph.setStyle("-fx-text-fill: #7eb7c9cc");
                feeRatePriorityGlyph.setIcon(FontAwesome5.Glyph.PLUS_CIRCLE);
                return;
            }
        }

        Integer targetBlocks = getTargetBlocks(feeRateAmt);
        if(targetBlocks != null) {
            if(targetBlocks < FeeRatesSource.BLOCKS_IN_HALF_HOUR) {
                Double maxFeeRate = FEE_RATES_RANGE.get(FEE_RATES_RANGE.size() - 1).doubleValue();
                Double highestBlocksRate = targetBlocksFeeRates.get(TARGET_BLOCKS_RANGE.get(0));
                if(highestBlocksRate < maxFeeRate && feeRateAmt > (highestBlocksRate + ((maxFeeRate - highestBlocksRate) / 10))) {
                    feeRatePriority.setText("Overpaid");
                    feeRatePriority.setTooltip(new Tooltip("Transaction fees at this rate are likely higher than necessary"));
                    feeRatePriorityGlyph.setStyle("-fx-text-fill: #c8416499");
                    feeRatePriorityGlyph.setIcon(FontAwesome5.Glyph.EXCLAMATION_CIRCLE);
                } else {
                    feeRatePriority.setText("High Priority");
                    feeRatePriority.setTooltip(new Tooltip("Typically confirms within minutes"));
                    feeRatePriorityGlyph.setStyle("-fx-text-fill: #c8416499");
                    feeRatePriorityGlyph.setIcon(FontAwesome5.Glyph.CIRCLE);
                }
            } else if(targetBlocks < FeeRatesSource.BLOCKS_IN_HOUR) {
                feeRatePriority.setText("Medium Priority");
                feeRatePriority.setTooltip(new Tooltip("Typically confirms within an hour or two"));
                feeRatePriorityGlyph.setStyle("-fx-text-fill: #fba71b99");
                feeRatePriorityGlyph.setIcon(FontAwesome5.Glyph.CIRCLE);
            } else {
                feeRatePriority.setText("Low Priority");
                feeRatePriority.setTooltip(new Tooltip("Typically confirms in a day or longer"));
                feeRatePriorityGlyph.setStyle("-fx-text-fill: #41a9c999");
                feeRatePriorityGlyph.setIcon(FontAwesome5.Glyph.CIRCLE);
            }
        }
    }

    private boolean isPayjoinTx() {
        if(walletTransactionProperty.get() != null) {
            return walletTransactionProperty.get().getPayments().stream().anyMatch(payment -> AppServices.getPayjoinURI(payment.getAddress()) != null);
        }

        return false;
    }

    private Node getSliderThumb() {
        return targetBlocks.lookup(".thumb");
    }

    private void setFiatFeeAmount(CurrencyRate currencyRate, Long amount) {
        if(amount != null && currencyRate != null && currencyRate.isAvailable() && Config.get().getExchangeSource() != ExchangeSource.NONE) {
            fiatFeeAmount.set(currencyRate, amount);
        }
    }

    private void updateMaxClearButtons(UtxoSelector utxoSelector, TxoFilter txoFilter) {
        if(utxoSelector instanceof PresetUtxoSelector presetUtxoSelector) {
            int num = presetUtxoSelector.getPresetUtxos().size();
            String selection = " (" + num + " UTXO" + (num != 1 ? "s" : "") + " selected)";
            utxoLabelSelectionProperty.set(selection);
        } else if(txoFilter instanceof ExcludeTxoFilter excludeTxoFilter) {
            int num = excludeTxoFilter.getExcludedTxos().size();
            String exclusion = " (" + num + " UTXO" + (num != 1 ? "s" : "") + " excluded)";
            utxoLabelSelectionProperty.set(exclusion);
        } else {
            utxoLabelSelectionProperty.set("");
        }
    }

    private boolean isFakeMixPossible(List<Payment> payments) {
        return utxoSelectorProperty.get() == null && payments.size() == 1
                && (payments.get(0).getAddress().getScriptType() == getWalletForm().getWallet().getFreshNode(KeyPurpose.RECEIVE).getAddress().getScriptType())
                && AppServices.getPayjoinURI(payments.get(0).getAddress()) == null;
    }

    private void updateOptimizationButtons(List<Payment> payments) {
        if(isFakeMixPossible(payments)) {
            setPreferredOptimizationStrategy();
            efficiencyToggle.setDisable(false);
            privacyToggle.setDisable(false);
        } else {
            optimizationToggleGroup.selectToggle(efficiencyToggle);
            transactionDiagram.setOptimizationStrategy(OptimizationStrategy.EFFICIENCY);
            efficiencyToggle.setDisable(false);
            privacyToggle.setDisable(true);
        }
    }

    private OptimizationStrategy getPreferredOptimizationStrategy() {
        OptimizationStrategy optimizationStrategy = Config.get().getSendOptimizationStrategy();
        if(StandardAccount.isWhirlpoolMixAccount(getWalletForm().getWallet().getStandardAccountType()) && !overrideOptimizationStrategy) {
            optimizationStrategy = OptimizationStrategy.PRIVACY;
        }

        return optimizationStrategy;
    }

    private void setPreferredOptimizationStrategy() {
        OptimizationStrategy optimizationStrategy = getPreferredOptimizationStrategy();
        optimizationToggleGroup.selectToggle(optimizationStrategy == OptimizationStrategy.PRIVACY ? privacyToggle : efficiencyToggle);
        transactionDiagram.setOptimizationStrategy(optimizationStrategy);
    }

    private void updatePrivacyAnalysis(WalletTransaction walletTransaction) {
        if(walletTransaction == null) {
            privacyAnalysis.setVisible(false);
            privacyAnalysis.setTooltip(null);
        } else {
            privacyAnalysis.setVisible(true);
            Tooltip tooltip = new Tooltip();
            tooltip.setShowDelay(new Duration(50));
            tooltip.setShowDuration(Duration.INDEFINITE);
            tooltip.setGraphic(new PrivacyAnalysisTooltip(walletTransaction));
            privacyAnalysis.setTooltip(tooltip);
        }
    }

    public void clear(ActionEvent event) {
        boolean firstTab = true;
        for(Iterator<Tab> iterator = paymentTabs.getTabs().iterator(); iterator.hasNext(); ) {
            PaymentController controller = (PaymentController)iterator.next().getUserData();
            if(firstTab) {
                controller.clear();
                firstTab = false;
            } else {
                EventManager.get().unregister(controller);
                iterator.remove();
            }
        }

        fee.textProperty().removeListener(feeListener);
        fee.setText("");
        fee.textProperty().addListener(feeListener);

        cpfpFeeRate.setVisible(false);
        fiatFeeAmount.setText("");

        userFeeSet.set(false);
        setDefaultFeeRate();
        utxoSelectorProperty.setValue(null);
        txoFilterProperty.setValue(null);
        replacedTransactionProperty.setValue(null);
        opReturnsList.clear();
        excludedChangeNodes.clear();
        walletTransactionProperty.setValue(null);
        walletForm.setCreatedWalletTransaction(null);
        insufficientInputsProperty.set(false);

        validationSupport.setErrorDecorationEnabled(false);

        setInputFieldsDisabled(false, false);

        efficiencyToggle.setDisable(false);
        privacyToggle.setDisable(false);

        notificationButton.setVisible(false);
        createButton.setDefaultButton(true);

        paymentCodeProperty.set(null);

        addressNodeMap.clear();
    }

    public UtxoSelector getUtxoSelector() {
        return utxoSelectorProperty.get();
    }

    public ObjectProperty<UtxoSelector> utxoSelectorProperty() {
        return utxoSelectorProperty;
    }

    public boolean isInsufficientInputs() {
        return insufficientInputsProperty.get();
    }

    public BooleanProperty insufficientInputsProperty() {
        return insufficientInputsProperty;
    }

    public WalletTransaction getWalletTransaction() {
        return walletTransactionProperty.get();
    }

    public ObjectProperty<WalletTransaction> walletTransactionProperty() {
        return walletTransactionProperty;
    }

    public String getUtxoLabelSelection() {
        return utxoLabelSelectionProperty.get();
    }

    public StringProperty utxoLabelSelectionProperty() {
        return utxoLabelSelectionProperty;
    }

    public TabPane getPaymentTabs() {
        return paymentTabs;
    }

    public Button getCreateButton() {
        return createButton;
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

    public void createTransaction(ActionEvent event) {
        WalletTransaction walletTransaction = walletTransactionProperty.get();
        if(log.isDebugEnabled()) {
            Map<WalletNode, List<String>> inputHashes = new LinkedHashMap<>();
            for(WalletNode node : walletTransaction.getSelectedUtxos().values()) {
                List<String> nodeHashes = inputHashes.computeIfAbsent(node, k -> new ArrayList<>());
                nodeHashes.add(ElectrumServer.getScriptHash(node));
            }
            Map<WalletNode, List<String>> changeHash = new LinkedHashMap<>();
            for(WalletNode changeNode : walletTransaction.getChangeMap().keySet()) {
                changeHash.put(changeNode, List.of(ElectrumServer.getScriptHash(changeNode)));
            }
            log.debug("Creating tx " + walletTransaction.getTransaction().getTxId() + ", expecting notifications for \ninputs \n" + inputHashes + " and \nchange \n" + changeHash);
        }

        addWalletTransactionNodes();
        walletForm.setCreatedWalletTransaction(walletTransaction);
        PSBT psbt = walletTransaction.createPSBT();
        EventManager.get().post(new ViewPSBTEvent(createButton.getScene().getWindow(), walletTransaction.getPayments().get(0).getLabel(), null, psbt));
    }

    private void addWalletTransactionNodes() {
        WalletTransaction walletTransaction = walletTransactionProperty.get();
        Set<WalletNode> nodes = new LinkedHashSet<>(walletTransaction.getSelectedUtxos().values());
        nodes.addAll(walletTransaction.getChangeMap().keySet());
        Map<Address, WalletNode> addressNodeMap = walletTransaction.getAddressNodeMap();
        nodes.addAll(addressNodeMap.values().stream().filter(Objects::nonNull).collect(Collectors.toList()));

        //All wallet nodes applicable to this transaction are stored so when the subscription status for one is updated, the history for all can be fetched in one atomic update
        walletForm.addWalletTransactionNodes(nodes);
    }

    public void broadcastNotification(ActionEvent event) {
        Wallet wallet = getWalletForm().getWallet();
        Storage storage = AppServices.get().getOpenWallets().get(wallet);
        if(wallet.isEncrypted()) {
            WalletPasswordDialog dlg = new WalletPasswordDialog(wallet.getMasterName(), WalletPasswordDialog.PasswordRequirement.LOAD);
            dlg.initOwner(paymentTabs.getScene().getWindow());
            Optional<SecureString> password = dlg.showAndWait();
            if(password.isPresent()) {
                Storage.DecryptWalletService decryptWalletService = new Storage.DecryptWalletService(wallet.copy(), password.get());
                decryptWalletService.setOnSucceeded(workerStateEvent -> {
                    EventManager.get().post(new StorageEvent(storage.getWalletId(wallet), TimedEvent.Action.END, "Done"));
                    Wallet decryptedWallet = decryptWalletService.getValue();
                    broadcastNotification(decryptedWallet);
                    decryptedWallet.clearPrivate();
                });
                decryptWalletService.setOnFailed(workerStateEvent -> {
                    EventManager.get().post(new StorageEvent(storage.getWalletId(wallet), TimedEvent.Action.END, "Failed"));
                    AppServices.showErrorDialog("Incorrect Password", decryptWalletService.getException().getMessage());
                });
                EventManager.get().post(new StorageEvent(storage.getWalletId(wallet), TimedEvent.Action.START, "Decrypting wallet..."));
                decryptWalletService.start();
            }
        } else {
            broadcastNotification(wallet);
        }
    }

    public void broadcastNotification(Wallet decryptedWallet) {
        try {
            PaymentCode paymentCode = decryptedWallet.isMasterWallet() ? decryptedWallet.getPaymentCode() : decryptedWallet.getMasterWallet().getPaymentCode();
            PaymentCode externalPaymentCode = paymentCodeProperty.get();
            WalletTransaction walletTransaction = walletTransactionProperty.get();
            WalletNode input0Node = walletTransaction.getSelectedUtxos().entrySet().iterator().next().getValue();
            Keystore keystore = input0Node.getWallet().isNested() ? decryptedWallet.getChildWallet(input0Node.getWallet().getName()).getKeystores().get(0) : decryptedWallet.getKeystores().get(0);
            ECKey input0Key = keystore.getKey(input0Node);
            TransactionOutPoint input0Outpoint = walletTransaction.getTransaction().getInputs().iterator().next().getOutpoint();
            SecretPoint secretPoint = new SecretPoint(input0Key.getPrivKeyBytes(), externalPaymentCode.getNotificationKey().getPubKey());
            byte[] blindingMask = PaymentCode.getMask(secretPoint.ECDHSecretAsBytes(), input0Outpoint.bitcoinSerialize());
            byte[] blindedPaymentCode = PaymentCode.blind(paymentCode.getPayload(), blindingMask);

            List<UtxoSelector> utxoSelectors = List.of(new PresetUtxoSelector(walletTransaction.getSelectedUtxos().keySet(), true, false));
            Long userFee = userFeeSet.get() ? getFeeValueSats() : null;
            double feeRate = getUserFeeRate();
            Integer currentBlockHeight = AppServices.getCurrentBlockHeight();
            boolean groupByAddress = Config.get().isGroupByAddress();
            boolean includeMempoolOutputs = Config.get().isIncludeMempoolOutputs();

            WalletTransaction finalWalletTx = decryptedWallet.createWalletTransaction(utxoSelectors, getTxoFilters(), walletTransaction.getPayments(), List.of(blindedPaymentCode), excludedChangeNodes, feeRate, getMinimumFeeRate(), userFee, currentBlockHeight, groupByAddress, includeMempoolOutputs);
            PSBT psbt = finalWalletTx.createPSBT();
            decryptedWallet.sign(psbt);
            decryptedWallet.finalise(psbt);
            Transaction transaction = psbt.extractTransaction();

            ServiceProgressDialog.ProxyWorker proxyWorker = new ServiceProgressDialog.ProxyWorker();
            ElectrumServer.BroadcastTransactionService broadcastTransactionService = new ElectrumServer.BroadcastTransactionService(transaction, psbt.getFee());
            broadcastTransactionService.setOnSucceeded(successEvent -> {
                ElectrumServer.TransactionMempoolService transactionMempoolService = new ElectrumServer.TransactionMempoolService(walletTransaction.getWallet(), transaction.getTxId(), new HashSet<>(walletTransaction.getSelectedUtxos().values()));
                transactionMempoolService.setDelay(Duration.seconds(2));
                transactionMempoolService.setPeriod(Duration.seconds(5));
                transactionMempoolService.setRestartOnFailure(false);
                transactionMempoolService.setOnSucceeded(mempoolWorkerStateEvent -> {
                    Set<String> scriptHashes = transactionMempoolService.getValue();
                    if(!scriptHashes.isEmpty()) {
                        transactionMempoolService.cancel();
                        clear(null);
                        if(Config.get().isUsePayNym()) {
                            proxyWorker.setMessage("Finding PayNym...");
                            PayNymService.getPayNym(externalPaymentCode.toString()).subscribe(payNym -> {
                                proxyWorker.end();
                                addChildWallets(walletTransaction.getWallet(), externalPaymentCode, transaction, payNym);
                            }, error -> {
                                proxyWorker.end();
                                addChildWallets(walletTransaction.getWallet(), externalPaymentCode, transaction, null);
                            });
                        } else {
                            proxyWorker.end();
                            addChildWallets(walletTransaction.getWallet(), externalPaymentCode, transaction, null);
                        }
                    }

                    if(transactionMempoolService.getIterationCount() > 5 && transactionMempoolService.isRunning()) {
                        transactionMempoolService.cancel();
                        proxyWorker.end();
                        log.error("Timeout searching for broadcasted notification transaction");
                        AppServices.showErrorDialog("Timeout searching for broadcasted transaction", "The transaction was broadcast but the server did not register it in the mempool. It is safe to try broadcasting again.");
                    }
                });
                transactionMempoolService.setOnFailed(mempoolWorkerStateEvent -> {
                    transactionMempoolService.cancel();
                    proxyWorker.end();
                    log.error("Error searching for broadcasted notification transaction", mempoolWorkerStateEvent.getSource().getException());
                    AppServices.showErrorDialog("Timeout searching for broadcasted transaction", "The transaction was broadcast but the server did not register it in the mempool. It is safe to try broadcasting again.");
                });
                proxyWorker.setMessage("Receiving notification transaction...");
                transactionMempoolService.start();
            });
            broadcastTransactionService.setOnFailed(failedEvent -> {
                proxyWorker.end();
                log.error("Error broadcasting notification transaction", failedEvent.getSource().getException());
                AppServices.showErrorDialog("Error broadcasting notification transaction", failedEvent.getSource().getException().getMessage());
            });
            ServiceProgressDialog progressDialog = new ServiceProgressDialog("Broadcast", "Broadcast Notification Transaction", new DialogImage(DialogImage.Type.PAYNYM), proxyWorker);
            progressDialog.initOwner(notificationButton.getScene().getWindow());
            AppServices.moveToActiveWindowScreen(progressDialog);
            proxyWorker.setMessage("Broadcasting notification transaction...");
            proxyWorker.start();
            broadcastTransactionService.start();
        } catch(Exception e) {
            log.error("Error creating notification transaction", e);
            AppServices.showErrorDialog("Error creating notification transaction", e.getMessage());
        }
    }

    private void addChildWallets(Wallet wallet, PaymentCode externalPaymentCode, Transaction transaction, PayNym payNym) {
        List<Wallet> addedWallets = addChildWallets(externalPaymentCode, payNym);
        Wallet masterWallet = getWalletForm().getMasterWallet();
        Storage storage = AppServices.get().getOpenWallets().get(masterWallet);
        EventManager.get().post(new ChildWalletsAddedEvent(storage, masterWallet, addedWallets));

        BlockTransaction blockTransaction = wallet.getWalletTransaction(transaction.getTxId());
        if(blockTransaction != null && blockTransaction.getLabel() == null) {
            blockTransaction.setLabel("Link " + (payNym == null ? externalPaymentCode.toAbbreviatedString() : payNym.nymName()));
            TransactionEntry transactionEntry = new TransactionEntry(wallet, blockTransaction, Collections.emptyMap(), Collections.emptyMap());
            EventManager.get().post(new WalletEntryLabelsChangedEvent(wallet, List.of(transactionEntry)));
        }

        if(paymentTabs.getTabs().size() > 0 && !addedWallets.isEmpty()) {
            Wallet addedWallet = addedWallets.stream().filter(w -> w.getScriptType() == ScriptType.P2WPKH).findFirst().orElse(addedWallets.iterator().next());
            PaymentController controller = (PaymentController)paymentTabs.getTabs().get(0).getUserData();
            controller.setPayNym(payNym == null ? PayNym.fromWallet(addedWallet) : payNym);
        }

        Glyph successGlyph = new Glyph(FontAwesome5.FONT_NAME, FontAwesome5.Glyph.CHECK_CIRCLE);
        successGlyph.getStyleClass().add("success");
        successGlyph.setFontSize(50);

        AppServices.showAlertDialog("Notification Successful", "The notification transaction was successfully sent for payment code " +
                externalPaymentCode.toAbbreviatedString() + (payNym == null ? "" : " (" + payNym.nymName() + ")") +
                ".\n\nYou can send to it by entering the payment code, or selecting `PayNym or Payment code` in the Pay to dropdown.", Alert.AlertType.INFORMATION, successGlyph, ButtonType.OK);
    }

    public List<Wallet> addChildWallets(PaymentCode externalPaymentCode, PayNym payNym) {
        List<Wallet> addedWallets = new ArrayList<>();
        Wallet masterWallet = getWalletForm().getMasterWallet();
        Storage storage = AppServices.get().getOpenWallets().get(masterWallet);
        List<ScriptType> scriptTypes = PayNym.getSegwitScriptTypes();
        for(ScriptType childScriptType : scriptTypes) {
            String label = (payNym == null ? externalPaymentCode.toAbbreviatedString() : payNym.nymName()) + " " + childScriptType.getName();
            Wallet addedWallet = masterWallet.addChildWallet(externalPaymentCode, childScriptType, label);
            if(!storage.isPersisted(addedWallet)) {
                try {
                    storage.saveWallet(addedWallet);
                    EventManager.get().post(new NewChildWalletSavedEvent(storage, masterWallet, addedWallet));
                } catch(Exception e) {
                    log.error("Error saving wallet", e);
                    AppServices.showErrorDialog("Error saving wallet " + addedWallet.getName(), e.getMessage());
                }
            }
            addedWallets.add(addedWallet);
        }

        return addedWallets;
    }

    private void setInputFieldsDisabled(boolean disablePayments, boolean disableFeeSelection) {
        for(int i = 0; i < paymentTabs.getTabs().size(); i++) {
            Tab tab = paymentTabs.getTabs().get(i);
            tab.setClosable(!disablePayments);
            PaymentController controller = (PaymentController)tab.getUserData();
            controller.setInputFieldsDisabled(disablePayments);
        }

        feeRange.setDisable(disableFeeSelection);
        targetBlocks.setDisable(disableFeeSelection);
        fee.setDisable(disableFeeSelection);
        feeAmountUnit.setDisable(disableFeeSelection);

        transactionDiagram.requestFocus();
    }

    @Subscribe
    public void walletNodesChanged(WalletNodesChangedEvent event) {
        if(event.getWallet().equals(walletForm.getWallet())) {
            clear(null);
        }
    }

    @Subscribe
    public void walletHistoryChanged(WalletHistoryChangedEvent event) {
        if(event.fromThisOrNested(walletForm.getWallet()) && walletForm.getCreatedWalletTransaction() != null) {
            if(walletForm.getCreatedWalletTransaction().getSelectedUtxos() != null && allSelectedUtxosSpent(event.getAllHistoryChangedNodes())) {
                clear(null);
            } else {
                updateTransaction();
            }
        }
    }

    private boolean allSelectedUtxosSpent(List<WalletNode> historyChangedNodes) {
        Set<BlockTransactionHashIndex> unspentUtxos = new HashSet<>(walletForm.getCreatedWalletTransaction().getSelectedUtxos().keySet());

        for(Map.Entry<BlockTransactionHashIndex, WalletNode> selectedUtxoEntry : walletForm.getCreatedWalletTransaction().getSelectedUtxos().entrySet()) {
            BlockTransactionHashIndex utxo = selectedUtxoEntry.getKey();
            WalletNode utxoWalletNode = selectedUtxoEntry.getValue();

            for(WalletNode changedNode : historyChangedNodes) {
                if(utxoWalletNode.equals(changedNode)) {
                    Optional<BlockTransactionHashIndex> spentTxo = changedNode.getTransactionOutputs().stream().filter(txo -> txo.getHash().equals(utxo.getHash()) && txo.getIndex() == utxo.getIndex() && txo.isSpent()).findAny();
                    if(spentTxo.isPresent()) {
                        unspentUtxos.remove(utxo);
                    }
                }
            }
        }

        return unspentUtxos.isEmpty();
    }

    @Subscribe
    public void walletEntryLabelChanged(WalletEntryLabelsChangedEvent event) {
        if(event.fromThisOrNested(walletForm.getWallet())) {
            updateTransaction();
        }
    }

    @Subscribe
    public void feeRatesUpdated(FeeRatesUpdatedEvent event) {
        blockTargetFeeRatesChart.update(event.getTargetBlockFeeRates());
        blockTargetFeeRatesChart.select(getTargetBlocks());
        if(targetBlocksField.isVisible()) {
            setFeeRate(event.getTargetBlockFeeRates().get(getTargetBlocks()));
        } else {
            setFeeRatePriority(getFeeRangeRate());
        }
        feeRange.updateTrackHighlight();

        if(event.getNextBlockMedianFeeRate() != null) {
            recentBlocksView.updateFeeRate(event.getNextBlockMedianFeeRate());
        } else {
            recentBlocksView.updateFeeRate(event.getTargetBlockFeeRates());
        }

        if(updateDefaultFeeRate) {
            if(getFeeRate() != null && Long.valueOf((long)getFallbackFeeRate()).equals(getFeeRate().longValue())) {
                setDefaultFeeRate();
            }
            updateDefaultFeeRate = false;
        }
    }

    @Subscribe
    public void mempoolRateSizesUpdated(MempoolRateSizesUpdatedEvent event) {
        mempoolSizeFeeRatesChart.update(getMempoolHistogram());
    }

    @Subscribe
    public void feeRateSelectionChanged(FeeRatesSelectionChangedEvent event) {
        if(event.getWallet() == getWalletForm().getWallet()) {
            feeRatesSelectionProperty.set(event.getFeeRateSelection());
        }
    }

    @Subscribe
    public void blockSummary(BlockSummaryEvent event) {
        Platform.runLater(() -> recentBlocksView.update(AppServices.getBlockSummaries().values().stream().sorted().toList(), AppServices.getNextBlockMedianFeeRate()));
    }

    @Subscribe
    public void spendUtxos(SpendUtxoEvent event) {
        if((event.getUtxos() == null || !event.getUtxos().isEmpty()) && event.getWallet().equals(getWalletForm().getWallet())) {
            if(paymentCodeProperty.get() != null) {
                clear(null);
            }

            if(event.getPayments() != null) {
                clear(null);
                setPayments(event.getPayments());
            } else if(paymentTabs.getTabs().size() == 1 && event.getUtxos() != null) {
                Payment payment = new Payment(null, null, event.getUtxos().stream().mapToLong(BlockTransactionHashIndex::getValue).sum(), true);
                setPayments(List.of(payment));
            }

            if(event.getOpReturns() != null) {
                opReturnsList.addAll(event.getOpReturns());
            }

            if(event.getFee() != null) {
                setFeeValueSats(event.getFee());
                userFeeSet.set(true);
            }

            replacedTransactionProperty.set(event.getReplacedTransaction());

            if(event.getUtxos() != null) {
                List<BlockTransactionHashIndex> utxos = event.getUtxos();
                utxoSelectorProperty.set(new PresetUtxoSelector(utxos, false, event.isRequireAllUtxos()));
            }

            txoFilterProperty.set(null);
            paymentCodeProperty.set(event.getPaymentCode());
            updateTransaction(event.getPayments() == null || event.getPayments().stream().anyMatch(Payment::isSendMax));

            boolean isNotificationTransaction = (event.getPaymentCode() != null);
            notificationButton.setVisible(isNotificationTransaction);
            notificationButton.setDefaultButton(isNotificationTransaction);

            setInputFieldsDisabled(isNotificationTransaction, false);
        }
    }

    @Subscribe
    public void sendPayments(SendPaymentsEvent event) {
        if(event.getWallet().equals(getWalletForm().getWallet())) {
            if(event.getPayments() != null) {
                clear(null);
                Platform.runLater(() -> {
                    setPayments(event.getPayments());
                    updateTransaction(event.getPayments() == null || event.getPayments().stream().anyMatch(Payment::isSendMax));
                });
            }
        }
    }

    @Subscribe
    public void bitcoinUnitChanged(BitcoinUnitChangedEvent event) {
        BitcoinUnit unit = getBitcoinUnit(event.getBitcoinUnit());
        feeAmountUnit.getSelectionModel().select(BitcoinUnit.BTC.equals(unit) ? 0 : 1);
    }

    @Subscribe
    public void unitFormatChanged(UnitFormatChangedEvent event) {
        setEffectiveFeeRate(getWalletTransaction());
        setFeeRate(getFeeRate());
        if(fee.getTextFormatter() instanceof CoinTextFormatter coinTextFormatter && coinTextFormatter.getUnitFormat() != event.getUnitFormat()) {
            Long value = getFeeValueSats(coinTextFormatter.getUnitFormat(), feeAmountUnit.getSelectionModel().getSelectedItem());
            fee.setTextFormatter(new CoinTextFormatter(event.getUnitFormat()));

            if(value != null) {
                setFeeValueSats(value);
            }
        }
        fiatFeeAmount.refresh(event.getUnitFormat());
    }

    @Subscribe
    public void fiatCurrencySelected(FiatCurrencySelectedEvent event) {
        if(event.getExchangeSource() == ExchangeSource.NONE) {
            fiatFeeAmount.setCurrency(null);
            fiatFeeAmount.setBtcRate(0.0);
            if(paymentTabs.getTabs().size() > 1) {
                updateTransaction();
            }
        }
    }

    @Subscribe
    public void exchangeRatesUpdated(ExchangeRatesUpdatedEvent event) {
        setFiatFeeAmount(event.getCurrencyRate(), getFeeValueSats());
        if(paymentTabs.getTabs().size() > 1) {
            updateTransaction();
        }
    }

    @Subscribe
    public void excludeUtxo(ExcludeUtxoEvent event) {
        if(event.getWalletTransaction() == walletTransactionProperty.get()) {
            UtxoSelector utxoSelector = utxoSelectorProperty.get();
            if(utxoSelector instanceof MaxUtxoSelector) {
                Collection<BlockTransactionHashIndex> utxos = event.getWalletTransaction().getSelectedUtxos().keySet();
                utxos.remove(event.getUtxo());
                PresetUtxoSelector presetUtxoSelector = new PresetUtxoSelector(utxos);
                presetUtxoSelector.getExcludedUtxos().add(event.getUtxo());
                utxoSelectorProperty.set(presetUtxoSelector);
                updateTransaction(true);
            } else if(utxoSelector instanceof PresetUtxoSelector existingUtxoSelector) {
                PresetUtxoSelector presetUtxoSelector = new PresetUtxoSelector(existingUtxoSelector.getPresetUtxos(), existingUtxoSelector.getExcludedUtxos());
                presetUtxoSelector.getPresetUtxos().remove(event.getUtxo());
                presetUtxoSelector.getExcludedUtxos().add(event.getUtxo());
                utxoSelectorProperty.set(presetUtxoSelector);
                updateTransaction(replacedTransactionProperty.get() == null);
            } else {
                ExcludeTxoFilter excludeTxoFilter = new ExcludeTxoFilter();
                if(txoFilterProperty.get() instanceof ExcludeTxoFilter existingTxoFilter) {
                    excludeTxoFilter.getExcludedTxos().addAll(existingTxoFilter.getExcludedTxos());
                }

                excludeTxoFilter.getExcludedTxos().add(event.getUtxo());
                txoFilterProperty.set(excludeTxoFilter);
                updateTransaction();
            }
        }
    }

    @Subscribe
    public void replaceChangeAddress(ReplaceChangeAddressEvent event) {
        if(event.getWalletTransaction() == walletTransactionProperty.get()) {
            excludedChangeNodes.addAll(event.getWalletTransaction().getChangeMap().keySet());
            updateTransaction();
        }
    }

    @Subscribe
    public void walletUtxoStatusChanged(WalletUtxoStatusChangedEvent event) {
        if(event.fromThisOrNested(getWalletForm().getWallet())) {
            UtxoSelector utxoSelector = utxoSelectorProperty.get();
            if(utxoSelector instanceof MaxUtxoSelector) {
                updateTransaction(true);
            } else if(utxoSelectorProperty().get() instanceof PresetUtxoSelector) {
                PresetUtxoSelector presetUtxoSelector = new PresetUtxoSelector(((PresetUtxoSelector)utxoSelector).getPresetUtxos());
                presetUtxoSelector.getPresetUtxos().removeAll(event.getUtxos());
                utxoSelectorProperty.set(presetUtxoSelector);
                updateTransaction(true);
            } else {
                updateTransaction();
            }
        }
    }

    @Subscribe
    public void includeMempoolOutputsChangedEvent(IncludeMempoolOutputsChangedEvent event) {
        updateTransaction();
    }

    @Subscribe
    public void newBlock(NewBlockEvent event) {
        if(cpfpFeeRate.isVisible()) {
            updateTransaction();
        }
    }

    @Subscribe
    public void feeRateSourceChanged(FeeRatesSourceChangedEvent event) {
        recentBlocksView.updateFeeRatesSource(event.getFeeRateSource());
    }

    private class PrivacyAnalysisTooltip extends VBox {
        private final List<Label> analysisLabels = new ArrayList<>();

        public PrivacyAnalysisTooltip(WalletTransaction walletTransaction) {
            List<Payment> payments = walletTransaction.getPayments();
            List<Payment> userPayments = payments.stream().filter(payment -> payment.getType() != Payment.Type.FAKE_MIX).collect(Collectors.toList());
            Map<Address, WalletNode> walletAddresses = walletTransaction.getAddressNodeMap();
            OptimizationStrategy optimizationStrategy = getPreferredOptimizationStrategy();
            boolean fakeMixPresent = payments.stream().anyMatch(payment -> payment.getType() == Payment.Type.FAKE_MIX);
            boolean roundPaymentAmounts = userPayments.stream().anyMatch(payment -> payment.getAmount() % 100 == 0);
            boolean mixedAddressTypes = userPayments.stream().anyMatch(payment -> payment.getAddress().getScriptType() != getWalletForm().getWallet().getFreshNode(KeyPurpose.RECEIVE).getAddress().getScriptType());
            boolean addressReuse = userPayments.stream().anyMatch(payment -> walletAddresses.get(payment.getAddress()) != null && !walletAddresses.get(payment.getAddress()).getTransactionOutputs().isEmpty());
            boolean payjoinPresent = userPayments.stream().anyMatch(payment -> AppServices.getPayjoinURI(payment.getAddress()) != null);

            if(optimizationStrategy == OptimizationStrategy.PRIVACY) {
                if(fakeMixPresent) {
                    addLabel("Appears as a two person coinjoin", getPlusGlyph());
                } else {
                    if(mixedAddressTypes) {
                        addLabel("Cannot fake coinjoin due to mixed address types", getInfoGlyph());
                    } else if(userPayments.size() > 1) {
                        addLabel("Cannot fake coinjoin due to multiple payments", getInfoGlyph());
                    } else if(payjoinPresent) {
                        addLabel("Cannot fake coinjoin due to payjoin", getInfoGlyph());
                    } else {
                        if(utxoSelectorProperty().get() instanceof MaxUtxoSelector) {
                            addLabel("Cannot fake coinjoin with max amount selected", getInfoGlyph());
                        } else if(utxoSelectorProperty().get() != null) {
                            addLabel("Cannot fake coinjoin due to coin control", getInfoGlyph());
                        } else {
                            addLabel("Cannot fake coinjoin due to insufficient funds", getInfoGlyph());
                        }
                    }
                }
            }

            if(mixedAddressTypes) {
                addLabel("Address types different to the wallet indicate external payments", getMinusGlyph());
            }

            if(roundPaymentAmounts && !fakeMixPresent) {
                addLabel("Rounded payment amounts indicate external payments", getMinusGlyph());
            }

            if(addressReuse) {
                addLabel("Address reuse detected", getMinusGlyph());
            }

            if(!fakeMixPresent && !mixedAddressTypes && !roundPaymentAmounts) {
                addLabel("Appears as a possible self transfer", getPlusGlyph());
            }

            analysisLabels.sort(Comparator.comparingInt(o -> (Integer)o.getGraphic().getUserData()));
            getChildren().addAll(analysisLabels);
            setSpacing(5);
        }

        private void addLabel(String text, Node graphic) {
            Label label = new Label(text);
            label.setStyle("-fx-font-size: 11px");
            label.setGraphic(graphic);
            analysisLabels.add(label);
        }

        private static Glyph getPlusGlyph() {
            Glyph plusGlyph = new Glyph(FontAwesome5.FONT_NAME, FontAwesome5.Glyph.PLUS_CIRCLE);
            plusGlyph.setUserData(0);
            plusGlyph.setStyle("-fx-text-fill: rgb(80, 161, 79)");
            plusGlyph.setFontSize(12);
            return plusGlyph;
        }

        private static Glyph getWarningGlyph() {
            Glyph warningGlyph = new Glyph(FontAwesome5.FONT_NAME, FontAwesome5.Glyph.EXCLAMATION_TRIANGLE);
            warningGlyph.setUserData(1);
            warningGlyph.setStyle("-fx-text-fill: rgb(238, 210, 2)");
            warningGlyph.setFontSize(12);
            return warningGlyph;
        }

        private static Glyph getMinusGlyph() {
            Glyph minusGlyph = new Glyph(FontAwesome5.FONT_NAME, FontAwesome5.Glyph.MINUS_CIRCLE);
            minusGlyph.setUserData(2);
            minusGlyph.setStyle("-fx-text-fill: #e06c75");
            minusGlyph.setFontSize(12);
            return minusGlyph;
        }

        private static Glyph getInfoGlyph() {
            Glyph infoGlyph = new Glyph(FontAwesome5.FONT_NAME, FontAwesome5.Glyph.INFO_CIRCLE);
            infoGlyph.setUserData(3);
            infoGlyph.setStyle("-fx-text-fill: -fx-accent");
            infoGlyph.setFontSize(12);
            return infoGlyph;
        }
    }
}
