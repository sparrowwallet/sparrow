package com.sparrowwallet.sparrow.net;

import com.sparrowwallet.drongo.Utils;
import com.sparrowwallet.drongo.protocol.Script;
import com.sparrowwallet.drongo.protocol.Sha256Hash;
import com.sparrowwallet.drongo.protocol.Transaction;
import com.sparrowwallet.drongo.wallet.BlockTransaction;
import com.sparrowwallet.drongo.wallet.BlockTransactionHash;
import com.sparrowwallet.drongo.wallet.Wallet;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * The fee a wallet transaction is stored with. A server reports one alongside every mempool entry in a history, and unlike the transaction itself and
 * the height it is held at, nothing later can test it - the txid re-hash proves the body and a merkle proof the height, but a fee is not committed to
 * anywhere. It is worked out here from the transactions funding the inputs wherever the wallet holds them all, since the number reaches the user as
 * the fee an RBF or CPFP is built from.
 */
public class TransactionFeeTest {
    private static final String P2PKH_SCRIPT = "76a914000000000000000000000000000000000000000088ac";

    private static final String EXTERNAL_TXID = "aa00000000000000000000000000000000000000000000000000000000000011";

    private static final long INFLATED_FEE = 500000L;

    @Test
    public void derivesTheFeeWhereTheWalletHoldsTheFundingTransaction() throws Exception {
        Transaction funding = transaction(EXTERNAL_TXID, 100000L);
        Transaction spending = spendingTransaction(funding, 99000L);

        Wallet wallet = new Wallet("test");
        wallet.updateTransactions(Map.of(funding.getTxId(), new BlockTransaction(funding.getTxId(), 1, null, null, funding)));

        Map<Sha256Hash, BlockTransaction> transactions = getTransactions(wallet, references(spending, INFLATED_FEE));

        assertEquals(1000L, transactions.get(spending.getTxId()).getFee());
    }

    @Test
    public void derivesTheFeeFromAFundingTransactionInTheSamePass() throws Exception {
        Transaction funding = transaction(EXTERNAL_TXID, 100000L);
        Transaction spending = spendingTransaction(funding, 99000L);

        //A wallet restored from scratch learns both transactions in the one history, and the fee must not depend on which was seen first
        Map<BlockTransactionHash, Transaction> references = references(spending, INFLATED_FEE);
        references.putAll(references(funding, INFLATED_FEE));

        Map<Sha256Hash, BlockTransaction> transactions = getTransactions(new Wallet("test"), references);

        assertEquals(1000L, transactions.get(spending.getTxId()).getFee());
    }

    @Test
    public void keepsTheReportedFeeWhereAFundingTransactionIsUnknown() throws Exception {
        Transaction funding = transaction(EXTERNAL_TXID, 100000L);
        Transaction spending = spendingTransaction(funding, 99000L);

        //An incoming payment is funded by transactions that paid nothing to this wallet, so its fee is the sender's word until the inputs are fetched
        Map<Sha256Hash, BlockTransaction> transactions = getTransactions(new Wallet("test"), references(spending, 1000L));

        assertEquals(1000L, transactions.get(spending.getTxId()).getFee());
    }

    @Test
    public void reportsNoFeeWhereTheServerReportsNoneAndTheWalletCannotDeriveOne() throws Exception {
        Transaction funding = transaction(EXTERNAL_TXID, 100000L);
        Transaction spending = spendingTransaction(funding, 99000L);

        Map<Sha256Hash, BlockTransaction> transactions = getTransactions(new Wallet("test"), references(spending, null));

        assertNull(transactions.get(spending.getTxId()).getFee());
    }

    @Test
    public void carriesTheStoredFeeOverWhereTheServerReportsNone() throws Exception {
        Transaction funding = transaction(EXTERNAL_TXID, 100000L);
        Transaction spending = spendingTransaction(funding, 99000L);

        //A history entry carries a fee only while the transaction is unconfirmed, so what was learned of an incoming payment is kept
        Wallet wallet = new Wallet("test");
        wallet.updateTransactions(Map.of(spending.getTxId(), new BlockTransaction(spending.getTxId(), 0, null, 1000L, spending)));

        Map<Sha256Hash, BlockTransaction> transactions = getTransactions(wallet, references(spending, null));

        assertEquals(1000L, transactions.get(spending.getTxId()).getFee());
    }

    @Test
    public void prefersTheDerivedFeeToTheStoredOne() throws Exception {
        Transaction funding = transaction(EXTERNAL_TXID, 100000L);
        Transaction spending = spendingTransaction(funding, 99000L);

        //A fee stored from an earlier pass may be one the server asserted, so a derivation that is now possible replaces it
        Wallet wallet = new Wallet("test");
        wallet.updateTransactions(Map.of(funding.getTxId(), new BlockTransaction(funding.getTxId(), 1, null, null, funding),
                spending.getTxId(), new BlockTransaction(spending.getTxId(), 0, null, INFLATED_FEE, spending)));

        Map<Sha256Hash, BlockTransaction> transactions = getTransactions(wallet, references(spending, INFLATED_FEE));

        assertEquals(1000L, transactions.get(spending.getTxId()).getFee());
    }

    /**
     * Builds the given references with their transactions already in hand, which is the state the fetch loop leaves them in and lets the fee be
     * exercised without a server.
     */
    private Map<Sha256Hash, BlockTransaction> getTransactions(Wallet wallet, Map<BlockTransactionHash, Transaction> references) throws ServerException {
        return new ElectrumServer().getTransactions(wallet, references, Collections.emptyMap());
    }

    private Map<BlockTransactionHash, Transaction> references(Transaction transaction, Long fee) {
        Map<BlockTransactionHash, Transaction> references = new TreeMap<>();
        references.put(new BlockTransaction(transaction.getTxId(), 0, null, fee, null), transaction);
        return references;
    }

    private Transaction transaction(String fundedBy, long... values) {
        Transaction transaction = new Transaction();
        transaction.addInput(Sha256Hash.wrap(fundedBy), 0, new Script(new byte[0]));
        for(long value : values) {
            transaction.addOutput(value, new Script(Utils.hexToBytes(P2PKH_SCRIPT)));
        }

        return transaction;
    }

    private Transaction spendingTransaction(Transaction funding, long value) {
        Transaction spending = new Transaction();
        spending.addInput(funding.getTxId(), 0, new Script(new byte[0]));
        spending.addOutput(value, new Script(Utils.hexToBytes(P2PKH_SCRIPT)));

        return spending;
    }
}
