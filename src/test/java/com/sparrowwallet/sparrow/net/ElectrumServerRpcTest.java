package com.sparrowwallet.sparrow.net;

import com.sparrowwallet.drongo.protocol.HeaderChainState;
import com.sparrowwallet.drongo.protocol.VerificationException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The checks Electrum applies to a blockchain.block.headers response. Every failure is refusal class rather than a session failure, so the
 * header sync stops advancing on a server answering nonsense instead of feeding it to the chain state.
 */
public class ElectrumServerRpcTest {
    private static final int TIP = 900000;

    @Test
    public void acceptsAFullResponse() {
        assertDoesNotThrow(() -> ElectrumServerRpc.checkBlockHeaders(headers(2016, 2016, 2016), 800000, 2016, TIP));
    }

    @Test
    public void acceptsAShortResponseThatReachesTheTip() {
        //The only reason a server may send fewer headers than asked for
        assertDoesNotThrow(() -> ElectrumServerRpc.checkBlockHeaders(headers(17, 17, 2016), TIP - 16, 2016, TIP));
    }

    @Test
    public void rejectsAShortResponseBelowTheTip() {
        assertThrows(VerificationException.class, () -> ElectrumServerRpc.checkBlockHeaders(headers(17, 17, 2016), 800000, 2016, TIP));
    }

    @Test
    public void acceptsAShortResponseWhenTheTipIsUnknown() {
        //Before the application has a chain view there is nothing to judge the response against, so the caller's own linkage check decides
        assertDoesNotThrow(() -> ElectrumServerRpc.checkBlockHeaders(headers(17, 17, 2016), 800000, 2016, null));
    }

    @Test
    public void rejectsMoreHeadersThanRequested() {
        assertThrows(VerificationException.class, () -> ElectrumServerRpc.checkBlockHeaders(headers(2017, 2017, 2016), 800000, 2016, TIP));
    }

    @Test
    public void rejectsAHexLengthInconsistentWithTheCount() {
        assertThrows(VerificationException.class, () -> ElectrumServerRpc.checkBlockHeaders(headers(2016, 2015, 2016), 800000, 2016, TIP));
        assertThrows(VerificationException.class, () -> ElectrumServerRpc.checkBlockHeaders(headers(2016, 2017, 2016), 800000, 2016, TIP));
    }

    @Test
    public void rejectsAServerThatCannotReturnAWholeDifficultyPeriod() {
        //A period must always fit one call, or the sync cannot advance across a retarget
        assertThrows(VerificationException.class, () -> ElectrumServerRpc.checkBlockHeaders(headers(2016, 2016, HeaderChainState.RETARGET_INTERVAL - 1), 800000, 2016, TIP));
    }

    @Test
    public void rejectsMalformedResponses() {
        assertThrows(VerificationException.class, () -> ElectrumServerRpc.checkBlockHeaders(null, 800000, 2016, TIP));
        assertThrows(VerificationException.class, () -> ElectrumServerRpc.checkBlockHeaders(headers(2016, -1, 2016), 800000, 2016, TIP));
        assertThrows(VerificationException.class, () -> ElectrumServerRpc.checkBlockHeaders(headers(-1, 0, 2016), 800000, 2016, TIP));
    }

    /**
     * @param count       the count the server reports
     * @param hexHeaders  the number of headers actually present in the hex
     * @param max         the maximum number of headers the server reports it will return
     */
    private static BlockHeaders headers(int count, int hexHeaders, int max) {
        BlockHeaders blockHeaders = new BlockHeaders();
        blockHeaders.count = count;
        blockHeaders.hex = hexHeaders < 0 ? null : "0".repeat(hexHeaders * BlockHeaders.HEADER_HEX_LENGTH);
        blockHeaders.max = max;

        return blockHeaders;
    }
}
