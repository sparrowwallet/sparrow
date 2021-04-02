package com.sparrowwallet.sparrow.wallet;

import com.csvreader.CsvWriter;
import com.google.common.eventbus.Subscribe;
import com.sparrowwallet.drongo.KeyPurpose;
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
import java.util.ResourceBundle;

public class AddressesController extends WalletFormController implements Initializable {
    private static final Logger log = LoggerFactory.getLogger(AddressesController.class);

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
    public void walletEntryLabelChanged(WalletEntryLabelChangedEvent event) {
        if(event.getWallet().equals(walletForm.getWallet())) {
            receiveTable.updateLabel(event.getEntry());
            changeTable.updateLabel(event.getEntry());
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
      exportFile();
    }

    private void exportFile() {
        Stage window = new Stage();

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Export Addresses File");
        String extension = "txt";
        fileChooser.setInitialFileName(getWalletForm().getWallet().getName() + "-" +
                "addresses" +
                (extension == null || extension.isEmpty() ? "" : "." + extension));

        File file = fileChooser.showSaveDialog(window);
        if(file != null) {
            try(FileOutputStream outputStream = new FileOutputStream(file)) {
                CsvWriter writer = new CsvWriter(outputStream, ',', StandardCharsets.UTF_8);
                writer.writeRecord(new String[] {"Index", "Payment Address", "Derivation"});
                for(Entry entry : getWalletForm().getNodeEntry(KeyPurpose.RECEIVE).getChildren()) {
                    NodeEntry childEntry = (NodeEntry)entry;
                    writer.write(childEntry.getNode().getIndex() + "");
                    writer.write(childEntry.getNode().toString());
                    writer.write(childEntry.getNode().getDerivationPath());
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
