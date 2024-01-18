package com.sparrowwallet.sparrow.transaction;

import com.sparrowwallet.drongo.protocol.TransactionInput;
import com.sparrowwallet.drongo.protocol.TransactionOutPoint;
import com.sparrowwallet.drongo.protocol.TransactionOutput;
import com.sparrowwallet.drongo.psbt.PSBTInput;
import com.sparrowwallet.drongo.wallet.BlockTransaction;
import com.sparrowwallet.drongo.wallet.BlockTransactionHashIndex;
import com.sparrowwallet.sparrow.glyphfont.GlyphUtils;
import com.sparrowwallet.sparrow.net.ElectrumServer;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.control.Label;
import org.controlsfx.glyphfont.Glyph;

import java.io.IOException;
import java.util.Optional;

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
        return getWallet() != null && getWallet().isWalletTxo(txInput);
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
        if(getTransactionInput().isCoinBase()) {
            return "Coinbase";
        }

        TransactionOutPoint outPoint = getTransactionInput().getOutpoint();
        return outPoint.getHash().toString().substring(0, 8) + "..:" + outPoint.getIndex();
    }

    @Override
    public Label getLabel() {
        if(getWalletTransaction() != null) {
            TransactionOutPoint outPoint = getTransactionInput().getOutpoint();
            Optional<BlockTransactionHashIndex> optRef = getWalletTransaction().getSelectedUtxos().keySet().stream()
                    .filter(txo -> txo.getHash().equals(outPoint.getHash()) && txo.getIndex() == outPoint.getIndex()).findFirst();
            Glyph inputGlyph = isWalletTxo() ? GlyphUtils.getTxoGlyph() : (getWallet() != null ? GlyphUtils.getMixGlyph() : GlyphUtils.getExternalInputGlyph());
            if(optRef.isPresent() && optRef.get().getLabel() != null) {
                return new Label(optRef.get().getLabel(), inputGlyph);
            } else {
                return new Label(toString(), inputGlyph);
            }
        }

        return super.getLabel();
    }
}
