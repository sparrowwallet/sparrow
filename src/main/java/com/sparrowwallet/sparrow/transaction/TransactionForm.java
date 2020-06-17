package com.sparrowwallet.sparrow.transaction;

import com.sparrowwallet.drongo.protocol.Sha256Hash;
import com.sparrowwallet.drongo.protocol.Transaction;
import com.sparrowwallet.drongo.psbt.PSBT;
import com.sparrowwallet.drongo.wallet.BlockTransaction;
import javafx.scene.Node;

import java.io.IOException;
import java.util.List;
import java.util.Map;

public abstract class TransactionForm {
    private final TransactionData txdata;

    public TransactionForm(TransactionData txdata) {
        this.txdata = txdata;
    }

    public Transaction getTransaction() {
        return txdata.getTransaction();
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

    public boolean allOutputsFetched() {
        return txdata.allOutputsFetched();
    }

    public boolean isEditable() {
        return txdata.getBlockTransaction() == null;
    }

    public abstract Node getContents() throws IOException;

    public abstract TransactionView getView();
}
