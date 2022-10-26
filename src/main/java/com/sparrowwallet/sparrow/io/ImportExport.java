package com.sparrowwallet.sparrow.io;

import com.sparrowwallet.drongo.wallet.WalletModel;

public interface ImportExport {
    String getName();
    WalletModel getWalletModel();
    default boolean isDeprecated() {
        return false;
    }
}
