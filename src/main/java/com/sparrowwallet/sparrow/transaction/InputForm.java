package com.sparrowwallet.sparrow.transaction;

import com.sparrowwallet.drongo.protocol.TransactionInput;
import com.sparrowwallet.drongo.protocol.TransactionOutput;
import com.sparrowwallet.drongo.psbt.PSBTInput;
import com.sparrowwallet.drongo.wallet.BlockTransaction;
import com.sparrowwallet.sparrow.io.ElectrumServer;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;

import java.io.IOException;

public class InputForm extends IndexedTransactionForm {
    private final TransactionInput transactionInput;
    private final PSBTInput psbtInput;

    public InputForm(TransactionData txdata, PSBTInput psbtInput) {
        super(txdata, txdata.getPsbt().getPsbtInputs().indexOf(psbtInput));
        this.transactionInput = txdata.getPsbt().getTransaction().getInputs().get(txdata.getPsbt().getPsbtInputs().indexOf(psbtInput));
        this.psbtInput = psbtInput;
    }

    public InputForm(TransactionData txdata, TransactionInput transactionInput) {
        super(txdata, txdata.getTransaction().getInputs().indexOf(transactionInput));
        this.transactionInput = transactionInput;
        this.psbtInput = null;
    }

    public TransactionInput getTransactionInput() {
        return transactionInput;
    }

    public PSBTInput getPsbtInput() {
        return psbtInput;
    }

    public TransactionOutput getReferencedTransactionOutput() {
        if(getInputTransactions() != null) {
            BlockTransaction inputTransaction = getInputTransactions().get(transactionInput.getOutpoint().getHash());
            if(inputTransaction != null && !inputTransaction.equals(ElectrumServer.UNFETCHABLE_BLOCK_TRANSACTION)) {
                return inputTransaction.getTransaction().getOutputs().get((int)transactionInput.getOutpoint().getIndex());
            }
        }

        return null;
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
        return "Input #" + transactionInput.getIndex();
    }
}
