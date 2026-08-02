package com.sparrowwallet.sparrow.net;

import com.sparrowwallet.drongo.wallet.BlockTransaction;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class VerboseTransactionTest {
    private static final String LEGACY_TXID = "b78d4708ed7e9d8f023231c770b35727e23689b28a09eae017965a38e41dec83";
    private static final String LEGACY_HEX = "01000000010000000000000000000000000000000000000000000000000000000000000000ffffffff0804e6ed5b1b02b000ffffffff0100f2052a01000000434104ab3779ba979cd2f7d76fd1b6a57f42bf4bdd9210409a693a46d6d426c0ba021aca2f364ce5141b7721b47fb5f34ce7301abbab24c067048b721c633ae65e1af0ac00000000";

    private static final String SEGWIT_TXID = "f8fc281eb915a1b350f89ffc5090c71dc65acaf6e59e29dc5afe53d842f19d01";
    private static final String SEGWIT_WTXID = "ef6da0d3f41a666c0eb914cf592caa803aa465c25cf630f786cd3384619605ad";
    private static final String SEGWIT_HEX = "020000000001014596cc6219630c13cbca099838c2fb0920cde29de1e5473087de8bbce06b9f510100000000ffffffff02502a4b000000000017a914b1cd708c9d49c7ad6ec851ad7f24076233fa7cfb8772915600000000001600145279dddc177883923bcf3bd5aab50e725dca01f302483045022100e9056474685b7d885956c7c7e5ac77e1249373e5d222b13620dcde6a63e337d602206ebb59c1834e991e9c9f6129a78c7669cfc4c41c4d19c6be4dabe6749715d5ee01210278f5f957591a07a51fc5033c3407de2ff722b0a5f98e91c9a9e1e038c9b1b59300000000";

    @Test
    public void acceptsHexMatchingDeclaredTxid() {
        BlockTransaction blockTransaction = verboseTransaction(LEGACY_TXID, LEGACY_HEX).getBlockTransaction();

        assertEquals(LEGACY_TXID, blockTransaction.getHashAsString());
        assertEquals(blockTransaction.getHash(), blockTransaction.getTransaction().getTxId());
    }

    /**
     * Segwit transactions are requested and declared by their non-witness txid, but are serialized with their witness data.
     * Comparing the wtxid here would reject every segwit transaction.
     */
    @Test
    public void acceptsSegwitTransactionDeclaredByTxid() {
        BlockTransaction blockTransaction = verboseTransaction(SEGWIT_TXID, SEGWIT_HEX).getBlockTransaction();

        assertEquals(SEGWIT_TXID, blockTransaction.getHashAsString());
        assertEquals(SEGWIT_WTXID, blockTransaction.getTransaction().getWTxId().toString());
        assertNotEquals(blockTransaction.getTransaction().getTxId(), blockTransaction.getTransaction().getWTxId());
    }

    @Test
    public void rejectsHexNotMatchingDeclaredTxid() {
        VerboseTransaction verboseTransaction = verboseTransaction(LEGACY_TXID, SEGWIT_HEX);

        assertThrows(IllegalStateException.class, verboseTransaction::getBlockTransaction);
    }

    private VerboseTransaction verboseTransaction(String txid, String hex) {
        VerboseTransaction verboseTransaction = new VerboseTransaction();
        verboseTransaction.txid = txid;
        verboseTransaction.hex = hex;
        verboseTransaction.confirmations = 0;

        return verboseTransaction;
    }
}
