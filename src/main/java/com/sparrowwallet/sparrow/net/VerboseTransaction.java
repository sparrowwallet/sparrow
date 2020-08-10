package com.sparrowwallet.sparrow.net;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.sparrowwallet.drongo.Utils;
import com.sparrowwallet.drongo.protocol.Sha256Hash;
import com.sparrowwallet.drongo.protocol.Transaction;
import com.sparrowwallet.drongo.wallet.BlockTransaction;
import com.sparrowwallet.sparrow.AppController;

import java.util.Date;

@JsonIgnoreProperties(ignoreUnknown = true)
class VerboseTransaction {
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
        Integer currentHeight = AppController.getCurrentBlockHeight();
        if(currentHeight != null) {
            return currentHeight - confirmations + 1;
        }

        return -1;
    }

    public Date getDate() {
        return new Date(blocktime * 1000);
    }

    public BlockTransaction getBlockTransaction() {
        return new BlockTransaction(Sha256Hash.wrap(txid), getHeight(), getDate(), 0L, new Transaction(Utils.hexToBytes(hex)), blockhash == null ? null : Sha256Hash.wrap(blockhash));
    }
}
