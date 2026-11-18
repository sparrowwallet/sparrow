package com.sparrowwallet.sparrow.io.satochip;

import org.junit.jupiter.api.Test;

import javax.smartcardio.CardException;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SatochipParserTest {
    /**
     * A response the card did not complete reports the card's status rather than being read as an extended key.
     */
    @Test
    public void extendedKeyResponseWithAnErrorStatusReportsTheStatus() {
        APDUResponse response = new APDUResponse(new byte[70], (byte)0x6f, (byte)0x00);
        CardException e = assertThrows(CardException.class, () -> new SatochipParser().parseBip32GetExtendedKey(response));
        assertTrue(e.getMessage().contains("0x6f00"), e.getMessage());
    }
}
