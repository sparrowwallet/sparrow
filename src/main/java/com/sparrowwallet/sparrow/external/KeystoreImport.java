package com.sparrowwallet.sparrow.external;

import com.sparrowwallet.drongo.policy.PolicyType;
import com.sparrowwallet.drongo.wallet.WalletModel;

public interface KeystoreImport extends Import {
    PolicyType getKeystorePolicyType();
    WalletModel getWalletModel();
    String getKeystoreImportDescription();
}
