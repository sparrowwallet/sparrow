package com.sparrowwallet.sparrow.transaction;

import com.sparrowwallet.drongo.KeyPurpose;
import com.sparrowwallet.drongo.protocol.*;
import com.sparrowwallet.drongo.psbt.PSBT;
import com.sparrowwallet.drongo.wallet.*;
import com.sparrowwallet.sparrow.io.Storage;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableMap;

import java.util.*;

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
    private final SimpleObjectProperty<WalletTransaction> walletTransaction = new SimpleObjectProperty<>(this, "walletTransaction", null);

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

    public Set<WalletNode> getSigningWalletNodes() {
        if(getSigningWallet() == null) {
            throw new IllegalStateException("Signing wallet cannot be null");
        }

        Set<WalletNode> signingWalletNodes = new LinkedHashSet<>();
        for(TransactionInput txInput : transaction.getInputs()) {
            Optional<WalletNode> optNode = getSigningWallet().getWalletTxos().entrySet().stream().filter(entry -> entry.getKey().getHash().equals(txInput.getOutpoint().getHash()) && entry.getKey().getIndex() == txInput.getOutpoint().getIndex()).map(Map.Entry::getValue).findFirst();
            optNode.ifPresent(signingWalletNodes::add);
        }

        for(TransactionOutput txOutput : transaction.getOutputs()) {
            WalletNode changeNode = getSigningWallet().getWalletOutputScripts(KeyPurpose.CHANGE).get(txOutput.getScript());
            if(changeNode != null) {
                signingWalletNodes.add(changeNode);
            } else {
                WalletNode receiveNode = getSigningWallet().getWalletOutputScripts(KeyPurpose.RECEIVE).get(txOutput.getScript());
                if(receiveNode != null) {
                    signingWalletNodes.add(receiveNode);
                }
            }
        }

        return signingWalletNodes;
    }

    public WalletTransaction getWalletTransaction() {
        return walletTransaction.get();
    }

    public SimpleObjectProperty<WalletTransaction> walletTransactionProperty() {
        return walletTransaction;
    }

    public void setWalletTransaction(WalletTransaction walletTransaction) {
        this.walletTransaction.set(walletTransaction);
    }

    public Wallet getWallet() {
        return getSigningWallet() != null ? getSigningWallet() : (getWalletTransaction() != null ? getWalletTransaction().getWallet() : null);
    }
}
