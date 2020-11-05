package com.sparrowwallet.sparrow.transaction;

import com.sparrowwallet.drongo.KeyPurpose;
import com.sparrowwallet.drongo.protocol.TransactionOutput;
import com.sparrowwallet.drongo.psbt.PSBTOutput;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;

import java.io.IOException;

public class OutputForm extends IndexedTransactionForm {
    private final TransactionOutput transactionOutput;
    private PSBTOutput psbtOutput;

    public OutputForm(TransactionData txdata, PSBTOutput psbtOutput) {
        super(txdata, txdata.getPsbt().getPsbtOutputs().indexOf(psbtOutput));
        this.transactionOutput = txdata.getPsbt().getTransaction().getOutputs().get(txdata.getPsbt().getPsbtOutputs().indexOf(psbtOutput));
        this.psbtOutput = psbtOutput;
    }

    public OutputForm(TransactionData txdata, TransactionOutput transactionOutput) {
        super(txdata, txdata.getTransaction().getOutputs().indexOf(transactionOutput));
        this.transactionOutput = transactionOutput;
    }

    public TransactionOutput getTransactionOutput() {
        return transactionOutput;
    }

    public PSBTOutput getPsbtOutput() {
        return psbtOutput;
    }

    public boolean isWalletConsolidation() {
        return (getSigningWallet() != null && getSigningWallet().getWalletOutputScripts(KeyPurpose.RECEIVE).containsKey(getTransactionOutput().getScript()));
    }

    public boolean isWalletChange() {
        return (getSigningWallet() != null && getSigningWallet().getWalletOutputScripts(KeyPurpose.CHANGE).containsKey(getTransactionOutput().getScript()));
    }

    public boolean isWalletPayment() {
        return getSigningWallet() != null;
    }

    @Override
    public Node getContents() throws IOException {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("output.fxml"));
        Node node = loader.load();
        node.setUserData(this);
        OutputController controller = loader.getController();
        controller.setModel(this);
        return node;
    }

    @Override
    public TransactionView getView() {
        return TransactionView.OUTPUT;
    }

    public String toString() {
        return "Output #" + transactionOutput.getIndex();
    }
}
