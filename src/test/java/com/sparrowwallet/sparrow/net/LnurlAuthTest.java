package com.sparrowwallet.sparrow.net;

import com.sparrowwallet.drongo.protocol.Bech32;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class LnurlAuthTest {
    private static final String K1 = "000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f";

    @Test
    public void acceptsHttpsCallbacks() throws Exception {
        LnurlAuth lnurlAuth = new LnurlAuth(lightningUri("https://example.com/lnurl-auth?tag=login&k1=" + K1));

        assertEquals("example.com", lnurlAuth.getDomain());
        assertEquals("login to example.com", lnurlAuth.getLoginMessage());
    }

    @Test
    public void acceptsHttpOnionCallbacks() throws Exception {
        LnurlAuth lnurlAuth = new LnurlAuth(lightningUri("http://abcdefghijklmnopqrstuvwxyzabcdefghijklmnop.onion/lnurl-auth?tag=login&k1=" + K1));

        assertEquals("abcdefghijklmnopqrstuvwxyzabcdefghijklmnop.onion", lnurlAuth.getDomain());
    }

    @Test
    public void rejectsHttpClearnetCallbacks() {
        assertThrows(IllegalArgumentException.class, () ->
                new LnurlAuth(lightningUri("http://example.com/lnurl-auth?tag=login&k1=" + K1)));
    }

    @Test
    public void rejectsNonHttpCallbacks() {
        assertThrows(IllegalArgumentException.class, () ->
                new LnurlAuth(lightningUri("ftp://example.com/lnurl-auth?tag=login&k1=" + K1)));
    }

    @Test
    public void rejectsNonLnurlBech32Prefix() {
        assertThrows(IllegalArgumentException.class, () ->
                new LnurlAuth(lightningUri("lnurlx", "https://example.com/lnurl-auth?tag=login&k1=" + K1)));
    }

    private static URI lightningUri(String url) throws Exception {
        return lightningUri("lnurl", url);
    }

    private static URI lightningUri(String prefix, String url) throws Exception {
        byte[] urlBytes = url.getBytes(StandardCharsets.UTF_8);
        byte[] lnurlData = Bech32.convertBits(urlBytes, 0, urlBytes.length, 8, 5, true);
        return new URI("lightning:" + Bech32.encode(prefix, Bech32.Encoding.BECH32, lnurlData));
    }
}
