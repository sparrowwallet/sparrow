package com.sparrowwallet.sparrow.io.ckcard;

import com.sparrowwallet.drongo.crypto.ECDSASignature;
import com.sparrowwallet.drongo.crypto.ECKey;

import java.math.BigInteger;
import java.util.Arrays;

public class CardRead extends CardResponse {
    byte[] sig;
    byte[] pubkey;

    public ECDSASignature getSignature() {
        if(sig != null) {
            BigInteger r = new BigInteger(1, Arrays.copyOfRange(sig, 0, 32));
            BigInteger s = new BigInteger(1, Arrays.copyOfRange(sig, 32, 64));
            return new ECDSASignature(r, s);
        }

        return null;
    }

    public ECKey getPubKey() {
        return ECKey.fromPublicOnly(pubkey);
    }
}
