package com.sparrowwallet.sparrow.io.ckcard;

import com.sparrowwallet.drongo.crypto.ECDSASignature;

import java.math.BigInteger;
import java.util.Arrays;

public class CardSignature extends CardResponse {
    byte[] auth_sig;

    public ECDSASignature getSignature() {
        BigInteger r = new BigInteger(1, Arrays.copyOfRange(auth_sig, 0, 32));
        BigInteger s = new BigInteger(1, Arrays.copyOfRange(auth_sig, 32, 64));
        return new ECDSASignature(r, s);
    }
}
