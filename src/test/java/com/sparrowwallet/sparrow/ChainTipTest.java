package com.sparrowwallet.sparrow;

import com.sparrowwallet.drongo.protocol.BlockHeader;
import com.sparrowwallet.drongo.protocol.Sha256Hash;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class ChainTipTest {
    private static BlockHeader getHeader(long nonce) {
        return new BlockHeader(1, Sha256Hash.ZERO_HASH, Sha256Hash.ZERO_HASH, null, 1700000000L, 0x207fffffL, nonce);
    }

    @Test
    public void sameBlockAnnouncedAgain() {
        ChainTip tip = new ChainTip(100, getHeader(1));
        Assertions.assertTrue(tip.isSameBlock(new ChainTip(100, getHeader(1))));
        Assertions.assertTrue(tip.isSameBlock(tip));
    }

    @Test
    public void differentBlock() {
        ChainTip tip = new ChainTip(100, getHeader(1));
        Assertions.assertFalse(tip.isSameBlock(new ChainTip(100, getHeader(2))));
        Assertions.assertFalse(tip.isSameBlock(new ChainTip(101, getHeader(1))));
        Assertions.assertFalse(tip.isSameBlock(new ChainTip(100, null)));
        Assertions.assertFalse(tip.isSameBlock(null));
    }
}
