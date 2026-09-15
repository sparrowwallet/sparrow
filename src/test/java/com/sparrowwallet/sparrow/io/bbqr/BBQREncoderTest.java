package com.sparrowwallet.sparrow.io.bbqr;

import com.sparrowwallet.sparrow.control.QRDensity;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Random;

public class BBQREncoderTest {
    @Test
    public void testEncoding() {
        Random random = new Random();
        for(QRDensity qrDensity : QRDensity.values()) {
            for(BBQREncoding encoding : BBQREncoding.values()) {
                for(int length = qrDensity.getMaxBbqrFragmentLength() / 2; length < qrDensity.getMaxBbqrFragmentLength() * 2.5d; length++) {
                    byte[] data = new byte[length];
                    random.nextBytes(data);

                    BBQREncoder encoder = new BBQREncoder(BBQRType.BINARY, encoding, data, qrDensity.getMaxBbqrFragmentLength(), 0);
                    int partLength = encoder.nextPart().length();
                    for(int i = 1; i < encoder.getNumParts(); i++) {
                        int nextPartLength = encoder.nextPart().length();
                        if(i < encoder.getNumParts() - 1) {
                            Assertions.assertEquals(0, nextPartLength % encoding.getPartModulo(), "Modulo test failed for " + length + " in encoding " + encoding + " on part " + (i+1) + " of " + encoder.getNumParts());
                            Assertions.assertEquals(partLength, nextPartLength);
                        } else {
                            Assertions.assertTrue(nextPartLength <= partLength);
                        }
                    }
                }
            }
        }
    }

    @Test
    public void testEncodingBeyondMaxParts() {
        //At 1000 character fragments this data needs 1440 parts, more than two base36 header characters can number
        byte[] data = new byte[900000];
        new Random().nextBytes(data);

        BBQREncoder encoder = new BBQREncoder(BBQRType.BINARY, BBQREncoding.BASE32, data, QRDensity.LOW.getMaxBbqrFragmentLength(), 0);
        Assertions.assertTrue(encoder.getNumParts() <= 1295, "Encoded " + encoder.getNumParts() + " parts");

        BBQRDecoder decoder = new BBQRDecoder();
        for(int i = 0; i < encoder.getNumParts(); i++) {
            String part = encoder.nextPart();
            Assertions.assertEquals(encoder.getNumParts(), BBQRHeader.fromString(part).seqTotal());
            Assertions.assertTrue(part.length() > 8);
            decoder.receivePart(part);
        }

        Assertions.assertEquals(BBQRDecoder.ResultType.SUCCESS, decoder.getResult().getResultType());
        Assertions.assertArrayEquals(data, decoder.getResult().getData());
    }
}
