package com.sparrowwallet.sparrow.net;

import com.sparrowwallet.drongo.protocol.Sha256Hash;
import com.sparrowwallet.drongo.wallet.BlockTransaction;
import com.sparrowwallet.drongo.wallet.BlockTransactionHash;

class ScriptHashTx {
    public static final ScriptHashTx ERROR_TX = new ScriptHashTx() {
        @Override
        public BlockTransactionHash getBlockchainTransactionHash() {
            return ElectrumServer.UNFETCHABLE_BLOCK_TRANSACTION;
        }
    };

    public int height;
    public String tx_hash;
    public long fee;

    public BlockTransactionHash getBlockchainTransactionHash() {
        Sha256Hash hash = Sha256Hash.wrap(tx_hash);
        return new BlockTransaction(hash, height, null, fee, null);
    }

    @Override
    public String toString() {
        return "ScriptHashTx{height=" + height + ", tx_hash='" + tx_hash + '\'' + ", fee=" + fee + '}';
    }
}
