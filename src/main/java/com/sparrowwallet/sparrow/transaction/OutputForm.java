package com.sparrowwallet.sparrow.transaction;

import com.sparrowwallet.drongo.KeyPurpose;
import com.sparrowwallet.drongo.protocol.TransactionOutput;
import com.sparrowwallet.drongo.psbt.PSBTOutput;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;

import java.io.IOException;

public class OutputForm extends IndexedTransactionForm {
    public OutputForm(TransactionData txdata, PSBTOutput psbtOutput) {
        super(txdata, txdata.getPsbt().getPsbtOutputs().indexOf(psbtOutput));
    }

    public OutputForm(TransactionData txdata, TransactionOutput transactionOutput) {
        super(txdata, txdata.getTransaction().getOutputs().indexOf(transactionOutput));
    }

    public TransactionOutput getTransactionOutput() {
        if(txdata.getTransaction() != null) {
            return txdata.getTransaction().getOutputs().get(getIndex());
        }

        return null;
    }

    public boolean isWalletConsolidation() {
        return (getSigningWallet() != null && getSigningWallet().getWalletOutputScripts(KeyPurpose.RECEIVE).containsKey(getTransactionOutput().getScript()));
    }

    public boolean isWalletChange() {
        return (getSigningWallet() != null && getSigningWallet().getWalletOutputScripts(getSigningWallet().getChangeKeyPurpose()).containsKey(getTransactionOutput().getScript()));
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
        return "Output #" + getIndex();
    }
}
