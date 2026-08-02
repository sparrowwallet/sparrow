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

        return new BlockTransaction(declaredTxid, getHeight(), getDate(), 0L, transaction, blockhash == null ? null : Sha256Hash.wrap(blockhash));
    }
}
