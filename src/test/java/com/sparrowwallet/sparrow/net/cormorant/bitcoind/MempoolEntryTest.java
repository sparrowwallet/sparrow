package com.sparrowwallet.sparrow.net.cormorant.bitcoind;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class MempoolEntryTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String FEES = "\"fees\":{\"base\":0.00000282,\"ancestor\":0.00000282}";

    @Test
    public void readsVsizeFromCoreBeforeV32() throws Exception {
        MempoolEntry entry = MAPPER.readValue("{\"vsize\":141,\"ancestorsize\":141,\"bip125-replaceable\":true," + FEES + "}", MempoolEntry.class);
        assertEquals(141, entry.getVsize());
        assertFalse(entry.hasUnconfirmedParents());
        assertEquals(2.0, entry.getVsizeFeerate().getFeerate());
    }

    @Test
    public void readsAdjustedVsizeFromCoreV32() throws Exception {
        MempoolEntry entry = MAPPER.readValue("{\"vsize\":141,\"vsize_adjusted\":141,\"vsize_bip141\":141,\"ancestorsize\":282," + FEES + "}", MempoolEntry.class);
        assertEquals(141, entry.getVsize());
        assertTrue(entry.hasUnconfirmedParents());
    }

    @Test
    public void readsAdjustedVsizeOnceVsizeIsRemoved() throws Exception {
        MempoolEntry entry = MAPPER.readValue("{\"vsize_adjusted\":141,\"vsize_bip141\":141,\"ancestorsize\":141," + FEES + "}", MempoolEntry.class);
        assertEquals(141, entry.getVsize());
        assertFalse(entry.hasUnconfirmedParents());
        assertEquals(2.0, entry.getVsizeFeerate().getFeerate());
    }
}
