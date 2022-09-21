package com.sparrowwallet.sparrow.whirlpool;

import com.samourai.whirlpool.client.tx0.Tx0Preview;
import com.samourai.whirlpool.client.tx0.Tx0Previews;
import com.samourai.whirlpool.client.wallet.beans.Tx0FeeTarget;
import com.samourai.whirlpool.client.whirlpool.beans.Pool;
import com.sparrowwallet.drongo.BitcoinUnit;
import com.sparrowwallet.drongo.wallet.MixConfig;
import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.sparrow.UnitFormat;
import com.sparrowwallet.sparrow.AppServices;
import com.sparrowwallet.sparrow.EventManager;
import com.sparrowwallet.sparrow.control.CopyableCoinLabel;
import com.sparrowwallet.sparrow.control.CopyableLabel;
import com.sparrowwallet.sparrow.event.WalletMasterMixConfigChangedEvent;
import com.sparrowwallet.sparrow.io.Config;
import com.sparrowwallet.sparrow.wallet.Entry;
import com.sparrowwallet.sparrow.wallet.UtxoEntry;
import com.sparrowwallet.sparrow.whirlpool.dataSource.SparrowMinerFeeSupplier;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;

import java.util.*;

public class WhirlpoolController {
    private static final List<Tx0FeeTarget> FEE_TARGETS = List.of(Tx0FeeTarget.MIN, Tx0FeeTarget.BLOCKS_4, Tx0FeeTarget.BLOCKS_2);

    @FXML
    private VBox whirlpoolBox;

    @FXML
    private VBox step1;

    @FXML
    private VBox step2;

    @FXML
    private VBox step3;

    @FXML
    private VBox step4;

    @FXML
    private TextField scode;

    @FXML
    private Slider premixPriority;

    @FXML
    private CopyableLabel premixFeeRate;

    @FXML
    private ComboBox<Pool> pool;

    @FXML
    private VBox selectedPool;

    @FXML
    private CopyableCoinLabel poolFee;

    @FXML
    private Label poolInsufficient;

    @FXML
    private Label poolAnonset;

    @FXML
    private HBox discountFeeBox;

    @FXML
    private HBox nbOutputsBox;

    @FXML
    private Label nbOutputsLoading;

    @FXML
    private Label nbOutputs;

    @FXML
    private CopyableCoinLabel discountFee;

    private String walletId;
    private Wallet wallet;
    private MixConfig mixConfig;
    private List<UtxoEntry> utxoEntries;
    private Tx0Previews tx0Previews;
    private final ObjectProperty<Tx0Preview> tx0PreviewProperty = new SimpleObjectProperty<>(null);

    public void initializeView(String walletId, Wallet wallet, List<UtxoEntry> utxoEntries) {
        this.walletId = walletId;
        this.wallet = wallet;
        this.utxoEntries = utxoEntries;
        this.mixConfig = wallet.getMasterMixConfig();

        step1.managedProperty().bind(step1.visibleProperty());
        step2.managedProperty().bind(step2.visibleProperty());
        step3.managedProperty().bind(step3.visibleProperty());
        step4.managedProperty().bind(step4.visibleProperty());

        step2.setVisible(false);
        step3.setVisible(false);
        step4.setVisible(false);

        scode.setText(mixConfig.getScode() == null ? "" : mixConfig.getScode());
        scode.setTextFormatter(new TextFormatter<>((change) -> {
            change.setText(change.getText().toUpperCase(Locale.ROOT));
            return change;
        }));
        scode.textProperty().addListener((observable, oldValue, newValue) -> {
            pool.setItems(FXCollections.emptyObservableList());
            tx0PreviewProperty.set(null);
            mixConfig.setScode(newValue);
            EventManager.get().post(new WalletMasterMixConfigChangedEvent(wallet));
        });

        premixPriority.setMin(0);
        premixPriority.setMax(FEE_TARGETS.size() - 1);
        premixPriority.setMajorTickUnit(1);
        premixPriority.setMinorTickCount(0);
        premixPriority.setLabelFormatter(new StringConverter<>() {
            @Override
            public String toString(Double object) {
                return object.intValue() == 0 ? "Low" : (object.intValue() == 1 ? "Normal" : "High");
            }

            @Override
            public Double fromString(String string) {
                return null;
            }
        });
        premixPriority.valueProperty().addListener((observable, oldValue, newValue) -> {
            pool.setItems(FXCollections.emptyObservableList());
            tx0Previews = null;
            tx0PreviewProperty.set(null);
            Tx0FeeTarget tx0FeeTarget = FEE_TARGETS.get(newValue.intValue());
            premixFeeRate.setText(SparrowMinerFeeSupplier.getFee(Integer.parseInt(tx0FeeTarget.getFeeTarget().getValue())) + " sats/vB");
        });
        premixPriority.setValue(1);

        if(mixConfig.getScode() != null) {
            step1.setVisible(false);
            step3.setVisible(true);
        }

        pool.setConverter(new StringConverter<Pool>() {
            @Override
            public String toString(Pool selectedPool) {
                if(selectedPool == null) {
                    pool.setTooltip(null);
                    return "Fetching pools...";
                }

                UnitFormat format = Config.get().getUnitFormat() == null ? UnitFormat.DOT : Config.get().getUnitFormat();
                BitcoinUnit bitcoinUnit = wallet.getAutoUnit();
                String satsValue = format.formatSatsValue(selectedPool.getDenomination()) + " sats";
                String btcValue = format.formatBtcValue(selectedPool.getDenomination()) + " BTC";

                pool.setTooltip(bitcoinUnit == BitcoinUnit.BTC ? new Tooltip(satsValue) : new Tooltip(btcValue));
                return bitcoinUnit == BitcoinUnit.BTC ? btcValue : satsValue;
            }

            @Override
            public Pool fromString(String string) {
                return null;
            }
        });

        pool.valueProperty().addListener((observable, oldValue, newValue) -> {
            if(newValue == null) {
                selectedPool.setVisible(false);
            } else {
                poolFee.setValue(newValue.getFeeValue());
                poolAnonset.setText(newValue.getMixAnonymitySet() + " UTXOs");
                selectedPool.setVisible(true);
                fetchTx0Preview(newValue);
            }
        });

        step4.visibleProperty().addListener((observable, oldValue, newValue) -> {
            if(newValue && pool.getItems().isEmpty()) {
                fetchPools();
            }
        });

        selectedPool.managedProperty().bind(selectedPool.visibleProperty());
        selectedPool.setVisible(false);
        pool.managedProperty().bind(pool.visibleProperty());
        poolInsufficient.managedProperty().bind(poolInsufficient.visibleProperty());
        poolInsufficient.visibleProperty().bind(pool.visibleProperty().not());
        discountFeeBox.managedProperty().bind(discountFeeBox.visibleProperty());
        discountFeeBox.setVisible(false);
        nbOutputsBox.managedProperty().bind(nbOutputsBox.visibleProperty());
        nbOutputsBox.setVisible(false);
        nbOutputsLoading.managedProperty().bind(nbOutputsLoading.visibleProperty());
        nbOutputs.managedProperty().bind(nbOutputs.visibleProperty());
        nbOutputsLoading.visibleProperty().bind(nbOutputs.visibleProperty().not());
        nbOutputs.setVisible(false);

        tx0PreviewProperty.addListener((observable, oldValue, tx0Preview) -> {
            if(tx0Preview == null) {
                nbOutputsBox.setVisible(true);
                nbOutputsLoading.setText("Calculating...");
                nbOutputs.setVisible(false);
                discountFeeBox.setVisible(false);
            } else {
                discountFeeBox.setVisible(tx0Preview.getPool().getFeeValue() != tx0Preview.getTx0Data().getFeeValue());
                discountFee.setValue(tx0Preview.getTx0Data().getFeeValue());
                nbOutputsBox.setVisible(true);
                nbOutputs.setText(tx0Preview.getNbPremix() + " UTXOs");
                nbOutputs.setVisible(true);
            }
        });
    }

    public boolean next() {
        if(step1.isVisible()) {
            step1.setVisible(false);
            step2.setVisible(true);
            return true;
        }

        if(step2.isVisible()) {
            step2.setVisible(false);
            step3.setVisible(true);
            return true;
        }

        if(step3.isVisible()) {
            step3.setVisible(false);
            step4.setVisible(true);
        }

        return false;
    }

    public boolean back() {
        if(step2.isVisible()) {
            step2.setVisible(false);
            step1.setVisible(true);
            return false;
        }

        if(step3.isVisible()) {
            step3.setVisible(false);
            step2.setVisible(true);
            return true;
        }

        if(step4.isVisible()) {
            step4.setVisible(false);
            step3.setVisible(true);
            return true;
        }

        return false;
    }

    private void fetchPools() {
        long totalUtxoValue = utxoEntries.stream().mapToLong(Entry::getValue).sum();
        Whirlpool.PoolsService poolsService = new Whirlpool.PoolsService(AppServices.getWhirlpoolServices().getWhirlpool(walletId), totalUtxoValue);
        poolsService.setOnSucceeded(workerStateEvent -> {
            List<Pool> availablePools = poolsService.getValue().stream().toList();
            if(availablePools.isEmpty()) {
                pool.setVisible(false);

                Whirlpool.PoolsService allPoolsService = new Whirlpool.PoolsService(AppServices.getWhirlpoolServices().getWhirlpool(walletId), null);
                allPoolsService.setOnSucceeded(poolsStateEvent -> {
                    OptionalLong optMinValue = allPoolsService.getValue().stream().mapToLong(pool1 -> pool1.getPremixValueMin() + pool1.getFeeValue()).min();
                    if(optMinValue.isPresent() && totalUtxoValue < optMinValue.getAsLong()) {
                        UnitFormat format = Config.get().getUnitFormat() == null ? UnitFormat.DOT : Config.get().getUnitFormat();
                        String satsValue = format.formatSatsValue(optMinValue.getAsLong()) + " sats";
                        String btcValue = format.formatBtcValue(optMinValue.getAsLong()) + " BTC";
                        poolInsufficient.setText("No available pools. Select a value over " + (Config.get().getBitcoinUnit() == BitcoinUnit.BTC ? btcValue : satsValue) + ".");
                    }
                });
                allPoolsService.start();
            } else {
                pool.setDisable(false);
                pool.setItems(FXCollections.observableList(availablePools));
                pool.getSelectionModel().select(0);
            }
        });
        poolsService.setOnFailed(workerStateEvent -> {
            Throwable exception = workerStateEvent.getSource().getException();
            while(exception.getCause() != null) {
                exception = exception.getCause();
            }

            Optional<ButtonType> optButton = AppServices.showErrorDialog("Error fetching pools", exception.getMessage(), ButtonType.CANCEL, new ButtonType("Retry", ButtonBar.ButtonData.APPLY));
            if(optButton.isPresent()) {
                if(optButton.get().getButtonData().equals(ButtonBar.ButtonData.APPLY)) {
                    fetchPools();
                } else {
                    pool.setDisable(true);
                }
            }
        });
        poolsService.start();
    }

    private void fetchTx0Preview(Pool pool) {
        if(mixConfig.getScode() == null) {
            mixConfig.setScode("");
            EventManager.get().post(new WalletMasterMixConfigChangedEvent(wallet));
        }

        Whirlpool whirlpool = AppServices.getWhirlpoolServices().getWhirlpool(walletId);
        if(tx0Previews != null && mixConfig.getScode().equals(whirlpool.getScode())) {
            Tx0Preview tx0Preview = tx0Previews.getTx0Preview(pool.getPoolId());
            tx0PreviewProperty.set(tx0Preview);
        } else {
            tx0Previews = null;
            whirlpool.setScode(mixConfig.getScode());
            whirlpool.setTx0FeeTarget(FEE_TARGETS.get(premixPriority.valueProperty().intValue()));

            Whirlpool.Tx0PreviewsService tx0PreviewsService = new Whirlpool.Tx0PreviewsService(whirlpool, utxoEntries);
            tx0PreviewsService.setOnRunning(workerStateEvent -> {
                nbOutputsBox.setVisible(true);
                nbOutputsLoading.setText("Calculating...");
                nbOutputs.setVisible(false);
                discountFeeBox.setVisible(false);
                tx0PreviewProperty.set(null);
            });
            tx0PreviewsService.setOnSucceeded(workerStateEvent -> {
                tx0Previews = tx0PreviewsService.getValue();
                Tx0Preview tx0Preview = tx0Previews.getTx0Preview(pool.getPoolId());
                tx0PreviewProperty.set(tx0Preview);
            });
            tx0PreviewsService.setOnFailed(workerStateEvent -> {
                Throwable exception = workerStateEvent.getSource().getException();
                while(exception.getCause() != null) {
                    exception = exception.getCause();
                }

                nbOutputsLoading.setText("Error fetching Tx0: " + exception.getMessage());
            });
            tx0PreviewsService.start();
        }
    }

    public ObjectProperty<Tx0Preview> getTx0PreviewProperty() {
        return tx0PreviewProperty;
    }
}
