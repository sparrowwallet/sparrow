package com.sparrowwallet.sparrow.net;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.sparrowwallet.drongo.Utils;
import com.sparrowwallet.drongo.protocol.Sha256Hash;
import com.sparrowwallet.drongo.protocol.Transaction;
import com.sparrowwallet.drongo.wallet.BlockTransaction;
import com.sparrowwallet.sparrow.AppServices;

import java.util.Date;

@JsonIgnoreProperties(ignoreUnknown = true)
public class VerboseTransaction {
    public String blockhash;
    public long blocktime;
    public int confirmations;
    public String hash;
    public String hex;
    public int locktime;
    public long size;
    public String txid;
    public int version;

    public int getHeight() {
        if(confirmations <= 0) {
            return 0;
        }

        Integer currentHeight = AppServices.getCurrentBlockHeight();
        if(currentHeight != null) {
            return currentHeight - confirmations + 1;
        }

        return -1;
    }

    public Date getDate() {
        if(blocktime == 0) {
            //Ok to return as null here as date inspection for verbose txes is only done by HeadersController, which checks for null values
            return null;
        }

        return new Date(blocktime * 1000);
    }

    public BlockTransaction getBlockTransaction() {
        Sha256Hash declaredTxid = Sha256Hash.wrap(txid);
        Transaction transaction = new Transaction(Utils.hexToBytes(hex));
        if(!transaction.getTxId().equals(declaredTxid)) {
            throw new IllegalStateException("Server returned transaction " + transaction.getTxId() + " for declared txid " + declaredTxid);
        }

        //A block hash records the block a transaction was proven to be in, and nothing here is proven: the server's own is dropped, and only the marker
        //for a response that could not carry one is passed on, that being a statement about the response rather than about the block
        boolean incomplete = Sha256Hash.ZERO_HASH.toString().equals(blockhash);
        return new BlockTransaction(declaredTxid, getHeight(), getDate(), 0L, transaction, incomplete ? Sha256Hash.ZERO_HASH : null);
    }
}
