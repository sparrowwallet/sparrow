package com.sparrowwallet.sparrow.transaction;

import com.sparrowwallet.drongo.KeyPurpose;
import com.sparrowwallet.drongo.address.Address;
import com.sparrowwallet.drongo.protocol.ScriptChunk;
import com.sparrowwallet.drongo.protocol.ScriptOpCodes;
import com.sparrowwallet.drongo.protocol.TransactionOutput;
import com.sparrowwallet.drongo.psbt.PSBTOutput;
import com.sparrowwallet.drongo.wallet.Payment;
import com.sparrowwallet.drongo.wallet.WalletTransaction;
import com.sparrowwallet.sparrow.glyphfont.GlyphUtils;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.control.Label;

import java.io.IOException;
import java.util.*;

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
        return (getWallet() != null && getWallet().getWalletOutputScripts(KeyPurpose.RECEIVE).containsKey(getTransactionOutput().getScript()));
    }

    public boolean isWalletChange() {
        return (getWallet() != null && getWallet().getWalletOutputScripts(getWallet().getChangeKeyPurpose()).containsKey(getTransactionOutput().getScript()));
    }

    public boolean isWalletPayment() {
        return getWallet() != null;
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
        Address address = getTransactionOutput().getScript().getToAddress();
        return address != null ? address.toString() : "Output #" + getIndex();
    }

    @Override
    public Label getLabel() {
        if(getWalletTransaction() != null) {
            List<WalletTransaction.Output> outputs = getWalletTransaction().getOutputs();
            if(getIndex() < outputs.size()) {
                WalletTransaction.Output output = outputs.get(getIndex());
                if(output instanceof WalletTransaction.NonAddressOutput) {
                    List<ScriptChunk> chunks = output.getTransactionOutput().getScript().getChunks();
                    if(!chunks.isEmpty() && chunks.get(0).isOpCode() && chunks.get(0).getOpcode() == ScriptOpCodes.OP_RETURN) {
                        return new Label(chunks.get(0).toString(), GlyphUtils.getOpcodeGlyph());
                    } else {
                        return new Label("Output #" + getIndex(), GlyphUtils.getOpcodeGlyph());
                    }
                } else if(output instanceof WalletTransaction.PaymentOutput paymentOutput) {
                    Payment payment = paymentOutput.getPayment();
                    return new Label(payment.getLabel() != null && payment.getType() != Payment.Type.FAKE_MIX && payment.getType() != Payment.Type.MIX ? payment.getLabel() : payment.getAddress().toString(),
                            GlyphUtils.getOutputGlyph(getWalletTransaction(), payment));
                } else if(output instanceof WalletTransaction.ChangeOutput changeOutput) {
                    return new Label("Change", GlyphUtils.getChangeGlyph());
                }
            }
        }

        return super.getLabel();
    }
}
