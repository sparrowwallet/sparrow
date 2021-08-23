package com.sparrowwallet.sparrow.whirlpool;

import com.samourai.wallet.hd.HD_Wallet;
import com.samourai.whirlpool.client.wallet.WhirlpoolWalletConfig;
import com.samourai.whirlpool.client.wallet.data.minerFee.BackendWalletDataSupplier;
import com.samourai.whirlpool.client.wallet.data.minerFee.WalletSupplier;
import com.samourai.whirlpool.client.wallet.data.utxo.UtxoConfigPersister;

public class SparrowWalletDataSupplier extends BackendWalletDataSupplier {
    public SparrowWalletDataSupplier(int refreshUtxoDelay, WhirlpoolWalletConfig config, HD_Wallet bip44w, String walletIdentifier) throws Exception {
        super(refreshUtxoDelay, config, bip44w, walletIdentifier);
    }

    @Override
    protected WalletSupplier computeWalletSupplier(WhirlpoolWalletConfig config, HD_Wallet bip44w, String walletIdentifier) throws Exception {
        int externalIndexDefault = config.getExternalDestination() != null ? config.getExternalDestination().getStartIndex() : 0;
        return new WalletSupplier(new SparrowWalletStatePersister(walletIdentifier), config.getBackendApi(), bip44w, externalIndexDefault);
    }

    @Override
    protected UtxoConfigPersister computeUtxoConfigPersister(String walletIdentifier) throws Exception {
        return new SparrowUtxoConfigPersister(walletIdentifier);
    }
}
