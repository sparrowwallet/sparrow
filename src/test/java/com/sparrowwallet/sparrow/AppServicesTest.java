package com.sparrowwallet.sparrow;

import com.sparrowwallet.drongo.uri.BitcoinURI;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public class AppServicesTest {
    private static final String ADDRESS = "bc1qw508d6qejxtdg4y5r3zarvary0c5xw7kv8f3t4";

    @Test
    public void acceptsHttpsPayjoinUri() throws Exception {
        BitcoinURI bitcoinURI = payjoinUri("https://example.com/payjoin");

        Assertions.assertDoesNotThrow(() -> AppServices.addPayjoinURI(bitcoinURI));
        Assertions.assertEquals(bitcoinURI, AppServices.getPayjoinURI(bitcoinURI.getAddress()));

        AppServices.clearPayjoinURI(bitcoinURI.getAddress());
    }

    @Test
    public void acceptsHttpOnionPayjoinUri() throws Exception {
        BitcoinURI bitcoinURI = payjoinUri("http://abcdefghijklmnopqrstuvwxyzabcdefghijklmnop.onion/payjoin");

        Assertions.assertDoesNotThrow(() -> AppServices.addPayjoinURI(bitcoinURI));
        Assertions.assertEquals(bitcoinURI, AppServices.getPayjoinURI(bitcoinURI.getAddress()));

        AppServices.clearPayjoinURI(bitcoinURI.getAddress());
    }

    @Test
    public void rejectsNonHttpOnionPayjoinUri() throws Exception {
        BitcoinURI bitcoinURI = payjoinUri("file://abcdefghijklmnopqrstuvwxyzabcdefghijklmnop.onion/payjoin");

        Assertions.assertThrows(IllegalArgumentException.class, () -> AppServices.addPayjoinURI(bitcoinURI));
    }

    @Test
    public void rejectsMalformedPayjoinUri() throws Exception {
        BitcoinURI bitcoinURI = payjoinUri("payjoin");

        Assertions.assertThrows(IllegalArgumentException.class, () -> AppServices.addPayjoinURI(bitcoinURI));
    }

    private static BitcoinURI payjoinUri(String payjoinUrl) throws Exception {
        return new BitcoinURI("bitcoin:" + ADDRESS + "?pj=" + URLEncoder.encode(payjoinUrl, StandardCharsets.UTF_8));
    }
}
