package com.sparrowwallet.sparrow.net;

import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class Auth47Test {
    @Test
    public void acceptsHttpsCallbacksWithResource() throws Exception {
        Auth47 auth47 = new Auth47(new URI("auth47://nonce?c=https://example.com/auth&r=example"));

        assertEquals("https", auth47.getCallback().getProtocol());
        assertEquals("example.com", auth47.getCallback().getHost());
    }

    @Test
    public void acceptsSrbnCallbacks() throws Exception {
        Auth47 auth47 = new Auth47(new URI("auth47://nonce?c=srbn://alice@relay.example.com"));

        assertEquals("https", auth47.getCallback().getProtocol());
        assertEquals("relay.example.com", auth47.getCallback().getHost());
    }

    @Test
    public void rejectsHttpClearnetCallbacks() {
        assertThrows(IllegalArgumentException.class, () ->
                new Auth47(new URI("auth47://nonce?c=http://example.com/auth&r=example")));
    }

    @Test
    public void rejectsNonHttpCallbacksWithResource() {
        assertThrows(IllegalArgumentException.class, () ->
                new Auth47(new URI("auth47://nonce?c=file:///tmp/auth47&r=example")));
    }
}
