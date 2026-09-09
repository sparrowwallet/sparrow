package com.sparrowwallet.sparrow.net;

import com.sparrowwallet.sparrow.SparrowWallet;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SubscriptionServiceTest {
    @TempDir
    private static Path tempHome;

    private static final String SCRIPT_HASH = "0000000000000000000000000000000000000000000000000000000000000001";

    private static final String STATUS_A = "aa00000000000000000000000000000000000000000000000000000000000000";
    private static final String STATUS_B = "bb00000000000000000000000000000000000000000000000000000000000000";

    private final SubscriptionService subscriptionService = new SubscriptionService();

    @BeforeAll
    public static void setUpAll() {
        System.setProperty(SparrowWallet.APP_HOME_PROPERTY, tempHome.toString());
    }

    @AfterAll
    public static void tearDownAll() {
        System.clearProperty(SparrowWallet.APP_HOME_PROPERTY);
    }

    @BeforeEach
    public void setUp() {
        ElectrumServer.getSubscribedScriptHashes().clear();
    }

    @AfterEach
    public void tearDown() {
        ElectrumServer.getSubscribedScriptHashes().clear();
    }

    @Test
    public void aRepeatedStatusIsNotAChange() {
        ElectrumServer.updateSubscribedScriptHashStatus(SCRIPT_HASH, STATUS_A);
        assertFalse(notifyStatus(SCRIPT_HASH, STATUS_A));
        assertEquals(STATUS_A, ElectrumServer.getSubscribedScriptHashStatus(SCRIPT_HASH));
    }

    /**
     * A mempool transaction that is evicted or replaced returns the script hash to the status it held before the transaction arrived, and that is a
     * change like any other: comparing against every status ever seen would leave the wallet showing a transaction the server no longer has.
     */
    @Test
    public void aStatusReturningToAnEarlierValueIsAChange() {
        ElectrumServer.updateSubscribedScriptHashStatus(SCRIPT_HASH, STATUS_A);
        assertTrue(notifyStatus(SCRIPT_HASH, STATUS_B));
        assertEquals(STATUS_B, ElectrumServer.getSubscribedScriptHashStatus(SCRIPT_HASH));

        assertTrue(notifyStatus(SCRIPT_HASH, STATUS_A));
        assertEquals(STATUS_A, ElectrumServer.getSubscribedScriptHashStatus(SCRIPT_HASH));
    }

    /**
     * A script hash with no history has a null status, which is a value the subscription carries rather than an absent subscription.
     */
    @Test
    public void anEmptyHistoryIsAStatusOfItsOwn() {
        ElectrumServer.updateSubscribedScriptHashStatus(SCRIPT_HASH, null);
        assertTrue(ElectrumServer.getSubscribedScriptHashes().containsKey(SCRIPT_HASH));
        assertFalse(notifyStatus(SCRIPT_HASH, null));

        assertTrue(notifyStatus(SCRIPT_HASH, STATUS_A));
        assertEquals(STATUS_A, ElectrumServer.getSubscribedScriptHashStatus(SCRIPT_HASH));

        //Every transaction on the script hash disappearing, as a reorg can do, empties the history again
        assertTrue(notifyStatus(SCRIPT_HASH, null));
        assertNull(ElectrumServer.getSubscribedScriptHashStatus(SCRIPT_HASH));
    }

    /**
     * The decoy and recent transaction subscriptions are not wallet script hashes and are deliberately not tracked, so their notifications are
     * passed on without being recorded - recording them would make them look like wallet nodes already subscribed to.
     */
    @Test
    public void anUntrackedScriptHashIsNotRecorded() {
        assertTrue(notifyStatus(SCRIPT_HASH, STATUS_A));
        assertFalse(ElectrumServer.getSubscribedScriptHashes().containsKey(SCRIPT_HASH));
    }

    /**
     * Delivers a subscription notification and returns whether it was passed on as a history change. The event is posted through Platform.runLater,
     * and no test here starts the JavaFX toolkit, so reaching that call is observable as its refusal - a filtered notification returns before it.
     */
    private boolean notifyStatus(String scriptHash, String status) {
        try {
            subscriptionService.scriptHashStatusUpdated(scriptHash, status);
            return false;
        } catch(IllegalStateException e) {
            assertEquals("Toolkit not initialized", e.getMessage());
            return true;
        }
    }
}
