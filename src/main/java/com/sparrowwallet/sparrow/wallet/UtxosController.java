package com.sparrowwallet.sparrow.wallet;

import com.csvreader.CsvWriter;
import com.google.common.eventbus.Subscribe;
import com.sparrowwallet.drongo.BitcoinUnit;
import com.sparrowwallet.drongo.OsType;
import com.sparrowwallet.drongo.wallet.*;
import com.sparrowwallet.sparrow.UnitFormat;
import com.sparrowwallet.sparrow.AppServices;
import com.sparrowwallet.sparrow.EventManager;
import com.sparrowwallet.sparrow.control.*;
import com.sparrowwallet.sparrow.event.*;
import com.sparrowwallet.sparrow.glyphfont.FontAwesome5;
import com.sparrowwallet.sparrow.io.Config;
import com.sparrowwallet.sparrow.io.WalletTransactions;
import com.sparrowwallet.sparrow.net.ExchangeSource;
import javafx.application.Platform;
import javafx.collections.ListChangeListener;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.Tooltip;
import javafx.scene.control.TreeItem;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.controlsfx.glyphfont.Glyph;
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
    private CopyableCoinLabel balance;

    @FXML
    private FiatLabel fiatBalance;

    @FXML
    private CopyableCoinLabel mempoolBalance;

    @FXML
    private FiatLabel fiatMempoolBalance;

    @FXML
    private CopyableLabel utxoCount;

    @FXML
    private UtxosTreeTable utxosTable;

    @FXML
    private Button selectAll;

    @FXML
    private Button clear;

    @FXML
    private Button sendSelected;

    @FXML
    private UtxosChart utxosChart;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        EventManager.get().register(this);
    }

    @Override
    public void initializeView() {
        balance.valueProperty().addListener((observable, oldValue, newValue) -> {
            setFiatBalance(fiatBalance, AppServices.getFiatCurrencyExchangeRate(), newValue.longValue());
        });
        mempoolBalance.valueProperty().addListener((observable, oldValue, newValue) -> {
            setFiatBalance(fiatMempoolBalance, AppServices.getFiatCurrencyExchangeRate(), newValue.longValue());
        });

        WalletUtxosEntry walletUtxosEntry = getWalletForm().getWalletUtxosEntry();
        updateFields(walletUtxosEntry);
        utxosTable.initialize(walletUtxosEntry);
        utxosChart.initialize(walletUtxosEntry);

        clear.setDisable(true);
        sendSelected.setDisable(true);
        sendSelected.setTooltip(new Tooltip("Send selected UTXOs. Use " + (OsType.getCurrent() == OsType.MACOS ? "Cmd" : "Ctrl") + "+click to select multiple." ));

        utxosTable.getSelectionModel().getSelectedIndices().addListener((ListChangeListener<Integer>) c -> {
            List<Entry> selectedEntries = utxosTable.getSelectionModel().getSelectedCells().stream().filter(tp -> tp.getTreeItem() != null).map(tp -> tp.getTreeItem().getValue()).collect(Collectors.toList());
            utxosChart.select(selectedEntries);
            updateButtons(Config.get().getUnitFormat(), Config.get().getBitcoinUnit());
            updateUtxoCount(getWalletForm().getWalletUtxosEntry());
        });
    }

    private void updateFields(WalletUtxosEntry walletUtxosEntry) {
        balance.setValue(walletUtxosEntry.getBalance());
        mempoolBalance.setValue(walletUtxosEntry.getMempoolBalance());
        updateUtxoCount(walletUtxosEntry);
        selectAll.setDisable(walletUtxosEntry.getChildren() == null || walletUtxosEntry.getChildren().size() == 0);
    }

    private void updateUtxoCount(WalletUtxosEntry walletUtxosEntry) {
        int selectedCount = utxosTable.getSelectionModel().getSelectedCells().size();
        utxoCount.setText((selectedCount > 0 ? selectedCount + "/" : "") + (walletUtxosEntry.getChildren() != null ? Integer.toString(walletUtxosEntry.getChildren().size()) : "0"));
    }

    private void updateButtons(UnitFormat format, BitcoinUnit unit) {
        List<Entry> selectedEntries = getSelectedEntries();

        selectAll.setDisable(utxosTable.getRoot().getChildren().size() == utxosTable.getSelectionModel().getSelectedCells().size());
        clear.setDisable(selectedEntries.isEmpty());
        sendSelected.setDisable(selectedEntries.isEmpty());

        long selectedTotal = selectedEntries.stream().mapToLong(Entry::getValue).sum();
        if(selectedTotal > 0) {
            if(Config.get().isHideAmounts()) {
                sendSelected.setText("Send Selected");
            } else {
                if(format == null) {
                    format = UnitFormat.DOT;
                }

                if(unit == null || unit.equals(BitcoinUnit.AUTO)) {
                    unit = (selectedTotal >= BitcoinUnit.getAutoThreshold() ? BitcoinUnit.BTC : BitcoinUnit.SATOSHIS);
                }

                if(unit.equals(BitcoinUnit.BTC)) {
                    sendSelected.setText("Send Selected (" + format.formatBtcValue(selectedTotal) + " BTC)");
                } else {
                    sendSelected.setText("Send Selected (" + format.formatSatsValue(selectedTotal) + " sats)");
                }
            }
        } else {
            sendSelected.setText("Send Selected");
        }
    }

    private List<Entry> getSelectedEntries() {
        return utxosTable.getSelectionModel().getSelectedCells().stream()
                .filter(tp -> tp.getTreeItem() != null)
                .map(tp -> (UtxoEntry)tp.getTreeItem().getValue())
                .filter(HashIndexEntry::isSpendable)
                .collect(Collectors.toList());
    }

    public void sendSelected(ActionEvent event) {
        List<UtxoEntry> utxoEntries = getSelectedUtxos();
        final List<BlockTransactionHashIndex> spendingUtxos = utxoEntries.stream().map(HashIndexEntry::getHashIndex).collect(Collectors.toList());
        EventManager.get().post(new SendActionEvent(getWalletForm().getWallet(), spendingUtxos));
        Platform.runLater(() -> EventManager.get().post(new SpendUtxoEvent(getWalletForm().getWallet(), spendingUtxos)));
    }

    private List<UtxoEntry> getSelectedUtxos() {
        return utxosTable.getSelectionModel().getSelectedCells().stream()
                .map(tp -> tp.getTreeItem().getValue())
                .filter(e -> e instanceof HashIndexEntry)
                .map(e -> (UtxoEntry)e)
                .filter(e -> e.getType().equals(HashIndexEntry.Type.OUTPUT) && e.isSpendable())
                .collect(Collectors.toList());
    }

    public void selectAll(ActionEvent event) {
        utxosTable.getSelectionModel().selectAll();
    }

    public void clear(ActionEvent event) {
        utxosTable.getSelectionModel().clearSelection();
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
                writer.writeRecord(new String[] {"Date (UTC)", "Output", "Address", "Label", "Value"});
                for(Entry entry : getWalletForm().getWalletUtxosEntry().getChildren()) {
                    UtxoEntry utxoEntry = (UtxoEntry)entry;
                    writer.write(utxoEntry.getBlockTransaction().getDate() == null ? "Unconfirmed" : WalletTransactions.DATE_FORMAT.format(utxoEntry.getBlockTransaction().getDate()));
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
        UnitFormat format = Config.get().getUnitFormat() == null ? UnitFormat.DOT : Config.get().getUnitFormat();
        return BitcoinUnit.BTC.equals(utxosTable.getBitcoinUnit()) ? format.tableFormatBtcValue(value) : String.format(Locale.ENGLISH, "%d", value);
    }

    private static Glyph getExternalGlyph() {
        Glyph externalGlyph = new Glyph(FontAwesome5.FONT_NAME, FontAwesome5.Glyph.EXTERNAL_LINK_ALT);
        externalGlyph.setFontSize(12);
        return externalGlyph;
    }

    private static Glyph getErrorGlyph() {
        Glyph glyph = new Glyph(FontAwesome5.FONT_NAME, FontAwesome5.Glyph.EXCLAMATION_CIRCLE);
        glyph.getStyleClass().add("failure");
        glyph.setFontSize(12);
        return glyph;
    }

    @Subscribe
    public void walletNodesChanged(WalletNodesChangedEvent event) {
        if(event.getWallet().equals(walletForm.getWallet())) {
            WalletUtxosEntry walletUtxosEntry = getWalletForm().getWalletUtxosEntry();
            updateFields(walletUtxosEntry);
            utxosTable.updateAll(walletUtxosEntry);
            utxosChart.update(walletUtxosEntry);
        }
    }

    @Subscribe
    public void walletHistoryChanged(WalletHistoryChangedEvent event) {
        if(event.getWallet().equals(walletForm.getWallet())) {
            WalletUtxosEntry walletUtxosEntry = getWalletForm().getWalletUtxosEntry();

            List<Entry> selectedEntries = utxosTable.getSelectionModel().getSelectedItems().stream().map(TreeItem::getValue).filter(Objects::nonNull).toList();

            //Will automatically update utxosTable
            walletUtxosEntry.updateUtxos();

            if(!walletUtxosEntry.getChildren().containsAll(selectedEntries)) {
                utxosTable.getSelectionModel().clearSelection();
            }

            updateFields(walletUtxosEntry);
            utxosTable.updateHistory();
            utxosChart.update(walletUtxosEntry);
        }
    }

    @Subscribe
    public void walletEntryLabelChanged(WalletEntryLabelsChangedEvent event) {
        if(event.fromThisOrNested(walletForm.getWallet())) {
            for(Entry entry : event.getEntries()) {
                utxosTable.updateLabel(entry);
            }
            utxosChart.update(getWalletForm().getWalletUtxosEntry());
        }
    }

    @Subscribe
    public void unitFormatChanged(UnitFormatChangedEvent event) {
        utxosTable.setUnitFormat(getWalletForm().getWallet(), event.getUnitFormat(), event.getBitcoinUnit());
        utxosChart.setUnitFormat(getWalletForm().getWallet(), event.getUnitFormat(), event.getBitcoinUnit());
        balance.refresh(event.getUnitFormat(), event.getBitcoinUnit());
        mempoolBalance.refresh(event.getUnitFormat(), event.getBitcoinUnit());
        updateButtons(event.getUnitFormat(), event.getBitcoinUnit());
        fiatBalance.refresh(event.getUnitFormat());
        fiatMempoolBalance.refresh(event.getUnitFormat());
    }

    @Subscribe
    public void hideAmountsStatusChanged(HideAmountsStatusEvent event) {
        utxosTable.refresh();
        utxosChart.update(getWalletForm().getWalletUtxosEntry());
        utxosChart.refreshAxisLabels();
        utxosChart.refreshTooltips();
        balance.refresh();
        mempoolBalance.refresh();
        fiatBalance.refresh();
        fiatMempoolBalance.refresh();
        updateButtons(Config.get().getUnitFormat(), Config.get().getBitcoinUnit());
    }

    @Subscribe
    public void walletHistoryStatus(WalletHistoryStatusEvent event) {
        utxosTable.updateHistoryStatus(event);
    }

    @Subscribe
    public void cormorantStatus(CormorantStatusEvent event) {
        if(event.isFor(walletForm.getWallet())) {
            walletHistoryStatus(new WalletHistoryStatusEvent(walletForm.getWallet(), true, event.getStatus()));
        }
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
        if(event.fromThisOrNested(getWalletForm().getWallet())) {
            utxosTable.refresh();
            updateButtons(Config.get().getUnitFormat(), Config.get().getBitcoinUnit());
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
    public void selectEntry(SelectEntryEvent event) {
        if(event.getWallet().equals(getWalletForm().getWallet()) && event.getEntry().getWalletFunction() == Function.UTXOS) {
            utxosTable.getSelectionModel().clearSelection();
            selectEntry(utxosTable, utxosTable.getRoot(), event.getEntry());
        }
    }

    @Subscribe
    public void fiatCurrencySelected(FiatCurrencySelectedEvent event) {
        if(event.getExchangeSource() == ExchangeSource.NONE) {
            fiatBalance.setCurrency(null);
            fiatBalance.setBtcRate(0.0);
            fiatMempoolBalance.setCurrency(null);
            fiatMempoolBalance.setBtcRate(0.0);
        }
    }

    @Subscribe
    public void exchangeRatesUpdated(ExchangeRatesUpdatedEvent event) {
        setFiatBalance(fiatBalance, event.getCurrencyRate(), getWalletForm().getWalletUtxosEntry().getBalance());
        setFiatBalance(fiatMempoolBalance, event.getCurrencyRate(), getWalletForm().getWalletUtxosEntry().getMempoolBalance());
    }
}
