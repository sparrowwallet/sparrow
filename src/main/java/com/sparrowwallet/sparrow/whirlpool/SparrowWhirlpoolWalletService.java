package com.sparrowwallet.sparrow.whirlpool;

import com.samourai.wallet.hd.HD_Wallet;
import com.samourai.whirlpool.client.wallet.WhirlpoolWalletConfig;
import com.samourai.whirlpool.client.wallet.WhirlpoolWalletService;
import com.samourai.whirlpool.client.wallet.data.minerFee.WalletDataSupplier;

public class SparrowWhirlpoolWalletService extends WhirlpoolWalletService {
    private String walletId;

    @Override
    protected WalletDataSupplier computeWalletDataSupplier(WhirlpoolWalletConfig config, HD_Wallet bip44w, String walletIdentifier) throws Exception {
        return new SparrowWalletDataSupplier(config.getRefreshUtxoDelay(), config, bip44w, walletId);
    }

    public String getWalletId() {
        return walletId;
    }

    public void setWalletId(String walletId) {
        this.walletId = walletId;
    }
}
