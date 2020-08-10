package com.sparrowwallet.sparrow.transaction;

import com.google.common.eventbus.Subscribe;
import com.sparrowwallet.drongo.address.Address;
import com.sparrowwallet.drongo.protocol.NonStandardScriptException;
import com.sparrowwallet.drongo.protocol.TransactionInput;
import com.sparrowwallet.drongo.protocol.TransactionOutput;
import com.sparrowwallet.drongo.wallet.BlockTransaction;
import com.sparrowwallet.sparrow.EventManager;
import com.sparrowwallet.sparrow.control.AddressLabel;
import com.sparrowwallet.sparrow.control.CoinLabel;
import com.sparrowwallet.sparrow.control.CopyableLabel;
import com.sparrowwallet.sparrow.control.ScriptArea;
import com.sparrowwallet.sparrow.event.BitcoinUnitChangedEvent;
import com.sparrowwallet.sparrow.event.BlockTransactionOutputsFetchedEvent;
import com.sparrowwallet.sparrow.event.ViewTransactionEvent;
import com.sparrowwallet.sparrow.net.ElectrumServer;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import tornadofx.control.Field;
import tornadofx.control.Fieldset;

import java.net.URL;
import java.util.List;
import java.util.ResourceBundle;

public class OutputController extends TransactionFormController implements Initializable {
    private OutputForm outputForm;

    @FXML
    private Fieldset outputFieldset;

    @FXML
    private CoinLabel value;

    @FXML
    private CopyableLabel to;

    @FXML
    private CopyableLabel change;

    @FXML
    private AddressLabel address;

    @FXML
    private Field spentField;

    @FXML
    private Label spent;

    @FXML
    private Field spentByField;

    @FXML
    private Hyperlink spentBy;

    @FXML
    private ScriptArea scriptPubKeyArea;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        EventManager.get().register(this);
    }

    public void initializeView() {
        TransactionOutput txOutput = outputForm.getTransactionOutput();

        outputFieldset.setText("Output #" + txOutput.getIndex());

        value.setValue(txOutput.getValue());
        to.setVisible(false);
        try {
            Address[] addresses = txOutput.getScript().getToAddresses();
            to.setVisible(true);
            if(addresses.length == 1) {
                address.setAddress(addresses[0]);
            } else {
                address.setText("multiple addresses");
            }
        } catch(NonStandardScriptException e) {
            //ignore
        }

        change.managedProperty().bind(change.visibleProperty());
        change.setVisible(false);
        outputForm.signingWalletProperty().addListener((observable, oldValue, signingWallet) -> {
            change.setVisible(signingWallet != null && signingWallet.isWalletOutputScript(txOutput.getScript()));
        });

        spentField.managedProperty().bind(spentField.visibleProperty());
        spentByField.managedProperty().bind(spentByField.visibleProperty());
        spentByField.setVisible(false);

        if(outputForm.getPsbt() != null) {
            spent.setText("Unspent");
        } else if(outputForm.getOutputTransactions() != null) {
            updateSpent(outputForm.getOutputTransactions());
        } else {
            spent.setText("Unknown");
        }

        initializeScriptField(scriptPubKeyArea);
        scriptPubKeyArea.clear();
        scriptPubKeyArea.appendScript(txOutput.getScript(), null, null);
    }

    private void updateSpent(List<BlockTransaction> outputTransactions) {
        int outputIndex = outputForm.getIndex();
        if(outputIndex < outputForm.getMaxOutputFetched()) {
            spent.setText("Unspent");
        } else {
            spent.setText("Unknown");
        }

        if(outputIndex >= 0 && outputIndex < outputTransactions.size()) {
            BlockTransaction outputBlockTransaction = outputTransactions.get(outputIndex);
            if(outputBlockTransaction != null) {
                spent.setText("Spent");

                if(outputBlockTransaction == ElectrumServer.UNFETCHABLE_BLOCK_TRANSACTION) {
                    spent.setText("Spent (Spending transaction history too large to fetch)");
                    return;
                }

                for(int i = 0; i < outputBlockTransaction.getTransaction().getInputs().size(); i++) {
                    TransactionInput input = outputBlockTransaction.getTransaction().getInputs().get(i);
                    if(input.getOutpoint().getHash().equals(outputForm.getTransaction().getTxId()) && input.getOutpoint().getIndex() == outputIndex) {
                        spentField.setVisible(false);
                        spentByField.setVisible(true);

                        final Integer inputIndex = i;
                        spentBy.setText(outputBlockTransaction.getHash().toString() + ":" + inputIndex);
                        spentBy.setOnAction(event -> {
                            EventManager.get().post(new ViewTransactionEvent(outputBlockTransaction, TransactionView.INPUT, inputIndex));
                        });
                        spentBy.setContextMenu(new TransactionReferenceContextMenu(spentBy.getText()));
                    }
                }
            }
        }
    }

    public void setModel(OutputForm form) {
        this.outputForm = form;
        initializeView();
    }

    @Subscribe
    public void blockTransactionOutputsFetched(BlockTransactionOutputsFetchedEvent event) {
        if(event.getTxId().equals(outputForm.getTransaction().getTxId()) && outputForm.getPsbt() == null && outputForm.getIndex() >= event.getPageStart() && outputForm.getIndex() < event.getPageEnd()) {
            updateSpent(event.getOutputTransactions());
        }
    }

    @Subscribe
    public void bitcoinUnitChanged(BitcoinUnitChangedEvent event) {
        value.refresh(event.getBitcoinUnit());
    }
}
