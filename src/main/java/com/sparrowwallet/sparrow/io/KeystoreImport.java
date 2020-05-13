package com.sparrowwallet.sparrow.io;

import com.sparrowwallet.drongo.policy.PolicyType;
import com.sparrowwallet.drongo.wallet.WalletModel;

public interface KeystoreImport extends Import {
    String getKeystoreImportDescription();
}
