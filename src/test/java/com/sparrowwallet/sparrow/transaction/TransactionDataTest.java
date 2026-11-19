package com.sparrowwallet.sparrow.transaction;

import com.sparrowwallet.drongo.protocol.Script;
import com.sparrowwallet.drongo.protocol.Sha256Hash;
import com.sparrowwallet.drongo.protocol.Transaction;
import com.sparrowwallet.drongo.wallet.BlockTransaction;
import com.sparrowwallet.drongo.wallet.Wallet;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public class TransactionDataTest {
    private static final Sha256Hash TXID = Sha256Hash.wrap("1111111111111111111111111111111111111111111111111111111111111111");
    private static final Sha256Hash BLOCK_HASH = Sha256Hash.wrap("2222222222222222222222222222222222222222222222222222222222222222");

    private static Transaction getTransaction() {
        Transaction transaction = new Transaction();
        transaction.addInput(BLOCK_HASH, 0, new Script(new byte[0]));
        transaction.addOutput(1000, new Script(new byte[] {0x51}));
        return transaction;
    }

    @Test
    public void unprovenHeightIsServerReported() {
        TransactionData txdata = new TransactionData("tx", new BlockTransaction(TXID, 100, null, null, getTransaction()));
        Assertions.assertTrue(txdata.isServerReported(Collections.emptySet()));
    }

    @Test
    public void provenHeightIsNotServerReported() {
        TransactionData txdata = new TransactionData("tx", new BlockTransaction(TXID, 100, null, null, getTransaction(), BLOCK_HASH));
        Assertions.assertFalse(txdata.isServerReported(Collections.emptySet()));
    }

    @Test
    public void walletTransactionIsNotServerReported() {
        BlockTransaction blockTransaction = new BlockTransaction(TXID, 100, null, null, getTransaction());
        Wallet wallet = new Wallet();
        wallet.updateTransactions(Map.of(TXID, blockTransaction));
        TransactionData txdata = new TransactionData("tx", blockTransaction);
        Assertions.assertFalse(txdata.isServerReported(List.of(wallet)));
    }

    @Test
    public void viewedTransactionIsNotTheWalletCopy() {
        BlockTransaction blockTransaction = new BlockTransaction(TXID, 100, null, null, getTransaction());
        TransactionData txdata = new TransactionData("tx", blockTransaction);
        Assertions.assertNotSame(blockTransaction.getTransaction(), txdata.getTransaction());
        Assertions.assertEquals(blockTransaction.getTransaction().getTxId(), txdata.getTransaction().getTxId());

        txdata.getTransaction().setLocktime(500);
        Assertions.assertEquals(0, blockTransaction.getTransaction().getLocktime());
    }

    @Test
    public void noStatusIsNotServerReported() {
        Assertions.assertFalse(new TransactionData("tx", new Transaction()).isServerReported(Collections.emptySet()));
    }
}
