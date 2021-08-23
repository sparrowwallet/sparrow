package com.sparrowwallet.sparrow.wallet;

import com.csvreader.CsvWriter;
import com.google.common.eventbus.Subscribe;
import com.samourai.whirlpool.client.tx0.Tx0Preview;
import com.sparrowwallet.drongo.BitcoinUnit;
import com.sparrowwallet.drongo.KeyPurpose;
import com.sparrowwallet.drongo.Network;
import com.sparrowwallet.drongo.address.Address;
import com.sparrowwallet.drongo.address.InvalidAddressException;
import com.sparrowwallet.drongo.protocol.Transaction;
import com.sparrowwallet.drongo.wallet.*;
import com.sparrowwallet.sparrow.AppServices;
import com.sparrowwallet.sparrow.EventManager;
import com.sparrowwallet.sparrow.control.*;
import com.sparrowwallet.sparrow.event.*;
import com.sparrowwallet.sparrow.io.Config;
import com.sparrowwallet.sparrow.whirlpool.Whirlpool;
import com.sparrowwallet.sparrow.whirlpool.WhirlpoolDialog;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.WeakChangeListener;
import javafx.collections.ListChangeListener;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

public class UtxosController extends WalletFormController implements Initializable {
    private static final Logger log = LoggerFactory.getLogger(UtxosController.class);

    @FXML
    private UtxosTreeTable utxosTable;

    @FXML
    private HBox mixButtonsBox;

    @FXML
    private Button startMix;

    @FXML
    private Button stopMix;

    @FXML
    private Button sendSelected;

    @FXML
    private Button mixSelected;

    @FXML
    private UtxosChart utxosChart;

    private final ChangeListener<Boolean> mixingOnlineListener = (observable, oldValue, newValue) -> {
        mixSelected.setDisable(getSelectedEntries().isEmpty() || !newValue);
        startMix.setDisable(!newValue);
        stopMix.setDisable(!newValue);
    };

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        EventManager.get().register(this);
    }

    @Override
    public void initializeView() {
        utxosTable.initialize(getWalletForm().getWalletUtxosEntry());
        utxosChart.initialize(getWalletForm().getWalletUtxosEntry());

        mixButtonsBox.managedProperty().bind(mixButtonsBox.visibleProperty());
        mixButtonsBox.setVisible(getWalletForm().getWallet().isWhirlpoolMixWallet());
        startMix.managedProperty().bind(startMix.visibleProperty());
        startMix.setDisable(!AppServices.isConnected());
        stopMix.managedProperty().bind(stopMix.visibleProperty());
        startMix.visibleProperty().bind(stopMix.visibleProperty().not());
        stopMix.visibleProperty().addListener((observable, oldValue, newValue) -> {
            stopMix.setDisable(!newValue);
            startMix.setDisable(newValue);
        });
        if(mixButtonsBox.isVisible()) {
            Whirlpool whirlpool = AppServices.get().getWhirlpool(getWalletForm().getWallet());
            if(whirlpool != null) {
                stopMix.visibleProperty().bind(whirlpool.mixingProperty());
            }
        }

        sendSelected.setDisable(true);
        sendSelected.setTooltip(new Tooltip("Send selected UTXOs. Use " + (org.controlsfx.tools.Platform.getCurrent() == org.controlsfx.tools.Platform.OSX ? "Cmd" : "Ctrl") + "+click to select multiple." ));
        mixSelected.managedProperty().bind(mixSelected.visibleProperty());
        mixSelected.setVisible(canWalletMix());
        mixSelected.setDisable(true);
        AppServices.onlineProperty().addListener(new WeakChangeListener<>(mixingOnlineListener));

        utxosTable.getSelectionModel().getSelectedIndices().addListener((ListChangeListener<Integer>) c -> {
            List<Entry> selectedEntries = utxosTable.getSelectionModel().getSelectedCells().stream().map(tp -> tp.getTreeItem().getValue()).collect(Collectors.toList());
            utxosChart.select(selectedEntries);
            updateButtons(Config.get().getBitcoinUnit());
        });

        utxosChart.managedProperty().bind(utxosChart.visibleProperty());
        utxosChart.setVisible(Config.get().isShowUtxosChart() && !getWalletForm().getWallet().isWhirlpoolMixWallet());
    }

    private boolean canWalletMix() {
        return Network.get() == Network.TESTNET && getWalletForm().getWallet().getKeystores().size() == 1 && getWalletForm().getWallet().getKeystores().get(0).hasSeed() && !getWalletForm().getWallet().isWhirlpoolMixWallet();
    }

    private void updateButtons(BitcoinUnit unit) {
        List<Entry> selectedEntries = getSelectedEntries();

        sendSelected.setDisable(selectedEntries.isEmpty());
        mixSelected.setDisable(selectedEntries.isEmpty() || !AppServices.isConnected());

        long selectedTotal = selectedEntries.stream().mapToLong(Entry::getValue).sum();
        if(selectedTotal > 0) {
            if(unit == null || unit.equals(BitcoinUnit.AUTO)) {
                unit = (selectedTotal >= BitcoinUnit.getAutoThreshold() ? BitcoinUnit.BTC : BitcoinUnit.SATOSHIS);
            }

            if(unit.equals(BitcoinUnit.BTC)) {
                sendSelected.setText("Send Selected (" + CoinLabel.getBTCFormat().format((double)selectedTotal / Transaction.SATOSHIS_PER_BITCOIN) + " BTC)");
                mixSelected.setText("Mix Selected (" + CoinLabel.getBTCFormat().format((double)selectedTotal / Transaction.SATOSHIS_PER_BITCOIN) + " BTC)");
            } else {
                sendSelected.setText("Send Selected (" + String.format(Locale.ENGLISH, "%,d", selectedTotal) + " sats)");
                mixSelected.setText("Mix Selected (" + String.format(Locale.ENGLISH, "%,d", selectedTotal) + " sats)");
            }
        } else {
            sendSelected.setText("Send Selected");
            mixSelected.setText("Mix Selected");
        }
    }

    private List<Entry> getSelectedEntries() {
        return utxosTable.getSelectionModel().getSelectedCells().stream().map(tp -> (UtxoEntry)tp.getTreeItem().getValue())
                .filter(utxoEntry -> utxoEntry.isSpendable() && !utxoEntry.isMixing())
                .collect(Collectors.toList());
    }

    public void sendSelected(ActionEvent event) {
        List<UtxoEntry> utxoEntries = getSelectedUtxos();
        final List<BlockTransactionHashIndex> spendingUtxos = utxoEntries.stream().map(HashIndexEntry::getHashIndex).collect(Collectors.toList());
        EventManager.get().post(new SendActionEvent(getWalletForm().getWallet(), spendingUtxos));
        Platform.runLater(() -> EventManager.get().post(new SpendUtxoEvent(getWalletForm().getWallet(), spendingUtxos)));
    }

    public void mixSelected(ActionEvent event) {
        List<UtxoEntry> selectedEntries = getSelectedUtxos();
        WhirlpoolDialog whirlpoolDialog = new WhirlpoolDialog(getWalletForm().getWalletId(), getWalletForm().getWallet(), selectedEntries);
        Optional<Tx0Preview> optTx0Preview = whirlpoolDialog.showAndWait();
        optTx0Preview.ifPresent(tx0Preview -> previewPremixTransaction(getWalletForm().getWallet(), tx0Preview, selectedEntries));
    }

    public void previewPremixTransaction(Wallet wallet, Tx0Preview tx0Preview, List<UtxoEntry> utxoEntries) {
        for(StandardAccount whirlpoolAccount : StandardAccount.WHIRLPOOL_ACCOUNTS) {
            if(wallet.getChildWallet(whirlpoolAccount) == null) {
                Wallet childWallet = wallet.addChildWallet(whirlpoolAccount);
                EventManager.get().post(new ChildWalletAddedEvent(getWalletForm().getStorage(), wallet, childWallet));
            }
        }

        Wallet premixWallet = wallet.getChildWallet(StandardAccount.WHIRLPOOL_PREMIX);
        Wallet badbankWallet = wallet.getChildWallet(StandardAccount.WHIRLPOOL_BADBANK);

        List<Payment> payments = new ArrayList<>();
        try {
            Address whirlpoolFeeAddress = Address.fromString(tx0Preview.getTx0Data().getFeeAddress());
            Payment whirlpoolFeePayment = new Payment(whirlpoolFeeAddress, "Whirlpool Fee", tx0Preview.getFeeValue(), false);
            whirlpoolFeePayment.setType(Payment.Type.WHIRLPOOL_FEE);
            payments.add(whirlpoolFeePayment);
        } catch(InvalidAddressException e) {
            throw new IllegalStateException("Cannot parse whirlpool fee address " + tx0Preview.getTx0Data().getFeeAddress(), e);
        }

        WalletNode badbankNode = badbankWallet.getFreshNode(KeyPurpose.RECEIVE);
        Payment changePayment = new Payment(badbankWallet.getAddress(badbankNode), "Badbank Change", tx0Preview.getChangeValue(), false);
        payments.add(changePayment);

        WalletNode premixNode = null;
        for(int i = 0; i < tx0Preview.getNbPremix(); i++) {
            premixNode = premixWallet.getFreshNode(KeyPurpose.RECEIVE, premixNode);
            Address premixAddress = premixWallet.getAddress(premixNode);
            payments.add(new Payment(premixAddress, "Premix #" + i, tx0Preview.getPremixValue(), false));
        }

        final List<BlockTransactionHashIndex> utxos = utxoEntries.stream().map(HashIndexEntry::getHashIndex).collect(Collectors.toList());
        Platform.runLater(() -> {
            EventManager.get().post(new SendActionEvent(getWalletForm().getWallet(), utxos));
            Platform.runLater(() -> EventManager.get().post(new SpendUtxoEvent(getWalletForm().getWallet(), utxos, payments, tx0Preview.getTx0MinerFee(), tx0Preview.getPool())));
        });
    }

    private List<UtxoEntry> getSelectedUtxos() {
        return utxosTable.getSelectionModel().getSelectedCells().stream()
                .map(tp -> tp.getTreeItem().getValue())
                .filter(e -> e instanceof HashIndexEntry)
                .map(e -> (UtxoEntry)e)
                .filter(e -> e.getType().equals(HashIndexEntry.Type.OUTPUT) && e.isSpendable())
                .collect(Collectors.toList());
    }

    public void clear(ActionEvent event) {
        utxosTable.getSelectionModel().clearSelection();
    }

    public void startMixing(ActionEvent event) {
        startMix.setDisable(true);
        stopMix.setDisable(false);

        Whirlpool whirlpool = AppServices.get().getWhirlpool(getWalletForm().getWallet());
        if(whirlpool != null && !whirlpool.isStarted() && AppServices.isConnected()) {
            Whirlpool.StartupService startupService = new Whirlpool.StartupService(whirlpool);
            startupService.setOnFailed(workerStateEvent -> {
                AppServices.showErrorDialog("Failed to start whirlpool", workerStateEvent.getSource().getException().getMessage());
                log.error("Failed to start whirlpool", workerStateEvent.getSource().getException());
            });
            startupService.start();
        }
    }

    public void stopMixing(ActionEvent event) {
        stopMix.setDisable(true);
        startMix.setDisable(false);

        Whirlpool whirlpool = AppServices.get().getWhirlpool(getWalletForm().getWallet());
        if(whirlpool.isStarted()) {
            Whirlpool.ShutdownService shutdownService = new Whirlpool.ShutdownService(whirlpool);
            shutdownService.setOnFailed(workerStateEvent -> {
                log.error("Failed to stop whirlpool", workerStateEvent.getSource().getException());
                AppServices.showErrorDialog("Failed to stop whirlpool", workerStateEvent.getSource().getException().getMessage());
            });
            shutdownService.start();
        } else {
            //Ensure http clients are shutdown
            whirlpool.shutdown();
        }
    }

    public void exportUtxos(ActionEvent event) {
        Stage window = new Stage();

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Export UTXOs to CSV");
        fileChooser.setInitialFileName(getWalletForm().getWallet().getFullName() + "-utxos.csv");

        AppServices.moveToActiveWindowScreen(window, 800, 450);
        File file = fileChooser.showSaveDialog(window);
        if(file != null) {
            try(FileOutputStream outputStream = new FileOutputStream(file)) {
                CsvWriter writer = new CsvWriter(outputStream, ',', StandardCharsets.UTF_8);
                writer.writeRecord(new String[] {"Date", "Output", "Address", "Label", "Value"});
                for(Entry entry : getWalletForm().getWalletUtxosEntry().getChildren()) {
                    UtxoEntry utxoEntry = (UtxoEntry)entry;
                    writer.write(utxoEntry.getBlockTransaction().getDate() == null ? "Unconfirmed" : EntryCell.DATE_FORMAT.format(utxoEntry.getBlockTransaction().getDate()));
                    writer.write(utxoEntry.getHashIndex().toString());
                    writer.write(utxoEntry.getAddress().getAddress());
                    writer.write(utxoEntry.getLabel());
                    writer.write(getCoinValue(utxoEntry.getValue()));
                    writer.endRecord();
                }
                writer.close();
            } catch(IOException e) {
                log.error("Error exporting UTXOs as CSV", e);
                AppServices.showErrorDialog("Error exporting UTXOs as CSV", e.getMessage());
            }
        }
    }

    private String getCoinValue(Long value) {
        return BitcoinUnit.BTC.equals(utxosTable.getBitcoinUnit()) ?
                CoinLabel.getBTCFormat().format(value.doubleValue() / Transaction.SATOSHIS_PER_BITCOIN) :
                String.format(Locale.ENGLISH, "%d", value);
    }

    @Subscribe
    public void walletNodesChanged(WalletNodesChangedEvent event) {
        if(event.getWallet().equals(walletForm.getWallet())) {
            WalletUtxosEntry walletUtxosEntry = getWalletForm().getWalletUtxosEntry();
            utxosTable.updateAll(walletUtxosEntry);
            utxosChart.update(walletUtxosEntry);
            mixSelected.setVisible(canWalletMix());
        }
    }

    @Subscribe
    public void walletHistoryChanged(WalletHistoryChangedEvent event) {
        if(event.getWallet().equals(walletForm.getWallet())) {
            WalletUtxosEntry walletUtxosEntry = getWalletForm().getWalletUtxosEntry();

            //Will automatically update utxosTable
            walletUtxosEntry.updateUtxos();

            utxosTable.updateHistory(event.getHistoryChangedNodes());
            utxosChart.update(walletUtxosEntry);
        }
    }

    @Subscribe
    public void walletEntryLabelChanged(WalletEntryLabelsChangedEvent event) {
        if(event.getWallet().equals(walletForm.getWallet())) {
            for(Entry entry : event.getEntries()) {
                utxosTable.updateLabel(entry);
            }
            utxosChart.update(getWalletForm().getWalletUtxosEntry());
        }
    }

    @Subscribe
    public void bitcoinUnitChanged(BitcoinUnitChangedEvent event) {
        utxosTable.setBitcoinUnit(getWalletForm().getWallet(), event.getBitcoinUnit());
        utxosChart.setBitcoinUnit(getWalletForm().getWallet(), event.getBitcoinUnit());
        updateButtons(event.getBitcoinUnit());
    }

    @Subscribe
    public void walletHistoryStatus(WalletHistoryStatusEvent event) {
        utxosTable.updateHistoryStatus(event);
    }


    @Subscribe
    public void bwtSyncStatus(BwtSyncStatusEvent event) {
        walletHistoryStatus(new WalletHistoryStatusEvent(walletForm.getWallet(), true, event.getStatus()));
    }

    @Subscribe
    public void bwtScanStatus(BwtScanStatusEvent event) {
        walletHistoryStatus(new WalletHistoryStatusEvent(walletForm.getWallet(), true, event.getStatus()));
    }

    @Subscribe
    public void bwtShutdown(BwtShutdownEvent event) {
        walletHistoryStatus(new WalletHistoryStatusEvent(walletForm.getWallet(), false));
    }

    @Subscribe
    private void connectionFailed(ConnectionFailedEvent event) {
        walletHistoryStatus(new WalletHistoryStatusEvent(walletForm.getWallet(), false));
    }

    @Subscribe
    public void walletUtxoStatusChanged(WalletUtxoStatusChangedEvent event) {
        if(event.getWallet().equals(getWalletForm().getWallet())) {
            utxosTable.refresh();
            updateButtons(Config.get().getBitcoinUnit());
        }
    }

    @Subscribe
    public void includeMempoolOutputsChangedEvent(IncludeMempoolOutputsChangedEvent event) {
        utxosTable.refresh();
    }

    @Subscribe
    public void utxosChartChanged(UtxosChartChangedEvent event) {
        utxosChart.setVisible(event.isVisible() && !getWalletForm().getWallet().isWhirlpoolMixWallet());
    }

    @Subscribe
    public void whirlpoolMix(WhirlpoolMixEvent event) {
        if(event.getWallet().equals(walletForm.getWallet())) {
            WalletUtxosEntry walletUtxosEntry = getWalletForm().getWalletUtxosEntry();
            for(Entry entry : walletUtxosEntry.getChildren()) {
                UtxoEntry utxoEntry = (UtxoEntry)entry;
                if(utxoEntry.getHashIndex().equals(event.getUtxo())) {
                    if(event.getNextUtxo() != null) {
                        utxoEntry.setNextMixUtxo(event.getNextUtxo());
                    } else if(event.getMixFailReason() != null) {
                        utxoEntry.setMixFailReason(event.getMixFailReason());
                    } else {
                        utxoEntry.setMixProgress(event.getMixProgress());
                    }
                }
            }
        }
    }
}
