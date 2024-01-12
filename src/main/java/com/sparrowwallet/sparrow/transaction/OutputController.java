package com.sparrowwallet.sparrow.transaction;

import com.google.common.eventbus.Subscribe;
import com.sparrowwallet.drongo.address.Address;
import com.sparrowwallet.drongo.protocol.NonStandardScriptException;
import com.sparrowwallet.drongo.protocol.TransactionInput;
import com.sparrowwallet.drongo.protocol.TransactionOutput;
import com.sparrowwallet.drongo.wallet.*;
import com.sparrowwallet.sparrow.AppServices;
import com.sparrowwallet.sparrow.EventManager;
import com.sparrowwallet.sparrow.control.*;
import com.sparrowwallet.sparrow.event.PSBTReorderedEvent;
import com.sparrowwallet.sparrow.event.UnitFormatChangedEvent;
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
    private CopyableCoinLabel value;

    @FXML
    private CopyableLabel to;

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

        outputForm.signingWalletProperty().addListener((observable, oldValue, signingWallet) -> {
            updateOutputLegendFromWallet(txOutput, signingWallet);
        });
        outputForm.walletTransactionProperty().addListener((observable, oldValue, walletTransaction) -> {
            updateOutputLegendFromWallet(txOutput, walletTransaction != null ? walletTransaction.getWallet() : null);
        });
        updateOutputLegendFromWallet(txOutput, outputForm.getWallet());

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

    private String getLegendText(TransactionOutput txOutput) {
        return "Output #" + txOutput.getIndex();
    }

    private void updateOutputLegendFromWallet(TransactionOutput txOutput, Wallet wallet) {
        String baseText = getLegendText(txOutput);
        WalletTransaction walletTx = outputForm.getWalletTransaction();
        if(walletTx != null) {
            List<WalletTransaction.Output> outputs = walletTx.getOutputs();
            if(outputForm.getIndex() < outputs.size()) {
                WalletTransaction.Output output = outputs.get(outputForm.getIndex());
                if(output instanceof WalletTransaction.NonAddressOutput) {
                    outputFieldset.setText(baseText);
                } else if(output instanceof WalletTransaction.PaymentOutput paymentOutput) {
                    Payment payment = paymentOutput.getPayment();
                    Wallet toWallet = walletTx.getToWallet(AppServices.get().getOpenWallets().keySet(), payment);
                    WalletNode toNode = walletTx.getWallet() != null && !walletTx.getWallet().isBip47() ? walletTx.getAddressNodeMap().get(payment.getAddress()) : null;
                    outputFieldset.setText(baseText + (toWallet == null ? (toNode != null ? " - Consolidation" : " - Payment") : " - Received to " + toWallet.getFullDisplayName()));
                } else if(output instanceof WalletTransaction.ChangeOutput changeOutput) {
                    outputFieldset.setText(baseText + " - Change to " + changeOutput.getWalletNode().toString());
                } else {
                    outputFieldset.setText(baseText);
                }
            } else {
                outputFieldset.setText(baseText);
            }
        } else if(wallet != null) {
            if(outputForm.isWalletChange()) {
                outputFieldset.setText(baseText + " - Change");
            } else if(outputForm.isWalletConsolidation()) {
                outputFieldset.setText(baseText + " - Consolidation");
            } else {
                outputFieldset.setText(baseText + " - Payment");
            }
        } else {
            outputFieldset.setText(baseText);
        }
        outputFieldset.setIcon(outputForm.getLabel().getGraphic());
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
                            EventManager.get().post(new ViewTransactionEvent(spentBy.getScene().getWindow(), outputBlockTransaction, TransactionView.INPUT, inputIndex));
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

    @Override
    protected TransactionForm getTransactionForm() {
        return outputForm;
    }

    @Subscribe
    public void blockTransactionOutputsFetched(BlockTransactionOutputsFetchedEvent event) {
        if(event.getTxId().equals(outputForm.getTransaction().getTxId()) && outputForm.getPsbt() == null && outputForm.getIndex() >= event.getPageStart() && outputForm.getIndex() < event.getPageEnd()) {
            updateSpent(event.getOutputTransactions());
        }
    }

    @Subscribe
    public void unitFormatChanged(UnitFormatChangedEvent event) {
        value.refresh(event.getUnitFormat(), event.getBitcoinUnit());
    }

    @Subscribe
    public void psbtReordered(PSBTReorderedEvent event) {
        if(event.getPsbt().equals(outputForm.getPsbt())) {
            updateOutputLegendFromWallet(outputForm.getTransactionOutput(), null);
        }
    }
}
