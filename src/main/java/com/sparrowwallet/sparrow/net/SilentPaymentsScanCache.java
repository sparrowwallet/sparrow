package com.sparrowwallet.sparrow.net;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

class SilentPaymentsScanCache {
    private enum State { SCANNING, COMPLETED, CANCELLED }

    private Integer serverStart;
    private int refCount;
    private State state = State.SCANNING;
    private final List<SilentPaymentsTx> entries = new ArrayList<>();

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

    void cancel() {
        assert lock.isHeldByCurrentThread();
        if(state == State.SCANNING) {
            state = State.CANCELLED;
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
        state = State.SCANNING;
    }

    Integer getServerStart() {
        assert lock.isHeldByCurrentThread();
        return serverStart;
    }

    void setServerStart(int height) {
        assert lock.isHeldByCurrentThread();
        serverStart = height;
        subscriptionComplete.signalAll();
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

    void addEntries(List<SilentPaymentsTx> newEntries) {
        assert lock.isHeldByCurrentThread();
        entries.addAll(newEntries);
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
    void restoreFromSnapshot(Snapshot snapshot) {
        assert lock.isHeldByCurrentThread();
        state = snapshot.state;
        serverStart = snapshot.serverStart;
        entries.clear();
        entries.addAll(snapshot.entries);
        //Wake hold-side waiters who may have been blocked on serverStart==null during the failed widening.
        //scanComplete waiters whose state-condition was unchanged during the widening don't need a signal,
        //but signalling is harmless (they re-check isScanning() and re-await if still scanning).
        subscriptionComplete.signalAll();
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
