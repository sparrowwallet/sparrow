package com.sparrowwallet.sparrow.io.bbqr;

import com.sparrowwallet.sparrow.control.QRDensity;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Random;

public class BBQRDecoderTest {
    @Test
    public void testDecoder() {
        Random random = new Random();

        for(QRDensity qrDensity : QRDensity.values()) {
            for(BBQREncoding encoding : BBQREncoding.values()) {
                for(int length = qrDensity.getMaxBbqrFragmentLength() / 2; length < qrDensity.getMaxBbqrFragmentLength() * 2.5d; length++) {
                    byte[] data = new byte[length];
                    random.nextBytes(data);

                    BBQREncoder encoder = new BBQREncoder(BBQRType.BINARY, encoding, data, qrDensity.getMaxBbqrFragmentLength() + 13, 0);
                    BBQRDecoder decoder = new BBQRDecoder();

                    while(decoder.getPercentComplete() < 1d) {
                        String part = encoder.nextPart();
                        Assertions.assertTrue(BBQRDecoder.isBBQRFragment(part));
                        if(random.nextDouble() < 0.7) {
                            decoder.receivePart(part);
                        }
                    }

                    Assertions.assertNotNull(decoder.getResult(), "Result was null for encoding " + encoding + " " + qrDensity + " " + length);
                    Assertions.assertArrayEquals(data, decoder.getResult().getData());
                }
            }
        }
    }
}
