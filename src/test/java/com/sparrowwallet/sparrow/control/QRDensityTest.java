package com.sparrowwallet.sparrow.control;

import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import com.google.zxing.qrcode.encoder.Encoder;
import com.sparrowwallet.hummingbird.UR;
import com.sparrowwallet.hummingbird.UREncoder;
import com.sparrowwallet.sparrow.io.bbqr.BBQREncoder;
import com.sparrowwallet.sparrow.io.bbqr.BBQREncoding;
import com.sparrowwallet.sparrow.io.bbqr.BBQRType;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Locale;
import java.util.Map;
import java.util.Random;

public class QRDensityTest {
    private static final Map<QRDensity, Integer> QR_VERSIONS = Map.of(QRDensity.LOW, 7, QRDensity.NORMAL, 17, QRDensity.HIGH, 27);

    @Test
    public void testEncodingsShareQrVersion() throws Exception {
        Random random = new Random(1);
        for(QRDensity density : QRDensity.values()) {
            int maxUrVersion = 0;
            int maxBbqrVersion = 0;
            for(int size = 50; size < 12000; size += 37) {
                byte[] data = new byte[size];
                random.nextBytes(data);

                UREncoder urEncoder = new UREncoder(UR.fromBytes("crypto-psbt", data), density.getMaxUrFragmentLength(), 10, 0);
                maxUrVersion = Math.max(maxUrVersion, getQrVersion(urEncoder.nextPart().toUpperCase(Locale.ROOT)));

                BBQREncoder bbqrEncoder = new BBQREncoder(BBQRType.PSBT, BBQREncoding.ZLIB, data, density.getMaxBbqrFragmentLength(), 0);
                maxBbqrVersion = Math.max(maxBbqrVersion, getQrVersion(bbqrEncoder.nextPart().toUpperCase(Locale.ROOT)));
            }

            Assertions.assertEquals(QR_VERSIONS.get(density), maxUrVersion, density + " UR");
            Assertions.assertEquals(QR_VERSIONS.get(density), maxBbqrVersion, density + " BBQr");
        }
    }

    private static int getQrVersion(String part) throws Exception {
        return Encoder.encode(part, ErrorCorrectionLevel.L).getVersion().getVersionNumber();
    }
}
