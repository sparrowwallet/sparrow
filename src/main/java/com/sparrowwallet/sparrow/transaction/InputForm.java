package com.sparrowwallet.sparrow.transaction;

import com.sparrowwallet.drongo.protocol.TransactionInput;
import com.sparrowwallet.drongo.protocol.TransactionOutput;
import com.sparrowwallet.drongo.psbt.PSBTInput;
import com.sparrowwallet.drongo.wallet.BlockTransaction;
import com.sparrowwallet.sparrow.net.ElectrumServer;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;

import java.io.IOException;

public class InputForm extends IndexedTransactionForm {
    public InputForm(TransactionData txdata, PSBTInput psbtInput) {
        super(txdata, txdata.getPsbt().getPsbtInputs().indexOf(psbtInput));
    }

    public InputForm(TransactionData txdata, TransactionInput transactionInput) {
        super(txdata, txdata.getTransaction().getInputs().indexOf(transactionInput));
    }

    public TransactionInput getTransactionInput() {
        if(txdata.getTransaction() != null) {
            return txdata.getTransaction().getInputs().get(getIndex());
        }

        return null;
    }

    public PSBTInput getPsbtInput() {
        if(txdata.getPsbt() != null) {
            return txdata.getPsbt().getPsbtInputs().get(getIndex());
        }

        return null;
    }

    public TransactionOutput getReferencedTransactionOutput() {
        if(getInputTransactions() != null) {
            BlockTransaction inputTransaction = getInputTransactions().get(getTransactionInput().getOutpoint().getHash());
            if(inputTransaction != null && !inputTransaction.equals(ElectrumServer.UNFETCHABLE_BLOCK_TRANSACTION)) {
                return inputTransaction.getTransaction().getOutputs().get((int)getTransactionInput().getOutpoint().getIndex());
            }
        }

        return null;
    }

    public boolean isWalletTxo() {
        TransactionInput txInput = getTransactionInput();
        return getSigningWallet() != null && getSigningWallet().isWalletTxo(txInput);
    }

    @Override
    public Node getContents() throws IOException {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("input.fxml"));
        Node node = loader.load();
        node.setUserData(this);
        InputController controller = loader.getController();
        controller.setModel(this);
        return node;
    }

    @Override
    public TransactionView getView() {
        return TransactionView.INPUT;
    }

    public String toString() {
        return "Input #" + getIndex();
    }
}
