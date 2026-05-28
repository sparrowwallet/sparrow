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

    @Test
    public void rejectsOversizedZlibPayload() {
        byte[] data = new byte[BBQREncoding.MAX_DECODED_DATA_LENGTH + 1];
        BBQREncoder encoder = new BBQREncoder(BBQRType.BINARY, BBQREncoding.ZLIB, data, 2000, 0);
        BBQRDecoder decoder = new BBQRDecoder();

        while(decoder.getResult() == null) {
            decoder.receivePart(encoder.nextPart());
        }

        Assertions.assertEquals(BBQRDecoder.ResultType.FAILURE, decoder.getResult().getResultType());
    }

    @Test
    public void rejectsOversizedBase32Payload() {
        byte[] data = new byte[BBQREncoding.MAX_DECODED_DATA_LENGTH + 1];
        BBQREncoder encoder = new BBQREncoder(BBQRType.BINARY, BBQREncoding.BASE32, data, 2000, 0);
        BBQRDecoder decoder = new BBQRDecoder();

        while(decoder.getResult() == null) {
            decoder.receivePart(encoder.nextPart());
        }

        Assertions.assertEquals(BBQRDecoder.ResultType.FAILURE, decoder.getResult().getResultType());
    }
}
