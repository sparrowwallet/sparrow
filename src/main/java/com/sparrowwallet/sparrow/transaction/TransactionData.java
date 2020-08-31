package com.sparrowwallet.sparrow.transaction;

import com.sparrowwallet.drongo.protocol.Sha256Hash;
import com.sparrowwallet.drongo.protocol.Transaction;
import com.sparrowwallet.drongo.protocol.TransactionSignature;
import com.sparrowwallet.drongo.psbt.PSBT;
import com.sparrowwallet.drongo.wallet.BlockTransaction;
import com.sparrowwallet.drongo.wallet.Keystore;
import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.sparrow.io.Storage;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableMap;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class TransactionData {
    private Transaction transaction;
    private String name;
    private PSBT psbt;
    private BlockTransaction blockTransaction;
    private Map<Sha256Hash, BlockTransaction> inputTransactions;
    private List<BlockTransaction> outputTransactions;

    private int minInputFetched;
    private int maxInputFetched;
    private int minOutputFetched;
    private int maxOutputFetched;

    private final ObservableMap<Wallet, Storage> availableWallets = FXCollections.observableHashMap();
    private final SimpleObjectProperty<Wallet> signingWallet = new SimpleObjectProperty<>(this, "signingWallet", null);
    private final ObservableMap<TransactionSignature, Keystore> signatureKeystoreMap = FXCollections.observableMap(new LinkedHashMap<>());

    public TransactionData(String name, PSBT psbt) {
        this(name, psbt.getTransaction());
        this.psbt = psbt;
    }

    public TransactionData(String name, BlockTransaction blockTransaction) {
        this(name, blockTransaction.getTransaction());
        this.blockTransaction = blockTransaction;
    }

    public TransactionData(String name, Transaction transaction) {
        this.name = name;
        this.transaction = transaction;
    }

    public Transaction getTransaction() {
        return transaction;
    }

    public void setTransaction(Transaction transaction) {
        this.transaction = transaction;
    }

    public String getName() {
        return name;
    }

    public PSBT getPsbt() {
        return psbt;
    }

    public BlockTransaction getBlockTransaction() {
        return blockTransaction;
    }

    public void setBlockTransaction(BlockTransaction blockTransaction) {
        this.blockTransaction = blockTransaction;
    }

    public Map<Sha256Hash, BlockTransaction> getInputTransactions() {
        return inputTransactions;
    }

    public void setInputTransactions(Map<Sha256Hash, BlockTransaction> inputTransactions) {
        this.inputTransactions = inputTransactions;
    }

    public void updateInputsFetchedRange(int pageStart, int pageEnd) {
        if(pageStart < 0 || pageEnd > transaction.getInputs().size()) {
            throw new IllegalStateException("Paging outside transaction inputs range");
        }

        if(pageStart != maxInputFetched) {
            //non contiguous range, ignore
            return;
        }

        this.minInputFetched = Math.min(minInputFetched, pageStart);
        this.maxInputFetched = Math.max(maxInputFetched, pageEnd);
    }

    public int getMaxInputFetched() {
        return maxInputFetched;
    }

    public boolean allInputsFetched() {
        return minInputFetched == 0 && maxInputFetched == transaction.getInputs().size();
    }

    public List<BlockTransaction> getOutputTransactions() {
        return outputTransactions;
    }

    public void setOutputTransactions(List<BlockTransaction> outputTransactions) {
        this.outputTransactions = outputTransactions;
    }

    public void updateOutputsFetchedRange(int pageStart, int pageEnd) {
        if(pageStart < 0 || pageEnd > transaction.getOutputs().size()) {
            throw new IllegalStateException("Paging outside transaction outputs range");
        }

        if(pageStart != maxOutputFetched) {
            //non contiguous range, ignore
            return;
        }

        this.minOutputFetched = Math.min(minOutputFetched, pageStart);
        this.maxOutputFetched = Math.max(maxOutputFetched, pageEnd);
    }

    public int getMaxOutputFetched() {
        return maxOutputFetched;
    }

    public boolean allOutputsFetched() {
        return minOutputFetched == 0 && maxOutputFetched == transaction.getOutputs().size();
    }

    public ObservableMap<Wallet, Storage> getAvailableWallets() {
        return availableWallets;
    }

    public Wallet getSigningWallet() {
        return signingWallet.get();
    }

    public SimpleObjectProperty<Wallet> signingWalletProperty() {
        return signingWallet;
    }

    public void setSigningWallet(Wallet wallet) {
        this.signingWallet.set(wallet);
    }

    public ObservableMap<TransactionSignature, Keystore> getSignatureKeystoreMap() {
        return signatureKeystoreMap;
    }

    public Collection<Keystore> getSignedKeystores() {
        return signatureKeystoreMap.values();
    }
}
