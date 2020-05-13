package com.sparrowwallet.sparrow.io;

import com.sparrowwallet.drongo.wallet.WalletModel;

public interface Import {
    String getName();
    WalletModel getWalletModel();
}
