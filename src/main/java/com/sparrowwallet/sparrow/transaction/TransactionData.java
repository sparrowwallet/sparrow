package com.sparrowwallet.sparrow.transaction;

import com.sparrowwallet.drongo.protocol.Sha256Hash;
import com.sparrowwallet.drongo.protocol.Transaction;
import com.sparrowwallet.drongo.psbt.PSBT;
import com.sparrowwallet.drongo.wallet.BlockTransaction;

import java.util.List;
import java.util.Map;

public class TransactionData {
    private final Transaction transaction;
    private PSBT psbt;
    private BlockTransaction blockTransaction;
    private Map<Sha256Hash, BlockTransaction> inputTransactions;
    private List<BlockTransaction> outputTransactions;

    public TransactionData(PSBT psbt) {
        this.transaction = psbt.getTransaction();
        this.psbt = psbt;
    }

    public TransactionData(BlockTransaction blockTransaction) {
        this.transaction = blockTransaction.getTransaction();
        this.blockTransaction = blockTransaction;
    }

    public TransactionData(Transaction transaction) {
        this.transaction = transaction;
    }

    public Transaction getTransaction() {
        return transaction;
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

    public List<BlockTransaction> getOutputTransactions() {
        return outputTransactions;
    }

    public void setOutputTransactions(List<BlockTransaction> outputTransactions) {
        this.outputTransactions = outputTransactions;
    }
}
