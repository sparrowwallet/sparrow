package com.sparrowwallet.sparrow.whirlpool;

import com.samourai.wallet.api.backend.MinerFee;
import com.samourai.whirlpool.client.wallet.data.minerFee.MinerFeeSupplier;

public class SparrowMinerFeeSupplier extends MinerFeeSupplier {
    public SparrowMinerFeeSupplier(int feeMin, int feeMax, int feeFallback, MinerFee currentMinerFee) {
        super(feeMin, feeMax, feeFallback);
        setValue(currentMinerFee);
    }
}
