package com.sparrowwallet.sparrow.wallet;

import com.csvreader.CsvWriter;
import com.google.common.eventbus.Subscribe;
import com.sparrowwallet.drongo.KeyPurpose;
import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.drongo.wallet.WalletNode;
import com.sparrowwallet.sparrow.AppServices;
import com.sparrowwallet.sparrow.EventManager;
import com.sparrowwallet.sparrow.control.AddressTreeTable;
import com.sparrowwallet.sparrow.event.*;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.ResourceBundle;

public class AddressesController extends WalletFormController implements Initializable {
    private static final Logger log = LoggerFactory.getLogger(AddressesController.class);
    public static final int DEFAULT_EXPORT_ADDRESSES_LENGTH = 250;

    @FXML
    private AddressTreeTable receiveTable;

    @FXML
    private AddressTreeTable changeTable;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        EventManager.get().register(this);
    }

    @Override
    public void initializeView() {
        receiveTable.initialize(getWalletForm().getNodeEntry(KeyPurpose.RECEIVE));
        changeTable.initialize(getWalletForm().getNodeEntry(KeyPurpose.CHANGE));
    }

    @Subscribe
    public void walletNodesChanged(WalletNodesChangedEvent event) {
        if(event.getWallet().equals(walletForm.getWallet())) {
            receiveTable.updateAll(getWalletForm().getNodeEntry(KeyPurpose.RECEIVE));
            changeTable.updateAll(getWalletForm().getNodeEntry(KeyPurpose.CHANGE));
        }
    }

    @Subscribe
    public void walletHistoryChanged(WalletHistoryChangedEvent event) {
        if(event.getWallet().equals(walletForm.getWallet())) {
            List<WalletNode> receiveNodes = event.getReceiveNodes();
            if(!receiveNodes.isEmpty()) {
                receiveTable.updateHistory(receiveNodes);
            }

            List<WalletNode> changeNodes = event.getChangeNodes();
            if(!changeNodes.isEmpty()) {
                changeTable.updateHistory(changeNodes);
            }
        }
    }

    @Subscribe
    public void walletEntryLabelChanged(WalletEntryLabelsChangedEvent event) {
        if(event.getWallet().equals(walletForm.getWallet())) {
            for(Entry entry : event.getEntries()) {
                receiveTable.updateLabel(entry);
                changeTable.updateLabel(entry);
            }
        }
    }

    @Subscribe
    public void bitcoinUnitChanged(BitcoinUnitChangedEvent event) {
        receiveTable.setBitcoinUnit(getWalletForm().getWallet(), event.getBitcoinUnit());
        changeTable.setBitcoinUnit(getWalletForm().getWallet(), event.getBitcoinUnit());
    }

    @Subscribe
    public void walletUtxoStatusChanged(WalletUtxoStatusChangedEvent event) {
        if(event.getWallet().equals(getWalletForm().getWallet())) {
            receiveTable.refresh();
            changeTable.refresh();
        }
    }

    @Subscribe
    public void walletAddressesStatusChanged(WalletAddressesStatusEvent event) {
        if(event.getWallet().equals(walletForm.getWallet())) {
            receiveTable.updateAll(getWalletForm().getNodeEntry(KeyPurpose.RECEIVE));
            changeTable.updateAll(getWalletForm().getNodeEntry(KeyPurpose.CHANGE));
        }
    }

    public void exportReceiveAddresses(ActionEvent event) {
        exportAddresses(KeyPurpose.RECEIVE);
    }

    public void exportChangeAddresses(ActionEvent event) {
        exportAddresses(KeyPurpose.CHANGE);
    }

    private void exportAddresses(KeyPurpose keyPurpose) {
        Stage window = new Stage();

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Export Addresses to CSV");
        fileChooser.setInitialFileName(getWalletForm().getWallet().getFullName() + "-" + keyPurpose.name().toLowerCase() + "-addresses.csv");

        boolean whirlpoolMixWallet = getWalletForm().getWallet().isWhirlpoolMixWallet();
        Wallet copy = getWalletForm().getWallet().copy();
        WalletNode purposeNode = copy.getNode(keyPurpose);
        purposeNode.fillToIndex(Math.max(purposeNode.getChildren().size(), DEFAULT_EXPORT_ADDRESSES_LENGTH));

        AppServices.moveToActiveWindowScreen(window, 800, 450);
        File file = fileChooser.showSaveDialog(window);
        if(file != null) {
            try(FileOutputStream outputStream = new FileOutputStream(file)) {
                CsvWriter writer = new CsvWriter(outputStream, ',', StandardCharsets.UTF_8);
                writer.writeRecord(new String[] {"Index", "Payment Address", "Derivation", "Label"});
                for(WalletNode indexNode : purposeNode.getChildren()) {
                    writer.write(Integer.toString(indexNode.getIndex()));
                    writer.write(whirlpoolMixWallet ? copy.getAddress(indexNode).toString().substring(0, 20) + "..." : copy.getAddress(indexNode).toString());
                    writer.write(getDerivationPath(indexNode));
                    Optional<Entry> optLabelEntry = getWalletForm().getNodeEntry(keyPurpose).getChildren().stream()
                            .filter(entry -> ((NodeEntry)entry).getNode().getIndex() == indexNode.getIndex()).findFirst();
                    writer.write(optLabelEntry.isPresent() ? optLabelEntry.get().getLabel() : "");
                    writer.endRecord();
                }
                writer.close();
            } catch(IOException e) {
                log.error("Error exporting addresses as CSV", e);
                AppServices.showErrorDialog("Error exporting addresses as CSV", e.getMessage());
            }
        }
    }
}
