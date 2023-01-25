package com.sparrowwallet.sparrow.io.ckcard;

import com.sparrowwallet.drongo.crypto.ECDSASignature;

import java.math.BigInteger;
import java.util.Arrays;

public class CardDerive extends CardResponse {
    byte[] sig;
    byte[] chain_code;
    byte[] master_pubkey;
    byte[] pubkey;

    public ECDSASignature getSignature() {
        BigInteger r = new BigInteger(1, Arrays.copyOfRange(sig, 0, 32));
        BigInteger s = new BigInteger(1, Arrays.copyOfRange(sig, 32, 64));
        return new ECDSASignature(r, s);
    }
}
