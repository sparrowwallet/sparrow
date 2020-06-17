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

    private int minInputFetched;
    private int maxInputFetched;
    private int minOutputFetched;
    private int maxOutputFetched;

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

    public boolean allOutputsFetched() {
        return minOutputFetched == 0 && maxOutputFetched == transaction.getOutputs().size();
    }
}
