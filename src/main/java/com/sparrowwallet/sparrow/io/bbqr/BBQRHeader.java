package com.sparrowwallet.sparrow.io.bbqr;

import java.math.BigInteger;

public record BBQRHeader(BBQREncoding encoding, BBQRType type, int seqTotal, int seqNumber) {
    public static final String HEADER = "B$";

    public String toString() {
        return HEADER + encoding.getCode() + type.getCode() + encodeToBase36(seqTotal) + encodeToBase36(seqNumber);
    }

    public byte[] decode(String part) {
        return encoding.decode(part.substring(8));
    }

    public static BBQRHeader fromString(String part) {
        if(part.length() < 8) {
            throw new IllegalArgumentException("Part too short");
        }

        if(!HEADER.equals(part.substring(0, 2))) {
            throw new IllegalArgumentException("Part does not start with " + HEADER);
        }

        BBQREncoding e = BBQREncoding.fromString(part.substring(2, 3));
        BBQRType t = BBQRType.fromString(part.substring(3, 4));

        return new BBQRHeader(e, t, decodeFromBase36(part.substring(4, 6)), decodeFromBase36(part.substring(6, 8)));
    }

    private String encodeToBase36(int number) {
        String base36Encoded = new BigInteger(String.valueOf(number)).toString(36);
        return String.format("%2s", base36Encoded).replace(' ', '0');
    }

    private static int decodeFromBase36(String base36) {
        BigInteger bigInteger = new BigInteger(base36, 36);
        return bigInteger.intValue();
    }
}
