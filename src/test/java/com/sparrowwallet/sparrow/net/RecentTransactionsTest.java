package com.sparrowwallet.sparrow.net;

import com.sparrowwallet.drongo.protocol.Sha256Hash;
import com.sparrowwallet.drongo.wallet.BlockTransaction;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class RecentTransactionsTest {
    private static final long FETCHED = 1_000_000_000L;

    private static final List<BlockTransaction> TRANSACTIONS = List.of(new BlockTransaction(Sha256Hash.ZERO_HASH, 0, null, 0L, null));

    @Test
    public void testFetchedAtCurrentTipIsUsable() {
        ElectrumServer.RecentTransactions recent = new ElectrumServer.RecentTransactions(100, FETCHED, TRANSACTIONS);
        assertEquals(TRANSACTIONS, ElectrumServer.BlockSummaryService.getBroadcastableRecent(recent, 100, FETCHED + 60_000L));
    }

    @Test
    public void testUsableUpToTheLongestDelay() {
        ElectrumServer.RecentTransactions recent = new ElectrumServer.RecentTransactions(100, FETCHED, TRANSACTIONS);
        long latest = FETCHED + ElectrumServer.RECENT_BROADCAST_MAX_DELAY_SECS * 1000L;
        assertEquals(TRANSACTIONS, ElectrumServer.BlockSummaryService.getBroadcastableRecent(recent, 100, latest));
    }

    @Test
    public void testFetchedBeforeCurrentTipIsNotUsable() {
        ElectrumServer.RecentTransactions recent = new ElectrumServer.RecentTransactions(100, FETCHED, TRANSACTIONS);
        assertTrue(ElectrumServer.BlockSummaryService.getBroadcastableRecent(recent, 101, FETCHED + 60_000L).isEmpty());
    }

    @Test
    public void testOlderThanTheLongestDelayIsNotUsable() {
        ElectrumServer.RecentTransactions recent = new ElectrumServer.RecentTransactions(100, FETCHED, TRANSACTIONS);
        long late = FETCHED + ElectrumServer.RECENT_BROADCAST_MAX_DELAY_SECS * 1000L + 1;
        assertTrue(ElectrumServer.BlockSummaryService.getBroadcastableRecent(recent, 100, late).isEmpty());
    }

    @Test
    public void testNothingFetchedOrUnknownTipIsNotUsable() {
        ElectrumServer.RecentTransactions recent = new ElectrumServer.RecentTransactions(100, FETCHED, TRANSACTIONS);
        assertTrue(ElectrumServer.BlockSummaryService.getBroadcastableRecent(null, 100, FETCHED).isEmpty());
        assertTrue(ElectrumServer.BlockSummaryService.getBroadcastableRecent(recent, null, FETCHED).isEmpty());
    }
}
