package com.sparrowwallet.sparrow.wallet;

import com.google.common.eventbus.Subscribe;
import com.sparrowwallet.drongo.BitcoinUnit;
import com.sparrowwallet.drongo.address.InvalidAddressException;
import com.sparrowwallet.drongo.protocol.Transaction;
import com.sparrowwallet.drongo.psbt.PSBT;
import com.sparrowwallet.drongo.wallet.*;
import com.sparrowwallet.sparrow.AppServices;
import com.sparrowwallet.sparrow.CurrencyRate;
import com.sparrowwallet.sparrow.EventManager;
import com.sparrowwallet.sparrow.control.*;
import com.sparrowwallet.sparrow.event.*;
import com.sparrowwallet.sparrow.glyphfont.FontAwesome5;
import com.sparrowwallet.sparrow.io.Config;
import com.sparrowwallet.sparrow.net.*;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.beans.property.*;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
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
import java.text.DecimalFormatSymbols;
import java.util.*;
import java.util.stream.Collectors;

public class SendController extends WalletFormController implements Initializable {
    private static final Logger log = LoggerFactory.getLogger(SendController.class);

    public static final List<Integer> TARGET_BLOCKS_RANGE = List.of(1, 2, 3, 4, 5, 10, 25, 50);
    public static final List<Long> FEE_RATES_RANGE = List.of(1L, 2L, 4L, 8L, 16L, 32L, 64L, 128L, 256L, 512L, 1024L);

    public static final double FALLBACK_FEE_RATE = 20000d / 1000;

    @FXML
    private TabPane paymentTabs;

    @FXML
    private ToggleGroup feeSelectionToggleGroup;

    @FXML
    private ToggleButton targetBlocksToggle;

    @FXML
    private ToggleButton mempoolSizeToggle;

    @FXML
    private Field targetBlocksField;

    @FXML
    private Slider targetBlocks;

    @FXML
    private Field feeRangeField;

    @FXML
    private Slider feeRange;

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
    private TransactionDiagram transactionDiagram;

    @FXML
    private Button clearButton;

    @FXML
    private Button createButton;

    private StackPane tabHeader;

    private final BooleanProperty userFeeSet = new SimpleBooleanProperty(false);

    private final ObjectProperty<UtxoSelector> utxoSelectorProperty = new SimpleObjectProperty<>(null);

    private final ObjectProperty<UtxoFilter> utxoFilterProperty = new SimpleObjectProperty<>(null);

    private final ObjectProperty<WalletTransaction> walletTransactionProperty = new SimpleObjectProperty<>(null);

    private final ObjectProperty<WalletTransaction> createdWalletTransactionProperty = new SimpleObjectProperty<>(null);

    private final BooleanProperty insufficientInputsProperty = new SimpleBooleanProperty(false);

    private final StringProperty utxoLabelSelectionProperty = new SimpleStringProperty("");

    private final BooleanProperty includeSpentMempoolOutputsProperty = new SimpleBooleanProperty(false);

    private final List<WalletNode> excludedChangeNodes = new ArrayList<>();

    private final ChangeListener<String> feeListener = new ChangeListener<>() {
        @Override
        public void changed(ObservableValue<? extends String> observable, String oldValue, String newValue) {
            userFeeSet.set(true);
            if(newValue.isEmpty()) {
                fiatFeeAmount.setText("");
            } else {
                setFiatFeeAmount(AppServices.getFiatCurrencyExchangeRate(), getFeeValueSats());
            }

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
                controller.revalidate();
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
                controller.revalidate();
            }
            updateTransaction();
        }
    };

    private ValidationSupport validationSupport;

    private WalletTransactionService walletTransactionService;

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
                paymentTabs.getTabs().forEach(tab -> tab.setClosable(true));
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
                controller.revalidate();
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
        feeRange.setMin(0);
        feeRange.setMax(FEE_RATES_RANGE.size() - 1);
        feeRange.setMajorTickUnit(1);
        feeRange.setMinorTickCount(0);
        feeRange.setLabelFormatter(new StringConverter<Double>() {
            @Override
            public String toString(Double object) {
                return Long.toString(FEE_RATES_RANGE.get(object.intValue()));
            }

            @Override
            public Double fromString(String string) {
                return null;
            }
        });
        feeRange.valueProperty().addListener(feeRangeListener);

        blockTargetFeeRatesChart.managedProperty().bind(blockTargetFeeRatesChart.visibleProperty());
        blockTargetFeeRatesChart.initialize();
        Map<Integer, Double> targetBlocksFeeRates = getTargetBlocksFeeRates();
        if(targetBlocksFeeRates != null) {
            blockTargetFeeRatesChart.update(targetBlocksFeeRates);
        } else {
            feeRate.setText("Unknown");
        }

        mempoolSizeFeeRatesChart.managedProperty().bind(mempoolSizeFeeRatesChart.visibleProperty());
        mempoolSizeFeeRatesChart.visibleProperty().bind(blockTargetFeeRatesChart.visibleProperty().not());
        mempoolSizeFeeRatesChart.initialize();
        Map<Date, Set<MempoolRateSize>> mempoolHistogram = getMempoolHistogram();
        if(mempoolHistogram != null) {
            mempoolSizeFeeRatesChart.update(mempoolHistogram);
        }

        FeeRatesSelection feeRatesSelection = Config.get().getFeeRatesSelection();
        feeRatesSelection = (feeRatesSelection == null ? FeeRatesSelection.MEMPOOL_SIZE : feeRatesSelection);
        cpfpFeeRate.managedProperty().bind(cpfpFeeRate.visibleProperty());
        cpfpFeeRate.setVisible(false);
        setDefaultFeeRate();
        updateFeeRateSelection(feeRatesSelection);
        feeSelectionToggleGroup.selectToggle(feeRatesSelection == FeeRatesSelection.BLOCK_TARGET ? targetBlocksToggle : mempoolSizeToggle);
        feeSelectionToggleGroup.selectedToggleProperty().addListener((observable, oldValue, newValue) -> {
            if(newValue != null) {
                FeeRatesSelection newFeeRatesSelection = (FeeRatesSelection)newValue.getUserData();
                Config.get().setFeeRatesSelection(newFeeRatesSelection);
                EventManager.get().post(new FeeRatesSelectionChangedEvent(getWalletForm().getWallet(), newFeeRatesSelection));
            };
        });

        fee.setTextFormatter(new CoinTextFormatter());
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
            updateMaxClearButtons(utxoSelector, utxoFilterProperty.get());
        });

        utxoFilterProperty.addListener((observable, oldValue, utxoFilter) -> {
            updateMaxClearButtons(utxoSelectorProperty.get(), utxoFilter);
        });

        walletTransactionProperty.addListener((observable, oldValue, walletTransaction) -> {
            if(walletTransaction != null) {
                setPayments(walletTransaction.getPayments());

                double feeRate = walletTransaction.getFeeRate();
                if(userFeeSet.get()) {
                    setTargetBlocks(getTargetBlocks(feeRate));
                    setFeeRangeRate(feeRate);
                } else {
                    setFeeValueSats(walletTransaction.getFee());
                }

                setFeeRate(feeRate);
                setEffectiveFeeRate(walletTransaction);
            }

            transactionDiagram.update(walletTransaction);
            createButton.setDisable(walletTransaction == null || isInsufficientFeeRate());
        });

        transactionDiagram.sceneProperty().addListener((observable, oldScene, newScene) -> {
            if(oldScene == null && newScene != null) {
                transactionDiagram.update(walletTransactionProperty.get());
                newScene.getWindow().heightProperty().addListener((observable1, oldValue, newValue) -> {
                    transactionDiagram.update(walletTransactionProperty.get());
                });
            }
        });

        addFeeRangeTrackHighlight(0);
    }

    private void initializeTabHeader(int count) {
        final int lookupCount = count;
        Platform.runLater(() -> {
            StackPane stackPane = (StackPane)paymentTabs.lookup(".tab-header-area");
            if(stackPane != null) {
                tabHeader = stackPane;
                tabHeader.managedProperty().bind(tabHeader.visibleProperty());
                tabHeader.setVisible(false);
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
                (Control c, String newValue) -> ValidationResult.fromErrorIf( c, "Insufficient Fee", getFeeValueSats() != null && getFeeValueSats() == 0),
                (Control c, String newValue) -> ValidationResult.fromErrorIf( c, "Insufficient Fee Rate", isInsufficientFeeRate())
        ));

        validationSupport.setErrorDecorationEnabled(false);
    }

    public Tab addPaymentTab() {
        Tab tab = getPaymentTab();
        paymentTabs.getTabs().add(tab);
        paymentTabs.getSelectionModel().select(tab);
        return tab;
    }

    public Tab getPaymentTab() {
        OptionalInt highestTabNo = paymentTabs.getTabs().stream().mapToInt(tab -> Integer.parseInt(tab.getText())).max();
        Tab tab = new Tab(Integer.toString(highestTabNo.isPresent() ? highestTabNo.getAsInt() + 1 : 1));

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
            if(!userFeeSet.get() || (getFeeValueSats() != null && getFeeValueSats() > 0)) {
                Wallet wallet = getWalletForm().getWallet();
                Long userFee = userFeeSet.get() ? getFeeValueSats() : null;
                double feeRate = getUserFeeRate();
                Integer currentBlockHeight = AppServices.getCurrentBlockHeight();
                boolean groupByAddress = Config.get().isGroupByAddress();
                boolean includeMempoolOutputs = Config.get().isIncludeMempoolOutputs();
                boolean includeSpentMempoolOutputs = includeSpentMempoolOutputsProperty.get();

                walletTransactionService = new WalletTransactionService(wallet, getUtxoSelectors(), getUtxoFilters(), payments, excludedChangeNodes, feeRate, getMinimumFeeRate(), userFee, currentBlockHeight, groupByAddress, includeMempoolOutputs, includeSpentMempoolOutputs);
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
                        transactionDiagram.update("Selecting UTXOs...");
                        createButton.setDisable(true);
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

    private List<UtxoSelector> getUtxoSelectors() throws InvalidAddressException {
        if(utxoSelectorProperty.get() != null) {
            return List.of(utxoSelectorProperty.get());
        }

        Wallet wallet = getWalletForm().getWallet();
        long noInputsFee = wallet.getNoInputsFee(getPayments(), getUserFeeRate());
        long costOfChange = wallet.getCostOfChange(getUserFeeRate(), getMinimumFeeRate());

        return List.of(new BnBUtxoSelector(noInputsFee, costOfChange), new KnapsackUtxoSelector(noInputsFee));
    }

    private static class WalletTransactionService extends Service<WalletTransaction> {
        private final Wallet wallet;
        private final List<UtxoSelector> utxoSelectors;
        private final List<UtxoFilter> utxoFilters;
        private final List<Payment> payments;
        private final List<WalletNode> excludedChangeNodes;
        private final double feeRate;
        private final double longTermFeeRate;
        private final Long fee;
        private final Integer currentBlockHeight;
        private final boolean groupByAddress;
        private final boolean includeMempoolOutputs;
        private final boolean includeSpentMempoolOutputs;
        private boolean ignoreResult;

        public WalletTransactionService(Wallet wallet, List<UtxoSelector> utxoSelectors, List<UtxoFilter> utxoFilters, List<Payment> payments, List<WalletNode> excludedChangeNodes, double feeRate, double longTermFeeRate, Long fee, Integer currentBlockHeight, boolean groupByAddress, boolean includeMempoolOutputs, boolean includeSpentMempoolOutputs) {
            this.wallet = wallet;
            this.utxoSelectors = utxoSelectors;
            this.utxoFilters = utxoFilters;
            this.payments = payments;
            this.excludedChangeNodes = excludedChangeNodes;
            this.feeRate = feeRate;
            this.longTermFeeRate = longTermFeeRate;
            this.fee = fee;
            this.currentBlockHeight = currentBlockHeight;
            this.groupByAddress = groupByAddress;
            this.includeMempoolOutputs = includeMempoolOutputs;
            this.includeSpentMempoolOutputs = includeSpentMempoolOutputs;
        }

        @Override
        protected Task<WalletTransaction> createTask() {
            return new Task<>() {
                protected WalletTransaction call() throws InsufficientFundsException {
                    return wallet.createWalletTransaction(utxoSelectors, utxoFilters, payments, excludedChangeNodes, feeRate, longTermFeeRate, fee, currentBlockHeight, groupByAddress, includeMempoolOutputs, includeSpentMempoolOutputs);
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

    private List<UtxoFilter> getUtxoFilters() {
        UtxoFilter utxoFilter = utxoFilterProperty.get();
        if(utxoFilter != null) {
            return List.of(utxoFilter, new FrozenUtxoFilter());
        }

        return List.of(new FrozenUtxoFilter());
    }

    private void updateFeeRateSelection(FeeRatesSelection feeRatesSelection) {
        boolean blockTargetSelection = (feeRatesSelection == FeeRatesSelection.BLOCK_TARGET);
        targetBlocksField.setVisible(blockTargetSelection);
        blockTargetFeeRatesChart.setVisible(blockTargetSelection);
        if(blockTargetSelection) {
            setTargetBlocks(getTargetBlocks(getFeeRangeRate()));
        } else {
            setFeeRangeRate(getTargetBlocksFeeRates().get(getTargetBlocks()));
        }
        updateTransaction();
    }

    private void setDefaultFeeRate() {
        int defaultTarget = TARGET_BLOCKS_RANGE.get((TARGET_BLOCKS_RANGE.size() / 2) - 1);
        int index = TARGET_BLOCKS_RANGE.indexOf(defaultTarget);
        Double defaultRate = getTargetBlocksFeeRates().get(defaultTarget);
        targetBlocks.setValue(index);
        blockTargetFeeRatesChart.select(defaultTarget);
        setFeeRangeRate(defaultRate);
        setFeeRate(getFeeRangeRate());
    }

    private Long getFeeValueSats() {
        return getFeeValueSats(feeAmountUnit.getSelectionModel().getSelectedItem());
    }

    private Long getFeeValueSats(BitcoinUnit bitcoinUnit) {
        if(fee.getText() != null && !fee.getText().isEmpty()) {
            double fieldValue = Double.parseDouble(fee.getText().replaceAll(",", ""));
            return bitcoinUnit.getSatsValue(fieldValue);
        }

        return null;
    }

    private void setFeeValueSats(long feeValue) {
        fee.textProperty().removeListener(feeListener);
        DecimalFormat df = new DecimalFormat("#.#", DecimalFormatSymbols.getInstance(Locale.ENGLISH));
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
            retrievedFeeRates = TARGET_BLOCKS_RANGE.stream().collect(Collectors.toMap(java.util.function.Function.identity(), v -> FALLBACK_FEE_RATE,
                    (u, v) -> { throw new IllegalStateException("Duplicate target blocks"); },
                    LinkedHashMap::new));
        }

        return retrievedFeeRates;
    }

    private Double getFeeRangeRate() {
        return Math.pow(2.0, feeRange.getValue());
    }

    private void setFeeRangeRate(Double feeRate) {
        feeRange.valueProperty().removeListener(feeRangeListener);
        feeRange.setValue(Math.log(feeRate) / Math.log(2));
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
        Double minRate = optMinFeeRate.orElse(FALLBACK_FEE_RATE);
        return Math.max(minRate, Transaction.DUST_RELAY_TX_FEE);
    }

    private Map<Date, Set<MempoolRateSize>> getMempoolHistogram() {
        return AppServices.getMempoolHistogram();
    }

    public boolean isInsufficientFeeRate() {
        return walletTransactionProperty.get() != null && walletTransactionProperty.get().getFeeRate() < AppServices.getMinimumRelayFeeRate();
    }

    private void setFeeRate(Double feeRateAmt) {
        feeRate.setText(String.format("%.2f", feeRateAmt) + " sats/vB");
        setFeeRatePriority(feeRateAmt);
    }

    private void setEffectiveFeeRate(WalletTransaction walletTransaction) {
        List<BlockTransaction> unconfirmedUtxoTxs = walletTransaction.getSelectedUtxos().keySet().stream().filter(ref -> ref.getHeight() <= 0)
                .map(ref -> getWalletForm().getWallet().getTransactions().get(ref.getHash())).filter(Objects::nonNull).distinct().collect(Collectors.toList());
        if(!unconfirmedUtxoTxs.isEmpty()) {
            long utxoTxFee = unconfirmedUtxoTxs.stream().mapToLong(BlockTransaction::getFee).sum();
            double utxoTxSize = unconfirmedUtxoTxs.stream().mapToDouble(blkTx -> blkTx.getTransaction().getVirtualSize()).sum();
            long thisFee = walletTransaction.getFee();
            double thisSize = walletTransaction.getTransaction().getVirtualSize();
            double effectiveRate = (utxoTxFee + thisFee) / (utxoTxSize + thisSize);
            Tooltip tooltip = new Tooltip("Child Pays For Parent\n" + String.format("%.2f", effectiveRate) + " sats/vB effective rate");
            cpfpFeeRate.setTooltip(tooltip);
            cpfpFeeRate.setVisible(true);
        } else {
            cpfpFeeRate.setVisible(false);
        }
    }

    private void setFeeRatePriority(Double feeRateAmt) {
        Map<Integer, Double> targetBlocksFeeRates = getTargetBlocksFeeRates();
        Integer targetBlocks = getTargetBlocks(feeRateAmt);
        if(targetBlocksFeeRates.get(Integer.MAX_VALUE) != null) {
            Double minFeeRate = targetBlocksFeeRates.get(Integer.MAX_VALUE);
            if(minFeeRate > 1.0 && feeRateAmt < minFeeRate) {
                feeRatePriority.setText("Below Minimum");
                feeRatePriority.setTooltip(new Tooltip("Transactions at this fee rate are currently being purged from the default sized mempool"));
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
        if(amount != null && currencyRate != null && currencyRate.isAvailable()) {
            fiatFeeAmount.set(currencyRate, amount);
        }
    }

    private void addFeeRangeTrackHighlight(int count) {
        Platform.runLater(() -> {
            Node track = feeRange.lookup(".track");
            if(track != null) {
                Map<Integer, Double> targetBlocksFeeRates = getTargetBlocksFeeRates();
                String highlight = "";
                if(targetBlocksFeeRates.get(Integer.MAX_VALUE) != null) {
                    highlight += "#a0a1a766 " + getPercentageOfFeeRange(targetBlocksFeeRates.get(Integer.MAX_VALUE)) + "%, ";
                }
                highlight += "#41a9c966 " + getPercentageOfFeeRange(targetBlocksFeeRates, FeeRatesSource.BLOCKS_IN_TWO_HOURS - 1) + "%, ";
                highlight += "#fba71b66 " + getPercentageOfFeeRange(targetBlocksFeeRates, FeeRatesSource.BLOCKS_IN_HOUR - 1) + "%, ";
                highlight += "#c8416466 " + getPercentageOfFeeRange(targetBlocksFeeRates, FeeRatesSource.BLOCKS_IN_HALF_HOUR - 1) + "%";

                track.setStyle("-fx-background-color: " +
                        "-fx-shadow-highlight-color, " +
                        "linear-gradient(to bottom, derive(-fx-text-box-border, -10%), -fx-text-box-border), " +
                        "linear-gradient(to bottom, derive(-fx-control-inner-background, -9%), derive(-fx-control-inner-background, 0%), derive(-fx-control-inner-background, -5%), derive(-fx-control-inner-background, -12%)), " +
                        "linear-gradient(to right, " + highlight + ")");
            } else if(count < 20) {
                addFeeRangeTrackHighlight(count+1);
            }
        });
    }

    private int getPercentageOfFeeRange(Map<Integer, Double> targetBlocksFeeRates, Integer minTargetBlocks) {
        List<Integer> rates = new ArrayList<>(targetBlocksFeeRates.keySet());
        Collections.reverse(rates);
        for(Integer targetBlocks : rates) {
            if(targetBlocks < minTargetBlocks) {
                return getPercentageOfFeeRange(targetBlocksFeeRates.get(targetBlocks));
            }
        }

        return 100;
    }

    private int getPercentageOfFeeRange(Double feeRate) {
        double index = Math.log(feeRate) / Math.log(2);
        return (int)Math.round(index * 10.0);
    }

    private void updateMaxClearButtons(UtxoSelector utxoSelector, UtxoFilter utxoFilter) {
        if(utxoSelector instanceof PresetUtxoSelector) {
            PresetUtxoSelector presetUtxoSelector = (PresetUtxoSelector)utxoSelector;
            int num = presetUtxoSelector.getPresetUtxos().size();
            String selection = " (" + num + " UTXO" + (num != 1 ? "s" : "") + " selected)";
            utxoLabelSelectionProperty.set(selection);
        } else if(utxoFilter instanceof ExcludeUtxoFilter) {
            ExcludeUtxoFilter excludeUtxoFilter = (ExcludeUtxoFilter)utxoFilter;
            int num = excludeUtxoFilter.getExcludedUtxos().size();
            String exclusion = " (" + num + " UTXO" + (num != 1 ? "s" : "") + " excluded)";
            utxoLabelSelectionProperty.set(exclusion);
        } else {
            utxoLabelSelectionProperty.set("");
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
        utxoFilterProperty.setValue(null);
        includeSpentMempoolOutputsProperty.set(false);
        excludedChangeNodes.clear();
        walletTransactionProperty.setValue(null);
        createdWalletTransactionProperty.set(null);

        validationSupport.setErrorDecorationEnabled(false);
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
        if(log.isDebugEnabled()) {
            Map<WalletNode, List<String>> inputHashes = new LinkedHashMap<>();
            for(WalletNode node : walletTransactionProperty.get().getSelectedUtxos().values()) {
                List<String> nodeHashes = inputHashes.computeIfAbsent(node, k -> new ArrayList<>());
                nodeHashes.add(ElectrumServer.getScriptHash(walletForm.getWallet(), node));
            }
            Map<WalletNode, List<String>> changeHash = Collections.emptyMap();
            if(walletTransactionProperty.get().getChangeNode() != null) {
                changeHash = Map.of(walletTransactionProperty.get().getChangeNode(), List.of(ElectrumServer.getScriptHash(walletForm.getWallet(), walletTransactionProperty.get().getChangeNode())));
            }
            log.debug("Creating tx " + walletTransactionProperty.get().getTransaction().getTxId() + ", expecting notifications for \ninputs \n" + inputHashes + " and \nchange \n" + changeHash);
        }

        addWalletTransactionNodes();
        createdWalletTransactionProperty.set(walletTransactionProperty.get());
        PSBT psbt = walletTransactionProperty.get().createPSBT();
        EventManager.get().post(new ViewPSBTEvent(createButton.getScene().getWindow(), walletTransactionProperty.get().getPayments().get(0).getLabel(), null, psbt));
    }

    private void addWalletTransactionNodes() {
        WalletTransaction walletTransaction = walletTransactionProperty.get();
        Set<WalletNode> nodes = new LinkedHashSet<>(walletTransaction.getSelectedUtxos().values());
        if(walletTransaction.getChangeNode() != null) {
            nodes.add(walletTransaction.getChangeNode());
        }
        List<WalletNode> consolidationNodes = walletTransaction.getConsolidationSendNodes();
        nodes.addAll(consolidationNodes);

        //All wallet nodes applicable to this transaction are stored so when the subscription status for one is updated, the history for all can be fetched in one atomic update
        walletForm.addWalletTransactionNodes(nodes);
    }

    @Subscribe
    public void walletNodesChanged(WalletNodesChangedEvent event) {
        if(event.getWallet().equals(walletForm.getWallet())) {
            clear(null);
        }
    }

    @Subscribe
    public void walletHistoryChanged(WalletHistoryChangedEvent event) {
        if(event.getWallet().equals(walletForm.getWallet()) && createdWalletTransactionProperty.get() != null) {
            if(createdWalletTransactionProperty.get().getSelectedUtxos() != null && allSelectedUtxosSpent(event.getHistoryChangedNodes())) {
                clear(null);
            } else {
                updateTransaction();
            }
        }
    }

    private boolean allSelectedUtxosSpent(List<WalletNode> historyChangedNodes) {
        Set<BlockTransactionHashIndex> unspentUtxos = new HashSet<>(createdWalletTransactionProperty.get().getSelectedUtxos().keySet());

        for(Map.Entry<BlockTransactionHashIndex, WalletNode> selectedUtxoEntry : createdWalletTransactionProperty.get().getSelectedUtxos().entrySet()) {
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
        if(event.getWallet().equals(walletForm.getWallet())) {
            updateTransaction();
        }
    }

    @Subscribe
    public void feeRatesUpdated(FeeRatesUpdatedEvent event) {
        blockTargetFeeRatesChart.update(event.getTargetBlockFeeRates());
        blockTargetFeeRatesChart.select(getTargetBlocks());
        mempoolSizeFeeRatesChart.update(getMempoolHistogram());
        if(targetBlocksField.isVisible()) {
            setFeeRate(event.getTargetBlockFeeRates().get(getTargetBlocks()));
        } else {
            setFeeRatePriority(getFeeRangeRate());
        }
        addFeeRangeTrackHighlight(0);
    }

    @Subscribe
    public void feeRateSelectionChanged(FeeRatesSelectionChangedEvent event) {
        if(event.getWallet() == getWalletForm().getWallet()) {
            updateFeeRateSelection(event.getFeeRateSelection());
        }
    }

    @Subscribe
    public void spendUtxos(SpendUtxoEvent event) {
        if(!event.getUtxos().isEmpty() && event.getWallet().equals(getWalletForm().getWallet())) {
            if(event.getPayments() != null) {
                clear(null);
                setPayments(event.getPayments());
            } else if(paymentTabs.getTabs().size() == 1) {
                Payment payment = new Payment(null, null, event.getUtxos().stream().mapToLong(BlockTransactionHashIndex::getValue).sum(), true);
                setPayments(List.of(payment));
            }

            if(event.getFee() != null) {
                setFeeValueSats(event.getFee());
                userFeeSet.set(true);
            }

            includeSpentMempoolOutputsProperty.set(event.isIncludeSpentMempoolOutputs());

            List<BlockTransactionHashIndex> utxos = event.getUtxos();
            utxoSelectorProperty.set(new PresetUtxoSelector(utxos));
            utxoFilterProperty.set(null);
            updateTransaction(event.getPayments() == null || event.getPayments().stream().anyMatch(Payment::isSendMax));
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
    public void fiatCurrencySelected(FiatCurrencySelectedEvent event) {
        if(event.getExchangeSource() == ExchangeSource.NONE) {
            fiatFeeAmount.setCurrency(null);
            fiatFeeAmount.setBtcRate(0.0);
        }
    }

    @Subscribe
    public void exchangeRatesUpdated(ExchangeRatesUpdatedEvent event) {
        setFiatFeeAmount(event.getCurrencyRate(), getFeeValueSats());
    }

    @Subscribe
    public void excludeUtxo(ExcludeUtxoEvent event) {
        if(event.getWalletTransaction() == walletTransactionProperty.get()) {
            UtxoSelector utxoSelector = utxoSelectorProperty.get();
            if(utxoSelector instanceof MaxUtxoSelector) {
                Collection<BlockTransactionHashIndex> utxos = walletForm.getWallet().getWalletUtxos().keySet();
                utxos.remove(event.getUtxo());
                if(utxoFilterProperty.get() instanceof ExcludeUtxoFilter) {
                    ExcludeUtxoFilter existingUtxoFilter = (ExcludeUtxoFilter)utxoFilterProperty.get();
                    utxos.removeAll(existingUtxoFilter.getExcludedUtxos());
                }
                PresetUtxoSelector presetUtxoSelector = new PresetUtxoSelector(utxos);
                utxoSelectorProperty.set(presetUtxoSelector);
                updateTransaction(true);
            } else if(utxoSelector instanceof PresetUtxoSelector) {
                PresetUtxoSelector presetUtxoSelector = new PresetUtxoSelector(((PresetUtxoSelector)utxoSelector).getPresetUtxos());
                presetUtxoSelector.getPresetUtxos().remove(event.getUtxo());
                utxoSelectorProperty.set(presetUtxoSelector);
                updateTransaction(true);
            } else {
                ExcludeUtxoFilter utxoFilter = new ExcludeUtxoFilter();
                if(utxoFilterProperty.get() instanceof ExcludeUtxoFilter) {
                    ExcludeUtxoFilter existingUtxoFilter = (ExcludeUtxoFilter)utxoFilterProperty.get();
                    utxoFilter.getExcludedUtxos().addAll(existingUtxoFilter.getExcludedUtxos());
                }

                utxoFilter.getExcludedUtxos().add(event.getUtxo());
                utxoFilterProperty.set(utxoFilter);
                updateTransaction();
            }
        }
    }

    @Subscribe
    public void replaceChangeAddress(ReplaceChangeAddressEvent event) {
        if(event.getWalletTransaction() == walletTransactionProperty.get()) {
            excludedChangeNodes.add(event.getWalletTransaction().getChangeNode());
            updateTransaction();
        }
    }

    @Subscribe
    public void walletUtxoStatusChanged(WalletUtxoStatusChangedEvent event) {
        if(event.getWallet().equals(getWalletForm().getWallet())) {
            UtxoSelector utxoSelector = utxoSelectorProperty.get();
            if(utxoSelector instanceof MaxUtxoSelector) {
                updateTransaction(true);
            } else if(utxoSelectorProperty().get() instanceof PresetUtxoSelector) {
                PresetUtxoSelector presetUtxoSelector = new PresetUtxoSelector(((PresetUtxoSelector)utxoSelector).getPresetUtxos());
                presetUtxoSelector.getPresetUtxos().remove(event.getUtxo());
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
}
