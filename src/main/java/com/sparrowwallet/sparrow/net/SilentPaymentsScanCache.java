package com.sparrowwallet.sparrow.net;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

class SilentPaymentsScanCache {
    private static final Logger log = LoggerFactory.getLogger(SilentPaymentsScanCache.class);

    private enum State { SCANNING, COMPLETED, CANCELLED }

    //The specification has the server send the subscribe response before any notification for it, so only a stalled
    //subscribe RPC can hold more than the one or two this leaves room for. The completion notification is the last to
    //arrive and so the first that a cap would lose, which would leave the scan waiting for a completion that can no
    //longer come, so reaching this cancels the scan instead of dropping anything
    private static final int MAX_PENDING_NOTIFICATIONS = 1000;

    private Integer serverStart;
    private int refCount;
    private State state = State.SCANNING;
    private final List<SilentPaymentsTx> entries = new ArrayList<>();

    //The subscribe response naming the canonical start height is recorded only once the RPC returns, while the read
    //thread that delivered it is free to dispatch a notification for the same subscription first. One arriving that
    //early is held here until setServerStart can say whether it belongs to the subscription being established
    private final List<PendingNotification> pendingNotifications = new ArrayList<>();

    private final ReentrantLock lock = new ReentrantLock();
    private final Condition subscriptionComplete = lock.newCondition();
    private final Condition scanComplete = lock.newCondition();

    void lock() {
        lock.lock();
    }

    void unlock() {
        lock.unlock();
    }

    void awaitSubscriptionComplete() throws InterruptedException {
        assert lock.isHeldByCurrentThread();
        subscriptionComplete.await();
    }

    void awaitScanComplete() throws InterruptedException {
        assert lock.isHeldByCurrentThread();
        scanComplete.await();
    }

    boolean isScanning() {
        return state == State.SCANNING;
    }

    boolean isCancelled() {
        return state == State.CANCELLED;
    }

    boolean isCompleted() {
        return state == State.COMPLETED;
    }

    void cancel() {
        assert lock.isHeldByCurrentThread();
        if(state == State.SCANNING) {
            state = State.CANCELLED;
            pendingNotifications.clear();
            subscriptionComplete.signalAll();
            scanComplete.signalAll();
        }
    }

    void complete() {
        assert lock.isHeldByCurrentThread();
        if(state == State.SCANNING) {
            state = State.COMPLETED;
            scanComplete.signalAll();
        }
    }

    /**
     * Reset for a widening rescan: clear serverStart, clear entries, set state back to SCANNING.
     * <p>
     * Deliberately does <b>not</b> signal either condition. Reasoning:
     * <ul>
     *   <li><b>subscriptionComplete</b> waiters require {@code hasMultipleHolders() && getServerStart() == null && isScanning()}.
     *       They cannot exist at this point — restartScan is reached only when {@code getServerStart() != null}
     *       (the widening branch's comparison just evaluated it), so any earlier waiters had already been signalled
     *       by {@link #setServerStart} and exited their wait loop. After restartScan clears serverStart, new waiters
     *       can arrive on subsequent holdSilentPaymentSubscription calls and will block normally; the next
     *       {@link #setServerStart} call (after the widening RPC returns) will signal them.</li>
     *   <li><b>scanComplete</b> waiters require {@code isScanning()}. After restartScan, isScanning is still true
     *       (transitioning {@code SCANNING → SCANNING} for mid-scan widening, or {@code COMPLETED → SCANNING} for
     *       post-scan widening — and in the latter case no scanComplete waiters can exist because they would have
     *       returned when the prior {@link #complete} signalled them). Existing waiters should keep waiting; they'll
     *       wake when the new scan reaches a terminal state via {@link #complete} or {@link #cancel}.</li>
     * </ul>
     * <b>Maintenance note:</b> if a future change adds a new wait condition that depends on state-not-being-SCANNING,
     * serverStart-being-non-null, or entries being non-empty, this method must be updated to signal the new condition.
     * The widening RPC's failure path is covered separately — {@link #cancel} fires both signals — so callers do not
     * rely on restartScan having signalled.
     */
    void restartScan() {
        assert lock.isHeldByCurrentThread();
        serverStart = null;
        entries.clear();
        pendingNotifications.clear();
        state = State.SCANNING;
    }

    Integer getServerStart() {
        assert lock.isHeldByCurrentThread();
        return serverStart;
    }

    /**
     * Records the canonical start height the subscribe response named, and applies the notifications held while it was
     * unknown that this subscription produced: those naming the same height that the server wrote after the response
     * establishing it, at or above the given sequence. Returns what the caller should post once it has released the lock.
     */
    List<Notified> setServerStart(int height, long responseSequence) {
        assert lock.isHeldByCurrentThread();
        serverStart = height;
        subscriptionComplete.signalAll();

        return applyPendingNotifications(responseSequence);
    }

    /**
     * Applies a notification to this cache, or holds it where the subscribe response naming the canonical start height
     * has not been recorded yet. Returns what the caller should post once it has released the lock, which is nothing
     * for a notification that was held or that names the start height of a subscription this cache has moved on from.
     */
    List<Notified> applyOrHold(int startHeight, long responseSequence, double progress, List<SilentPaymentsTx> history) {
        assert lock.isHeldByCurrentThread();
        //Tested against CANCELLED rather than SCANNING because a completed scan still applies the history deltas that
        //follow it. A cancelled one applies nothing, and holding for it would refill the list cancelling just emptied
        if(state == State.CANCELLED) {
            return Collections.emptyList();
        }

        if(serverStart == null) {
            if(pendingNotifications.size() >= MAX_PENDING_NOTIFICATIONS) {
                log.warn("Cancelling silent payments scan: " + pendingNotifications.size() + " notifications held while the subscribe response is outstanding");
                cancel();
                return Collections.emptyList();
            }

            pendingNotifications.add(new PendingNotification(startHeight, responseSequence, progress, history));
            return Collections.emptyList();
        }

        if(startHeight != serverStart) {
            return Collections.emptyList();
        }

        return List.of(apply(progress, history));
    }

    /**
     * Applies the held notifications the subscription now established produced, which are those naming its start
     * height that the server wrote after the response establishing it. The rest are from the subscription it replaced:
     * a server replaces a subscription for the same keys silently, so a re-subscribe at an unchanged start height
     * leaves the two indistinguishable by anything the notification itself carries.
     */
    private List<Notified> applyPendingNotifications(long responseSequence) {
        assert lock.isHeldByCurrentThread();
        if(serverStart == null) {
            //Still not established, so the held notifications cannot be matched yet and must keep waiting
            return Collections.emptyList();
        }

        List<Notified> notified = new ArrayList<>();
        for(PendingNotification pending : pendingNotifications) {
            if(pending.startHeight() == serverStart && pending.responseSequence() >= responseSequence) {
                notified.add(apply(pending.progress(), pending.history()));
            }
        }
        pendingNotifications.clear();

        return notified;
    }

    private Notified apply(double progress, List<SilentPaymentsTx> history) {
        assert lock.isHeldByCurrentThread();
        entries.addAll(history);

        boolean justCompleted = false;
        if(progress >= 1.0 && state == State.SCANNING) {
            complete();
            justCompleted = true;
        }

        return new Notified(progress, progress >= 1.0 && !justCompleted && !history.isEmpty());
    }

    int incrementRefCount() {
        assert lock.isHeldByCurrentThread();
        return ++refCount;
    }

    boolean decrementRefCount() {
        assert lock.isHeldByCurrentThread();
        return --refCount <= 0;
    }

    boolean hasMultipleHolders() {
        assert lock.isHeldByCurrentThread();
        return refCount > 1;
    }

    List<SilentPaymentsTx> snapshotEntries() {
        assert lock.isHeldByCurrentThread();
        return new ArrayList<>(entries);
    }

    /**
     * Captures the cache's pre-widening state ({@code state}, {@code serverStart}, {@code entries}) so it
     * can be restored if the widening RPC fails and other holders are still relying on the cache. Caller
     * must already hold the cache lock.
     */
    Snapshot captureSnapshot() {
        assert lock.isHeldByCurrentThread();
        return new Snapshot(state, serverStart, new ArrayList<>(entries));
    }

    /**
     * Restores the cache's state from a previously captured {@link Snapshot} and signals condition
     * waiters whose conditions may have become re-evaluable. Used by the widening-failure recovery path
     * to restore an in-progress scan when the widening RPC fails but other holders still depend on the cache.
     */
    List<Notified> restoreFromSnapshot(Snapshot snapshot) {
        assert lock.isHeldByCurrentThread();
        //If the cache was cancelled between captureSnapshot and now (e.g., a server disconnect ran
        //cancelSilentPaymentScans during the widening RPC), preserve the cancellation rather than
        //resurrecting a CANCELLED cache to its pre-widening state. Cancel already signalled both
        //conditions, so no further signal is needed here.
        if(state == State.CANCELLED) {
            return Collections.emptyList();
        }
        state = snapshot.state;
        serverStart = snapshot.serverStart;
        entries.clear();
        entries.addAll(snapshot.entries);
        //Wake hold-side waiters who may have been blocked on serverStart==null during the failed widening.
        //scanComplete waiters whose state-condition was unchanged during the widening don't need a signal,
        //but signalling is harmless (they re-check isScanning() and re-await if still scanning).
        subscriptionComplete.signalAll();

        //A notification held while the widening RPC was in flight belongs to the subscription just restored, that RPC
        //having established nothing, so there is no response for the server to have written them before
        return applyPendingNotifications(0L);
    }

    private record PendingNotification(int startHeight, long responseSequence, double progress, List<SilentPaymentsTx> history) {
    }

    /**
     * What the caller should post for an applied notification once it has released the lock, kept separate so that
     * this cache has no dependency on the event bus.
     */
    record Notified(double progress, boolean historyUpdated) {
    }

    static final class Snapshot {
        private final State state;
        private final Integer serverStart;
        private final List<SilentPaymentsTx> entries;

        private Snapshot(State state, Integer serverStart, List<SilentPaymentsTx> entries) {
            this.state = state;
            this.serverStart = serverStart;
            this.entries = entries;
        }
    }
}
