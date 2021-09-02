package com.sparrowwallet.sparrow.whirlpool;

import com.samourai.whirlpool.client.tx0.Tx0Preview;
import com.samourai.whirlpool.client.whirlpool.beans.Pool;
import com.sparrowwallet.drongo.BitcoinUnit;
import com.sparrowwallet.drongo.protocol.Transaction;
import com.sparrowwallet.drongo.wallet.MixConfig;
import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.sparrow.AppServices;
import com.sparrowwallet.sparrow.EventManager;
import com.sparrowwallet.sparrow.control.CoinLabel;
import com.sparrowwallet.sparrow.event.WalletMixConfigChangedEvent;
import com.sparrowwallet.sparrow.io.Config;
import com.sparrowwallet.sparrow.wallet.Entry;
import com.sparrowwallet.sparrow.wallet.UtxoEntry;
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
    private ComboBox<Pool> pool;

    @FXML
    private VBox selectedPool;

    @FXML
    private CoinLabel poolFee;

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
    private CoinLabel discountFee;

    private String walletId;
    private Wallet wallet;
    private MixConfig mixConfig;
    private List<UtxoEntry> utxoEntries;
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
        scode.textProperty().addListener((observable, oldValue, newValue) -> {
            mixConfig.setScode(newValue);
            EventManager.get().post(new WalletMixConfigChangedEvent(wallet));
        });

        if(mixConfig.getScode() != null) {
            step1.setVisible(false);
            step3.setVisible(true);
        }

        pool.setConverter(new StringConverter<Pool>() {
            @Override
            public String toString(Pool pool) {
                return pool == null ? "Fetching pools..." : pool.getPoolId().replace("btc", " BTC");
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
        Whirlpool.PoolsService poolsService = new Whirlpool.PoolsService(AppServices.getWhirlpoolServices().getWhirlpool(walletId));
        poolsService.setOnSucceeded(workerStateEvent -> {
            List<Pool> availablePools = poolsService.getValue().stream().filter(pool1 -> totalUtxoValue >= (pool1.getPremixValueMin() + pool1.getFeeValue())).toList();
            if(availablePools.isEmpty()) {
                pool.setVisible(false);
                OptionalLong optMinValue = poolsService.getValue().stream().mapToLong(pool1 -> pool1.getPremixValueMin() + pool1.getFeeValue()).min();
                if(optMinValue.isPresent()) {
                    String satsValue = String.format(Locale.ENGLISH, "%,d", optMinValue.getAsLong()) + " sats";
                    String btcValue = CoinLabel.BTC_FORMAT.format((double)optMinValue.getAsLong() / Transaction.SATOSHIS_PER_BITCOIN) + " BTC";
                    poolInsufficient.setText("No available pools. Select a value over " + (Config.get().getBitcoinUnit() == BitcoinUnit.BTC ? btcValue : satsValue) + ".");
                }
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
            EventManager.get().post(new WalletMixConfigChangedEvent(wallet));
        }

        Whirlpool whirlpool = AppServices.getWhirlpoolServices().getWhirlpool(walletId);
        whirlpool.setScode(mixConfig.getScode());

        Whirlpool.Tx0PreviewService tx0PreviewService = new Whirlpool.Tx0PreviewService(whirlpool, wallet, pool, utxoEntries);
        tx0PreviewService.setOnRunning(workerStateEvent -> {
            nbOutputsBox.setVisible(true);
            nbOutputsLoading.setText("Calculating...");
            nbOutputs.setVisible(false);
            discountFeeBox.setVisible(false);
        });
        tx0PreviewService.setOnSucceeded(workerStateEvent -> {
            Tx0Preview tx0Preview = tx0PreviewService.getValue();
            discountFeeBox.setVisible(tx0Preview.getPool().getFeeValue() != tx0Preview.getTx0Data().getFeeValue());
            discountFee.setValue(tx0Preview.getTx0Data().getFeeValue());
            nbOutputsBox.setVisible(true);
            nbOutputs.setText(tx0Preview.getNbPremix() + " UTXOs");
            nbOutputs.setVisible(true);
            tx0PreviewProperty.set(tx0Preview);
        });
        tx0PreviewService.setOnFailed(workerStateEvent -> {
            Throwable exception = workerStateEvent.getSource().getException();
            while(exception.getCause() != null) {
                exception = exception.getCause();
            }

            nbOutputsLoading.setText("Error fetching fee: " + exception.getMessage());
        });
        tx0PreviewService.start();
    }

    public ObjectProperty<Tx0Preview> getTx0PreviewProperty() {
        return tx0PreviewProperty;
    }
}
