package com.sparrowwallet.sparrow.io;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class ZBarTest {
    @Test
    public void onlyPrintableAsciiIsTakenAsTheRawData() {
        Assertions.assertTrue(ZBar.isPrintableAscii("UR:CRYPTO-PSBT/HDCXLKAHSSQZWFVSLOFZOXWKRE"));
        Assertions.assertTrue(ZBar.isPrintableAscii("bitcoin:bc1qar0srrr7xfkvy5l643lydnw9re59gtzzwf5mdq?amount=0.01\n"));

        Assertions.assertFalse(ZBar.isPrintableAscii(""));
        Assertions.assertFalse(ZBar.isPrintableAscii("\u0001ç\u0094\u001b"));
        Assertions.assertFalse(ZBar.isPrintableAscii("café"));
    }
}
