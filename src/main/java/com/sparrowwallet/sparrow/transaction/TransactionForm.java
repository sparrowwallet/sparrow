package com.sparrowwallet.sparrow.transaction;

import com.sparrowwallet.drongo.protocol.Sha256Hash;
import com.sparrowwallet.drongo.protocol.Transaction;
import com.sparrowwallet.drongo.protocol.TransactionSignature;
import com.sparrowwallet.drongo.psbt.PSBT;
import com.sparrowwallet.drongo.wallet.*;
import com.sparrowwallet.sparrow.io.Storage;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.ObservableMap;
import javafx.scene.Node;
import javafx.scene.control.Label;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

public abstract class TransactionForm {
    protected final TransactionData txdata;

    public TransactionForm(TransactionData txdata) {
        this.txdata = txdata;
    }

    public TransactionData getTransactionData() {
        return txdata;
    }

    public Transaction getTransaction() {
        return txdata.getTransaction();
    }

    public String getName() {
        return txdata.getName();
    }

    public PSBT getPsbt() {
        return txdata.getPsbt();
    }

    public BlockTransaction getBlockTransaction() {
        return txdata.getBlockTransaction();
    }

    public Map<Sha256Hash, BlockTransaction> getInputTransactions() {
        return txdata.getInputTransactions();
    }

    public int getMaxInputFetched() {
        return txdata.getMaxInputFetched();
    }

    public boolean allInputsFetched() {
        return txdata.allInputsFetched();
    }

    public List<BlockTransaction> getOutputTransactions() {
        return txdata.getOutputTransactions();
    }

    public int getMaxOutputFetched() {
        return txdata.getMaxOutputFetched();
    }

    public boolean allOutputsFetched() {
        return txdata.allOutputsFetched();
    }

    public ObservableMap<Wallet, Storage> getAvailableWallets() {
        return txdata.getAvailableWallets();
    }

    public Wallet getSigningWallet() {
        return txdata.getSigningWallet();
    }

    public SimpleObjectProperty<Wallet> signingWalletProperty() {
        return txdata.signingWalletProperty();
    }

    public void setSigningWallet(Wallet signingWallet) {
        txdata.setSigningWallet(signingWallet);
    }

    public ObservableMap<TransactionSignature, Keystore> getSignatureKeystoreMap() {
        return txdata.getSignatureKeystoreMap();
    }

    public Collection<Keystore> getSignedKeystores() {
        return txdata.getSignedKeystores();
    }

    public Set<WalletNode> getSigningWalletNodes() {
        return txdata.getSigningWalletNodes();
    }

    public WalletTransaction getWalletTransaction() {
        return txdata.getWalletTransaction();
    }

    public SimpleObjectProperty<WalletTransaction> walletTransactionProperty() {
        return txdata.walletTransactionProperty();
    }

    public void setWalletTransaction(WalletTransaction walletTransaction) {
        txdata.setWalletTransaction(walletTransaction);
    }

    public Wallet getWallet() {
        return txdata.getWallet();
    }

    public boolean isEditable() {
        if(getBlockTransaction() != null) {
            return false;
        }

        if(getPsbt() != null) {
            if(getPsbt().hasSignatures() || getPsbt().isSigned()) {
                return false;
            }
            return txdata.getSigningWallet() == null;
        }

        return true;
    }

    public boolean isTransactionFinalized() {
        return getPsbt() == null || getTransaction().hasScriptSigs() || getTransaction().hasWitnesses();
    }

    public abstract Node getContents() throws IOException;

    public abstract TransactionView getView();

    public Label getLabel() {
        return new Label(toString());
    }
}
