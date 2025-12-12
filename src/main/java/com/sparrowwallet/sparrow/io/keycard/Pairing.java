package com.sparrowwallet.sparrow.io.keycard;

import java.util.Arrays;
import java.util.Base64;

/**
 * Stores pairing information.
 */
public class Pairing {
    private byte[] pairingKey;
    private byte pairingIndex;

    /**
     * Constructor. The pairingKey and pairingIndex are those generated at the end of a successful pairing.
     *
     * @param pairingKey   the pairing key
     * @param pairingIndex the pairing index
     */
    public Pairing(byte[] pairingKey, byte pairingIndex) {
        this.pairingKey = pairingKey;
        this.pairingIndex = pairingIndex;
    }

    /**
     * Constructor. Initializes from a byte array previously generated from the toByteArray method
     *
     * @param fromByteArray the result of a previous toByteArray invocation
     */
    public Pairing(byte[] fromByteArray) {
        pairingIndex = fromByteArray[0];
        pairingKey = Arrays.copyOfRange(fromByteArray, 1, fromByteArray.length);
    }

    /**
     * Constructor. Initializes from a String previously generated from the toBase64 method
     *
     * @param base64 the result of a previous toBase64 invocation
     */
    public Pairing(String base64) {
        this(Base64.getDecoder().decode(base64));
    }

    public byte[] getPairingKey() {
        return pairingKey;
    }

    public byte getPairingIndex() {
        return pairingIndex;
    }

    public byte[] toByteArray() {
        byte[] res = new byte[pairingKey.length + 1];
        res[0] = pairingIndex;
        System.arraycopy(pairingKey, 0, res, 1, pairingKey.length);

        return res;
    }

    public String toBase64() {
        return Base64.getEncoder().encodeToString(toByteArray());
    }
}
