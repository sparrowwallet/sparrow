package com.sparrowwallet.sparrow.io.ckcard;

import com.sparrowwallet.drongo.crypto.ECKey;

public class CardUnseal extends CardResponse {
    int slot;
    byte[] privkey;
    byte[] pubkey;
    byte[] master_pk;
    byte[] chain_code;

    public ECKey getPrivateKey() {
        return ECKey.fromPrivate(privkey);
    }
}
