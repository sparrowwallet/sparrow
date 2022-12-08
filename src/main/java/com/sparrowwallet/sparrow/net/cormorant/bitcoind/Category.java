package com.sparrowwallet.sparrow.net.cormorant.bitcoind;

import java.util.Locale;

public enum Category {
    send, receive, generate, immature, orphan;

    public String toString() {
        return super.toString().toLowerCase(Locale.ROOT);
    }
}
