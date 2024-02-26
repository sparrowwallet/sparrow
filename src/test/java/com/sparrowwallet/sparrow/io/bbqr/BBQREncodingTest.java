package com.sparrowwallet.sparrow.io.bbqr;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Random;

public class BBQREncodingTest {
    @Test
    public void testHex() {
        byte[] data = new byte[1000];
        Random random = new Random();
        random.nextBytes(data);

        String deflated = BBQREncoding.HEX.encode(data);
        byte[] inflated = BBQREncoding.HEX.decode(deflated);

        Assertions.assertArrayEquals(data, inflated);
    }

    @Test
    public void testBase32() {
        byte[] data = new byte[1000];
        Random random = new Random();
        random.nextBytes(data);

        String deflated = BBQREncoding.BASE32.encode(data);
        byte[] inflated = BBQREncoding.BASE32.decode(deflated);

        Assertions.assertArrayEquals(data, inflated);
    }


    @Test
    public void testZlib() {
        byte[] data = new byte[1000];
        Random random = new Random();
        random.nextBytes(data);

        String deflated = BBQREncoding.ZLIB.encode(data);
        byte[] inflated = BBQREncoding.ZLIB.decode(deflated);

        Assertions.assertArrayEquals(data, inflated);
    }
}
