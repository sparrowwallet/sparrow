package com.sparrowwallet.sparrow.net;

import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class Auth47Test {
    @Test
    public void acceptsHttpCallbacksWithResource() throws Exception {
        Auth47 auth47 = new Auth47(new URI("auth47://nonce?c=https://example.com/auth&r=example"));

        assertEquals("https", auth47.getCallback().getProtocol());
        assertEquals("example.com", auth47.getCallback().getHost());
    }

    @Test
    public void rejectsNonHttpCallbacksWithResource() {
        assertThrows(IllegalArgumentException.class, () ->
                new Auth47(new URI("auth47://nonce?c=file:///tmp/auth47&r=example")));
    }
}
