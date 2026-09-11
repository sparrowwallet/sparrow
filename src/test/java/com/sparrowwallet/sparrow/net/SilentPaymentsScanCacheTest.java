package com.sparrowwallet.sparrow.net;

import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SilentPaymentsScanCacheTest {
    private static final int SERVER_START = 800000;

    //The place in the delivery order of the subscribe response establishing the subscription under test
    private static final long RESPONSE_SEQUENCE = 10L;

    private List<SilentPaymentsTx> history(String txid) {
        return List.of(new SilentPaymentsTx(800001, txid, "00"));
    }

    private List<SilentPaymentsScanCache.Notified> applyOrHold(SilentPaymentsScanCache cache, int startHeight, double progress, List<SilentPaymentsTx> history) {
        return applyOrHold(cache, startHeight, RESPONSE_SEQUENCE, progress, history);
    }

    private List<SilentPaymentsScanCache.Notified> applyOrHold(SilentPaymentsScanCache cache, int startHeight, long responseSequence, double progress, List<SilentPaymentsTx> history) {
        cache.lock();
        try {
            return cache.applyOrHold(startHeight, responseSequence, progress, history);
        } finally {
            cache.unlock();
        }
    }

    private List<SilentPaymentsScanCache.Notified> setServerStart(SilentPaymentsScanCache cache, int height) {
        return setServerStart(cache, height, RESPONSE_SEQUENCE);
    }

    private List<SilentPaymentsScanCache.Notified> setServerStart(SilentPaymentsScanCache cache, int height, long responseSequence) {
        cache.lock();
        try {
            return cache.setServerStart(height, responseSequence);
        } finally {
            cache.unlock();
        }
    }

    private List<SilentPaymentsTx> entries(SilentPaymentsScanCache cache) {
        cache.lock();
        try {
            return cache.snapshotEntries();
        } finally {
            cache.unlock();
        }
    }

    @Test
    public void testNotificationBeforeSubscribeResponseIsApplied() {
        SilentPaymentsScanCache cache = new SilentPaymentsScanCache();

        //The read thread delivering the subscribe response is free to dispatch the first notification for it before
        //the caller has parsed that response and recorded the start height it names
        assertTrue(applyOrHold(cache, SERVER_START, 0.5, history("aa")).isEmpty(), "Nothing can be posted for a notification that is only held");
        assertTrue(entries(cache).isEmpty());

        List<SilentPaymentsScanCache.Notified> replayed = setServerStart(cache, SERVER_START);

        assertEquals(1, entries(cache).size(), "The held notification must be applied once the start height is known");
        assertEquals("aa", entries(cache).getFirst().tx_hash);
        assertEquals(1, replayed.size());
        assertEquals(0.5, replayed.getFirst().progress());
        assertTrue(cache.isScanning());
    }

    @Test
    public void testHeldCompletionCompletesTheScan() {
        SilentPaymentsScanCache cache = new SilentPaymentsScanCache();

        //A dropped completion is what leaves getSilentPaymentHistory waiting on an untimed await until a reconnect
        applyOrHold(cache, SERVER_START, 1.0, history("aa"));
        assertTrue(cache.isScanning());

        List<SilentPaymentsScanCache.Notified> replayed = setServerStart(cache, SERVER_START);

        assertTrue(cache.isCompleted(), "A held completion must complete the scan when it is applied");
        assertEquals(1, replayed.size());
        assertFalse(replayed.getFirst().historyUpdated(), "The completion that completes the scan needs no history event of its own");
    }

    @Test
    public void testHeldNotificationsAppliedInOrder() {
        SilentPaymentsScanCache cache = new SilentPaymentsScanCache();

        applyOrHold(cache, SERVER_START, 0.5, history("aa"));
        applyOrHold(cache, SERVER_START, 1.0, history("bb"));
        setServerStart(cache, SERVER_START);

        assertEquals(List.of("aa", "bb"), entries(cache).stream().map(tx -> tx.tx_hash).toList());
        assertTrue(cache.isCompleted());
    }

    @Test
    public void testHeldNotificationFromPriorSubscribeIsDiscarded() {
        SilentPaymentsScanCache cache = new SilentPaymentsScanCache();

        applyOrHold(cache, SERVER_START, 1.0, history("aa"));
        List<SilentPaymentsScanCache.Notified> replayed = setServerStart(cache, 700000);

        assertTrue(entries(cache).isEmpty(), "A notification naming another subscription's start height must not be applied");
        assertTrue(replayed.isEmpty());
        assertTrue(cache.isScanning());
    }

    @Test
    public void testLiveNotificationIsAppliedAndFiltered() {
        SilentPaymentsScanCache cache = new SilentPaymentsScanCache();
        setServerStart(cache, SERVER_START);

        List<SilentPaymentsScanCache.Notified> notified = applyOrHold(cache, SERVER_START, 0.5, history("aa"));
        assertEquals(1, notified.size());
        assertEquals(1, entries(cache).size());

        assertTrue(applyOrHold(cache, 700000, 1.0, history("bb")).isEmpty(), "A notification from a prior subscribe must still be dropped");
        assertEquals(1, entries(cache).size());
        assertTrue(cache.isScanning());
    }

    @Test
    public void testLaterHistoryOnCompletedScanIsReported() {
        SilentPaymentsScanCache cache = new SilentPaymentsScanCache();
        setServerStart(cache, SERVER_START);
        applyOrHold(cache, SERVER_START, 1.0, Collections.emptyList());
        assertTrue(cache.isCompleted());

        List<SilentPaymentsScanCache.Notified> notified = applyOrHold(cache, SERVER_START, 1.0, history("aa"));

        assertEquals(1, notified.size());
        assertTrue(notified.getFirst().historyUpdated(), "History arriving after the scan completed must be reported");
    }

    @Test
    public void testCancelDiscardsHeldNotifications() {
        SilentPaymentsScanCache cache = new SilentPaymentsScanCache();
        applyOrHold(cache, SERVER_START, 1.0, history("aa"));

        cache.lock();
        try {
            cache.cancel();
        } finally {
            cache.unlock();
        }

        assertTrue(setServerStart(cache, SERVER_START).isEmpty(), "A cancelled cache must not apply what it was holding");
        assertTrue(entries(cache).isEmpty());
    }

    @Test
    public void testRestartScanDiscardsHeldNotifications() {
        SilentPaymentsScanCache cache = new SilentPaymentsScanCache();
        setServerStart(cache, SERVER_START);

        cache.lock();
        try {
            cache.restartScan();
        } finally {
            cache.unlock();
        }

        applyOrHold(cache, SERVER_START, 1.0, history("aa"));

        cache.lock();
        try {
            cache.restartScan();
        } finally {
            cache.unlock();
        }

        assertTrue(setServerStart(cache, SERVER_START).isEmpty(), "A restarted scan must not apply what the prior one was holding");
        assertTrue(entries(cache).isEmpty());
    }

    @Test
    public void testOverflowCancelsTheScan() {
        SilentPaymentsScanCache cache = new SilentPaymentsScanCache();
        for(int i = 0; i < 1000; i++) {
            applyOrHold(cache, SERVER_START, 0.5, history("aa"));
        }
        assertTrue(cache.isScanning());

        applyOrHold(cache, SERVER_START, 0.5, history("aa"));

        //Dropping to stay within the cap would lose the completion, which arrives last, and leave the scan waiting for one
        assertTrue(cache.isCancelled(), "A cache that can hold no more must fail the scan rather than drop what it cannot hold");
        assertTrue(setServerStart(cache, SERVER_START).isEmpty());
        assertTrue(entries(cache).isEmpty());
    }

    @Test
    public void testNotificationWrittenBeforeTheSubscribeResponseIsDiscarded() {
        SilentPaymentsScanCache cache = new SilentPaymentsScanCache();

        //A subscription replaced by a re-subscribe at an unchanged start height can only be told from its replacement
        //by where the server wrote its notifications relative to the response establishing that replacement
        applyOrHold(cache, SERVER_START, RESPONSE_SEQUENCE - 1, 1.0, history("stale"));
        applyOrHold(cache, SERVER_START, RESPONSE_SEQUENCE, 0.5, history("fresh"));

        List<SilentPaymentsScanCache.Notified> replayed = setServerStart(cache, SERVER_START);

        assertEquals(List.of("fresh"), entries(cache).stream().map(tx -> tx.tx_hash).toList(), "Only what the replacing subscription produced may be applied");
        assertEquals(1, replayed.size());
        assertTrue(cache.isScanning(), "A completion from the replaced subscription must not complete this scan");
    }

    @Test
    public void testWideningFailureAppliesNotificationsHeldForTheRestoredSubscription() {
        SilentPaymentsScanCache cache = new SilentPaymentsScanCache();
        setServerStart(cache, SERVER_START);
        applyOrHold(cache, SERVER_START, 1.0, history("scanned"));
        assertTrue(cache.isCompleted());

        //Widening captures the established scan and clears it before re-subscribing at an earlier start
        SilentPaymentsScanCache.Snapshot snapshot;
        cache.lock();
        try {
            snapshot = cache.captureSnapshot();
            cache.restartScan();
        } finally {
            cache.unlock();
        }

        //A live update for the subscription still in place arrives while the widening RPC is outstanding, stamped
        //below any response sequence, since the widening establishes no response for the server to have written it before
        applyOrHold(cache, SERVER_START, RESPONSE_SEQUENCE - 1, 1.0, history("delta"));

        List<SilentPaymentsScanCache.Notified> replayed;
        cache.lock();
        try {
            replayed = cache.restoreFromSnapshot(snapshot);
        } finally {
            cache.unlock();
        }

        assertEquals(List.of("scanned", "delta"), entries(cache).stream().map(tx -> tx.tx_hash).toList(), "A held update belongs to the subscription restored");
        assertTrue(cache.isCompleted());
        assertEquals(1, replayed.size());
        assertTrue(replayed.getFirst().historyUpdated(), "History arriving after the restored scan completed must be reported");
    }

    @Test
    public void testCancelledCacheHoldsNothingFurther() {
        SilentPaymentsScanCache cache = new SilentPaymentsScanCache();
        cache.lock();
        try {
            cache.cancel();
        } finally {
            cache.unlock();
        }

        //A server still streaming into a cancelled cache would otherwise refill the list cancelling just emptied
        assertTrue(applyOrHold(cache, SERVER_START, 1.0, history("aa")).isEmpty());
        assertTrue(setServerStart(cache, SERVER_START).isEmpty(), "A cancelled cache must not hold what it will never apply");
        assertTrue(entries(cache).isEmpty());
    }
}
